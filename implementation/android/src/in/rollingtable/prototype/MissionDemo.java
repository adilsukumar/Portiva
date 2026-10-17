package in.rollingtable.prototype;
import java.util.*;

/** Interactive artificial world using the shared rectangle planner and request coordinator. */
public final class MissionDemo {
  public enum Fault {NONE,OBSTACLE,LOST_PERSON,STALE_POSE}
  public final RectangleNavigation navigation;public final MissionCoordinator coordinator;
  public Fault fault=Fault.NONE;
  public double x=.605,y=1.005,yaw=0,speed=0,angular=0,targetX=1.905,targetY=1.005;
  public long now=100;public String status="Virtual world ready; real motor output is zero";
  private double originX=.605,originY=1.005;private long followingSince;private int requestId;
  private final ArrayList<String> events=new ArrayList<>();
  public static boolean[][] map(){boolean[][] map=new boolean[200][360];for(int yy=0;yy<200;yy++)if(yy<72||yy>=128)map[yy][180]=true;return map;}
  public MissionDemo(){navigation=new RectangleNavigation(map(),.01,.390,.410,.050);navigation.destination("kitchen",3.005,1.005,0);navigation.destination("pooja",.605,1.005,0);coordinator=new MissionCoordinator(navigation);}
  public int physicalLeftPwm(){return 0;}public int physicalRightPwm(){return 0;}
  public List<String> events(){return Collections.unmodifiableList(new ArrayList<>(events));}
  public MissionController.State state(){return navigation.mission.state;}
  private void log(String text){status=text;events.add(now+": "+text);if(events.size()>200)events.remove(0);}
  private MissionController.Evidence evidence(){
    MissionController.Evidence e=new MissionController.Evidence();e.commissioned=e.calibrated=e.commandFresh=e.poseValid=e.routeValid=true;
    e.obstacleClear=fault!=Fault.OBSTACLE;e.identityValid=fault!=Fault.LOST_PERSON;e.distanceValid=true;e.positionBoundM=.02;
    return e;
  }
  private MissionCoordinator.Frame frame(){
    double distance=Math.hypot(targetX-x,targetY-y),bearing=RectanglePlanner.wrap(Math.atan2(targetY-y,targetX-x)-yaw);
    MissionController.Evidence e=evidence();double gx="_origin".equals(navigation.mission.destination)?originX:"pooja".equals(navigation.mission.destination)?.605:3.005;
    double gy="_origin".equals(navigation.mission.destination)?originY:1.005;e.arrivalVerified=Math.hypot(x-gx,y-gy)<=.025;
    return new MissionCoordinator.Frame(now,new NavigationEngine.Observation(x,y,yaw,speed,.02,fault==Fault.STALE_POSE?now-1000:now),angular,
      new FollowController.Observation(distance,bearing,.02,.01,speed,fault==Fault.OBSTACLE?0:2,fault!=Fault.LOST_PERSON,now),e);
  }
  public boolean request(String action,String room){RequestInbox inbox=new RequestInbox();inbox.offer("demo-"+(requestId++),action,room,"offline_demo",now);return accept(inbox.poll(),now);}
  public boolean accept(RequestInbox.Request r,long sourceClock){
    if(r!=null&&r.action.equals("DELIVER")&&state()==MissionController.State.LOADING){originX=x;originY=y;}
    boolean ok=coordinator.accept(r,sourceClock,frame());if(ok&&r.action.equals("FOLLOW")){followingSince=now;targetX=x+1.3;targetY=y;}
    log(coordinator.requestStatus);return ok;
  }
  public boolean follow(){return Math.abs(angular)<1e-5&&request("FOLLOW","");}
  public void load(){request("LOAD","");}
  public boolean send(String room){if(Math.abs(angular)>1e-5||!("kitchen".equals(room)||"pooja".equals(room)))return false;return request("DELIVER",room);}
  public boolean returnAfterUnloading(){return Math.abs(angular)<1e-5&&request("RETURN","");}
  public boolean resume(){boolean ok=coordinator.resume(frame());if(ok)log("Virtual RESUME requested");return ok;}
  public void stop(){request("STOP","");}
  public void setFault(Fault f){fault=Objects.requireNonNull(f);log("Virtual fault set to "+f+"; clearing it does not resume travel");}
  public void step(){
    now+=50;MissionController.State before=state();if(before==MissionController.State.FOLLOW&&now-followingSince<15000)targetX+=.06*.05;
    MissionCoordinator.Output c=coordinator.tick(frame());double dv=c.linear-speed;speed+=Math.signum(dv)*Math.min(Math.abs(dv),(dv<0?.20:.15)*.05);
    double dw=c.angular-angular;angular+=Math.signum(dw)*Math.min(Math.abs(dw),.8*.05);
    double[] a={x,y,yaw};yaw+=angular*.05;x+=speed*Math.cos(yaw)*.05;y+=speed*Math.sin(yaw)*.05;
    if(!navigation.planner.sweep(a,new double[]{x,y,yaw}))throw new IllegalStateException("Virtual rectangle collided; simulation failed");
    if(state()!=before)log("Virtual "+state()+": "+navigation.mission.reason);
    if(c.physicalLeftPwm!=0||c.physicalRightPwm!=0)throw new IllegalStateException("Demo cannot emit physical motion");
  }
}
