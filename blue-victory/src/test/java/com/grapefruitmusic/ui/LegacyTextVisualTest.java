package com.grapefruitmusic.ui;

import android.app.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import com.grapefruitmusic.R;
import com.grapefruitmusic.util.*;
import java.io.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowDialog;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,application=Application.class,qualifiers="w411dp-h914dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class LegacyTextVisualTest {
    @Test public void appDrawnHandlesDragTheSelectionAndMenuRenders()throws Exception{
        Application app=RuntimeEnvironment.getApplication();Prefs.init(app);Fonts.init(app);Brand.init(app);
        try(org.robolectric.android.controller.ActivityController<LegacyTextEditingTest.GuardActivity> controller=Robolectric.buildActivity(LegacyTextEditingTest.GuardActivity.class)){
            Activity a=controller.get();Ui.applyTheme(a);controller.setup().visible();LinearLayout root=new LinearLayout(a);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(16,30,16,16);root.setBackgroundColor(Ui.color(a,R.attr.gfBackground));TextView title=Ui.text(a,24,R.attr.gfTextPrimary,true);title.setText("Blue text editing");root.addView(title);
            TextView explanation=Ui.text(a,14,R.attr.gfTextSecondary,false);explanation.setSingleLine(false);explanation.setText("Android 4.x compatibility controls\nSelect a word, drag the blue handles, then copy or paste from the Blue menu.");explanation.setPadding(0,16,0,20);root.addView(explanation);
            BlueEditText field=new BlueEditText(a,true);field.setSingleLine(true);field.setTextSize(18);field.setTypeface(Fonts.regular());root.addView(field,new LinearLayout.LayoutParams(-1,64));a.setContentView(root);field.setText("Blue Grapefruit");field.requestFocus();field.setSelection(0,4);layout(a);
            Bitmap before=renderCurrent(field);PointF end=field.handleAt(4);assertEquals(Ui.accent(a),before.getPixel(Math.round(end.x),Math.round(end.y)));save(renderCurrent(root),"legacy-selection.png");
            ((android.content.ClipboardManager)a.getSystemService(android.content.Context.CLIPBOARD_SERVICE)).setPrimaryClip(android.content.ClipData.newPlainText("","Blue Grapefruit"));
            PointF destination=field.handleAt(field.length());long t=SystemClock.uptimeMillis();touch(field,t,MotionEvent.ACTION_DOWN,end.x,end.y);touch(field,t+40,MotionEvent.ACTION_MOVE,destination.x+1,destination.y);touch(field,t+60,MotionEvent.ACTION_UP,destination.x+1,destination.y);Shadows.shadowOf(Looper.getMainLooper()).idle();assertEquals(0,field.getSelectionStart());assertEquals(field.length(),field.getSelectionEnd());
            GrapefruitDialog menu=(GrapefruitDialog)ShadowDialog.getLatestDialog();assertTrue(menu.isShowing());assertNotNull(BlueIntegrationTest.text(menu.getWindow().getDecorView(),"Copy"));save(render(menu.getWindow().getDecorView(),411,914),"legacy-edit-menu.png");menu.dismiss();
            // Supplementary Unicode characters remain intact when copied and pasted.
            field.setText("Blue \uD83C\uDF4A");field.setSelection(5,7);assertTrue(field.onTextContextMenuItem(android.R.id.copy));field.setSelection(field.length());assertTrue(field.onTextContextMenuItem(android.R.id.paste));assertEquals("Blue \uD83C\uDF4A\uD83C\uDF4A",field.getText().toString());
        }
    }
    private static void touch(BlueEditText f,long time,int action,float x,float y){MotionEvent e=MotionEvent.obtain(time,time,action,x,y,0);assertTrue(f.onTouchEvent(e));e.recycle();}
    private static void layout(Activity a){Shadows.shadowOf(Looper.getMainLooper()).idle();View v=a.getWindow().getDecorView();v.measure(View.MeasureSpec.makeMeasureSpec(411,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(914,View.MeasureSpec.EXACTLY));v.layout(0,0,411,914);Shadows.shadowOf(Looper.getMainLooper()).idle();}
    private static Bitmap renderCurrent(View v){Bitmap b=Bitmap.createBitmap(v.getWidth(),v.getHeight(),Bitmap.Config.ARGB_8888);v.draw(new Canvas(b));return b;}
    private static Bitmap render(View v,int w,int h){v.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));v.layout(0,0,w,h);Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);v.draw(new Canvas(b));return b;}
    private static void save(Bitmap b,String name)throws Exception{File file=new File("build/reports/blue-beta5/"+name);file.getParentFile().mkdirs();try(FileOutputStream output=new FileOutputStream(file)){b.compress(Bitmap.CompressFormat.PNG,100,output);}}
}
