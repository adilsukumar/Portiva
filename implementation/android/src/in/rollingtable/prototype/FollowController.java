package in.rollingtable.prototype;

/** Reference follow controller. Requires independently qualified identity, range and obstacle inputs.
 * It has no camera/radio/motor adapter and cannot certify that the selected person is Grandma. */
public final class FollowController {
  public static final class Observation {
    public final double distance,bearing,distanceError,bearingError,speed,frontClearance;
    public final boolean identityQualified;public final long timestamp;
    public Observation(double distance,double bearing,double distanceError,double bearingError,double speed,double frontClearance,boolean identityQualified,long timestamp){
      this.distance=distance;this.bearing=bearing;this.distanceError=distanceError;this.bearingError=bearingError;this.speed=speed;this.frontClearance=frontClearance;this.identityQualified=identityQualified;this.timestamp=timestamp;
    }
  }
  public static final class Command {
    public final double linear,angular;public final boolean hold;public final String reason;
    Command(double v,double w,boolean hold,String reason){linear=v;angular=w;this.hold=hold;this.reason=reason;}
  }
  private boolean armed;private long previous=-1;private double previousV;private String reason="not deliberately armed";
  public void stop(){armed=false;previousV=0;reason="deliberate stop";}
  public boolean arm(boolean commissioned){stop();previous=-1;if(commissioned){armed=true;reason="armed at rest";}return armed;}
  private Command hold(String why){stop();reason=why;return new Command(0,0,true,why);}
  public Command tick(long now,Observation o){
    if(!armed)return new Command(0,0,true,reason);
    if(now<0||previous>=0&&(now<=previous||now-previous>350))return hold("control clock stale or reversed");
    double dt=previous<0?.05:(now-previous)/1000.0;previous=now;
    if(o==null||o.timestamp>now||now-o.timestamp>200)return hold("target observation missing or stale");
    if(!Double.isFinite(o.distance+o.bearing+o.distanceError+o.bearingError+o.speed+o.frontClearance)||o.distance<=0||o.distanceError<0||o.distanceError>.05||o.bearingError<0||o.bearingError>.03||o.speed<0||o.speed>.13||o.frontClearance<0)return hold("unqualified range bearing or speed");
    if(!o.identityQualified)return hold("identity lost or ambiguous");
    if(Math.abs(o.bearing)+o.bearingError>.60)return hold("target outside admitted view");
    if(o.distance+o.distanceError>2.0)return hold("target too far; do not chase");
    double lower=o.distance-o.distanceError;
    // 0.20m/s^2 braking is a simulator assumption, not a measured hardware fact.
    double stopping=o.speed*o.speed/(2*.20)+o.speed*((now-o.timestamp)/1000.0+.05);
    if(lower<.60+stopping)return hold("target too close for stopping margin");
    if(o.frontClearance<.25+stopping)return hold("obstacle stopping margin");
    double error=lower-1.0;double v=error<=.04?0:Math.min(.12,.35*error);
    if(Math.abs(o.bearing)+o.bearingError>.15)v=0;
    v=Math.min(v,previousV+.15*dt);previousV=v;
    return new Command(v,Math.max(-.4,Math.min(.4,1.2*o.bearing)),false,error<=.04?"at following distance":"reference following command only");
  }
}
