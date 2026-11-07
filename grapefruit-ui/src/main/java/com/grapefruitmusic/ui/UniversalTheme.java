// SPDX-License-Identifier: GPL-3.0-or-later
package com.grapefruitmusic.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;

/** Optional, read-only theme contract. Missing Settings app never changes an app's own theme. */
public final class UniversalTheme {
    public static final Uri URI = Uri.parse("content://io.github.hayliewordsman.grapefruitsettings.theme/theme");
    private UniversalTheme() {}

    /** Refresh a visible Activity immediately; release the observer with its decor window. */
    public static void watch(final android.app.Activity activity, final SharedPreferences prefs,
                             final boolean media, final int[] mapping) {
        final android.view.View decor=activity.getWindow().getDecorView();
        final android.database.ContentObserver observer=new android.database.ContentObserver(new android.os.Handler()) {
            @Override public void onChange(boolean self) {
                if(sync(activity,prefs,media,mapping) && !activity.isFinishing())activity.recreate();
            }
        };
        decor.addOnAttachStateChangeListener(new android.view.View.OnAttachStateChangeListener() {
            private boolean registered;
            public void onViewAttachedToWindow(android.view.View v) {
                if(!registered){activity.getContentResolver().registerContentObserver(URI,false,observer);registered=true;}
                observer.onChange(false);
            }
            public void onViewDetachedFromWindow(android.view.View v) {
                if(registered){activity.getContentResolver().unregisterContentObserver(observer);registered=false;}
            }
        });
    }

    /** Canonical IDs: Dark=0, Light=1, Black=2, Blue=3; mappings are client preference IDs. */
    public static boolean sync(Context c, SharedPreferences prefs, boolean media, int[] mapping) {
        try (Cursor cursor = c.getContentResolver().query(URI, null, null, null, null)) {
            if (cursor == null || !cursor.moveToFirst()) return false;
            String value = cursor.getString(cursor.getColumnIndexOrThrow(media ? "media_theme" : "theme"));
            String revision = cursor.getString(cursor.getColumnIndexOrThrow(media ? "media_revision" : "revision"));
            if (value == null || revision == null || revision.equals(prefs.getString("universal_revision", ""))) return false;
            int id = Integer.parseInt(value);
            if (id < 0 || id >= mapping.length || mapping[id] < 0) return false;
            boolean changed = prefs.getInt("theme", -1) != mapping[id];
            prefs.edit().putInt("theme", mapping[id]).putString("universal_revision", revision).commit();
            return changed;
        } catch (RuntimeException unavailable) {
            // Optional companion absent, inaccessible or incompatible: preserve the local choice.
            return false;
        }
    }
}
