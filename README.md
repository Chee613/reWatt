# REWATT

REWATT is a Kotlin Android sensor-hub app for upcycled phones. V1 accepts newline-delimited ESP32-C6 / LD2412 radar telemetry and SHT30 environment readings over USB OTG / USB CDC serial, parses it, stores valid readings locally with Room, syncs perception summaries to Firestore, and displays live room presence telemetry in a Jetpack Compose dashboard.

## Sensor Payload

The ESP32-C6 firmware should emit one packet per line at `115200` baud:

```text
LD2412:PRES=1,MOV=42,STA=85,DIST=120|SHT30:T=26.5,H=58.2
```

Fields map to binary presence, moving energy, stationary energy, target distance in centimeters, SHT30 temperature Celsius, and SHT30 humidity percent. The legacy `RADAR:` prefix and radar-only packets are still accepted, but `LD2412:` plus `SHT30:` is preferred. Older AQI, eCO2, and TVOC packets are rejected by the current parser.

## Firebase

Add `app/google-services.json` before building a Firebase-enabled APK. When configured, the app writes YOLO people count, top three YAMNet activity labels, latest SHT30 readings, and Gemini activity analysis to `rooms/default-room/nodes/{androidId}` and appends the same summary to `rooms/default-room/telemetry/{timestamp-androidId}`.

Setup steps:

1. Open the Firebase console and create or select a project.
2. Add an Android app with package name `com.rewatt.app`.
3. Download `google-services.json`.
4. Place it at `app/google-services.json`.
5. Enable Cloud Firestore. Use test mode only for prototype hardware testing.
6. Build with `.\gradlew.bat --no-daemon assembleDebug`.

## Gemini Activity Analysis

For the prototype, Gemini is called directly from the Android app using the REST API and the stable model id `gemini-3.5-flash`. This avoids the Firebase Functions / Blaze requirement, but the API key can be extracted from the installed APK. Use this path only for testing and rotate the key before wider sharing.

Setup steps:

1. Add the Gemini API key to the project-level `local.properties` file:
   ```powershell
   GEMINI_API_KEY=your_new_gemini_api_key_here
   ```
2. Rebuild and reinstall the app after changing the key:
   ```powershell
   .\gradlew.bat --no-daemon assembleDebug
   ```

When top-three YAMNet labels are available, the app sends people count, activity labels, temperature, and humidity to Gemini. The app displays the returned activity and merges it back into Firestore as `activityAnalysis`.

## Perception

The dashboard also has a manual Perception panel. Tap **Start** on the phone to grant camera/microphone access, show the live camera preview, capture one YOLO frame every 10 minutes, and record a 15-second YAMNet audio window every 10 minutes. Only the top three human-activity YAMNet labels are displayed and synced.

Lecture hall filtering:

- YOLO occupancy evidence: `person`
- YOLO context objects: `backpack`, `handbag`, `bottle`, `cup`, `chair`, `bench`, `dining table`, `tv`, `laptop`, `mouse`, `remote`, `keyboard`, `cell phone`, `book`, `clock`
- YAMNet strong human activity: speech, conversation, whispering, laughter, singing, humming, cough, throat clearing, sneeze, footsteps, hands, clapping, cheering, applause, crowd/chatter, typing, computer keyboard, writing
- YAMNet weak support only: `Keys jangling`, `Knock`, `Tap`, `Clicking`

Bundled model assets:

- `yolo26n_int8.tflite`: Ultralytics COCO detector, with `person` as class `0`
- `yamnet.tflite`: raw-audio YAMNet model for 16 kHz mono microphone windows
- `yamnet_head_int8.tflite`: retained for future embedding-head experiments

## Build

Set `ANDROID_HOME` to your Android SDK if it is not already configured, then run:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.
