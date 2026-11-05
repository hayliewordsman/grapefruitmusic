package com.grapefruitmusic.ui;

import android.app.*;
import android.content.*;
import android.content.ClipboardManager;
import android.os.*;
import android.text.*;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import com.grapefruitmusic.util.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowDialog;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={23,37},application=Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class LegacyTextEditingTest {
    public static class GuardActivity extends Activity {
        @Override public ActionMode onWindowStartingActionMode(ActionMode.Callback callback){throw new AssertionError("Legacy field must not open the platform action bar");}
        @Override public ActionMode onWindowStartingActionMode(ActionMode.Callback callback,int type){throw new AssertionError("Legacy field must not open the platform floating toolbar");}
    }
    private org.robolectric.android.controller.ActivityController<GuardActivity> controller;
    private GuardActivity activity;private BlueEditText field;private ClipboardManager clipboard;
    @Before public void setup(){Application app=RuntimeEnvironment.getApplication();Prefs.init(app);Fonts.init(app);Brand.init(app);controller=Robolectric.buildActivity(GuardActivity.class);activity=controller.get();Ui.applyTheme(activity);controller.setup().visible();field=new BlueEditText(activity,true);activity.setContentView(field);field.setText("Blue Grapefruit");field.requestFocus();field.setSelection(field.length());clipboard=(ClipboardManager)activity.getSystemService(Context.CLIPBOARD_SERVICE);layout();}
    @After public void close(){Dialog dialog=ShadowDialog.getLatestDialog();if(dialog!=null)dialog.dismiss();controller.close();Shadows.shadowOf(Looper.getMainLooper()).idle();}
    @Test public void longPressUsesBlueMenuAndItsCopyCutPasteCommands(){
        clipboard.setPrimaryClip(ClipData.newPlainText("","replacement"));field.setSelection(0,4);assertTrue(field.performLongClick());GrapefruitDialog menu=menu();assertArrayEquals(new String[]{"Cut","Copy","Paste","Select all"},menu.getItems());click(menu,"Copy");assertEquals("Blue",clipboard.getPrimaryClip().getItemAt(0).getText().toString());assertEquals("Blue Grapefruit",field.getText().toString());
        assertTrue(field.onTextContextMenuItem(android.R.id.cut));assertEquals(" Grapefruit",field.getText().toString());assertTrue(field.onTextContextMenuItem(android.R.id.paste));assertEquals("Blue Grapefruit",field.getText().toString());assertEquals(4,field.getSelectionStart());
    }
    @Test public void emptyFieldsAndReversedSelectionsPasteWithoutSelectionOrClipboardFailures(){
        field.setText("");clipboard.setPrimaryClip(ClipData.newPlainText("",""));assertTrue(field.performLongClick());assertTrue(field.onTextContextMenuItem(android.R.id.paste));assertEquals("",field.getText().toString());
        field.setText("Blue Grapefruit");field.setSelection(4,0);clipboard.setPrimaryClip(ClipData.newPlainText("","Green"));assertTrue(field.onTextContextMenuItem(android.R.id.paste));assertEquals("Green Grapefruit",field.getText().toString());assertEquals(5,field.getSelectionStart());
    }
    @Test public void passwordFieldsDoNotOfferOrAllowCopyAndCut(){
        clipboard.setPrimaryClip(ClipData.newPlainText("","secret"));
        for(int type:new int[]{InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD}){
            field.setInputType(type);field.setText("12345");field.setSelection(0,5);field.performLongClick();GrapefruitDialog menu=menu();assertFalse(Arrays.asList(menu.getItems()).contains("Copy"));assertFalse(Arrays.asList(menu.getItems()).contains("Cut"));menu.dismiss();assertFalse(field.onTextContextMenuItem(android.R.id.copy));assertFalse(field.onTextContextMenuItem(android.R.id.cut));assertEquals("12345",field.getText().toString());
        }
    }
    @Test public void readOnlyTextSupportsSelectionAndCopyWithoutMutation(){
        field.setKeyListener(null);field.setSelection(0,4);field.performLongClick();assertArrayEquals(new String[]{"Copy","Select all"},menu().getItems());assertFalse(field.onTextContextMenuItem(android.R.id.cut));assertFalse(field.onTextContextMenuItem(android.R.id.paste));assertTrue(field.onTextContextMenuItem(android.R.id.copy));assertEquals("Blue",clipboard.getPrimaryClip().getItemAt(0).getText().toString());
    }
    @Test public void inputConnectionAndFiltersContinueToHandleTypingAndConstrainedPaste(){
        field.setSingleLine(true);field.setImeOptions(EditorInfo.IME_ACTION_SEARCH);EditorInfo info=new EditorInfo();InputConnection connection=field.onCreateInputConnection(info);assertNotNull(connection);field.setText("");assertTrue(connection.commitText("typed",1));assertEquals("typed",field.getText().toString());
        field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(5)});field.setText("abcde");field.setSelection(0,5);clipboard.setPrimaryClip(ClipData.newPlainText("","123456789"));assertTrue(field.onTextContextMenuItem(android.R.id.paste));assertEquals("12345",field.getText().toString());assertEquals(5,field.getSelectionStart());
        field.setFilters(new InputFilter[0]);field.setInputType(InputType.TYPE_CLASS_NUMBER);field.setText("");clipboard.setPrimaryClip(ClipData.newPlainText("","12a3"));field.onTextContextMenuItem(android.R.id.paste);assertEquals("123",field.getText().toString());
    }
    @Test public void pastingFinishesTheOldImeComposition(){
        field.setText("");InputConnection connection=field.onCreateInputConnection(new EditorInfo());assertTrue(connection.setComposingText("Blue",1));field.setSelection(0,4);clipboard.setPrimaryClip(ClipData.newPlainText("","Green"));field.onTextContextMenuItem(android.R.id.paste);assertTrue(connection.commitText("!",1));assertEquals("Green!",field.getText().toString());
    }
    @Test public void doubleTapAndTouchLongPressDoNotEnterThePlatformEditor(){
        field.setText("Blue");layout();long time=SystemClock.uptimeMillis();touch(time,time,MotionEvent.ACTION_DOWN,20,20);idle(40);touch(time,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,20,20);idle(80);time=SystemClock.uptimeMillis();touch(time,time,MotionEvent.ACTION_DOWN,20,20);idle(40);touch(time,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,20,20);layout();assertEquals(0,field.getSelectionStart());assertEquals(4,field.getSelectionEnd());assertNotNull(menu());menu().dismiss();
        field.setSelection(4);idle(500);time=SystemClock.uptimeMillis();touch(time,time,MotionEvent.ACTION_DOWN,20,20);idle(ViewConfiguration.getLongPressTimeout()+ViewConfiguration.getTapTimeout()+100);touch(time,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,20,20);layout();assertNotNull(menu());assertTrue(field.getSelectionEnd()>field.getSelectionStart());
    }
    @Test public void requestedPlatformActionModesAreRedirectedToBlue(){
        ActionMode.Callback callback=new ActionMode.Callback(){public boolean onCreateActionMode(ActionMode mode,Menu menu){throw new AssertionError("Platform selection callback must not run");}public boolean onPrepareActionMode(ActionMode mode,Menu menu){return true;}public boolean onActionItemClicked(ActionMode mode,MenuItem item){return true;}public void onDestroyActionMode(ActionMode mode){}};
        field.setSelection(0,4);assertNull(field.startActionMode(callback));layout();assertNotNull(menu());menu().dismiss();
    }
    @Test public void detachedFieldClosesItsMenuWithoutRestoringAnOldKeyboard(){
        field.performLongClick();GrapefruitDialog menu=menu();activity.setContentView(new android.view.View(activity));layout();assertFalse(menu.isShowing());assertNull(field.getWindowToken());
    }
    private GrapefruitDialog menu(){Dialog d=ShadowDialog.getLatestDialog();assertTrue(d instanceof GrapefruitDialog);assertTrue(d.isShowing());return(GrapefruitDialog)d;}
    private void click(GrapefruitDialog menu,String text){View label=BlueIntegrationTest.text(menu.getWindow().getDecorView(),text);assertNotNull(label);((View)label.getParent()).performClick();Shadows.shadowOf(Looper.getMainLooper()).idle();}
    private void idle(long ms){Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(ms));}
    private void touch(long down,long when,int action,float x,float y){MotionEvent event=MotionEvent.obtain(down,when,action,x,y,0);assertTrue(field.onTouchEvent(event));event.recycle();}
    private void layout(){Shadows.shadowOf(Looper.getMainLooper()).idle();View v=activity.getWindow().getDecorView();v.measure(View.MeasureSpec.makeMeasureSpec(411,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(914,View.MeasureSpec.EXACTLY));v.layout(0,0,411,914);Shadows.shadowOf(Looper.getMainLooper()).idle();}
}
