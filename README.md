# reWatt — Upcycle Phones into AI Energy Sensors

> Saving energy while eliminating e-waste.

reWatt turns an old smartphone into an AI occupancy-and-comfort sensor for buildings (lecture
halls, faculties, offices). Instead of buying expensive smart switches, a retired phone watches a
room, counts people on-device, tracks temperature/humidity, predicts the room's daily routine, and
tells facility managers **when to turn off the lights/AC and when to pre-cool** — catching the empty
rooms that humans forget.

It's a **decision-support** tool, not full building automation: old buildings rarely have smart
switches, so reWatt focuses on accurate sensing, prediction, and proactive alerts that a worker can
act on immediately.

---

## The problem

- **~275 kWh / RM2,600 wasted every month per faculty** on empty, lit, air-conditioned rooms —
  multiplied across every campus.
- **Manual checks fail** — no one can memorise every room's schedule and walk every corridor.
- **Retrofitting is expensive** — smart switches cost RM2,000+ per room, impractical for
  budget-constrained schools.
- Meanwhile the average household has **2.3+ unused phones** (UN Global E-Waste 2024).

reWatt solves the energy problem *and* the e-waste problem with the same device.

---

## The reWatt module (RM39)

A small 3D-printed module clips onto an old phone over USB-C and adds the senses a camera lacks:

| Component | Role | Cost (RM) |
| --- | --- | --- |
| ESP32-C6 (XIAO) | Matter-ready microcontroller, talks to the phone over USB serial | 13.00 |
| HLK-LD2412 | 24 GHz mmWave radar — stationary + moving presence (9 m radius, ~150°) | 9.00 |
| GY-SHT30 | Temperature & humidity (comfort) | 3.00 |
| Charging/data splitter | Keeps the phone powered while sending data | 7.50 |
| 180° U-shape Type-C adapter | Tidy mount | 4.00 |
| USB C-to-C adapter | Connection | 2.10 |
| 3D-printed enclosure (per unit) | Housing | 0.40 |
| **Total** | | **RM 39.00** |

The phone supplies the **free CPU/GPU, camera, and connectivity**; the module supplies low-power
occupancy + comfort sensing.

---

## How it works

```
mmWave (LD2412) presence  ──►  triggers camera capture only when a room is occupied
        │                                   │
        ▼                                   ▼
  SHT30 temp/humidity            Phone camera frame  ──►  YOLO26n (on-device) counts people
        │                                   │
        └──────────────┬────────────────────┘
                       ▼
        Firebase (per-room sync)  +  Prophet occupancy forecast  +  Maps Weather API
                       ▼
        Gemini turns the numbers into plain actions ("turn off all systems", "pre-cool to 23°C")
                       ▼
        Admin dashboard + proactive notifications / full-screen "ghost room" alerts
```

**Why mmWave first:** PIR misses people sitting still and can't count; thermal cameras merge people
into heat blobs. The LD2412 reliably detects stationary *and* moving occupants and is cheap to run
(~0.26 W), so the heavier YOLO model (~3.4 W) only wakes when someone is actually present.

**Privacy:** YOLO runs **100% on-device** — no images are saved or uploaded, only the headcount.

---

## App modes

reWatt is a single Android app with two roles (chosen on first launch):

### Sensor mode (the mounted phone)
- Magnetic-mounts to a wall/whiteboard; plug-and-play, no rewiring.
- Reads LD2412 + SHT30 over USB serial (foreground `UsbSentinelService`).
- Captures one camera frame on a 10-minute exact-alarm loop (`CameraCaptureService`), runs YOLO26n,
  derives a binary "lights on/off" from photo exposure (EV), and syncs a summary to Firestore.
- Shows live telemetry + the latest capture; keeps running with the screen off.

### Admin mode (the manager's phone)
- **LIVE** — per-room occupancy, AC/lights state, indoor temp/humidity, outdoor weather + Δ vs
  indoor, session-ends, plus a Gemini AC suggestion and yesterday's wasted-energy estimate.
- **Analytic** — Action timeline (what to do and when, up to 2 days ahead), class schedule, weekly
  occupancy heatmap, and per-week history.
