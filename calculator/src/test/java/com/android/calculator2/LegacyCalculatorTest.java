package com.android.calculator2;

import android.content.Context;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.TextView;
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
@Config(sdk={17,19}, qualifiers="w360dp-h640dp-port",shadows=LegacyCalculatorTest.SwitcherModel.class)
public class LegacyCalculatorTest {
    // Robolectric 4.8's ViewAnimator shadow tracks the child separately from the framework's
    // ViewSwitcher. Keep getNextView consistent with its current-child model.
    @org.robolectric.annotation.Implements(android.widget.ViewSwitcher.class)
    public static class SwitcherModel extends org.robolectric.shadows.ShadowViewAnimator {
        @org.robolectric.annotation.RealObject android.widget.ViewSwitcher view;
        @org.robolectric.annotation.Implementation protected View getNextView() {
            return view.getChildAt(view.getDisplayedChild()==0?1:0);
        }
    }
    private Calculator open() {
        Calculator a=Robolectric.buildActivity(Calculator.class).setup().visible().get();
        View root=a.getWindow().getDecorView();
        root.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(640,View.MeasureSpec.EXACTLY));
        root.layout(0,0,360,640);
        return a;
    }
    private void click(Calculator a,int...ids){for(int id:ids)assertTrue(a.findViewById(id).performClick());}
    private String display(Calculator a) {
        CalculatorDisplay display=(CalculatorDisplay)a.findViewById(R.id.display);
        return ((TextView)display.getCurrentView()).getText().toString();
    }
    @Test public void arithmeticAndDeleteKeepAospBehavior() {
        Calculator a=open();
        click(a,R.id.digit2,R.id.plus,R.id.digit3,R.id.mul,R.id.digit4,R.id.equal);
        assertEquals("14",display(a));
        click(a,R.id.clear,R.id.digit8,R.id.digit9,R.id.del);
        assertEquals("8",display(a));
    }
    @Test public void scientificPanelAndHistorySurviveRecreation() {
        ActivityController<Calculator> controller=Robolectric.buildActivity(Calculator.class).setup().visible();
        Calculator a=controller.get();
        click(a,R.id.digit9,R.id.mul,R.id.digit9,R.id.equal);
        controller.pause().stop().destroy();
        a=open();assertEquals("81",display(a));
        assertNotNull(a.findViewById(R.id.sin));
        assertNotNull(a.findViewById(R.id.sqrt));
    }
    @Test public void allFourThemesStyleBothPanels() {
        Context app=RuntimeEnvironment.getApplication();
        for(int theme=0;theme<4;theme++) {
            PreferenceManager.getDefaultSharedPreferences(app).edit()
                .putString(GrapefruitTheme.PREF_THEME,""+theme).commit();
            Calculator a=open();GrapefruitTheme palette=new GrapefruitTheme(a);
            assertEquals(theme,palette.id);
            assertEquals(palette.text,((TextView)a.findViewById(R.id.digit1)).getCurrentTextColor());
            assertEquals(palette.onAccent,((TextView)a.findViewById(R.id.equal)).getCurrentTextColor());
            assertEquals(palette.text,((TextView)a.findViewById(R.id.sin)).getCurrentTextColor());
            a.finish();
        }
    }
    @Test @Config(qualifiers="w640dp-h360dp-land") public void landscapeKeepsControls() {
        Calculator a=open();click(a,R.id.digit6,R.id.div,R.id.digit2,R.id.equal);assertEquals("3",display(a));
    }
    @Test @Config(qualifiers="sw600dp-w800dp-h1024dp-port") public void tabletKeepsScientificKeys() {
        Calculator a=open();click(a,R.id.digit2,R.id.plus,R.id.digit2,R.id.equal);assertEquals("4",display(a));
        assertNotNull(a.findViewById(R.id.sin));
        assertEquals(View.VISIBLE,a.findViewById(R.id.overflow_menu).getVisibility());
    }
    @Test public void themePickerPersistsChoice() {
        Calculator a=open();CalculatorAppearance.chooseTheme(a);
        android.app.AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
        android.widget.ListView list=dialog.getListView();
        list.performItemClick(null,3,list.getAdapter().getItemId(3));
        assertEquals(3,new GrapefruitTheme(a).id);
    }
}
