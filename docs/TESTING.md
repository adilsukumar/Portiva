# Software testing

Tests were run on 16 October 2026. The results apply to software and artificial inputs. No Redmi, camera board, loaded trolley or real house mission has been tested.

## Portable core

Run `python implementation/check_core.py` from the repository root. It compiles shared Java code and C++ checks, then runs map, calibration, camera-protocol and independent rectangle geometry tests. It does not need an Android SDK, model or APK.

The latest run passed 4,974 mission-demo assertions, 168 Java rectangle checks, 15 coordinator checks and 50 real localhost HTTP/inbox checks. The older navigation/following and C++ suites also passed. Counts include per-step output checks and are not delivery success rates.

An independent native OpenCV check agreed with Java on 1,000 random body poses and 1,558 samples along a 162-state route. The artificial opening was 558.8 mm wide, with a 390 by 410 mm body and 55 mm padding per side. This does not establish real house accuracy or loaded stability.

## Android emulator

The final debug APK was built, signature checked, installed and tested on a dedicated x86-64 emulator. Its SHA256 is `85b4474c3b72424ced97dafd8407a1307eb12ab889611423149ab881990c590e`.

The public photo produced 3 person detections. First inference took 1250 ms. Warm median was 1248 ms. This exceeds the preview's 350 ms frame limit; do not interpret it as a real-time following pass. These are emulator times, not Redmi times.

The procedural camera pose fixture gave about 9.84 mm position error. The virtual mission delivered and returned using the shared rectangle code. Lost-identity fault holds passed. All physical output remained disabled.

The screenshot still showed an Android **System UI isn't responding** dialog. Functional assertions passed, but clean and smooth UI behaviour remains unverified. No polished app screenshot is claimed.

## Firmware

Uno and AI-Thinker ESP32-CAM source compiled. The renamed camera build used 1,041,521 bytes of flash and 59,232 bytes of RAM. Nothing was flashed to a physical board. The first sandbox compile could not start its ESP32 compiler because of a Windows DLL relocation error; compiling with normal host access succeeded.

## Limits and next measurements

Simulation assumes identity, location, range, braking and motor response. Those assumptions must be replaced with measured adapters and tested evidence before real motion. The current appearance tracker can mistake a similarly dressed replacement person. The localizer needs surveyed natural features and is not automatic SLAM.

The inbox now accepts requests into the same coordinator in the offline demo. It cannot bypass missing evidence or enable physical motion. STOP clears older queued requests, but an HTTP request is not an independent physical emergency stop.

The full purchasing list remains unqualified. The [camera development stage](START-BUILDING.md) is the recommended first experiment. Physical results must be recorded separately from these software fixtures.

[Machine-readable software evidence](evidence/software-checks.json)
