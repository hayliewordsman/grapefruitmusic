package com.grapefruitmusic.ui;

import android.content.*;
import android.os.*;
import android.view.View;
import com.grapefruitmusic.compat.Platform;
import com.grapefruitmusic.playback.PlaybackSwitch;
import java.util.concurrent.CopyOnWriteArrayList;

/** One application-owned TV player, independent of the Activity and its current video surface. */
final class TvSession implements LivePlayer.Listener {
    private static TvSession instance;
    static synchronized TvSession get(Context c){if(instance==null)instance=new TvSession(c.getApplicationContext());return instance;}
    final Context context;
    final TvRepository repository;
    final CopyOnWriteArrayList<Runnable> listeners=new CopyOnWriteArrayList<Runnable>();
    LivePlayer engine;
    Channel current;
    boolean selected,foreground,watchVisible;
    boolean captionsAvailable;
    CharSequence captions="";
    String status="",format="";
    float aspect=16f/9f;
    private TvSession(Context c){
        context=c;repository=TvRepository.get(c);
        PlaybackSwitch.pauseVideo=()->pause(true);
        PlaybackSwitch.videoKeys=key->{
            if(!active())return false;
            switch(key){
                case android.view.KeyEvent.KEYCODE_HEADSETHOOK:
                case android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:toggle();return true;
                case android.view.KeyEvent.KEYCODE_MEDIA_PLAY:if(!playing()&&current!=null)play(current);return true;
                case android.view.KeyEvent.KEYCODE_MEDIA_PAUSE:pause(false);return true;
                case android.view.KeyEvent.KEYCODE_MEDIA_STOP:stop();return true;
                case android.view.KeyEvent.KEYCODE_MEDIA_NEXT:next(1);return true;
                case android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS:next(-1);return true;
                default:return false;
            }
        };
        IntentFilter filter=new IntentFilter("android.net.conn.CONNECTIVITY_CHANGE");
        BroadcastReceiver receiver=new BroadcastReceiver(){public void onReceive(Context c,Intent i){if(TvDownloads.networkReady(c))TvDownloads.wake(c);}};
        if(Build.VERSION.SDK_INT>=33)c.registerReceiver(receiver,filter,Context.RECEIVER_NOT_EXPORTED);else c.registerReceiver(receiver,filter);
    }
    boolean play(Channel channel){
        synchronized(Connections.class){
        if(Channel.SERIES.equals(channel.kind))return false;
        if(channel.added()&&!channel.local()&&!Connections.canWatch(Guide.sourceId(channel),Connections.limit(context,Guide.sourceId(channel)))){
            com.grapefruitmusic.util.Notice.showLong(context,"A download is using this provider's connection. Pause it in TV Downloads, then try again.");return false;
        }
        saveResume();if(engine!=null){engine.release();engine=null;}
        current=channel;selected=true;captions="";captionsAvailable=false;status="Connecting…";
        com.grapefruitmusic.playback.Player.get(context).pause();
        com.grapefruitmusic.radio.Radio.get(context).stop();
        engine=new LivePlayer(context,this,repository.prefs.getInt("quality",0));
        engine.setCaptions(repository.prefs.getBoolean("captions",false),repository.prefs.getString("captionLanguage","en"));
        engine.setVideoEnabled(foreground);
        engine.tune(channel,channel.live()?0:repository.prefs.getLong("resume:"+channel.id,0));
        Platform.service(context,new Intent(context,TvPlaybackService.class));changed();return true;
        }
    }
    boolean active(){return selected&&current!=null;}
    boolean playing(){return active()&&engine!=null&&!engine.paused();}
    long position(){return engine==null?(current==null?0:repository.prefs.getLong("resume:"+current.id,0)):engine.position();}
    long duration(){return engine==null?0:engine.duration();}
    void saveResume(){if(current!=null&&!current.live()&&engine!=null){long at=engine.position(),duration=engine.duration();repository.prefs.edit().putLong("resume:"+current.id,duration>0&&at>duration-5000?0:at).apply();}}
    void toggle(){if(playing())pause(false);else if(current!=null)play(current);}
    void pause(boolean forAudio){
        if(current==null)return;saveResume();if(engine!=null){engine.release();engine=null;}
        if(forAudio)selected=false;status="Paused";context.stopService(new Intent(context,TvPlaybackService.class));changed();TvDownloads.wake(context);
    }
    void stop(){pause(true);current=null;captions="";changed();}
    void seekBy(long delta){if(engine!=null&&!current.live())engine.seekBy(delta);}
    void seekTo(long position){if(engine!=null&&!current.live())engine.seekTo(position);}
    void next(int delta){
        if(current==null)return;
        if(!current.live()){seekBy(delta*15000L);return;}
        java.util.List<Channel> list=repository.channels;if(list.isEmpty())return;int index=0;for(int n=0;n<list.size();n++)if(list.get(n).id.equals(current.id)){index=n;break;}
        play(list.get((index+delta+list.size())%list.size()));
    }
    void settingsChanged(){if(engine!=null){engine.setQuality(repository.prefs.getInt("quality",0));engine.setCaptions(repository.prefs.getBoolean("captions",false),repository.prefs.getString("captionLanguage","en"));}changed();}
    void attach(View surface){if(engine==null)return;View desired=foreground&&selected?surface:null;if(engine.view()!=desired)engine.attach(desired);}
    void foreground(boolean visible){
        foreground=visible;
        if(!visible){saveResume();if(!repository.prefs.getBoolean("backgroundAudio",true))pause(false);else if(engine!=null)engine.setVideoEnabled(false);}
        else {if(engine!=null)engine.setVideoEnabled(true);TvDownloads.wake(context);}
        changed();
    }
    void changed(){for(Runnable r:listeners)r.run();TvPlaybackService.refresh();}
    public void status(String text){if("Finished".equals(text)&&engine!=null){pause(false);}if(!text.equals(status)){status=text;changed();}}
    public void format(String text){if(!text.equals(format)){format=text;changed();}}
    public void aspect(float ratio){if(Math.abs(aspect-ratio)>0.001){aspect=ratio;changed();}}
    public void captionsAvailable(boolean value){captionsAvailable=value;changed();}
    public void captions(CharSequence text){captions=text;changed();}
    public void unavailable(boolean location){status=location?"Not available in your region":"This stream is unavailable. Try Retry or another channel.";changed();}
}
