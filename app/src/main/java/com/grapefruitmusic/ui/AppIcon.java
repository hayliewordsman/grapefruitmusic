package com.grapefruitmusic.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

import com.grapefruitmusic.R;
import com.grapefruitmusic.util.Prefs;

/**
 * Grapefruit's icon in the theme's colours. Android 4.2 can't change an app's launcher icon, so
 * the manifest has a launcher entry (an activity-alias) for each theme with its own icon, and
 * only the theme's is enabled. Launchers keep icons by entry, so the new one shows fresh rather
 * than from their cache.
 */
public final class AppIcon {
    /** The launcher entries, by Prefs.THEME_* (from THEME_STARLIGHT). */
    private static final String[] ENTRIES = {"StarlightIcon", "PitchBlackIcon", "GrapefruitLightIcon",
            "GrapefruitDarkIcon"};
    private static final int[] LAUNCHER = {R.mipmap.ic_launcher_starlight, R.mipmap.ic_launcher_pitch_black,
            R.mipmap.ic_launcher_grapefruit_light, R.mipmap.ic_launcher_grapefruit_dark};
    private static final int[] ABOUT = {R.drawable.ic_about_starlight, R.drawable.ic_about_pitch_black,
            R.drawable.ic_about_grapefruit_light, R.drawable.ic_about_grapefruit_dark};
    /** The entry the manifest enables. */
    private static final int MANIFEST_THEME = Prefs.THEME_GRAPEFRUIT_DARK;

    private AppIcon() {
    }

    /** The launcher icon for the theme showing. */
    public static int launcher(Context c) {
        return LAUNCHER[Ui.theme(c) - Prefs.THEME_STARLIGHT];
    }

    /** The About page's (larger) icon for the theme showing. */
    public static int about(Context c) {
        return ABOUT[Ui.theme(c) - Prefs.THEME_STARLIGHT];
    }

    /** The launcher entry for a theme (a Prefs.THEME_* other than THEME_SYSTEM). */
    public static ComponentName entry(Context c, int theme) {
        // Named in the manifest's package, whatever the app's id.
        return new ComponentName(c.getPackageName(), "com.grapefruitmusic." + ENTRIES[theme - Prefs.THEME_STARLIGHT]);
    }

    /**
     * Turns on the launcher entry of the theme showing and off the others, if they aren't already.
     * The new one goes on first, so a launcher never finds Grapefruit without one.
     *
     * DONT_KILL_APP keeps Grapefruit, and whatever it's playing, going; for that Android 4.2 tells
     * launchers about the change 10 seconds later instead of at once. A launcher may also take the
     * old icon off the home screen, as the entry it points to has gone.
     */
    public static void match(Context c) {
        PackageManager pm = c.getPackageManager();
        int theme = Ui.theme(c);
        set(c, pm, theme, true);
        for (int t = Prefs.THEME_STARLIGHT; t <= Prefs.THEME_GRAPEFRUIT_DARK; t++) {
            if (t != theme) set(c, pm, t, false);
        }
    }

    /** Is the theme's launcher entry on? */
    public static boolean enabled(Context c, int theme) {
        int state = c.getPackageManager().getComponentEnabledSetting(entry(c, theme));
        return state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                || (state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && theme == MANIFEST_THEME);
    }

    private static void set(Context c, PackageManager pm, int theme, boolean on) {
        if (enabled(c, theme) == on) return;
        pm.setComponentEnabledSetting(entry(c, theme), on ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                : PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
    }
}
