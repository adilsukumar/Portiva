"""Deterministic protocol fixtures, not a physical camera or network test."""
import json
from pathlib import Path
import cv2
import numpy as np
from camera_bench_check import capture_check,validate_url,validate_health,decode_frame
ROOT=Path(__file__).resolve().parent
checks=0
def rejects(function,*args):
    global checks
    try:function(*args)
    except (ValueError,TypeError):checks+=1;return
    raise AssertionError('Invalid fixture was accepted')

health=dict(firmware='Portiva-camera-bench-v1',camera_pid=38,psram_bytes=4194304,free_heap_bytes=200000,uptime_ms=100,motion_enabled=False)
assert validate_health(health)==health;checks+=1
for change in [{'motion_enabled':True},{'camera_pid':0},{'psram_bytes':524288},{'uptime_ms':True},{'free_heap_bytes':1},{'firmware':'different'}]:
    rejects(validate_health,{**health,**change})
for url in ['https://192.168.4.1','http://example.com','http://127.0.0.1','http://8.8.8.8','http://192.168.4.1/other','http://user:secret@192.168.4.1','http://192.168.4.1?token=x','http://192.168.4.1#x']:
    rejects(validate_url,url)
_,encoded=cv2.imencode('.jpg',np.zeros((480,640,3),np.uint8));jpeg=encoded.tobytes()
assert decode_frame(jpeg).shape==(480,640,3);checks+=1
rejects(decode_frame,b'not JPEG')
rejects(decode_frame,b'\xff\xd8'+b'0'*700000+b'\xff\xd9')
_,small=cv2.imencode('.jpg',np.zeros((20,20,3),np.uint8));rejects(decode_frame,small.tobytes())
def fixture(url,token,limit):
    if token is None:return 401,'text/plain',b''
    if url.endswith('/health'):return 200,'application/json',json.dumps(health).encode()
    return 200,'image/jpeg',jpeg
result,_=capture_check('http://192.168.4.1','a'*32,3,fixture)
assert result['valid_vga_frames']==3 and not result['full_bom_approved'];checks+=1
rejects(capture_check,'http://192.168.4.1','short',3,fixture)
rejects(capture_check,'http://192.168.4.1','a'*32,0,fixture)
rejects(capture_check,'http://192.168.4.1','a'*32,3,lambda *args:(200,'image/jpeg',jpeg))
out=dict(scope='Software fixtures only; no physical camera, Wi-Fi or phone was tested',assertions=checks,passed=True,physical_camera_tested=False)
(ROOT/'Camera-bench-tool-test-results.json').write_text(json.dumps(out,indent=2),encoding='utf-8')
print(json.dumps(out))
