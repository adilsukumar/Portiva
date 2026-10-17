package in.rollingtable.prototype;
import java.util.*;

/** Shared navigation-to-velocity integration. No network or motor output; sensor evidence is supplied externally. */
public final class NavigationEngine {
  public static final class Observation {
    public final double x,y,yaw,speed,bound;public final long timestamp;
    public Observation(double x,double y,double yaw,double speed,double bound,long timestamp){this.x=x;this.y=y;this.yaw=yaw;this.speed=speed;this.bound=bound;this.timestamp=timestamp;}
  }
  public static final class Command {
    public final double linear,angular;public final String reason;
    Command(double v,double w,String reason){linear=v;angular=w;this.reason=reason;}
  }
  public final MissionController mission=new MissionController();
  private final double cell,radius,clearance,maxBound;
  private boolean[][] occupancy;private GridPlanner planner;private double plannedBound=Double.NaN;
  private final Map<String,double[]> destinations=new HashMap<>();
  private List<int[]> route=Collections.emptyList();private int waypoint;
  private double goalX=Double.NaN,goalY=Double.NaN;private int mapRevision,routeRevision=-1;
  private double previousV;private long previousTick=-1,previousObservation=-1;
  public NavigationEngine(boolean[][] map,double cell,double radius,double clearance,double maxBound){
    if(!Double.isFinite(cell+radius+clearance+maxBound)||cell<=0||radius<=0||clearance<0||maxBound<0)throw new IllegalArgumentException("metric bounds");
    this.cell=cell;this.radius=radius;this.clearance=clearance;this.maxBound=maxBound;replaceMap(map);
  }
  public void replaceMap(boolean[][] map){
    if(map==null||map.length==0||map[0]==null||map[0].length==0||(long)map.length*map[0].length>2000000)throw new IllegalArgumentException("map");
    boolean[][] copy=new boolean[map.length][];for(int i=0;i<map.length;i++){if(map[i]==null||map[i].length!=map[0].length)throw new IllegalArgumentException("ragged map");copy[i]=map[i].clone();}
    occupancy=copy;mapRevision++;planner=null;route=Collections.emptyList();
  }
  public void destination(String name,double x,double y){if(name==null||name.trim().isEmpty()||!Double.isFinite(x+y))throw new IllegalArgumentException("destination");destinations.put(name,new double[]{x,y});}
  private Command stop(String why,boolean latch){previousV=0;if(latch)mission.hold(why);return new Command(0,0,why);}
  private static double wrap(double a){return Math.atan2(Math.sin(a),Math.cos(a));}
  private int index(double value){return (int)Math.floor(value/cell);}
  public Command tick(long now,Observation pose,MissionController.Evidence evidence){
    if(now<0||previousTick>=0&&(now<=previousTick||now-previousTick>350)){previousTick=now;return stop("control clock stale/reversed",true);}
    double dt=previousTick<0?.05:(now-previousTick)/1000.0;previousTick=now;
    if(!mission.permit(evidence))return stop(mission.reason,false);
    // FOLLOW uses separate identity/range input; this engine does not invent person distance from boxes.
    if(mission.state==MissionController.State.FOLLOW)return stop("follow range/control adapter unavailable",true);
    if(pose==null||!Double.isFinite(pose.x+pose.y+pose.yaw+pose.speed+pose.bound)||pose.speed<0||pose.speed>.13||pose.bound<0||pose.bound>maxBound||pose.timestamp>now||now-pose.timestamp>350||previousObservation>pose.timestamp)return stop("invalid/stale pose/speed",true);
    previousObservation=pose.timestamp;
    // The planner must use at least the evidence bound, never the smaller of contradictory bounds.
    double bound=Math.max(pose.bound,evidence.positionBoundM);
    if(!Double.isFinite(bound)||bound>maxBound)return stop("position uncertainty exceeds admission",true);
    double[] goal=destinations.get(mission.destination);if(goal==null)return stop("unknown destination",true);
    if(planner==null||plannedBound<bound){planner=new GridPlanner(occupancy,cell,radius,clearance,bound);plannedBound=bound;route=Collections.emptyList();}
    if(!planner.free(index(pose.x),index(pose.y)))return stop("pose outside inflated free space",true);
    if(goal[0]!=goalX||goal[1]!=goalY||routeRevision!=mapRevision||route.isEmpty()){
      route=planner.path(index(pose.x),index(pose.y),index(goal[0]),index(goal[1]));waypoint=route.size()>1?1:0;goalX=goal[0];goalY=goal[1];routeRevision=mapRevision;
      if(route.isEmpty())return stop("no admissible path",true);
    }
    if(Math.hypot(pose.x-goalX,pose.y-goalY)<=.04){
      if(evidence.arrivalVerified)mission.arrive(evidence);
      return stop(evidence.arrivalVerified?"verified arrival":"at candidate goal; arrival verification required",false);
    }
    while(waypoint<route.size()-1&&Math.hypot((route.get(waypoint)[0]+.5)*cell-pose.x,(route.get(waypoint)[1]+.5)*cell-pose.y)<cell*.8)waypoint++;
    int[] next=route.get(waypoint);double tx=(next[0]+.5)*cell,ty=(next[1]+.5)*cell;
    // A drifted pose may not shortcut across a blocked corner to its old route.
    int sx=index(pose.x),sy=index(pose.y);int samples=Math.max(1,(int)Math.ceil(Math.hypot(tx-pose.x,ty-pose.y)/(cell*.25)));
    for(int i=0;i<=samples;i++)if(!planner.free(index(pose.x+(tx-pose.x)*i/samples),index(pose.y+(ty-pose.y)*i/samples)))return stop("route connector blocked",true);
    if(Math.hypot(next[0]-sx,next[1]-sy)>4)return stop("route deviation; explicit replan needed",true);
    double error=wrap(Math.atan2(ty-pose.y,tx-pose.x)-pose.yaw);
    double omega=Math.max(-.45,Math.min(.45,1.5*error));double v=Math.abs(error)>.08?0:Math.min(.12,Math.hypot(goalX-pose.x,goalY-pose.y));
    v=Math.min(v,previousV+.15*dt);
    // Reference braking constants belong to the simulator; actual hardware must measure these.
    // Project a stopping corridor at the larger of measured speed and requested speed.
    double speed=Math.max(pose.speed,v),duration=speed/.20+.05,distance=speed*speed/(2*.20)+speed*.05+cell*.5;
    boolean stoppingFree=true;
    for(int j=-2;j<=2;j++)for(int i=1;i<=12;i++){
      double heading=pose.yaw+j*.5*.45*duration;
      if(!planner.free(index(pose.x+distance*i/12*Math.cos(heading)),index(pose.y+distance*i/12*Math.sin(heading))))stoppingFree=false;
    }
    if(!stoppingFree)v=0;
    previousV=v;
    return new Command(v,omega,"virtual/reference navigation command only");
  }
}
