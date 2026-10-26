package com.grapefruitmusic.playback;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.PowerManager;

import java.io.FileInputStream;
import java.io.IOException;

/** Android's own MediaPlayer: no effects, but every format and stream the phone supports. */
final class MediaPlayerEngine implements Engine {
    private final MediaPlayer mp = new MediaPlayer();
    private Callback callback;

    MediaPlayerEngine(Context context) {
        mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
        mp.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK);
        mp.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer m) {
                if (callback != null) callback.onPrepared(MediaPlayerEngine.this);
            }
        });
        mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer m) {
                if (callback != null) callback.onCompletion(MediaPlayerEngine.this);
            }
        });
        mp.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer m, int what, int extra) {
                if (callback != null) callback.onError(MediaPlayerEngine.this, "MediaPlayer error " + what + "/" + extra);
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
        if (source.startsWith("/")) {
            FileInputStream in = new FileInputStream(source);
            try {
                // A file descriptor works for files in app-private storage too.
                mp.setDataSource(in.getFD());
            } finally {
                in.close();
            }
        } else {
            mp.setDataSource(source);
        }
    }

    @Override
    public void prepareAsync() {
        mp.prepareAsync();
    }

    @Override
    public void start() {
        mp.start();
    }

    @Override
    public void pause() {
        mp.pause();
    }

    @Override
    public boolean isPlaying() {
        return mp.isPlaying();
    }

    @Override
    public void seekTo(int ms) {
        mp.seekTo(ms);
    }

    @Override
    public int getCurrentPosition() {
        return mp.getCurrentPosition();
    }

    @Override
    public int getDuration() {
        return mp.getDuration();
    }

    @Override
    public void setVolume(float volume) {
        mp.setVolume(volume, volume);
    }

    @Override
    public void setEffects(int boostLevel, float[] eq, boolean mono) {
        // MediaPlayer plays the music untouched.
    }

    @Override
    public boolean hasEffects() {
        return false;
    }

    @Override
    public void release() {
        mp.reset();
        mp.release();
    }
}
