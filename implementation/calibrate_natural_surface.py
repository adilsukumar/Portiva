"""Fit a camera model from measured existing surface corners. No added house markers required."""
from pathlib import Path
import argparse,json
import numpy as np
import cv2

def fit(data):
    if data.get('units')!='metres':raise ValueError('Measured surface coordinates must use metres')
    w,h=data['image_size_px']
    if not isinstance(w,int) or not isinstance(h,int) or w<100 or h<100:raise ValueError('Actual image dimensions required')
    views=data.get('views',[])
    if len(views)<12:raise ValueError('At least 12 views required: 9 fit views and 3 reserved views')
    objects=[];images=[]
    for view in views:
        obj=np.asarray(view['surface_points_m'],np.float32);img=np.asarray(view['image_points_px'],np.float32)
        if obj.ndim!=2 or obj.shape[1]!=3 or img.shape!=(len(obj),2) or len(obj)<12:raise ValueError('Each view needs at least 12 matching measured corners')
        if not np.isfinite(obj).all() or not np.isfinite(img).all() or (img<0).any() or (img[:,0]>=w).any() or (img[:,1]>=h).any():raise ValueError('Invalid points')
        if np.max(np.abs(obj[:,2]))>1e-5 or np.linalg.matrix_rank(obj[:,:2]-obj[0,:2])<2:raise ValueError('Use one flat measured surface in its local XY plane with Z equal to zero')
        if abs(cv2.contourArea(cv2.convexHull(img)))<w*h*.03:raise ValueError('Surface is too small in a view')
        objects.append(obj);images.append(img)
    rms,K,lens,rotations,translations=cv2.calibrateCamera(objects[:-3],images[:-3],(w,h),None,None,
        flags=cv2.CALIB_FIX_K3,criteria=(cv2.TERM_CRITERIA_EPS+cv2.TERM_CRITERIA_COUNT,200,1e-10))
    if not np.isfinite(K).all() or not np.isfinite(lens).all() or not .2*w<K[0,0]<4*w or not .2*w<K[1,1]<4*w:raise ValueError('Implausible fitted lens')
    normals=np.array([cv2.Rodrigues(r)[0][:,2] for r in rotations]);spread=max(np.degrees(np.arccos(np.clip(np.dot(a,b),-1,1))) for a in normals for b in normals)
    if spread<15:raise ValueError('More varied camera angles are needed')
    holdout=[]
    for obj,img in zip(objects[-3:],images[-3:]):
        okay,r,t=cv2.solvePnP(obj,img,K,lens)
        if not okay:raise ValueError('Reserved view pose fit failed')
        projected=cv2.projectPoints(obj,r,t,K,lens)[0].reshape(-1,2)
        holdout.append(float(np.sqrt(np.mean(np.sum((projected-img)**2,axis=1)))))
    if rms>1 or max(holdout)>1:raise ValueError('Pixel residuals are too high; inspect measurement and correspondence errors')
    return {'status':'CAMERA_MODEL_CANDIDATE_ONLY','image_size_px':[w,h],'intrinsics_row_major':K.reshape(-1).tolist(),
        'distortion_opencv':lens.reshape(-1).tolist(),'fit_rms_px':float(rms),'reserved_view_rms_px':holdout,
        'fitted_view_normal_spread_degrees':float(spread),'fit_views':len(views)-3,'reserved_views':3,
        'physical_position_error_bound_m':None,'motor_motion_allowed':False,
        'note':'Pixel agreement does not establish real-world centimetre accuracy. Independently measure position/yaw errors before navigation.'}

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('input');p.add_argument('--out',required=True);args=p.parse_args()
    result=fit(json.loads(Path(args.input).read_text(encoding='utf-8-sig')))
    Path(args.out).write_text(json.dumps(result,indent=2),encoding='utf-8');print('Saved camera model candidate. Real position bound remains unverified.')

if __name__=='__main__':main()
