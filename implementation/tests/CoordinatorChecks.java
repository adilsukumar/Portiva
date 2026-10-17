import in.rollingtable.prototype.*;

/** Actual shared coordinator checks; no assertion grants real commissioning. */
public final class CoordinatorChecks {
  private static int checks,id;private static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
  private static MissionCoordinator.Frame frame(long now,boolean commissioned,double angular){
    MissionController.Evidence e=new MissionController.Evidence();e.commissioned=commissioned;e.calibrated=e.commandFresh=e.obstacleClear=e.poseValid=e.routeValid=e.identityValid=e.distanceValid=true;e.positionBoundM=.02;
    return new MissionCoordinator.Frame(now,new NavigationEngine.Observation(.605,1.005,0,0,.02,now),angular,new FollowController.Observation(1.3,0,.02,.01,0,2,true,now),e);
  }
  private static RequestInbox.Request request(String action,long time){RequestInbox q=new RequestInbox();q.offer("check-"+(id++),action,action.equals("DELIVER")?"kitchen":"","test",time);return q.poll();}
  private static MissionCoordinator core(){RectangleNavigation n=new RectangleNavigation(MissionDemo.map(),.01,.39,.41,.05);n.destination("kitchen",3.005,1.005,0);return new MissionCoordinator(n);}
  public static void main(String[] args){
    MissionCoordinator c=core();MissionCoordinator.Frame f=frame(100,true,0);
    check(!c.accept(request("DELIVER",100),100,f),"loading mandatory");check(!c.accept(request("RETURN",100),100,f),"origin required");
    check(!c.accept(request("FOLLOW",101),100,f),"future request");check(!c.accept(request("FOLLOW",100),5101,f),"expired request");
    RequestInbox.Request q=request("FOLLOW",100);check(c.accept(q,100,f),"qualified simulation follow");check(!c.accept(q,100,f),"duplicate replay");
    MissionCoordinator.Output o=c.tick(frame(150,true,0));check(o.linear>0&&o.physicalLeftPwm==0&&o.physicalRightPwm==0,"reference command only");
    o=c.tick(frame(200,false,0));check(o.linear==0&&c.navigation.mission.state==MissionController.State.HOLD,"uncommissioned evidence stops");
    o=c.tick(frame(250,true,0));check(o.linear==0&&c.navigation.mission.state==MissionController.State.HOLD,"no auto restart");
    check(c.accept(request("STOP",0),9000,frame(300,false,0)),"stale STOP still stops");check(c.navigation.mission.state==MissionController.State.IDLE,"STOP state");
    check(!c.accept(request("FOLLOW",300),300,frame(300,true,.01)),"rotation must stop first");
    check(!c.accept(request("FOLLOW",300),300,frame(300,false,0)),"cannot commission by request");
    c=core();check(c.accept(request("FOLLOW",100),100,f),"second follow");o=c.tick(frame(150,true,0));o=c.tick(frame(150,true,0));check(o.linear==0&&c.navigation.mission.state==MissionController.State.HOLD,"repeated clock hold");
    System.out.println(checks+" shared request coordinator checks passed; physical outputs remain zero.");
  }
}
