package in.rollingtable.prototype;
import android.app.Activity;
import android.os.*;
import android.graphics.*;
import android.view.View;
import android.widget.*;

/** Interactive offline mission UI. This Activity never opens a socket or arms a board. */
public final class DemoActivity extends Activity {
  private MissionDemo world=new MissionDemo();private final Handler handler=new Handler();private TextView state;private Floor floor;private boolean paused;
  private final Runnable tick=new Runnable(){public void run(){if(!paused)for(int i=0;i<10;i++)world.step();refresh();handler.postDelayed(this,50);}};
  public void onCreate(Bundle saved){super.onCreate(saved);LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(16,12,16,12);
    root.setOnApplyWindowInsetsListener((view,insets)->{root.setPadding(16,insets.getSystemWindowInsetTop()+12,16,insets.getSystemWindowInsetBottom()+12);return insets;});
    TextView title=new TextView(this);title.setText("Portiva offline mission demo");title.setTextSize(20);root.addView(title);
    TextView note=new TextView(this);note.setText("SIMULATION ONLY. Runs at 10 times speed. The room, person and sensors are artificial. Real motors always receive zero.");root.addView(note);
    state=new TextView(this);root.addView(state);
    row(root,new String[]{"Follow person","Load / stop","STOP"},new Runnable[]{()->world.follow(),()->world.load(),()->world.stop()});
    row(root,new String[]{"Send kitchen","Send pooja","Unloaded: return"},new Runnable[]{()->world.send("kitchen"),()->world.send("pooja"),()->world.returnAfterUnloading()});
    row(root,new String[]{"Obstacle fault","Lost person","Stale pose"},new Runnable[]{()->world.setFault(MissionDemo.Fault.OBSTACLE),()->world.setFault(MissionDemo.Fault.LOST_PERSON),()->world.setFault(MissionDemo.Fault.STALE_POSE)});
    row(root,new String[]{"Clear fault","Resume","Reset"},new Runnable[]{()->world.setFault(MissionDemo.Fault.NONE),()->world.resume(),()->world=new MissionDemo()});
    row(root,new String[]{"Pause / play","Back"},new Runnable[]{()->paused=!paused,()->finish()});
    row(root,new String[]{"Apply queued request to simulation"},new Runnable[]{()->{RequestInbox.Request r=RequestService.inbox.poll();world.accept(r,SystemClock.elapsedRealtime());}});
    floor=new Floor();root.addView(floor,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);refresh();
  }
  private void row(LinearLayout parent,String[] names,Runnable[] actions){LinearLayout row=new LinearLayout(this);for(int i=0;i<names.length;i++){Button b=new Button(this);b.setText(names[i]);final Runnable action=actions[i];b.setOnClickListener(v->{action.run();refresh();});row.addView(b,new LinearLayout.LayoutParams(0,-2,1));}parent.addView(row);}
  private void refresh(){state.setText("Virtual state: "+world.state()+" | "+world.now/1000+"s\n"+world.status+"\nPhysical PWM: 0 / 0");floor.invalidate();}
  public void onResume(){super.onResume();handler.post(tick);}public void onPause(){handler.removeCallbacks(tick);super.onPause();}
  private final class Floor extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);Floor(){super(DemoActivity.this);}
    protected void onDraw(Canvas c){super.onDraw(c);float s=Math.min(getWidth()/3.6f,getHeight()/2f),top=(getHeight()-2*s)/2;
      c.save();c.translate(0,top);p.setColor(Color.rgb(240,244,246));c.drawRect(0,0,3.6f*s,2*s,p);
      p.setColor(Color.DKGRAY);c.drawRect(1.8f*s,0,1.81f*s,.72f*s,p);c.drawRect(1.8f*s,1.28f*s,1.81f*s,2*s,p);
      p.setColor(Color.rgb(20,105,135));c.save();c.translate((float)world.x*s,(float)world.y*s);c.rotate((float)Math.toDegrees(world.yaw));c.drawRect(-.195f*s,-.205f*s,.195f*s,.205f*s,p);c.restore();
      p.setColor(Color.WHITE);p.setStrokeWidth(3);c.drawLine((float)world.x*s,(float)world.y*s,(float)(world.x+.20*Math.cos(world.yaw))*s,(float)(world.y+.20*Math.sin(world.yaw))*s,p);
      if(world.state()==MissionController.State.FOLLOW){p.setColor(Color.rgb(190,115,10));c.drawCircle((float)world.targetX*s,(float)world.targetY*s,.06f*s,p);}
      p.setTextSize(13);p.setColor(Color.BLACK);c.drawText("Virtual pooja",.3f*s,.4f*s,p);c.drawText("Virtual kitchen",2.6f*s,.4f*s,p);c.restore();
    }
  }
}
