# CCTV Intrusion Alert (FAMILY face check), v2

> **v3 update (Lane 2, agreed during implementation):** these points override v2 where they differ.
> - **YOLO person check comes first.** No person → ignore and keep checking, nothing recorded. Recording starts only once a person is confirmed.
> - **Person but no face** (back turned, fully covered face, too far, too dark) → keep recording, keep the clip as evidence, and say **"You are being recorded" once per event at max alarm volume — no siren, no red screen**.
> - **FAMILY ✓** → silent: stop the recording and **delete** the clip and photos. FAMILY visits are not kept in history.
> - **Unknown or masked ✗** → torch strobe + **"You are being recorded"** + siren + red alert + MQTT. With the siren, the voice plays first and the siren follows; it no longer plays on every motion.
> - **Event timing:** an event starts after 2 moving radar samples and continues while radar presence (moving or stationary) remains, with a 5 s grace period.
> - **Occupancy camera:** the ImageCapture keeps its existing warm behaviour, so headcount and illuminance stay identical. Only the CCTV video turns on and off with motion.
## Context
ReWatt phones already sit on a wall with an ESP32-C6 mmWave radar on USB and a background camera service (`CameraCaptureService`) that takes a full-quality back-camera photo every 10 min for **illuminance (EV) + headcount (YOLO)**.

The user wants a home-security mode. While **Armed**, radar movement opens a **motion event**, and during the event the phone:
- records a video clip
- takes photos, finds faces, and compares them with faces registered as **"FAMILY"** using InsightFace **buffalo_s** (SCRFD `det_500m` + MobileFaceNet `w600k_mbf`, run with ONNX Runtime)

If a face is found that is **not** FAMILY, the phone:
- plays a loud siren and wakes the screen with a red full-screen alert
- vibrates and strobes the torch
- saves the event and publishes it to MQTT

v2 refines the timing and recording logic using the team's previous PIR-based Motion CCTV project. That project used an event state machine with a 5 s grace period, kept the camera off until motion, recorded one clip per event, played a TTS deterrent, and re-requested the torch after binding.

**Hard constraint:** the CCTV feature must **not change headcount or illuminance results**. The 10-min occupancy capture keeps working exactly as today.

## Decisions (confirmed with user)
| Topic | Decision |
|---|---|
| Arming | Manual **Arm / Disarm** switch (persisted). Motion never auto-arms, unlike the old project. |
| Event model | Old-project state machine: `IDLE → MOTION → GRACE(5 s) → IDLE`. Movement resuming within 5 s continues the same event. Event id `yyyyMMdd_HHmmss`. |
| Evidence | One **video clip** per event (FHD, 30 fps fixed, back camera, **with audio**, max 5 min per part, auto-split) **plus** still **photos** for face checks |
| Face checks | Photo at event start, then **every ~2 s** until decided: STRANGER → alarm and stop checking, or FAMILY confirmed → stop checking. No face in every photo → ignored. |
| Multiple faces | Any face in a photo that doesn't match FAMILY → STRANGER |
| Alarm count | At most **one alarm per event**. Recording continues until the event ends, even after Dismiss. |
| Camera power | **Off until motion**: bound at event start, released 5 s after the event ends (except during the normal 10-min occupancy photo) |
| Voice | TTS "You are being recorded" (Malay: "Anda sedang dirakam", following the app language) **once per event**; the screen stays dark |
| Red screen + siren | Only when a STRANGER is confirmed |
| Torch | On during an event **only if dark**, decided from the EV of the event's first photo (taken before any torch). Re-requested 300 ms after recording starts, as in the old project. |
| Illuminance protection | If the 10-min occupancy photo fires while the torch is on: torch off → wait ~400 ms for AE to settle → capture → torch back on |
| Storage | App-private storage with an **Event history** screen. Keep all events (FAMILY / no face / STRANGER). Auto-delete oldest when > **2 GB** or > **7 days**. |
| Registration | Live **front** camera in-app, ~5 guided shots per person, all saved as **FAMILY**; add more / delete / clear all |
| Stop alarm | One big **Dismiss** button (also on the notification) |
| Siren | ALARM stream, volume forced to **max** and restored after Dismiss; rings in silent/DND |
| Extras | Vibrate + torch strobe during the alarm; MQTT publish |
| Occupancy capture | Runs alongside, unchanged |
| Licence / models | FYP (non-commercial), so buffalo_s is OK. I download `buffalo_s.zip` (InsightFace GitHub release v0.7) and take `det_500m.onnx` + `w600k_mbf.onnx` (~16 MB). |

**Defaults I chose; please review:**
- Event starts after **2 consecutive** radar "moving" samples (~250 ms) to filter radar noise.
- Match threshold **0.40** cosine, with an adjustable slider.
- Disarming during an event ends the event cleanly.
- Clips are **not** sent over MQTT (too large); only the STRANGER photo and a small end-of-event summary are sent.

