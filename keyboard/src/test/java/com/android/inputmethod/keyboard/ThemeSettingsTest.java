package com.android.inputmethod.keyboard;

import android.app.Fragment;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.PreferenceManager;
import com.android.inputmethod.latin.Settings;
import com.android.inputmethod.latin.SettingsActivity;
import com.grapefruit.legacy.GrapefruitTheme;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={17,19},qualifiers="w360dp-h640dp-port")
public class ThemeSettingsTest {
    private Settings settings(SettingsActivity a) {
        a.getFragmentManager().executePendingTransactions();
        int prefs=a.getResources().getIdentifier("prefs","id","android");
        Fragment fragment=a.getFragmentManager().findFragmentById(prefs);
        assertTrue("Keyboard settings fragment",fragment instanceof Settings);
        return (Settings)fragment;
    }
    @Test public void themeSelectionAndSavedStateReopenAllFourThemes() {
        for(int choice=0;choice<4;choice++) {
            ActivityController<SettingsActivity> controller=Robolectric.buildActivity(SettingsActivity.class).setup().visible();
            ListPreference theme=(ListPreference)settings(controller.get()).findPreference(GrapefruitTheme.PREF_THEME);
            assertEquals(4,theme.getEntries().length);
            theme.setValue(Integer.toString(choice));
            assertEquals(choice,new GrapefruitTheme(controller.get()).id);
            Bundle saved=new Bundle();
            controller.pause().saveInstanceState(saved).stop().destroy();
            ActivityController<SettingsActivity> reopened=Robolectric.buildActivity(SettingsActivity.class).create(saved).start().restoreInstanceState(saved).resume().visible();
            assertEquals(choice,new GrapefruitTheme(reopened.get()).id);
            assertEquals(Integer.toString(choice),((ListPreference)settings(reopened.get())
                    .findPreference(GrapefruitTheme.PREF_THEME)).getValue());
            reopened.pause().stop().destroy();
        }
    }
    @Test public void everySavedThemeCanOpenAfterProcessRestart() {
        for(int choice=0;choice<4;choice++) {
            PreferenceManager.getDefaultSharedPreferences(RuntimeEnvironment.getApplication()).edit()
                    .putString(GrapefruitTheme.PREF_THEME,Integer.toString(choice)).commit();
            ActivityController<SettingsActivity> controller=Robolectric.buildActivity(SettingsActivity.class).setup().visible();
            assertEquals(choice,new GrapefruitTheme(controller.get()).id);
            assertNotNull(settings(controller.get()).findPreference(GrapefruitTheme.PREF_THEME));
            controller.pause().stop().destroy();
        }
    }
    @Test public void selectingVictoryBlueUpdatesTheCurrentSettingsScreen() {
        ActivityController<SettingsActivity> controller=Robolectric.buildActivity(SettingsActivity.class).setup().visible();
        SettingsActivity original=controller.get();
        ListPreference theme=(ListPreference)settings(original).findPreference(GrapefruitTheme.PREF_THEME);
        theme.setValue("3");
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertSame("Theme changes should not recreate the settings activity",original,controller.get());
        assertEquals(3,new GrapefruitTheme(original).id);
        assertFalse(original.isDestroyed());
    }
    @Test public void oldAppearanceTypesAndInvalidValuesDoNotTrapSettingsOnStartup() {
        android.content.SharedPreferences prefs=PreferenceManager.getDefaultSharedPreferences(
                RuntimeEnvironment.getApplication());
        prefs.edit().putInt(GrapefruitTheme.PREF_THEME,3).putBoolean("grapefruit_key_height",true)
                .putString(com.grapefruit.legacy.KeyboardLayout.PREF_NUMBER_ROW,"bad").commit();
        ActivityController<SettingsActivity> controller=Robolectric.buildActivity(SettingsActivity.class).setup().visible();
        assertEquals("3",prefs.getString(GrapefruitTheme.PREF_THEME,null));
        assertEquals("100",prefs.getString("grapefruit_key_height",null));
        assertFalse(prefs.getBoolean(com.grapefruit.legacy.KeyboardLayout.PREF_NUMBER_ROW,true));
        assertEquals(3,new GrapefruitTheme(controller.get()).id);
        controller.pause().stop().destroy();
        prefs.edit().putString(GrapefruitTheme.PREF_THEME,"broken").commit();
        controller=Robolectric.buildActivity(SettingsActivity.class).setup().visible();
        assertEquals(0,new GrapefruitTheme(controller.get()).id);
        controller.pause().stop().destroy();
    }
    @Test public void launcherAndRecentAppTitleUseShortName() {
        ActivityController<SettingsActivity> controller=Robolectric.buildActivity(SettingsActivity.class).setup();
        assertEquals("Grapefruit Keyboard",controller.get().getTitle().toString());
        assertEquals("Grapefruit Keyboard",controller.get().getApplicationInfo().loadLabel(
                controller.get().getPackageManager()).toString());
        controller.pause().stop().destroy();
    }
}
