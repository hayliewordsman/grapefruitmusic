package com.grapefruitmusic.download;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Environment;

import com.grapefruitmusic.data.Db;
import com.grapefruitmusic.model.MediaItem;
import com.grapefruitmusic.util.Bg;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/** Offline downloads: what's queued, what's on the phone, and where. */
public final class Downloads {
    public static final int NONE = -1;
    public static final int QUEUED = 0;
    public static final int ACTIVE = 1;
    public static final int DONE = 2;
    public static final int FAILED = 3;

    public interface Listener {
        void onDownloadsChanged();
    }

    /** Separates the original source id from an album/playlist id in downloaded containers. */
    public static final String SEP = "\t";

    private static Downloads instance;

    private final Context context;
    private final Db db;
    private final List<Listener> listeners = new CopyOnWriteArrayList<Listener>();
    private final Runnable notifier = new Runnable() {
        @Override
        public void run() {
            for (Listener l : listeners) l.onDownloadsChanged();
        }
    };

    public static synchronized Downloads get(Context context) {
        if (instance == null) instance = new Downloads(context.getApplicationContext());
        return instance;
    }

    private Downloads(Context context) {
        this.context = context;
        this.db = Db.get(context);
    }

    public void addListener(Listener l) {
        listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    void notifyChanged() {
        Bg.cancelMain(notifier);
        Bg.main(notifier);
    }

    /** Downloads live in the app's folder on the SD card when there is one. */
    public File root() {
        File dir = null;
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            dir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC);
        }
        if (dir == null) dir = new File(context.getFilesDir(), "music");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    // ---- Queueing ----

    /** Queues tracks (skipping ones already downloaded) and remembers the playlist, if any. */
    public void enqueue(List<MediaItem> tracks, MediaItem playlist) {
        SQLiteDatabase w = db.getWritableDatabase();
        long now = System.currentTimeMillis();
        w.beginTransaction();
        try {
            int i = 0;
            for (MediaItem t : tracks) {
                ContentValues v = new ContentValues();
                v.put("source_id", t.sourceId);
                v.put("track_id", t.id);
                v.put("title", t.title);
                v.put("artist", t.artist);
                v.put("album", t.album);
                v.put("album_id", t.albumId);
                v.put("album_artist", t.albumArtist != null ? t.albumArtist : t.artist);
                v.put("track_no", t.trackNumber);
                v.put("disc_no", t.discNumber);
                v.put("year", t.year);
                v.put("duration_ms", t.durationMs);
                v.put("suffix", t.suffix);
                v.put("art_key", t.artKey);
                v.put("state", QUEUED);
                v.put("added_at", now + (i++));
                int updated = w.update("downloads", v, "source_id = ? AND track_id = ? AND state = ?",
                        new String[]{t.sourceId, t.id, String.valueOf(FAILED)});
                if (updated == 0) w.insertWithOnConflict("downloads", null, v, SQLiteDatabase.CONFLICT_IGNORE);
            }
            if (playlist != null) {
                ContentValues p = new ContentValues();
                p.put("source_id", playlist.sourceId);
                p.put("playlist_id", playlist.id);
                p.put("name", playlist.title);
                p.put("art_key", playlist.artKey);
                p.put("added_at", now);
                w.insertWithOnConflict("dl_playlists", null, p, SQLiteDatabase.CONFLICT_REPLACE);
                w.delete("dl_playlist_tracks", "source_id = ? AND playlist_id = ?",
                        new String[]{playlist.sourceId, playlist.id});
                int pos = 0;
                for (MediaItem t : tracks) {
                    ContentValues e = new ContentValues();
                    e.put("source_id", playlist.sourceId);
                    e.put("playlist_id", playlist.id);
                    e.put("position", pos++);
                    e.put("track_id", t.id);
                    w.insert("dl_playlist_tracks", null, e);
                }
            }
            w.setTransactionSuccessful();
        } finally {
            w.endTransaction();
        }
        notifyChanged();
        startWorker();
    }

