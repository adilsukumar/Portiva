"""Reference rectangle planner and wider-stance sensitivity; no camera/motor adapter."""
from pathlib import Path
import heapq
import itertools
import json
import math
import sys
import time
import numpy as np
import cv2
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from matplotlib.patches import Polygon,Rectangle

ROOT=Path(__file__).resolve().parent
sys.path.insert(0,str(ROOT.parent))
from tall_rack_stability import hull,margin

def overlap(cx,cy,angle,half_length,half_width,x0,y0,x1,y1):
    """Rectangle versus occupied square SAT; touching is collision."""
    c,s=math.cos(angle),math.sin(angle)
    dx,dy=(x0+x1)/2-cx,(y0+y1)/2-cy
    hx,hy=(x1-x0)/2,(y1-y0)/2
    return (abs(dx)<=half_length*abs(c)+half_width*abs(s)+hx and
            abs(dy)<=half_length*abs(s)+half_width*abs(c)+hy and
            abs(dx*c+dy*s)<=half_length+hx*abs(c)+hy*abs(s) and
            abs(-dx*s+dy*c)<=half_width+hx*abs(s)+hy*abs(c))

class RectanglePlanner:
    # Rectangle length is robot-forward x; width is robot-lateral y.
    moves=[(1,0),(1,1),(0,1),(-1,1),(-1,0),(-1,-1),(0,-1),(1,-1)]
    def __init__(self,occupied,cell,length=.390,width=.410,padding=.055):
        if not isinstance(occupied,np.ndarray) or occupied.dtype!=bool or occupied.ndim!=2 or not occupied.size:
            raise ValueError('A nonempty boolean occupancy map is required')
        if not all(math.isfinite(x) for x in [cell,length,width,padding]) or min(cell,length,width)<=0 or padding<0:
            raise ValueError('Invalid metric geometry')
        if occupied.size>1000000: raise ValueError('Map too large for reference planner')
        self.map=occupied.copy();self.cell=cell;self.length=length;self.width=width;self.padding=padding
        self.radius=math.hypot(length/2+padding,width/2+padding)
        self.cache={}

    def clear(self,x,y,a,extra=0):
        if not all(math.isfinite(v) for v in [x,y,a,extra]) or extra<0:return False
        hl=self.length/2+self.padding+extra;hw=self.width/2+self.padding+extra
        c,s=math.cos(a),math.sin(a)
        ex,ey=hl*abs(c)+hw*abs(s),hl*abs(s)+hw*abs(c)
        ny,nx=self.map.shape
        if x-ex<=0 or y-ey<=0 or x+ex>=nx*self.cell or y+ey>=ny*self.cell:return False
        x0=max(0,math.floor((x-ex)/self.cell));x1=min(nx-1,math.floor((x+ex)/self.cell))
        y0=max(0,math.floor((y-ey)/self.cell));y1=min(ny-1,math.floor((y+ey)/self.cell))
        occupied=np.argwhere(self.map[y0:y1+1,x0:x1+1])
        for yy,xx in occupied:
            left,bottom=(xx+x0)*self.cell,(yy+y0)*self.cell
            if overlap(x,y,a,hl,hw,left,bottom,left+self.cell,bottom+self.cell):return False
        return True

    def pose(self,node):return ((node[0]+.5)*self.cell,(node[1]+.5)*self.cell,node[2]*math.pi/4)

    def edge_clear(self,a,b):
        p,q=self.pose(a),self.pose(b)
        da=math.atan2(math.sin(q[2]-p[2]),math.cos(q[2]-p[2]))
        # Any footprint point moves at most this distance. Subdivision plus
        # isotropic inflation covers between samples, not just sample endpoints.
        bound=math.hypot(q[0]-p[0],q[1]-p[1])+self.radius*abs(da)
        n=max(1,math.ceil(bound/.010));extra=bound/(2*n)+1e-9
        for i in range(n+1):
            t=i/n
            if not self.clear(p[0]+t*(q[0]-p[0]),p[1]+t*(q[1]-p[1]),p[2]+t*da,extra):return False
        return True

    def path(self,start,goal,max_expansions=30000):
        if len(start)!=3 or len(goal)!=3:raise ValueError('Grid x y yaw-index required')
        ny,nx=self.map.shape
        for n in [start,goal]:
            if any(type(v)!=int for v in n) or not(0<=n[0]<nx and 0<=n[1]<ny and 0<=n[2]<8):
                return [],'invalid start or goal'
            if not self.clear(*self.pose(n)):return [],'blocked start or goal'
        distance={start:0.};previous={};queue=[(0.,0.,start)];expanded=0
        while queue:
            _,cost,node=heapq.heappop(queue)
            if cost!=distance.get(node):continue
            if node==goal:
                route=[node]
                while node in previous:node=previous[node];route.append(node)
                return route[::-1],'route found in discrete reference map'
            expanded+=1
            if expanded>max_expansions:return [],'search limit hold'
            dx,dy=self.moves[node[2]]
            options=[((node[0]+dx,node[1]+dy,node[2]),self.cell*math.hypot(dx,dy)),
                     ((node[0],node[1],(node[2]+1)%8),self.radius*math.pi/4),
                     ((node[0],node[1],(node[2]-1)%8),self.radius*math.pi/4)]
            for nxt,increment in options:
                if not(0<=nxt[0]<nx and 0<=nxt[1]<ny):continue
                new=cost+increment
                if new>=distance.get(nxt,math.inf):continue
                key=(node,nxt)
                if key not in self.cache:self.cache[key]=self.edge_clear(node,nxt)
                if not self.cache[key]:continue
                distance[nxt]=new;previous[nxt]=node
                h=self.cell*math.hypot(goal[0]-nxt[0],goal[1]-nxt[1])
                heapq.heappush(queue,(new+h,new,nxt))
        return [],'no admissible route'

