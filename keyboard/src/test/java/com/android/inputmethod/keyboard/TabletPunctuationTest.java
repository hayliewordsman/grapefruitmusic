package com.android.inputmethod.keyboard;

import android.content.Context;
import android.content.res.Configuration;
import android.text.InputType;
import android.view.ContextThemeWrapper;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodSubtype;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.SubtypeLocale;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={17,19},qualifiers="sw600dp-w600dp-h960dp-port",shadows=LegacyKeyboardTest.NativeProximity.class)
public class TabletPunctuationTest {
    private void check(int formFactor) {
        Context c=new ContextThemeWrapper(RuntimeEnvironment.getApplication(),R.style.KeyboardTheme_IceCreamSandwich);
        SubtypeLocale.init(c);
        for(boolean languageSwitch:new boolean[]{false,true}) {
            EditorInfo editor=new EditorInfo();editor.inputType=InputType.TYPE_CLASS_TEXT;
            InputMethodSubtype subtype=new InputMethodSubtype(0,0,"en_US","keyboard","KeyboardLayoutSet=qwerty,AsciiCapable",false,false);
            KeyboardLayoutSet.clearKeyboardCache();
            KeyboardLayoutSet set=new KeyboardLayoutSet.Builder(c,editor)
                .setScreenGeometry(formFactor,c.getResources().getConfiguration().orientation,c.getResources().getDisplayMetrics().widthPixels)
                .setSubtype(subtype).setOptions(false,false,languageSwitch).build();
            Keyboard letters=set.getKeyboard(KeyboardId.ELEMENT_ALPHABET);
            assertNotNull(letters.getKey(','));assertNotNull(letters.getKey('.'));
            assertNull(letters.getKey('\''));assertNull(letters.getKey('-'));
            assertNotNull(letters.getKey(32));
            for(Key key:letters.mKeys) assertTrue(key.toString(),key.mX+key.mWidth<=letters.mOccupiedWidth+1);
            Keyboard symbols=set.getKeyboard(KeyboardId.ELEMENT_SYMBOLS);
            assertNotNull(symbols.getKey('\''));assertNotNull(symbols.getKey('-'));
            assertEquals(symbols.getKey(32).mY,symbols.getKey(',').mY);
            assertEquals(symbols.getKey(32).mY,symbols.getKey('.').mY);
            assertTrue(symbols.getKey('-').mMoreKeys.length>=2);
        }
    }
    @Test public void sevenInchTabletPlacesApostropheAndDashOnSymbols() { check(1); }
    @Test @Config(qualifiers="sw768dp-w1024dp-h768dp-land") public void largeTabletKeepsTheSamePunctuationSplit() { check(2); }
}
