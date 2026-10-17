import in.rollingtable.prototype.FollowController;
import java.nio.file.*;
import java.util.*;
public class FollowControllerChecks {
  static int checks;static void check(boolean b,String reason){if(!b)throw new AssertionError(reason);checks++;}
  static FollowController.Observation obs(double distance,double bearing,double speed,long time){return new FollowController.Observation(distance,bearing,.02,.01,speed,2.0,true,time);}
  public static void main(String[] args)throws Exception{
    FollowController c=new FollowController();check(c.tick(0,obs(1.3,0,0,0)).hold,"must deliberately arm");check(!c.arm(false),"must commission");check(c.arm(true),"virtual commissioning");
    StringBuilder trace=new StringBuilder("ms,robot_x,target_x,target_distance,command_speed,actual_speed,hold\n");
    double robot=0,target=1.2,speed=0,minDistance=100,maxSpeed=0;boolean finalHold=false;
    for(int i=0;i<600;i++){
      long now=i*50L;double targetSpeed=i<300?.06:0;target+=targetSpeed*.05;
      FollowController.Command q=c.tick(now,obs(target-robot,0,speed,now));check(!q.hold,"slow virtual walk should not hold");
      double change=q.linear-speed;speed+=Math.signum(change)*Math.min(Math.abs(change),(change<0?.2:.15)*.05);robot+=speed*.05;
      minDistance=Math.min(minDistance,target-robot);maxSpeed=Math.max(maxSpeed,speed);finalHold=q.hold;
      trace.append(String.format(Locale.ROOT,"%d,%.6f,%.6f,%.6f,%.6f,%.6f,%b%n",now,robot,target,target-robot,q.linear,speed,q.hold));
    }
    check(!finalHold&&minDistance>.60&&maxSpeed<=.12&&speed<.001,"virtual walk and stop separation");
    Files.write(Paths.get("implementation/follow-slow-walk-trace.csv"),trace.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    String[] faults={"lost","identity","stale","future","distance uncertainty","view","too close","too far","obstacle","speed","clock"};
    for(String fault:faults){
      c=new FollowController();c.arm(true);c.tick(100,obs(1.3,0,0,100));long now=150;FollowController.Observation o=obs(1.3,0,0,150);
      if(fault.equals("lost"))o=null;
      if(fault.equals("identity"))o=new FollowController.Observation(1.3,0,.02,.01,0,2,false,150);
      if(fault.equals("stale"))o=obs(1.3,0,0,0); // use 201ms age below
      if(fault.equals("stale"))now=201;
      if(fault.equals("future"))o=obs(1.3,0,0,151);
      if(fault.equals("distance uncertainty"))o=new FollowController.Observation(1.3,0,.1,.01,0,2,true,150);
      if(fault.equals("view"))o=obs(1.3,.61,0,150);
      if(fault.equals("too close"))o=obs(.55,0,0,150);
      if(fault.equals("too far"))o=obs(2.1,0,0,150);
      if(fault.equals("obstacle"))o=new FollowController.Observation(1.3,0,.02,.01,.1,.20,true,150);
      if(fault.equals("speed"))o=obs(1.3,0,.2,150);
      if(fault.equals("clock"))now=99;
      FollowController.Command q=c.tick(now,o);check(q.hold&&q.linear==0&&q.angular==0,fault+" zero hold");
      check(c.tick(now+50,obs(1.3,0,0,now+50)).hold,fault+" must not resume on data recovery");
    }
    c=new FollowController();c.arm(true);check(c.tick(100,obs(1.3,.3,0,100)).linear==0,"align before moving");
    // Demonstrate the missing identity sensor cannot be repaired by motion control.
    c=new FollowController();c.arm(true);boolean wrongIdentityCouldMove=c.tick(100,obs(1.4,0,0,100)).linear>0;
    check(wrongIdentityCouldMove,"known false identity input counterexample");
    String json="{\"scope\":\"reference follow controller with simulator-supplied identity/range/bearing/speed; no Grandma or physical robot test\",\"checks_passed\":"+checks+",\"completed_virtual_slow_walk\":true,\"fault_scenarios_held\":11,\"minimum_virtual_separation_m\":"+minDistance+",\"maximum_virtual_speed_m_s\":"+maxSpeed+",\"wrong_identity_claim_can_still_move\":true,\"live_following_qualified\":false}";
    Files.write(Paths.get("implementation/Follow-controller-results.json"),json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    System.out.println(checks+" follow-controller assertions passed; 11 fault holds. False identity input remains an explicit counterexample.");
  }
}