def wider_stance():
    # Four contacts, same two motors/two casters, assumed full body410x390mm.
    drives=[(-.180,.160),(.180,.160)]
    pivots=[(-.170,-.160),(.170,-.160)]
    assert 2*(.180+.010)<=.410 and 2*(.160+.035)<=.390+1e-12
    assert 2*(.170+.035)<=.410+1e-12
    rows=[]
    for label,px,py in [('centred',0,0),('rear_edge',0,-.130),('side_edge',.160,0)]:
        for slope,accel,force in itertools.product([0,2],[0,.35,.70],[0,10]):
            cg=.61375;x=5*px/8;y=5*py/8;normal=9.81*math.cos(math.radians(slope))
            offset=cg*(math.tan(math.radians(slope))+accel/normal)+force*.85/(8*normal)
            # Every contact moves by at most15mm from its nominal point.
            # Thus the nominal support polygon eroded by15mm is contained in
            # every actual contact hull. The force resultant lies in a disk of
            # radius offset about the static CG. This lower bound covers ALL
            # caster and force angles within this rigid, coplanar-contact model.
            continuous_lower=margin(hull(drives+pivots),(x,y))-.015-offset
            worst=math.inf
            for left,right in itertools.product(range(0,360,45),repeat=2):
                polygon=hull(drives+[(p[0]+.015*math.cos(math.radians(a)),p[1]+.015*math.sin(math.radians(a))) for p,a in zip(pivots,[left,right])])
                for angle in range(0,360,15):
                    q=(x+offset*math.cos(math.radians(angle)),y+offset*math.sin(math.radians(angle)))
                    worst=min(worst,margin(polygon,q))
            rows.append(dict(load=label,slope_deg=slope,acceleration_m_s2=accel,hand_force_n=force,margin_mm=round(1000*worst,4),continuous_lower_bound_mm=round(1000*continuous_lower,4)))
    selected=[r for r in rows if (r['load']!='centred' and r['slope_deg']==2 and r['acceleration_m_s2']==.35 and r['hand_force_n']==0)
              or (r['load']=='centred' and r['slope_deg']==0 and r['acceleration_m_s2']==.35 and r['hand_force_n']==10)]
    assert len(selected)==3 and all(r['margin_mm']>0 for r in selected)
    assert all(r['continuous_lower_bound_mm']>0 for r in selected)
    assert all(r['continuous_lower_bound_mm']<=r['margin_mm']+.0002 for r in rows)
    assert any(r['margin_mm']<0 for r in rows),'More demanding cases must not be silently omitted'
    return dict(assumed_outer_width_mm=410,assumed_outer_length_mm=390,drive_contacts_m=drives,
                caster_pivots_m=pivots,compared_cases=selected,all_cases=rows,physically_tested=False)

