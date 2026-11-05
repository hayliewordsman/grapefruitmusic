package com.grapefruitmusic.ui;
import android.app.Notification;
import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.*;
/** Loaded only on Android 5+; API 17 continues to use the foreground notification controls. */
@android.annotation.TargetApi(21)
final class TvMediaSession {
    private final MediaSession media;private final TvSession player;
    TvMediaSession(Context c,TvSession p){player=p;media=new MediaSession(c,"BlueGrapefruitTV");media.setCallback(new MediaSession.Callback(){
        public void onPlay(){if(player.current!=null)player.play(player.current);}
        public void onPause(){player.pause(false);}
        public void onStop(){player.stop();}
        public void onSkipToNext(){player.next(1);}
        public void onSkipToPrevious(){player.next(-1);}
        public void onSeekTo(long at){player.seekTo(at);}
    });media.setActive(true);}
    void update(){media.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE,player.current==null?"Blue Grapefruit":player.current.name).putString(MediaMetadata.METADATA_KEY_ARTIST,"Blue Grapefruit TV").putLong(MediaMetadata.METADATA_KEY_DURATION,player.duration()).build());
        media.setPlaybackState(new PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_STOP|PlaybackState.ACTION_SKIP_TO_NEXT|PlaybackState.ACTION_SKIP_TO_PREVIOUS|PlaybackState.ACTION_SEEK_TO).setState(player.playing()?PlaybackState.STATE_PLAYING:PlaybackState.STATE_PAUSED,player.position(),player.playing()?1:0).build());}
    void style(Notification.Builder b){b.setStyle(new Notification.MediaStyle().setMediaSession(media.getSessionToken()).setShowActionsInCompactView(0,1,2));}
    void release(){media.setActive(false);media.release();}
}
