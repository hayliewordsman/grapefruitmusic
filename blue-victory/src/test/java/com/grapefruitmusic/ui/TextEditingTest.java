package com.grapefruitmusic.ui;

import android.app.*;
import android.content.*;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import com.grapefruitmusic.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={23,28,35,37},application=Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class TextEditingTest {
    @Test public void longPressSelectCopyAndPasteInAThemedField(){
        Application app=RuntimeEnvironment.getApplication();Prefs.init(app);Fonts.init(app);Brand.init(app);
        try(org.robolectric.android.controller.ActivityController<Activity> controller=Robolectric.buildActivity(Activity.class)){
            Activity a=controller.get();Ui.applyTheme(a);controller.setup().visible();
            EditText field=Ui.field(a,16,false,"Test input");a.setContentView(field);field.setText("Blue Grapefruit");field.requestFocus();field.setSelection(0,4);layout(a);
            field.performLongClick();layout(a);
            assertTrue(field.onTextContextMenuItem(android.R.id.selectAll));assertEquals("Blue Grapefruit",field.getText().subSequence(field.getSelectionStart(),field.getSelectionEnd()).toString());
            assertTrue(field.onTextContextMenuItem(android.R.id.copy));ClipboardManager clipboard=(ClipboardManager)a.getSystemService(Context.CLIPBOARD_SERVICE);assertEquals("Blue Grapefruit",clipboard.getPrimaryClip().getItemAt(0).getText().toString());
            clipboard.setPrimaryClip(ClipData.newPlainText("Test","Works"));field.setSelection(0,field.length());assertTrue(field.onTextContextMenuItem(android.R.id.paste));assertEquals("Works",field.getText().toString());
        }
    }
    private static void layout(Activity a){Shadows.shadowOf(Looper.getMainLooper()).idle();View v=a.getWindow().getDecorView();v.measure(View.MeasureSpec.makeMeasureSpec(411,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(914,View.MeasureSpec.EXACTLY));v.layout(0,0,411,914);Shadows.shadowOf(Looper.getMainLooper()).idle();}
}
