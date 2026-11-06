// SPDX-License-Identifier: GPL-3.0-or-later
package com.grapefruitmusic.ui;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.provider.Settings;

/** Read-only bridge to stock AOSP Settings on Grapefruit OS; absent on standalone installs. */
public final class OsTheme {
    private OsTheme() {}
    public static int selected(Context context) {
        try (Cursor cursor = context.getContentResolver().query(UniversalTheme.URI,
                new String[]{"os", "theme"}, null, null, null)) {
            if (cursor == null || !cursor.moveToFirst()
                    || cursor.getInt(cursor.getColumnIndexOrThrow("os")) != 1) return -1;
            int theme = cursor.getInt(cursor.getColumnIndexOrThrow("theme"));
            return theme >= 0 && theme < 4 ? theme : -1;
        } catch (RuntimeException unavailable) { return -1; }
    }
    public static boolean openSettings(Context context) {
        if (selected(context) < 0) return false;
        Intent intent = new Intent(Settings.ACTION_DISPLAY_SETTINGS);
        if (!(context instanceof android.app.Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        return true;
    }
}
