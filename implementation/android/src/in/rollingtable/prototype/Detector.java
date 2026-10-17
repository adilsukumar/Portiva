package in.rollingtable.prototype;
import ai.onnxruntime.*;
import android.content.Context;
import android.graphics.*;
import java.io.*;
import java.nio.*;
import java.util.*;

/** YOLO11n, fixed 320 square letterboxed RGB, class 0 person, local ONNX inference. */
public final class Detector implements AutoCloseable {
  private final OrtEnvironment env; private final OrtSession session;
  public Detector(Context context)throws Exception {
    byte[] bytes;try(InputStream s=context.getAssets().open("yolo11n.onnx")){bytes=VisionService.boundedRead(s,20000000);}
    env=OrtEnvironment.getEnvironment();
    OrtSession.SessionOptions opts=new OrtSession.SessionOptions();opts.setIntraOpNumThreads(2);
    session=env.createSession(bytes,opts);opts.close();
  }
  public List<TargetTracker.Box> detect(Bitmap image)throws Exception {
    int n=320,w=image.getWidth(),h=image.getHeight();
    float scale=Math.min((float)n/w,(float)n/h);int rw=Math.round(w*scale),rh=Math.round(h*scale);
    int px=(n-rw)/2,py=(n-rh)/2;
    Bitmap square=Bitmap.createBitmap(n,n,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(square);
    c.drawColor(Color.rgb(114,114,114));
    c.drawBitmap(image,null,new Rect(px,py,px+rw,py+rh),new Paint(Paint.FILTER_BITMAP_FLAG));
    int[] pixels=new int[n*n];square.getPixels(pixels,0,n,0,0,n,n);
    float[] data=new float[3*n*n];for(int i=0;i<pixels.length;i++){
      data[i]=Color.red(pixels[i])/255f;data[i+n*n]=Color.green(pixels[i])/255f;data[i+2*n*n]=Color.blue(pixels[i])/255f;
    }square.recycle();
    ArrayList<TargetTracker.Box> boxes=new ArrayList<>();
    try(OnnxTensor in=OnnxTensor.createTensor(env,FloatBuffer.wrap(data),new long[]{1,3,n,n});
        OrtSession.Result result=session.run(Collections.singletonMap(session.getInputNames().iterator().next(),in))) {
      float[][] output=((float[][][])result.get(0).getValue())[0];
      if(output.length!=84)throw new IOException("Unexpected YOLO output layout");
      for(int i=0;i<output[0].length;i++) {
        float score=output[4][i]; if(!Float.isFinite(score)||score<.35f)continue;
        boolean personBest=true;for(int k=5;k<84;k++)if(output[k][i]>score){personBest=false;break;}
        if(!personBest)continue;
        float x1=Math.max(0,(output[0][i]-output[2][i]/2-px)/scale/w);
        float y1=Math.max(0,(output[1][i]-output[3][i]/2-py)/scale/h);
        float x2=Math.min(1,(output[0][i]+output[2][i]/2-px)/scale/w);
        float y2=Math.min(1,(output[1][i]+output[3][i]/2-py)/scale/h);
        if(!Float.isFinite(x1+y1+x2+y2)||x2<=x1||y2<=y1)continue;
        boxes.add(new TargetTracker.Box(x1,y1,x2,y2,score,histogram(image,x1,y1,x2,y2)));
      }
    }
    boxes.sort((a,b)->Float.compare(b.score,a.score));ArrayList<TargetTracker.Box> kept=new ArrayList<>();
    for(TargetTracker.Box b:boxes){boolean overlap=false;for(TargetTracker.Box a:kept)if(iou(a,b)>.45)overlap=true;if(!overlap)kept.add(b);}
    return kept;
  }
  private static float[] histogram(Bitmap image,float x1,float y1,float x2,float y2) {
    float[] out=new float[64];int count=0,w=image.getWidth(),h=image.getHeight();
    int ax=Math.max(0,(int)((x1+.15*(x2-x1))*w)),bx=Math.min(w,(int)((x2-.15*(x2-x1))*w));
    int ay=Math.max(0,(int)((y1+.2*(y2-y1))*h)),by=Math.min(h,(int)((y1+.8*(y2-y1))*h));
    for(int y=ay;y<by;y+=2)for(int x=ax;x<bx;x+=2){int rgb=image.getPixel(x,y);
      int bin=(Color.red(rgb)/64)*16+(Color.green(rgb)/64)*4+Color.blue(rgb)/64;out[bin]++;count++;}
    if(count==0)throw new IllegalArgumentException("Empty person crop");
    for(int i=0;i<64;i++)out[i]/=count;return out;
  }
  static float iou(TargetTracker.Box a,TargetTracker.Box b) {
    float q=Math.max(0,Math.min(a.x2,b.x2)-Math.max(a.x1,b.x1))*Math.max(0,Math.min(a.y2,b.y2)-Math.max(a.y1,b.y1));
    return q/((a.x2-a.x1)*(a.y2-a.y1)+(b.x2-b.x1)*(b.y2-b.y1)-q);
  }
  public void close()throws Exception{session.close();}
}
