package com.grapefruitfast;
import android.app.*;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.*;
import java.io.File;
import java.util.*;

/**
 * Records and downloads with the app in the background, or swiped out of Recents: a foreground service
 * with a notification (Android keeps it running; on Android 14+ it's a "special use" service, which has
 * no time limit, unlike data sync's 6 hours a day). An exact alarm starts it when a recording is due
 * (Recordings.wake), and it stops itself when nothing is left to do.
 *
 * The viewer's own services often allow only so many streams at once (Connections): a recording takes
 * one, and stops what the app is playing from the same service if there isn't another; a download waits.
 */
public final class RecorderService extends Service {
    static final String WAKE="com.grapefruitfast.WAKE", STOP_ALL="com.grapefruitfast.STOP_ALL";
    private static final int NOTE=7;
    /** The jobs running now, by item ID (static: the app and the service share one process). */
    private static final Map<String,Job> running=new HashMap<>();
    private static RecorderService current;
    private final Handler main=new Handler(Looper.getMainLooper());
    private PowerManager.WakeLock wake;
    private android.net.wifi.WifiManager.WifiLock wifi;

    static PendingIntent pending(Context c) {
        Intent i=Recordings.intent(c,WAKE);
        int flags=PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0);
        return Build.VERSION.SDK_INT>=26?PendingIntent.getForegroundService(c,1,i,flags):PendingIntent.getService(c,1,i,flags);
    }
    static void start(Context c) {
        Intent i=Recordings.intent(c,WAKE);
        try{if(Build.VERSION.SDK_INT>=26)c.startForegroundService(i);else c.startService(i);}
        catch(RuntimeException e){/* Android refused a start from the background: the alarm brings it */}
    }
    /** Stops one job (a recording ends early and keeps what it has; a download pauses). */
    static synchronized void stop(String id){Job j=running.get(id);if(j!=null)j.stop=true;}
    static synchronized boolean running(String id){return running.containsKey(id);}
    /** Stops (or pauses) every job using one of the viewer's services, to free its streams for watching. */
    static synchronized void stopSource(String sourceId){for(Job j:running.values())if(j.item.sourceId.equals(sourceId)){j.stop=true;j.paused=j.item.download();}}

    @Override public IBinder onBind(Intent i){return null;}
    @Override public void onCreate(){
        super.onCreate();current=this;
        PowerManager power=(PowerManager)getSystemService(POWER_SERVICE);
        wake=power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"GrapefruitTV:recorder");wake.setReferenceCounted(false);
        android.net.wifi.WifiManager w=(android.net.wifi.WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);
        if(w!=null){wifi=w.createWifiLock(Build.VERSION.SDK_INT>=29?android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF:android.net.wifi.WifiManager.WIFI_MODE_FULL,"GrapefruitTV:recorder");wifi.setReferenceCounted(false);}
    }
    @Override public int onStartCommand(Intent intent,int flags,int id){
        foreground(notification());
        if(intent!=null&&STOP_ALL.equals(intent.getAction()))synchronized(RecorderService.class){for(Job j:running.values()){j.stop=true;j.paused=j.item.download();}}
        pump();
        return START_STICKY; // if Android ends the process, it starts the service again to carry on
    }
    @Override public void onDestroy(){
        if(wake.isHeld())wake.release();if(wifi!=null&&wifi.isHeld())wifi.release();
        if(current==this)current=null;super.onDestroy();
    }
    /** Android 15+: a foreground service past its time limit. Special use has none, but end cleanly if asked. */
    @Override public void onTimeout(int startId,int type){synchronized(RecorderService.class){for(Job j:running.values())j.stop=true;}}

    private void foreground(Notification n){
        if(Build.VERSION.SDK_INT>=34)startForeground(NOTE,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(NOTE,n);
    }
    /** Starts whatever is due, marks what was missed, and sets the alarm for what's next; stops when idle. */
    private void pump(){synchronized(RecorderService.class){pumpLocked();}}
    private void pumpLocked() {
        long now=System.currentTimeMillis();
        SharedPrefs prefs=new SharedPrefs(this);
        for(Recordings.Item i:Recordings.all(this)){
            if(running(i.id))continue;
            if(!i.download()&&(Recordings.SCHEDULED.equals(i.state)||Recordings.RECORDING.equals(i.state))){
                if(i.to<=now){
                    // Its time passed with nothing running it (the phone was off, or the app was force-stopped).
                    boolean some=new File(i.file).length()>0;
                    i.state=some?Recordings.FINISHED:Recordings.FAILED;
                    if(!some)i.error="Missed: the phone was off, or "+MainActivity.NAME+" was stopped, at the time.";
                    Recordings.put(this,i);
                } else if(i.from<=now+5000)begin(i,prefs);
            } else if(i.download()&&(Recordings.QUEUED.equals(i.state)||Recordings.DOWNLOADING.equals(i.state))){
                if(prefs.wifiOnly&&!unmetered()){if(!"Waiting for Wi-Fi".equals(i.error)){i.error="Waiting for Wi-Fi";i.state=Recordings.QUEUED;Recordings.put(this,i);}continue;}
                begin(i,prefs);
            }
        }
        if(running.isEmpty()){
            if(wake.isHeld())wake.release();if(wifi!=null&&wifi.isHeld())wifi.release();
            Recordings.wake(this); // the alarm for the next one (and a restart if a download waits for Wi-Fi)
            boolean waiting=false;for(Recordings.Item i:Recordings.all(this))if(i.download()&&Recordings.QUEUED.equals(i.state))waiting=true;
            if(waiting)main.postDelayed(this::pump,60_000); // check for Wi-Fi again in a minute
            else{stopForeground(true);stopSelf();}
        } else {
            wake.acquire(12*60*60*1000L);if(wifi!=null)wifi.acquire();
            foreground(notification());
        }
    }
    private void begin(final Recordings.Item item,final SharedPrefs prefs) {
        if(item.added&&!Connections.take(item.sourceId,Connections.limit(this,item.sourceId),!item.download())){
            if(!"Waiting for a free stream".equals(item.error)){item.error="Waiting for a free stream";Recordings.put(this,item);}
            main.postDelayed(this::pump,20_000);return;
        }
        final Job job=new Job(item);running.put(item.id,job);
        item.state=item.download()?Recordings.DOWNLOADING:Recordings.RECORDING;item.error="";Recordings.put(this,item);
        new Thread(()->{
            String error=null;
            try{
                Recorder.save(item.url,item.userAgent,item.added,new File(item.file),item.download()?Long.MAX_VALUE:item.to,prefs.maxHeight,new Recorder.Progress(){
                    long shown;
                    public void saved(long bytes,long total){
                        item.bytes=bytes;if(total>0)item.total=total;
                        if(System.currentTimeMillis()-shown>3000){shown=System.currentTimeMillis();Recordings.put(RecorderService.this,item);main.post(()->show(notification()));}
                    }
                    public boolean stopped(){
                        if(job.stop)return true;
                        if(item.download()&&prefs.wifiOnly&&!unmetered()){job.stop=true;job.paused=true;item.error="Waiting for Wi-Fi";return true;}
                        return false;
                    }
                });
            }catch(Exception e){error=e.getMessage()==null?"The stream stopped.":e.getMessage();}
            finally{if(item.added)Connections.give(item.sourceId);}
            finish(job,error);
        },"record-"+item.id).start();
    }
    private void finish(Job job,String error) {
        Recordings.Item item=job.item;
        File file=new File(item.file);
        if(job.paused){item.state=Recordings.QUEUED;}
        else if(item.download()){
            if(error==null&&file.exists()){item.state=Recordings.FINISHED;item.bytes=file.length();}
            else{item.state=Recordings.FAILED;item.error=error==null?"The download stopped.":error;}
        } else {
            boolean some=file.length()>0;
            item.state=some?Recordings.FINISHED:Recordings.FAILED;item.bytes=file.length();
            if(job.stop&&System.currentTimeMillis()<item.to)item.to=System.currentTimeMillis(); // stopped early
            item.error=error==null?"":some?"Ended early: "+error:error;
        }
        Recordings.put(this,item);
        synchronized(RecorderService.class){running.remove(item.id);}
        main.post(this::pump);
    }
    private boolean unmetered(){
        ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
        if(cm==null)return false;
        NetworkInfo n=cm.getActiveNetworkInfo();
        return n!=null&&n.isConnected()&&!cm.isActiveNetworkMetered();
    }

    // ---- The notification. ----

    private Notification notification() {
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if(Build.VERSION.SDK_INT>=26&&nm.getNotificationChannel("recordings")==null)
            nm.createNotificationChannel(new NotificationChannel("recordings","Recordings and downloads",NotificationManager.IMPORTANCE_LOW));
        int recording=0,downloading=0;String title=null;int percent=-1;
        synchronized(RecorderService.class){
            for(Job j:running.values()){
                if(j.item.download()){downloading++;if(j.item.total>0)percent=(int)(j.item.bytes*100/j.item.total);}else recording++;
                if(title==null)title=j.item.title;
            }
        }
        String text=recording+downloading==0?"Getting ready…":(recording>0?"Recording "+(recording==1?title:recording+" programs"):"")
            +(recording>0&&downloading>0?" · ":"")+(downloading>0?"Downloading "+(downloading==1&&recording==0?title:downloading+" videos")+(percent>=0?" ("+percent+"%)":""):"");
        Intent open=new Intent(this,MainActivity.class).putExtra("tab","Recordings").addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int f=Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT:PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent tap=PendingIntent.getActivity(this,2,open,f);
        PendingIntent stop=PendingIntent.getService(this,3,Recordings.intent(this,STOP_ALL),f);
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,"recordings"):new Notification.Builder(this);
        b.setSmallIcon(R.drawable.ic_stat_record).setContentTitle(MainActivity.NAME).setContentText(text).setOngoing(true).setContentIntent(tap);
        if(Build.VERSION.SDK_INT>=20)b.addAction(new Notification.Action.Builder(0,"Stop",stop).build());
        if(percent>=0&&recording==0)b.setProgress(100,percent,false);
        if(Build.VERSION.SDK_INT>=31)b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        return Build.VERSION.SDK_INT>=16?b.build():b.getNotification();
    }
    private void show(Notification n){
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        synchronized(RecorderService.class){if(!running.isEmpty())nm.notify(NOTE,n);}
    }
    private static final class Job { final Recordings.Item item;volatile boolean stop,paused; Job(Recordings.Item i){item=i;} }
    /** The settings a job reads once. */
    static final class SharedPrefs {
        final boolean wifiOnly;final int maxHeight;
        SharedPrefs(Context c){
            android.content.SharedPreferences p=c.getSharedPreferences("fast",0);
            wifiOnly=p.getBoolean("downloadWifiOnly",true);
            int q=p.getInt("quality",0);maxHeight=q==2?360:q==1?480:Build.VERSION.SDK_INT<21?720:1080;
        }
    }
}
