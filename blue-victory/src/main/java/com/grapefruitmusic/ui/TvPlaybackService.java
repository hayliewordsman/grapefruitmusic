package com.grapefruitmusic.ui;

import android.app.*;
import android.content.*;
import android.os.*;
import com.grapefruitmusic.compat.Platform;

/** Audio-only TV playback while Blue is in the background, with transport controls. */
public final class TvPlaybackService extends Service {
    private static final int NOTE=201;
    private static TvPlaybackService active;
    private TvMediaSession media;
    private android.os.PowerManager.WakeLock wake;
    static void refresh(){if(active!=null)active.update();}
    @Override public void onCreate(){super.onCreate();active=this;
        PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);if(pm!=null){wake=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,getPackageName()+":TVAudio");wake.setReferenceCounted(false);wake.acquire(6*60*60*1000L);}
        if(Build.VERSION.SDK_INT>=21)media=new TvMediaSession(this,TvSession.get(this));
    }
    @Override public int onStartCommand(Intent intent,int flags,int id){
        TvSession session=TvSession.get(this);String action=intent==null?null:intent.getAction();
        if("stop".equals(action)){session.stop();stopForeground(true);stopSelf();return START_NOT_STICKY;}
        if("toggle".equals(action))session.toggle();else if("next".equals(action))session.next(1);else if("previous".equals(action))session.next(-1);
        if(session.active())startForeground(NOTE,notification());else {stopForeground(true);stopSelf();}
        return START_NOT_STICKY;
    }
    private PendingIntent action(String name,int id){return PendingIntent.getService(this,id,new Intent(this,TvPlaybackService.class).setAction(name),Platform.pendingFlags(PendingIntent.FLAG_UPDATE_CURRENT));}
    private Notification notification(){
        TvSession s=TvSession.get(this);
        Intent intent=new Intent(this,MainActivity.class).putExtra("open_tv_player",true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        Notification.Builder b=Platform.notification(this,"tv_playback","TV playback")
            .setSmallIcon(android.R.drawable.ic_media_play).setContentTitle(s.current==null?"Blue Grapefruit":s.current.name)
            .setContentText(!s.foreground?"TV · background audio":s.status).setOngoing(s.playing())
            .setContentIntent(PendingIntent.getActivity(this,201,intent,Platform.pendingFlags(PendingIntent.FLAG_UPDATE_CURRENT)))
            .addAction(android.R.drawable.ic_media_previous,"Previous",action("previous",211))
            .addAction(s.playing()?android.R.drawable.ic_media_pause:android.R.drawable.ic_media_play,s.playing()?"Pause":"Play",action("toggle",212))
            .addAction(android.R.drawable.ic_menu_close_clear_cancel,"Stop",action("stop",213));
        if(media!=null){media.update();media.style(b);}return b.build();
    }
    private void update(){if(TvSession.get(this).active())((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(NOTE,notification());}
    @Override public void onDestroy(){active=null;if(media!=null)media.release();if(wake!=null&&wake.isHeld())wake.release();super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