def main():
    begin=time.monotonic();cell=.0125
    grid=np.zeros((192,256),dtype=bool)
    # 3.2x2.4m artificial scene, not the user's floor plan. Wall thickness50mm.
    wallx=(1.575,1.625);bottom=1.2-.5588/2;top=1.2+.5588/2
    for yy in range(grid.shape[0]):
        y0,y1=yy*cell,(yy+1)*cell
        for xx in range(126,130):grid[yy,xx]=not(y0>=bottom and y1<=top)
    p=RectanglePlanner(grid,cell)
    start=(48,95,2);goal=(207,95,0)
    route,why=p.path(start,goal)
    assert route,why
    # Independent native OpenCV intersection, denser than planner sampling,
    # against continuous full walls rather than only the raster cells.
    obstacles=[((1.6,bottom/2),(.05,bottom),0),((1.6,(top+2.4)/2),(.05,2.4-top),0)]
    checked=0;max_contact=0
    for a,b in zip(route,route[1:]):
        assert a[:2]==b[:2] or (a[2]==b[2] and (b[0]-a[0],b[1]-a[1])==p.moves[a[2]])
        ap,bp=p.pose(a),p.pose(b);da=math.atan2(math.sin(bp[2]-ap[2]),math.cos(bp[2]-ap[2]))
        n=max(1,math.ceil((math.hypot(bp[0]-ap[0],bp[1]-ap[1])+p.radius*abs(da))/.002))
        for i in range(n+1):
            t=i/n;x=ap[0]+t*(bp[0]-ap[0]);y=ap[1]+t*(bp[1]-ap[1]);yaw=ap[2]+t*da
            rect=((x,y),(p.length+2*p.padding,p.width+2*p.padding),math.degrees(yaw))
            for obstacle in obstacles:
                collision,_=cv2.rotatedRectangleIntersection(rect,obstacle)
                max_contact=max(max_contact,collision);assert collision==0
            checked+=1
    crossing=(127,95,0)
    assert p.clear(*p.pose(crossing))
    assert not p.edge_clear(crossing,(127,95,1)),'Cannot spin in doorway'
    uncertain=RectanglePlanner(grid,cell,padding=.085)
    assert not uncertain.clear(*uncertain.pose(crossing)),'Uncertainty consumes clearance'
    blocked=grid.copy();blocked[:,126:130]=True
    no_route,reason=RectanglePlanner(blocked,cell).path(start,goal,max_expansions=300)
    assert not no_route
    assert not p.clear(-1,1,0) and not p.clear(float('nan'),1,0)
    assert not p.path((-1,95,0),goal)[0]
    model=wider_stance()
    result=dict(date='2026-10-15',scope='Reference rectangle path and rigid-body calculations only; no real house, rack or motors',
        footprint_mm=[410,390],padding_per_side_mm=55,pooja_gap_mm=558.8,virtual_scene_m=[3.2,2.4],
        route_found=True,route_states=len(route),independent_opencv_samples=checked,wall_intersections=max_contact,
        doorway_rotation_rejected=True,larger_uncertainty_rejected=True,blocked_route_rejected=True,
        blocked_route_reason=reason,sweep_coverage='Between-sample movement bound is added to footprint inflation',
        planner='Forward moves and in-place turns on eight heading lattice; weighted cost, not globally shortest continuous path',
        wider_stance=model,elapsed_s=round(time.monotonic()-begin,3),
        limits=['No live camera, obstacle updates or motor follower connected',
                'Geometry, mass, caster trail and braking are assumed, not measured',
                'Positive support margins are not structural or hardware certification',
                'More demanding load/force combinations still fail; not walking support'],
        purchase_ready=False)
    (ROOT/'Rectangle-base-results.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
    (ROOT/'Rectangle-route.csv').write_text('x_m,y_m,yaw_rad\n'+'\n'.join(','.join(map(str,p.pose(n))) for n in route)+'\n',encoding='utf-8')
    fig,ax=plt.subplots(figsize=(9,5))
    ax.add_patch(Rectangle((wallx[0],0),.05,bottom,color='#637381'))
    ax.add_patch(Rectangle((wallx[0],top),.05,2.4-top,color='#637381'))
    points=[p.pose(n) for n in route]
    ax.plot([v[0] for v in points],[v[1] for v in points],color='#176e80',label='Planned centre path')
    for i in [0,len(route)//2,len(route)-1]:
        x,y,a=points[i];c,s=math.cos(a),math.sin(a)
        corners=[(x+u*c-v*s,y+u*s+v*c) for u,v in [(-p.length/2,-p.width/2),(p.length/2,-p.width/2),(p.length/2,p.width/2),(-p.length/2,p.width/2)]]
        ax.add_patch(Polygon(corners,closed=True,facecolor='#b9dce3',edgecolor='#176e80',alpha=.75))
        ax.arrow(x,y,.12*c,.12*s,head_width=.035,color='#15434e')
    ax.annotate('558.8 mm opening',xy=(1.6,top),xytext=(1.85,1.9),arrowprops={'arrowstyle':'->'},fontsize=10)
    ax.set(xlim=(0,3.2),ylim=(0,2.4),xlabel='Metres',ylabel='Metres',title='Virtual passage for a 410 by 390 mm body')
    ax.set_aspect('equal');ax.legend(loc='lower left')
    fig.text(.5,.015,'Artificial scene. 55 mm padding per side. No camera or motors connected.',ha='center',fontsize=9)
    fig.tight_layout(rect=(0,.04,1,1));fig.savefig(ROOT/'Rectangle-route.png',dpi=160);plt.close(fig)
    print(json.dumps({k:v for k,v in result.items() if k!='wider_stance'},indent=2))
    print('Wider stance selected comparisons:',model['compared_cases'])

if __name__=='__main__':main()
