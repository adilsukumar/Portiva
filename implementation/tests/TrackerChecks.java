import in.rollingtable.prototype.TargetTracker;
import java.util.*;
public class TrackerChecks {
  static int checks;
  static void check(boolean b,String n){if(!b)throw new AssertionError(n);checks++;}
  static TargetTracker.Box b(float x,float[] h){return new TargetTracker.Box(x,.1f,x+.2f,.9f,.9f,h);}
  public static void main(String[] args){
    TargetTracker t=new TargetTracker();float[] blue={1,0},red={0,1};
    check(t.update(Arrays.asList(b(.2f,blue)),0).box==null,"selection needed");
    t.enroll(b(.2f,blue),0);
    check(t.update(Arrays.asList(b(.21f,blue),b(.65f,red)),100).box!=null,"distinct distractor rejected");
    check(t.update(Arrays.asList(b(.22f,blue),b(.23f,blue)),200).box==null,"ambiguity holds");
    check(t.update(Arrays.asList(b(.22f,blue)),300).box==null,"no automatic recovery");
    t.enroll(b(.2f,blue),1000);
    check(t.update(Collections.emptyList(),1100).box==null,"occlusion holds");
    check(t.update(Arrays.asList(b(.2f,blue)),1200).box==null,"lost target needs selection");
    t.enroll(b(.2f,blue),2000);check(t.update(Arrays.asList(b(.2f,blue)),2600).box==null,"stale input holds");
    t.enroll(b(.2f,blue),3000);check(t.update(Arrays.asList(b(.7f,blue)),3100).box==null,"teleport holds");
    t.enroll(b(.2f,blue),4000);check(t.update(Arrays.asList(b(.2f,blue)),3900).box==null,"time rollback holds");
    // Counterexample: an identical-looking replacement inside the gate is accepted.
    t.enroll(b(.2f,blue),5000);
    check(t.update(Arrays.asList(b(.21f,blue)),5100).box!=null,"indistinguishable replacement counterexample");
    System.out.println(checks+" Java association checks passed, including explicit identity counterexample; not Grandma identity validation.");
  }
}
