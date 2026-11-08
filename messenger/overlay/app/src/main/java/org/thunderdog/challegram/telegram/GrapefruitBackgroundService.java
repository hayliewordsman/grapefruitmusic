/*
 * This file is a part of Grapefruitgram Messenger, based on Telegram X.
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Notifications without a push service. Grapefruitgram has no Firebase; it uses UnifiedPush where a
 * distributor is installed. Android 4.x phones usually have none, and then Telegram X would only learn
 * of new messages while the app is open. While no push service works, this low-priority foreground
 * service keeps the process, and so each account's connection to Telegram, alive, and new messages
 * arrive as they do with the app open. It stops by itself once a push service works.
 */
package org.thunderdog.challegram.telegram;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.unsorted.AppContext;

public class GrapefruitBackgroundService extends Service {
  private static final String CHANNEL_ID = "grapefruit_background_connection";
  private static final int NOTIFICATION_ID = 0x47524150; // "GRAP"

  private static volatile boolean running;

  /** Starts or stops the service as the push service state requires. Any thread, any context. */
  public static void check (Context context) {
    if (context == null)
      return;
    final Context app = context.getApplicationContext();
    boolean wanted;
    try {
      wanted = isWanted();
    } catch (Throwable t) {
      Log.w("Unable to check background connection state", t);
      return;
    }
    if (wanted == running)
      return;
    Intent intent = new Intent(app, GrapefruitBackgroundService.class);
    try {
      if (wanted) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          app.startForegroundService(intent);
        } else {
          app.startService(intent);
        }
      } else {
        app.stopService(intent);
      }
    } catch (Throwable t) {
      // Android 12+ refuses foreground services started from the background; the next time the
      // app is opened (BaseActivity.onResume) tries again.
      Log.w("Unable to change background connection", t);
    }
  }

  /** Wanted while the push service has failed and some account is signed in. */
  private static boolean isWanted () {
    TdlibManager manager = TdlibManager.instance();
    if (manager.getTokenState() != TdlibManager.TokenState.ERROR)
      return false;
    for (TdlibAccount account : manager) {
      if (!account.isUnauthorized() && !account.isService()) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  @Override
  public IBinder onBind (Intent intent) {
    return null;
  }

  @Override
  public void onCreate () {
    super.onCreate();
    AppContext.init(getApplicationContext());
    running = true;
    startForeground(NOTIFICATION_ID, buildNotification());
  }

  @Override
  public int onStartCommand (Intent intent, int flags, int startId) {
    if (!isWanted()) {
      stopSelf();
      return START_NOT_STICKY;
    }
    // Open every signed-in account: without a registered push token, TDLib keeps them connected.
    TdlibManager manager = TdlibManager.instance();
    for (TdlibAccount account : manager) {
      if (!account.isUnauthorized() && !account.isService()) {
        manager.setKeepAlive(account.id, true);
      }
    }
    return START_STICKY;
  }

  @Override
  public void onDestroy () {
    running = false;
    super.onDestroy();
  }

  private Notification buildNotification () {
    String title = Lang.getString(R.string.GrapefruitBackgroundConnection);
    String text = Lang.getString(R.string.GrapefruitBackgroundConnectionDesc);
    Notification.Builder builder;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
      NotificationChannel channel = new NotificationChannel(CHANNEL_ID, title, NotificationManager.IMPORTANCE_MIN);
      channel.setShowBadge(false);
      manager.createNotificationChannel(channel);
      builder = new Notification.Builder(this, CHANNEL_ID);
    } else {
      builder = new Notification.Builder(this);
      // Lowest priority: no status bar icon, listed at the bottom of the shade.
      builder.setPriority(Notification.PRIORITY_MIN);
    }
    Intent open = getPackageManager().getLaunchIntentForPackage(getPackageName());
    if (open != null) {
      int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0;
      builder.setContentIntent(PendingIntent.getActivity(this, 0, open, flags));
    }
    return builder
      .setSmallIcon(R.mipmap.app_notification)
      .setContentTitle(title)
      .setContentText(text)
      .setOngoing(true)
      .setShowWhen(false)
      .build();
  }
}