## Implementation

### 1. Dependencies, permissions, assets
- **`gradle/libs.versions.toml` + `app/build.gradle.kts`**:
  - add `com.microsoft.onnxruntime:onnxruntime-android` (latest stable 1.2x)
  - add `androidx.camera:camera-video` at the same `cameraX` version (1.6.1)
  - add `"onnx"` to `androidResources.noCompress`
- **Assets**: `app/src/main/assets/face/det_500m.onnx` and `app/src/main/assets/face/w600k_mbf.onnx`, loaded via `AssetModelLoader.loadBytes` (`perception/AssetModelLoader.kt`).
- **`AndroidManifest.xml`**:
  - permissions: `RECORD_AUDIO`, `FOREGROUND_SERVICE_MICROPHONE`, `USE_FULL_SCREEN_INTENT`, `VIBRATE`
  - `CameraCaptureService` gets `foregroundServiceType="camera|microphone"`. Android 14+ needs the microphone type for background audio.
  - declare `IntrusionAlertActivity` with `showWhenLocked`, `turnScreenOn`, `excludeFromRecents` and `singleTask`
- **Microphone permission**: requested when the user arms, alongside the camera permission check.

### 2. Face pipeline: package `com.rewatt.app.security`
- **`ScrfdFaceDetector.kt`**: letterbox to 640×640, RGB, `(x-127.5)/128`. Decode strides 8/16/32 × 2 anchors (score, box, 5 keypoints). Score ≥ 0.5, NMS 0.4, drop faces < 40 px. Map coordinates back to the full-resolution bitmap.
- **`FaceAligner.kt`**: Umeyama similarity transform from the 5 keypoints to the ArcFace 112×112 template, using `Matrix` + `Canvas`.
- **`MobileFaceNetEmbedder.kt`**: 1×3×112×112 RGB `(x-127.5)/127.5` → 512-d embedding, L2-normalised.
- **`FaceRecognizer.kt`**: `analyze(bitmap)` returns one of `NoFace` / `AllFamily(scores)` / `Stranger(faces, bestScore, annotatedBitmap)`. ORT sessions are lazy and closed with the service.

### 3. Storage (file-based, no Room change)
Room uses `fallbackToDestructiveMigration` (`data/ReWattDatabase.kt:23`), so security data lives in files under `filesDir/security/`.

**`FamilyFaceStore.kt`** (`family/`):
- A 512-float embedding plus a 112² JPEG thumbnail per sample, labelled `"FAMILY"`.
- Exposes a `StateFlow` plus `add`, `delete` and `clear`.

**`SecurityEventStore.kt`** (`events/<eventId>/`):
- **Files:** `clip_1.mp4`, `clip_2.mp4`, … (one per 5-min part), `photo_<n>.jpg` (face-check photos; annotated where faces were found), and `event.json`.
- **`event.json` fields:** id, start, end, result (`FAMILY` / `NO_FACE` / `STRANGER`), bestScore, torchUsed, alarmDismissedAt, mqttSent.
- **Retention:** at each event end and on app start, delete the oldest events while total size > 2 GB or age > 7 days. The active event is never deleted.

**`SecuritySettings.kt`** (SharedPreferences): `armed` and `matchThreshold`.

**`SecurityStateHolder.kt`**:
- A `StateFlow<SecurityUiState>` holding armed, eventPhase (IDLE/MOTION/GRACE), activeEventId, recording, torchOn, lastCheck, alarmActive.
- Follows the pattern of `perception/PerceptionStateHolder.kt`.

Register all of these in `core/AppGraph.kt`.

### 4. Camera: one shared session in `CameraCaptureController`
This is the key change that protects headcount and illuminance (`perception/CameraCaptureController.kt`).
- **Use cases:** manage the set of bound use cases explicitly instead of `unbindAll()` + bind one:
  - `ImageCapture` (always present when the camera is bound)
  - `VideoCapture<Recorder>` (only during a CCTV event): `Quality.FHD` with HD fallback, `setTargetFrameRate(Range(30,30))`
  - No `Preview` (headless in the service)
- **Rebinding:** a single helper `rebind(wantVideo: Boolean)` binds `DEFAULT_BACK_CAMERA` with the right combination. It keeps the returned `Camera` for torch control (`enableTorch`) and re-checks `cameraProvider.isBound(...)` before each capture. That also fixes the case where `SetupGuideDialog`/registration called `unbindAll()`.
- **Two capture purposes:**
  - `captureOccupancy()`: today's path, completely unchanged. It produces EXIF EV → `onFramePreview`, then YOLO → `onFrameAnalyzed` → `PerceptionStateHolder` + `TelemetryRepository`. If the torch is on: torch off → 400 ms → capture → restore torch.
  - `captureForSecurity(callback)`: the decoded and rotated bitmap plus its EV reading go **only** to the callback. No YOLO, and occupancy state is never touched.
  - Both share one serial queue, so neither request is dropped. Today a request arriving while `captureInFlight` is set is silently dropped; that changes to a queue.