- **Manage** — live sensor-fleet health across rooms (online/offline, battery), synced from Firestore
  across all admin phones on the same account.
- **Alerts** — a persistent "NOW action" notification, and a full-screen **ghost-room alert**
  ("lights/AC still on, no people detected") that escalates when a turn-off action is left undone.

---

## Intelligence

- **YOLO26n (custom, on-device)** — lightweight occupancy counter trained on real lecture halls /
  meeting rooms / multiple angles / rear-views (~89% accuracy). Exact headcount matters because a
  room with 10 students needs far less cooling than one with 200.
- **mmWave gating** — LD2412 confirms presence; YOLO only counts when needed (saves power).
- **Prophet forecasting** — predicts occupancy as a *calendar pattern* (morning arrivals, lunch dips,
  lecture peaks, weekend slowdowns), recognises repeated class timings, isolates public-holiday
  anomalies, and tolerates missing sensor logs.
- **Gemini (`gemini-3.1-flash-lite`)** — converts raw sensor + weather + occupancy into human actions:
  activity analysis, AC energy advice, and the next-day wasted-energy (kWh / RM) summary.
- **THI comfort** — a Temperature-Humidity Index (`0.8·T + RH·T/500`) flags rooms that are
  over-cooled (too cold while occupied) so the AC setpoint can be raised.

---

## Sensor payload

The ESP32-C6 firmware emits **one JSON object per line at 115200 baud**:

```json
{"radar":{"state":1,"mov_d":120,"mov_e":42,"stat_d":0,"stat_e":85},"temp":26.5,"hum":58.2}
```

- `radar.state` — 0 = empty, 1/3 = moving present, 2 = stationary present (`state > 0` ⇒ presence).
- `mov_d` / `stat_d` — moving / stationary target distance (cm); `mov_e` / `stat_e` — energy levels.
- `temp` / `hum` — SHT30 temperature (°C) and relative humidity (%).

Parsing lives in `telemetry/UsbDataParser.kt`; malformed lines are counted and dropped.

---

## Security camera (CCTV mode)

The **SECURITY** tab turns the sensor phone into an intrusion camera. It runs alongside the 10-minute occupancy capture without changing headcount or illuminance results.

**Lane 2 flow, while Armed:**

```
radar motion (2 moving samples) → event starts → photo every ~2 s
  YOLO: no person            → ignore, keep checking (nothing recorded)
  person, no face            → record video (kept as evidence) + "You are being recorded" once (no siren), keep checking
  face → InsightFace buffalo_s vs "FAMILY"
        ✓ FAMILY             → silent: stop and delete the clip
        ✗ unknown / masked   → torch strobe + "You are being recorded" + siren + red full-screen alert + MQTT
presence gone for 5 s (grace) → event ends, camera released
```

- **Setup:**
  1. Open **SECURITY → Register FAMILY face** and take 5 front-camera shots per person.
  2. Turn on **Armed**.
  3. Allow full-screen alerts. On Xiaomi/HyperOS, also allow *Show on Lock screen* and *Display pop-up windows while running in the background*.
- **Evidence:** saved in app storage under `files/security/events/<yyyyMMdd_HHmmss>/` as FHD 30 fps clips with audio (5-min parts), the face-check photos and an `event.json`. Events are deleted after 7 days or when storage passes 2 GB.
- **MQTT:**
  - `rewatt/sensors/<room>/intrusion`: the intruder photo (base64 JPEG) and match score
  - `rewatt/sensors/<room>/security/event`: an end-of-event summary
