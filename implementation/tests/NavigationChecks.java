import in.rollingtable.prototype.*;
import java.util.*;
public class NavigationChecks {
  static int count;
  static void check(boolean b,String name){if(!b)throw new AssertionError(name);count++;}
  public static void main(String[] args){
    boolean[][] raw=new boolean[100][100];
    for(int y=0;y<100;y++)raw[y][50]=true;
    for(int y=36;y<64;y++)raw[y][50]=false;
    GridPlanner planner=new GridPlanner(raw,.02,.215,.02,.02);
    List<int[]> path=planner.path(20,50,80,50);check(!path.isEmpty(),"adequate doorway passage");
    for(int[] p:path)check(planner.free(p[0],p[1]),"inflated path cells");
    GridPlanner uncertain=new GridPlanner(raw,.02,.215,.02,.08);
    check(uncertain.path(20,50,80,50).isEmpty(),"uncertainty closes narrow passage");
    raw[50][50]=true;GridPlanner blocked=new GridPlanner(raw,.02,.215,.02,.02);
    check(blocked.path(20,50,80,50).isEmpty(),"obstacle closes passage");
    MissionController m=new MissionController();MissionController.Evidence e=new MissionController.Evidence();
    check(!m.follow(e),"no commissioning means hold");check(m.state==MissionController.State.HOLD,"hold latched");
    e.commissioned=e.calibrated=e.commandFresh=e.obstacleClear=e.identityValid=e.distanceValid=e.poseValid=e.routeValid=true;e.ageMs=100;e.positionBoundM=.02;
    check(!m.permit(e),"valid evidence does not automatically resume");check(m.resume(e),"deliberate resume");
    m.load();check(!m.permit(e),"loading no creep");check(m.dispatch("kitchen",e),"dispatch saved room");
    check(m.doorway(.5588,.215,.03,.01,e),"20mm bound doorway");e.positionBoundM=.03;
    check(!m.doorway(.5588,.215,.03,.01,e),"30mm bound rejected");e.positionBoundM=.02;
    m.arrive(e);check(m.state==MissionController.State.DELIVER,"unverified arrival stays delivery");
    e.arrivalVerified=true;m.arrive(e);check(m.state==MissionController.State.ARRIVED,"verified arrival stops");
    check(m.returnTo("pooja",e),"return after unloading");e.ageMs=351;check(!m.permit(e),"stale observation stops");
    e.ageMs=1;check(!m.permit(e),"no reconnect restart");m.stop();check(m.state==MissionController.State.IDLE,"stop");
    System.out.println(count+" Java grid/workflow checks passed; geometry/validity inputs remain supplied, not home validation.");
  }
}
