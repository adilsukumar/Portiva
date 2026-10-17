"""Portiva development tools. No command enables physical motor movement."""
from pathlib import Path
import argparse
import importlib.util
import json
import subprocess
import sys

ROOT=Path(__file__).resolve().parent
IMPL=ROOT/'implementation'
sys.path.insert(0,str(IMPL))
from toolchain import java_tools,android_sdk,exe

def run(args):
    result=subprocess.run([str(x) for x in args],cwd=ROOT)
    if result.returncode:raise SystemExit(result.returncode)

def demo():
    JAVA=java_tools()
    src=IMPL/'android/src/in/rollingtable/prototype'
    run([JAVA/exe('javac'),'-d',IMPL/'test-java',*[src/(name+'.java') for name in ['MissionController','GridPlanner','NavigationEngine','FollowController','RectanglePlanner','RectangleNavigation','MissionCoordinator','RequestInbox','MissionDemo']],IMPL/'tests/MissionDemoRunner.java'])
    run([JAVA/exe('java'),'-cp',IMPL/'test-java','MissionDemoRunner'])

def doctor():
    try:JAVA=java_tools()
    except FileNotFoundError:JAVA=Path('not-installed-jdk')
    try:SDK=android_sdk()
    except FileNotFoundError:SDK=Path('not-installed-sdk')
    checks={name:path.exists() for name,path in {
        'python':Path(sys.executable),'java_compiler':JAVA/exe('javac'),'java_runtime':JAVA/exe('java'),
        'android_sdk':SDK/'platforms/android-35/android.jar',
        'onnx_model':IMPL/'downloads/yolo11n.onnx','onnx_android':IMPL/'downloads/onnxruntime-android-1.22.0.aar',
        'opencv_android':IMPL/'downloads/opencv-4.11.0.aar','apk':IMPL/'Portiva-Bench.apk'}.items()}
    checks.update({name:importlib.util.find_spec(name) is not None for name in ['numpy','cv2','onnxruntime']})
    result=dict(project='Portiva',workspace=str(ROOT),development_dependencies=checks,motion_enabled=False,
                phone_available_or_tested=False,hardware_qualified=False,full_software_commissioned=False)
    (IMPL/'Portiva-doctor-results.json').write_text(json.dumps(result,indent=2),encoding='utf-8');print(json.dumps(result,indent=2))
    return 0 if all(checks.values()) else 1

def main():
    parser=argparse.ArgumentParser(description=__doc__);commands=parser.add_subparsers(dest='command',required=True)
    for name in ['doctor','test','core-test','demo','build','android-test','readiness']:commands.add_parser(name)
    mapcmd=commands.add_parser('plan');mapcmd.add_argument('--config',type=Path,default=IMPL/'config/Portiva-demo-map.json');mapcmd.add_argument('--origin',default='pooja');mapcmd.add_argument('--destination',default='kitchen')
    camera=commands.add_parser('camera');camera.add_argument('--url',default='http://192.168.4.1');camera.add_argument('--frames',type=int,default=20)
    command=commands.add_parser('calibrate');command.add_argument('arguments',nargs=argparse.REMAINDER)
    args=parser.parse_args()
    if args.command=='doctor':return doctor()
    if args.command=='demo':demo()
    elif args.command=='core-test':run([sys.executable,IMPL/'check_core.py'])
    elif args.command=='test':run([sys.executable,IMPL/'verify_host.py'])
    elif args.command=='build':run([sys.executable,IMPL/'build_android.py'])
    elif args.command=='android-test':run(['powershell.exe','-NoProfile','-ExecutionPolicy','Bypass','-File',IMPL/'run_android_bench.ps1'])
    elif args.command=='readiness':run([sys.executable,ROOT/'final_design_audit.py'])
    elif args.command=='plan':
        sys.path.insert(0,str(IMPL));from map_tools import plan
        result=plan(json.loads(args.config.read_text(encoding='utf-8-sig')),args.origin,args.destination)
        out=IMPL/'Portiva-reference-route.json';out.write_text(json.dumps(result,indent=2),encoding='utf-8')
        print(result['outcome']+': '+str(out));return 0 if result['poses'] else 2
    elif args.command=='camera':run([sys.executable,IMPL/'camera_bench_check.py','--url',args.url,'--frames',args.frames])
    elif args.command=='calibrate':
        rest=args.arguments[1:] if args.arguments[:1]==['--'] else args.arguments
        run([sys.executable,IMPL/'calibrate_natural_surface.py',*rest])
    return 0

if __name__=='__main__':
    try:raise SystemExit(main())
    except (ValueError,OSError,json.JSONDecodeError) as error:print('Portiva: '+str(error),file=sys.stderr);raise SystemExit(2)
