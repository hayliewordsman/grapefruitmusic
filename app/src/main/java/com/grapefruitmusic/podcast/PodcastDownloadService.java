package com.grapefruitmusic.podcast;

import android.app.*;
import android.content.*;
import android.net.wifi.WifiManager;
import android.os.*;

import com.grapefruitmusic.R;
import com.grapefruitmusic.compat.Platform;
import com.grapefruitmusic.net.Http;
import com.grapefruitmusic.net.PodcastAudio;
import com.grapefruitmusic.ui.MainActivity;
import com.grapefruitmusic.util.Prefs;

import java.io.*;
import java.net.HttpURLConnection;
import java.util.Locale;

/** Downloads with Grapefruit's modern TLS stack, rather than Jelly Bean's system TLS stack. */
public final class PodcastDownloadService extends Service {
    private static final int NOTIFICATION = 4002;
    private static volatile PodcastDownloadService live;
    private volatile HttpURLConnection connection;
    private volatile String currentId;
    private volatile boolean stopping;
    private Thread worker;
    private PodcastStore store;
    private PowerManager.WakeLock cpu;
    private WifiManager.WifiLock wifi;

    public static void cancel(String id) {
        PodcastDownloadService s = live;
        if (s != null && id.equals(s.currentId) && s.connection != null) s.connection.disconnect();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        live = this;
        store = PodcastStore.get(this);
        cpu =
                ((PowerManager) getSystemService(POWER_SERVICE))
                        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "grapefruit:podcast-download");
        cpu.setReferenceCounted(false);
        wifi =
                ((WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE))
                        .createWifiLock(WifiManager.WIFI_MODE_FULL, "grapefruit-podcast-download");
        wifi.setReferenceCounted(false);
        store.recoverTransfers();
    }

    @Override
    public synchronized int onStartCommand(Intent i, int flags, int startId) {
        startForeground(NOTIFICATION, notice("Preparing podcast downloads", null, null));
        if (worker == null || !worker.isAlive()) {
            stopping = false;
            worker = new Thread(() -> work(), "gf-podcast-download");
            worker.start();
        }
        return START_STICKY;
    }

    private int waitingReason() {
        if (!Http.isOnline(this)) return 2;
        return Prefs.wifiOnlyDownloads() && !Http.isOnWifi(this) ? 3 : 0;
    }

    private void work() {
        Notification last = null;
        boolean loopFinished = false;
        cpu.acquire();
        wifi.acquire();
        try {
            while (!stopping && !Thread.currentThread().isInterrupted()) {
                PodcastStore.Transfer t = store.nextTransfer();
                if (t == null) break;
                currentId = t.item.id;
                int wait = waitingReason();
                if (wait != 0) {
                    store.updateTransfer(t, PodcastDownload.PAUSED, 0, -1, wait, null);
                    last =
                            notice(
                                    "Podcast downloads paused",
                                    new PodcastDownload(PodcastDownload.PAUSED, wait, 0, -1),
                                    null);
                    break;
                }
                try {
                    boolean complete = transfer(t);
                    if (complete) last = notice("Podcast download finished", null, t.item.title);
                } catch (Exception e) {
                    if (store.currentTransfer(t)) {
                        int reason = stopping ? 1 : waitingReason();
                        int state = reason != 0 ? PodcastDownload.PAUSED : PodcastDownload.FAILED;
                        String error = reason == 0 ? e.getMessage() : null;
                        store.updateTransfer(t, state, 0, -1, reason, error);
                        last =
                                notice(
                                        state == PodcastDownload.PAUSED
                                                ? "Podcast downloads paused"
                                                : "Podcast download failed",
                                        new PodcastDownload(state, reason, 0, -1, error),
                                        t.item.title);
                        if (state == PodcastDownload.PAUSED) break;
                    }
                } finally {
                    currentId = null;
                }
            }
            loopFinished = true;
        } finally {
            if (wifi.isHeld()) wifi.release();
            if (cpu.isHeld()) cpu.release();
            final Notification result = last;
            final boolean mayRestart = loopFinished;
            synchronized (this) {
                worker = null;
            }
            com.grapefruitmusic.util.Bg.main(
                    () -> {
                        synchronized (PodcastDownloadService.this) {
                            if (stopping || (worker != null && worker.isAlive())) return;
                            if (mayRestart && waitingReason() == 0 && store.hasPendingTransfers()) {
                                onStartCommand(null, 0, 0);
                                return;
                            }
                            stopForeground(true);
                            if (result != null)
                                ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
                                        .notify(NOTIFICATION, result);
                            stopSelf();
                        }
                    });
        }
    }

    private boolean transfer(PodcastStore.Transfer t) throws IOException {
        File dest = store.destination(t.item);
        if (!dest.getParentFile().isDirectory() && !dest.getParentFile().mkdirs())
            throw new IOException("Download storage unavailable");
        File part = new File(dest.getPath() + "." + t.generation + ".part");
        HttpURLConnection c = null;
        long received = 0, total = -1;
        try {
            c =
                    PodcastAudio.openObserved(
                            t.item.streamUrl,
                            null,
                            opened -> {
                                connection = opened;
                                if (stopping || !store.currentTransfer(t))
                                    throw new IOException("Download canceled");
                            });
            int code = c.getResponseCode();
            if (code != 200) throw new IOException("Audio server returned HTTP " + code);
            String type = c.getContentType();
            if (type != null
                    && (type.toLowerCase(Locale.US).contains("text/html")
                            || type.toLowerCase(Locale.US).contains("application/xhtml")))
                throw new IOException("Audio server returned a web page");
            try {
                total = Long.parseLong(c.getHeaderField("Content-Length"));
            } catch (Exception ignored) {
            }
            store.updateTransfer(t, PodcastDownload.RUNNING, 0, total, 0, null);
            startForeground(
                    NOTIFICATION,
                    notice(
                            "Downloading podcast",
                            new PodcastDownload(PodcastDownload.RUNNING, 0, 0, total),
                            t.item.title));
            InputStream in = c.getInputStream();
            try {
                OutputStream out = new FileOutputStream(part);
                try {
                    final long[] last = {0};
                    received =
                            PodcastAudio.copy(
                                    in,
                                    out,
                                    total,
                                    (bytes, length) -> {
                                        if (stopping || Thread.currentThread().isInterrupted())
                                            throw new IOException("Download interrupted");
                                        long now = System.currentTimeMillis();
                                        if (now - last[0] >= 750) {
                                            if (!store.currentTransfer(t))
                                                throw new IOException("Download canceled");
                                            if (waitingReason() != 0)
                                                throw new IOException("Connection changed");
                                            last[0] = now;
                                            store.updateTransfer(
                                                    t,
                                                    PodcastDownload.RUNNING,
                                                    bytes,
                                                    length,
                                                    0,
                                                    null);
                                            startForeground(
                                                    NOTIFICATION,
                                                    notice(
                                                            "Downloading podcast",
                                                            new PodcastDownload(
                                                                    PodcastDownload.RUNNING,
                                                                    0,
                                                                    bytes,
                                                                    length),
                                                            t.item.title));
                                        }
                                    });
                } finally {
                    out.close();
                }
            } finally {
                in.close();
            }
            return store.finishTransfer(t, part);
        } finally {
            connection = null;
            if (c != null) c.disconnect();
            part.delete();
        }
    }

    private Notification notice(String title, PodcastDownload p, String episode) {
        Intent open = new Intent(this, MainActivity.class).putExtra("open_podcast_downloads", true);
        Notification.Builder b =
                Platform.notification(this, "podcast_downloads", "Podcast Downloads")
                        .setSmallIcon(R.drawable.ic_stat_grapefruit)
                        .setContentTitle(title)
                        .setContentText(p == null ? episode : p.text())
                        .setContentIntent(
                                PendingIntent.getActivity(
                                        this,
                                        NOTIFICATION,
                                        open,
                                        Platform.pendingFlags(PendingIntent.FLAG_UPDATE_CURRENT)))
                        .setOnlyAlertOnce(true)
                        .setAutoCancel(p == null || !p.active());
        if (p != null && p.status == PodcastDownload.RUNNING) {
            b.setProgress(100, Math.max(0, p.percent()), p.percent() < 0).setOngoing(true);
            if (episode != null) b.setContentTitle(episode);
        }
        return b.build();
    }

    @Override
    public void onDestroy() {
        stopping = true;
        if (connection != null) connection.disconnect();
        if (worker != null) worker.interrupt();
        if (live == this) live = null;
        super.onDestroy();
    }

    @Override
    public void onTimeout(int startId, int fgsType) {
        stopping = true;
        stopSelf();
    }

    @Override
    public IBinder onBind(Intent i) {
        return null;
    }
}
