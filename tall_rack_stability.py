"""Rigid-body tipping sensitivity. Assumed geometry only, never hardware certification."""
from pathlib import Path
import csv
import itertools
import json
import math

ROOT = Path(__file__).resolve().parent
G = 9.81

def hull(points):
    points = sorted(set(points))
    def cross(a, b, c):
        return (b[0]-a[0])*(c[1]-a[1])-(b[1]-a[1])*(c[0]-a[0])
    lower, upper = [], []
    for p in points:
        while len(lower)>1 and cross(lower[-2], lower[-1], p)<=0: lower.pop()
        lower.append(p)
    for p in reversed(points):
        while len(upper)>1 and cross(upper[-2], upper[-1], p)<=0: upper.pop()
        upper.append(p)
    return lower[:-1]+upper[:-1]

def margin(polygon, point):
    return min(((b[0]-a[0])*(point[1]-a[1])-(b[1]-a[1])*(point[0]-a[0]))
               /math.hypot(b[0]-a[0], b[1]-a[1])
               for a,b in zip(polygon, polygon[1:]+polygon[:1]))

def main():
    # Cross-check independent analytic shapes, including boundary and exterior.
    square = hull([(-1,-1), (1,-1), (1,1), (-1,1), (0,0)])
    assert abs(margin(square,(0,0))-1)<1e-12
    assert abs(margin(square,(1,0)))<1e-12
    assert margin(square,(1.1,0))<0
    triangle = hull([(0,0),(1,0),(0,1)])
    assert abs(margin(triangle,(.2,.2))-.2)<1e-12
    assert abs(margin(triangle,(.5,.5)))<1e-12

    drive = [(-.138,.118),(.138,.118)]
    pivots = [(-.125,-.118),(.125,-.118)]
    trail = .015
    # Full 70 mm wheels, 20 mm tyre thickness; caster swept radius 35 mm.
    projection = max(math.hypot(.148,.153), math.hypot(.125,.118)+.035,
                     math.hypot(.160,.130))
    assert projection<=.215
    # 3 kg frame/battery at 220 mm; 5 kg payload; these masses are NOT measured.
    layouts = {
        'all_5kg_high_centred':[(3,.22,0,0),(5,.85,0,0)],
        'all_5kg_high_at_rear_edge':[(3,.22,0,0),(5,.85,0,-.13)],
        'all_5kg_high_at_side_edge':[(3,.22,0,0),(5,.85,.16,0)],
        'distributed_centred':[(3,.22,0,0),(2,.85,0,0),(2,.65,0,0),(1,.25,0,0)],
    }
    rows=[]
    # Sampled caster orientations, force directions and load cases, not continuous proof.
    for name, masses in layouts.items():
        total=sum(m for m,_,_,_ in masses)
        z=sum(m*h for m,h,_,_ in masses)/total
        x=sum(m*px for m,_,px,_ in masses)/total
        y=sum(m*py for m,_,_,py in masses)/total
        for slope, accel, hand in itertools.product([0,2], [0,.35,.70], [0,10]):
            worst=math.inf; witness=None
            for left,right in itertools.product(range(0,360,45),repeat=2):
                contacts=drive+[(px+trail*math.cos(math.radians(a)),py+trail*math.sin(math.radians(a)))
                                for (px,py),a in zip(pivots,[left,right])]
                polygon=hull(contacts)
                for angle in range(0,360,15):
                    # Gravity slope, horizontal inertial load and tray force aligned
                    # adversarially. Force applied at 850 mm; no walking-aid claim.
                    normal_g=G*math.cos(math.radians(slope))
                    displacement=z*(math.tan(math.radians(slope))+accel/normal_g)+hand*.85/(total*normal_g)
                    q=(x+displacement*math.cos(math.radians(angle)),
                       y+displacement*math.sin(math.radians(angle)))
                    distance=margin(polygon,q)
                    if distance<worst:
                        worst=distance;witness=[left,right,angle]
            rows.append(dict(layout=name,assumed_cg_height_m=z,slope_deg=slope,
                horizontal_acceleration_m_s2=accel,hand_force_n=hand,
                minimum_signed_support_margin_mm=round(worst*1000,6),
                stable_in_sampled_rigid_flat_contact_model=worst>0,
                caster_left_deg=witness[0],caster_right_deg=witness[1],force_direction_deg=witness[2]))
    def case(name,slope,accel,hand):
        return next(r for r in rows if r['layout']==name and r['slope_deg']==slope
                    and r['horizontal_acceleration_m_s2']==accel and r['hand_force_n']==hand)
    assert case('all_5kg_high_centred',0,.70,0)['stable_in_sampled_rigid_flat_contact_model']
    assert not case('all_5kg_high_at_rear_edge',2,.35,0)['stable_in_sampled_rigid_flat_contact_model']
    assert not case('all_5kg_high_at_side_edge',2,.35,0)['stable_in_sampled_rigid_flat_contact_model']
    assert not case('all_5kg_high_centred',0,.35,10)['stable_in_sampled_rigid_flat_contact_model']
    with (ROOT/'Tall-rack-stability-cases.csv').open('w',encoding='utf-8-sig',newline='') as f:
        writer=csv.DictWriter(f,fieldnames=list(rows[0]));writer.writeheader();writer.writerows(rows)
    result=dict(date='2026-10-15',scope='Sampled quasi-static rigid-body tipping calculation, not physical certification',
        assumed_frame_kg=3,payload_kg=5,assumed_frame_cg_height_mm=220,
        proposed_shelf_heights_mm=[250,450,650,850],measured_grandma_reach=False,
        outer_circle_mm=430,maximum_projection_radius_mm=projection*1000,
        driven_wheel_diameter_mm=70,driven_wheel_width_mm=20,
        drive_contacts_m=drive,caster_pivots_m=pivots,caster_trail_mm=15,
        cases=len(rows),sampled_evaluations=len(rows)*64*24,analytic_shape_checks=5,
        findings=[case('all_5kg_high_centred',0,.70,0),
                  case('all_5kg_high_at_rear_edge',2,.35,0),
                  case('all_5kg_high_at_side_edge',2,.35,0),
                  case('all_5kg_high_centred',0,.35,10)],
        limitations=['Assumed masses, contact points and caster trail need measurements',
                    'Flat coplanar wheel contact only; wheel lifting and flexible rack not modelled',
                    'No caster shimmy, impact, shelf strength, traction or braking measurement',
                    'Sampled directions and caster angles are not a continuous worst-case bound',
                    'No selected shelf/base/bracket assembly or validated payload distribution'],
        purchase_approved=False,physical_load_test_passed=False)
    (ROOT/'Tall-rack-stability-results.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
    print(json.dumps(result,indent=2))

if __name__=='__main__': main()
