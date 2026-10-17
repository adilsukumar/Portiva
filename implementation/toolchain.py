"""Find installed tools without downloading software or requiring one user's home path."""
from pathlib import Path
import os,shutil

def java_tools():
    candidates=[os.environ.get('PORTIVA_JAVA_HOME'),os.environ.get('JAVA_HOME')]
    if os.name=='nt':candidates.append(str(Path(os.environ.get('ProgramFiles','C:/Program Files'))/'Android/Android Studio/jbr'))
    for value in candidates:
        if value:
            folder=Path(value)/'bin'
            if (folder/exe('javac')).exists():return folder
    found=shutil.which('javac')
    if found:return Path(found).resolve().parent
    raise FileNotFoundError('Install a JDK (17+ for Android tools), then set JAVA_HOME or PORTIVA_JAVA_HOME.')

def exe(name):return name+'.exe' if os.name=='nt' else name

def android_sdk():
    for value in [os.environ.get('ANDROID_SDK_ROOT'),os.environ.get('ANDROID_HOME'),str(Path(os.environ.get('LOCALAPPDATA',Path.home()/'AppData/Local'))/'Android/Sdk'),str(Path.home()/'AppData/Local/Android/Sdk')]:
        if value and Path(value).is_dir():return Path(value)
    raise FileNotFoundError('Install Android SDK platform/build-tools 35, then set ANDROID_SDK_ROOT.')

def cpp_compiler():
    configured=os.environ.get('CXX');found=shutil.which(configured or 'g++')
    if found:return found
    fallback=Path('C:/msys64/mingw64/bin/g++.exe')
    if os.name=='nt' and fallback.exists():return str(fallback)
    raise FileNotFoundError('Install a C++17 compiler and set CXX, or put g++ on PATH.')
