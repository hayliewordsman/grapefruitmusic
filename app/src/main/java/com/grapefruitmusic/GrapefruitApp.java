package com.grapefruitmusic;

import android.app.Application;
import android.util.Log;

import com.grapefruitmusic.download.Downloads;
import com.grapefruitmusic.net.StreamProxy;
import com.grapefruitmusic.net.Tls;
import com.grapefruitmusic.playback.Player;
import com.grapefruitmusic.util.Bg;
import com.grapefruitmusic.util.Prefs;

import java.io.IOException;

public final class GrapefruitApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Prefs.init(this);
        Tls.install(this);
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
        Downloads.get(this).startWorker();
        // Karaoke Mode left on: have it ready before the first song.
        if (Prefs.karaoke()) Player.prepareKaraoke(this);
    }
}
