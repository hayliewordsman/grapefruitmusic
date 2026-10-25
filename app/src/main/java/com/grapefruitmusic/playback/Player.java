package com.grapefruitmusic.playback;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.wifi.WifiManager;
import android.os.PowerManager;
import android.util.Log;
import android.widget.Toast;

import com.grapefruitmusic.download.Downloads;
import com.grapefruitmusic.model.MediaItem;
import com.grapefruitmusic.net.StreamProxy;
import com.grapefruitmusic.source.MusicSource;
import com.grapefruitmusic.source.Sources;
import com.grapefruitmusic.source.StreamRequest;
import com.grapefruitmusic.util.Bg;
import com.grapefruitmusic.util.Prefs;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

/** The play queue and the MediaPlayer. Main thread only. */
public final class Player implements AudioManager.OnAudioFocusChangeListener {
    private static final String TAG = "GrapefruitPlayer";

    public static final int REPEAT_OFF = 0;
    public static final int REPEAT_ALL = 1;
    public static final int REPEAT_ONE = 2;

    public interface Listener {
        /** Track, play state, queue or modes changed. */
        void onPlayerChanged();
    }

    private static Player instance;

    private final Context context;
    private final AudioManager audio;
    private final WifiManager.WifiLock wifiLock;
    private final List<Listener> listeners = new CopyOnWriteArrayList<Listener>();

    /** Tracks in the order they were given. */
    private final List<MediaItem> queue = new ArrayList<MediaItem>();
    /** Play order: indexes into queue (shuffled or not). */
    private final List<Integer> order = new ArrayList<Integer>();
    private int position = -1;
    private boolean shuffle;
    private int repeat = REPEAT_OFF;

    private MediaPlayer mp;
    private boolean prepared;
    private boolean playWhenReady;
    private boolean buffering;
    private boolean pausedByFocusLoss;
    private int consecutiveErrors;

    public static synchronized Player get(Context context) {
        if (instance == null) instance = new Player(context.getApplicationContext());
        return instance;
    }

    private Player(Context context) {
        this.context = context;
        audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        WifiManager wifi = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        wifiLock = wifi.createWifiLock(WifiManager.WIFI_MODE_FULL, "grapefruit-stream");
        wifiLock.setReferenceCounted(false);
    }

