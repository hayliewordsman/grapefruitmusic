package com.android.inputmethod.keyboard;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.AudioManager;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;
import com.android.inputmethod.latin.AudioAndHapticFeedbackManager;
import com.android.inputmethod.latin.LatinIME;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.Settings;
import com.android.inputmethod.latin.SettingsActivity;
import com.android.inputmethod.latin.SettingsValues;
import com.grapefruit.legacy.GrapefruitTheme;
import com.grapefruit.legacy.ui.Toggle;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.Implementation;
import org.robolectric.shadows.ShadowDialog;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={17,19},qualifiers="w360dp-h640dp-port",shadows={LegacyKeyboardTest.NativeProximity.class,GrapefruitSettingsTest.AudioSpy.class})
public class GrapefruitSettingsTest {
    @Implements(AudioManager.class)
    public static class AudioSpy extends org.robolectric.shadows.ShadowAudioManager {
        static int clicks;
        @Implementation protected void playSoundEffect(int effect,float volume) {clicks++;}
    }
    private View row(SettingsActivity a,String key) {
        ListView list=(ListView)a.getWindow().getDecorView().findViewWithTag("grapefruit-settings-list");
        for(int i=0;i<list.getAdapter().getCount();i++) {
            Object p=list.getAdapter().getItem(i);
            if(p instanceof Preference && key.equals(((Preference)p).getKey())) {
                return list.getAdapter().getView(i,null,list).findViewWithTag("pref:"+key);
            }
        }
        throw new AssertionError("Missing setting "+key);
    }
    private Toggle toggle(View view) {
        if(view instanceof Toggle)return (Toggle)view;
        if(view instanceof android.view.ViewGroup) for(int i=0;i<((android.view.ViewGroup)view).getChildCount();i++) {
            Toggle found=toggle(((android.view.ViewGroup)view).getChildAt(i));if(found!=null)return found;
        }
        return null;
    }
    @Test public void audioStyleSoundToggleAndChoicesPersistWithoutRecreatingSettings() {
        ActivityController<SettingsActivity> controller=Robolectric.buildActivity(SettingsActivity.class).setup().visible();
        SettingsActivity a=controller.get();
        assertEquals("Settings",((TextView)a.findViewById(R.id.grapefruit_settings_title)).getText().toString());
        Toggle sound=toggle(row(a,Settings.PREF_SOUND_ON));assertNotNull(sound);
        boolean initial=sound.isChecked();sound.performClick();
        assertEquals(!initial,PreferenceManager.getDefaultSharedPreferences(a).getBoolean(Settings.PREF_SOUND_ON,initial));
        row(a,GrapefruitTheme.PREF_THEME).performClick();
        Dialog chooser=ShadowDialog.getLatestDialog();assertNotNull(chooser.findViewById(android.R.id.content).findViewWithTag("choice:3"));
        chooser.findViewById(android.R.id.content).findViewWithTag("choice:3").performClick();
        assertEquals(3,new GrapefruitTheme(a).id);assertFalse(a.isDestroyed());
        assertEquals(new GrapefruitTheme(a).bg,((ColorDrawable)a.getWindow().getDecorView().findViewWithTag("grapefruit-settings-list").getBackground()).getColor());
        controller.pause().stop().destroy();
        controller=Robolectric.buildActivity(SettingsActivity.class).setup().visible();
        assertEquals(!initial,toggle(row(controller.get(),Settings.PREF_SOUND_ON)).isChecked());
        controller.pause().stop().destroy();
    }
    @Test public void advancedSettingsUseTheSameRowsAndBackKeepsTrialText() {
        ActivityController<SettingsActivity> controller=Robolectric.buildActivity(SettingsActivity.class).setup().visible();
        SettingsActivity a=controller.get();((TextView)a.findViewById(R.id.grapefruit_trial)).setText("keep this text");
        ListView list=(ListView)a.getWindow().getDecorView().findViewWithTag("grapefruit-settings-list");
        for(int i=0;i<list.getAdapter().getCount();i++) {
            Object item=list.getAdapter().getItem(i);
            if(item instanceof Preference && Settings.PREF_ADVANCED_SETTINGS.equals(((Preference)item).getKey())) {
                list.performItemClick(row(a,Settings.PREF_ADVANCED_SETTINGS),i,i);break;
            }
        }
        assertEquals(a.getString(R.string.advanced_settings),((TextView)a.findViewById(R.id.grapefruit_settings_title)).getText().toString());
        assertNotNull(toggle(row(a,Settings.PREF_SHOW_LANGUAGE_SWITCH_KEY)));
        a.onBackPressed();
        assertEquals("keep this text",((TextView)a.findViewById(R.id.grapefruit_trial)).getText().toString());
        assertEquals(View.VISIBLE,a.findViewById(R.id.grapefruit_trial).getVisibility());
        controller.pause().stop().destroy();
    }
    private TextView text(View view,String value) {
        if(view instanceof TextView && value.contentEquals(((TextView)view).getText()))return (TextView)view;
        if(view instanceof android.view.ViewGroup) for(int i=0;i<((android.view.ViewGroup)view).getChildCount();i++) {
            TextView found=text(((android.view.ViewGroup)view).getChildAt(i),value);if(found!=null)return found;
        }
        return null;
    }
    @Test public void volumeCardPreservesAndRestoresSystemDefaultAndCancelDoesNotSave() {
        android.content.SharedPreferences prefs=PreferenceManager.getDefaultSharedPreferences(RuntimeEnvironment.getApplication());
        prefs.edit().putBoolean(Settings.PREF_SOUND_ON,true).putFloat(Settings.PREF_KEYPRESS_SOUND_VOLUME,-1f).commit();
        ActivityController<SettingsActivity> controller=Robolectric.buildActivity(SettingsActivity.class).setup().visible();
        SettingsActivity a=controller.get();row(a,Settings.PREF_ADVANCED_SETTINGS).performClick();
        row(a,Settings.PREF_KEYPRESS_SOUND_VOLUME).performClick();Dialog dialog=ShadowDialog.getLatestDialog();
        text(dialog.getWindow().getDecorView(),a.getString(android.R.string.ok)).performClick();
        assertEquals(-1f,prefs.getFloat(Settings.PREF_KEYPRESS_SOUND_VOLUME,0),0);
        row(a,Settings.PREF_KEYPRESS_SOUND_VOLUME).performClick();dialog=ShadowDialog.getLatestDialog();
        android.widget.SeekBar slider=(android.widget.SeekBar)dialog.getWindow().getDecorView().findViewWithTag("grapefruit-slider");
        assertTrue(slider.onKeyDown(android.view.KeyEvent.KEYCODE_DPAD_RIGHT,new android.view.KeyEvent(0,android.view.KeyEvent.KEYCODE_DPAD_RIGHT)));
        text(dialog.getWindow().getDecorView(),a.getString(android.R.string.cancel)).performClick();
        assertEquals(-1f,prefs.getFloat(Settings.PREF_KEYPRESS_SOUND_VOLUME,0),0);
        row(a,Settings.PREF_KEYPRESS_SOUND_VOLUME).performClick();dialog=ShadowDialog.getLatestDialog();
        slider=(android.widget.SeekBar)dialog.getWindow().getDecorView().findViewWithTag("grapefruit-slider");
        slider.onKeyDown(android.view.KeyEvent.KEYCODE_DPAD_RIGHT,new android.view.KeyEvent(0,android.view.KeyEvent.KEYCODE_DPAD_RIGHT));
        text(dialog.getWindow().getDecorView(),a.getString(android.R.string.ok)).performClick();
        assertTrue(prefs.getFloat(Settings.PREF_KEYPRESS_SOUND_VOLUME,-1)>0);
        row(a,Settings.PREF_KEYPRESS_SOUND_VOLUME).performClick();dialog=ShadowDialog.getLatestDialog();
        dialog.getWindow().getDecorView().findViewWithTag("grapefruit-system-default").performClick();
        text(dialog.getWindow().getDecorView(),a.getString(android.R.string.ok)).performClick();
        assertEquals(-1f,prefs.getFloat(Settings.PREF_KEYPRESS_SOUND_VOLUME,0),0);
        controller.pause().stop().destroy();
    }
    @Test public void inputStyleFormUsesAudioChoicesAndCanCancelOnJellyBean() throws Exception {
        Context c=RuntimeEnvironment.getApplication();android.content.pm.ResolveInfo service=new android.content.pm.ResolveInfo();
        service.serviceInfo=c.getPackageManager().getServiceInfo(new android.content.ComponentName(c,LatinIME.class),
                android.content.pm.PackageManager.GET_META_DATA);
        android.view.inputmethod.InputMethodInfo ime=new android.view.inputmethod.InputMethodInfo(c,service);
        android.view.inputmethod.InputMethodManager manager=(android.view.inputmethod.InputMethodManager)c.getSystemService(Context.INPUT_METHOD_SERVICE);
        org.robolectric.Shadows.shadowOf(manager).setInputMethodInfoList(java.util.Collections.singletonList(ime));
        android.content.Intent intent=new android.content.Intent().putExtra(android.preference.PreferenceActivity.EXTRA_SHOW_FRAGMENT,
                com.android.inputmethod.latin.AdditionalSubtypeSettings.class.getName());
        ActivityController<SettingsActivity> controller=Robolectric.buildActivity(SettingsActivity.class,intent).setup().visible();
        SettingsActivity a=controller.get();text(a.getWindow().getDecorView(),a.getString(R.string.add_style)).performClick();
        Dialog form=ShadowDialog.getLatestDialog();assertTrue(form instanceof com.grapefruit.legacy.ui.SettingsDialog);
        assertNotNull(text(form.getWindow().getDecorView(),a.getString(R.string.add)));
        text(form.getWindow().getDecorView(),a.getString(android.R.string.cancel)).performClick();assertFalse(form.isShowing());
        controller.pause().stop().destroy();
    }
    @Test public void disablingSoundsMutesAnAlreadyConstructedFeedbackManagerAndRespectsRingerMode() {
        LatinIME service=Robolectric.buildService(LatinIME.class).get();
        android.content.SharedPreferences prefs=PreferenceManager.getDefaultSharedPreferences(service);
        prefs.edit().putBoolean(Settings.PREF_SOUND_ON,true).putBoolean(Settings.PREF_VIBRATE_ON,false).commit();
        AudioManager audio=(AudioManager)service.getSystemService(Context.AUDIO_SERVICE);
        audio.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
        AudioAndHapticFeedbackManager feedback=new AudioAndHapticFeedbackManager(service,new SettingsValues(prefs,null,service));
        AudioSpy.clicks=0;feedback.hapticAndAudioFeedback('a',null);assertEquals(1,AudioSpy.clicks);
        prefs.edit().putBoolean(Settings.PREF_SOUND_ON,false).commit();
        feedback.hapticAndAudioFeedback('b',null);assertEquals(1,AudioSpy.clicks);
        prefs.edit().putBoolean(Settings.PREF_SOUND_ON,true).commit();
        audio.setRingerMode(AudioManager.RINGER_MODE_SILENT);
        feedback.hapticAndAudioFeedback('c',null);assertEquals(1,AudioSpy.clicks);
        audio.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
        feedback.hapticAndAudioFeedback('d',null);assertEquals(2,AudioSpy.clicks);
    }
}
