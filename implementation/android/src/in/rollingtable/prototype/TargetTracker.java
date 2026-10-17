package in.rollingtable.prototype;
import java.util.*;

/** Appearance association is NOT personal identity proof. Ambiguity latches a hold. */
public final class TargetTracker {
  public static final class Box {
    public final float x1,y1,x2,y2,score; public final float[] appearance;
    public Box(float a,float b,float c,float d,float s,float[] h) {
      x1=a;y1=b;x2=c;y2=d;score=s;appearance=h.clone();
    }
    public float cx(){return (x1+x2)/2;} public float cy(){return (y1+y2)/2;}
    public float height(){return y2-y1;}
  }
  public static final class Result {
    public final Box box; public final String reason;
    Result(Box b,String r){box=b;reason=r;}
  }
  private float[] enrolled; private Box previous; private long lastTime;
  private boolean hold=true;
  public synchronized void enroll(Box box,long now) {
    if(box==null || !valid(box))throw new IllegalArgumentException("Valid selected person required");
    enrolled=box.appearance.clone(); previous=box;lastTime=now;hold=false;
  }
  public synchronized void clear(){enrolled=null;previous=null;hold=true;}
  public synchronized Result update(List<Box> boxes,long now) {
    if(enrolled==null)return new Result(null,"select person first");
    if(hold)return new Result(null,"hold: select person again");
    if(now<lastTime || now-lastTime>500){hold=true;return new Result(null,"stale observations");}
    ArrayList<Box> plausible=new ArrayList<>(); ArrayList<Double> costs=new ArrayList<>();
    for(Box b:boxes) {
      if(!valid(b)||b.score<.35f)continue;
      double similarity=similarity(enrolled,b.appearance);
      double displacement=Math.hypot(b.cx()-previous.cx(),b.cy()-previous.cy());
      double ratio=b.height()/previous.height();
      if(similarity<.85 || displacement>.15 || ratio<.65 || ratio>1.55)continue;
      plausible.add(b);costs.add(1-similarity+displacement*.5);
    }
    if(plausible.isEmpty()){hold=true;return new Result(null,"target lost");}
    int best=0;for(int i=1;i<costs.size();i++)if(costs.get(i)<costs.get(best))best=i;
    for(int i=0;i<costs.size();i++)if(i!=best && costs.get(i)-costs.get(best)<.08) {
      hold=true;return new Result(null,"ambiguous people");
    }
    previous=plausible.get(best); lastTime=now;
    return new Result(previous,"appearance association only");
  }
  static boolean valid(Box b) {
    if(!(Float.isFinite(b.score)&&b.x1>=0&&b.y1>=0&&b.x2<=1&&b.y2<=1&&b.x2>b.x1&&b.y2>b.y1))return false;
    float total=0;for(float f:b.appearance){if(!Float.isFinite(f)||f<0)return false;total+=f;}
    return total>.99&&total<1.01;
  }
  static double similarity(float[] a,float[] b) {
    if(a.length!=b.length)return 0;
    double sum=0;for(int i=0;i<a.length;i++)sum+=Math.sqrt(a[i]*b[i]);return Math.min(1,sum);
  }
}
