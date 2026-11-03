package com.grapefruitfast;
import android.app.Application;
import com.grapefruitmusic.net.Tls;
import com.grapefruitmusic.ui.Fonts;
public final class FastApp extends Application {
    @Override public void onCreate() {
        super.onCreate();
        // First, so a fault anywhere after this leaves a report to share (CrashReport).
        CrashReport.install(this);
        Tls.install(this);
        Fonts.init(this);
    }
}
