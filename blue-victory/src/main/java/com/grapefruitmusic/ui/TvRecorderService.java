package com.grapefruitmusic.ui;
import com.grapefruitmusic.R;
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
 * (TvRecordings.wake), and it stops itself when nothing is left to do.
 *
 * The viewer's own services often allow only so many streams at once (Connections): a recording takes
 * one, and stops what the app is playing from the same service if there isn't another; a download waits.
 */
public final class TvRecorderService extends Service {
    static final String WAKE="io.github.hayliewordsman.bluegrapefruit.victory.WAKE", STOP_ALL="io.github.hayliewordsman.bluegrapefruit.victory.STOP_ALL";
    private static final int NOTE=2007;
    /** The jobs running now, by item ID (static: the app and the service share one process). */
    private static final Map<String,Job> running=new HashMap<>();
    private static TvRecorderService current;
    private final Handler main=new Handler(Looper.getMainLooper());
    private PowerManager.WakeLock wake;
    private android.net.wifi.WifiManager.WifiLock wifi;

    static PendingIntent pending(Context c) {
        Intent i=TvRecordings.intent(c,WAKE);
        int flags=PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0);
        return Build.VERSION.SDK_INT>=26?PendingIntent.getForegroundService(c,1,i,flags):PendingIntent.getService(c,1,i,flags);
    }
    static void start(Context c) {
        Intent i=TvRecordings.intent(c,WAKE);
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
        wake=power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"BlueGrapefruit:recorder");wake.setReferenceCounted(false);
        android.net.wifi.WifiManager w=(android.net.wifi.WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);
        if(w!=null){wifi=w.createWifiLock(Build.VERSION.SDK_INT>=29?android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF:android.net.wifi.WifiManager.WIFI_MODE_FULL,"BlueGrapefruit:recorder");wifi.setReferenceCounted(false);}
    }
    @Override public int onStartCommand(Intent intent,int flags,int id){
        foreground(notification());
        if(intent!=null&&STOP_ALL.equals(intent.getAction()))synchronized(TvRecorderService.class){for(Job j:running.values()){j.stop=true;j.paused=j.item.download();}}
        pump();
        return START_STICKY; // if Android ends the process, it starts the service again to carry on
    }
    @Override public void onDestroy(){
        if(wake.isHeld())wake.release();if(wifi!=null&&wifi.isHeld())wifi.release();
        if(current==this)current=null;super.onDestroy();
    }
    /** Android 15+: a foreground service past its time limit. Special use has none, but end cleanly if asked. */
    @Override public void onTimeout(int startId,int type){synchronized(TvRecorderService.class){for(Job j:running.values())j.stop=true;}}

    private void foreground(Notification n){
        if(Build.VERSION.SDK_INT>=34)startForeground(NOTE,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(NOTE,n);
    }
    /** Starts whatever is due, marks what was missed, and sets the alarm for what's next; stops when idle. */
    private void pump(){synchronized(TvRecorderService.class){pumpLocked();}}
    private void pumpLocked() {
        long now=System.currentTimeMillis();
        SharedPrefs prefs=new SharedPrefs(this);
        for(TvRecordings.Item i:TvRecordings.all(this)){
            if(running(i.id))continue;
            if(!i.download()&&(TvRecordings.SCHEDULED.equals(i.state)||TvRecordings.RECORDING.equals(i.state))){
                if(i.to<=now){
                    // Its time passed with nothing running it (the phone was off, or the app was force-stopped).
                    boolean some=new File(i.file).length()>0;
                    i.state=some?TvRecordings.FINISHED:TvRecordings.FAILED;
                    if(!some)i.error="Missed: the phone was off, or "+"Blue Grapefruit"+" was stopped, at the time.";
                    TvRecordings.updateExisting(this,i);
                } else if(i.from<=now+5000)begin(i,prefs);
            } else if(i.download()&&(TvRecordings.QUEUED.equals(i.state)||TvRecordings.DOWNLOADING.equals(i.state))){
                if(prefs.wifiOnly&&!unmetered()){if(!"Waiting for Wi-Fi".equals(i.error)){i.error="Waiting for Wi-Fi";i.state=TvRecordings.QUEUED;TvRecordings.updateExisting(this,i);}continue;}
                begin(i,prefs);
            }
        }
        if(running.isEmpty()){
            if(wake.isHeld())wake.release();if(wifi!=null&&wifi.isHeld())wifi.release();
            boolean waiting=false;for(TvRecordings.Item i:TvRecordings.all(this))if(i.waiting()&&i.from<=now+5000&&i.to>now)waiting=true;
            if(waiting){foreground(notification());main.postDelayed(this::pump,20_000);}
            else{TvRecordings.wake(this);stopForeground(true);stopSelf();}
        } else {
            wake.acquire(12*60*60*1000L);if(wifi!=null)wifi.acquire();
            foreground(notification());
        }
    }
    private void begin(final TvRecordings.Item item,final SharedPrefs prefs) {
        if(item.added&&!Connections.take(item.sourceId,Connections.limit(this,item.sourceId),!item.download())){
            if(!"Waiting for a free stream".equals(item.error)){item.error="Waiting for a free stream";TvRecordings.updateExisting(this,item);}
            main.postDelayed(this::pump,20_000);return;
        }
        final Job job=new Job(item);running.put(item.id,job);
        item.state=item.download()?TvRecordings.DOWNLOADING:TvRecordings.RECORDING;item.error="";TvRecordings.updateExisting(this,item);
        new Thread(()->{
            String error=null;
            try{
                TvRecorder.save(item.url,item.userAgent,item.added,new File(item.file),item.download()?Long.MAX_VALUE:item.to,prefs.maxHeight,new TvRecorder.Progress(){
                    long shown,checkedSpace;
                    public void saved(long bytes,long total){
                        item.bytes=bytes;if(total>0)item.total=total;
                        if(System.currentTimeMillis()-shown>3000){shown=System.currentTimeMillis();TvRecordings.updateExisting(TvRecorderService.this,item);main.post(()->show(notification()));}
                    }
                    public boolean stopped(){
                        if(job.stop)return true;
                        long now=System.currentTimeMillis();if(now-checkedSpace>1000){checkedSpace=now;if(TvRecordings.free(TvRecorderService.this)<32L*1024*1024){job.stop=true;job.reason="Storage is full.";return true;}}
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
        if(error==null&&!job.reason.isEmpty())error=job.reason;
        TvRecordings.Item item=job.item;
        File file=new File(item.file);
        if(job.paused){item.state=TvRecordings.QUEUED;}
        else if(item.download()){
            if(error==null&&file.exists()){item.state=TvRecordings.FINISHED;item.bytes=file.length();}
            else{item.state=TvRecordings.FAILED;item.error=error==null?"The download stopped.":error;}
        } else {
            boolean some=file.length()>0;
            item.state=some?TvRecordings.FINISHED:TvRecordings.FAILED;item.bytes=file.length();
            if(job.stop&&System.currentTimeMillis()<item.to)item.to=System.currentTimeMillis(); // stopped early
            item.error=error==null?"":some?"Ended early: "+error:error;
        }
        if(!TvRecordings.updateExisting(this,item)){file.delete();new File(item.file+".part").delete();}
        synchronized(TvRecorderService.class){running.remove(item.id);}
        main.post(()->{TvDownloads.wake(this);pump();});
    }
    private boolean unmetered(){
        ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
        if(cm==null)return false;
        NetworkInfo n=cm.getActiveNetworkInfo();
        return n!=null&&n.isConnected()&&!cm.isActiveNetworkMetered();
    }

    // ---- The notification. ----

    private Notification notification() {
        int recording=0,downloading=0;String title=null;int percent=-1;
        synchronized(TvRecorderService.class){
            for(Job j:running.values()){
                if(j.item.download()){downloading++;if(j.item.total>0)percent=(int)(j.item.bytes*100/j.item.total);}else recording++;
                if(title==null)title=j.item.title;
            }
        }
        String text=recording+downloading==0?"Getting ready…":(recording>0?"Recording "+(recording==1?title:recording+" programs"):"")
            +(recording>0&&downloading>0?" · ":"")+(downloading>0?"Downloading "+(downloading==1&&recording==0?title:downloading+" videos")+(percent>=0?" ("+percent+"%)":""):"");
        Intent open=new Intent(this,MainActivity.class).putExtra("open_tv_section","Recordings").addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int f=Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT:PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent tap=PendingIntent.getActivity(this,2,open,f);
        PendingIntent stop=PendingIntent.getService(this,3,TvRecordings.intent(this,STOP_ALL),f);
        Notification.Builder b=com.grapefruitmusic.compat.Platform.notification(this,"tv_recordings","TV Recordings");
        b.setSmallIcon(android.R.drawable.presence_video_online).setContentTitle("Blue Grapefruit").setContentText(text).setOngoing(true).setContentIntent(tap);
        b.addAction(0,"Stop",stop);
        if(percent>=0&&recording==0)b.setProgress(100,percent,false);
        if(Build.VERSION.SDK_INT>=31)b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        return Build.VERSION.SDK_INT>=16?b.build():b.getNotification();
    }
    private void show(Notification n){
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        synchronized(TvRecorderService.class){if(!running.isEmpty())nm.notify(NOTE,n);}
    }
    private static final class Job { final TvRecordings.Item item;volatile boolean stop,paused;volatile String reason=""; Job(TvRecordings.Item i){item=i;} }
    /** The settings a job reads once. */
    static final class SharedPrefs {
        final boolean wifiOnly;final int maxHeight;
        SharedPrefs(Context c){
            android.content.SharedPreferences p=c.getSharedPreferences("blue_tv",0);
            wifiOnly=com.grapefruitmusic.util.Prefs.wifiOnlyDownloads();
            int q=p.getInt("quality",0);maxHeight=q==2?360:q==1?480:Build.VERSION.SDK_INT<21?720:1080;
        }
    }
}
