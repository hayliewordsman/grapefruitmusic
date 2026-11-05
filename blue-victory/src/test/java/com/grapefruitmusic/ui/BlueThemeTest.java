package com.grapefruitmusic.ui;

import android.app.Activity;
import android.app.Application;
import android.graphics.Color;
import com.grapefruitmusic.R;
import com.grapefruitmusic.util.Prefs;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {23, 28, 35, 37}, application = Application.class)
public class BlueThemeTest {
    @Test public void allOldThemePreferencesResolveToBlue() {
        Application app = RuntimeEnvironment.getApplication();
        Prefs.init(app);
        com.grapefruitmusic.util.Brand.init(app);
        assertEquals("Blue Grapefruit", app.getString(R.string.app_name));
        assertEquals("Blue Grapefruit", app.getString(R.string.launcher_name));
        assertEquals("Blue Grapefruit and Blue Grapefruit", com.grapefruitmusic.util.Brand.text(
                "Grapefruit Audio and Grapefruit TV & Movies"));
        assertEquals("Blue Grapefruit's sound", com.grapefruitmusic.util.Brand.text("Grapefruit's sound"));
        assertEquals("Blue Grapefruit", com.grapefruitmusic.util.Brand.text("Blue Grapefruit"));
        for (int theme = 0; theme <= 4; theme++) {
            Prefs.setTheme(theme);
            try (org.robolectric.android.controller.ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class)) {
                Activity activity = controller.get();
                Ui.applyTheme(activity);
                controller.setup();
                assertEquals(Prefs.THEME_GRAPEFRUIT_DARK, Ui.theme(activity));
                assertEquals(Color.parseColor("#07162B"), Ui.color(activity, R.attr.gfBackground));
                assertEquals(Color.parseColor("#67C7FF"), Ui.color(activity, R.attr.gfAccent));
                assertEquals(Color.parseColor("#EAF4FF"), Ui.color(activity, R.attr.gfTextPrimary));
            }
        }
    }
}
