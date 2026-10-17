package in.rollingtable.prototype;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import java.io.*;
import java.net.*;
import java.util.*;

/** Bench preview: ZERO motor commands only. No unqualified distance/pose drives hardware. */
public final class VisionService extends Service {
  public static final class Snapshot {
    public final Bitmap image;public final List<TargetTracker.Box> boxes;public final long timestamp;
    Snapshot(Bitmap b,List<TargetTracker.Box> q,long t){image=b;boxes=Collections.unmodifiableList(new ArrayList<>(q));timestamp=t;}
  }
  public static volatile Snapshot snapshot;
  public static volatile String status="Stopped"; public static final TargetTracker tracker=new TargetTracker();
  private volatile boolean running; private Thread worker; private PowerManager.WakeLock wake;
  private String base,token;
  private long telemetryAt;private String telemetryStatus="Base telemetry not read";
  private final BaseTelemetry.ButtonEvents buttonEvents=new BaseTelemetry.ButtonEvents();
  public void onCreate(){super.onCreate();NotificationManager nm=getSystemService(NotificationManager.class);
    nm.createNotificationChannel(new NotificationChannel("bench","Portiva bench preview",NotificationManager.IMPORTANCE_LOW));}
  public int onStartCommand(Intent intent,int flags,int id) {
    if(intent==null)return START_NOT_STICKY;
    if(running)return START_NOT_STICKY;
    base=intent.getStringExtra("url");token=intent.getStringExtra("token");
    try {URI u=URI.create(base);String host=u.getHost();
      if(!"http".equals(u.getScheme())||!privateAddress(host)||u.getUserInfo()!=null||!"".equals(u.getPath()))throw new IllegalArgumentException();
      if(token==null||token.length()<32)throw new IllegalArgumentException();
    }catch(Exception e){status="Use private numeric HTTP robot IP and pairing token (32+ chars)";stopSelf();return START_NOT_STICKY;}
    Notification n=new Notification.Builder(this,"bench").setSmallIcon(android.R.drawable.ic_menu_camera)
      .setContentTitle("Portiva: bench preview").setContentText("Local vision active. Motion disabled.").build();
    startForeground(7,n);
    wake=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"Portiva:vision");wake.acquire(60*60*1000L);
    running=true;worker=new Thread(this::loop,"rollingtable-vision");worker.start();return START_NOT_STICKY;
  }
  static boolean privateAddress(String h){
    if(h==null||!h.matches("[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+"))return false;
    String[] p=h.split("\\.");int[] v=new int[4];for(int i=0;i<4;i++){v[i]=Integer.parseInt(p[i]);if(v[i]>255)return false;}
    return v[0]==10||(v[0]==192&&v[1]==168)||(v[0]==172&&v[1]>=16&&v[1]<=31);
  }
  private void loop(){
    try(Detector detector=new Detector(this)) {
      while(running&&!Thread.currentThread().isInterrupted()) {
        try {
          long begin=SystemClock.elapsedRealtime();HttpURLConnection c=open("/capture");
          byte[] jpeg;try(InputStream in=c.getInputStream()){jpeg=boundedRead(in,700000);}finally{c.disconnect();}
          Bitmap image=BitmapFactory.decodeByteArray(jpeg,0,jpeg.length);if(image==null)throw new IOException("Invalid JPEG");
          List<TargetTracker.Box> found=detector.detect(image);long now=SystemClock.elapsedRealtime();
          TargetTracker.Result selected=tracker.update(found,now);
          // Preview cannot send steering: actual range, identity and loaded-braking gates are absent.
          zero();snapshot=new Snapshot(image,found,now);
          if(now-telemetryAt>=1000){telemetryAt=now;readTelemetry();}
          status="Preview / "+selected.reason+" / "+(now-begin)+" ms / MOTION DISABLED\n"+telemetryStatus;
          if(now-begin>350){tracker.clear();status="Slow frame: reselect target / MOTION DISABLED";}
        }catch(Exception e){tracker.clear();status="Connection/detection hold: "+e.getClass().getSimpleName();try{zero();}catch(Exception ignored){}Thread.sleep(250);}
      }
    }catch(Exception e){status="Model initialization failed: "+e.getClass().getSimpleName();}
    finally{running=false;tracker.clear();try{zero();}catch(Exception ignored){}stopSelf();}
  }
  HttpURLConnection open(String path)throws IOException {
    HttpURLConnection c=(HttpURLConnection)new URL(base+path).openConnection();c.setConnectTimeout(800);c.setReadTimeout(800);
    c.setInstanceFollowRedirects(false);c.setRequestProperty("X-Robot-Token",token);return c;
  }
  void zero()throws IOException {HttpURLConnection c=open("/stop");c.setRequestMethod("POST");try{c.getResponseCode();}finally{c.disconnect();}}
  void readTelemetry(){HttpURLConnection c=null;try{
    c=open("/telemetry");org.json.JSONObject object;try(InputStream in=c.getInputStream()){object=new org.json.JSONObject(new String(boundedRead(in,1024),java.nio.charset.StandardCharsets.UTF_8));}
    long age=object.getLong("age_ms");if(age<0||age>600||object.getBoolean("motion_enabled"))throw new IOException("Invalid bench telemetry");
    BaseTelemetry packet=BaseTelemetry.decode(object.getString("packet"));int event=buttonEvents.accept(packet);
    telemetryStatus=packet.summary()+"; gateway age "+age+"ms (read-only)";
    if(event<0)telemetryStatus+="; ambiguous/reset button event held";
    if(event==1)RequestService.inbox.offer("base-"+packet.fields[0]+"-follow-"+packet.fields[10],"FOLLOW","","robot_button",SystemClock.elapsedRealtime());
    if(event==2){String room=RequestService.selectedDestination;if(room.isEmpty())telemetryStatus+="; SEND held: choose destination first";else RequestService.inbox.offer("base-"+packet.fields[0]+"-send-"+packet.fields[11],"DELIVER",room,"robot_button",SystemClock.elapsedRealtime());}
  }catch(Exception e){buttonEvents.accept(null);telemetryStatus="Base telemetry absent/invalid; no sensor qualification";}finally{if(c!=null)c.disconnect();}}
  static byte[] boundedRead(InputStream in,int limit)throws IOException {ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;
    while((n=in.read(b))!=-1){if(out.size()+n>limit)throw new IOException("Oversized frame");out.write(b,0,n);}return out.toByteArray();}
  public void onDestroy(){running=false;if(worker!=null)worker.interrupt();tracker.clear();if(wake!=null&&wake.isHeld())wake.release();super.onDestroy();}
  public IBinder onBind(Intent intent){return null;}
}
