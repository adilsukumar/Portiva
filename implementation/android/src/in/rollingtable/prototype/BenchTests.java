package in.rollingtable.prototype;
import android.app.Instrumentation;
import android.graphics.*;
import android.os.*;
import java.io.*;
import java.util.*;

/** CLI instrumentation on an isolated emulator; no phone camera, microphone or household data. */
public final class BenchTests extends Instrumentation {
  public void onCreate(Bundle args){super.onCreate(args);start();}
  public void onStart(){Bundle out=new Bundle();
    try(Detector d=new Detector(getTargetContext())){
      byte[] jpg;try(InputStream in=getTargetContext().getAssets().open("bus.jpg")){jpg=VisionService.boundedRead(in,2000000);}
      Bitmap image=BitmapFactory.decodeByteArray(jpg,0,jpg.length);if(image==null)throw new AssertionError("sample decode");
      long start=SystemClock.elapsedRealtime();List<TargetTracker.Box> found=d.detect(image);long duration=SystemClock.elapsedRealtime()-start;
      long[] warm=new long[5];for(int i=0;i<5;i++){start=SystemClock.elapsedRealtime();d.detect(image);warm[i]=SystemClock.elapsedRealtime()-start;}Arrays.sort(warm);
      if(found.size()<2)throw new AssertionError("public photograph persons");
      Bitmap blank=Bitmap.createBitmap(640,480,Bitmap.Config.ARGB_8888);
      if(!d.detect(blank).isEmpty())throw new AssertionError("blank fixture false positive");
      TargetTracker t=new TargetTracker();TargetTracker.Box chosen=found.get(0);t.enroll(chosen,1000);
      if(t.update(Collections.singletonList(chosen),1100).box==null)throw new AssertionError("same appearance association");
      if(t.update(Arrays.asList(chosen,chosen),1200).box!=null)throw new AssertionError("ambiguity hold");
      if(t.update(Collections.singletonList(chosen),1300).box!=null)throw new AssertionError("hold latch");
      out.putString("scope","Android native detector, procedural localization and reference controller checks; not Redmi, Grandma, house or motors");
      out.putInt("person_count",found.size());out.putLong("first_inference_ms",duration);out.putLong("warm_median_ms",warm[2]);out.putBoolean("motion_enabled",false);
      double[] k={400,0,320,0,400,240,0,0,1};
      try(PlanarLocalizer p=new PlanarLocalizer(k,new double[5])){
        Bitmap ref,query;try(InputStream in=getTargetContext().getAssets().open("planar-reference.png")){ref=BitmapFactory.decodeStream(in);}
        try(InputStream in=getTargetContext().getAssets().open("planar-query.png")){query=BitmapFactory.decodeStream(in);}
        org.json.JSONObject fixture;try(InputStream in=getTargetContext().getAssets().open("planar-fixture.json")){fixture=new org.json.JSONObject(new String(VisionService.boundedRead(in,10000),java.nio.charset.StandardCharsets.UTF_8));}
        org.json.JSONArray corners=fixture.getJSONArray("reference_corners");org.opencv.core.Point[] uv=new org.opencv.core.Point[4];
        for(int i=0;i<4;i++){org.json.JSONArray a=corners.getJSONArray(i);uv[i]=new org.opencv.core.Point(a.getDouble(0),a.getDouble(1));}
        org.opencv.core.Point3[] xyz={new org.opencv.core.Point3(-1.5,-1,3),new org.opencv.core.Point3(1.5,-1,3),new org.opencv.core.Point3(1.5,1,3),new org.opencv.core.Point3(-1.5,1,3)};
        p.teach("procedural patch",ref,uv,xyz);PlanarLocalizer.Pose pose=p.locate(query);
        if(pose==null)throw new AssertionError("procedural localization returned hold");
        double error=Math.sqrt(Math.pow(pose.centre[0]-.1,2)+Math.pow(pose.centre[1]-.02,2)+Math.pow(pose.centre[2]-.05,2));
        if(error>.04)throw new AssertionError("procedural pose error "+error);out.putDouble("procedural_pose_error_m",error);
        if(p.locate(blank)!=null)throw new AssertionError("blank pose should hold");
      }
      FollowController f=new FollowController();if(f.arm(false))throw new AssertionError("uncommissioned follow arm");
      f.arm(true);if(f.tick(100,new FollowController.Observation(1.3,0,.02,.01,0,2,true,100)).linear<=0)throw new AssertionError("virtual follow command");
      if(!f.tick(150,new FollowController.Observation(1.3,0,.02,.01,0,2,false,150)).hold)throw new AssertionError("identity fault hold");
      if(!f.tick(200,new FollowController.Observation(1.3,0,.02,.01,0,2,true,200)).hold)throw new AssertionError("follow hold latch");
      out.putBoolean("follow_fault_hold",true);
      boolean[][] raw=new boolean[80][100];GridPlanner planner=new GridPlanner(raw,.02,.215,.03,.02);
      if(planner.path(20,40,80,40).isEmpty())throw new AssertionError("Android grid route");out.putBoolean("grid_route",true);
      MissionDemo demo=new MissionDemo();demo.load();if(!demo.send("kitchen"))throw new AssertionError("demo dispatch");
      int steps=0;while(steps++<4000&&(demo.state()!=MissionController.State.ARRIVED||demo.speed>1e-6||Math.abs(demo.angular)>1e-6))demo.step();
      if(steps>=4000||!demo.returnAfterUnloading())throw new AssertionError("demo outbound/return request");
      steps=0;while(steps++<5000&&(demo.state()!=MissionController.State.ARRIVED||demo.speed>1e-6||Math.abs(demo.angular)>1e-6))demo.step();
      if(steps>=5000||Math.hypot(demo.x-.6,demo.y-1.005)>.05||demo.physicalLeftPwm()!=0||demo.physicalRightPwm()!=0)throw new AssertionError("demo round trip");
      out.putBoolean("mission_demo_round_trip",true);
      java.util.concurrent.CountDownLatch cameraFrame=new java.util.concurrent.CountDownLatch(1);
      java.util.concurrent.CountDownLatch cameraClosed=new java.util.concurrent.CountDownLatch(1);
      java.util.concurrent.atomic.AtomicReference<byte[]> localJpeg=new java.util.concurrent.atomic.AtomicReference<>();
      java.util.concurrent.atomic.AtomicReference<String> cameraError=new java.util.concurrent.atomic.AtomicReference<>();
      PhoneCamera local=new PhoneCamera(getTargetContext(),new PhoneCamera.Listener(){
        public void frame(byte[] jpeg,long request,long received){localJpeg.set(jpeg);cameraFrame.countDown();}
        public void error(String reason){cameraError.set(reason);cameraFrame.countDown();}
        public void stopped(){cameraClosed.countDown();}
      });
      try{local.start();if(!cameraFrame.await(20,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("Synthetic Camera2 frame timeout");}
      finally{local.close();}
      if(!cameraClosed.await(5,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("Camera close timeout");
      if(cameraError.get()!=null)throw new AssertionError(cameraError.get());
      byte[] captured=localJpeg.get();if(captured==null)throw new AssertionError("Missing synthetic camera JPEG");
      Bitmap localImage=BitmapFactory.decodeByteArray(captured,0,captured.length);
      if(localImage==null||localImage.getWidth()<160||localImage.getHeight()<120)throw new AssertionError("Local camera decode");
      d.detect(localImage);out.putBoolean("phone_camera_synthetic_capture",true);out.putBoolean("phone_camera_detector_on_captured_frame",true);
      out.putInt("phone_camera_width",localImage.getWidth());out.putInt("phone_camera_height",localImage.getHeight());localImage.recycle();
      android.content.Intent cameraIntent=new android.content.Intent(getTargetContext(),PhoneCameraActivity.class);cameraIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
      PhoneCameraActivity cameraActivity=(PhoneCameraActivity)startActivitySync(cameraIntent);waitForIdleSync();
      runOnMainSync(()->cameraActivity.startCamera());long cameraDeadline=SystemClock.elapsedRealtime()+15000;
      while(cameraActivity.frameCount()<1&&SystemClock.elapsedRealtime()<cameraDeadline)SystemClock.sleep(100);
      if(cameraActivity.frameCount()<1)throw new AssertionError("Camera activity did not process a synthetic frame");
      runOnMainSync(()->cameraActivity.startCamera());cameraDeadline=SystemClock.elapsedRealtime()+15000;
      while(cameraActivity.frameCount()<1&&SystemClock.elapsedRealtime()<cameraDeadline)SystemClock.sleep(100);
      if(cameraActivity.frameCount()<1)throw new AssertionError("Camera activity restart failed");
      runOnMainSync(()->cameraActivity.finish());out.putBoolean("phone_camera_activity_capture_restart",true);
      out.putBoolean("phone_camera_activity_opened",true);
      android.content.Intent demoIntent=new android.content.Intent(getTargetContext(),DemoActivity.class);demoIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
      android.app.Activity demoActivity=startActivitySync(demoIntent);waitForIdleSync();SystemClock.sleep(250);
      Bitmap screenshot=getUiAutomation().takeScreenshot();
      if(screenshot!=null){try(FileOutputStream stream=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),"Portiva-demo-screen.png"))){screenshot.compress(Bitmap.CompressFormat.PNG,100,stream);}screenshot.recycle();out.putBoolean("mission_ui_screenshot",true);}
      runOnMainSync(()->demoActivity.finish());out.putBoolean("mission_ui_opened",true);
      out.putString("result","passed");finish(-1,out);
    }catch(Throwable e){out.putString("result","failed");out.putString("error",e.toString());finish(0,out);}
  }
}
