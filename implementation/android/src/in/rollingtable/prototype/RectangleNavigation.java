package in.rollingtable.prototype;
import java.util.*;

/** Rectangle route follower for supplied observations. All output remains reference velocity. */
public final class RectangleNavigation {
  public final MissionController mission=new MissionController();
  public final RectanglePlanner planner;private final Map<String,double[]> destinations=new HashMap<>();
  private List<int[]> route=Collections.emptyList();private int waypoint;private String plannedDestination="";private long previous=-1,previousPose=-1;private double previousV;
  public RectangleNavigation(boolean[][] map,double cell,double length,double width,double padding){planner=new RectanglePlanner(map,cell,length,width,padding);}
  public void destination(String name,double x,double y,double yaw){if(name==null||name.isEmpty()||!Double.isFinite(x+y+yaw))throw new IllegalArgumentException("destination");destinations.put(name,new double[]{x,y,yaw});route=Collections.emptyList();}
  public boolean known(String name){return destinations.containsKey(name);}
  public void invalidateRoute(){route=Collections.emptyList();previous=-1;previousV=0;}
  private NavigationEngine.Command hold(String why){previousV=0;mission.hold(why);return new NavigationEngine.Command(0,0,why);}
  public boolean validPose(long now,NavigationEngine.Observation o){return o!=null&&Double.isFinite(o.x+o.y+o.yaw+o.speed+o.bound)&&o.speed>=0&&o.speed<=.13&&o.bound>=0&&o.bound<=.02&&o.timestamp<=now&&now-o.timestamp<=200&&o.timestamp>=previousPose&&planner.clear(o.x,o.y,o.yaw,0);}
  public NavigationEngine.Command tick(long now,NavigationEngine.Observation o,MissionController.Evidence e){
    if(now<0||previous>=0&&(now<=previous||now-previous>350)){previous=now;return hold("control clock stale/reversed");}
    double dt=previous<0?.05:(now-previous)/1000.;previous=now;
    if(!mission.permit(e)){previousV=0;return new NavigationEngine.Command(0,0,mission.reason);}
    if(mission.state==MissionController.State.FOLLOW)return hold("follow uses shared coordinator");
    if(!validPose(now,o)||!Double.isFinite(e.positionBoundM)||e.positionBoundM<0||e.positionBoundM>.02)return hold("invalid/stale pose or bounds");previousPose=o.timestamp;
    double[] goal=destinations.get(mission.destination);if(goal==null)return hold("unknown destination");
    if(Math.hypot(o.x-goal[0],o.y-goal[1])<=.025){previousV=0;if(e.arrivalVerified&&o.speed<=.005)mission.arrive(e);return new NavigationEngine.Command(0,0,"candidate arrival; verified rest required");}
    if(route.isEmpty()||!plannedDestination.equals(mission.destination)){
      route=planner.path(planner.node(o.x,o.y,o.yaw),planner.node(goal[0],goal[1],goal[2]),30000,2000);waypoint=0;plannedDestination=mission.destination;
      if(route.isEmpty())return hold(planner.reason);
    }
    double[] p=planner.pose(route.get(waypoint));double distance=Math.hypot(p[0]-o.x,p[1]-o.y);
    while(waypoint<route.size()-1&&distance<.006&&Math.abs(RectanglePlanner.wrap(p[2]-o.yaw))<.035){p=planner.pose(route.get(++waypoint));distance=Math.hypot(p[0]-o.x,p[1]-o.y);}
    if(distance>planner.cell*2.5)return hold("route deviation requires explicit replan");
    double desired=distance>.006?Math.atan2(p[1]-o.y,p[0]-o.x):p[2],error=RectanglePlanner.wrap(desired-o.yaw);
    double w=Math.max(-.35,Math.min(.35,2*error)),v=Math.abs(error)>.035?0:Math.min(.08,2*distance);v=Math.min(v,previousV+.15*dt);
    // Check the whole interpolated turn and a conservative stopping translation.
    double speed=Math.max(o.speed,v),stop=speed*speed/(2*.20)+speed*.10;
    double[] a={o.x,o.y,o.yaw},b={o.x+stop*Math.cos(o.yaw),o.y+stop*Math.sin(o.yaw),o.yaw+Math.signum(w)*w*w/(2*.8)+w*.10};
    if(!planner.sweep(a,b)||!planner.sweep(a,p))return hold("swept rectangle or stopping corridor blocked");
    previousV=v;return new NavigationEngine.Command(v,w,"rectangle reference command; no physical transport");
  }
}
