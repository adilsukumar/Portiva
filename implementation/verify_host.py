"""Re-run executable host checks; summarize virtual-device logs without inventing hardware evidence."""
from pathlib import Path
import subprocess, json, hashlib, re, sys
from toolchain import java_tools,cpp_compiler,exe

ROOT = Path(__file__).resolve().parent
JAVA = java_tools()
SRC = ROOT/'android/src/in/rollingtable/prototype'

def run(*args):
    p = subprocess.run([str(a) for a in args], cwd=ROOT.parent, capture_output=True, text=True)
    if p.returncode:
        raise RuntimeError(p.stdout+p.stderr)
    print(p.stdout.strip())
    return p.stdout.strip()

def main():
    classes=ROOT/'test-java';classes.mkdir(exist_ok=True)
    run(JAVA/exe('javac'),'-d',classes,SRC/'TargetTracker.java',SRC/'GridPlanner.java',SRC/'MissionController.java',SRC/'NavigationEngine.java',SRC/'PoseTransform.java',SRC/'BaseTelemetry.java',SRC/'FollowController.java',SRC/'MissionDemo.java',SRC/'RectanglePlanner.java',SRC/'RectangleNavigation.java',SRC/'MissionCoordinator.java',ROOT/'tests/RectangleChecks.java',ROOT/'tests/CoordinatorChecks.java',SRC/'RequestInbox.java',SRC/'RecipientServer.java',ROOT/'tests/MissionDemoRunner.java',ROOT/'tests/RecipientChecks.java',
        ROOT/'tests/TrackerChecks.java',ROOT/'tests/NavigationChecks.java',ROOT/'tests/IntegratedNavigationChecks.java',ROOT/'tests/PoseTransformChecks.java',ROOT/'tests/TelemetryChecks.java',ROOT/'tests/FollowControllerChecks.java')
    tracker=run(JAVA/exe('java'),'-cp',classes,'TrackerChecks')
    navigation=run(JAVA/exe('java'),'-cp',classes,'NavigationChecks')
    integrated=run(JAVA/exe('java'),'-cp',classes,'IntegratedNavigationChecks')
    transform=run(JAVA/exe('java'),'-cp',classes,'PoseTransformChecks')
    android_telemetry=run(JAVA/exe('java'),'-cp',classes,'TelemetryChecks')
    following=run(JAVA/exe('java'),'-cp',classes,'FollowControllerChecks')
    mission_demo=run(JAVA/exe('java'),'-cp',classes,'MissionDemoRunner')
    recipient=run(JAVA/exe('java'),'-cp',classes,'RecipientChecks')
    rectangle=run(JAVA/exe('java'),'-cp',classes,'RectangleChecks')
    coordinator=run(JAVA/exe('java'),'-cp',classes,'CoordinatorChecks')
    run(sys.executable,ROOT/'test_rectangle_java.py')
    control_exe=ROOT/'tests/control_core_checks.exe'
    run(cpp_compiler(),'-std=c++17','-Wall','-Wextra','-Werror',
        ROOT/'tests/control_core_checks.cpp','-o',control_exe)
    controller=run(control_exe)
    sensor_exe=ROOT/'tests/sensor_core_checks.exe'
    run(cpp_compiler(),'-std=c++17','-Wall','-Wextra','-Werror',ROOT/'tests/sensor_core_checks.cpp','-o',sensor_exe)
    sensors=run(sensor_exe)
    telemetry_exe=ROOT/'tests/telemetry_core_checks.exe'
    run(cpp_compiler(),'-std=c++17','-Wall','-Wextra','-Werror',ROOT/'tests/telemetry_core_checks.cpp','-o',telemetry_exe)
    telemetry=run(telemetry_exe)
    run(sys.executable,ROOT/'test_detector.py')
    run(sys.executable,ROOT/'test_natural_calibration.py')
    run(sys.executable,ROOT/'test_camera_bench.py')
    run(sys.executable,ROOT/'test_map_tools.py')
    run(sys.executable,ROOT/'rectangular_base_check.py')
    apk=ROOT/'Portiva-Bench.apk'
    apk_hash=hashlib.sha256(apk.read_bytes()).hexdigest()
    tested_build=ROOT/'Android-tested-build.json'
    tested_hash='3bac723a4b85610c2feca49b58232660f47ea6403aa5c68722a48bbc69c200e5'
    if tested_build.exists():
        tested=json.loads(tested_build.read_text(encoding='utf-8-sig'))
        if tested.get('instrumentation_passed'):tested_hash=tested['apk_sha256']
    native=(ROOT/'Android-instrumentation-output.txt').read_text(encoding='utf-8-sig')
    fields={k:v.strip() for k,v in re.findall(r'INSTRUMENTATION_RESULT: (\w+)=(.*)',native)}
    assert fields.get('result')=='passed' and fields.get('motion_enabled')=='false',native
    assert 'INSTRUMENTATION_CODE: -1' in native
    if 'procedural_pose_error_m' in fields:
        fields['raw_scope_label']=fields.get('scope','')
        fields['scope']='Android native ONNX, association, procedural ORB/PnP and reference controller checks; not Redmi, Grandma, house or motors'
    firmware={}
    for name in ['Uno','Camera']:
        log=ROOT/('Uno-sensors-build-output.txt' if name=='Uno' else 'Camera-health-build-output.txt')
        content=log.read_text(encoding='utf-8-sig') if log.exists() else ''
        firmware[name]={'compile_passed':'Sketch uses ' in content and 'Error during build' not in content,
                        'log':str(log),'flashed':False}
    report={'date':'2026-10-16','scope':'Executable software and procedural/virtual-device tests; no physical robot or Redmi tests',
            'host_checks':{'tracker':tracker,'navigation':navigation,'integrated_navigation':integrated,'camera_base_transform':transform,'controller':controller,'sensor_core':sensors,'telemetry_core':telemetry,'android_telemetry':android_telemetry,'follow_controller':following,'recipient':recipient,'rectangle_navigation':rectangle,'request_coordinator':coordinator},
            'android_virtual_device':fields,'firmware':firmware,
            'apk_sha256':apk_hash,
            'native_instrumentation_tested_apk_sha256':tested_hash,
            'current_apk_reinstalled_on_emulator':apk_hash==tested_hash,
            'model_sha256':hashlib.sha256((ROOT/'downloads/yolo11n.onnx').read_bytes()).hexdigest(),
            'real_phone_tested':False,'real_house_tested':False,'loaded_robot_tested':False,
            'natural_camera_calibration_test':json.loads((ROOT/'Natural-calibration-test-results.json').read_text(encoding='utf-8')),
            'camera_bench_tool_test':json.loads((ROOT/'Camera-bench-tool-test-results.json').read_text(encoding='utf-8')),
            'rectangle_base_test':json.loads((ROOT/'Rectangle-base-results.json').read_text(encoding='utf-8')),
            'mission_demo_test':json.loads((ROOT/'Portiva-mission-demo-results.json').read_text(encoding='utf-8')),
            'map_tool_test':json.loads((ROOT/'Portiva-map-tool-results.json').read_text(encoding='utf-8')),
            'java_rectangle_test':json.loads((ROOT/'Portiva-rectangle-java-results.json').read_text()),
            'motion_enabled':False,'purchase_ready':False}
    (ROOT/'Verification-results.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
    print('Wrote Verification-results.json. Hardware/purchase gates remain unresolved.')

if __name__=='__main__':main()