    public void retryFailed() {
        ContentValues v = new ContentValues();
        v.put("state", QUEUED);
        v.putNull("error");
        db.getWritableDatabase().update("downloads", v, "state = ?", new String[]{String.valueOf(FAILED)});
        notifyChanged();
        startWorker();
    }

    public void startWorker() {
        if (hasQueued()) context.startService(new Intent(context, DownloadService.class));
    }

    public boolean hasQueued() {
        return count("state IN (" + QUEUED + "," + ACTIVE + ")") > 0;
    }

    // ---- Removing ----

    public void remove(MediaItem track) {
        removeWhere("source_id = ? AND track_id = ?", new String[]{track.sourceId, track.id});
    }

    /** Removes every downloaded track of an album (original source id + album id). */
    public void removeAlbum(String sourceId, String albumId) {
        removeWhere("source_id = ? AND album_id = ?", new String[]{sourceId, albumId});
    }

    public void removePlaylist(String sourceId, String playlistId) {
        SQLiteDatabase w = db.getWritableDatabase();
        w.delete("dl_playlists", "source_id = ? AND playlist_id = ?", new String[]{sourceId, playlistId});
        w.delete("dl_playlist_tracks", "source_id = ? AND playlist_id = ?", new String[]{sourceId, playlistId});
        notifyChanged();
    }

    public void removeAll() {
        removeWhere(null, null);
        SQLiteDatabase w = db.getWritableDatabase();
        w.delete("dl_playlists", null, null);
        w.delete("dl_playlist_tracks", null, null);
        deleteTree(root());
        notifyChanged();
    }

    private void removeWhere(String where, String[] args) {
        SQLiteDatabase w = db.getWritableDatabase();
        Cursor c = w.query("downloads", new String[]{"file_path"}, where, args, null, null, null);
        try {
            while (c.moveToNext()) {
                String path = c.getString(0);
                if (path != null) {
                    new File(path).delete();
                    new File(lrcPathFor(path)).delete();
                }
            }
        } finally {
            c.close();
        }
        w.delete("downloads", where, args);
        notifyChanged();
    }

    private static void deleteTree(File f) {
        File[] children = f.listFiles();
        if (children != null) {
            for (File c : children) deleteTree(c);
        }
        f.delete();
    }

    static String lrcPathFor(String audioPath) {
        int dot = audioPath.lastIndexOf('.');
        return (dot > 0 ? audioPath.substring(0, dot) : audioPath) + ".lrc";
    }

    // ---- Lookups ----

    /** The downloaded file for a track, or null. */
    public File fileFor(MediaItem track) {
        if (track == null) return null;
        Cursor c = db.getReadableDatabase().query("downloads", new String[]{"file_path"},
                "source_id = ? AND track_id = ? AND state = ?",
                new String[]{track.sourceId, track.id, String.valueOf(DONE)}, null, null, null);
        try {
            if (c.moveToFirst() && c.getString(0) != null) {
                File f = new File(c.getString(0));
                if (f.exists()) return f;
            }
        } finally {
            c.close();
        }
        return null;
    }

    /** track id -> state for every track of a source that has a download row. */
    public Map<String, Integer> states(String sourceId) {
        Map<String, Integer> out = new HashMap<String, Integer>();
        Cursor c = db.getReadableDatabase().query("downloads", new String[]{"track_id", "state"},
                "source_id = ?", new String[]{sourceId}, null, null, null);
        try {
            while (c.moveToNext()) out.put(c.getString(0), c.getInt(1));
        } finally {
            c.close();
        }
        return out;
    }

