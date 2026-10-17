"""Run packaged ONNX detector on a public photo; no Grandma/home data is invented."""
from pathlib import Path
import cv2,numpy as np,onnxruntime as ort,json,time
ROOT=Path(__file__).resolve().parent

def detect(session,image):
    h,w=image.shape[:2];n=320;scale=min(n/w,n/h);rw,rh=round(w*scale),round(h*scale);px,py=(n-rw)//2,(n-rh)//2
    square=np.full((n,n,3),114,np.uint8);square[py:py+rh,px:px+rw]=cv2.resize(image,(rw,rh))
    x=cv2.cvtColor(square,cv2.COLOR_BGR2RGB).astype(np.float32).transpose(2,0,1)[None]/255
    output=session.run(None,{session.get_inputs()[0].name:x})[0][0]
    assert output.shape[0]==84,output.shape
    boxes=[]
    for row in output.T:
        if row[4]<.35 or np.argmax(row[4:])!=0:continue
        cx,cy,bw,bh=row[:4];a=max(0,(cx-bw/2-px)/scale);b=max(0,(cy-bh/2-py)/scale)
        c=min(w,(cx+bw/2-px)/scale);d=min(h,(cy+bh/2-py)/scale)
        if not np.isfinite([a,b,c,d]).all() or c<=a or d<=b:continue
        boxes.append([float(a),float(b),float(c-a),float(d-b),float(row[4])])
    if not boxes:return []
    indices=cv2.dnn.NMSBoxes([b[:4] for b in boxes],[b[4] for b in boxes],.35,.45)
    return [boxes[i] for i in np.asarray(indices).reshape(-1)]

def main():
    opts=ort.SessionOptions();opts.intra_op_num_threads=2
    s=ort.InferenceSession(str(ROOT/'downloads/yolo11n.onnx'),opts,providers=['CPUExecutionProvider'])
    photo=cv2.imread(str(ROOT/'downloads/bus.jpg'));assert photo is not None
    cases={'public_photo':photo,'dim_photo':np.uint8(photo*.5),
           'blurred_photo':cv2.GaussianBlur(photo,(15,15),0),'blank':np.zeros_like(photo)}
    results=[]
    for name,image in cases.items():
        start=time.perf_counter();boxes=detect(s,image);elapsed=(time.perf_counter()-start)*1000
        if name=='public_photo':assert len(boxes)>=2,'Public-photo person detection failed'
        if name=='blank':assert not boxes,'Blank image should not have persons in this fixture'
        canvas=image.copy()
        for a,b,w,h,score in boxes:cv2.rectangle(canvas,(round(a),round(b)),(round(a+w),round(b+h)),(255,120,0),3)
        cv2.imwrite(str(ROOT/f'detection-{name}.jpg'),canvas)
        results.append(dict(case=name,person_count=len(boxes),host_inference_ms=round(elapsed,2),boxes=boxes))
    report=dict(scope='Real pretrained YOLO inference on one public Ultralytics photo and transformations; host CPU only',
                model='YOLO11n 320 ONNX',tests=results,grandma_identity_tested=False,redmi_latency_tested=False,
                source='https://github.com/ultralytics/ultralytics/blob/main/ultralytics/assets/bus.jpg')
    (ROOT/'Detector-test-results.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
    print([(r['case'],r['person_count']) for r in results])

if __name__=='__main__':main()
