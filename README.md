# REWATT

REWATT is a Kotlin Android sensor-hub app for upcycled phones. V1 accepts newline-delimited ESP32-C6 telemetry over USB OTG / USB CDC serial, parses it, stores valid readings locally with Room, and displays live room telemetry in a Jetpack Compose dashboard.

## Sensor Payload

The ESP32-C6 firmware should emit one packet per line at `115200` baud:

```text
ENV:T=26.5,H=58.2,AQI=1,TVOC=45,CO2=415|RADAR:MOV=42,STA=85,PRES=1
```

Fields map to temperature, humidity, air quality index, TVOC, eCO2, LD2412 moving energy, LD2412 stationary energy, and binary presence.

## Build

Set `ANDROID_HOME` to your Android SDK if it is not already configured, then run:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.
