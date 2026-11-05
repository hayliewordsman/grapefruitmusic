package com.android.inputmethod.keyboard;

import android.content.Context;
import android.content.res.Configuration;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.view.ContextThemeWrapper;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodSubtype;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.SettingsActivity;
import com.android.inputmethod.latin.SubtypeLocale;
import com.grapefruit.legacy.GrapefruitTheme;
import com.grapefruit.legacy.KeyHeight;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.Implementation;
import static org.junit.Assert.*;

/** Native proximity calls are supplied by the APK on devices; JVM tests verify layout geometry. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk={17,19},qualifiers="w360dp-h640dp-port",shadows=LegacyKeyboardTest.NativeProximity.class)
public class LegacyKeyboardTest {
    @Implements(ProximityInfo.class)
    public static class NativeProximity {
        @Implementation protected long setProximityInfoNative(String locale,int max,int width,
            int height,int gridWidth,int gridHeight,int keyWidth,int[] neighbors,int count,
            int[] xs,int[] ys,int[] widths,int[] heights,int[] codes,float[] sx,float[] sy,float[] sr) {
            return 1;
        }
        @Implementation protected void releaseProximityInfoNative(long pointer) {}
    }
    private Context context() {
        Context c=new ContextThemeWrapper(RuntimeEnvironment.getApplication(),R.style.KeyboardTheme_IceCreamSandwich);
        SubtypeLocale.init(c);return c;
    }
    public static Keyboard build(Context c,int inputType,int element) {
        EditorInfo info=new EditorInfo();info.inputType=inputType;
        InputMethodSubtype subtype=new InputMethodSubtype(0,0,"en_US","keyboard","KeyboardLayoutSet=qwerty,AsciiCapable",false,false);
        return new KeyboardLayoutSet.Builder(c,info).setScreenGeometry(0,
            c.getResources().getConfiguration().orientation,c.getResources().getDisplayMetrics().widthPixels)
            .setSubtype(subtype).setOptions(false,false,true).build().getKeyboard(element);
    }
    @Test public void heightChangesRealKeysAndHitGeometry() {
        Context c=context();
        KeyboardLayoutSet.clearKeyboardCache();Keyboard base=build(c,InputType.TYPE_CLASS_TEXT,KeyboardId.ELEMENT_ALPHABET);
        PreferenceManager.getDefaultSharedPreferences(c).edit().putString(KeyHeight.PREF,"140").commit();
        KeyboardLayoutSet.clearKeyboardCache();Keyboard tall=build(c,InputType.TYPE_CLASS_TEXT,KeyboardId.ELEMENT_ALPHABET);
        assertTrue(tall.mOccupiedHeight>base.mOccupiedHeight);
        assertTrue(tall.getKey('q').mHeight>base.getKey('q').mHeight);
        assertTrue(tall.getKey('z').mY>base.getKey('z').mY);
        Key z=tall.getKey('z');
        assertTrue(java.util.Arrays.asList(tall.getNearestKeys(z.mX+z.mWidth/2,z.mY+z.mHeight/2)).contains(z));
    }
    @Test public void numericAndPasswordLayoutsKeepTheirKeys() {
        Context c=context();KeyboardLayoutSet.clearKeyboardCache();
        Keyboard number=build(c,InputType.TYPE_CLASS_NUMBER,KeyboardId.ELEMENT_ALPHABET);
        assertNotNull(number.getKey('1'));
        Keyboard password=build(c,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD,KeyboardId.ELEMENT_ALPHABET);
        assertTrue(password.mId.passwordInput());assertNotNull(password.getKey('q'));
    }
    @Test public void allFourPalettesAndInvalidHeightPreferencesAreSafe() {
        Context c=context();
        for(int i=0;i<4;i++) {
            PreferenceManager.getDefaultSharedPreferences(c).edit().putString(GrapefruitTheme.PREF_THEME,""+i).commit();
            assertEquals(i,new GrapefruitTheme(c).id);
        }
        PreferenceManager.getDefaultSharedPreferences(c).edit().putString(KeyHeight.PREF,"broken").commit();
        assertEquals(100,KeyHeight.percent(c));
        PreferenceManager.getDefaultSharedPreferences(c).edit().putString(KeyHeight.PREF,"9999").commit();
        assertEquals(150,KeyHeight.percent(c));
    }
    @Test public void launcherSettingsCanOpen() {
        SettingsActivity a=Robolectric.buildActivity(SettingsActivity.class).setup().visible().get();
        assertNotNull(a.findViewById(android.R.id.list));
    }
    @Test public void settingsUseOnlyTheirOwnFragmentsOnBothLegacyVersions() {
        android.content.Intent intent=new android.content.Intent().putExtra(
            android.preference.PreferenceActivity.EXTRA_SHOW_FRAGMENT,"com.android.inputmethod.research.FeedbackFragment");
        SettingsActivity a=Robolectric.buildActivity(SettingsActivity.class,intent).setup().get();
        assertEquals("com.android.inputmethod.latin.Settings",a.getIntent().getStringExtra(
            android.preference.PreferenceActivity.EXTRA_SHOW_FRAGMENT));
        android.app.Activity spell=Robolectric.buildActivity(
            com.android.inputmethod.latin.spellcheck.SpellCheckerSettingsActivity.class).setup().get();
        assertNotNull(spell.findViewById(android.R.id.list));
    }
    @Test public void languageLayoutsAndLongPressAlternatesRemainAvailable() {
        Context c=context();
        String[][] layouts={{"en_US","qwerty"},{"fr","azerty"},{"de","qwertz"},{"ru","east_slavic"},{"ar","arabic"}};
        for(String[] language:layouts) {
            EditorInfo info=new EditorInfo();info.inputType=InputType.TYPE_CLASS_TEXT;
            InputMethodSubtype subtype=new InputMethodSubtype(0,0,language[0],"keyboard",
                "KeyboardLayoutSet="+language[1],false,false);
            KeyboardLayoutSet.clearKeyboardCache();
            Keyboard k=new KeyboardLayoutSet.Builder(c,info).setScreenGeometry(0,Configuration.ORIENTATION_PORTRAIT,360)
                .setSubtype(subtype).setOptions(false,false,true).build().getKeyboard(KeyboardId.ELEMENT_ALPHABET);
            assertTrue(language[0],k.mKeys.length>20);
        }
        KeyboardLayoutSet.clearKeyboardCache();
        assertTrue(build(c,InputType.TYPE_CLASS_TEXT,KeyboardId.ELEMENT_ALPHABET).getKey('e').mMoreKeys.length>0);
    }
    @Test @Config(qualifiers="w640dp-h360dp-land") public void tallestLandscapeFitsScreen() {
        Context c=context();PreferenceManager.getDefaultSharedPreferences(c).edit().putString(KeyHeight.PREF,"150").commit();
        KeyboardLayoutSet.clearKeyboardCache();Keyboard k=build(c,InputType.TYPE_CLASS_TEXT,KeyboardId.ELEMENT_ALPHABET);
        assertTrue(k.mOccupiedHeight<c.getResources().getDisplayMetrics().heightPixels);
        assertNotNull(k.getKey(32));
    }
}
