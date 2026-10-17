package in.rollingtable.prototype;
import android.graphics.Bitmap;
import org.opencv.android.*;
import org.opencv.core.*;
import org.opencv.features2d.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.calib3d.Calib3d;
import java.util.*;

/** Natural-feature pose candidates from surveyed rectangles. NOT automatic SLAM or a measured error bound. */
public final class PlanarLocalizer implements AutoCloseable {
  public static final class Pose {
    public final String patch;public final double[] centre,cameraToWorld;public final double rms;public final int inliers;
    Pose(String p,double[] c,double[] rotation,double r,int n){patch=p;centre=c;cameraToWorld=rotation;rms=r;inliers=n;}
  }
  private static final class Patch {
    String name;Mat descriptors;Point3[] world;Point[] pixels;double referenceArea;
  }
  private final Mat K;private final MatOfDouble distortion;private final ORB orb;
  private final DescriptorMatcher matcher;private final ArrayList<Patch> patches=new ArrayList<>();
  public PlanarLocalizer(double[] intrinsics,double[] lens){
    if(!OpenCVLoader.initLocal())throw new IllegalStateException("OpenCV load failed");
    if(intrinsics.length!=9||intrinsics[0]<=0||intrinsics[4]<=0)throw new IllegalArgumentException("Measured intrinsics required");
    for(double x:intrinsics)if(!Double.isFinite(x))throw new IllegalArgumentException("Invalid intrinsics");
    for(double x:lens)if(!Double.isFinite(x))throw new IllegalArgumentException("Invalid distortion");
    K=new Mat(3,3,CvType.CV_64F);K.put(0,0,intrinsics);distortion=new MatOfDouble(lens);
    orb=ORB.create(1200);orb.setFastThreshold(12);matcher=DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING);
  }
  private static Mat gray(Bitmap bitmap){Mat rgba=new Mat(),g=new Mat();Utils.bitmapToMat(bitmap,rgba);Imgproc.cvtColor(rgba,g,Imgproc.COLOR_RGBA2GRAY);rgba.release();return g;}
  public void teach(String name,Bitmap undistortedReference,Point[] corners,Point3[] measuredCorners){
    if(corners.length!=4||measuredCorners.length!=4)throw new IllegalArgumentException("Four surveyed rectangle corners required");
    Point3 a=measuredCorners[0],b=measuredCorners[1],c=measuredCorners[2],d=measuredCorners[3];
    double[] ex={b.x-a.x,b.y-a.y,b.z-a.z},ey={d.x-a.x,d.y-a.y,d.z-a.z};
    double[] cross={ex[1]*ey[2]-ex[2]*ey[1],ex[2]*ey[0]-ex[0]*ey[2],ex[0]*ey[1]-ex[1]*ey[0]};
    if(Math.sqrt(cross[0]*cross[0]+cross[1]*cross[1]+cross[2]*cross[2])<1e-5||Math.sqrt(Math.pow(c.x-a.x-ex[0]-ey[0],2)+Math.pow(c.y-a.y-ex[1]-ey[1],2)+Math.pow(c.z-a.z-ex[2]-ey[2],2))>.005)throw new IllegalArgumentException("Non-rectangular/degenerate survey");
    Mat image=gray(undistortedReference),mask=Mat.zeros(image.rows(),image.cols(),CvType.CV_8U),desc=new Mat();
    MatOfPoint poly=new MatOfPoint(corners);Imgproc.fillConvexPoly(mask,poly,new Scalar(255));MatOfKeyPoint keys=new MatOfKeyPoint();
    orb.detectAndCompute(image,mask,keys,desc);KeyPoint[] points=keys.toArray();
    if(points.length<20){image.release();mask.release();desc.release();keys.release();poly.release();throw new IllegalArgumentException("Insufficient natural texture");}
    MatOfPoint2f src=new MatOfPoint2f(corners),dst=new MatOfPoint2f(new Point(0,0),new Point(1,0),new Point(1,1),new Point(0,1));
    Mat transform=Imgproc.getPerspectiveTransform(src,dst);Point[] pixel=new Point[points.length];for(int i=0;i<points.length;i++)pixel[i]=points[i].pt;
    MatOfPoint2f observed=new MatOfPoint2f(pixel),local=new MatOfPoint2f();Core.perspectiveTransform(observed,local,transform);Point[] uv=local.toArray();
    Patch p=new Patch();p.name=name;p.descriptors=desc;p.pixels=pixel;p.world=new Point3[uv.length];p.referenceArea=image.total();
    for(int i=0;i<uv.length;i++)p.world[i]=new Point3(a.x+uv[i].x*ex[0]+uv[i].y*ey[0],a.y+uv[i].x*ex[1]+uv[i].y*ey[1],a.z+uv[i].x*ex[2]+uv[i].y*ey[2]);
    patches.add(p);for(Mat m:new Mat[]{image,mask,keys,poly,src,dst,transform,observed,local})m.release();
  }
  public Pose locate(Bitmap bitmap){
    Mat image=gray(bitmap),empty=new Mat(),des=new Mat();MatOfKeyPoint keys=new MatOfKeyPoint();orb.detectAndCompute(image,empty,keys,des);
    ArrayList<Pose> candidates=new ArrayList<>();KeyPoint[] observed=keys.toArray();
    try {
      if(des.empty())return null;
      for(Patch patch:patches){ArrayList<MatOfDMatch> pairs=new ArrayList<>();matcher.knnMatch(des,patch.descriptors,pairs,2);HashMap<Integer,DMatch> unique=new HashMap<>();
        for(MatOfDMatch pair:pairs){DMatch[] m=pair.toArray();if(m.length==2&&m[0].distance<.70*m[1].distance){DMatch previous=unique.get(m[0].trainIdx);if(previous==null||m[0].distance<previous.distance)unique.put(m[0].trainIdx,m[0]);}pair.release();}
        if(unique.size()<20)continue;ArrayList<DMatch> good=new ArrayList<>(unique.values());
        Point3[] obj=new Point3[good.size()];Point[] img=new Point[good.size()];for(int i=0;i<good.size();i++){DMatch m=good.get(i);obj[i]=patch.world[m.trainIdx];img[i]=observed[m.queryIdx].pt;}
        MatOfPoint3f object=new MatOfPoint3f(obj);MatOfPoint2f pixels=new MatOfPoint2f(img);Mat rv=new Mat(),tv=new Mat(),inside=new Mat();
        try {
          if(!Calib3d.solvePnPRansac(object,pixels,K,distortion,rv,tv,false,150,2f,.999,inside,Calib3d.SOLVEPNP_ITERATIVE)||inside.rows()<20||inside.rows()<good.size()*.6)continue;
          int count=inside.rows();Point3[] io=new Point3[count];Point[] ii=new Point[count],ref=new Point[count];
          for(int i=0;i<count;i++){int index=(int)inside.get(i,0)[0];io[i]=obj[index];ii[i]=img[index];ref[i]=patch.pixels[good.get(index).trainIdx];}
          MatOfPoint contour=new MatOfPoint(ref);MatOfInt hull=new MatOfInt();Imgproc.convexHull(contour,hull);int[] hi=hull.toArray();Point[] hp=new Point[hi.length];for(int i=0;i<hi.length;i++)hp[i]=ref[hi[i]];MatOfPoint hullContour=new MatOfPoint(hp);
          double area=Imgproc.contourArea(hullContour);contour.release();hull.release();hullContour.release();if(area<.15*patch.referenceArea)continue;
          MatOfPoint3f inobj=new MatOfPoint3f(io);MatOfPoint2f inimg=new MatOfPoint2f(ii);
          try {
            Calib3d.solvePnPRefineLM(inobj,inimg,K,distortion,rv,tv);double rms=error(inobj,inimg,rv,tv);if(!Double.isFinite(rms)||rms>1.6)continue;
            double[] centre=centre(rv,tv);if(!finite(centre)||!front(io,rv,tv))continue;
            ArrayList<Mat> rotations=new ArrayList<>(),translations=new ArrayList<>();Calib3d.solvePnPGeneric(inobj,inimg,K,distortion,rotations,translations,false,Calib3d.SOLVEPNP_IPPE);
            boolean ambiguous=false;for(int i=0;i<rotations.size();i++){
              double err=error(inobj,inimg,rotations.get(i),translations.get(i));double[] alternative=centre(rotations.get(i),translations.get(i));
              if(Double.isFinite(err)&&Math.abs(err-rms)<.25&&distance(centre,alternative)>.03&&front(io,rotations.get(i),translations.get(i)))ambiguous=true;
            }
            for(Mat m:rotations)m.release();for(Mat m:translations)m.release();if(ambiguous)continue;
            candidates.add(new Pose(patch.name,centre,cameraToWorld(rv),rms,count));
          }finally{inobj.release();inimg.release();}
        }finally{object.release();pixels.release();rv.release();tv.release();inside.release();}
      }
      candidates.sort((a,b)->Double.compare(b.inliers/(1+b.rms),a.inliers/(1+a.rms)));
      if(candidates.isEmpty())return null;Pose best=candidates.get(0);
      if(candidates.size()>1){Pose second=candidates.get(1);if(second.inliers/(1+second.rms)>=.85*best.inliers/(1+best.rms)&&distance(best.centre,second.centre)>.03)return null;}
      return best;
    }finally{image.release();empty.release();des.release();keys.release();}
  }
  private double error(MatOfPoint3f obj,MatOfPoint2f actual,Mat rv,Mat tv){MatOfPoint2f proj=new MatOfPoint2f();Calib3d.projectPoints(obj,rv,tv,K,distortion,proj);Point[] a=actual.toArray(),b=proj.toArray();double sum=0;for(int i=0;i<a.length;i++)sum+=Math.pow(a[i].x-b[i].x,2)+Math.pow(a[i].y-b[i].y,2);proj.release();return Math.sqrt(sum/a.length);}
  private static double[] centre(Mat rv,Mat tv){Mat r=new Mat();Calib3d.Rodrigues(rv,r);double[] c=new double[3];for(int i=0;i<3;i++)for(int j=0;j<3;j++)c[i]-=r.get(j,i)[0]*tv.get(j,0)[0];r.release();return c;}
  private static double[] cameraToWorld(Mat rv){Mat r=new Mat();Calib3d.Rodrigues(rv,r);double[] out=new double[9];for(int i=0;i<3;i++)for(int j=0;j<3;j++)out[3*i+j]=r.get(j,i)[0];r.release();return out;}
  private static boolean front(Point3[] obj,Mat rv,Mat tv){Mat r=new Mat();Calib3d.Rodrigues(rv,r);boolean okay=true;for(Point3 p:obj)if(r.get(2,0)[0]*p.x+r.get(2,1)[0]*p.y+r.get(2,2)[0]*p.z+tv.get(2,0)[0]<=0)okay=false;r.release();return okay;}
  private static boolean finite(double[] a){for(double x:a)if(!Double.isFinite(x))return false;return true;}
  private static double distance(double[] a,double[] b){double s=0;for(int i=0;i<3;i++)s+=Math.pow(a[i]-b[i],2);return Math.sqrt(s);}
  public void close(){for(Patch p:patches)p.descriptors.release();K.release();distortion.release();orb.clear();matcher.clear();}
}
