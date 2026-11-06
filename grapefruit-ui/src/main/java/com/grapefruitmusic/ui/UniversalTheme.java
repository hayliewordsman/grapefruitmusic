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
