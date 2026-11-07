package com.android.inputmethod.keyboard;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.preference.CheckBoxPreference;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import com.android.inputmethod.latin.InputView;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.Settings;
import com.android.inputmethod.latin.SettingsActivity;
import com.android.inputmethod.latin.SubtypeLocale;
import com.grapefruit.legacy.GrapefruitTheme;
import com.grapefruit.legacy.KeyboardLayout;
import com.grapefruit.legacy.KeyHeight;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={17,19},qualifiers="w360dp-h640dp-port",shadows=LegacyKeyboardTest.NativeProximity.class)
public class ReferenceLayoutTest {
    private Context context() {
        Context c=new ContextThemeWrapper(RuntimeEnvironment.getApplication(),R.style.KeyboardTheme_IceCreamSandwich);
        SubtypeLocale.init(c);return c;
    }
    private Keyboard keyboard(Context c,int element) {
        KeyboardLayoutSet.clearKeyboardCache();
        return LegacyKeyboardTest.build(c,InputType.TYPE_CLASS_TEXT,element);
    }
    private void centeredSpaceAndEmptyGutters(Keyboard k) {
        Key space=k.getKey(32);
        assertEquals(k.mOccupiedWidth/2f,space.mX+space.mWidth/2f,2f);
        KeyDetector detector=new KeyDetector(0);detector.setKeyboard(k,0,0);
        int y=space.mY+space.mHeight/2;
        assertSame(space,detector.detectHitKey(space.mX+space.mWidth/2,y));
        assertNull("left gutter must not type a space",detector.detectHitKey(space.mHitBox.left-3,y));
        assertNull("right gutter must not type a space",detector.detectHitKey(space.mHitBox.right+3,y));
        for(Key key:k.mKeys) {
            assertTrue(key.toString(),key.mX+key.mWidth<=k.mOccupiedWidth+1);
            assertTrue(key.toString(),key.mY+key.mHeight<=k.mOccupiedHeight+1);
        }
    }
    private void checkReference() {
        Context c=context();
        for(boolean numberRow:new boolean[]{false,true}) {
            PreferenceManager.getDefaultSharedPreferences(c).edit().putBoolean(KeyboardLayout.PREF_NUMBER_ROW,numberRow).commit();
            Keyboard letters=keyboard(c,KeyboardId.ELEMENT_ALPHABET);
            centeredSpaceAndEmptyGutters(letters);
            assertEquals(letters.getKey('q').mY,letters.getKey(Keyboard.CODE_DELETE).mY);
            assertEquals(letters.getKey('a').mY,letters.getKey(10).mY);
            assertEquals(letters.getKey('z').mY,letters.getKey('!').mY);
            assertEquals(letters.getKey('z').mY,letters.getKey('?').mY);
            assertEquals(2,letters.mShiftKeys.length);
            assertEquals(letters.getKey(32).mY,letters.getKey(',').mY);
            assertEquals(letters.getKey(32).mY,letters.getKey('.').mY);
            assertNull(letters.getKey('\''));assertNull(letters.getKey('-'));
            if(numberRow) for(char digit='0';digit<='9';digit++) assertTrue(letters.getKey(digit).mY<letters.getKey('q').mY);
            else assertNull(letters.getKey('1'));
            Keyboard symbols=keyboard(c,KeyboardId.ELEMENT_SYMBOLS);
            centeredSpaceAndEmptyGutters(symbols);
            assertEquals(symbols.getKey('1').mY,symbols.getKey(Keyboard.CODE_DELETE).mY);
            assertEquals(symbols.getKey('@').mY,symbols.getKey('-').mY);
            assertEquals(symbols.getKey('@').mY,symbols.getKey(10).mY);
            assertEquals(symbols.getKey('\\').mY,symbols.getKey('\'').mY);
            assertEquals(symbols.getKey(32).mY,symbols.getKey(',').mY);
            assertEquals(symbols.getKey(32).mY,symbols.getKey('.').mY);
            assertTrue(symbols.getKey('-').mMoreKeys.length>=2);
            centeredSpaceAndEmptyGutters(keyboard(c,KeyboardId.ELEMENT_SYMBOLS_SHIFTED));
        }
    }
    @Test public void phoneMatchesReferenceArrangement() { checkReference(); }
    @Test @Config(qualifiers="sw600dp-w600dp-h800dp-port") public void smallTabletMatchesReferenceArrangement() { checkReference(); }
    @Test @Config(qualifiers="sw768dp-w1024dp-h768dp-land") public void largeTabletMatchesReferenceArrangement() { checkReference(); }
    @Test public void numberRowAddsHeightAndKeepsHeightSettingWorking() {
        Context c=context();Keyboard base=keyboard(c,KeyboardId.ELEMENT_ALPHABET);
        PreferenceManager.getDefaultSharedPreferences(c).edit().putBoolean(KeyboardLayout.PREF_NUMBER_ROW,true).commit();
        Keyboard numbers=keyboard(c,KeyboardId.ELEMENT_ALPHABET);
        assertTrue(numbers.mOccupiedHeight>base.mOccupiedHeight);
        assertEquals(base.getKey('q').mHeight,numbers.getKey('q').mHeight,3);
        PreferenceManager.getDefaultSharedPreferences(c).edit().putString(KeyHeight.PREF,"130").commit();
        assertTrue(keyboard(c,KeyboardId.ELEMENT_ALPHABET).getKey('q').mHeight>numbers.getKey('q').mHeight);
        Keyboard shifted=keyboard(c,KeyboardId.ELEMENT_ALPHABET_MANUAL_SHIFTED);
        assertNotNull(shifted.getKey('Q'));assertNotNull(shifted.getKey('1'));
    }
    @Test @Config(qualifiers="w640dp-h360dp-land") public void numberRowAtMaximumHeightFitsLandscape() {
        Context c=context();PreferenceManager.getDefaultSharedPreferences(c).edit()
                .putBoolean(KeyboardLayout.PREF_NUMBER_ROW,true).putString(KeyHeight.PREF,"150").commit();
        Keyboard k=keyboard(c,KeyboardId.ELEMENT_ALPHABET);
        assertTrue(k.mOccupiedHeight+96*c.getResources().getDisplayMetrics().density<=c.getResources().getDisplayMetrics().heightPixels+1);
        centeredSpaceAndEmptyGutters(k);
    }
    @Test public void numberPadHasDigitsAndWorkingReturnCodeWithoutChangingNumericEditors() {
        Context c=context();Keyboard textPad=keyboard(c,KeyboardId.ELEMENT_NUMBER);
        for(char digit='0';digit<='9';digit++) assertNotNull(textPad.getKey(digit));
        assertNotNull(textPad.getKey(Keyboard.CODE_GRAPEFRUIT_RETURN_ALPHA));
        com.android.inputmethod.accessibility.KeyCodeDescriptionMapper.init();
        com.android.inputmethod.accessibility.KeyCodeDescriptionMapper descriptions=
                com.android.inputmethod.accessibility.KeyCodeDescriptionMapper.getInstance();
        assertEquals(c.getString(R.string.spoken_description_to_alpha),descriptions.getDescriptionForKey(
                c,textPad,textPad.getKey(Keyboard.CODE_GRAPEFRUIT_RETURN_ALPHA),false));
        Keyboard symbols=keyboard(c,KeyboardId.ELEMENT_SYMBOLS);
        assertEquals(c.getString(R.string.spoken_description_to_numeric),descriptions.getDescriptionForKey(
                c,symbols,symbols.getKey(Keyboard.CODE_GRAPEFRUIT_NUMBER_PAD),false));
        KeyboardLayoutSet.clearKeyboardCache();
        Keyboard numeric=LegacyKeyboardTest.build(c,InputType.TYPE_CLASS_NUMBER,KeyboardId.ELEMENT_ALPHABET);
        assertNull(numeric.getKey(Keyboard.CODE_GRAPEFRUIT_RETURN_ALPHA));
    }
    @Test public void messagingFieldsKeepEnterAndEditorSendBesideTheHomeRow() {
        Context c=context();
        for(int action:new int[]{android.view.inputmethod.EditorInfo.IME_ACTION_NONE,
                android.view.inputmethod.EditorInfo.IME_ACTION_SEND}) {
            android.view.inputmethod.EditorInfo info=new android.view.inputmethod.EditorInfo();
            info.inputType=InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE;
            info.imeOptions=action;
            android.view.inputmethod.InputMethodSubtype subtype=new android.view.inputmethod.InputMethodSubtype(
                    0,0,"en_US","keyboard","KeyboardLayoutSet=qwerty,AsciiCapable",false,false);
            KeyboardLayoutSet.clearKeyboardCache();
            Keyboard k=new KeyboardLayoutSet.Builder(c,info).setScreenGeometry(0,
                    c.getResources().getConfiguration().orientation,c.getResources().getDisplayMetrics().widthPixels)
                    .setSubtype(subtype).setOptions(false,false,false).build().getKeyboard(KeyboardId.ELEMENT_ALPHABET);
            int code=action==android.view.inputmethod.EditorInfo.IME_ACTION_SEND?Keyboard.CODE_ACTION_ENTER:10;
            assertNotNull(k.getKey(code));
            assertEquals(k.getKey('a').mY,k.getKey(code).mY);
            assertEquals(k.getKey(32).mY,k.getKey(Keyboard.CODE_OUTPUT_TEXT).mY);
        }
    }
    @Test public void transparentPreviewBackingNeverBecomesAnOpaqueSuggestionsBar() {
        Context c=context();
        for(int theme=0;theme<4;theme++) {
            PreferenceManager.getDefaultSharedPreferences(c).edit().putString(GrapefruitTheme.PREF_THEME,""+theme).commit();
            InputView input=(InputView)LayoutInflater.from(c).inflate(R.layout.input_view,null);
            View backing=input.findViewById(R.id.key_preview_backing);
            backing.getLayoutParams().height=270;
            MainKeyboardView keys=(MainKeyboardView)input.findViewById(R.id.keyboard_view);
            keys.setKeyboard(keyboard(c,KeyboardId.ELEMENT_ALPHABET));
            input.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.AT_MOST));
            input.layout(0,0,input.getMeasuredWidth(),input.getMeasuredHeight());
            View strip=input.findViewById(R.id.suggestions_container);
            assertEquals(0,Color.alpha(((ColorDrawable)input.getBackground()).getColor()));
            assertNull(backing.getBackground());
            assertEquals(c.getResources().getDimensionPixelSize(R.dimen.suggestions_strip_height),strip.getHeight());
            assertEquals(new GrapefruitTheme(c).bg,((ColorDrawable)strip.getBackground()).getColor());
            assertEquals(270,strip.getTop());
            assertEquals(strip.getBottom(),keys.getTop());
        }
    }
    @Test public void numberRowSettingPersistsAcrossSettingsReopen() {
        org.robolectric.android.controller.ActivityController<SettingsActivity> a=Robolectric.buildActivity(SettingsActivity.class).setup().visible();
        a.get().getFragmentManager().executePendingTransactions();
        Settings settings=(Settings)a.get().getFragmentManager().findFragmentById(a.get().getResources().getIdentifier("prefs","id","android"));
        CheckBoxPreference numberRow=(CheckBoxPreference)settings.findPreference(KeyboardLayout.PREF_NUMBER_ROW);
        assertFalse(numberRow.isChecked());numberRow.setChecked(true);
        a.pause().stop().destroy();
        a=Robolectric.buildActivity(SettingsActivity.class).setup().visible();
        assertTrue(KeyboardLayout.numberRow(a.get()));a.pause().stop().destroy();
    }
}
