// SPDX-License-Identifier: GPL-3.0-or-later
package com.grapefruitmaps.barlow;

import android.app.Application;
import com.grapefruitmusic.net.Tls;
import com.grapefruitmusic.ui.Fonts;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;

public final class MapsApp extends Application {
    @Override public void onCreate() {
        super.onCreate();
        Tls.install(this);
        Fonts.init(this);
        AndroidGraphicFactory.createInstance(this);
    }
}
