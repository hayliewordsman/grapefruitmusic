package com.grapefruitmusic;

import android.app.Application;
import android.util.Log;

import com.grapefruitmusic.download.Downloads;
import com.grapefruitmusic.net.StreamProxy;
import com.grapefruitmusic.net.Tls;
import com.grapefruitmusic.playback.Player;
import com.grapefruitmusic.ui.AppIcon;
import com.grapefruitmusic.util.Bg;
import com.grapefruitmusic.util.Prefs;

import java.io.IOException;

public final class GrapefruitApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Prefs.init(this);
        Tls.install(this);
        com.grapefruitmusic.podcast.PodcastRefresh.schedule(this);
        // Manifest connectivity broadcasts are not delivered to modern target SDKs. Keep a
        // process-scoped receiver for paused transfers; it does not keep the app process alive.
        android.content.IntentFilter network = new android.content.IntentFilter("android.net.conn.CONNECTIVITY_CHANGE");
        android.content.BroadcastReceiver receiver = new android.content.BroadcastReceiver() {
            @Override public void onReceive(android.content.Context c, android.content.Intent i) {
                if (com.grapefruitmusic.net.Http.isOnline(c))
                    com.grapefruitmusic.podcast.PodcastStore.get(c).resumeDownloads();
            }
        };
        if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, network, android.content.Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(receiver, network);

        // The launcher icon in the theme's colours (after an update, or a theme picked last time).
        Bg.execute(new Runnable() {
            @Override
            public void run() {
                AppIcon.match(GrapefruitApp.this);
            }
        });
        Bg.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    StreamProxy.get().ensureStarted();
                } catch (IOException e) {
                    Log.w("Grapefruit", "stream relay failed to start", e);
                }
            }
        });
        // Pick up downloads interrupted last time.
        // Queued downloads resume when the activity is visible.
        // Karaoke Mode left on: have it ready before the first song.
        if (Prefs.karaoke()) Player.prepareKaraoke(this);
    }
}
