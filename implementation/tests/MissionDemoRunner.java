import in.rollingtable.prototype.*;
import java.nio.file.*;
import java.util.*;

/** Tests the same interactive mission core used by the Android demo. */
public final class MissionDemoRunner {
  private static int checks;
  private static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);checks++;}
  private static int untilRest(MissionDemo d,int limit){int i=0;for(;i<limit;i++){d.step();check(d.physicalLeftPwm()==0&&d.physicalRightPwm()==0,"physical output zero");if(d.state()==MissionController.State.ARRIVED&&d.speed<1e-6&&Math.abs(d.angular)<1e-6)break;}check(i<limit,"virtual arrival deadline: "+d.state()+" / "+d.status+" x="+d.x+" y="+d.y+" yaw="+d.yaw);return i;}
  public static void main(String[] args)throws Exception{
    MissionDemo d=new MissionDemo();check(!d.send("kitchen"),"loading required");d.load();check(!d.send("unknown"),"known destination required");check(d.send("kitchen"),"send");int outbound=untilRest(d,4000);
    check(d.returnAfterUnloading(),"explicit unloaded return");int inbound=untilRest(d,5000);check(Math.hypot(d.x-.6,d.y-1.005)<.05,"return origin");
    for(MissionDemo.Fault fault:new MissionDemo.Fault[]{MissionDemo.Fault.OBSTACLE,MissionDemo.Fault.STALE_POSE}){
      MissionDemo f=new MissionDemo();f.load();f.send("kitchen");for(int i=0;i<10;i++)f.step();f.setFault(fault);f.step();check(f.state()==MissionController.State.HOLD,"fault hold");f.setFault(MissionDemo.Fault.NONE);for(int i=0;i<20;i++)f.step();check(f.state()==MissionController.State.HOLD,"no auto resume");check(f.resume(),"deliberate resume");f.stop();check(f.state()==MissionController.State.IDLE,"stop");
    }
    MissionDemo f=new MissionDemo();check(f.follow(),"follow");for(int i=0;i<200;i++)f.step();check(f.x>.6,"virtual target followed");f.setFault(MissionDemo.Fault.LOST_PERSON);f.step();check(f.state()==MissionController.State.HOLD,"identity loss hold");f.setFault(MissionDemo.Fault.NONE);for(int i=0;i<20;i++)f.step();check(f.state()==MissionController.State.HOLD,"identity recovery no auto resume");
    String result="{\"scope\":\"Shared Android/host mission simulation; virtual person/pose/obstacles, swept rectangle and shared request coordinator; assumed drive. No physical autonomy\",\"passed\":true,\"assertions\":"+checks+",\"outbound_steps\":"+outbound+",\"return_steps\":"+inbound+",\"physical_motion_enabled\":false}";
    Files.write(Paths.get("implementation/Portiva-mission-demo-results.json"),result.getBytes(java.nio.charset.StandardCharsets.UTF_8));System.out.println(result);
  }
}
