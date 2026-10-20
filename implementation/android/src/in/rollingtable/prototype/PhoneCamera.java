package in.rollingtable.prototype;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.*;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.*;
import android.util.Size;
import java.nio.ByteBuffer;
import java.util.*;

/** Foreground bench capture only. No storage, radio, navigation or motor commands. */
public final class PhoneCamera implements AutoCloseable {
  public interface Listener {void frame(byte[] jpeg,long requestedAt,long receivedAt);void error(String reason);default void stopped(){}}
  private final Context context;private final Listener listener;
  private HandlerThread thread;private Handler handler;private CameraDevice device;
  private CameraCaptureSession session;private ImageReader reader;
  private volatile boolean closed;private boolean opening,started,reportedClosed;private long requestedAt;
  public PhoneCamera(Context context,Listener listener){this.context=context;this.listener=listener;}
  public synchronized void start(){
    if(started||closed)throw new IllegalStateException("Use a new camera after stop");
    if(context.checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)throw new SecurityException("Camera permission required");
    started=true;thread=new HandlerThread("portiva-phone-camera");thread.start();handler=new Handler(thread.getLooper());handler.post(this::open);
  }
  private void open(){
    if(closed){shutdown();return;}
    try{
      CameraManager manager=context.getSystemService(CameraManager.class);String selected=null;Size chosen=null;
      for(String id:manager.getCameraIdList()){
        CameraCharacteristics c=manager.getCameraCharacteristics(id);
        if(!Objects.equals(c.get(CameraCharacteristics.LENS_FACING),CameraCharacteristics.LENS_FACING_BACK))continue;
        StreamConfigurationMap map=c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);if(map==null)continue;
        Size[] sizes=map.getOutputSizes(ImageFormat.JPEG);if(sizes==null)continue;
        for(Size size:sizes){
          long pixels=(long)size.getWidth()*size.getHeight();if(pixels>1280L*960||pixels<160L*120)continue;
          if(chosen==null||Math.abs(pixels-640L*480)<Math.abs((long)chosen.getWidth()*chosen.getHeight()-640L*480)){chosen=size;selected=id;}
        }
      }
      if(selected==null)throw new IllegalStateException("No supported rear JPEG camera");
      reader=ImageReader.newInstance(chosen.getWidth(),chosen.getHeight(),ImageFormat.JPEG,2);
      reader.setOnImageAvailableListener(r->{
        try{
          byte[] bytes;
          try(Image image=r.acquireLatestImage()){
            if(image==null||closed)return;ByteBuffer buffer=image.getPlanes()[0].getBuffer();
            if(buffer.remaining()>4000000)throw new IllegalStateException("Camera frame too large");
            bytes=new byte[buffer.remaining()];buffer.get(bytes);
          }
          long received=SystemClock.elapsedRealtime();if(!closed)listener.frame(bytes,requestedAt,received);
          // One capture at a time. The listener completes inference before another capture.
          if(!closed)handler.postDelayed(this::capture,100);
        }catch(Exception e){fail(e);}
      },handler);
      opening=true;
      try{manager.openCamera(selected,new CameraDevice.StateCallback(){
        public void onOpened(CameraDevice camera){opening=false;device=camera;if(closed){shutdown();return;}
          try{camera.createCaptureSession(Collections.singletonList(reader.getSurface()),new CameraCaptureSession.StateCallback(){
            public void onConfigured(CameraCaptureSession s){session=s;if(closed){shutdown();return;}capture();}
            public void onConfigureFailed(CameraCaptureSession s){s.close();fail(new IllegalStateException("Camera session configuration failed"));}
          },handler);}catch(Exception e){fail(e);}
        }
        public void onDisconnected(CameraDevice camera){opening=false;camera.close();fail(new IllegalStateException("Camera disconnected"));}
        public void onError(CameraDevice camera,int error){opening=false;camera.close();fail(new IllegalStateException("Camera error "+error));}
      },handler);}catch(Exception e){opening=false;throw e;}
    }catch(Exception e){fail(e);}
  }
  private void capture(){
    if(closed||session==null||device==null)return;
    try{CaptureRequest.Builder request=device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
      request.addTarget(reader.getSurface());request.set(CaptureRequest.CONTROL_MODE,CaptureRequest.CONTROL_MODE_AUTO);
      request.set(CaptureRequest.JPEG_QUALITY,(byte)80);request.set(CaptureRequest.JPEG_ORIENTATION,0);
      requestedAt=SystemClock.elapsedRealtime();session.capture(request.build(),new CameraCaptureSession.CaptureCallback(){
        public void onCaptureFailed(CameraCaptureSession s,CaptureRequest q,CaptureFailure f){fail(new IllegalStateException("Capture failed "+f.getReason()));}
      },handler);
    }catch(Exception e){fail(e);}
  }
  private void fail(Exception e){if(!closed)listener.error(e.getClass().getSimpleName()+": "+e.getMessage());closed=true;shutdown();}
  private void shutdown(){
    if(handler!=null&&!opening)handler.removeCallbacksAndMessages(null);
    if(session!=null){session.close();session=null;}if(device!=null){device.close();device=null;}
    if(reader!=null){reader.close();reader=null;}
    // A pending open callback must run so it can close its CameraDevice.
    if(!opening){if(!reportedClosed){reportedClosed=true;listener.stopped();}if(thread!=null)thread.quitSafely();}
  }
  public synchronized void close(){closed=true;if(handler!=null)handler.post(this::shutdown);}
}
