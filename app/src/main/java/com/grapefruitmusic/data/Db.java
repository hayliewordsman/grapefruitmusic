package com.grapefruitmusic.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/** Downloads and cached lyrics. */
public final class Db extends SQLiteOpenHelper {
    private static Db instance;

    public static synchronized Db get(Context context) {
        if (instance == null) instance = new Db(context.getApplicationContext());
        return instance;
    }

    private Db(Context context) {
        super(context, "grapefruit.db", null, 1);
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
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Version 1 only.
    }
}
