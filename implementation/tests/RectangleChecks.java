import in.rollingtable.prototype.*;
import java.util.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

/** Deterministic geometry fixtures and bounded search checks for the Android planner. */
public final class RectangleChecks {
  private static int checks;private static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
  public static void main(String[] args)throws Exception{
    double cell=.0125;boolean[][] map=new boolean[192][256];double bottom=1.2-.5588/2,top=1.2+.5588/2;
    for(int y=0;y<192;y++)if(!(y*cell>=bottom&&(y+1)*cell<=top))for(int x=126;x<130;x++)map[y][x]=true;
    RectanglePlanner p=new RectanglePlanner(map,cell,.390,.410,.055);
    check(p.clear(1.6,1.2,0,0),"straight through doorway");check(!p.clear(1.6,1.2,Math.PI/4,0),"rotated body rejected");check(!p.clear(Double.NaN,1,0,0),"NaN rejected");check(!p.clear(.01,1,0,0),"outside body rejected");
    List<int[]> route=p.path(new int[]{48,95,2},new int[]{207,95,0},30000,5000);check(!route.isEmpty(),p.reason);
    for(int i=1;i<route.size();i++)check(p.sweep(p.pose(route.get(i-1)),p.pose(route.get(i))),"route edge");
    check(p.path(new int[]{48,95,2},new int[]{207,95,0},1,5000).isEmpty(),"bounded search hold");
    check(p.path(new int[]{-1,95,2},new int[]{207,95,0},30000,5000).isEmpty(),"invalid start");
    Random random=new Random(61016);StringBuilder csv=new StringBuilder("x_m,y_m,yaw_rad,java_clear\n");
    for(int i=0;i<1000;i++){double x=random.nextDouble()*3.2,y=random.nextDouble()*2.4,a=random.nextDouble()*Math.PI*2;csv.append(x+","+y+","+a+","+p.clear(x,y,a,0)+"\n");}
    Path out=Paths.get("implementation/Portiva-rectangle-java-fixtures.csv");Files.write(out,csv.toString().getBytes(StandardCharsets.UTF_8));
    StringBuilder path=new StringBuilder("x_m,y_m,yaw_rad\n");for(int[] n:route){double[] a=p.pose(n);path.append(a[0]+","+a[1]+","+a[2]+"\n");}Files.write(Paths.get("implementation/Portiva-rectangle-java-route.csv"),path.toString().getBytes(StandardCharsets.UTF_8));
    System.out.println(checks+" Java rectangle path/bounds checks passed; 1000 poses exported for independent OpenCV verification.");
  }
}
