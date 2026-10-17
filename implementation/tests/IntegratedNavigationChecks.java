import in.rollingtable.prototype.*;
import java.util.*;
import java.nio.file.*;

/** Closed-loop virtual drive. Pose/obstacle/arrival measurements are supplied by the simulator, not a camera. */
public class IntegratedNavigationChecks {
  static final double CELL=.01;
  static boolean[][] map(){boolean[][] m=new boolean[200][360];for(int y=0;y<200;y++)if(y<72||y>=128)m[y][180]=true;return m;}
  static MissionController.Evidence evidence(){MissionController.Evidence e=new MissionController.Evidence();e.commissioned=e.calibrated=e.commandFresh=e.obstacleClear=e.poseValid=e.routeValid=true;e.positionBoundM=.02;return e;}
  static NavigationEngine engine(boolean[][] map){NavigationEngine n=new NavigationEngine(map,CELL,.215,.03,.0294);n.destination("kitchen",3.0,1.005);n.destination("pooja",.6,1.005);return n;}
  static NavigationEngine start(boolean[][] map,MissionController.Evidence e){NavigationEngine n=engine(map);n.mission.load();if(!n.mission.dispatch("kitchen",e))throw new AssertionError("dispatch");return n;}
  static void check(boolean okay,String reason){if(!okay)throw new AssertionError(reason);}
  static String drive(String name,boolean misaligned,boolean obstacle,boolean noisy,boolean roundTrip)throws Exception{
    boolean[][] raw=map();MissionController.Evidence e=evidence();NavigationEngine n=start(raw,e);
    double x=.6,y=1.005,yaw=misaligned?Math.PI/2:0,v=0,w=0;Random random=new Random(4081);StringBuilder csv=new StringBuilder("ms,x_m,y_m,yaw_rad,command_v,command_w,state\n");
    double maxSpeed=0;int steps=0;boolean arrived=false,returning=false;GridPlanner ground=new GridPlanner(raw,CELL,.215,0,0);
    for(int i=0;i<5000;i++){
      long now=100+i*50L;double px=x+(noisy?(random.nextDouble()-.5)*.002:0),py=y+(noisy?(random.nextDouble()-.5)*.002:0);
      e.arrivalVerified=Math.hypot(x-(returning?.6:3),y-1.005)<=.04;
      if(obstacle&&i==80){for(int yy=85;yy<=115;yy++)for(int xx=245;xx<=255;xx++)raw[yy][xx]=true;n.replaceMap(raw);ground=new GridPlanner(raw,CELL,.215,0,0);}
      NavigationEngine.Command c=n.tick(now,new NavigationEngine.Observation(px,py,yaw,v,.02,now),e);
      // Illustrative acceleration/braking lag; no empirical motor model is claimed.
      double dv=c.linear-v;v+=Math.signum(dv)*Math.min(Math.abs(dv),(dv<0?.20:.15)*.05);
      double dw=c.angular-w;w+=Math.signum(dw)*Math.min(Math.abs(dw),.8*.05);
      yaw+=w*.05;x+=v*Math.cos(yaw)*.05;y+=v*Math.sin(yaw)*.05;
      maxSpeed=Math.max(maxSpeed,Math.abs(v));steps++;
      csv.append(String.format(Locale.ROOT,"%d,%.5f,%.5f,%.5f,%.5f,%.5f,%s%n",now,x,y,yaw,c.linear,c.angular,n.mission.state));
      check(ground.free((int)(x/CELL),(int)(y/CELL)),name+" body collision at "+x+","+y);
      check(n.mission.state!=MissionController.State.HOLD,name+" unexpected hold at step "+i+" pose "+x+","+y+" yaw "+yaw+": "+c.reason);
      if(n.mission.state==MissionController.State.ARRIVED&&v<1e-6){
        if(roundTrip&&!returning){check(n.mission.returnTo("pooja",e),"explicit unloaded return");returning=true;e.arrivalVerified=false;continue;}
        arrived=true;break;
      }
    }
    check(arrived,name+" did not arrive");check(maxSpeed<=.120001,name+" speed limit");
    Files.write(Paths.get("implementation",name+"-trace.csv"),csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    return "{\"scenario\":\""+name+"\",\"outcome\":\"arrived\",\"steps\":"+steps+",\"maximum_virtual_speed_m_s\":"+maxSpeed+"}";
  }
  public static void main(String[] args)throws Exception{
    ArrayList<String> results=new ArrayList<>();
    results.add(drive("closed-loop-straight",false,false,false,false));
    results.add(drive("closed-loop-turned-start",true,false,false,false));
    results.add(drive("closed-loop-obstacle-detour",false,true,false,false));
    results.add(drive("closed-loop-noisy-pose",false,false,true,false));
    results.add(drive("closed-loop-round-trip",false,false,false,true));
    for(String fault:new String[]{"stale","uncertain","lost-pose","obstacle","blocked-door","unknown-room","time-reversal"}){
      boolean[][] raw=map();MissionController.Evidence e=evidence();NavigationEngine n=start(raw,e);
      NavigationEngine.Observation p=new NavigationEngine.Observation(.6,1.005,0,0,.02,100);
      n.tick(100,p,e);long now=150;
      if(fault.equals("stale")){now=500;p=new NavigationEngine.Observation(.6,1.005,0,0,.02,100);}
      if(fault.equals("uncertain"))p=new NavigationEngine.Observation(.6,1.005,0,0,.04,150);
      if(fault.equals("lost-pose"))p=null;
      if(fault.equals("obstacle"))e.obstacleClear=false;
      if(fault.equals("blocked-door")){for(int yy=72;yy<128;yy++)raw[yy][180]=true;n.replaceMap(raw);}
      if(fault.equals("unknown-room"))n.mission.destination="unsurveyed room";
      if(fault.equals("time-reversal"))now=99;
      if(p!=null&&!fault.equals("stale")&&!fault.equals("uncertain"))p=new NavigationEngine.Observation(p.x,p.y,p.yaw,p.speed,p.bound,now);
      NavigationEngine.Command c=n.tick(now,p,e);check(c.linear==0&&c.angular==0,fault+" zero command");check(n.mission.state==MissionController.State.HOLD,fault+" hold");
      e.obstacleClear=true;n.tick(now+50,new NavigationEngine.Observation(.6,1.005,0,0,.02,now+50),e);check(n.mission.state==MissionController.State.HOLD,fault+" must not automatically resume");
      results.add("{\"scenario\":\""+fault+"\",\"outcome\":\"latched_hold\"}");
    }
    String json="{\"scope\":\"closed-loop Java virtual drive with supplied pose, sensor/arrival truth and assumed kinematics; not camera or loaded hardware validation\",\"motion_output_connected\":false,\"scenarios\":["+String.join(",",results)+"]}";
    Files.write(Paths.get("implementation/Integrated-navigation-results.json"),json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    System.out.println(results.size()+" integrated navigation scenarios passed (5 completed virtual missions including round trip, 7 fault holds); no physical robot test.");
  }
}

