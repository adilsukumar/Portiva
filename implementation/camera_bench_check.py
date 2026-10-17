"""Read-only camera acceptance check. This does not arm motors or prove autonomy."""
import argparse
import getpass
import ipaddress
import json
from pathlib import Path
import statistics
import time
import urllib.error
import urllib.parse
import urllib.request
import cv2
import numpy as np

ROOT=Path(__file__).resolve().parent

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self,*args,**kwargs):return None

def validate_url(value):
    url=urllib.parse.urlsplit(value)
    if url.scheme!='http' or url.username or url.password or url.path not in ('','/') or url.query or url.fragment:
        raise ValueError('Use a private numeric HTTP camera address without credentials or paths')
    address=ipaddress.ip_address(url.hostname)
    private=any(address in ipaddress.ip_network(n) for n in ['10.0.0.0/8','172.16.0.0/12','192.168.0.0/16']) if address.version==4 else False
    if not private:raise ValueError('Only private RFC1918 camera addresses are allowed')
    if url.port is not None and not 1<=url.port<=65535:raise ValueError('Invalid port')
    return value.rstrip('/')

def validate_health(h):
    if not isinstance(h,dict) or h.get('firmware')!='Portiva-camera-bench-v1' or h.get('motion_enabled') is not False:
        raise ValueError('Wrong firmware or motion not disabled')
    for key in ['camera_pid','psram_bytes','free_heap_bytes','uptime_ms']:
        if type(h.get(key)) is not int or h[key]<0:raise ValueError('Invalid health field')
    if h['camera_pid']!=0x26:raise ValueError('The board did not report an OV2640 sensor')
    if not 4*1024*1024<=h['psram_bytes']<=16*1024*1024:raise ValueError('Expected at least 4 MB PSRAM')
    if h['free_heap_bytes']<16384:raise ValueError('Very low reported heap')
    return h

def decode_frame(data):
    if len(data)>700000 or not data.startswith(b'\xff\xd8') or not data.endswith(b'\xff\xd9'):
        raise ValueError('Invalid or oversized JPEG')
    frame=cv2.imdecode(np.frombuffer(data,dtype=np.uint8),cv2.IMREAD_COLOR)
    if frame is None or frame.shape[:2]!=(480,640):raise ValueError('Expected a decodable VGA image')
    return frame

def capture_check(base,token,count,request):
    base=validate_url(base)
    if len(token)<32 or '\n' in token or '\r' in token:raise ValueError('A private pairing token of at least32 characters is required')
    if type(count) is not int or not 3<=count<=200:raise ValueError('Use3 to200 frames')
    # Do not silently accept a camera with a public health/capture endpoint.
    for path in ['/health','/capture']:
        status,_,_=request(base+path,None,1024)
        if status!=401:raise ValueError('Unauthenticated request was not rejected')
    status,_,body=request(base+'/health',token,2048)
    if status!=200:raise ValueError('Authenticated health check failed')
    health=validate_health(json.loads(body))
    timings=[];sizes=[];last=None
    for _ in range(count):
        start=time.monotonic();status,kind,body=request(base+'/capture',token,700000)
        if status!=200 or kind.split(';')[0].strip().lower()!='image/jpeg':raise ValueError('Capture request failed')
        last=decode_frame(body);timings.append(1000*(time.monotonic()-start));sizes.append(len(body))
    result=dict(scope='Physical camera HTTP/authentication/VGA capture only when run against the actual board',
        camera_address=base,health=health,frames=count,valid_vga_frames=count,
        capture_and_decode_median_ms=round(statistics.median(timings),3),
        capture_and_decode_max_ms=round(max(timings),3),max_jpeg_bytes=max(sizes),
        not_included=['Redmi inference or total control latency','Grandma identity or range','house localization','rack load or motor performance'],
        motion_enabled=False,autonomous_robot_qualified=False,full_bom_approved=False)
    return result,last

def network_request(url,token,limit):
    headers={} if token is None else {'X-Robot-Token':token}
    request=urllib.request.Request(url,headers=headers)
    opener=urllib.request.build_opener(urllib.request.ProxyHandler({}),NoRedirect())
    try:
        with opener.open(request,timeout=3) as response:
            body=response.read(limit+1)
            if len(body)>limit:raise ValueError('Oversized response')
            return response.status,response.headers.get('Content-Type',''),body
    except urllib.error.HTTPError as e:
        return e.code,e.headers.get('Content-Type',''),b''

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--url',default='http://192.168.4.1')
    parser.add_argument('--frames',type=int,default=20)
    args=parser.parse_args()
    token=getpass.getpass('Camera pairing token (hidden): ')
    result,frame=capture_check(args.url,token,args.frames,network_request)
    # No frame is saved by default. Avoid storing private images of the house/people.
    out=ROOT/'Actual-camera-bench-results.json'
    out.write_text(json.dumps(result,indent=2),encoding='utf-8')
    print('Camera-only check passed. Result:',out)
    print('Median capture/decode ms:',result['capture_and_decode_median_ms'])
    print('This does not approve the full robot BOM.')

if __name__=='__main__':main()
