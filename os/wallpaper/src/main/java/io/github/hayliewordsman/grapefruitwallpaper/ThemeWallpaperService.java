// SPDX-License-Identifier: Apache-2.0
package io.github.hayliewordsman.grapefruitwallpaper;

import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;
import com.grapefruit.os.theme.GrapefruitPalette;

/** Paints its own live wallpaper surface. Never changes a user's wallpaper or lock photo. */
public final class ThemeWallpaperService extends WallpaperService {
    private static final Uri URI = Uri.parse(
            "content://io.github.hayliewordsman.grapefruitsettings.theme/theme");
    @Override public Engine onCreateEngine() { return new ThemeEngine(); }
    private final class ThemeEngine extends Engine {
        private boolean visible;
        private final ContentObserver observer = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override public void onChange(boolean selfChange) { if (visible) draw(); }
        };
        @Override public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            getContentResolver().registerContentObserver(URI, false, observer);
        }
        @Override public void onVisibilityChanged(boolean value) { visible = value; if (value) draw(); }
        @Override public void onSurfaceChanged(SurfaceHolder h, int format, int width, int height) { draw(); }
        @Override public void onDestroy() {
            getContentResolver().unregisterContentObserver(observer);
            super.onDestroy();
        }
        private void draw() {
            int id = getResources().getInteger(R.integer.grapefruit_default_theme);
            try (Cursor c = getContentResolver().query(URI, new String[]{"theme"}, null, null, null)) {
                if (c != null && c.moveToFirst()) id = c.getInt(0);
            } catch (RuntimeException locked) { /* Edition default before Settings becomes available. */ }
            SurfaceHolder holder = getSurfaceHolder();
            if (!holder.getSurface().isValid()) return;
            Canvas canvas = null;
            try {
                canvas = holder.lockCanvas();
                if (canvas != null) canvas.drawColor(GrapefruitPalette.background(id));
            } finally { if (canvas != null) holder.unlockCanvasAndPost(canvas); }
        }
    }
}
