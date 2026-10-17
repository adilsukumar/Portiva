package in.rollingtable.prototype;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.LongSupplier;

/** Phone-hosted LAN request bench. Authenticated requests queue; no motor adapter. */
public final class RecipientServer implements AutoCloseable {
  private final ServerSocket server;private final String token;private final RequestInbox inbox;private final LongSupplier clock;
  private final Set<Socket> connections=ConcurrentHashMap.newKeySet();
  private final Thread accept;private volatile boolean running=true;
  private final ThreadPoolExecutor workers=new ThreadPoolExecutor(2,2,1,TimeUnit.SECONDS,new ArrayBlockingQueue<>(4),r->{Thread t=new Thread(r,"Portiva-request");t.setDaemon(true);return t;});
  public RecipientServer(InetAddress bind,int port,String token,RequestInbox inbox,LongSupplier clock)throws IOException {
    if(token==null||!token.matches("[A-Za-z0-9_-]{32,128}")||inbox==null||clock==null)throw new IllegalArgumentException("Private token and inbox required");
    this.token=token;this.inbox=inbox;this.clock=clock;server=new ServerSocket();server.bind(new InetSocketAddress(bind,port));
    accept=new Thread(()->{while(running)try{Socket s=server.accept();if(!running||!privatePeer(s.getInetAddress())){s.close();continue;}connections.add(s);try{workers.execute(()->serve(s));}catch(RejectedExecutionException full){connections.remove(s);s.close();}}catch(IOException e){if(running)running=false;}},"Portiva-accept");accept.setDaemon(true);accept.start();
  }
  public int port(){return server.getLocalPort();}
  private static boolean privatePeer(InetAddress a){if(a.isLoopbackAddress())return true;byte[] b=a.getAddress();if(b.length!=4)return false;int x=b[0]&255,y=b[1]&255;return x==10||x==192&&y==168||x==172&&y>=16&&y<=31;}
  private static String line(InputStream in,int limit)throws IOException {
    ByteArrayOutputStream b=new ByteArrayOutputStream();int prev=-1;
    while(b.size()<limit){int c=in.read();if(c<0)throw new IOException("Truncated request");if(prev==13&&c==10){byte[] raw=b.toByteArray();return new String(raw,0,raw.length-1,StandardCharsets.US_ASCII);}if(c>127||c==0)throw new IOException("ASCII headers required");b.write(c);prev=c;}
    throw new IOException("Header too long");
  }
  private static void response(OutputStream out,int status,String kind,String body)throws IOException {
    byte[] data=body.getBytes(StandardCharsets.UTF_8);String header="HTTP/1.1 "+status+" Response\r\nContent-Type: "+kind+"\r\nContent-Length: "+data.length+"\r\nCache-Control: no-store\r\nConnection: close\r\nX-Content-Type-Options: nosniff\r\n\r\n";
    out.write(header.getBytes(StandardCharsets.US_ASCII));out.write(data);out.flush();
  }
  private void serve(Socket socket){try(Socket s=socket){s.setSoTimeout(1500);InputStream in=s.getInputStream();OutputStream out=s.getOutputStream();
    try{
      String[] first=line(in,512).split(" ");if(first.length!=3||!first[2].equals("HTTP/1.1")){response(out,400,"text/plain","Bad request");return;}
      Map<String,String> headers=new HashMap<>();int total=0;
      for(int i=0;i<32;i++){String h=line(in,1024);total+=h.length();if(total>4096)throw new IOException("Headers too large");if(h.isEmpty())break;int colon=h.indexOf(':');if(colon<1)throw new IOException("Bad header");String key=h.substring(0,colon).toLowerCase(Locale.ROOT);if(headers.put(key,h.substring(colon+1).trim())!=null)throw new IOException("Duplicate header");if(i==31)throw new IOException("Too many headers");}
      if(headers.containsKey("transfer-encoding")){response(out,400,"text/plain","Chunked requests unsupported");return;}
      if(first[0].equals("GET")&&first[1].equals("/")){response(out,200,"text/html; charset=utf-8",page());return;}
      byte[] supplied=headers.getOrDefault("x-portiva-token","").getBytes(StandardCharsets.UTF_8);
      if(!MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),supplied)){response(out,401,"text/plain","Pairing required");return;}
      if(first[0].equals("GET")&&first[1].equals("/status")){response(out,200,"application/json","{\"queued\":"+inbox.size()+",\"motion_enabled\":false}");return;}
      if(!first[0].equals("POST")||!first[1].equals("/request")){response(out,404,"text/plain","Unknown endpoint");return;}
      String length=headers.getOrDefault("content-length","");if(!length.matches("[0-9]{1,3}")){response(out,400,"text/plain","Content length required");return;}int n=Integer.parseInt(length);
      if(n<1||n>256||!headers.getOrDefault("content-type","").startsWith("application/x-www-form-urlencoded")){response(out,400,"text/plain","Invalid request body");return;}
      byte[] body=new byte[n];int offset=0;while(offset<n){int read=in.read(body,offset,n-offset);if(read<0)throw new IOException("Truncated body");offset+=read;}
      Map<String,String> form=new HashMap<>();for(String pair:new String(body,StandardCharsets.UTF_8).split("&")){String[] kv=pair.split("=",2);if(kv.length!=2)throw new IllegalArgumentException();String key=URLDecoder.decode(kv[0],"UTF-8");if(!Arrays.asList("id","action","destination").contains(key)||form.put(key,URLDecoder.decode(kv[1],"UTF-8"))!=null)throw new IllegalArgumentException();}
      boolean queued=inbox.offer(form.get("id"),form.get("action"),form.getOrDefault("destination",""),"recipient_browser",clock.getAsLong());
      response(out,queued?202:409,"application/json",queued?"{\"queued\":true,\"motion_enabled\":false}":"{\"duplicate\":true,\"motion_enabled\":false}");
    }catch(IllegalArgumentException e){response(out,400,"text/plain","Invalid fields");}catch(IllegalStateException e){response(out,429,"text/plain","Inbox full");}
  }catch(IOException ignored){/* malformed/timeout closes only this bounded connection */}finally{connections.remove(socket);}}
  private static String page(){return "<!doctype html><meta name=viewport content='width=device-width,initial-scale=1'><title>Portiva requests</title><style>body{font:18px system-ui;max-width:650px;margin:30px auto;padding:20px}button,input,select{font:inherit;padding:12px;margin:7px 0;max-width:100%}</style><h1>Portiva request bench</h1><p>This queues requests. It cannot move the trolley. The recipient phone does not supply its location.</p><label>Pairing token <input id=t type=password autocomplete=off></label><br><select id=a><option>DELIVER</option><option>FOLLOW</option><option>LOAD</option><option>RETURN</option><option>STOP</option></select><select id=d><option value=kitchen>Kitchen</option><option value=pooja>Pooja</option></select><br><button id=b>Queue request</button><p id=o></p><script>document.getElementById('b').onclick=async()=>{let id=Array.from(crypto.getRandomValues(new Uint8Array(16)),v=>v.toString(16).padStart(2,'0')).join('');let action=document.getElementById('a').value;try{let r=await fetch('/request',{method:'POST',headers:{'X-Portiva-Token':document.getElementById('t').value},body:new URLSearchParams({id,action,destination:action==='DELIVER'?document.getElementById('d').value:''})});document.getElementById('o').textContent=r.status===202?'Queued for bench review. No motor movement.':'Request rejected ('+r.status+').';}catch(e){document.getElementById('o').textContent='Connection failed.';}}</script>";}
  public void close()throws IOException {running=false;server.close();for(Socket s:connections)try{s.close();}catch(IOException ignored){}connections.clear();workers.shutdownNow();try{accept.join(1000);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
}
