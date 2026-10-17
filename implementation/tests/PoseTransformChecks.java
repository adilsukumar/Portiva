import in.rollingtable.prototype.PoseTransform;
public class PoseTransformChecks {
  static void near(double a,double b){if(Math.abs(a-b)>1e-9)throw new AssertionError(a+" != "+b);}
  public static void main(String[] args){
    // OpenCV camera axes: right, down, forward. Body forward points along camera Z here.
    double[] rotation={0,0,1,-1,0,0,0,-1,0};double[] forward={0,0,1},offset={.12,.03,.75};
    double[] p=PoseTransform.base(new double[]{1.12,2.03,.75},rotation,forward,offset);near(p[0],1);near(p[1],2);near(p[2],0);near(p[3],0);
    double[] turn={1,0,0,0,0,1,0,-1,0};p=PoseTransform.base(new double[]{.97,2.12,.75},turn,forward,offset);near(p[0],1);near(p[1],2);near(p[2],Math.PI/2);near(p[3],0);
    boolean rejected=false;try{PoseTransform.base(new double[3],new double[9],forward,offset);}catch(IllegalArgumentException e){rejected=true;}if(!rejected)throw new AssertionError("invalid rotation accepted");
    System.out.println("9 camera/base transform checks passed; extrinsic survey and position bound still required.");
  }
}
