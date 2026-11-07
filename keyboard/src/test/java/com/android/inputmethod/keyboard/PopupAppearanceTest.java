package com.android.inputmethod.keyboard;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.SubtypeLocale;
import com.grapefruit.legacy.GrapefruitTheme;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={17,19},qualifiers="sw600dp-w1024dp-h768dp-land",shadows=LegacyKeyboardTest.NativeProximity.class)
public class PopupAppearanceTest {
    @Test public void suggestionSideFillsMatchEveryPaletteAtNonzeroTabletPadding() {
        Context c=new ContextThemeWrapper(RuntimeEnvironment.getApplication(),R.style.KeyboardTheme_IceCreamSandwich);
        for(int id=0;id<4;id++) {
            PreferenceManager.getDefaultSharedPreferences(c).edit().putString(GrapefruitTheme.PREF_THEME,""+id).commit();
            View root=LayoutInflater.from(c).inflate(R.layout.input_view,null);
            ViewGroup strip=(ViewGroup)root.findViewById(R.id.suggestions_container);
            assertTrue(strip.getChildAt(0).getLayoutParams().width>0);
            assertEquals(new GrapefruitTheme(c).bg,((ColorDrawable)strip.getBackground()).getColor());
            for(int edge:new int[]{0,2}) {
                assertEquals(0,Color.alpha(((ColorDrawable)strip.getChildAt(edge).getBackground()).getColor()));
            }
            assertEquals(0,Color.alpha(((ColorDrawable)root.getBackground()).getColor()));
        }
    }
    @Test @Config(qualifiers="w360dp-h640dp-port") public void paddedLongPressPanelsFitAtBothKeyboardEdgesAndKeepDefaultKeyUnderFinger() {
        Context c=new ContextThemeWrapper(RuntimeEnvironment.getApplication(),R.style.KeyboardTheme_IceCreamSandwich);
        SubtypeLocale.init(c);KeyboardLayoutSet.clearKeyboardCache();
        Keyboard keyboard=LegacyKeyboardTest.build(c,InputType.TYPE_CLASS_TEXT,KeyboardId.ELEMENT_ALPHABET);
        View root=LayoutInflater.from(c).inflate(R.layout.input_view,null);
        MainKeyboardView main=(MainKeyboardView)root.findViewById(R.id.keyboard_view);main.setKeyboard(keyboard);
        main.setKeyPreviewPopupEnabled(false,0);
        main.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.AT_MOST));
        for(char code:new char[]{'q','e','p'}) {
            Key parent=keyboard.getKey(code);
            MoreKeysKeyboardView popup=(MoreKeysKeyboardView)main.onCreateMoreKeysPanel(parent);
            View panel=(View)popup.getParent();MoreKeysKeyboard pane=(MoreKeysKeyboard)popup.getKeyboard();
            int gap=new GrapefruitTheme(c).dp(8);
            assertEquals(gap,panel.getPaddingLeft());assertEquals(gap,panel.getPaddingRight());
            assertEquals(gap,panel.getPaddingTop());assertEquals(gap,panel.getPaddingBottom());
            assertEquals(0,Color.alpha(((ColorDrawable)popup.getBackground()).getColor()));
            int point=parent.mX+parent.mWidth/2;
            int left=MoreKeysKeyboardView.clampPanelX(point-pane.getDefaultCoordX()-gap,360,panel.getMeasuredWidth());
            assertTrue("left edge",left>=0);assertTrue("right edge",left+panel.getMeasuredWidth()<=360);
            KeyDetector detector=new KeyDetector(0);detector.setKeyboard(pane,0,0);
            Key centered=detector.detectHitKey(pane.getDefaultCoordX(),pane.mOccupiedHeight-pane.mMostCommonKeyHeight/2);
            assertNotNull(centered);
            assertSame(centered,detector.detectHitKey(point-left-gap,pane.mOccupiedHeight-pane.mMostCommonKeyHeight/2));
        }
    }
}
