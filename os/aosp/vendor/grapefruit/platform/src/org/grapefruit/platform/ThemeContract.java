// SPDX-License-Identifier: Apache-2.0
package org.grapefruit.platform;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

/** Same read-only authority and canonical IDs as the existing universal-theme provider. */
public final class ThemeContract {
    public static final Uri URI = Uri.parse("content://io.github.hayliewordsman.grapefruitsettings.theme/theme");
    public static final String[] NAMES = {"Grapefruit Dark", "Grapefruit Light", "Pitch Black", "Victory Blue"};
    private ThemeContract() {}
    public static int read(Context context) {
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(URI, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int id = cursor.getInt(cursor.getColumnIndexOrThrow("theme"));
                if (id >= 0 && id < NAMES.length) return id;
            }
        } catch (RuntimeException unavailable) {
            // Settings may not yet be running during early SystemUI startup.
        } finally { if (cursor != null) cursor.close(); }
        return 0;
    }
}
