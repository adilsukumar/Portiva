"""Validated offline map planning. Output is a reference route, never motor commands."""
from pathlib import Path
import json
import math
import numpy as np
from rectangular_base_check import RectanglePlanner

def number(value,low,high):
    if type(value) not in (int,float) or not math.isfinite(value) or not low<=value<=high:raise ValueError('Invalid metric value')
    return float(value)

def validate_map(data):
    if not isinstance(data,dict) or data.get('schema')!='portiva-map-v1':raise ValueError('Expected portiva-map-v1')
    if data.get('motion_enabled',False) is not False:raise ValueError('Map files cannot enable motor movement')
    if data.get('kind') not in ('synthetic','draft','surveyed'):raise ValueError('State whether map is synthetic, draft or surveyed')
    cell=number(data.get('cell_m'),.005,.2)
    rows=data.get('grid')
    if not isinstance(rows,list) or not rows or type(rows[0]) is not str or not rows[0]:raise ValueError('Enter a nonempty occupancy grid first')
    if len(rows)*len(rows[0])>1000000:raise ValueError('Map too large')
    if any(type(row) is not str or len(row)!=len(rows[0]) or any(c not in '.#' for c in row) for row in rows):raise ValueError('Grid must be rectangular; use only dot for free and # for blocked')
    footprint=data.get('footprint_m')
    if not isinstance(footprint,list) or len(footprint)!=2:raise ValueError('Need forward length and lateral width')
    length,width=[number(v,.10,.4572) for v in footprint]
    padding=number(data.get('padding_m'),.03,.20)
    rooms=data.get('rooms')
    if not isinstance(rooms,dict) or not 2<=len(rooms)<=50:raise ValueError('Register two to fifty room destinations')
    for name,room in rooms.items():
        if type(name) is not str or not 1<=len(name)<=48 or any(ord(c)<32 for c in name):raise ValueError('Invalid room name')
        if not isinstance(room,dict):raise ValueError('Invalid room record')
        point=room.get('cell');heading=room.get('heading_index')
        if not isinstance(point,list) or len(point)!=2 or any(type(n) is not int for n in point):raise ValueError('Room cell needs integer x and y')
        if not(0<=point[0]<len(rows[0]) and 0<=point[1]<len(rows)) or type(heading) is not int or not 0<=heading<8:raise ValueError('Room outside map or heading invalid')
    raw=np.array([[c=='#' for c in row] for row in rows],dtype=bool)
    planner=RectanglePlanner(raw,cell,length,width,padding)
    for room in rooms.values():
        node=(*room['cell'],room['heading_index'])
        if not planner.clear(*planner.pose(node)):raise ValueError('A room destination has insufficient body clearance')
    return planner,rooms

def plan(data,origin,destination):
    planner,rooms=validate_map(data)
    if origin not in rooms or destination not in rooms or origin==destination:raise ValueError('Choose two distinct registered rooms')
    start=(*rooms[origin]['cell'],rooms[origin]['heading_index']);goal=(*rooms[destination]['cell'],rooms[destination]['heading_index'])
    route,reason=planner.path(start,goal)
    return dict(project='Portiva',schema='portiva-reference-route-v1',kind=data['kind'],origin=origin,destination=destination,
                outcome='reference_route_found' if route else 'hold',reason=reason,
                poses=[dict(x_m=x,y_m=y,yaw_rad=yaw) for x,y,yaw in map(planner.pose,route)],
                motion_enabled=False,physical_route_qualified=False,
                limitations=['Camera localization and heading error are not measured here','No live obstacle adapter or motor transport','Surveyed label alone is not independent measurement evidence'])

def demo_map():
    # Match the separately verified artificial doorway, not the actual house.
    cell=.0125;rows=[];bottom=1.2-.5588/2;top=1.2+.5588/2
    for yy in range(192):
        row=['.']*256
        if not(yy*cell>=bottom and (yy+1)*cell<=top):
            for xx in range(126,130):row[xx]='#'
        rows.append(''.join(row))
    return dict(schema='portiva-map-v1',kind='synthetic',cell_m=cell,grid=rows,footprint_m=[.390,.410],padding_m=.055,
                rooms={'pooja':{'cell':[48,95],'heading_index':2},'kitchen':{'cell':[207,95],'heading_index':0}},motion_enabled=False)

if __name__=='__main__':
    root=Path(__file__).resolve().parent
    output=root/'config/Portiva-demo-map.json';output.write_text(json.dumps(demo_map(),indent=2),encoding='utf-8');print(output)
