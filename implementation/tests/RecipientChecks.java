import in.rollingtable.prototype.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.io.*;

/** Real loopback HTTP tests. No household phones or external recipients. */
public final class RecipientChecks {
  private static int checks;
  static void check(boolean x,String why){if(!x)throw new AssertionError(why);checks++;}
  static int call(int port,String path,String token,String body)throws Exception {
    HttpURLConnection c=(HttpURLConnection)new URL("http://127.0.0.1:"+port+path).openConnection(Proxy.NO_PROXY);c.setConnectTimeout(2000);c.setReadTimeout(2000);
    if(token!=null)c.setRequestProperty("X-Portiva-Token",token);
    if(body!=null){c.setRequestMethod("POST");c.setRequestProperty("Content-Type","application/x-www-form-urlencoded");c.setDoOutput(true);try(OutputStream o=c.getOutputStream()){o.write(body.getBytes(StandardCharsets.UTF_8));}}
    try{int code=c.getResponseCode();InputStream stream=code<400?c.getInputStream():c.getErrorStream();if(stream!=null)try(InputStream in=stream){while(in.read()!=-1){}}return code;}finally{c.disconnect();}
  }
  public static void main(String[] args)throws Exception {
    RequestInbox inbox=new RequestInbox();String token="0123456789abcdef0123456789abcdef";
    try(RecipientServer server=new RecipientServer(InetAddress.getLoopbackAddress(),0,token,inbox,()->42L)){
      int port=server.port();check(call(port,"/",null,null)==200,"public landing page");
      check(call(port,"/status",null,null)==401,"private status");check(call(port,"/request",null,"id=a&action=FOLLOW")==401,"auth required");
      check(call(port,"/request","incorrect","id=a&action=FOLLOW")==401,"wrong auth");
      check(call(port,"/request",token,"id=a&action=DELIVER&destination=kitchen")==202,"valid delivery request");
      check(call(port,"/request",token,"id=a&action=DELIVER&destination=kitchen")==409,"replay rejected");
      RequestInbox.Request r=inbox.poll();check(r!=null&&r.action.equals("DELIVER")&&r.destination.equals("kitchen")&&r.receivedMs==42,"typed receipt, local time");
      check(call(port,"/request",token,"id=a&action=FOLLOW")==409,"replay remains rejected after poll");
      check(call(port,"/request",token,"id=b&action=DELIVER&destination=unregistered")==400,"unknown room");
      check(call(port,"/request",token,"id=b&action=FOLLOW&action=STOP")==400,"duplicate form fields");
      check(call(port,"/request",token,"id=b&action=ARM")==400,"no arm action");
      check(call(port,"/request",token,"id=b&action=FOLLOW&extra=x")==400,"unknown field");
      check(call(port,"/missing",token,null)==404,"unknown endpoint");
      for(int i=0;i<32;i++)check(inbox.offer("batch"+i,"FOLLOW","","test",42),"bounded queue fill");
      check(call(port,"/request",token,"id=overflow&action=FOLLOW")==429,"queue bound");
      check(inbox.size()==32,"no overflow");check(call(port,"/request",token,"id=stop-priority&action=STOP")==202,"STOP bypasses full queue");
      check(inbox.size()==1&&inbox.poll().action.equals("STOP"),"STOP supersedes old requests");inbox.clear();check(inbox.size()==0,"shutdown clear");
    }
    System.out.println(checks+" recipient inbox and loopback HTTP checks passed; requests queue only, no real phones or motor actions.");
  }
}
