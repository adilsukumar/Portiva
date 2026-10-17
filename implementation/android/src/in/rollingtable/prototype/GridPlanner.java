package in.rollingtable.prototype;
import java.util.*;

/** Metric grid A*: circle inflation, no diagonal corner cutting, bounded map size. */
public final class GridPlanner {
  public final int width,height;public final double cell;
  private final boolean[] blocked;
  public GridPlanner(boolean[][] raw,double cell,double bodyRadius,double clearance,double positionBound){
    height=raw.length;width=height==0?0:raw[0].length;
    if(width==0||height==0||(long)width*height>2000000||!Double.isFinite(cell+bodyRadius+clearance+positionBound)||cell<=0||bodyRadius<=0||clearance<0||positionBound<0)throw new IllegalArgumentException("Invalid metric map/bounds");
    for(boolean[] row:raw)if(row==null||row.length!=width)throw new IllegalArgumentException("Ragged map");
    this.cell=cell;blocked=new boolean[width*height];int radius=(int)Math.ceil((bodyRadius+clearance+positionBound)/cell);
    if((long)radius*2>=Math.min(width,height)){Arrays.fill(blocked,true);return;}
    for(int y=0;y<height;y++){
      if(raw[y].length!=width)throw new IllegalArgumentException("Ragged map");
      for(int x=0;x<width;x++)if(raw[y][x]){
        for(int dy=-radius;dy<=radius;dy++)for(int dx=-radius;dx<=radius;dx++){
          if((long)dx*dx+(long)dy*dy>(long)radius*radius)continue;int xx=x+dx,yy=y+dy;
          if(xx>=0&&xx<width&&yy>=0&&yy<height)blocked[yy*width+xx]=true;
        }
      }
    }
    // Outside-map space is unknown/occupied, including the inflated edge region.
    for(int y=0;y<height;y++)for(int x=0;x<width;x++)if(x<radius||y<radius||x>=width-radius||y>=height-radius)blocked[y*width+x]=true;
  }
  public boolean free(int x,int y){return x>=0&&x<width&&y>=0&&y<height&&!blocked[y*width+x];}
  private static final class Node {int index;double g,f;Node(int i,double g,double f){index=i;this.g=g;this.f=f;}}
  public List<int[]> path(int sx,int sy,int gx,int gy){
    if(!free(sx,sy)||!free(gx,gy))return Collections.emptyList();
    int start=sy*width+sx,goal=gy*width+gx;double[] cost=new double[blocked.length];Arrays.fill(cost,Double.POSITIVE_INFINITY);
    int[] parent=new int[blocked.length];Arrays.fill(parent,-1);PriorityQueue<Node> q=new PriorityQueue<>((a,b)->Double.compare(a.f,b.f));
    cost[start]=0;q.add(new Node(start,0,Math.hypot(gx-sx,gy-sy)));int expanded=0;
    while(!q.isEmpty()&&expanded++<blocked.length*8){Node n=q.poll();if(n.g>cost[n.index])continue;
      if(n.index==goal){ArrayList<int[]> out=new ArrayList<>();int k=goal;while(k!=-1){out.add(new int[]{k%width,k/width});k=parent[k];}Collections.reverse(out);return out;}
      int x=n.index%width,y=n.index/width;
      for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++){
        if(dx==0&&dy==0)continue;int xx=x+dx,yy=y+dy;if(!free(xx,yy))continue;
        if(dx!=0&&dy!=0&&(!free(x+dx,y)||!free(x,y+dy)))continue;
        int next=yy*width+xx;double value=n.g+Math.hypot(dx,dy);
        if(value<cost[next]){cost[next]=value;parent[next]=n.index;q.add(new Node(next,value,value+Math.hypot(gx-xx,gy-yy)));}
      }
    }return Collections.emptyList();
  }
}
