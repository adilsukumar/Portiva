"""Exercise the actual camera fitting code using procedural projections, not house photos."""
from pathlib import Path
import json
import numpy as np
import cv2
from calibrate_natural_surface import fit
ROOT=Path(__file__).resolve().parent

def main():
    rng=np.random.default_rng(8711);K=np.array([[510.,0,320],[0,505,240],[0,0,1]])
    lens=np.array([-.10,.025,.0008,-.0006,0.]);obj=np.array([[(x-3)*.065,(y-2)*.065,0] for y in range(5) for x in range(7)],np.float32)
    views=[]
    while len(views)<15:
        r=rng.uniform([-.45,-.45,-.18],[.45,.45,.18]);t=rng.uniform([-.18,-.12,.65],[.18,.12,1.1]);img=cv2.projectPoints(obj,r,t,K,lens)[0].reshape(-1,2)
        if (img[:,0]<4).any() or (img[:,0]>636).any() or (img[:,1]<4).any() or (img[:,1]>476).any():continue
        img+=rng.normal(0,.08,img.shape);views.append({'surface_points_m':obj.tolist(),'image_points_px':img.tolist()})
    data={'scope':'PROCEDURAL TEST ONLY NOT THE HOUSE','units':'metres','image_size_px':[640,480],'views':views}
    result=fit(data);candidate=np.array(result['intrinsics_row_major']).reshape(3,3);relative=np.abs(np.diag(candidate)[:2]-np.diag(K)[:2])/np.diag(K)[:2]
    assert max(relative)<.03 and result['physical_position_error_bound_m'] is None
    result['scope']='Procedural camera calibration test; not real camera or house validation';result['focal_length_relative_error']=relative.tolist()
    result['test_outcome']='passed'
    config=ROOT/'config';config.mkdir(exist_ok=True)
    (config/'procedural-calibration-input.json').write_text(json.dumps(data,indent=2),encoding='utf-8')
    invalid=[('empty survey',{'units':'metres','image_size_px':[640,480],'views':[]}),
             ('wrong units',{**data,'units':'millimetres'}),('too few views',{**data,'views':views[:5]})]
    wrong_plane=json.loads(json.dumps(data))
    for point in wrong_plane['views'][0]['surface_points_m']:point[2]=1
    invalid.append(('wrong surface coordinates',wrong_plane))
    result['rejected_invalid_inputs']=[]
    for name,candidate in invalid:
        rejected=False
        try:fit(candidate)
        except ValueError:rejected=True
        assert rejected,name
        result['rejected_invalid_inputs'].append(name)
    (ROOT/'Natural-calibration-test-results.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
    print('Natural-surface calibration: actual fit passed on procedural images; 4 invalid inputs rejected. No real position bound claimed.')

if __name__=='__main__':main()
