package com.grapefruitmusic.lyrics;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.grapefruitmusic.data.Db;
import com.grapefruitmusic.model.MediaItem;
import com.grapefruitmusic.net.Http;
import com.grapefruitmusic.source.LocalSource;
import com.grapefruitmusic.source.MusicSource;
import com.grapefruitmusic.source.Sources;
import com.grapefruitmusic.util.Prefs;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Locale;

/**
 * Finds lyrics for a song: saved copy, a .lrc file next to the song, the server, then LRCLIB
 * (a free, community-run database of synced lyrics). Results are cached so they work offline.
 */
public final class LyricsRepository {
    private static final long RETRY_MISSING_MS = 7L * 24 * 60 * 60 * 1000;
    private static final String LRCLIB = "https://lrclib.net/api/";

    private LyricsRepository() {
    }

    /** Cache key shared by every copy of a song (streamed, downloaded, local). */
    public static String songKey(MediaItem t) {
        return norm(t.artist) + "|" + norm(t.title);
    }

    private static String norm(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.US).replaceAll("\\s+", " ");
    }

    /** Blocking. Returns null when there are no lyrics. */
    public static Lyrics load(Context context, MediaItem track) {
        String key = songKey(track);
        SQLiteDatabase db = Db.get(context).getWritableDatabase();
        Cursor c = db.query("lyrics", new String[]{"synced", "body", "credit", "fetched_at"}, "song_key = ?",
                new String[]{key}, null, null, null);
        try {
            if (c.moveToFirst()) {
                String body = c.getString(1);
                if (body != null) {
                    Lyrics l = LrcParser.parse(body, c.getString(2));
                    if (l != null) return l;
                } else if (System.currentTimeMillis() - c.getLong(3) < RETRY_MISSING_MS
                        || !Http.isOnline(context)) {
                    return null;
                }
            }
        } finally {
            c.close();
        }

        boolean online = Http.isOnline(context);
        boolean networkFailed = false;
        Lyrics found = null;
        try {
            found = LocalSource.sidecarLyrics(track.path, "Lyrics file");
        } catch (IOException ignored) {
            // Unreadable .lrc file.
        }
        MusicSource source = Sources.get(context).byId(track.sourceId);
        if (found == null && source != null && (online || !source.isRemote())) {
            try {
                found = source.lyrics(track);
            } catch (IOException e) {
                networkFailed = source.isRemote();
            }
        }
        if ((found == null || !found.synced) && Prefs.onlineLyrics() && online) {
            try {
                Lyrics fromLrclib = lrclib(track);
                if (fromLrclib != null && (found == null || fromLrclib.synced)) found = fromLrclib;
            } catch (IOException e) {
                networkFailed = true;
            }
        }
        if (found != null && found.isEmpty()) found = null;

        if (found != null || (online && !networkFailed)) {
            ContentValues v = new ContentValues();
            v.put("song_key", key);
            v.put("synced", found != null && found.synced ? 1 : 0);
            if (found != null) {
                v.put("body", found.toText());
                v.put("credit", found.credit);
            } else {
                v.putNull("body");
            }
            v.put("fetched_at", System.currentTimeMillis());
            db.insertWithOnConflict("lyrics", null, v, SQLiteDatabase.CONFLICT_REPLACE);
        }
        return found;
    }

    private static Lyrics lrclib(MediaItem t) throws IOException {
        if (t.title == null || t.artist == null) return null;
        long seconds = Math.round(t.durationMs / 1000.0);
        StringBuilder get = new StringBuilder(LRCLIB).append("get?artist_name=").append(Http.enc(t.artist))
                .append("&track_name=").append(Http.enc(t.title));
        if (t.album != null) get.append("&album_name=").append(Http.enc(t.album));
        if (seconds > 0) get.append("&duration=").append(seconds);
        try {
            Lyrics l = fromLrclib(Http.getJson(get.toString(), null));
            if (l != null) return l;
        } catch (Http.StatusException e) {
            if (e.code != 404) throw e;
        }

        String artist = t.artist;
        int cut = artist.indexOf(", ");
        if (cut > 0) artist = artist.substring(0, cut);
        JSONArray results = Http.getJsonArray(LRCLIB + "search?track_name=" + Http.enc(t.title)
                + "&artist_name=" + Http.enc(artist), null);
        JSONObject synced = null;
        JSONObject plain = null;
        for (int i = 0; i < results.length(); i++) {
            JSONObject r = results.optJSONObject(i);
            if (r == null) continue;
            if (seconds > 0 && r.has("duration") && Math.abs(r.optDouble("duration") - seconds) > 3) continue;
            if (synced == null && !r.isNull("syncedLyrics")) synced = r;
            if (plain == null && !r.isNull("plainLyrics")) plain = r;
        }
        return fromLrclib(synced != null ? synced : plain);
    }

    private static Lyrics fromLrclib(JSONObject r) {
        if (r == null) return null;
        if (!r.isNull("syncedLyrics")) {
            Lyrics l = LrcParser.parse(r.optString("syncedLyrics"), "LRCLIB");
            if (l != null) return l;
        }
        if (!r.isNull("plainLyrics")) return LrcParser.parse(r.optString("plainLyrics"), "LRCLIB");
        return null;
    }
}
