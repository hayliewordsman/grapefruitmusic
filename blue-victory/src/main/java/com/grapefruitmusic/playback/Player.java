package com.grapefruitmusic.playback;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.net.wifi.WifiManager;
import android.util.Log;

import com.grapefruitmusic.download.Downloads;
import com.grapefruitmusic.model.MediaItem;
import com.grapefruitmusic.net.StreamProxy;
import com.grapefruitmusic.source.MusicSource;
import com.grapefruitmusic.source.Sources;
import com.grapefruitmusic.source.StreamRequest;
import com.grapefruitmusic.util.Bg;
import com.grapefruitmusic.util.Lists;
import com.grapefruitmusic.util.Notice;
import com.grapefruitmusic.util.Prefs;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

/** The play queue and the player engine. Main thread only. */
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

    private final Runnable podcastCheckpoint = new Runnable() {
        @Override public void run() {
            savePodcastPosition();
            MediaItem m = current();
            if (playWhenReady && keepsPlace(m)) Bg.mainDelayed(this, 15000);
        }
    };

    /** Podcast episodes and audiobooks: where they were left is kept, every 15 seconds and on pause. */
    private static boolean keepsPlace(MediaItem m) {
        return m != null && !m.live && (isAudiobook(m)
                || com.grapefruitmusic.podcast.PodcastSource.ID.equals(m.sourceId));
    }
    private Engine engine;
    private boolean prepared;
    private boolean playWhenReady;
    private boolean buffering;
    private boolean pausedByFocusLoss;
    private int consecutiveErrors;

    /** Songs that actually started playing, oldest first (shown as Recently Played). */
    private final PlayHistory history = new PlayHistory(50);
    private final File historyFile;
    /** The song in the MediaPlayer, and whether it started playing. */
    private MediaItem loadedItem;
    private boolean loadedItemStarted;

    // Volume Match: this song's volume (1 = as recorded), and whether it's still being measured.
    private final VolumeMatch volumeMatch;
    private float matchVolume = 1f;
    private boolean measuring;
    /** Turned down while another app talks over the music. */
    private boolean ducked;

    /** Where to start the loading song (after switching engines mid-song), in ms. */
    private long startAtMs;
    /** A song Grapefruit's own engine couldn't play; Android's plays it, without effects. */
    private String effectsFailedKey;

    // A live station: what it says is playing, and how often it's dropped out lately.
    private String liveTitle;
    private int liveDrops;
    private long liveSince;

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
        historyFile = new File(context.getFilesDir(), "history.ser");
        loadHistory();
        volumeMatch = VolumeMatch.get(context);
    }

    // ---- Volume Match ----

    /** The current song's Volume Match volume, 0 to 1 (1 = unchanged). */
    public float matchVolume() {
        return matchVolume;
    }

    /** Volume Match was switched on or off: applies it to the song that's playing. */
    public void refreshVolumeMatch() {
        matchVolume = volumeMatch.volumeFor(current());
        applyVolume();
    }

    private void applyVolume() {
        if (engine == null) return;
        engine.setVolume(matchVolume * (ducked ? 0.2f : 1f) * fade);
    }

    /** The sleep timer's fade-out: 1 at full volume, down to 0 as it goes off. */
    private float fade = 1f;

    void setFade(float f) {
        f = Math.max(0f, Math.min(1f, f));
        if (f == fade) return;
        fade = f;
        applyVolume();
    }

    /** Starts the prepared song once nothing's holding it back (it may still be measured). */
    private void startIfReady() {
        if (engine == null || !prepared || !playWhenReady || measuring || engine.isPlaying()) return;
        engine.start();
        loadedItemStarted = true;
        changed();
    }

    // ---- Karaoke Mode, Volume Boost, Equalizer and Mono Audio ----

    /** Is the song playing through Grapefruit's own engine (with effects)? */
    public boolean effectsActive() {
        return engine != null && engine.hasEffects();
    }

    /**
     * Karaoke Mode, Volume Boost, the Equalizer or Mono Audio changed: applies it to the song that's playing, switching
     * engines (and carrying on from the same spot) when effects go on or off.
     */
    public void audioEffectsChanged() {
        MediaItem item = current();
        if (engine == null || item == null) return;
        engine.setEffects(effectsFor(item));
        boolean want = wantsEffects(item);
        if (want != engine.hasEffects()) load(playWhenReady, item.live ? 0 : positionMs());
    }

    /** Grapefruit's own engine: for effects, and for any speed but 1 (Android 4's can't change it). */
    private boolean wantsEffects(MediaItem item) {
        return (effectsFor(item).anyOn() || speedFor(item) != 1f) && !item.key().equals(effectsFailedKey);
    }

    // ---- Speed ----

    /** The speeds offered (Now Playing's Speed button). */
    public static final float[] SPEEDS = {0.8f, 1f, 1.2f, 1.5f, 1.75f, 2f};

    /** Which speed an item plays at (Prefs.SPEED_...), or -1 for none: radio and live streams. */
    public static int speedKind(MediaItem item) {
        if (item == null || item.live) return -1;
        if (isAudiobook(item)) return Prefs.SPEED_AUDIOBOOKS;
        return isPodcast(item) ? Prefs.SPEED_PODCASTS : Prefs.SPEED_MUSIC;
    }

    public static float speedFor(MediaItem item) {
        int kind = speedKind(item);
        return kind < 0 ? 1f : Prefs.speed(kind);
    }

    /** The speed of what's playing (1 for the radio). */
    public float speed() {
        return speedFor(current());
    }

    /**
     * Plays what's playing, and the rest of its kind (music, podcasts or audiobooks), at this speed:
     * switching engines at the same spot when it goes to or from 1.
     */
    public void setSpeed(float speed) {
        MediaItem item = current();
        int kind = speedKind(item);
        if (kind < 0) return;
        Prefs.setSpeed(kind, speed);
        if (engine != null) {
            engine.setSpeed(speedFor(item));
            if (wantsEffects(item) != engine.hasEffects()) load(playWhenReady, positionMs());
        }
        changed();
    }

    /** Is this part of an audiobook? */
    public static boolean isAudiobook(MediaItem item) {
        return item != null && com.grapefruitmusic.audiobooks.AudiobookSource.ID.equals(item.sourceId);
    }

    /** Is this a podcast episode? */
    public static boolean isPodcast(MediaItem item) {
        return item != null && com.grapefruitmusic.podcast.PodcastSource.ID.equals(item.sourceId);
    }

    /**
     * The sound settings for this item. Karaoke Mode and Live Mode are for music: a podcast plays
     * without them (Volume Boost, the Equalizer and Mono Audio still apply).
     */
    public static Effects effectsFor(MediaItem item) {
        Effects e = effects();
        if (!(isPodcast(item) || isAudiobook(item)) || (!e.karaoke && !e.live)) return e;
        return new Effects(e.boost, e.eq, e.mono, false, e.karaokeAmount, false, e.liveAmount);
    }

    /** The effects that are on, in a few words (for a crash report), or "". */
    private static String describe(Effects e) {
        if (e == null) return "";
        StringBuilder b = new StringBuilder();
        if (e.boost > Dsp.BOOST_OFF) b.append(", Volume Boost");
        if (Equalizer.isOn(e.eq)) b.append(", EQ");
        if (e.mono) b.append(", Mono Audio");
        if (e.karaoke) b.append(", Karaoke");
        if (e.live) b.append(", Live");
        return b.length() == 0 ? "" : " (" + b.substring(2) + ")";
    }

    /**
     * Gets Karaoke Mode's model ready in the background (read, and run once so Android has compiled
     * it), so turning Karaoke Mode on starts it straight away. Any thread.
     */
    public static void prepareKaraoke(final Context context) {
        Bg.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    DspEngine.karaokeModel(context.getApplicationContext());
                } catch (IOException e) {
                    Log.w(TAG, "karaoke model", e);
                }
            }
        });
    }

    /** The sound settings, for Grapefruit's own engine. */
    public static Effects effects() {
        return new Effects(Prefs.volumeBoost(), eqCurve(), Prefs.monoAudio(), Prefs.karaoke(), Prefs.karaokeAmount(), Prefs.liveMode(), Prefs.liveAmount());
    }

    /** The equalizer curve chosen in Settings: a preset's, or the Custom one. */
    public static float[] eqCurve() {
        int preset = Prefs.equalizer();
        if (preset != Equalizer.CUSTOM) return Equalizer.curve(preset);
        float[] custom = Prefs.customEq();
        return custom != null && custom.length == Equalizer.BANDS ? custom : new float[Equalizer.BANDS];
    }

    // ---- Live radio ----

    /** Is the current item a live station (no length, no seeking; stop rather than pause)? */
    public boolean isLive() {
        MediaItem c = current();
        return c != null && c.live;
    }

    /** What the live station playing says is on ("Artist - Song"), or null. */
    public String liveTitle() {
        return isLive() ? liveTitle : null;
    }

    /** Songs' tags (or pictures) were changed: the queue and Recently Played show the new ones. */
    public void songsEdited() {
        com.grapefruitmusic.tags.SongEdits.apply(queue);
        com.grapefruitmusic.tags.SongEdits.apply(history.items());
        changed();
    }

    // ---- Recently played ----

    public List<MediaItem> history() {
        return history.items();
    }

    /** Records a song as played (normally done when playback moves on from it). */
    public void recordPlayed(MediaItem item) {
        history.add(item);
        saveHistory();
        changed();
    }

    public void clearHistory() {
        history.clear();
        saveHistory();
        changed();
    }

    /** Plays item right away, keeping the rest of Up Next. */
    public void playNow(MediaItem item) {
        if (current() == null) {
            play(Collections.singletonList(item), 0, false);
            return;
        }
        queue.add(item);
        order.add(position + 1, queue.size() - 1);
        position++;
        consecutiveErrors = 0;
        load(true);
    }

    @SuppressWarnings("unchecked")
    private void loadHistory() {
        if (!historyFile.exists()) return;
        try {
            ObjectInputStream in = new ObjectInputStream(new FileInputStream(historyFile));
            try {
                history.setAll((List<MediaItem>) in.readObject());
            } finally {
                in.close();
            }
        } catch (Exception e) {
            // Unreadable (e.g. from an incompatible version): start a new history.
            historyFile.delete();
        }
    }

    private void saveHistory() {
        final ArrayList<MediaItem> snapshot = new ArrayList<MediaItem>(history.items());
        Bg.execute(new Runnable() {
            @Override
            public void run() {
                synchronized (historyFile) {
                    try {
                        ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(historyFile));
                        try {
                            out.writeObject(snapshot);
                        } finally {
                            out.close();
                        }
                    } catch (IOException e) {
                        Log.w(TAG, "couldn't save history", e);
                    }
                }
            }
        });
    }

    public void addListener(Listener l) {
        listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    private void changed() {
        Bg.cancelMain(podcastCheckpoint);
        MediaItem m = current();
        if (playWhenReady && keepsPlace(m)) Bg.mainDelayed(podcastCheckpoint, 15000);
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

    /** Reorders Up Next: the item at index from of upNext() moves to index to. */
    public void moveUpNext(int from, int to) {
        int a = position + 1 + from;
        int b = position + 1 + to;
        if (from == to || from < 0 || to < 0 || a >= order.size() || b >= order.size()) return;
        Lists.move(order, a, b);
        changed();
    }

    /** Plays the queue's index-th item (in its order) from ms: an audiobook's chapter in another part. */
    public void jumpTo(int index, long ms) {
        if (index < 0 || index >= order.size()) return;
        savePodcastPosition();
        position = index;
        consecutiveErrors = 0;
        load(true, ms);
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
        return engine != null && prepared && engine.isPlaying();
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
        return engine != null && prepared ? engine.getCurrentPosition() : startAtMs;
    }

    /** Where the song playing is in the queue, from 1 (0 if nothing is). */
    public int queueNumber() {
        return current() != null ? position + 1 : 0;
    }

    /** How many songs the queue holds. */
    public int queueLength() {
        return order.size();
    }

    /** Jumps ms on (or back, if negative) within the song: a podcast's 15 seconds. */
    public void skipBy(long ms) {
        long dur = durationMs();
        long to = Math.max(0, positionMs() + ms);
        if (dur > 0) to = Math.min(to, Math.max(0, dur - 1000));
        seekTo(to);
    }

    public long durationMs() {
        if (engine != null && prepared) {
            int d = engine.getDuration();
            // A length estimated from the file (a podcast without an index, say) can be short: the
            // time must not stop at the end while the episode plays on.
            if (d > 0) return Math.max(d, engine.getCurrentPosition());
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
        if (engine == null) {
            load(true);
            return;
        }
        if (!requestFocus()) return;
        playWhenReady = true;
        if (prepared && !measuring) {
            engine.start();
            loadedItemStarted = true;
        }
        startService();
        changed();
    }

    public void pause() {
        savePodcastPosition();
        playWhenReady = false;
        pausedByFocusLoss = false;
        if (isLive()) {
            // A paused stream falls behind and gets cut off: stop, and tune in afresh to resume.
            // What was on is no longer known.
            release();
            liveTitle = null;
        } else if (engine != null && prepared && engine.isPlaying()) {
            engine.pause();
        }
        if (wifiLock.isHeld()) wifiLock.release();
        changed();
    }

    public void next() {
        if (order.isEmpty()) return;
        if (position + 1 < order.size()) {
            position++;
        } else if (repeatFor(current()) == REPEAT_ALL) {
            position = 0;
        } else {
            return;
        }
        load(playWhenReady || engine == null);
    }

    /** The repeat mode for this item: an audiobook plays its parts once, in order, whatever it is. */
    private int repeatFor(MediaItem item) {
        return item != null && isAudiobook(item) ? REPEAT_OFF : repeat;
    }

    /** Restarts the track if more than 3 seconds in, otherwise goes back one. */
    public void previous() {
        if (order.isEmpty()) return;
        if (isLive() && position > 0) {
            position--;
            load(playWhenReady);
            return;
        }
        if (positionMs() > 3000 || position == 0) {
            seekTo(0);
            return;
        }
        position--;
        load(playWhenReady);
    }

    public void seekTo(long ms) {
        if (engine != null && prepared && !isLive()) {
            engine.seekTo((int) Math.max(0, Math.min(ms, durationMs() > 0
                    ? Math.min(durationMs(), Integer.MAX_VALUE) : Integer.MAX_VALUE)));
            changed();
        }
    }

    /** Stops playback and clears the queue. */
    public void stop() {
        if (loadedItem != null && loadedItemStarted) {
            history.add(loadedItem);
            saveHistory();
        }
        loadedItem = null;
        loadedItemStarted = false;
        liveTitle = null;
        release();
        queue.clear();
        order.clear();
        position = -1;
        playWhenReady = false;
        audio.abandonAudioFocus(this);
        changed();
    }

    // ---- Engine ----

    private void savePodcastPosition() {
        MediaItem m = loadedItem;
        if (isAudiobook(m) && engine != null && prepared) {
            com.grapefruitmusic.audiobooks.AudiobookStore.get(context)
                    .savePosition(m.albumId, m.trackNumber - 1, engine.getCurrentPosition());
            return;
        }
        if (m != null && com.grapefruitmusic.podcast.PodcastSource.ID.equals(m.sourceId) && engine != null && prepared) {
            long position = engine.getCurrentPosition(), duration = engine.getDuration();
            if (duration > 0 && position >= duration - 1500) position = 0;
            // Its own small file, not the settings: see PodcastPositions (the stutter in 4.1.1).
            com.grapefruitmusic.podcast.PodcastPositions.get(context).set(m.id, position);
        }
    }

    private void release() {
        savePodcastPosition();
        if (engine != null) {
            engine.release();
            engine = null;
        }
        prepared = false;
        buffering = false;
        if (wifiLock.isHeld()) wifiLock.release();
    }

    private void load(boolean play) {
        MediaItem m = current();
        savePodcastPosition();
        long at = 0;
        if (isAudiobook(m)) at = com.grapefruitmusic.audiobooks.AudiobookStore.get(context).positionFor(m);
        else if (m != null && com.grapefruitmusic.podcast.PodcastSource.ID.equals(m.sourceId))
            at = com.grapefruitmusic.podcast.PodcastPositions.get(context).get(m.id);
        load(play, at);
    }

    /** Loads the current song, starting at startAt ms. */
    private void load(boolean play, long startAt) {
        release();
        startAtMs = startAt;
        final MediaItem item = current();
        if (loadedItem != null && loadedItemStarted && loadedItem != item) {
            history.add(loadedItem);
            saveHistory();
        }
        if (item != loadedItem) {
            liveTitle = null;
            liveDrops = 0;
            // Its lyrics, ready (and saved for offline) before Lyrics is opened.
            com.grapefruitmusic.lyrics.LyricsRepository.prefetch(context, item);
        }
        loadedItem = item;
        loadedItemStarted = false;
        if (item == null) {
            changed();
            return;
        }
        playWhenReady = play && requestFocus();
        engine = wantsEffects(item) ? new DspEngine(context) : new MediaPlayerEngine(context);
        engine.setEffects(effectsFor(item));
        engine.setSpeed(speedFor(item));
        final Engine player = engine;
        matchVolume = volumeMatch.volumeFor(item);
        measuring = false;
        applyVolume();
        engine.setCallback(new Engine.Callback() {
            @Override
            public void onPrepared(Engine e) {
                if (e != engine) return;
                prepared = true;
                buffering = false;
                consecutiveErrors = 0;
                liveSince = android.os.SystemClock.elapsedRealtime();
                if (startAtMs > 0) e.seekTo((int) startAtMs);
                startAtMs = 0;
                if (playWhenReady && !measuring) {
                    e.start();
                    loadedItemStarted = true;
                }
                changed();
            }

            @Override
            public void onCompletion(Engine e) {
                if (e != engine) return;
                if (item.live) {
                    reconnect(item);
                    return;
                }
                if (com.grapefruitmusic.podcast.PodcastSource.ID.equals(item.sourceId))
                    com.grapefruitmusic.podcast.PodcastPositions.get(context).clear(item.id);
                if (isAudiobook(item) && position + 1 >= order.size()) {
                    // The book's end: finished, and next time from the start.
                    com.grapefruitmusic.audiobooks.AudiobookStore.get(context).setFinished(item.albumId, true);
                }
                if (SleepTimer.get(context).stopsAfter(item)) {
                    // The sleep timer, set for the end of this: the next one ready, but not playing.
                    playWhenReady = false;
                    if (position + 1 < order.size()) {
                        next();
                    } else {
                        e.seekTo(0);
                        changed();
                    }
                    return;
                }
                int repeating = repeatFor(item);
                if (repeating == REPEAT_ONE) {
                    e.seekTo(0);
                    e.start();
                } else if (position + 1 < order.size() || repeating == REPEAT_ALL) {
                    next();
                } else {
                    playWhenReady = false;
                    e.seekTo(0);
                    changed();
                }
            }

            @Override
            public void onError(Engine e, String what) {
                if (e != engine) return;
                Log.w(TAG, "playback error (" + what + ") for " + item.title);
                if (item.live && prepared) {
                    // It was playing: the station dropped out, rather than can't be played.
                    reconnect(item);
                    return;
                }
                if (e.hasEffects()) {
                    // Grapefruit's engine can't play this one: Android's plays it, without effects.
                    effectsFailedKey = item.key();
                    load(playWhenReady, prepared ? e.getCurrentPosition() : startAtMs);
                    return;
                }
                failed(item, "Couldn't play “" + item.title + "”");
            }

        });
        try {
            File file = localFile(item);
            if (file != null && volumeMatch.enabled() && !volumeMatch.isMeasured(item)) {
                // Not measured yet: measure it while it loads, so it starts at the right volume.
                measuring = true;
                Runnable measured = new Runnable() {
                    @Override
                    public void run() {
                        if (engine != player || !measuring) return;
                        measuring = false;
                        matchVolume = volumeMatch.volumeFor(item);
                        applyVolume();
                        startIfReady();
                    }
                };
                volumeMatch.measureNow(item, file.getPath(), measured);
                // Don't keep the listener waiting on a slow measurement.
                Bg.mainDelayed(measured, 2500);
            }
            boolean podcast = com.grapefruitmusic.podcast.PodcastSource.ID.equals(item.sourceId);
            com.grapefruitmusic.util.CrashReport.note("load " + (item.live ? "station" : podcast ? "episode" : "song")
                    + (file != null ? " (file)" : " (stream)") + " with " + player.getClass().getSimpleName()
                    + describe(player.hasEffects() ? effectsFor(item) : null) + ", from " + startAtMs / 1000 + " s");
            if (file != null) {
                player.setDataSource(file.getPath());
                if (podcast) measureLength(player, file);
            } else {
                MusicSource source = Sources.get(context).byId(item.sourceId);
                StreamRequest req = source != null ? source.stream(item, Prefs.streamQuality()) : null;
                if (req == null) throw new IOException("This song is no longer available");
                if (item.live) {
                    player.setDataSource(StreamProxy.get().registerLive(req.url, req.headers, titlesFor(player)));
                } else if (podcast) {
                    player.setDataSource(StreamProxy.get().registerPodcast(req.url, req.headers,
                            volumeMatch.captureFor(item), lengthFor(player)));
                } else {
                    player.setDataSource(StreamProxy.get().register(req.url, req.headers, volumeMatch.captureFor(item)));
                }
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

    /** Lengths of podcast files measured this run, by path, size and date. */
    private static final Map<String, Long> LENGTHS = new HashMap<String, Long>();

    /**
     * A downloaded episode's real length, from every frame of it (in the background, once): the
     * length Android reads from an MP3's header leaves out ads put in when it was downloaded, and
     * the time ran out minutes before the episode did.
     */
    private void measureLength(final Engine player, final File file) {
        final String key = file.getPath() + "|" + file.length() + "|" + file.lastModified();
        Long known;
        synchronized (LENGTHS) {
            known = LENGTHS.get(key);
        }
        if (known != null) {
            if (known > 0) player.setLength(known);
            return;
        }
        Bg.run(new Bg.Work<Long>() {
            @Override
            public Long run() throws Exception {
                // Reading the whole file: at low priority, so it never holds up playback.
                int tid = android.os.Process.myTid();
                int was = android.os.Process.getThreadPriority(tid);
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
                try {
                    return Mp3Length.ofFile(file);
                } finally {
                    android.os.Process.setThreadPriority(was);
                }
            }
        }, new Bg.Done<Long>() {
            @Override
            public void done(Long ms, Exception error) {
                if (ms == null) return;
                synchronized (LENGTHS) {
                    LENGTHS.put(key, ms);
                }
                if (ms > 0 && engine == player) {
                    player.setLength(ms);
                    changed();
                }
            }
        });
    }

    /** A streamed episode's real length, as the relay works it out from the start of the stream. */
    private StreamProxy.LengthListener lengthFor(final Engine player) {
        return new StreamProxy.LengthListener() {
            @Override
            public void onLength(final long ms) {
                Bg.main(new Runnable() {
                    @Override
                    public void run() {
                        if (engine != player) return;
                        player.setLength(ms);
                        changed();
                    }
                });
            }
        };
    }

    /** Shows what the station says is on, while this engine is the one playing. */
    private StreamProxy.TitleListener titlesFor(final Engine player) {
        return new StreamProxy.TitleListener() {
            @Override
            public void onTitle(final String title) {
                Bg.main(new Runnable() {
                    @Override
                    public void run() {
                        if (engine != player) return;
                        if (title == null ? liveTitle == null : title.equals(liveTitle)) return;
                        liveTitle = title;
                        changed();
                    }
                });
            }
        };
    }

    /** A live station's stream ended (the connection dropped): tune in again, a few times. */
    private void reconnect(final MediaItem item) {
        // A good long listen since the last drop starts the count again.
        if (android.os.SystemClock.elapsedRealtime() - liveSince > 60000) liveDrops = 0;
        if (++liveDrops > 3) {
            failed(item, "\u201c" + item.title + "\u201d stopped broadcasting");
            return;
        }
        final boolean play = playWhenReady;
        release();
        buffering = true;
        changed();
        Bg.mainDelayed(new Runnable() {
            @Override
            public void run() {
                if (current() == item && engine == null) load(play);
            }
        }, 1000);
    }

    private File localFile(MediaItem item) throws IOException {
        if (com.grapefruitmusic.source.BundledSource.owns(item)) {
            MusicSource source = Sources.get(context).byId(item.sourceId);
            if (source instanceof com.grapefruitmusic.source.BundledSource)
                return ((com.grapefruitmusic.source.BundledSource) source).fileFor(item);
            throw new IOException("This Bonus Bundled Content requires Blue Grapefruit");
        }
        if (com.grapefruitmusic.podcast.PodcastSource.ID.equals(item.sourceId))
            return com.grapefruitmusic.podcast.PodcastStore.get(context).localFile(item);
        if (item.path != null) {
            File f = new File(item.path);
            if (f.exists()) return f;
        }
        return Downloads.get(context).fileFor(item);
    }

    private void failed(MediaItem item, String message) {
        Notice.show(context, message);
        boolean wasPlaying = playWhenReady;
        release();
        consecutiveErrors++;
        // A station that won't play isn't skipped for another one: the listener picks.
        if (!item.live && consecutiveErrors < Math.min(5, order.size()) && position + 1 < order.size()) {
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
        PlaybackSwitch.audioRequested();
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
                ducked = true;
                applyVolume();
                break;
            case AudioManager.AUDIOFOCUS_GAIN:
                ducked = false;
                applyVolume();
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
        com.grapefruitmusic.compat.Platform.service(context, new Intent(context, PlaybackService.class));
    }
}
