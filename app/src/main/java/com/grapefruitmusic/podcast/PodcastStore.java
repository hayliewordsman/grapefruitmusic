package com.grapefruitmusic.podcast;

import android.app.DownloadManager;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import com.grapefruitmusic.data.Db;
import com.grapefruitmusic.model.MediaItem;
import com.grapefruitmusic.net.Http;
import com.grapefruitmusic.source.Sources;
import com.grapefruitmusic.util.*;

import java.io.*;
import java.net.*;
import java.util.*;

/** Durable feed subscriptions and system-managed downloads, independent of music servers. */
public final class PodcastStore {
    private static PodcastStore instance;
    private final Context context;
    private final Db db;

    public static synchronized PodcastStore get(Context c) {
        if (instance == null) instance = new PodcastStore(c.getApplicationContext());
        return instance;
    }

    private PodcastStore(Context c) {
        context = c;
        db = Db.get(c);
    }

    public List<MediaItem> subscriptions() {
        return shows(true);
    }

    public List<MediaItem> shows(boolean subscribed) {
        List<MediaItem> out = new ArrayList<MediaItem>();
        Cursor c =
                db.getReadableDatabase()
                        .query(
                                "podcasts",
                                null,
                                subscribed ? "subscribed > 0" : null,
                                null,
                                null,
                                null,
                                "subscribed DESC, title COLLATE NOCASE");
        try {
            while (c.moveToNext()) {
                MediaItem m =
                        new MediaItem(
                                MediaItem.PODCAST,
                                PodcastSource.ID,
                                c.getString(c.getColumnIndexOrThrow("feed")),
                                c.getString(c.getColumnIndexOrThrow("title")));
                m.artist = c.getString(c.getColumnIndexOrThrow("author"));
                m.artKey = c.getString(c.getColumnIndexOrThrow("art"));
                m.added = c.getLong(c.getColumnIndexOrThrow("subscribed"));
                out.add(m);
            }
        } finally {
            c.close();
        }
        return out;
    }

    public boolean subscribed(String feed) {
        Cursor c =
                db.getReadableDatabase()
                        .rawQuery(
                                "SELECT subscribed FROM podcasts WHERE feed=?",
                                new String[] {feed});
        try {
            return c.moveToFirst() && c.getLong(0) > 0;
        } finally {
            c.close();
        }
    }

    public void subscribe(MediaItem show, boolean on) {
        SQLiteDatabase sql = db.getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put("feed", show.id);
        v.put("title", show.title);
        v.put("author", show.artist);
        v.put("art", show.artKey);
        sql.insertWithOnConflict("podcasts", null, v, SQLiteDatabase.CONFLICT_IGNORE);
        v.clear();
        v.put("subscribed", on ? System.currentTimeMillis() : 0);
        sql.update("podcasts", v, "feed=?", new String[] {show.id});
        Sources.get(context).notifyChanged();
        PodcastRefresh.schedule(context);
    }

    /** Returns new episodes only after the initial feed snapshot. */
    public synchronized int refresh(MediaItem show) throws IOException {
        android.content.SharedPreferences prefs = Prefs.raw();
        String key = "feed_" + Hashes.sha1(show.id);
        Response response =
                fetch(
                        show.id,
                        prefs.getString(key + "_etag", null),
                        prefs.getString(key + "_modified", null),
                        key);
        if (response == null) return 0;
        PodcastFeed parsed = PodcastFeed.parse(response.body, show.id, response.url);
        SQLiteDatabase sql = db.getWritableDatabase();
        int fresh = 0;
        boolean baseline = prefs.getBoolean(key + "_loaded", false);
        sql.beginTransaction();
        try {
            ContentValues v = new ContentValues();
            v.put("feed", show.id);
            v.put("title", parsed.show.title);
            v.put("author", parsed.show.artist);
            v.put("art", parsed.show.artKey);
            sql.insertWithOnConflict("podcasts", null, v, SQLiteDatabase.CONFLICT_IGNORE);
            v.remove("feed");
            sql.update("podcasts", v, "feed=?", new String[] {show.id});
            for (MediaItem m : parsed.episodes) {
                v = new ContentValues();
                v.put("id", m.id);
                v.put("feed", show.id);
                v.put("title", m.title);
                v.put("url", m.streamUrl);
                v.put("art", m.artKey);
                v.put("published", m.added);
                v.put("duration", m.durationMs);
                if (sql.insertWithOnConflict("episodes", null, v, SQLiteDatabase.CONFLICT_IGNORE)
                        != -1) fresh++;
                else {
                    v.remove("id");
                    sql.update("episodes", v, "id=?", new String[] {m.id});
                }
            }
            sql.setTransactionSuccessful();
        } finally {
            sql.endTransaction();
        }
        prefs.edit()
                .putBoolean(key + "_loaded", true)
                .putString(key + "_etag", response.etag)
                .putString(key + "_modified", response.modified)
                .apply();
        return baseline ? fresh : 0;
    }

    private static final class Response {
        final String body, url, etag, modified;

        Response(String body, String url, String etag, String modified) {
            this.body = body;
            this.url = url;
            this.etag = etag;
            this.modified = modified;
        }
    }

