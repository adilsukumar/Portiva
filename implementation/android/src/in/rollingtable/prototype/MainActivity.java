package in.rollingtable.prototype;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;

public final class MainActivity extends Activity {
  private TextView label;private EditText ip,token;private Preview preview;private Handler handler=new Handler();
  private final Runnable refresh=new Runnable(){public void run(){label.setText(VisionService.status+"\n"+RequestService.status+"\nQueued requests: "+RequestService.inbox.size()+"\n"+RequestService.lastReviewed);preview.invalidate();handler.postDelayed(this,250);}};
  public void onCreate(Bundle bundle){super.onCreate(bundle);
    LinearLayout column=new LinearLayout(this);column.setPadding(18,18,18,18);column.setOrientation(1);
    TextView title=new TextView(this);title.setText("Portiva bench preview - motion disabled");title.setTextSize(20);column.addView(title);
    TextView note=new TextView(this);note.setText("This is a vision prototype, not a finished autonomous trolley. Tap the intended person. Similar people or loss require selection again.");column.addView(note);
    Button onboard=new Button(this);onboard.setText("Open phone's own camera bench (new design)");onboard.setOnClickListener(v->{stopService(new Intent(this,VisionService.class));startActivity(new Intent(this,PhoneCameraActivity.class));});column.addView(onboard);
    ip=new EditText(this);ip.setHint("Robot URL: http://192.168.4.1");ip.setText("http://192.168.4.1");column.addView(ip);
    token=new EditText(this);token.setHint("Local pairing token (32+ characters)");token.setInputType(129);column.addView(token);
    Button demo=new Button(this);demo.setText("Open offline mission demo (no robot needed)");demo.setOnClickListener(v->{stopService(new Intent(this,VisionService.class));startActivity(new Intent(this,DemoActivity.class));});column.addView(demo);
    Button requests=new Button(this);requests.setText("Start recipient request bench");requests.setOnClickListener(v->{if(Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=0)requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},1);startForegroundService(new Intent(this,RequestService.class));});column.addView(requests);
    Button endRequests=new Button(this);endRequests.setText("Stop recipient request bench");endRequests.setOnClickListener(v->stopService(new Intent(this,RequestService.class)));column.addView(endRequests);
    Spinner room=new Spinner(this);room.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Choose SEND destination","kitchen","pooja"}));room.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> a,View v,int position,long id){RequestService.selectedDestination=position==0?"":position==1?"kitchen":"pooja";}public void onNothingSelected(AdapterView<?> a){RequestService.selectedDestination="";}});column.addView(room);
    Button review=new Button(this);review.setText("Review next queued request (no movement)");review.setOnClickListener(v->{RequestInbox.Request r=RequestService.inbox.poll();RequestService.lastReviewed=r==null?"No request queued":"Reviewed "+r.action+" "+r.destination+" from "+r.source+". Motion remains disabled.";});column.addView(review);
    Button sample=new Button(this);sample.setText("Run offline detector test (no robot needed)");sample.setOnClickListener(v->{
      stopService(new Intent(this,VisionService.class));sample.setEnabled(false);
      new Thread(()->{try(Detector d=new Detector(this)){
        byte[] jpg;try(java.io.InputStream in=getAssets().open("bus.jpg")){jpg=VisionService.boundedRead(in,2000000);}
        Bitmap b=BitmapFactory.decodeByteArray(jpg,0,jpg.length);long[] times=new long[6];java.util.List<TargetTracker.Box> found=null;
        for(int n=0;n<6;n++){long begin=SystemClock.elapsedRealtime();found=d.detect(b);times[n]=SystemClock.elapsedRealtime()-begin;}
        long first=times[0];long[] warm=java.util.Arrays.copyOfRange(times,1,6);java.util.Arrays.sort(warm);
        VisionService.snapshot=new VisionService.Snapshot(b,found,SystemClock.elapsedRealtime());
        VisionService.status="Offline public photo: "+found.size()+" persons; first "+first+" ms; warm median "+warm[2]+" ms. Not Grandma/home validation. Motion disabled.";
      }catch(Exception e){VisionService.status="Offline test failed: "+e.toString();}finally{runOnUiThread(()->sample.setEnabled(true));}},"local-detector-test").start();
    });column.addView(sample);
    Button start=new Button(this);start.setText("Start camera preview");start.setOnClickListener(v->{
      if(Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=0)requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},1);
      Intent i=new Intent(this,VisionService.class);i.putExtra("url",ip.getText().toString().trim());i.putExtra("token",token.getText().toString());startForegroundService(i);
    });column.addView(start);
    Button stop=new Button(this);stop.setText("STOP preview");stop.setOnClickListener(v->stopService(new Intent(this,VisionService.class)));column.addView(stop);
    label=new TextView(this);column.addView(label);preview=new Preview();column.addView(preview,new LinearLayout.LayoutParams(-1,(int)(280*getResources().getDisplayMetrics().density)));ScrollView screen=new ScrollView(this);screen.addView(column);screen.setOnApplyWindowInsetsListener((view,insets)->{screen.setPadding(0,insets.getSystemWindowInsetTop(),0,insets.getSystemWindowInsetBottom());return insets;});setContentView(screen);
  }
  public void onResume(){super.onResume();handler.post(refresh);}
  public void onPause(){handler.removeCallbacks(refresh);super.onPause();}
  private final class Preview extends View {
    Paint p=new Paint();RectF displayed=new RectF();Preview(){super(MainActivity.this);}
    private VisionService.Snapshot shown;
    protected void onDraw(Canvas c){super.onDraw(c);shown=VisionService.snapshot;if(shown==null)return;Bitmap b=shown.image;
      float s=Math.min((float)getWidth()/b.getWidth(),(float)getHeight()/b.getHeight());float w=b.getWidth()*s,h=b.getHeight()*s;
      displayed.set((getWidth()-w)/2,(getHeight()-h)/2,(getWidth()+w)/2,(getHeight()+h)/2);c.drawBitmap(b,null,displayed,p);
      p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);p.setColor(Color.rgb(0,120,240));
      for(TargetTracker.Box q:shown.boxes)c.drawRect(displayed.left+q.x1*w,displayed.top+q.y1*h,displayed.left+q.x2*w,displayed.top+q.y2*h,p);
      p.setStyle(Paint.Style.FILL);
    }
    public boolean onTouchEvent(android.view.MotionEvent e){if(e.getAction()!=MotionEvent.ACTION_UP)return true;
      if(shown==null||SystemClock.elapsedRealtime()-shown.timestamp>350||!displayed.contains(e.getX(),e.getY()))return true;
      float x=(e.getX()-displayed.left)/displayed.width(),y=(e.getY()-displayed.top)/displayed.height();
      ArrayList<TargetTracker.Box> hits=new ArrayList<>();for(TargetTracker.Box b:shown.boxes)if(x>=b.x1&&x<=b.x2&&y>=b.y1&&y<=b.y2)hits.add(b);
      if(hits.size()==1)VisionService.tracker.enroll(hits.get(0),SystemClock.elapsedRealtime());else VisionService.tracker.clear();return true;
    }
  }
}
