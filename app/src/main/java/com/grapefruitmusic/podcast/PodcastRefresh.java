package com.grapefruitmusic.podcast;

import android.app.*;
import android.app.job.*;
import android.content.*;
import android.os.Build;

import com.grapefruitmusic.R;
import com.grapefruitmusic.compat.Platform;
import com.grapefruitmusic.model.MediaItem;
import com.grapefruitmusic.net.Http;
import com.grapefruitmusic.ui.MainActivity;
import com.grapefruitmusic.util.Prefs;

/** Inexact periodic refresh, not a push service. No exact alarms or Google services required. */
public final class PodcastRefresh extends BroadcastReceiver {
    private static final int JOB = 4001;

    public static void schedule(Context c) {
        if (Build.VERSION.SDK_INT >= 21) {
            JobScheduler jobs = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            jobs.schedule(
                    new JobInfo.Builder(JOB, new ComponentName(c, PodcastJob.class))
                            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                            .setPeriodic(6 * 60 * 60 * 1000L)
                            .setPersisted(true)
                            .build());
        } else {
            PendingIntent pending =
                    PendingIntent.getBroadcast(
                            c,
                            JOB,
                            new Intent(c, PodcastRefresh.class).setAction("refresh"),
                            Platform.pendingFlags(PendingIntent.FLAG_UPDATE_CURRENT));
            ((AlarmManager) c.getSystemService(Context.ALARM_SERVICE))
                    .setInexactRepeating(
                            AlarmManager.RTC_WAKEUP,
                            System.currentTimeMillis() + 6 * 60 * 60 * 1000L,
                            6 * 60 * 60 * 1000L,
                            pending);
        }
    }

    @Override
    public void onReceive(Context c, Intent i) {
        if ("refresh".equals(i.getAction()) && Build.VERSION.SDK_INT < 21)
            c.startService(new Intent(c, LegacyPodcastRefresh.class));
        else schedule(c);
    }

    static void refresh(Context c) {
        if (!Http.isOnline(c)) return;
        PodcastStore store = PodcastStore.get(c);
        int count = 0;
        for (MediaItem show : store.subscriptions()) {
            if (Thread.currentThread().isInterrupted()) break;
            try {
                count += store.refresh(show);
            } catch (java.io.IOException e) {
                android.util.Log.w("GrapefruitPodcasts", "Feed refresh failed", e);
            }
        }
        if (Thread.currentThread().isInterrupted()
                || count <= 0
                || !Prefs.raw().getBoolean("podcast_notifications", true)) return;
        if (Build.VERSION.SDK_INT >= 33
                && c.checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                        != android.content.pm.PackageManager.PERMISSION_GRANTED) return;
        Intent open = new Intent(c, MainActivity.class).putExtra("open_podcasts", true);
        Notification n =
                Platform.notification(c, "podcasts", "New podcast episodes")
                        .setSmallIcon(R.drawable.ic_stat_grapefruit)
                        .setContentTitle(
                                count == 1
                                        ? "New podcast episode"
                                        : count + " new podcast episodes")
                        .setContentText("Open your podcast subscriptions to listen.")
                        .setAutoCancel(true)
                        .setContentIntent(
                                PendingIntent.getActivity(
                                        c,
                                        JOB,
                                        open,
                                        Platform.pendingFlags(PendingIntent.FLAG_UPDATE_CURRENT)))
                        .build();
        ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).notify(JOB, n);
    }
}
