package in.rollingtable.prototype;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Explicit local-camera bench. Never connects to an Uno or starts motion. */
public final class PhoneCameraActivity extends Activity {
  private PhoneCamera camera;private TextView status;private ImageView image;
  private volatile int generation;private volatile int frames;private final Handler ui=new Handler();
  private final Runnable ageCheck=new Runnable(){public void run(){
    if(lastAt>0&&SystemClock.elapsedRealtime()-lastAt>2000)status.setText("No fresh camera result. Motion disabled. Stop and restart the bench if needed.");
    ui.postDelayed(this,500);
  }};
  private long lastAt;
  public void onCreate(Bundle saved){super.onCreate(saved);LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setPadding(20,30,20,20);
    TextView title=new TextView(this);title.setText("Portiva phone camera bench");title.setTextSize(22);box.addView(title);
    TextView note=new TextView(this);note.setText("Uses this phone's rear camera. Pictures stay in memory. No robot connection or wheel commands. Raw camera orientation and measured lens setup still need commissioning.");box.addView(note);
    Button start=new Button(this);start.setText("Start phone camera");start.setOnClickListener(v->{
      if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.CAMERA},7);else startCamera();
    });box.addView(start);Button stop=new Button(this);stop.setText("Stop camera");stop.setOnClickListener(v->stopCamera());box.addView(stop);
    status=new TextView(this);status.setText("Stopped. Grant camera permission only when you choose Start.");box.addView(status);
    image=new ImageView(this);image.setAdjustViewBounds(true);box.addView(image,new LinearLayout.LayoutParams(-1,-2));
    ScrollView scroll=new ScrollView(this);scroll.addView(box);scroll.setOnApplyWindowInsetsListener((v,insets)->{scroll.setPadding(0,insets.getSystemWindowInsetTop(),0,insets.getSystemWindowInsetBottom());return insets;});setContentView(scroll);
  }
  public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){super.onRequestPermissionsResult(request,permissions,grants);
    if(request==7){if(grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED)startCamera();else status.setText("Camera permission denied. Nothing started.");}}
  int frameCount(){return frames;}
  void startCamera(){stopCamera();final int run=++generation;frames=0;lastAt=0;status.setText("Opening local rear camera. Motion disabled.");
    PhoneCamera source=new PhoneCamera(this,new PhoneCamera.Listener(){
      private Detector detector;
      public void frame(byte[] jpeg,long request,long received){
        try{
          Bitmap b=BitmapFactory.decodeByteArray(jpeg,0,jpeg.length);if(b==null)throw new IllegalStateException("JPEG decode failed");
          if(run!=generation)return;
          if(detector==null)detector=new Detector(PhoneCameraActivity.this);
          long begin=SystemClock.elapsedRealtime();List<TargetTracker.Box> boxes=detector.detect(b);long end=SystemClock.elapsedRealtime();
          if(run!=generation)return;
          Bitmap annotated=b.copy(Bitmap.Config.ARGB_8888,true);Canvas canvas=new Canvas(annotated);Paint paint=new Paint();paint.setColor(Color.GREEN);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(3);
          for(TargetTracker.Box q:boxes)canvas.drawRect(q.x1*b.getWidth(),q.y1*b.getHeight(),q.x2*b.getWidth(),q.y2*b.getHeight(),paint);
          int count=++frames;runOnUiThread(()->{if(run==generation){lastAt=end;image.setImageBitmap(annotated);status.setText("Local frame "+count+"; people "+boxes.size()+"; inference "+(end-begin)+" ms; request to result "+(end-request)+" ms. This is a bench measurement, not a tracking or distance test. Motion disabled.");}});
        }catch(Exception e){error(e.getClass().getSimpleName()+": "+e.getMessage());}
      }
      public void error(String reason){runOnUiThread(()->{if(run==generation){stopCamera();status.setText("Camera bench held: "+reason+". Motion disabled.");}});}
      public void stopped(){if(detector!=null){try{detector.close();}catch(Exception ignored){}detector=null;}}
    });camera=source;
    try{source.start();}catch(Exception e){stopCamera();status.setText("Camera not started: "+e.getMessage());}
  }
  private void stopCamera(){generation++;lastAt=0;if(camera!=null){camera.close();camera=null;}status.setText("Camera stopped. Motion disabled.");
    // Each capture session owns its detector and closes it on its own worker.
  }
  public void onResume(){super.onResume();ui.post(ageCheck);}
  public void onPause(){ui.removeCallbacks(ageCheck);stopCamera();super.onPause();}
}
