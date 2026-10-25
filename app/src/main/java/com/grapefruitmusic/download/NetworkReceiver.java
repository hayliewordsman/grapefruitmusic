package com.grapefruitmusic.download;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.grapefruitmusic.net.Http;
import com.grapefruitmusic.util.Prefs;

/** Resumes queued downloads when a (Wi-Fi) connection comes back. */
public final class NetworkReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        boolean ok = Prefs.wifiOnlyDownloads() ? Http.isOnWifi(context) : Http.isOnline(context);
        if (ok) Downloads.get(context).startWorker();
    }
}
