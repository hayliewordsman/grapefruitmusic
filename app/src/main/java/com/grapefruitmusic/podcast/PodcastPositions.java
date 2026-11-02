package com.grapefruitmusic.podcast;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Process;
import android.util.Log;

import com.grapefruitmusic.util.Prefs;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Where each podcast episode was left, saved every few seconds while one plays.
 *
 * <p>Until 4.1.2 these went into the app's settings (SharedPreferences), and every save rewrote
 * the whole settings file and forced it to disk (fsync). With the screen off, older Android
 * tablets hold back writing to storage, so each forced write had a lot to flush, and reading the
 * episode waited behind it: playback stuttered every 15 seconds with the screen locked. Now they
 * have a small file of their own, written on a low-priority thread and left for Android to write
 * out in its own time (no fsync). A position lost to a sudden power cut costs a few seconds of
 * listening; the stutter cost every listen.
 */
public final class PodcastPositions {
    private static final String TAG = "GrapefruitPositions";
    private static final String FILE = "podcast-positions.txt";
    private static final String OLD_KEY = "podcast_position_";
    /** Episodes remembered, most recently saved last. */
    static final int KEEP = 500;

    private static PodcastPositions instance;

    private final File file;
    private final LinkedHashMap<String, Long> positions = new LinkedHashMap<String, Long>();
    private final ExecutorService writer = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "gf-positions");
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        }
    });
    private boolean writeQueued;

    public static synchronized PodcastPositions get(Context context) {
        if (instance == null) instance = new PodcastPositions(context.getApplicationContext());
        return instance;
    }

    private PodcastPositions(Context context) {
        file = new File(context.getFilesDir(), FILE);
        try {
            FileInputStream in = new FileInputStream(file);
            try {
                parse(new BufferedReader(new InputStreamReader(in, "UTF-8")), positions);
            } finally {
                in.close();
            }
        } catch (IOException e) {
            // None saved yet.
        }
        migrate();
    }

    /** Moves positions saved by 4.1.1 and earlier out of the settings file, once. */
    private void migrate() {
        SharedPreferences prefs = Prefs.raw();
        if (prefs == null) return;
        List<String> old = new ArrayList<String>();
        for (Map.Entry<String, ?> e : prefs.getAll().entrySet()) {
            if (!e.getKey().startsWith(OLD_KEY)) continue;
            old.add(e.getKey());
            String id = e.getKey().substring(OLD_KEY.length());
            if (!positions.containsKey(id) && e.getValue() instanceof Long && (Long) e.getValue() > 0) {
                positions.put(id, (Long) e.getValue());
            }
        }
        if (old.isEmpty()) return;
        SharedPreferences.Editor edit = prefs.edit();
        for (String key : old) edit.remove(key);
        edit.apply();
        save();
    }

    /** Where the episode was left, in ms (0: from the start). */
    public synchronized long get(String id) {
        Long ms = positions.get(id);
        return ms != null ? ms : 0;
    }

    /** Remembers where the episode is (0 or less: from the start next time). */
    public void set(String id, long ms) {
        synchronized (this) {
            Long was = positions.get(id);
            if (ms <= 0) {
                if (was == null) return;
                positions.remove(id);
            } else {
                // Unchanged (paused, say): nothing to write.
                if (was != null && Math.abs(was - ms) < 1000) return;
                positions.remove(id);
                positions.put(id, ms);
                prune(positions, KEEP);
            }
        }
        save();
    }

    /** Forgets where the episode was (played to the end, or marked unplayed). */
    public void clear(String id) {
        set(id, 0);
    }

    /** One write at a time; a save asked for while one waits is covered by it. */
    private void save() {
        synchronized (this) {
            if (writeQueued) return;
            writeQueued = true;
        }
        writer.execute(new Runnable() {
            @Override
            public void run() {
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                String text;
                synchronized (PodcastPositions.this) {
                    writeQueued = false;
                    text = format(positions);
                }
                write(text);
            }
        });
    }

    /** Writes a new copy beside the file and swaps it in: no fsync (see the class comment). */
    private void write(String text) {
        File tmp = new File(file.getPath() + ".new");
        try {
            Writer out = new OutputStreamWriter(new FileOutputStream(tmp), "UTF-8");
            try {
                out.write(text);
            } finally {
                out.close();
            }
            if (!tmp.renameTo(file)) throw new IOException("couldn't replace " + file);
        } catch (IOException e) {
            Log.w(TAG, "couldn't save podcast positions", e);
            if (!tmp.delete()) Log.d(TAG, "no temporary file to delete");
        }
    }

    /** "id TAB ms" lines. */
    static String format(Map<String, Long> positions) {
        StringBuilder b = new StringBuilder(positions.size() * 56);
        for (Map.Entry<String, Long> e : positions.entrySet()) {
            b.append(e.getKey()).append('\t').append(e.getValue()).append('\n');
        }
        return b.toString();
    }

    static void parse(BufferedReader in, Map<String, Long> into) throws IOException {
        String line;
        while ((line = in.readLine()) != null) {
            int tab = line.indexOf('\t');
            if (tab <= 0) continue;
            try {
                long ms = Long.parseLong(line.substring(tab + 1).trim());
                if (ms > 0) into.put(line.substring(0, tab), ms);
            } catch (NumberFormatException ignored) {
                // A damaged line: skip it.
            }
        }
    }

    /** Keeps the newest keep entries (the map is in the order they were saved). */
    static void prune(LinkedHashMap<String, Long> positions, int keep) {
        Iterator<String> oldest = positions.keySet().iterator();
        while (positions.size() > keep && oldest.hasNext()) {
            oldest.next();
            oldest.remove();
        }
    }
}
