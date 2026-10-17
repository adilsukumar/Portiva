"""Independent native OpenCV check of Java footprint predictions and continuous route samples."""
from pathlib import Path
import csv,json,math
import cv2
ROOT=Path(__file__).resolve().parent

def independent_clear(x,y,yaw):
    hl,hw=.390/2+.055,.410/2+.055
    ex=hl*abs(math.cos(yaw))+hw*abs(math.sin(yaw));ey=hl*abs(math.sin(yaw))+hw*abs(math.cos(yaw))
    if x-ex<=0 or y-ey<=0 or x+ex>=3.2 or y+ey>=2.4:return False
    body=((x,y),(2*hl,2*hw),math.degrees(yaw))
    # Raster occupancy is deliberately rounded into the opening. Verify exactly
    # that physical grid, independent of the Java SAT implementation.
    bottom=math.ceil((1.2-.5588/2)/.0125)*.0125
    top=math.floor((1.2+.5588/2)/.0125)*.0125
    for wall in [((1.6,bottom/2),(.05,bottom),0),((1.6,(top+2.4)/2),(.05,2.4-top),0)]:
        kind,_=cv2.rotatedRectangleIntersection(body,wall)
        if kind!=cv2.INTERSECT_NONE:return False
    return True

def main():
    rows=list(csv.DictReader((ROOT/'Portiva-rectangle-java-fixtures.csv').open()))
    for r in rows:
        actual=independent_clear(float(r['x_m']),float(r['y_m']),float(r['yaw_rad']))
        assert actual==(r['java_clear']=='true'),r
    route=list(csv.DictReader((ROOT/'Portiva-rectangle-java-route.csv').open()));poses=[tuple(float(r[k]) for k in ('x_m','y_m','yaw_rad')) for r in route]
    checked=0
    for a,b in zip(poses,poses[1:]):
        da=math.atan2(math.sin(b[2]-a[2]),math.cos(b[2]-a[2]));n=max(1,math.ceil((math.hypot(b[0]-a[0],b[1]-a[1])+.361*abs(da))/.002))
        for i in range(n+1):
            t=i/n;assert independent_clear(a[0]+t*(b[0]-a[0]),a[1]+t*(b[1]-a[1]),a[2]+t*da);checked+=1
    result=dict(passed=True,scope='Java Android planner versus independent native OpenCV on artificial geometry',random_pose_checks=len(rows),continuous_route_samples=checked,route_states=len(route),real_house_tested=False,motion_enabled=False)
    (ROOT/'Portiva-rectangle-java-results.json').write_text(json.dumps(result,indent=2)+'\n');print(result)
if __name__=='__main__':main()