- **Release rule:** unbind everything when no event is active and no occupancy capture is in flight. The camera turns on only for motion or the 10-min photo.
- **Video API:** `startRecording(file, withAudio)`, `stopRecording()`, `setTorch(on)`.

### 5. Event engine: `security/IntrusionMonitor.kt` (runs inside `CameraCaptureService`)
In the old project this logic lived in a Composable, so it only worked with the screen on. Here it runs in the foreground service, so it works with the screen off and the app closed.
- **Input:** `appGraph.telemetryRepository.telemetryState` (~8 Hz). Moving = `radarState` 1 or 3, `movingEnergy >= 10`, distance 1..600 cm. Extract this rule from `telemetry/OccupancyThreshold.kt:52-55` into `RadarTelemetry.isMovingTarget()` and reuse it in both places.
- **State machine** (only while Armed):
  - **IDLE → MOTION** after 2 consecutive moving samples:
    1. Create the event and take a partial wake lock.
    2. Bind camera (image + video).
    3. Take face-check photo #1. Its EV decides whether it's dark.
    4. Start recording `clip_1.mp4` with audio.
    5. If dark: torch on, then re-request after 300 ms.
    6. TTS once.
    7. Start the face-check loop.
  - **Face-check loop:** every ~2 s, `captureForSecurity` → `FaceRecognizer.analyze` → save the photo. STRANGER → `IntrusionAlarm.trigger` and stop the loop. AllFamily → mark the event FAMILY and stop the loop. NoFace → keep trying.
  - **5-min parts:** at 5 min, stop the part and start `clip_<n+1>.mp4`.
  - **MOTION → GRACE:** when no moving sample arrives, start a 5 s timer (only if one isn't already running). Movement → cancel the timer, back to MOTION.
  - **GRACE → IDLE** after 5 s without movement:
    1. Stop recording and turn the torch off.
    2. Finalise `event.json`; result = STRANGER, else FAMILY, else NO_FACE.
    3. Publish the MQTT event summary.
    4. Run retention, release the camera and the wake lock.
- **Disarm** mid-event: same teardown immediately (like the old STOP RECORDING → `resetMotion()`).
- **Service changes** (`perception/CameraCaptureService.kt`):
  - New actions `ACTION_ARM`, `ACTION_DISARM`, `ACTION_STOP_PERCEPTION`, `ACTION_RELEASE_CAMERA`, `ACTION_RESUME_CAMERA` and `ACTION_DISMISS_ALARM`.
  - Track `perceptionActive` and `armed` independently; the service stops only when both are off.
  - `PerceptionViewModel.stopPerception()` sends `ACTION_STOP_PERCEPTION`, so stopping occupancy doesn't disarm CCTV.
  - On a START_STICKY restart, restore `armed` from settings. If Android refuses camera or mic access from the background, show a "Open ReWatt to resume CCTV" notification.
  - Fix the existing no-op `ACTION_CAPTURE_NOW`-before-start.

### 6. Alarm and red alert
- **`security/IntrusionAlarm.kt`**:
  - Saves the STRANGER photo to the event.
  - Publishes to MQTT `rewatt/sensors/<roomId>/intrusion` with `{eventId, nodeId, roomId, timestamp, faceCount, bestScore, imageBase64}` (JPEG q70, longest side 960 px).
  - Posts a full-screen-intent notification.
  - Plays the siren: `AudioTrack` with `USAGE_ALARM` and a synthesised 600↔1400 Hz sweep (no asset needed). `STREAM_ALARM` is saved, then set to max.
  - Vibrates with a repeating waveform and strobes the torch every 250 ms (restored to the event torch state afterwards).
  - `dismiss()` stops everything, restores the volume and cancels the notification. The event and recording continue.
- **`security/IntrusionNotifier.kt`**: channel "Intrusion alerts", `IMPORTANCE_HIGH`, `CATEGORY_ALARM`, `setFullScreenIntent` → `IntrusionAlertActivity`, with a Dismiss action → `ACTION_DISMISS_ALARM`.
- **`security/IntrusionAlertActivity.kt`** (Compose):
  - `setShowWhenLocked`/`setTurnScreenOn` with the legacy flags for API 26, plus `FLAG_KEEP_SCREEN_ON`.
  - A brief `ACQUIRE_CAUSES_WAKEUP` wake lock, as in the old project.
  - Red screen: "INTRUDER ALERT", annotated photo, time/room/score, big **DISMISS** button.
- **`security/DeterrentVoice.kt`**: wraps `TextToSpeech`, initialised when Armed, and speaks once per event.
- **`data/MqttSyncRepository.kt`**: add `publishJson(topic, payload)`; `publishTelemetryJson` becomes a wrapper. Add the event summary topic `rewatt/sensors/<roomId>/security/event` with `{eventId, start, end, result, bestScore}`. Skipped in demo mode.

### 7. UI
- **`ui/sensor/SensorScreen.kt`**: new `StitchTab.SECURITY` (icon `Icons.Outlined.Shield`), wired from `MainActivity.kt` like the other tabs.
- **`ui/security/SecurityTab.kt`**, top to bottom:
  - **Arm/Disarm card:** a big switch. It's disabled until at least 1 FAMILY face is registered, and warns if radar USB is disconnected.
  - **Live status:** IDLE / MOTION / GRACE, Recording ●, Torch, and the last face check (e.g. "FAMILY 0.62", "No face", "STRANGER 0.18").
  - **FAMILY faces:** thumbnails, **Register FAMILY face**, delete per sample, **Clear all**.
  - **Settings and tests:** match strictness slider and a **Test alarm** button.
  - **Storage line:** e.g. "1.2 GB used of 2 GB".
  - **Event history:** list of events with time, duration and result chip. Tap one to open the detail view: clip playback via `VideoView` in `AndroidView` (no new dependency), its photos and a delete button.
  - **Permission warnings with fix buttons:**
    - full-screen intent on Android 14+ (`canUseFullScreenIntent()`)
    - camera and microphone
    - a Xiaomi/HyperOS hint: "Show on lock screen" and "Display pop-up windows while running in background"
- **`ui/security/FaceRegistrationScreen.kt`**:
  - Full-screen dialog with CameraX `Preview` + `ImageCapture` on `DEFAULT_FRONT_CAMERA`, following `ui/SetupGuideDialog.kt:642-719`.
  - Sends `ACTION_RELEASE_CAMERA` on open and `ACTION_RESUME_CAMERA` on close. It's blocked while an event is active.
  - 5 guided shots. Each must contain exactly one face, otherwise it asks to retake. Embeddings are saved as FAMILY.
- **`ui/security/SecurityViewModel.kt`** plus a factory, following the `SensorViewModelFactory` pattern.
- **Strings**: new text goes in both `res/values/strings.xml` and `res/values-ms/strings.xml`.

### 8. Docs
README CCTV section:
- setup steps
- the buffalo_s **non-commercial licence** and the model URL
- limitations:
  - after a reboot or a forced kill, open the app to resume (Android blocks camera/mic services from starting in the background)
  - night quality depends on the torch and room light

## Verification
1. **Build and install**: `gradlew assembleDebug` (3 GB heap override), then `adb install -r` on the 23078PND5G phone. Logcat shows the ORT input/output shapes and inference time (< 1 s per photo).
2. **Registration**: 5 shots of person A → 5 thumbnails. A photo with no face or two faces is rejected.
3. **Event timing**:
   - Walk past while Armed → event starts within ~1 s and TTS plays once.
   - Pause < 5 s → same event.
   - Leave → the event closes ~5 s later with one clip and its photos, and the camera-in-use dot disappears.
4. **Recognition**:
   - Person A → FAMILY, no alarm.
   - Stranger or printed photo → alarm within ~2–4 s.
   - Back turned the whole time → NO_FACE, no alarm.
5. **Alarm with the screen off**: lock the phone and set it to silent. A stranger should trigger:
   - the screen wakes with the red alert over the lock screen
   - the siren at max volume, vibration and torch strobe
   - Dismiss stops the alarm and restores the volume; recording continues until the event ends
6. **Headcount and illuminance unchanged**:
   - In a dark room, trigger an event (torch on) and force the occupancy capture (Diagnostics → Test Capture) → the EV reading matches a no-torch capture (within ~0.3 EV).
   - Headcount comes only from occupancy photos.
   - Stopping perception keeps the system Armed.
7. **Clip limits**: hold motion > 5 min → `clip_1.mp4` + `clip_2.mp4`; both play in Event history, with audio.
8. **Retention**: temporarily set the cap to 50 MB → the oldest events are deleted and the active event is kept.
9. **MQTT**: subscribe to `rewatt/sensors/Room1/#` → an `intrusion` message with the photo, and a `security/event` summary at event end. Demo mode sends nothing.
10. **Edge cases**:
    - Unplug the radar mid-event → the event ends after grace with a warning.
    - Open registration while Armed → the back camera works again afterwards.
    - Disarm mid-event → clean teardown.