- **Models:** `app/src/main/assets/face/det_500m.onnx` and `w600k_mbf.onnx` come from InsightFace [`buffalo_s`](https://github.com/deepinsight/insightface/releases/download/v0.7/buffalo_s.zip) and run with ONNX Runtime.
  > **Licence:** the InsightFace pretrained models are for **non-commercial research only**. This is fine for the FYP, but a commercial release needs a licensed model.
- **Limitations:**
  - After a reboot or a force-stop, open the app once to resume CCTV. Android doesn't let a camera/microphone service start from the background.
  - At night, the face check relies on room light or the torch.

---

## Tech stack

- **Android** — Kotlin, Jetpack Compose + Material 3, AppCompat per-app locale (English + Bahasa
  Melayu). `minSdk 26` (Android 8.0), `target/compileSdk 36`, Java 17.
- **Sensing** — `usb-serial-for-android` (USB CDC), CameraX, TensorFlow Lite (YOLO26n).
- **Cloud** — Firebase Auth + Cloud Firestore (per-account room/node/telemetry sync), Google Maps
  Platform Weather API, Gemini REST API.
- **Background** — foreground services + exact `AlarmManager`, WorkManager (forecast / class-schedule /
  daily energy-waste workers).
- **Edge control** — ESP32-C6 is Matter-ready (ESP-Matter) for future direct device control.

---

## Project structure

```
app/src/main/java/com/rewatt/app/
├─ MainActivity.kt, ReWattApplication.kt
├─ core/            AppGraph (manual DI), LocaleManager
├─ data/            Repositories (Telemetry, FirebaseSync, Weather, Auth),
│                   Gemini analyzers, Room (TelemetryDao), WorkManager workers, models
├─ perception/      CameraCaptureService/Controller, YoloDetector + post-processing,
│                   ExposureValueCalculator, capture scheduling
├─ telemetry/       UsbDataParser, RadarTelemetry, TelemetryFreshness
├─ usb/             UsbSentinelService (LD2412/SHT30 over USB)
├─ notify/          Energy-alert (full-screen), NOW-action & scheduled-action notifications
└─ ui/              SetupGuideDialog, ModeSelectionDialog, auth/, dashboard/, theme/
app/src/main/assets/    yolo26n-1920.tflite  (bundled detector)
tools/                  Python helpers (forecast, data seeding, inspection)
```

---

## Getting started

**Prerequisites:** Android Studio / Android SDK, JDK 17, a device or emulator on **Android 8.0+**.

1. **API keys** — add to the project-level `local.properties` (they're injected as `BuildConfig`
   fields, so they never need to be committed):
   ```properties
   GEMINI_API_KEY=your_gemini_api_key
   MAPS_WEATHER_API_KEY=your_google_maps_weather_api_key
   ```
2. **Firebase** — create a Firebase project, add an Android app with package `com.rewatt.app`,
   download `google-services.json`, and place it at `app/google-services.json`. Enable Cloud
   Firestore and Authentication. (The Google Services plugin is only applied when this file exists.)
3. **Build & test:**
   ```powershell
   .\gradlew.bat testDebugUnitTest assembleDebug
   ```
   The debug APK lands at `app/build/outputs/apk/debug/app-debug.apk`.
4. **Install:**
   ```powershell
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
5. **Run** — sign in, then pick **Sensor mode** (mounted phone with the module) or **Admin mode**
   (manager dashboard). Sensor phones and admin phones on the **same account** sync through Firestore.

> Prototype Firestore rules should be locked down before any wider release — add proper Auth/App
> Check rules; don't ship open test-mode rules.

---

## Deployment (Sensor mode)

- **Requirements:** Android 8.0+, ~2 GB RAM (runs on 10+ year-old phones).
- **Permissions:** Camera + Location.
- **Critical:** turn **Battery Saver off** (Samsung Off mode) so the app keeps running with the
  screen off — the setup guide checks this and links straight to the setting.
- Magnetic-mount the phone facing the room; plug in the reWatt module; done.

---

## Roadmap

- **Q4 2026 — Smart Home Ecosystem:** integrate beyond the ESP-Matter SDK (Apple Home, Xiaomi Home).
- **Q2 2027 — Enterprise:** hardware at RM99 retail + RM10/month software subscription.
- **Q1 2028 — Global Expansion:** full multi-language support and regional adaptations.

---

## Impact

**People · Planet · Profit** — reWatt reduces CO₂ by cutting grid demand, keeps unused phones out of
landfills, and lowers electricity bills for institutions. Built for commercial buildings, educational
institutions, and smart homes.

---

Built for the UM Technothon · Universiti Malaya, Centre of Innovation and Enterprise.
