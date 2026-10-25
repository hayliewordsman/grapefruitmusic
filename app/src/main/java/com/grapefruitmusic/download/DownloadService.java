package com.grapefruitmusic.download;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import com.grapefruitmusic.R;
import com.grapefruitmusic.lyrics.Lyrics;
import com.grapefruitmusic.lyrics.LyricsRepository;
import com.grapefruitmusic.model.MediaItem;
import com.grapefruitmusic.net.Http;
import com.grapefruitmusic.source.Formats;
import com.grapefruitmusic.source.MusicSource;
import com.grapefruitmusic.source.Sources;
import com.grapefruitmusic.source.StreamRequest;
import com.grapefruitmusic.ui.MainActivity;
import com.grapefruitmusic.util.ArtworkLoader;
import com.grapefruitmusic.util.Hashes;
import com.grapefruitmusic.util.Prefs;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;

/** Works through the download queue one song at a time. */
public final class DownloadService extends Service {
    private static final String TAG = "GrapefruitDownloads";
    private static final int NOTIFICATION_ID = 2;
    private static final int ART_SIZE = 600;

    private Downloads downloads;
    private NotificationManager notifications;
    private Thread worker;

    @Override
    public void onCreate() {
        super.onCreate();
        downloads = Downloads.get(this);
        notifications = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        downloads.resetActive();
    }

    @Override
    public synchronized int onStartCommand(Intent intent, int flags, int startId) {
        if (worker == null || !worker.isAlive()) {
            worker = new Thread(new Runnable() {
                @Override
                public void run() {
                    work();
                }
            }, "gf-downloads");
            worker.setPriority(Thread.MIN_PRIORITY);
            worker.start();
        }
        return START_STICKY;
    }

    private void work() {
        int done = 0;
        int failed = 0;
        while (true) {
            if (Prefs.wifiOnlyDownloads() && !Http.isOnWifi(this)) {
                // NetworkReceiver restarts us when Wi-Fi comes back.
                showWaiting();
                break;
            }
            if (!Http.isOnline(this)) {
                showWaiting();
                break;
            }
            Downloads.Job job = downloads.next();
            if (job == null) {
                notifications.cancel(NOTIFICATION_ID);
                break;
            }
            int remaining = downloads.count("state = " + Downloads.QUEUED);
            showProgress(job.track, done + 1, done + remaining, 0);
            downloads.setState(job.track, Downloads.ACTIVE, null);
            downloads.notifyChanged();
            try {
                download(job, done + 1, done + remaining);
                done++;
            } catch (IOException e) {
                Log.w(TAG, "download failed: " + job.track.title, e);
                downloads.setState(job.track, Downloads.FAILED, e.getMessage());
                failed++;
                if (!Http.isOnline(this)) {
                    // Lost the connection: put it back and wait.
                    downloads.setState(job.track, Downloads.QUEUED, null);
                    failed--;
                }
            }
            downloads.notifyChanged();
        }
        stopForeground(false);
        if (failed > 0) showFailed(failed);
        stopSelf();
    }

    private void download(Downloads.Job job, int index, int total) throws IOException {
        MediaItem t = job.track;
        MusicSource source = Sources.get(this).byId(t.sourceId);
        if (source == null) throw new IOException("The server was removed");
        StreamRequest req = source.download(t, Prefs.downloadQuality());
        if (req == null) throw new IOException("This server can't download songs");

        File dir = new File(downloads.root(), safe(t.sourceId));
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("Can't write to storage");

        HttpURLConnection c = Http.open("GET", req.url, req.headers, null, null);
        File dest;
        File part;
        long written = 0;
        try {
            String ext = Formats.extensionFor(c.getContentType(), req.extension != null ? req.extension : "mp3");
            dest = new File(dir, safe(t.id) + "." + ext);
            part = new File(dir, safe(t.id) + ".part");
            long length = c.getContentLength();
            InputStream in = c.getInputStream();
            OutputStream out = new FileOutputStream(part);
            try {
                byte[] buf = new byte[32768];
                int n;
                long lastUpdate = 0;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    written += n;
                    long now = System.currentTimeMillis();
                    if (now - lastUpdate > 750) {
                        lastUpdate = now;
                        showProgress(t, index, total, length > 0 ? (int) (written * 100 / length) : -1);
                    }
                }
            } finally {
                out.close();
            }
        } finally {
            c.disconnect();
        }
        if (written == 0) {
            part.delete();
            throw new IOException("The server sent an empty file");
        }
        if (dest.exists()) dest.delete();
        if (!part.renameTo(dest)) {
            part.delete();
            throw new IOException("Can't write to storage");
        }

        // Cover art, once per album.
        if (job.artPath == null && t.artKey != null) {
            String existing = downloads.artPathFor(t.sourceId, t.artKey);
            if (existing == null || !new File(existing).exists()) {
                String url = source.artworkUrl(t.artKey, ART_SIZE);
                if (url != null && url.startsWith("http")) {
                    File art = new File(dir, "art_" + Hashes.md5(t.artKey) + ".jpg");
                    try {
                        ArtworkLoader.download(url, art);
                        existing = art.getPath();
                    } catch (IOException e) {
                        existing = null; // The song still works without artwork.
                    }
                }
            }
            if (existing != null) downloads.setArtPath(t.sourceId, t.artKey, existing);
        }

        // Lyrics, saved next to the song so they work offline.
        Lyrics lyrics = LyricsRepository.load(this, t);
        if (lyrics != null) {
            Writer w = new OutputStreamWriter(new FileOutputStream(Downloads.lrcPathFor(dest.getPath())), "UTF-8");
            try {
                w.write(lyrics.toText());
            } finally {
                w.close();
            }
        }

        if (!downloads.markDone(t, dest.getPath(), dest.length())) {
            // Removed while downloading.
            dest.delete();
            new File(Downloads.lrcPathFor(dest.getPath())).delete();
        }
    }

    private static String safe(String s) {
        return s.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private PendingIntent openApp() {
        return PendingIntent.getActivity(this, 1, new Intent(this, MainActivity.class), 0);
    }

    private void showProgress(MediaItem t, int index, int total, int percent) {
        Notification n = new Notification.Builder(this)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("Downloading " + index + " of " + Math.max(index, total))
                .setContentText(t.title)
                .setProgress(100, Math.max(0, percent), percent < 0)
                .setOngoing(true)
                .setShowWhen(false)
                .setContentIntent(openApp())
                .build();
        startForeground(NOTIFICATION_ID, n);
    }

    private void showWaiting() {
        if (!downloads.hasQueued()) return;
        Notification n = new Notification.Builder(this)
                .setSmallIcon(R.drawable.ic_stat_grapefruit)
                .setContentTitle("Downloads paused")
                .setContentText(Prefs.wifiOnlyDownloads() ? "Waiting for Wi‑Fi" : "Waiting for a connection")
                .setContentIntent(openApp())
                .build();
        stopForeground(false);
        notifications.notify(NOTIFICATION_ID, n);
    }

    private void showFailed(int failed) {
        Notification n = new Notification.Builder(this)
                .setSmallIcon(R.drawable.ic_stat_grapefruit)
                .setContentTitle(failed == 1 ? "1 song didn't download" : failed + " songs didn't download")
                .setContentText("Open Settings › Downloads to try again")
                .setAutoCancel(true)
                .setContentIntent(openApp())
                .build();
        notifications.notify(NOTIFICATION_ID, n);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
