package in.rollingtable.prototype;
import java.util.*;

/** One request and evidence path for phone/buttons/replay. Physical transport deliberately absent. */
public final class MissionCoordinator {
  public static final class Frame {
    public final long now;public final double angularSpeed;public final NavigationEngine.Observation pose;public final FollowController.Observation target;public final MissionController.Evidence evidence;
    public Frame(long now,NavigationEngine.Observation pose,double angularSpeed,FollowController.Observation target,MissionController.Evidence evidence){this.now=now;this.pose=pose;this.angularSpeed=angularSpeed;this.target=target;this.evidence=evidence;}
  }
  public static final class Output {
    public final double linear,angular;public final int physicalLeftPwm=0,physicalRightPwm=0;public final String reason;
    Output(double v,double w,String reason){linear=v;angular=w;this.reason=reason;}
  }
  public final RectangleNavigation navigation;public final FollowController following=new FollowController();
  private final LinkedHashSet<String> seen=new LinkedHashSet<>();private boolean hasOrigin;private long lastTick=-1,lastObservation=-1;public String requestStatus="No request";
  public MissionCoordinator(RectangleNavigation navigation){this.navigation=Objects.requireNonNull(navigation);}
  public boolean accept(RequestInbox.Request r,long requestClock,Frame frame){
    if(r==null||frame==null)return reject("missing request or frame");
    if(r.action.equals("STOP")){following.stop();navigation.mission.stop();navigation.invalidateRoute();requestStatus="STOP accepted";return true;}
    if(requestClock<r.receivedMs||requestClock-r.receivedMs>5000||seen.contains(r.id))return reject("expired, future or replayed request");
    seen.add(r.id);if(seen.size()>256)seen.remove(seen.iterator().next());
    if(r.action.equals("LOAD")){following.stop();navigation.mission.load();navigation.invalidateRoute();requestStatus="LOAD accepted; wait for rest";return true;}
    if(!navigation.validPose(frame.now,frame.pose)||frame.pose.speed>.005||!Double.isFinite(frame.angularSpeed)||Math.abs(frame.angularSpeed)>.005)return reject("fresh stationary pose required");
    boolean ok=false;
    if(r.action.equals("FOLLOW")){ok=navigation.mission.follow(frame.evidence);if(ok)following.arm(true);}
    else if(r.action.equals("DELIVER")){
      if(!navigation.known(r.destination)||r.destination.equals("_origin"))return reject("unknown destination");
      if(navigation.mission.state!=MissionController.State.LOADING)return reject("load before dispatch");
      navigation.destination("_origin",frame.pose.x,frame.pose.y,frame.pose.yaw);ok=navigation.mission.dispatch(r.destination,frame.evidence);if(ok)hasOrigin=true;
    }else if(r.action.equals("RETURN")){if(!hasOrigin)return reject("no recorded origin");ok=navigation.mission.returnTo("_origin",frame.evidence);}
    if(ok)navigation.invalidateRoute();requestStatus=ok?"Accepted "+r.action:"Held "+r.action+": "+navigation.mission.reason;return ok;
  }
  private boolean reject(String why){requestStatus="Rejected: "+why;return false;}
  public boolean resume(Frame f){if(f==null||!navigation.validPose(f.now,f.pose)||f.pose.speed>.005||!Double.isFinite(f.angularSpeed)||Math.abs(f.angularSpeed)>.005)return false;navigation.invalidateRoute();boolean ok=navigation.mission.resume(f.evidence);if(ok&&navigation.mission.state==MissionController.State.FOLLOW)following.arm(true);return ok;}
  public Output tick(Frame f){
    if(f==null){navigation.mission.hold("missing frame");return new Output(0,0,"missing frame");}
    if(f.now<0||lastTick>=0&&(f.now<=lastTick||f.now-lastTick>350)){lastTick=f.now;following.stop();navigation.mission.hold("coordinator clock fault");return new Output(0,0,"coordinator clock fault");}lastTick=f.now;
    if(f.pose==null||f.pose.timestamp<lastObservation||!Double.isFinite(f.angularSpeed)||Math.abs(f.angularSpeed)>.45){following.stop();navigation.mission.hold("observation time or angular speed invalid");return new Output(0,0,"observation time or angular speed invalid");}lastObservation=f.pose.timestamp;
    if(navigation.mission.state==MissionController.State.FOLLOW){
      if(!navigation.mission.permit(f.evidence)||!navigation.validPose(f.now,f.pose)){following.stop();navigation.mission.hold("follow evidence or body pose missing");return new Output(0,0,navigation.mission.reason);}
      FollowController.Command c=following.tick(f.now,f.target);if(c.hold){navigation.mission.hold(c.reason);return new Output(0,0,c.reason);}
      double stop=Math.max(c.linear,f.pose.speed);stop=stop*stop/(2*.20)+stop*.10;
      double[] a={f.pose.x,f.pose.y,f.pose.yaw},b={f.pose.x+stop*Math.cos(f.pose.yaw),f.pose.y+stop*Math.sin(f.pose.yaw),f.pose.yaw+c.angular*.20};
      if(!navigation.planner.sweep(a,b)){following.stop();navigation.mission.hold("follow swept body blocked");return new Output(0,0,"follow swept body blocked");}
      return new Output(c.linear,c.angular,c.reason);
    }
    NavigationEngine.Command c=navigation.tick(f.now,f.pose,f.evidence);return new Output(c.linear,c.angular,c.reason);
  }
}
