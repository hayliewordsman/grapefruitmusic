package com.grapefruitmusic.compat;

import android.app.*;
import android.content.*;
import android.os.Build;

/** Version-gated Android services; the API 17 path remains deliberately small. */
public final class Platform {
    private Platform() {}

    public static int pendingFlags(int flags) {
        return Build.VERSION.SDK_INT >= 23 ? flags | PendingIntent.FLAG_IMMUTABLE : flags;
    }

    public static Notification.Builder notification(Context c, String channel, String name) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm =
                    (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.createNotificationChannel(
                    new NotificationChannel(channel, name, NotificationManager.IMPORTANCE_LOW));
            return new Notification.Builder(c, channel);
        }
        return new Notification.Builder(c);
    }

    public static void service(Context c, Intent intent) {
        try {
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(intent);
            else c.startService(intent);
        } catch (IllegalStateException e) {
            // Modern Android can reject a background start; the durable queue retries when visible.
            android.util.Log.w(
                    "GrapefruitServices", "Background start deferred until the app is opened", e);
        }
    }
}
