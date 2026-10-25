package com.grapefruitmusic.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.grapefruitmusic.model.MediaItem;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Playlist cover photos and descriptions kept on this phone. Servers can't store a cover photo,
 * and not every source keeps a description. Covers are JPEG files in files/covers.
 */
public final class PlaylistMeta {
    private static PlaylistMeta instance;
    /** Bumped by every edit, so pages listing playlists know to reload. */
    private static volatile int version;

    private final Db db;
    private final File coversDir;
    /** Playlist key -> its own artwork key, from before a cover photo replaced it. */
    private final Map<String, String> sourceArt = new HashMap<String, String>();

    public static synchronized PlaylistMeta get(Context context) {
        if (instance == null) instance = new PlaylistMeta(context.getApplicationContext());
        return instance;
    }

    private PlaylistMeta(Context context) {
        db = Db.get(context);
        coversDir = new File(context.getFilesDir(), "covers");
    }

    public static int version() {
        return version;
    }

    /** A new, unused file for a cover photo. */
    public File newCoverFile() {
        coversDir.mkdirs();
        return new File(coversDir, "cover-" + System.currentTimeMillis() + "-" + (int) (Math.random() * 1e6) + ".jpg");
    }

    /** Is this artwork key one of the phone's cover photos? */
    public boolean isCover(String artKey) {
        return artKey != null && artKey.startsWith(coversDir.getPath() + "/");
    }

    /** The playlist's artwork without its cover photo, if known. */
    public synchronized String sourceArtKey(MediaItem playlist) {
        if (sourceArt.containsKey(playlist.key())) return sourceArt.get(playlist.key());
        return isCover(playlist.artKey) ? null : playlist.artKey;
    }

    /** Puts the phone's covers and descriptions on playlists. Blocking (database). */
    public List<MediaItem> apply(List<MediaItem> playlists) {
        for (MediaItem m : playlists) apply(m);
        return playlists;
    }

    public synchronized void apply(MediaItem playlist) {
        Cursor c = db.getReadableDatabase().query("playlist_meta", new String[]{"description", "cover_path"},
                "source_id = ? AND playlist_id = ?", new String[]{playlist.sourceId, playlist.id}, null, null, null);
        try {
            if (!c.moveToFirst()) return;
            if (!c.isNull(0)) playlist.description = c.getString(0);
            String cover = c.getString(1);
            if (cover != null && new File(cover).exists() && !cover.equals(playlist.artKey)) {
                sourceArt.put(playlist.key(), playlist.artKey);
                playlist.artKey = cover;
            }
        } finally {
            c.close();
        }
    }

    /**
     * Saves a playlist's description (null: unchanged) and cover photo (a file from
     * newCoverFile(), "" to remove it, or null: unchanged), and updates playlist to match.
     * The replaced cover file is deleted. Blocking.
     */
    public synchronized void save(MediaItem playlist, String description, String coverPath) {
        SQLiteDatabase w = db.getWritableDatabase();
        String[] where = {playlist.sourceId, playlist.id};
        String oldDescription = null;
        String oldCover = null;
        boolean exists;
        Cursor c = w.query("playlist_meta", new String[]{"description", "cover_path"},
                "source_id = ? AND playlist_id = ?", where, null, null, null);
        try {
            exists = c.moveToFirst();
            if (exists) {
                oldDescription = c.getString(0);
                oldCover = c.getString(1);
            }
        } finally {
            c.close();
        }
        ContentValues v = new ContentValues();
        v.put("source_id", playlist.sourceId);
        v.put("playlist_id", playlist.id);
        v.put("description", description != null ? description : oldDescription);
        String cover = coverPath == null ? oldCover : (coverPath.length() == 0 ? null : coverPath);
        v.put("cover_path", cover);
        if (exists) w.update("playlist_meta", v, "source_id = ? AND playlist_id = ?", where);
        else w.insert("playlist_meta", null, v);

        if (oldCover != null && !oldCover.equals(cover)) new File(oldCover).delete();
        if (description != null) playlist.description = description;
        if (coverPath != null) {
            String key = playlist.key();
            if (cover != null) {
                if (!sourceArt.containsKey(key)) sourceArt.put(key, playlist.artKey);
                playlist.artKey = cover;
            } else if (sourceArt.containsKey(key)) {
                playlist.artKey = sourceArt.remove(key);
            } else if (playlist.artKey != null && playlist.artKey.equals(oldCover)) {
                playlist.artKey = null;
            }
        }
        version++;
    }

    /** Notes that a playlist changed on its source (renamed, songs removed). */
    public static void changed() {
        version++;
    }
}
