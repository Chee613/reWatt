# REWATT

REWATT is a Kotlin Android sensor-hub app for upcycled phones. V1 accepts newline-delimited ESP32-C6 / LD2410B radar telemetry over USB OTG / USB CDC serial, parses it, stores valid readings locally with Room, syncs the latest state to Firestore, and displays live room presence telemetry in a Jetpack Compose dashboard.

## Sensor Payload

The ESP32-C6 firmware should emit one packet per line at `115200` baud:

```text
RADAR:PRES=1,MOV=42,STA=85,DIST=120
```

Fields map to binary presence, moving energy, stationary energy, and target distance in centimeters. Older temperature, humidity, AQI, eCO2, and TVOC packets are rejected by the current parser.

## Firebase

Add `app/google-services.json` before building a Firebase-enabled APK. When configured, the app writes the latest node state to `rooms/default-room/nodes/{androidId}` and appends telemetry history to `rooms/default-room/telemetry/{timestamp-androidId}`.

Setup steps:

1. Open the Firebase console and create or select a project.
2. Add an Android app with package name `com.rewatt.app`.
3. Download `google-services.json`.
4. Place it at `app/google-services.json`.
5. Enable Cloud Firestore. Use test mode only for prototype hardware testing.
6. Build with `.\gradlew.bat --no-daemon assembleDebug`.

## Perception

The dashboard also has a manual Perception panel. Tap **Start** on the phone to grant camera/microphone access, show the live camera preview, display the latest YOLO-analyzed frame with bounding boxes, and show microphone capture level plus filtered YAMNet sound labels.

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
