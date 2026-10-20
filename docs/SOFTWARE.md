# Portiva software guide

Portiva is a development bench for a robot that follows a selected person, carries items and delivers to registered rooms. The current app and firmware cannot move physical motors. A physical mission requires measured perception and commissioning that are not complete.

## Run the core software

Use Python 3.11+, JDK 17+ and a C++17 compiler. Set `JAVA_HOME` if Java is not discoverable. Put `g++` on PATH, or set `CXX` to its executable path.

```bash
python -m pip install -r implementation/requirements-core.txt
python implementation/check_core.py
python portiva.py demo
python portiva.py plan
```

The first command installs host test libraries. The core test command needs no Android SDK, camera, model or robot. The demo executes the shared Java mission model. The map command uses a synthetic example and writes a reference route. Neither enables real wheels.

## Build the Android APK

Install Android SDK platform 35 and build-tools 35.0.0. Set `ANDROID_SDK_ROOT` and `JAVA_HOME` if needed. The build script can also find Android Studio's bundled JBR on Windows.

```bash
python -m pip install -r implementation/requirements-model-export.txt
python implementation/fetch_dependencies.py --export-model
python implementation/test_detector.py
python portiva.py build
```

The downloader uses official public sources and the hashes in `implementation/dependencies.lock.json`. Model export requires the separately installed Ultralytics requirements. It is an explicit download step, not an automatic side effect of importing the tools. Review [dependency notices](../THIRD_PARTY_NOTICES.md).

The output is `implementation/Portiva-Bench.apk`. It is a local debug-signed Android 8/API 26+ app. Generated APKs and debug keys are excluded from Git. The package name `in.rollingtable.prototype` is retained for compatibility.

## Use the Android offline demo

Open **Portiva Bench**, then **Open offline mission demo**. The room, person, location, sensors and motor response are artificial. The demo runs ten times faster than real time.

Press **Follow person**. Press **Load / stop** and wait for rest. Choose **Send kitchen** or **Send pooja**. After arrival, press **Unloaded: return**. The model returns to its recorded departure position. **STOP** stops the mission. **Reset** creates a new artificial world.

The fault buttons simulate an obstacle, identity loss and stale pose. Clearing a fault does not resume travel. A deliberate **Resume** is required. All physical PWM stays zero.

## Request from another phone

Start the **recipient request bench** on the processing phone. It displays a local page address and a temporary pairing token. A second phone on the same private network can open that page and queue a request. No recipient APK is required.

The page supports FOLLOW, LOAD, DELIVER, RETURN and STOP. The current example rooms are kitchen and pooja. Room names are logical destinations, not radio-derived phone coordinates. The server keeps at most 32 queued requests and a bounded duplicate history. STOP clears older requests. Stopping the service clears its inbox and token.

The main app can review a message without executing it. The offline demo can **Apply queued request to simulation**. Requests older than five seconds are rejected by the coordinator. Receiving a request does not commission a physical robot. HTTP STOP does not replace an independent physical stop circuit.

## Prepare a camera bench

The current architecture uses the Android phone's own rear camera. In Portiva Bench, choose **Open phone's own camera bench (new design)**, then **Start phone camera**. Grant camera permission when requested. Frames are processed locally in memory. The screen reports detections and inference/request-to-result timing. Stop the camera or leave the activity to close capture. This bench has no robot connection and cannot move wheels. Raw image orientation, calibration, device speed and thermal behaviour remain physical tests.

The planned HC05 connection to the Uno is not implemented. Mounting the phone does not enable autonomous following or delivery. The following ESP32 setup is retained for the older camera research and is not required for the current design.

Use an actual AI-Thinker-compatible ESP32-CAM with OV2640 and working PSRAM, a matching programmer and documented regulated power. Verify the board's pin map before connecting it. The initial camera-only bench needs no drive hardware.

```bash
python implementation/configure_camera.py
```

The command creates private `implementation/firmware/camera/secrets.h` and `implementation/config/camera-private.json`. Existing settings are preserved unless `--force` is supplied deliberately. Password and token values are not printed. Keep both files private.

Open `implementation/firmware/camera/camera.ino` in Arduino IDE, with the full `firmware` folder structure intact. Install the appropriate Espressif Arduino core and select the AI-Thinker target only after matching the actual board. Source compilation used ESP32 core 3.3.8. Nothing is automatically flashed.

The camera provides authenticated `/health`, `/capture`, read-only `/telemetry` and `/stop` endpoints. There is no steering or arming endpoint. Join the board's local Wi-Fi and run:

```bash
python portiva.py camera --url http://192.168.4.1 --frames 20
```

Enter the pairing token at the hidden prompt. The tool checks authentication, sensor identity, PSRAM and decodable VGA frames. It records an actual-camera result only when real hardware is connected. It does not establish phone inference speed, personal identity, distance or autonomous navigation.

## Uno sensor bench

Open `implementation/firmware/base/base.ino` in Arduino IDE. Keep `ControlCore.h`, `SensorCore.h`, `SensorAdapters.h` and `TelemetryCore.h` together. The compiled target is an Arduino Uno using AVR core 1.8.7. Motor power stays disconnected during sensor setup. `ALLOW_MOTION` is false.

The source reports raw range, encoder, gyro, battery ADC and button events. A pulse count does not prove direction or distance. Follow the actual board voltage specifications; the final driver, level conversion, stop chain and wiring have not been commissioned. The source pin assignments are development assignments, not an approved finished wiring harness.

## Camera and map calibration

`implementation/config/house-calibration.template.json` and `Portiva-map-template.json` are empty survey templates. They contain no private house map or measurements. Camera fitting can use measured corners on an existing flat textured surface; no installed printed markers are proposed.

`calibrate_natural_surface.py` fits a candidate camera model. `map_tools.py` validates a bounded occupancy grid and registered destinations. Neither establishes live pose uncertainty or enables motor movement. Synthetic calibration and map inputs must not be presented as physical survey evidence.

Read [architecture](ARCHITECTURE.md), [test limits](TESTING.md) and the [first hardware stage](START-BUILDING.md) before commissioning any hardware.