    public int count(String where) {
        Cursor c = db.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM downloads"
                + (where == null ? "" : " WHERE " + where), null);
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }

    public long totalBytes() {
        Cursor c = db.getReadableDatabase().rawQuery("SELECT SUM(size) FROM downloads WHERE state = " + DONE, null);
        try {
            return c.moveToFirst() ? c.getLong(0) : 0;
        } finally {
            c.close();
        }
    }

    // ---- For the worker ----

    static final class Job {
        MediaItem track;
        String artPath;
    }

    Job next() {
        Cursor c = db.getReadableDatabase().query("downloads", null, "state = ?",
                new String[]{String.valueOf(QUEUED)}, null, null, "added_at", "1");
        try {
            if (!c.moveToFirst()) return null;
            Job j = new Job();
            j.track = track(c);
            j.track.artKey = c.getString(c.getColumnIndexOrThrow("art_key"));
            j.artPath = c.getString(c.getColumnIndexOrThrow("art_path"));
            return j;
        } finally {
            c.close();
        }
    }

    /** Puts tracks interrupted by a crash or reboot back in the queue. */
    void resetActive() {
        ContentValues v = new ContentValues();
        v.put("state", QUEUED);
        db.getWritableDatabase().update("downloads", v, "state = ?", new String[]{String.valueOf(ACTIVE)});
    }

    boolean setState(MediaItem t, int state, String error) {
        ContentValues v = new ContentValues();
        v.put("state", state);
        v.put("error", error);
        return update(t, v);
    }

    boolean markDone(MediaItem t, String path, long size) {
        ContentValues v = new ContentValues();
        v.put("state", DONE);
        v.put("file_path", path);
        v.put("size", size);
        v.putNull("error");
        return update(t, v);
    }

    void setArtPath(String sourceId, String artKey, String path) {
        ContentValues v = new ContentValues();
        v.put("art_path", path);
        db.getWritableDatabase().update("downloads", v, "source_id = ? AND art_key = ?", new String[]{sourceId, artKey});
    }

    /** Art already downloaded for the same artwork key, if any. */
    String artPathFor(String sourceId, String artKey) {
        Cursor c = db.getReadableDatabase().query("downloads", new String[]{"art_path"},
                "source_id = ? AND art_key = ? AND art_path IS NOT NULL", new String[]{sourceId, artKey},
                null, null, null, "1");
        try {
            return c.moveToFirst() ? c.getString(0) : null;
        } finally {
            c.close();
        }
    }

    private boolean update(MediaItem t, ContentValues v) {
        return db.getWritableDatabase().update("downloads", v, "source_id = ? AND track_id = ?",
                new String[]{t.sourceId, t.id}) > 0;
    }

    // ---- Browsing downloaded music (used by DownloadedSource) ----

    static MediaItem track(Cursor c) {
        MediaItem m = new MediaItem(MediaItem.TRACK, c.getString(c.getColumnIndexOrThrow("source_id")),
                c.getString(c.getColumnIndexOrThrow("track_id")), c.getString(c.getColumnIndexOrThrow("title")));
        m.artist = c.getString(c.getColumnIndexOrThrow("artist"));
        m.album = c.getString(c.getColumnIndexOrThrow("album"));
        m.albumId = c.getString(c.getColumnIndexOrThrow("album_id"));
        m.albumArtist = c.getString(c.getColumnIndexOrThrow("album_artist"));
        m.trackNumber = c.getInt(c.getColumnIndexOrThrow("track_no"));
        m.discNumber = c.getInt(c.getColumnIndexOrThrow("disc_no"));
        m.year = c.getInt(c.getColumnIndexOrThrow("year"));
        m.durationMs = c.getLong(c.getColumnIndexOrThrow("duration_ms"));
        m.suffix = c.getString(c.getColumnIndexOrThrow("suffix"));
        m.path = c.getString(c.getColumnIndexOrThrow("file_path"));
        String art = c.getString(c.getColumnIndexOrThrow("art_path"));
        m.artKey = art != null ? art : (m.path != null ? "embedded:" + m.path : null);
        return m;
    }

    public List<MediaItem> doneTracks(String where, String[] args, String order) {
        List<MediaItem> out = new ArrayList<MediaItem>();
        String w = "state = " + DONE + (where == null ? "" : " AND (" + where + ")");
        Cursor c = db.getReadableDatabase().query("downloads", null, w, args, null, null, order);
        try {
            while (c.moveToNext()) {
                MediaItem m = track(c);
                if (m.path != null && new File(m.path).exists()) out.add(m);
            }
        } finally {
            c.close();
        }
        return out;
    }

    public List<MediaItem> albums(String where, String[] args, String order, String downloadedSourceId) {
        List<MediaItem> out = new ArrayList<MediaItem>();
        String w = "state = " + DONE + (where == null ? "" : " AND (" + where + ")");
        Cursor c = db.getReadableDatabase().rawQuery("SELECT source_id, album_id, album, album_artist, "
                + "MAX(year), COUNT(*), MAX(art_path), MAX(file_path), MAX(added_at) AS added "
                + "FROM downloads WHERE " + w + " GROUP BY source_id, album_id ORDER BY " + order, args);
        try {
            while (c.moveToNext()) {
                MediaItem m = new MediaItem(MediaItem.ALBUM, downloadedSourceId,
                        c.getString(0) + SEP + c.getString(1), c.getString(2));
                m.artist = c.getString(3);
                m.year = c.getInt(4);
                m.count = c.getInt(5);
                m.artKey = c.getString(6) != null ? c.getString(6)
                        : (c.getString(7) != null ? "embedded:" + c.getString(7) : null);
                out.add(m);
            }
        } finally {
            c.close();
        }
        return out;
    }

    public List<MediaItem> artists(String where, String[] args, String downloadedSourceId) {
        List<MediaItem> out = new ArrayList<MediaItem>();
        String w = "state = " + DONE + " AND album_artist IS NOT NULL" + (where == null ? "" : " AND (" + where + ")");
        Cursor c = db.getReadableDatabase().rawQuery("SELECT album_artist, COUNT(DISTINCT source_id || album_id) "
                + "FROM downloads WHERE " + w + " GROUP BY album_artist ORDER BY album_artist COLLATE NOCASE", args);
        try {
            while (c.moveToNext()) {
                MediaItem m = new MediaItem(MediaItem.ARTIST, downloadedSourceId, c.getString(0), c.getString(0));
                m.count = c.getInt(1);
                out.add(m);
            }
        } finally {
            c.close();
        }
        return out;
    }

    public List<MediaItem> playlists(String downloadedSourceId) {
        List<MediaItem> out = new ArrayList<MediaItem>();
        Cursor c = db.getReadableDatabase().rawQuery("SELECT p.source_id, p.playlist_id, p.name, "
                + "(SELECT COUNT(*) FROM dl_playlist_tracks t JOIN downloads d ON d.source_id = t.source_id "
                + "AND d.track_id = t.track_id AND d.state = " + DONE
                + " WHERE t.source_id = p.source_id AND t.playlist_id = p.playlist_id), "
                + "(SELECT d.art_path FROM dl_playlist_tracks t JOIN downloads d ON d.source_id = t.source_id "
                + "AND d.track_id = t.track_id WHERE t.source_id = p.source_id AND t.playlist_id = p.playlist_id "
                + "AND d.art_path IS NOT NULL ORDER BY t.position LIMIT 1) "
                + "FROM dl_playlists p ORDER BY p.name COLLATE NOCASE", null);
        try {
            while (c.moveToNext()) {
                MediaItem m = new MediaItem(MediaItem.PLAYLIST, downloadedSourceId,
                        c.getString(0) + SEP + c.getString(1), c.getString(2));
                m.count = c.getInt(3);
                m.artKey = c.getString(4);
                out.add(m);
            }
        } finally {
            c.close();
        }
        return out;
    }

    public List<MediaItem> playlistTracks(String sourceId, String playlistId) {
        List<MediaItem> out = new ArrayList<MediaItem>();
        Cursor c = db.getReadableDatabase().rawQuery("SELECT d.* FROM dl_playlist_tracks t JOIN downloads d "
                + "ON d.source_id = t.source_id AND d.track_id = t.track_id "
                + "WHERE t.source_id = ? AND t.playlist_id = ? AND d.state = " + DONE + " ORDER BY t.position",
                new String[]{sourceId, playlistId});
        try {
            while (c.moveToNext()) {
                MediaItem m = track(c);
                if (m.path != null && new File(m.path).exists()) out.add(m);
            }
        } finally {
            c.close();
        }
        return out;
    }
}
