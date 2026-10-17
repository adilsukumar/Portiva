package in.rollingtable.prototype;
import java.util.*;

/** Heading-aware A* with a continuous swept rectangle. No device or motor access. */
public final class RectanglePlanner {
  public static final int[][] MOVES={{1,0},{1,1},{0,1},{-1,1},{-1,0},{-1,-1},{0,-1},{1,-1}};
  public final double cell,length,width,padding,radius;private final boolean[][] map;private final int nx,ny;private final int[][] sums;
  public String reason="not planned";
  public RectanglePlanner(boolean[][] occupancy,double cell,double length,double width,double padding){
    if(occupancy==null||occupancy.length==0||occupancy[0]==null||occupancy[0].length==0||(long)occupancy.length*occupancy[0].length>1000000)throw new IllegalArgumentException("bounded occupancy grid required");
    if(!Double.isFinite(cell+length+width+padding)||cell<=0||length<=0||width<=0||padding<0)throw new IllegalArgumentException("metric geometry required");
    this.cell=cell;this.length=length;this.width=width;this.padding=padding;ny=occupancy.length;nx=occupancy[0].length;map=new boolean[ny][];
    for(int y=0;y<ny;y++){if(occupancy[y]==null||occupancy[y].length!=nx)throw new IllegalArgumentException("ragged grid");map[y]=occupancy[y].clone();}
    sums=new int[ny+1][nx+1];for(int y=0;y<ny;y++)for(int x=0;x<nx;x++)sums[y+1][x+1]=sums[y][x+1]+sums[y+1][x]-sums[y][x]+(map[y][x]?1:0);
    radius=Math.hypot(length/2+padding,width/2+padding);
  }
  public static double wrap(double a){return Math.atan2(Math.sin(a),Math.cos(a));}
  public double[] pose(int[] node){return new double[]{(node[0]+.5)*cell,(node[1]+.5)*cell,node[2]*Math.PI/4};}
  public int[] node(double x,double y,double yaw){return new int[]{(int)Math.floor(x/cell),(int)Math.floor(y/cell),Math.floorMod((int)Math.round(yaw/(Math.PI/4)),8)};}
  private boolean valid(int[] n){return n!=null&&n.length==3&&n[0]>=0&&n[0]<nx&&n[1]>=0&&n[1]<ny&&n[2]>=0&&n[2]<8;}
  public boolean clear(double x,double y,double yaw,double extra){
    if(!Double.isFinite(x+y+yaw+extra)||extra<0)return false;
    double hl=length/2+padding+extra,hw=width/2+padding+extra,c=Math.cos(yaw),s=Math.sin(yaw),ex=hl*Math.abs(c)+hw*Math.abs(s),ey=hl*Math.abs(s)+hw*Math.abs(c);
    if(x-ex<=0||y-ey<=0||x+ex>=nx*cell||y+ey>=ny*cell)return false;
    int x0=Math.max(0,(int)Math.floor((x-ex)/cell)),x1=Math.min(nx-1,(int)Math.floor((x+ex)/cell)),y0=Math.max(0,(int)Math.floor((y-ey)/cell)),y1=Math.min(ny-1,(int)Math.floor((y+ey)/cell));
    if(sums[y1+1][x1+1]-sums[y0][x1+1]-sums[y1+1][x0]+sums[y0][x0]==0)return true;
    for(int yy=y0;yy<=y1;yy++)for(int xx=x0;xx<=x1;xx++)if(map[yy][xx]){
      double dx=(xx+.5)*cell-x,dy=(yy+.5)*cell-y,h=cell/2;
      if(Math.abs(dx)<=ex+h&&Math.abs(dy)<=ey+h&&Math.abs(dx*c+dy*s)<=hl+h*(Math.abs(c)+Math.abs(s))&&Math.abs(-dx*s+dy*c)<=hw+h*(Math.abs(s)+Math.abs(c)))return false;
    }
    return true;
  }
  public boolean sweep(double[] a,double[] b){
    if(a==null||b==null||a.length!=3||b.length!=3)return false;
    double da=wrap(b[2]-a[2]),bound=Math.hypot(b[0]-a[0],b[1]-a[1])+radius*Math.abs(da);
    if(!Double.isFinite(bound)||bound>20)return false;
    int n=Math.max(1,(int)Math.ceil(bound/.01));double extra=bound/(2*n)+1e-9;
    for(int i=0;i<=n;i++){double t=(double)i/n;if(!clear(a[0]+t*(b[0]-a[0]),a[1]+t*(b[1]-a[1]),a[2]+t*da,extra))return false;}return true;
  }
  private int key(int[] n){return (n[1]*nx+n[0])*8+n[2];}
  private int[] from(int n){return new int[]{n/8%nx,n/8/nx,n%8};}
  private static final class Entry {final int key;final double cost,score;Entry(int k,double c,double f){key=k;cost=c;score=f;}}
  public List<int[]> path(int[] start,int[] goal,int maxExpansions,long maxMillis){
    if(maxExpansions<1||maxExpansions>100000||maxMillis<1||maxMillis>30000)throw new IllegalArgumentException("bounded search required");
    if(!valid(start)||!valid(goal)){reason="invalid start or goal";return Collections.emptyList();}
    double[] sp=pose(start),gp=pose(goal);if(!clear(sp[0],sp[1],sp[2],0)||!clear(gp[0],gp[1],gp[2],0)){reason="blocked start or goal";return Collections.emptyList();}
    int sk=key(start),gk=key(goal),expanded=0;Map<Integer,Double> costs=new HashMap<>();Map<Integer,Integer> previous=new HashMap<>();
    PriorityQueue<Entry> q=new PriorityQueue<>((a,b)->{int c=Double.compare(a.score,b.score);return c==0?Integer.compare(a.key,b.key):c;});costs.put(sk,0.);q.add(new Entry(sk,0,0));long begin=System.nanoTime();
    while(!q.isEmpty()){
      if(Thread.currentThread().isInterrupted()||(System.nanoTime()-begin)/1000000>maxMillis){reason="search deadline hold";return Collections.emptyList();}
      Entry e=q.remove();if(e.cost!=costs.getOrDefault(e.key,Double.POSITIVE_INFINITY))continue;
      if(e.key==gk){LinkedList<int[]> result=new LinkedList<>();int k=gk;result.addFirst(from(k));while(k!=sk){k=previous.get(k);result.addFirst(from(k));}reason="rectangle reference route found";return result;}
      if(++expanded>maxExpansions){reason="search limit hold";return Collections.emptyList();}
      int[] a=from(e.key);int dx=MOVES[a[2]][0],dy=MOVES[a[2]][1];int[][] options={{a[0]+dx,a[1]+dy,a[2]},{a[0],a[1],(a[2]+1)%8},{a[0],a[1],(a[2]+7)%8}};
      for(int[] b:options){if(!valid(b))continue;int k=key(b);double cost=e.cost+(a[2]==b[2]?cell*Math.hypot(dx,dy):radius*Math.PI/4);
        if(cost>=costs.getOrDefault(k,Double.POSITIVE_INFINITY)||!sweep(pose(a),pose(b)))continue;
        costs.put(k,cost);previous.put(k,e.key);double dxg=goal[0]-b[0],dyg=goal[1]-b[1],h=cell*Math.hypot(dxg,dyg);
        // A forward-only route must accumulate enough heading variation to reach
        // its displacement direction and final heading. Keep translation cost separate.
        double bearing=Math.atan2(dyg,dxg),turn=dxg==0&&dyg==0?Math.abs(wrap((goal[2]-b[2])*Math.PI/4)):
          Math.abs(wrap(bearing-b[2]*Math.PI/4))+Math.abs(wrap(goal[2]*Math.PI/4-bearing));
        h+=radius*turn;q.add(new Entry(k,cost,cost+h));}
    }
    reason="no admissible route";return Collections.emptyList();
  }
}
