package in.rollingtable.prototype;

/** No action bypasses qualification. Button source does not make evidence valid. */
public final class MissionController {
  public enum State {IDLE,FOLLOW,LOADING,DELIVER,RETURN,ARRIVED,HOLD}
  public State state=State.IDLE;private State held;
  public String destination;public String reason="idle";
  public static final class Evidence {
    public boolean commissioned,calibrated,commandFresh,obstacleClear,identityValid,distanceValid,poseValid,routeValid,arrivalVerified;
    public long ageMs;public double positionBoundM=Double.POSITIVE_INFINITY;
  }
  public synchronized void stop(){state=State.IDLE;held=null;reason="deliberate stop";}
  public synchronized void load(){state=State.LOADING;held=null;reason="loading at rest";}
  public synchronized void hold(String why){if(state!=State.HOLD){held=(state==State.FOLLOW||state==State.DELIVER||state==State.RETURN)?state:null;}state=State.HOLD;reason=why;}
  public synchronized boolean follow(Evidence e){if(state!=State.IDLE&&state!=State.LOADING&&state!=State.HOLD)return false;state=State.FOLLOW;held=null;return permit(e);}
  public synchronized boolean dispatch(String room,Evidence e){if(state!=State.LOADING||room==null||room.isEmpty())return false;destination=room;state=State.DELIVER;return permit(e);}
  public synchronized boolean returnTo(String room,Evidence e){if(state!=State.ARRIVED||room==null||room.isEmpty())return false;destination=room;state=State.RETURN;return permit(e);}
  public synchronized boolean resume(Evidence e){if(state!=State.HOLD||held==null)return false;state=held;held=null;return permit(e);}
  public synchronized boolean permit(Evidence e){
    if(state==State.IDLE||state==State.LOADING||state==State.ARRIVED||state==State.HOLD)return false;
    boolean common=e!=null&&e.commissioned&&e.calibrated&&e.commandFresh&&e.obstacleClear&&e.ageMs>=0&&e.ageMs<=350;
    boolean specific=common&&(state==State.FOLLOW?(e.identityValid&&e.distanceValid):(e.poseValid&&e.routeValid&&Double.isFinite(e.positionBoundM)&&e.positionBoundM>=0));
    if(!specific){held=state;state=State.HOLD;reason="missing or stale measured evidence";return false;}
    reason="qualified request";return true;
  }
  public synchronized boolean doorway(double measuredGap,double radius,double margin,double geometricAllowance,Evidence e){
    if(e==null||!Double.isFinite(measuredGap+radius+margin+geometricAllowance+e.positionBoundM)||radius<=0||margin<0||geometricAllowance<0||e.positionBoundM<0)return false;
    return permit(e)&&2*radius+2*(margin+e.positionBoundM)+geometricAllowance<=measuredGap;
  }
  public synchronized void arrive(Evidence e){if((state==State.DELIVER||state==State.RETURN)&&permit(e)&&e.arrivalVerified){state=State.ARRIVED;held=null;reason="verified arrival: wait for unloading";}}
}
