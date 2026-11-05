package com.grapefruitmusic.playback;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.PowerManager;
import com.grapefruitmusic.util.Bg;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import java.io.FileInputStream;
import java.io.IOException;

/** Android's own MediaPlayer: no effects, but every format and stream the phone supports. */
final class MediaPlayerEngine implements Engine {
    private final MediaPlayer mp;
    // setDataSource(url) can wait for HTTP headers even before prepareAsync. Keep both source
    // setup and release on this serial worker; never hold a UI-thread lock around native I/O.
    private final ExecutorService sourceWorker=Executors.newSingleThreadExecutor(Bg.named("gf-media-source"));
    private String source;
    private boolean preparing,ready;
    private volatile boolean released;
    private float volume=1f;
    private Callback callback;
    private final PendingSeek seek = new PendingSeek();
    private volatile long length = -1;

    MediaPlayerEngine(Context context) { this(context,new MediaPlayer()); }

    MediaPlayerEngine(Context context,MediaPlayer player) {
        mp=player;
        mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
        mp.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK);
        mp.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer m) {
                if(released)return;
                ready=true;
                mp.setVolume(volume,volume);
                if (callback != null) callback.onPrepared(MediaPlayerEngine.this);
            }
        });
        mp.setOnSeekCompleteListener(new MediaPlayer.OnSeekCompleteListener() {
            @Override
            public void onSeekComplete(MediaPlayer m) {
                // Old Android may discard seeks issued while another seek is in flight.
                if(released)return;
                int next = seek.complete();
                if (!released&&ready&&next >= 0) mp.seekTo(next);
            }
        });
        mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer m) {
                if (!released && callback != null) callback.onCompletion(MediaPlayerEngine.this);
            }
        });
        mp.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer m, int what, int extra) {
                if (!released && callback != null) callback.onError(MediaPlayerEngine.this, "MediaPlayer error " + what + "/" + extra);
                return true;
            }
        });
    }

    @Override
    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    @Override
    public void setDataSource(String source) throws IOException {
        if(released||preparing)throw new IOException("Player is no longer available");
        if(source==null||source.isEmpty())throw new IOException("No audio source");
        this.source=source;
    }

    @Override
    public void prepareAsync() {
        if(released||preparing)return;
        preparing=true;
        final String address=source;
        sourceWorker.execute(new Runnable(){
            @Override public void run(){
                if(released)return;
                try{
                    if(address==null)throw new IOException("No audio source");
                    if(address.startsWith("/")){
                        try(FileInputStream input=new FileInputStream(address)){mp.setDataSource(input.getFD());}
                    }else mp.setDataSource(address);
                    if(!released)mp.prepareAsync();
                }catch(final IOException|RuntimeException error){
                    Bg.main(new Runnable(){@Override public void run(){
                        if(!released&&callback!=null)callback.onError(MediaPlayerEngine.this,"Audio source could not be opened");
                    }});
                }
            }
        });
    }

    @Override
    public void start() {
        if(!released&&ready)mp.start();
    }

    @Override
    public void pause() {
        if(!released&&ready)mp.pause();
    }

    @Override
    public boolean isPlaying() {
        return !released&&ready&&mp.isPlaying();
    }

    @Override
    public void seekTo(int ms) {
        int next = seek.request(ms);
        if (!released&&ready&&next >= 0) mp.seekTo(next);
    }

    @Override
    public int getCurrentPosition() {
        return !released&&ready?seek.position(mp.getCurrentPosition()):0;
    }

    @Override
    public int getDuration() {
        long l = length;
        return l > 0 ? (int) Math.min(Integer.MAX_VALUE, l) : !released&&ready?mp.getDuration():0;
    }

    @Override
    public void setLength(long ms) {
        length = ms;
    }

    @Override
    public void setVolume(float volume) {
        this.volume=volume;
        if(!released&&ready)mp.setVolume(volume, volume);
    }

    @Override
    public void setEffects(Effects effects) {
        // MediaPlayer plays the music untouched.
    }

    @Override
    public boolean hasEffects() {
        return false;
    }

    @Override
    public void release() {
        if(released)return;
        released=true;
        ready=false;
        callback=null;
        sourceWorker.execute(new Runnable(){@Override public void run(){
            try{mp.reset();}catch(RuntimeException ignored){/* Failed source setup may leave it in error. */}
            finally{mp.release();}
        }});
        sourceWorker.shutdown();
    }
}
