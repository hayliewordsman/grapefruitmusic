package com.grapefruitmusic.ui;

import android.app.*;
import android.content.*;
import android.os.*;
import com.grapefruitmusic.compat.Platform;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Foreground, resumable On Demand downloads. No DVR service or scheduler is installed. */
public final class TvDownloadService extends Service {
    private static final int NOTE=202;
    private static volatile String activeId="",activeSource="";
    private static volatile boolean cancel;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private boolean working,destroyed;
    private PowerManager.WakeLock wake;
    static void cancel(String id){if(activeId.equals(id))cancel=true;}
    static void pauseSource(String id){if(activeSource.equals(id))cancel=true;}
    @Override public void onCreate(){super.onCreate();PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);if(pm!=null){wake=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,getPackageName()+":TVDownloads");wake.setReferenceCounted(false);}}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null&&"pause".equals(intent.getAction())){cancel=true;for(TvDownloads.Item i:TvDownloads.all(this))if(TvDownloads.QUEUED.equals(i.state)||TvDownloads.RUNNING.equals(i.state)){i.state=TvDownloads.PAUSED;TvDownloads.put(this,i);}}
        startForeground(NOTE,notification("Preparing TV downloads"));
        if(!working){working=true;worker.execute(this::pump);}return START_NOT_STICKY;
    }
    private void pump(){
        try{
            for(TvDownloads.Item stale:TvDownloads.all(this))if(TvDownloads.RUNNING.equals(stale.state)){stale.state=TvDownloads.QUEUED;TvDownloads.updateExisting(this,stale);}
            while(!destroyed){
                if(!TvDownloads.networkReady(this))break;
                TvDownloads.Item selected=null;
                for(TvDownloads.Item i:TvDownloads.all(this))if(TvDownloads.QUEUED.equals(i.state)&&Connections.take(i.sourceId,Connections.limit(this,i.sourceId))){selected=i;break;}
                if(selected==null)break;
                final TvDownloads.Item item=selected;activeId=item.id;activeSource=item.sourceId;cancel=false;
                item.state=TvDownloads.RUNNING;item.error="";TvDownloads.updateExisting(this,item);
                main.post(()->notifyProgress("Downloading "+item.title));
                if(wake!=null&&!wake.isHeld())wake.acquire(6*60*60*1000L);
                try{
                    int quality=TvRepository.get(this).prefs.getInt("quality",0);int maxHeight=quality==2?360:quality==1?480:Build.VERSION.SDK_INT<21?720:0;
                    TvFileDownload.save(item.url,item.userAgent,true,new File(item.file),maxHeight,new TvFileDownload.Progress(){
                        long last;
                        public boolean stopped(){return destroyed||cancel||!TvDownloads.networkReady(TvDownloadService.this)||TvDownloads.get(TvDownloadService.this,item.id)==null;}
                        public void saved(long bytes,long total){item.bytes=bytes;item.total=total;if(System.currentTimeMillis()-last>1500){last=System.currentTimeMillis();TvDownloads.updateExisting(TvDownloadService.this,item);main.post(()->notifyProgress("Downloading "+item.title+(total>0?" · "+(bytes*100/total)+"%":"")));}}
                    });
                    if(new File(item.file).isFile()){item.state=TvDownloads.DONE;item.bytes=new File(item.file).length();}
                    else {TvDownloads.Item current=TvDownloads.get(this,item.id);item.state=cancel||(current!=null&&TvDownloads.PAUSED.equals(current.state))?TvDownloads.PAUSED:TvDownloads.QUEUED;}
                }catch(IOException e){item.state=cancel?TvDownloads.PAUSED:!TvDownloads.networkReady(this)?TvDownloads.QUEUED:TvDownloads.FAILED;item.error=TvDownloads.FAILED.equals(item.state)?e.getMessage():"";}
                finally{Connections.give(item.sourceId);activeId="";activeSource="";TvDownloads.updateExisting(this,item);if(wake!=null&&wake.isHeld())wake.release();}
            }
        }finally{main.post(()->{working=false;stopForeground(true);stopSelf();});}
    }
    private Notification notification(String text){
        Intent open=new Intent(this,MainActivity.class).putExtra("open_tv_section","Downloads").addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent tap=PendingIntent.getActivity(this,202,open,Platform.pendingFlags(PendingIntent.FLAG_UPDATE_CURRENT));
        return Platform.notification(this,"tv_downloads","TV downloads").setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle("Blue Grapefruit").setContentText(text).setContentIntent(tap).setOngoing(true).build();
    }
    private void notifyProgress(String text){if(!destroyed)((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(NOTE,notification(text));}
    @Override public void onDestroy(){destroyed=true;cancel=true;worker.shutdownNow();if(wake!=null&&wake.isHeld())wake.release();super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public void onTimeout(int id,int type){cancel=true;stopForeground(true);stopSelf();}
}
