package com.grapefruitmusic.podcast;

import android.app.DownloadManager;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.grapefruitmusic.data.Db;
import com.grapefruitmusic.model.MediaItem;
import com.grapefruitmusic.net.Http;
import com.grapefruitmusic.source.Sources;
import com.grapefruitmusic.util.*;

import java.io.*;
import java.net.*;
import java.util.*;

/** Durable feed subscriptions and app-managed downloads, with legacy system-download compatibility. */
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
        PodcastFeed parsed = response.feed;
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
        final PodcastFeed feed;
        final String etag, modified;

        Response(PodcastFeed feed, String etag, String modified) {
            this.feed = feed;
            this.etag = etag;
            this.modified = modified;
        }
    }

    private Response fetch(String url, String etag, String modified, String key)
            throws IOException {
        if (!PodcastFeed.webUrl(url)) throw new IOException("Use an http or https feed address");
        String originalUrl = url;
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
                InputStream in = c.getInputStream();
                try {
                    return new Response(
                            PodcastFeed.parse(in, originalUrl, url),
                            c.getHeaderField("ETag"),
                            c.getHeaderField("Last-Modified"));
                } finally {
                    in.close();
                }
            } finally {
                c.disconnect();
            }
        }
        throw new IOException("Too many feed redirects");
    }

    public List<MediaItem> episodes(String feed, boolean downloaded) {
        List<MediaItem> out = new ArrayList<MediaItem>();
        Map<Long, PodcastDownload> progress = downloadSnapshots();
        Cursor c =
                db.getReadableDatabase()
                        .rawQuery(
                                "SELECT e.*,p.title AS show_title,p.author FROM episodes e JOIN"
                                        + " podcasts p ON p.feed=e.feed"
                                        + (feed == null ? " WHERE 1=1" : " WHERE e.feed=?")
                                        + (downloaded
                                                ? " AND (e.download_id >= 0 OR e.download_state >="
                                                        + " 0)"
                                                : "")
                                        + " ORDER BY published DESC",
                                feed == null ? null : new String[] {feed});
        try {
            while (c.moveToNext()) {
                MediaItem m = readEpisode(c);
                int owned = c.getInt(c.getColumnIndexOrThrow("download_state"));
                if (owned >= 0) {
                    m.podcastDownload = ownProgress(c, m);
                    if (m.podcastDownload.status == PodcastDownload.COMPLETE)
                        m.path = destination(m).getPath();
                }
                long downloadId = c.getLong(c.getColumnIndexOrThrow("download_id"));
                if (owned < 0 && downloadId >= 0) {
                    PodcastDownload state = progress.get(downloadId);
                    File file = destination(m);
                    if (state != null && state.status == PodcastDownload.COMPLETE && file.isFile())
                        m.path = file.getPath();
                    else if (state == null || state.status == PodcastDownload.COMPLETE)
                        state = new PodcastDownload(PodcastDownload.MISSING, 0, 0, -1);
                    m.podcastDownload = state;
                }
                out.add(m);
            }
        } finally {
            c.close();
        }
        if (downloaded)
            Collections.sort(
                    out,
                    (a, b) -> {
                        int pa =
                                a.podcastDownload != null && a.podcastDownload.active()
                                        ? 0
                                        : a.path == null ? 1 : 2;
                        int pb =
                                b.podcastDownload != null && b.podcastDownload.active()
                                        ? 0
                                        : b.path == null ? 1 : 2;
                        return pa - pb; // Stable sort retains publication order within each status
                        // group.
                    });
        return out;
    }

    private static MediaItem readEpisode(Cursor c) {
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
        return m;
    }

    private PodcastDownload ownProgress(Cursor c, MediaItem m) {
        int state = c.getInt(c.getColumnIndexOrThrow("download_state"));
        if (state == PodcastDownload.COMPLETE && !destination(m).isFile())
            state = PodcastDownload.MISSING;
        return new PodcastDownload(
                state,
                c.getInt(c.getColumnIndexOrThrow("download_reason")),
                c.getLong(c.getColumnIndexOrThrow("download_received")),
                c.getLong(c.getColumnIndexOrThrow("download_total")),
                c.getString(c.getColumnIndexOrThrow("download_error")));
    }

    /** Batched provider queries per refresh, including only episode downloads owned by this app. */
    private Map<Long, PodcastDownload> downloadSnapshots() {
        List<Long> ids = new ArrayList<Long>();
        Cursor rows =
                db.getReadableDatabase()
                        .rawQuery("SELECT download_id FROM episodes WHERE download_id >= 0", null);
        try {
            while (rows.moveToNext()) ids.add(rows.getLong(0));
        } finally {
            rows.close();
        }
        Map<Long, PodcastDownload> out = new HashMap<Long, PodcastDownload>();
        if (ids.isEmpty()) return out;
        DownloadManager manager =
                (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        // Bound the query argument count on old DownloadProvider implementations.
        for (int start = 0; start < ids.size(); start += 400) {
            long[] batch = new long[Math.min(400, ids.size() - start)];
            for (int i = 0; i < batch.length; i++) batch[i] = ids.get(start + i);
            Cursor c = manager.query(new DownloadManager.Query().setFilterById(batch));
            if (c == null) continue;
            try {
                while (c.moveToNext()) {
                    out.put(
                            c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)),
                            new PodcastDownload(
                                    c.getInt(
                                            c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                                    c.getInt(
                                            c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)),
                                    c.getLong(
                                            c.getColumnIndexOrThrow(
                                                    DownloadManager
                                                            .COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                                    c.getLong(
                                            c.getColumnIndexOrThrow(
                                                    DownloadManager.COLUMN_TOTAL_SIZE_BYTES))));
                }
            } finally {
                c.close();
            }
        }
        return out;
    }

    /** Read live state when opening a menu, even if the screen snapshot is slightly older. */
    public PodcastDownload downloadProgress(MediaItem m) {
        Cursor row =
                db.getReadableDatabase()
                        .rawQuery("SELECT * FROM episodes WHERE id=?", new String[] {m.id});
        try {
            if (row.moveToFirst() && row.getInt(row.getColumnIndexOrThrow("download_state")) >= 0)
                return ownProgress(row, m);
        } finally {
            row.close();
        }
        long id = downloadId(m);
        if (id < 0) return null;
        DownloadManager manager =
                (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        Cursor c = manager.query(new DownloadManager.Query().setFilterById(id));
        try {
            if (c != null && c.moveToFirst()) {
                PodcastDownload p =
                        new PodcastDownload(
                                c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                                c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)),
                                c.getLong(
                                        c.getColumnIndexOrThrow(
                                                DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                                c.getLong(
                                        c.getColumnIndexOrThrow(
                                                DownloadManager.COLUMN_TOTAL_SIZE_BYTES)));
                if (p.status != PodcastDownload.COMPLETE || destination(m).isFile()) return p;
            }
            return new PodcastDownload(PodcastDownload.MISSING, 0, 0, -1);
        } finally {
            if (c != null) c.close();
        }
    }

    File destination(MediaItem m) {
        File root = context.getExternalFilesDir("podcasts");
        if (root == null) root = new File(context.getFilesDir(), "podcasts");
        return new File(root, Hashes.sha1(m.id) + ".audio");
    }

    public File localFile(MediaItem m) {
        File file = destination(m);
        if (!file.isFile()) return null;
        PodcastDownload state = downloadProgress(m);
        return state != null && state.status == PodcastDownload.COMPLETE ? file : null;
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

    public synchronized void download(MediaItem m) throws IOException {
        if (localFile(m) != null) return;
        if (!PodcastFeed.webUrl(m.streamUrl))
            throw new IOException("No downloadable episode audio");
        Cursor c =
                db.getReadableDatabase()
                        .rawQuery(
                                "SELECT download_state FROM episodes WHERE id=?",
                                new String[] {m.id});
        try {
            if (!c.moveToFirst()) throw new IOException("Refresh this podcast before downloading");
            int state = c.getInt(0);
            if (state == PodcastDownload.QUEUED
                    || state == PodcastDownload.RUNNING
                    || state == PodcastDownload.PAUSED) {
                resumeDownloads();
                return;
            }
        } finally {
            c.close();
        }
        removeLegacy(m);
        db.getWritableDatabase()
                .execSQL(
                        "UPDATE episodes SET"
                            + " download_id=-1,download_state=1,download_received=0,download_total=-1,download_error=NULL,download_reason=0,download_generation=download_generation+1"
                            + " WHERE id=?",
                        new Object[] {m.id});
        resumeDownloads();
    }

    private void removeLegacy(MediaItem m) {
        long id = downloadId(m);
        if (id >= 0)
            ((DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE)).remove(id);
    }

    public synchronized void removeDownload(MediaItem m) {
        removeLegacy(m);
        db.getWritableDatabase()
                .execSQL(
                        "UPDATE episodes SET"
                            + " download_id=-1,download_state=-1,download_received=0,download_total=-1,download_error=NULL,download_generation=download_generation+1"
                            + " WHERE id=?",
                        new Object[] {m.id});
        PodcastDownloadService.cancel(m.id);
        destination(m).delete();
    }

    public boolean hasPendingTransfers() {
        Cursor c =
                db.getReadableDatabase()
                        .rawQuery(
                                "SELECT 1 FROM episodes WHERE download_state IN (1,2,4) LIMIT 1",
                                null);
        try {
            return c.moveToFirst();
        } finally {
            c.close();
        }
    }

    public void resumeDownloads() {
        if (hasPendingTransfers())
            com.grapefruitmusic.compat.Platform.service(
                    context, new Intent(context, PodcastDownloadService.class));
    }

    static final class Transfer {
        final MediaItem item;
        final long generation;

        Transfer(MediaItem item, long generation) {
            this.item = item;
            this.generation = generation;
        }
    }

    synchronized void recoverTransfers() {
        db.getWritableDatabase()
                .execSQL(
                        "UPDATE episodes SET download_state=1,download_received=0 WHERE"
                                + " download_state=2");
    }

    synchronized Transfer nextTransfer() {
        Cursor c =
                db.getReadableDatabase()
                        .rawQuery(
                                "SELECT e.*,p.title AS show_title,p.author FROM episodes e JOIN"
                                    + " podcasts p ON p.feed=e.feed WHERE e.download_state IN (1,4)"
                                    + " ORDER BY e.published DESC LIMIT 1",
                                null);
        try {
            if (!c.moveToFirst()) return null;
            Transfer t =
                    new Transfer(
                            readEpisode(c),
                            c.getLong(c.getColumnIndexOrThrow("download_generation")));
            updateTransfer(t, PodcastDownload.RUNNING, 0, -1, 0, null);
            return t;
        } finally {
            c.close();
        }
    }

    synchronized boolean currentTransfer(Transfer t) {
        Cursor c =
                db.getReadableDatabase()
                        .rawQuery(
                                "SELECT 1 FROM episodes WHERE id=? AND download_generation=? AND"
                                        + " download_state IN (1,2,4)",
                                new String[] {t.item.id, Long.toString(t.generation)});
        try {
            return c.moveToFirst();
        } finally {
            c.close();
        }
    }

    synchronized void updateTransfer(
            Transfer t, int state, long received, long total, int reason, String error) {
        ContentValues v = new ContentValues();
        v.put("download_state", state);
        v.put("download_received", received);
        v.put("download_total", total);
        v.put("download_reason", reason);
        v.put("download_error", error);
        db.getWritableDatabase()
                .update(
                        "episodes",
                        v,
                        "id=? AND download_generation=? AND download_state IN (1,2,4)",
                        new String[] {t.item.id, Long.toString(t.generation)});
    }

    synchronized boolean finishTransfer(Transfer t, File part) throws IOException {
        if (!currentTransfer(t)) return false;
        File dest = destination(t.item);
        if (!part.renameTo(dest)) throw new IOException("Couldn't save episode to storage");
        updateTransfer(t, PodcastDownload.COMPLETE, dest.length(), dest.length(), 0, null);
        return true;
    }
}
