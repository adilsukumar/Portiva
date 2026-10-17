"""Build the bench APK with installed Android SDK tools; no Gradle or device needed."""
from pathlib import Path
import subprocess,zipfile,shutil,json,hashlib,os
from toolchain import java_tools,android_sdk,exe

ROOT=Path(__file__).resolve().parent
SDK=android_sdk()
JDK=java_tools()
TOOLS=SDK/'build-tools/35.0.0'
JAR=SDK/'platforms/android-35/android.jar'
OUT=ROOT/'android-build'

def run(*args):
    subprocess.run([str(x) for x in args],check=True,cwd=ROOT)

def main():
    for folder in ['classes','dex','assets','native']:(OUT/folder).mkdir(parents=True,exist_ok=True)
    aar=ROOT/'downloads/onnxruntime-android-1.22.0.aar'
    with zipfile.ZipFile(aar) as z:
        (OUT/'ort.jar').write_bytes(z.read('classes.jar'))
        native=[n for n in z.namelist() if n.startswith(('jni/arm64-v8a/','jni/armeabi-v7a/','jni/x86_64/')) and n.endswith('.so')]
        for n in native:
            p=OUT/'native'/n;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(z.read(n))
    with zipfile.ZipFile(ROOT/'downloads/opencv-4.11.0.aar') as z:
        (OUT/'opencv.jar').write_bytes(z.read('classes.jar'))
        for n in z.namelist():
            if n.startswith(('jni/arm64-v8a/','jni/armeabi-v7a/','jni/x86_64/')) and n.endswith('.so'):
                p=OUT/'native'/n;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(z.read(n))
    model=ROOT/'downloads/yolo11n.onnx'
    if not model.exists():raise SystemExit('Export yolo11n.onnx before building; no model-free APK is released.')
    shutil.copyfile(model,OUT/'assets/yolo11n.onnx')
    shutil.copyfile(ROOT/'downloads/bus.jpg',OUT/'assets/bus.jpg')
    for fixture in (ROOT/'fixtures').glob('*'):shutil.copyfile(fixture,OUT/'assets'/fixture.name)
    sources=list((ROOT/'android/src').rglob('*.java'))
    run(JDK/exe('javac'),'--release','11','-cp',os.pathsep.join(map(str,[JAR,OUT/'ort.jar',OUT/'opencv.jar'])),'-d',OUT/'classes',*sources)
    with zipfile.ZipFile(OUT/'app-classes.jar','w') as z:
        for f in (OUT/'classes').rglob('*.class'):z.write(f,f.relative_to(OUT/'classes').as_posix())
    # Invoke the SDK Java tools directly with the verified JBR, avoiding a system Java shim.
    run(JDK/exe('java'),'-Xmx1024M','-cp',TOOLS/'lib/d8.jar','com.android.tools.r8.D8',
        '--release','--min-api','26','--lib',JAR,'--output',OUT/'dex',OUT/'app-classes.jar',OUT/'ort.jar',OUT/'opencv.jar')
    run(TOOLS/exe('aapt2'),'link','--manifest',ROOT/'android/AndroidManifest.xml','-I',JAR,
        '--min-sdk-version','26','--target-sdk-version','35','-A',OUT/'assets','-o',OUT/'unsigned.apk')
    with zipfile.ZipFile(OUT/'unsigned.apk','a') as z:
        for f in (OUT/'dex').glob('*.dex'):z.write(f,f.name)
        for f in (OUT/'native/jni').rglob('*.so'):z.write(f,'lib/'+f.relative_to(OUT/'native/jni').as_posix(),compress_type=zipfile.ZIP_STORED)
    run(TOOLS/exe('zipalign'),'-P','16','-f','4',OUT/'unsigned.apk',OUT/'aligned.apk')
    key=OUT/'bench-debug.keystore'
    if not key.exists():
        run(JDK/exe('keytool'),'-genkeypair','-keystore',key,'-storepass','android','-keypass','android',
            '-alias','androiddebugkey','-keyalg','RSA','-keysize','2048','-validity','3650','-dname','CN=Portiva Bench')
    apk=ROOT/'Portiva-Bench.apk'
    run(JDK/exe('java'),'-jar',TOOLS/'lib/apksigner.jar','sign','--ks',key,'--ks-key-alias','androiddebugkey','--ks-pass','pass:android',
        '--key-pass','pass:android','--out',apk,OUT/'aligned.apk')
    run(JDK/exe('java'),'-jar',TOOLS/'lib/apksigner.jar','verify','--verbose',apk)
    run(TOOLS/exe('zipalign'),'-c','-P','16','4',apk)
    report={'apk':str(apk),'sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'bytes':apk.stat().st_size,
            'minimum_android_api':26,'target_android_api':35,'abis':['arm64-v8a','armeabi-v7a','x86_64'],
            'compiler':'installed Android SDK35 + Android Studio JBR','model':'YOLO11n fixed320 ONNX opset13',
            'motion_enabled':False,'device_installed_or_benchmarked':False,'build_and_signature_verified':True}
    (ROOT/'Android-build-results.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
    print('Built and verified bench-only APK:',apk)

if __name__=='__main__':main()
