"""Portable software checks without an Android SDK, APK, model download or hardware."""
from pathlib import Path
import subprocess,sys,json
from toolchain import java_tools,cpp_compiler,exe
ROOT=Path(__file__).resolve().parent;SRC=ROOT/'android/src/in/rollingtable/prototype'

RESULTS=[]
def run(*args):
    p=subprocess.run([str(x) for x in args],cwd=ROOT.parent,text=True,capture_output=True)
    if p.stdout:print(p.stdout.strip(),flush=True)
    if p.returncode:raise RuntimeError(p.stdout+p.stderr)
    return p.stdout.strip()
def main():
    classes=ROOT/'test-java';classes.mkdir(exist_ok=True);java=java_tools()
    names=['TargetTracker','GridPlanner','MissionController','NavigationEngine','PoseTransform','BaseTelemetry','FollowController','RectanglePlanner','RectangleNavigation','MissionCoordinator','MissionDemo','RequestInbox','RecipientServer']
    tests=['TrackerChecks','NavigationChecks','IntegratedNavigationChecks','PoseTransformChecks','TelemetryChecks','FollowControllerChecks','MissionDemoRunner','RecipientChecks','RectangleChecks','CoordinatorChecks']
    run(java/exe('javac'),'--release','11','-d',classes,*[SRC/(x+'.java') for x in names],*[ROOT/'tests'/(x+'.java') for x in tests])
    for test in tests:RESULTS.append(dict(test=test,result=run(java/exe('java'),'-cp',classes,test)))
    for name in ['control_core_checks','sensor_core_checks','telemetry_core_checks']:
        output=ROOT/'tests'/exe(name);run(cpp_compiler(),'-std=c++17','-Wall','-Wextra','-Werror',ROOT/'tests'/(name+'.cpp'),'-o',output);RESULTS.append(dict(test=name,result=run(output)))
    for name in ['test_map_tools.py','test_natural_calibration.py','test_camera_bench.py','test_rectangle_java.py']:RESULTS.append(dict(test=name,result=run(sys.executable,ROOT/name)))
    (ROOT/'Portiva-core-checks.json').write_text(json.dumps(dict(passed=True,scope='Portable host software checks; no physical hardware',tests=RESULTS,motion_enabled=False),indent=2)+'\n')
    print('Core software checks passed. No physical robot or phone was tested.')
if __name__=='__main__':main()
