package in.rollingtable.prototype;

/** Convert surveyed camera world pose to level base pose; XY horizontal, Z up. No inferred error bound. */
public final class PoseTransform {
  private PoseTransform(){}
  public static double[] base(double[] centre,double[] cameraToWorld,double[] bodyForwardInCamera,double[] cameraCentreInBody){
    if(centre==null||cameraToWorld==null||bodyForwardInCamera==null||cameraCentreInBody==null||centre.length!=3||cameraToWorld.length!=9||bodyForwardInCamera.length!=3||cameraCentreInBody.length!=3)throw new IllegalArgumentException("measured extrinsics required");
    for(double[] a:new double[][]{centre,cameraToWorld,bodyForwardInCamera,cameraCentreInBody})for(double x:a)if(!Double.isFinite(x))throw new IllegalArgumentException("nonfinite pose/extrinsics");
    for(int a=0;a<3;a++)for(int b=0;b<3;b++){double dot=0;for(int j=0;j<3;j++)dot+=cameraToWorld[3*a+j]*cameraToWorld[3*b+j];if(Math.abs(dot-(a==b?1:0))>1e-5)throw new IllegalArgumentException("rotation not orthonormal");}
    double[] r=cameraToWorld;double det=r[0]*(r[4]*r[8]-r[5]*r[7])-r[1]*(r[3]*r[8]-r[5]*r[6])+r[2]*(r[3]*r[7]-r[4]*r[6]);
    if(Math.abs(det-1)>1e-5)throw new IllegalArgumentException("reflected rotation");
    double norm=0;for(double x:bodyForwardInCamera)norm+=x*x;if(Math.abs(norm-1)>1e-5)throw new IllegalArgumentException("forward vector must be unit length");
    double[] f=new double[3];for(int i=0;i<3;i++)for(int j=0;j<3;j++)f[i]+=r[3*i+j]*bodyForwardInCamera[j];
    if(Math.abs(f[2])>.08)throw new IllegalArgumentException("base not level / wrong world axes");
    double yaw=Math.atan2(f[1],f[0]),co=Math.cos(yaw),si=Math.sin(yaw);
    return new double[]{centre[0]-co*cameraCentreInBody[0]+si*cameraCentreInBody[1],centre[1]-si*cameraCentreInBody[0]-co*cameraCentreInBody[1],yaw,centre[2]-cameraCentreInBody[2]};
  }
}