    private Response fetch(String url, String etag, String modified, String key)
            throws IOException {
        if (!PodcastFeed.webUrl(url)) throw new IOException("Use an http or https feed address");
        for (int redirects = 0; redirects < 6; redirects++) {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(12000);
            c.setReadTimeout(20000);
            c.setRequestProperty("User-Agent", Http.USER_AGENT);
            if (etag != null) c.setRequestProperty("If-None-Match", etag);
            if (modified != null) c.setRequestProperty("If-Modified-Since", modified);
            try {
                int status = c.getResponseCode();
                if (status == 304) return null;
                if (status >= 300 && status < 400) {
                    String location = c.getHeaderField("Location");
                    if (location == null) throw new IOException("Invalid feed redirect");
                    String next = new URL(new URL(url), location).toString();
                    if (!PodcastFeed.webUrl(next)
                            || (url.startsWith("https:") && !next.startsWith("https:")))
                        throw new IOException("Unsafe feed redirect");
                    url = next;
                    continue;
                }
                if (status != 200) throw new IOException("Feed returned HTTP " + status);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                InputStream in = c.getInputStream();
                try {
                    int n;
                    while ((n = in.read(buf)) != -1) {
                        if (out.size() + n > 8 * 1024 * 1024)
                            throw new IOException("Feed is too large");
                        out.write(buf, 0, n);
                    }
                } finally {
                    in.close();
                }
                return new Response(
                        out.toString("UTF-8"),
                        url,
                        c.getHeaderField("ETag"),
                        c.getHeaderField("Last-Modified"));
            } finally {
                c.disconnect();
            }
        }
        throw new IOException("Too many feed redirects");
    }

    public List<MediaItem> episodes(String feed, boolean downloaded) {
        List<MediaItem> out = new ArrayList<MediaItem>();
        Cursor c =
                db.getReadableDatabase()
                        .rawQuery(
                                "SELECT e.*,p.title AS show_title,p.author FROM episodes e JOIN"
                                    + " podcasts p ON p.feed=e.feed"
                                        + (feed == null ? "" : " WHERE e.feed=?")
                                        + " ORDER BY published DESC",
                                feed == null ? null : new String[] {feed});
        try {
            while (c.moveToNext()) {
                MediaItem m =
                        new MediaItem(
                                MediaItem.TRACK,
                                PodcastSource.ID,
                                c.getString(c.getColumnIndexOrThrow("id")),
                                c.getString(c.getColumnIndexOrThrow("title")));
                m.albumId = c.getString(c.getColumnIndexOrThrow("feed"));
                m.album = c.getString(c.getColumnIndexOrThrow("show_title"));
                m.artist = c.getString(c.getColumnIndexOrThrow("author"));
                m.artKey = c.getString(c.getColumnIndexOrThrow("art"));
                m.streamUrl = c.getString(c.getColumnIndexOrThrow("url"));
                m.durationMs = c.getLong(c.getColumnIndexOrThrow("duration"));
                m.added = c.getLong(c.getColumnIndexOrThrow("published"));
                File file = localFile(m);
                if (file != null) m.path = file.getPath();
                if (!downloaded || file != null) out.add(m);
            }
        } finally {
            c.close();
        }
        return out;
    }

    private File destination(MediaItem m) {
        File root = context.getExternalFilesDir("podcasts");
        if (root == null) root = new File(context.getFilesDir(), "podcasts");
        return new File(root, Hashes.sha1(m.id) + ".audio");
    }

    public File localFile(MediaItem m) {
        File f = destination(m);
        if (!f.isFile()) return null;
        long id = downloadId(m);
        if (id < 0) return null;
        DownloadManager manager =
                (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        Cursor c = manager.query(new DownloadManager.Query().setFilterById(id));
        try {
            return c != null
                            && c.moveToFirst()
                            && c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                                    == DownloadManager.STATUS_SUCCESSFUL
                    ? f
                    : null;
        } finally {
            if (c != null) c.close();
        }
    }

    private long downloadId(MediaItem m) {
        Cursor c =
                db.getReadableDatabase()
                        .rawQuery(
                                "SELECT download_id FROM episodes WHERE id=?", new String[] {m.id});
        try {
            return c.moveToFirst() ? c.getLong(0) : -1;
        } finally {
            c.close();
        }
    }

    public void download(MediaItem m) throws IOException {
        if (localFile(m) != null) return;
        if (!PodcastFeed.webUrl(m.streamUrl))
            throw new IOException("No downloadable episode audio");
        DownloadManager manager =
                (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        long old = downloadId(m);
        if (old >= 0) {
            Cursor c = manager.query(new DownloadManager.Query().setFilterById(old));
            try {
                if (c != null
                        && c.moveToFirst()
                        && c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                                != DownloadManager.STATUS_FAILED) return;
            } finally {
                if (c != null) c.close();
            }
            manager.remove(old);
        }
        File dest = destination(m);
        if (!dest.getParentFile().isDirectory() && !dest.getParentFile().mkdirs())
            throw new IOException("Download storage is unavailable");
        if (dest.exists() && !dest.delete())
            throw new IOException("Couldn't replace incomplete episode");
        DownloadManager.Request request =
                new DownloadManager.Request(Uri.parse(m.streamUrl))
                        .setTitle(m.title)
                        .setDescription(m.album)
                        .setDestinationUri(Uri.fromFile(dest))
                        .setNotificationVisibility(
                                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        if (Prefs.wifiOnlyDownloads())
            request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI);
        long id = manager.enqueue(request);
        ContentValues v = new ContentValues();
        v.put("download_id", id);
        db.getWritableDatabase().update("episodes", v, "id=?", new String[] {m.id});
    }

    public void removeDownload(MediaItem m) {
        long id = downloadId(m);
        if (id >= 0)
            ((DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE)).remove(id);
        ContentValues v = new ContentValues();
        v.put("download_id", -1);
        db.getWritableDatabase().update("episodes", v, "id=?", new String[] {m.id});
    }
}
