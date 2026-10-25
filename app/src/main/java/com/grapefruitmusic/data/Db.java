package com.grapefruitmusic.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/** Downloads, cached lyrics, playlist covers and descriptions, and song loudness. */
public final class Db extends SQLiteOpenHelper {
    private static Db instance;

    public static synchronized Db get(Context context) {
        if (instance == null) instance = new Db(context.getApplicationContext());
        return instance;
    }

    private Db(Context context) {
        super(context, "grapefruit.db", null, 3);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE downloads ("
                + "source_id TEXT NOT NULL, track_id TEXT NOT NULL,"
                + "title TEXT, artist TEXT, album TEXT, album_id TEXT, album_artist TEXT,"
                + "track_no INTEGER, disc_no INTEGER, year INTEGER, duration_ms INTEGER,"
                + "suffix TEXT, art_key TEXT, art_path TEXT, file_path TEXT, size INTEGER,"
                + "state INTEGER NOT NULL, error TEXT, added_at INTEGER,"
                + "PRIMARY KEY (source_id, track_id))");
        db.execSQL("CREATE INDEX downloads_state ON downloads(state, added_at)");
        db.execSQL("CREATE TABLE dl_playlists ("
                + "source_id TEXT NOT NULL, playlist_id TEXT NOT NULL, name TEXT, art_key TEXT, added_at INTEGER,"
                + "PRIMARY KEY (source_id, playlist_id))");
        db.execSQL("CREATE TABLE dl_playlist_tracks ("
                + "source_id TEXT NOT NULL, playlist_id TEXT NOT NULL, position INTEGER NOT NULL, track_id TEXT NOT NULL)");
        db.execSQL("CREATE TABLE lyrics ("
                + "song_key TEXT PRIMARY KEY, synced INTEGER, body TEXT, credit TEXT, fetched_at INTEGER)");
        createPlaylistMeta(db);
        createLoudness(db);
    }

    /** Version 2 (Grapefruit 1.5). */
    private static void createPlaylistMeta(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE playlist_meta ("
                + "source_id TEXT NOT NULL, playlist_id TEXT NOT NULL, description TEXT, cover_path TEXT,"
                + "PRIMARY KEY (source_id, playlist_id))");
    }

    /** Version 3 (Grapefruit 1.7): Volume Match measurements. lufs is NULL if unmeasurable. */
    private static void createLoudness(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE loudness (song_key TEXT PRIMARY KEY, lufs REAL, measured_at INTEGER)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) createPlaylistMeta(db);
        if (oldVersion < 3) createLoudness(db);
    }
}
