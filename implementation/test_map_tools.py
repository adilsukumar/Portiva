"""Map schema and route acceptance tests. No home data or motors."""
import copy,json
from pathlib import Path
from map_tools import demo_map,validate_map,plan

def main():
    good=demo_map();checks=0
    p,rooms=validate_map(good);checks+=1
    route=plan(good,'pooja','kitchen');assert route['outcome']=='reference_route_found' and not route['motion_enabled'];checks+=1
    cases=[{'schema':'other'},{'motion_enabled':True},{'cell_m':True},{'cell_m':float('nan')},{'grid':[]},
           {'grid':['..','...']},{'grid':['xxx']},{'footprint_m':[.5,.4]},{'padding_m':0},
           {'rooms':{'only':{'cell':[1,1],'heading_index':0}}}]
    for update in cases:
        bad=copy.deepcopy(good);bad.update(update)
        try:validate_map(bad)
        except ValueError:checks+=1
        else:raise AssertionError('Bad map accepted: '+str(update))
    for update in [{'cell':[-1,95],'heading_index':0},{'cell':[48,95],'heading_index':True},{'cell':[127,40],'heading_index':0}]:
        bad=copy.deepcopy(good);bad['rooms']['pooja']=update
        try:validate_map(bad)
        except ValueError:checks+=1
        else:raise AssertionError('Bad room accepted')
    for start,end in [('missing','kitchen'),('pooja','pooja')]:
        try:plan(good,start,end)
        except ValueError:checks+=1
        else:raise AssertionError('Bad route request accepted')
    out=dict(scope='Offline map schema and synthetic route only',passed=True,assertions=checks,physical_home_tested=False,motion_enabled=False)
    root=Path(__file__).resolve().parent
    (root/'Portiva-map-tool-results.json').write_text(json.dumps(out,indent=2),encoding='utf-8')
    print(json.dumps(out))

if __name__=='__main__':main()
