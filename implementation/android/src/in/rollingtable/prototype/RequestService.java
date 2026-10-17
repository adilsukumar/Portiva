package in.rollingtable.prototype;
import android.app.*;
import android.content.Intent;
import android.os.*;
import java.net.*;
import java.security.SecureRandom;
import java.util.*;

/** Foreground local recipient request inbox. No camera or motor dependency. */
public final class RequestService extends Service {
  public static volatile String status="Recipient request bench stopped";
  public static volatile String lastReviewed="No request reviewed",selectedDestination="";
  public static final RequestInbox inbox=new RequestInbox();private RecipientServer server;
  public void onCreate(){super.onCreate();getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel("requests","Portiva requests",NotificationManager.IMPORTANCE_LOW));}
  public int onStartCommand(Intent intent,int flags,int id){if(server!=null)return START_NOT_STICKY;
    startForeground(8,new Notification.Builder(this,"requests").setSmallIcon(android.R.drawable.ic_menu_send).setContentTitle("Portiva request bench").setContentText("Local requests only. Motors disabled.").build());
    byte[] raw=new byte[16];new SecureRandom().nextBytes(raw);StringBuilder secret=new StringBuilder();for(byte b:raw)secret.append(String.format(Locale.ROOT,"%02x",b&255));
    inbox.clear();try{server=new RecipientServer(InetAddress.getByName("0.0.0.0"),8080,secret.toString(),inbox,SystemClock::elapsedRealtime);
      ArrayList<String> urls=new ArrayList<>();Enumeration<NetworkInterface> interfaces=NetworkInterface.getNetworkInterfaces();while(interfaces.hasMoreElements()){Enumeration<InetAddress> addresses=interfaces.nextElement().getInetAddresses();while(addresses.hasMoreElements()){InetAddress a=addresses.nextElement();if(a instanceof Inet4Address&&a.isSiteLocalAddress()&&!a.isLoopbackAddress())urls.add("http://"+a.getHostAddress()+":8080");}}
      status="Recipient bench URLs: "+urls+"\nPairing token: "+secret+"\nRequests queue only. No motor control.";
    }catch(Exception e){status="Recipient bench could not start: "+e.getClass().getSimpleName();stopSelf();}return START_NOT_STICKY;
  }
  public void onDestroy(){if(server!=null)try{server.close();}catch(Exception ignored){}server=null;inbox.clear();lastReviewed="No request reviewed";status="Recipient request bench stopped";super.onDestroy();}
  public IBinder onBind(Intent intent){return null;}
}
