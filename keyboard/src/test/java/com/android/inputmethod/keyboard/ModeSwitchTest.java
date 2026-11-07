package com.android.inputmethod.keyboard;

import android.content.Context;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import com.android.inputmethod.keyboard.internal.KeyboardState;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.SubtypeLocale;
import com.grapefruit.legacy.KeyboardLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** Real pointer down/up events drive the AOSP state machine and parsed keyboard geometry. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk={17,19},qualifiers="sw600dp-w600dp-h800dp-port",shadows=LegacyKeyboardTest.NativeProximity.class)
public class ModeSwitchTest {
    private static long time=1000;
    private static final class Session implements KeyboardState.SwitchActions,PointerTracker.KeyEventHandler {
        final Context c;
        final MainKeyboardView view;
        final KeyboardState state;
        boolean numberPad;
        final StringBuilder typed=new StringBuilder();
        static boolean paneKey(int code) {
            return code==Keyboard.CODE_GRAPEFRUIT_SYMBOLS || code==Keyboard.CODE_GRAPEFRUIT_RETURN_ALPHA
                    || code==Keyboard.CODE_GRAPEFRUIT_NUMBER_PAD;
        }
        Session(boolean numbers) {
            c=new ContextThemeWrapper(RuntimeEnvironment.getApplication(),R.style.KeyboardTheme_IceCreamSandwich);
            SubtypeLocale.init(c);
            PreferenceManager.getDefaultSharedPreferences(c).edit().putBoolean(KeyboardLayout.PREF_NUMBER_ROW,numbers).commit();
            KeyboardLayoutSet.clearKeyboardCache();
            view=(MainKeyboardView)LayoutInflater.from(c).inflate(R.layout.input_view,null).findViewById(R.id.keyboard_view);
            PointerTracker.init(true,false);view.setKeyPreviewPopupEnabled(false,0);
            state=new KeyboardState(this);
            view.setKeyboardActionListener(new KeyboardActionListener.Adapter() {
                @Override public void onPressKey(int code) {if(!numberPad && !paneKey(code))state.onPressKey(code,true,0);}
                @Override public void onCodeInput(int code,int x,int y) {
                    if(code==Keyboard.CODE_GRAPEFRUIT_SYMBOLS) {numberPad=false;state.onSwitchToSymbols();}
                    else if(code==Keyboard.CODE_GRAPEFRUIT_RETURN_ALPHA) {numberPad=false;state.onSwitchToAlphabet();}
                    else if(code==Keyboard.CODE_GRAPEFRUIT_NUMBER_PAD) {numberPad=true;show(KeyboardId.ELEMENT_NUMBER);}
                    else {
                        if(code>0)typed.append((char)code);
                        if(!numberPad)state.onCodeInput(code,true,0);
                    }
                }
                @Override public void onReleaseKey(int code,boolean sliding) {if(!numberPad && !paneKey(code))state.onReleaseKey(code,sliding);}
                @Override public void onCancelInput() {if(!numberPad)state.onCancelInput(true);}
            });
            state.onLoadKeyboard("");
        }
        @Override public KeyDetector getKeyDetector() {return view.getKeyDetector();}
        @Override public KeyboardActionListener getKeyboardActionListener() {return view.getKeyboardActionListener();}
        @Override public PointerTracker.DrawingProxy getDrawingProxy() {return view;}
        @Override public PointerTracker.TimerProxy getTimerProxy() {return new PointerTracker.TimerProxy.Adapter();}
        void show(int element) {view.setKeyboard(LegacyKeyboardTest.build(c,InputType.TYPE_CLASS_TEXT,element));}
        void symbols() {
            state.onPressKey(Keyboard.CODE_SWITCH_ALPHA_SYMBOL,true,0);
            state.onCodeInput(Keyboard.CODE_SWITCH_ALPHA_SYMBOL,true,0);
            state.onReleaseKey(Keyboard.CODE_SWITCH_ALPHA_SYMBOL,false);
        }
        Key mode(boolean right) {
            Keyboard k=view.getKeyboard();Key result=null;
            for(Key key:k.mKeys) if(!key.isSpacer() && key.mY==k.getKey(32).mY) {
                if(result==null || (right?key.mX>result.mX:key.mX<result.mX))result=key;
            }
            return result;
        }
        void tap(Key key) {
            PointerTracker tracker=PointerTracker.getPointerTracker(0,this);
            int x=key.mX+key.mWidth/2,y=key.mY+key.mHeight/2;
            Keyboard before=view.getKeyboard();
            time+=200;tracker.processMotionEvent(MotionEvent.ACTION_DOWN,x,y,time,this);
            if(paneKey(key.mCode))assertSame("Mode button must not reflow under the finger",before,view.getKeyboard());
            tracker.processMotionEvent(MotionEvent.ACTION_UP,x,y,time+40,this);
        }
        @Override public void setAlphabetKeyboard() {show(KeyboardId.ELEMENT_ALPHABET);}
        @Override public void setAlphabetManualShiftedKeyboard() {show(KeyboardId.ELEMENT_ALPHABET_MANUAL_SHIFTED);}
        @Override public void setAlphabetAutomaticShiftedKeyboard() {show(KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED);}
        @Override public void setAlphabetShiftLockedKeyboard() {show(KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCKED);}
        @Override public void setAlphabetShiftLockShiftedKeyboard() {show(KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCK_SHIFTED);}
        @Override public void setSymbolsKeyboard() {show(KeyboardId.ELEMENT_SYMBOLS);}
        @Override public void setSymbolsShiftedKeyboard() {show(KeyboardId.ELEMENT_SYMBOLS_SHIFTED);}
        @Override public void requestUpdatingShiftState() {state.onUpdateShiftState(0);}
        @Override public void startDoubleTapTimer() {}
        @Override public boolean isInDoubleTapTimeout() {return false;}
        @Override public void cancelDoubleTapTimer() {}
        @Override public void startLongPressTimer(int code) {}
        @Override public void cancelLongPressTimer() {}
        @Override public void hapticAndAudioFeedback(int code) {}
    }
    @Test public void abcReturnsToLettersWithNumberRow() {
        Session s=new Session(true);s.symbols();s.tap(s.mode(false));
        assertTrue("ABC must remain on letters after the finger lifts",s.view.getKeyboard().mId.isAlphabetKeyboard());
    }
    private void roundTrips() {
        for(boolean numbers:new boolean[]{false,true}) for(String height:new String[]{"70","100","150"}) {
            PreferenceManager.getDefaultSharedPreferences(RuntimeEnvironment.getApplication()).edit()
                    .putString(com.grapefruit.legacy.KeyHeight.PREF,height).commit();
            for(boolean right:new boolean[]{false,true}) {
                Session s=new Session(numbers);
                for(int i=0;i<3;i++) {
                    s.tap(s.mode(right));assertEquals(KeyboardId.ELEMENT_SYMBOLS,s.view.getKeyboard().mId.mElementId);
                    s.tap(s.view.getKeyboard().getKey('1'));
                    s.tap(s.mode(right));assertTrue(s.view.getKeyboard().mId.isAlphabetKeyboard());
                    s.tap(s.view.getKeyboard().getKey('q'));
                }
                assertEquals("1q1q1q",s.typed.toString());
            }
        }
    }
    @Test public void repeatedLeftAndRightModeTapsRemainStableOnTablets() {roundTrips();}
    @Test @Config(qualifiers="w360dp-h640dp-port") public void repeatedModeTapsRemainStableOnPhones() {roundTrips();}
    @Test @Config(qualifiers="sw768dp-w1024dp-h768dp-land") public void repeatedModeTapsRemainStableInLandscape() {roundTrips();}
    @Test public void abcReturnsFromSecondarySymbolsAndNumberPad() {
        for(boolean numbers:new boolean[]{false,true}) {
            Session s=new Session(numbers);s.tap(s.mode(false));
            s.tap(s.view.getKeyboard().getKey(Keyboard.CODE_SHIFT));
            assertEquals(KeyboardId.ELEMENT_SYMBOLS_SHIFTED,s.view.getKeyboard().mId.mElementId);
            s.tap(s.mode(true));assertTrue(s.view.getKeyboard().mId.isAlphabetKeyboard());
            s.tap(s.mode(true));assertEquals(KeyboardId.ELEMENT_SYMBOLS,s.view.getKeyboard().mId.mElementId);
            s.tap(s.view.getKeyboard().getKey(Keyboard.CODE_GRAPEFRUIT_NUMBER_PAD));
            s.tap(s.view.getKeyboard().getKey('1'));s.tap(s.view.getKeyboard().getKey('2'));
            s.tap(s.view.getKeyboard().getKey(Keyboard.CODE_GRAPEFRUIT_RETURN_ALPHA));
            assertTrue(s.view.getKeyboard().mId.isAlphabetKeyboard());s.tap(s.view.getKeyboard().getKey('q'));
            assertEquals("12q",s.typed.toString());
        }
    }

}
