package com.grapefruitmusic.playback;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;

import com.grapefruitmusic.data.Db;
import com.grapefruitmusic.download.Downloads;
import com.grapefruitmusic.model.MediaItem;
import com.grapefruitmusic.net.StreamProxy;
import com.grapefruitmusic.source.Sources;
import com.grapefruitmusic.util.Bg;
import com.grapefruitmusic.util.Hashes;
import com.grapefruitmusic.util.Prefs;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Volume Match: plays every song at about the same loudness, like Apple's Sound Check.
 *
 * <p>A song's loudness comes from, in order: Grapefruit's own measurement (songs on the phone,
 * measured in the background, and streamed songs, measured from a saved copy after their first
 * play), then the server (Navidrome ReplayGain tags, Jellyfin 10.9+ normalization). Loud songs
 * are turned down to {@link Loudness#TARGET_LUFS}; Android's player can't turn songs up.
 */
public final class VolumeMatch implements Downloads.Listener {
    private static final String TAG = "VolumeMatch";
    /** Rest between measurements, as a multiple of the time the last one took, so the phone stays responsive. */
    private static final int REST_FACTOR = 2;

    public interface Listener {
        /** Measuring started, progressed or finished (main thread). */
        void onVolumeMatchProgress();
    }

    private static VolumeMatch instance;

    private final Context context;
    /** Song key -> loudness in LUFS; NaN for files that couldn't be measured. */
    private final Map<String, Double> measured = new HashMap<String, Double>();
    private boolean loaded;
    private final ExecutorService scanner = Executors.newSingleThreadExecutor(Bg.named("gf-volume"));
    /** Songs about to play go here, ahead of the background scan. */
    private final ExecutorService priority = Executors.newSingleThreadExecutor(Bg.named("gf-volume-now"));
    private final List<Listener> listeners = new CopyOnWriteArrayList<Listener>();
    private volatile boolean scanning;
    private volatile int scanDone;
    private volatile int scanTotal;
    private File activeCapture;

    private final Runnable startScan = new Runnable() {
        @Override
        public void run() {
            if (!enabled() || scanning) return;
            scanning = true;
            scanner.execute(new Runnable() {
                @Override
                public void run() {
                    scan();
                }
            });
        }
    };

    public static synchronized VolumeMatch get(Context context) {
        if (instance == null) instance = new VolumeMatch(context.getApplicationContext());
        return instance;
    }

    private VolumeMatch(Context context) {
        this.context = context;
        Downloads.get(context).addListener(this);
        // Copies of streams left over from last time.
        File[] old = context.getCacheDir().listFiles();
        if (old != null) {
            for (File f : old) {
                if (f.getName().startsWith("volume-match-")) f.delete();
            }
        }
        if (enabled()) scanSoon(15000);
    }

    public boolean enabled() {
        return Prefs.volumeMatch();
    }

    public void setEnabled(boolean on) {
        Prefs.setVolumeMatch(on);
        if (on) scanSoon(1000);
    }

    public void addListener(Listener l) {
        listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    /** {done, total} while measuring songs on the phone, otherwise null. */
    public int[] progress() {
        return scanning && scanTotal > 0 ? new int[]{scanDone, scanTotal} : null;
    }

    private synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        Cursor c = Db.get(context).getReadableDatabase().query("loudness", new String[]{"song_key", "lufs"},
                null, null, null, null, null);
        try {
            while (c.moveToNext()) measured.put(c.getString(0), c.isNull(1) ? Double.NaN : c.getDouble(1));
        } finally {
            c.close();
        }
    }

    /** The song's loudness in LUFS, or NaN if unknown. */
    public synchronized double loudness(MediaItem item) {
        ensureLoaded();
        Double m = measured.get(item.key());
        if (m != null && !m.isNaN()) return m;
        if (item.replayGain != null) return Loudness.fromReplayGain(item.replayGain);
        return Double.NaN;
    }

    /** The player volume for the song (0 to 1): 1 when Volume Match is off or the loudness is unknown. */
    public float volumeFor(MediaItem item) {
        if (item == null || !enabled()) return 1f;
        return Loudness.volume(Loudness.adjustmentDb(loudness(item)));
    }

    /** Has Grapefruit measured this song (or tried to)? */
    public synchronized boolean isMeasured(MediaItem item) {
        ensureLoaded();
        return measured.containsKey(item.key());
    }

    private void store(String key, double lufs) {
        synchronized (this) {
            ensureLoaded();
            measured.put(key, lufs);
        }
        ContentValues v = new ContentValues();
        v.put("song_key", key);
        if (Double.isNaN(lufs)) v.putNull("lufs");
        else v.put("lufs", lufs);
        v.put("measured_at", System.currentTimeMillis());
        Db.get(context).getWritableDatabase().insertWithOnConflict("loudness", null, v,
                android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE);
    }

    private static double measureFile(String path) {
        long t0 = SystemClock.uptimeMillis();
        double lufs;
        try {
            lufs = AudioMeasure.measure(path);
        } catch (Throwable e) {
            // Whatever went wrong with this file, the next one still gets measured.
            Log.w(TAG, "couldn't measure " + path, e);
            lufs = Double.NaN;
        }
        Log.d(TAG, String.format(java.util.Locale.US, "%s: %.1f LUFS in %d ms", new File(path).getName(), lufs,
                SystemClock.uptimeMillis() - t0));
        return lufs;
    }

    /** Measures a song on the phone right away (it's about to play), then runs done on the main thread. */
    public void measureNow(final MediaItem item, final String path, final Runnable done) {
        priority.execute(new Runnable() {
            @Override
            public void run() {
                if (!isMeasured(item)) store(item.key(), measureFile(path));
                Bg.main(done);
            }
        });
    }

    /**
     * For a song streamed from a server that Grapefruit hasn't measured: where the stream relay
     * should save a copy, measured once the whole song has come in. Null when not needed.
     */
    public StreamProxy.Capture captureFor(final MediaItem item) {
        if (!enabled() || isMeasured(item)) return null;
        final File file = new File(context.getCacheDir(), "volume-match-" + Hashes.md5(item.key()));
        synchronized (this) {
            // One copy at a time: drop the previous song's if it never finished.
            if (activeCapture != null && !activeCapture.equals(file)) activeCapture.delete();
            activeCapture = file;
        }
        file.delete();
        return new StreamProxy.Capture() {
            @Override
            public File file() {
                return file;
            }

            @Override
            public void complete() {
                scanner.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            if (!isMeasured(item)) store(item.key(), measureFile(file.getPath()));
                        } finally {
                            file.delete();
                            synchronized (VolumeMatch.this) {
                                if (file.equals(activeCapture)) activeCapture = null;
                            }
                        }
                    }
                });
            }
        };
    }

    /** Measures songs on the phone (and downloads) that haven't been, after delayMs. */
    public void scanSoon(long delayMs) {
        Bg.cancelMain(startScan);
        Bg.mainDelayed(startScan, delayMs);
    }

    @Override
    public void onDownloadsChanged() {
        // New downloads get measured once things settle.
        if (enabled()) scanSoon(20000);
    }

    private File fileOf(MediaItem item) {
        if (item.path != null && new File(item.path).exists()) return new File(item.path);
        return Downloads.get(context).fileFor(item);
    }

    private void scan() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
        try {
            List<MediaItem> todo = new ArrayList<MediaItem>();
            List<MediaItem> songs = new ArrayList<MediaItem>();
            Sources sources = Sources.get(context);
            try {
                songs.addAll(sources.local().songs());
            } catch (Exception e) {
                Log.w(TAG, "couldn't list songs on the phone", e);
            }
            songs.addAll(sources.downloaded().songs());
            for (MediaItem m : songs) {
                if (!isMeasured(m) && fileOf(m) != null) todo.add(m);
            }
            scanTotal = todo.size();
            scanDone = 0;
            notifyProgress();
            for (MediaItem m : todo) {
                if (!enabled()) break;
                File f = fileOf(m);
                if (f != null && !isMeasured(m)) {
                    long t0 = SystemClock.uptimeMillis();
                    store(m.key(), measureFile(f.getPath()));
                    long took = SystemClock.uptimeMillis() - t0;
                    scanDone++;
                    notifyProgress();
                    try {
                        Thread.sleep(Math.max(500, took * REST_FACTOR));
                    } catch (InterruptedException e) {
                        break;
                    }
                } else {
                    scanDone++;
                }
            }
        } catch (Throwable e) {
            Log.w(TAG, "measuring stopped", e);
        } finally {
            scanning = false;
            scanTotal = 0;
            notifyProgress();
        }
    }

    private void notifyProgress() {
        Bg.main(new Runnable() {
            @Override
            public void run() {
                for (Listener l : listeners) l.onVolumeMatchProgress();
            }
        });
    }
}
