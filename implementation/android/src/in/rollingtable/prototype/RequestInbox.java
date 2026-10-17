package in.rollingtable.prototype;
import java.util.*;

/** Bounded bench requests. Receipt is not acceptance for a live mission. */
public final class RequestInbox {
  public static final class Request {
    public final String id,action,destination,source;public final long receivedMs;
    Request(String i,String a,String d,String s,long t){id=i;action=a;destination=d;source=s;receivedMs=t;}
  }
  private final ArrayDeque<Request> queue=new ArrayDeque<>();private final LinkedHashSet<String> recent=new LinkedHashSet<>();
  public synchronized boolean offer(String id,String action,String destination,String source,long now){
    if(id==null||!id.matches("[A-Za-z0-9_-]{1,64}")||!Arrays.asList("FOLLOW","LOAD","DELIVER","RETURN","STOP").contains(action)||source==null||now<0)throw new IllegalArgumentException("Invalid request");
    if(action.equals("DELIVER")&&!Arrays.asList("kitchen","pooja").contains(destination))throw new IllegalArgumentException("Register the destination first");
    if(!action.equals("DELIVER")&&destination!=null&&!destination.isEmpty())throw new IllegalArgumentException("Unexpected destination");
    if(recent.contains(id))return false;if(action.equals("STOP"))queue.clear();else if(queue.size()>=32)throw new IllegalStateException("Inbox full");
    queue.addLast(new Request(id,action,destination==null?"":destination,source,now));recent.add(id);
    if(recent.size()>256)recent.remove(recent.iterator().next());return true;
  }
  public synchronized int size(){return queue.size();}
  public synchronized Request poll(){return queue.pollFirst();}
  public synchronized void clear(){queue.clear();recent.clear();}
}