    public void addListener(Listener l) {
        listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    private void changed() {
        for (Listener l : listeners) l.onPlayerChanged();
    }

    // ---- Queue ----

    /**
     * Replaces the queue and starts playing items[start]. With shuffle, items[start] plays
     * first and the rest follow in random order; pass start = -1 to shuffle everything.
     */
    public void play(List<MediaItem> items, int start, boolean shuffled) {
        if (items.isEmpty()) return;
        queue.clear();
        queue.addAll(items);
        shuffle = shuffled;
        int first = Math.max(-1, Math.min(start, items.size() - 1));
        buildOrder(first);
        position = shuffled ? 0 : Math.max(0, first);
        consecutiveErrors = 0;
        load(true);
    }

    /** Shuffle: random order (with `first` at the front when >= 0). Otherwise natural order. */
    private void buildOrder(int first) {
        order.clear();
        for (int i = 0; i < queue.size(); i++) order.add(i);
        if (shuffle) {
            Collections.shuffle(order, new Random());
            if (first >= 0) {
                order.remove(Integer.valueOf(first));
                order.add(0, first);
            }
        }
    }

    public void playNext(MediaItem item) {
        if (current() == null) {
            play(Collections.singletonList(item), 0, false);
            return;
        }
        queue.add(item);
        order.add(position + 1, queue.size() - 1);
        changed();
    }

    public void addToQueue(MediaItem item) {
        if (current() == null) {
            play(Collections.singletonList(item), 0, false);
            return;
        }
        queue.add(item);
        order.add(queue.size() - 1);
        changed();
    }

    /** What plays after the current track, in order. */
    public List<MediaItem> upNext() {
        List<MediaItem> out = new ArrayList<MediaItem>();
        for (int i = position + 1; i < order.size(); i++) out.add(queue.get(order.get(i)));
        return out;
    }

    /** Jump to the n-th item of upNext(). */
    public void skipToUpNext(int n) {
        int target = position + 1 + n;
        if (target < order.size()) {
            position = target;
            load(true);
        }
    }

    public MediaItem current() {
        return position >= 0 && position < order.size() ? queue.get(order.get(position)) : null;
    }

    public boolean isShuffle() {
        return shuffle;
    }

    public void setShuffle(boolean on) {
        if (on == shuffle || queue.isEmpty()) {
            shuffle = on;
            changed();
            return;
        }
        int cur = order.get(position);
        shuffle = on;
        buildOrder(cur);
        position = order.indexOf(cur);
        changed();
    }

    public int repeatMode() {
        return repeat;
    }

    public void cycleRepeat() {
        repeat = (repeat + 1) % 3;
        changed();
    }

    // ---- Transport ----

    public boolean isPlaying() {
        return mp != null && prepared && mp.isPlaying();
    }

    /** True while waiting for a stream to start. */
    public boolean isBuffering() {
        return buffering;
    }

    /** "Playing" from the user's point of view, including while buffering. */
    public boolean wantsToPlay() {
        return playWhenReady;
    }

    public long positionMs() {
        return mp != null && prepared ? mp.getCurrentPosition() : 0;
    }

    public long durationMs() {
        if (mp != null && prepared) {
            int d = mp.getDuration();
            if (d > 0) return d;
        }
        MediaItem c = current();
        return c != null ? c.durationMs : 0;
    }

    public void togglePlay() {
        if (playWhenReady) pause();
        else resume();
    }

    public void resume() {
        if (current() == null) return;
        if (mp == null) {
            load(true);
            return;
        }
        if (!requestFocus()) return;
        playWhenReady = true;
        if (prepared) mp.start();
        startService();
        changed();
    }

    public void pause() {
        playWhenReady = false;
        pausedByFocusLoss = false;
        if (mp != null && prepared && mp.isPlaying()) mp.pause();
        if (wifiLock.isHeld()) wifiLock.release();
        changed();
    }

    public void next() {
        if (order.isEmpty()) return;
        if (position + 1 < order.size()) {
            position++;
        } else if (repeat == REPEAT_ALL) {
            position = 0;
        } else {
            return;
        }
        load(playWhenReady || mp == null);
    }

    /** Restarts the track if more than 3 seconds in, otherwise goes back one. */
    public void previous() {
        if (order.isEmpty()) return;
        if (positionMs() > 3000 || position == 0) {
            seekTo(0);
            return;
        }
        position--;
        load(playWhenReady);
    }

    public void seekTo(long ms) {
        if (mp != null && prepared) {
            mp.seekTo((int) ms);
            changed();
        }
    }

    /** Stops playback and clears the queue. */
    public void stop() {
        release();
        queue.clear();
        order.clear();
        position = -1;
        playWhenReady = false;
        audio.abandonAudioFocus(this);
        changed();
    }

    // ---- MediaPlayer ----

    private void release() {
        if (mp != null) {
            mp.reset();
            mp.release();
            mp = null;
        }
        prepared = false;
        buffering = false;
        if (wifiLock.isHeld()) wifiLock.release();
    }

    private void load(boolean play) {
        release();
        final MediaItem item = current();
        if (item == null) {
            changed();
            return;
        }
        playWhenReady = play && requestFocus();
        mp = new MediaPlayer();
        mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
        mp.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK);
        final MediaPlayer player = mp;
        mp.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer m) {
                if (m != mp) return;
                prepared = true;
                buffering = false;
                consecutiveErrors = 0;
                if (playWhenReady) m.start();
                changed();
            }
        });
        mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer m) {
                if (m != mp) return;
                if (repeat == REPEAT_ONE) {
                    m.seekTo(0);
                    m.start();
                } else if (position + 1 < order.size() || repeat == REPEAT_ALL) {
                    next();
                } else {
                    playWhenReady = false;
                    m.seekTo(0);
                    changed();
                }
            }
        });
        mp.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer m, int what, int extra) {
                if (m != mp) return true;
                Log.w(TAG, "playback error " + what + "/" + extra + " for " + item.title);
                failed(item, "Couldn't play “" + item.title + "”");
                return true;
            }
        });
        try {
            File file = localFile(item);
            if (file != null) {
                FileInputStream in = new FileInputStream(file);
                try {
                    // A file descriptor works for files in app-private storage too.
                    player.setDataSource(in.getFD());
                } finally {
                    in.close();
                }
            } else {
                MusicSource source = Sources.get(context).byId(item.sourceId);
                StreamRequest req = source != null ? source.stream(item, Prefs.streamQuality()) : null;
                if (req == null) throw new IOException("This song is no longer available");
                player.setDataSource(StreamProxy.get().register(req.url, req.headers));
                wifiLock.acquire();
                buffering = true;
            }
            player.prepareAsync();
            if (playWhenReady) startService();
        } catch (IOException e) {
            failed(item, e.getMessage());
            return;
        } catch (RuntimeException e) {
            failed(item, "Couldn't play “" + item.title + "”");
            return;
        }
        changed();
    }

    private File localFile(MediaItem item) {
        if (item.path != null) {
            File f = new File(item.path);
            if (f.exists()) return f;
        }
        return Downloads.get(context).fileFor(item);
    }

    private void failed(MediaItem item, String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        boolean wasPlaying = playWhenReady;
        release();
        consecutiveErrors++;
        if (consecutiveErrors < Math.min(5, order.size()) && position + 1 < order.size()) {
            position++;
            final boolean play = wasPlaying;
            Bg.mainDelayed(new Runnable() {
                @Override
                public void run() {
                    load(play);
                }
            }, 500);
        } else {
            playWhenReady = false;
        }
        changed();
    }

    private boolean requestFocus() {
        return audio.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
                == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    @Override
    public void onAudioFocusChange(int change) {
        switch (change) {
            case AudioManager.AUDIOFOCUS_LOSS:
                pause();
                audio.abandonAudioFocus(this);
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                if (playWhenReady) {
                    pause();
                    pausedByFocusLoss = true;
                }
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                if (mp != null) mp.setVolume(0.2f, 0.2f);
                break;
            case AudioManager.AUDIOFOCUS_GAIN:
                if (mp != null) mp.setVolume(1f, 1f);
                if (pausedByFocusLoss) {
                    pausedByFocusLoss = false;
                    resume();
                }
                break;
            default:
                break;
        }
    }

    private void startService() {
        context.startService(new Intent(context, PlaybackService.class));
    }
}
