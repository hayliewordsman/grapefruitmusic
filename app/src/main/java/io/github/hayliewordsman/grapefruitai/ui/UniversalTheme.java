package io.github.hayliewordsman.grapefruitai.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;

/**
 * Grapefruit Settings' public, read-only theme contract (as in the shared grapefruit-ui and the
 * legacy Holo apps). IDs: Dark=0, Light=1, Black=2, Blue=3. A local choice lasts until Settings
 * publishes a new revision. A missing companion never changes the app's own theme.
 */
public final class UniversalTheme {
    public static final Uri URI = Uri.parse("content://io.github.hayliewordsman.grapefruitsettings.theme/theme");
    static final String PREF_REVISION = "universal_revision";

    private UniversalTheme() {
    }

    /** True if the theme changed. */
    public static boolean sync(Context context, SharedPreferences prefs) {
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(URI, null, null, null, null);
            if (cursor == null || !cursor.moveToFirst()) return false;
            String value = cursor.getString(cursor.getColumnIndexOrThrow("theme"));
            String revision = cursor.getString(cursor.getColumnIndexOrThrow("revision"));
            if (value == null || revision == null || revision.length() == 0) return false;
            if (revision.equals(prefs.getString(PREF_REVISION, ""))) return false;
            int choice = Integer.parseInt(value);
            if (choice < 0 || choice >= Palette.NAMES.length) return false;
            boolean changed = prefs.getInt("theme", Palette.GRAPEFRUIT_DARK) != choice;
            prefs.edit().putInt("theme", choice).putString(PREF_REVISION, revision).commit();
            return changed;
        } catch (RuntimeException unavailable) {
            return false;
        } finally {
            if (cursor != null) {
                try {
                    cursor.close();
                } catch (RuntimeException ignored) {
                    // Optional provider.
                }
            }
        }
    }
}
