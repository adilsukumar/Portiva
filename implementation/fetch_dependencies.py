"""Explicit, checksum-verified download of public build dependencies. Never runs during import."""
from pathlib import Path
import argparse,hashlib,json,subprocess,sys,urllib.request
ROOT=Path(__file__).resolve().parent

def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--export-model',action='store_true');args=parser.parse_args()
    lock=json.loads((ROOT/'dependencies.lock.json').read_text());folder=ROOT/'downloads';folder.mkdir(exist_ok=True)
    for item in lock['downloads']:
        target=folder/item['name']
        if target.exists():
            if sha(target)!=item['sha256']:raise SystemExit('Existing dependency checksum differs; preserved: '+str(target))
            print('Verified '+target.name);continue
        temporary=folder/(item['name']+'.part');request=urllib.request.Request(item['url'],headers={'User-Agent':'Portiva-development'})
        with urllib.request.urlopen(request,timeout=60) as source,temporary.open('xb') as out:
            total=0
            while True:
                block=source.read(1024*1024)
                if not block:break
                total+=len(block)
                if total>item['bytes']+1024:raise ValueError('Dependency exceeded locked size')
                out.write(block)
        if sha(temporary)!=item['sha256']:raise SystemExit('Downloaded checksum differs. Untrusted .part file was not promoted: '+str(temporary))
        temporary.rename(target);print('Downloaded and verified '+target.name)
    if args.export_model:
        subprocess.run([sys.executable,'-c',"from ultralytics import YOLO; YOLO('yolo11n.pt').export(format='onnx',imgsz=320,opset=13,dynamic=False,simplify=False,half=False,batch=1)"],cwd=folder,check=True)
        print('Exported ONNX candidate. Run detector tests before building the APK. Read THIRD_PARTY_NOTICES.md for model terms.')
    print('Dependencies verified. No device was contacted or flashed.')
if __name__=='__main__':main()
