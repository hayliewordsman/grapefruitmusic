package com.grapefruitmusic.ui;

import android.app.Application;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import com.grapefruitmusic.R;
import com.grapefruitmusic.util.*;
import com.grapefruitmusic.ui.widget.Toggle;
import java.io.*;
import java.util.concurrent.TimeUnit;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,application=Application.class,qualifiers="w411dp-h914dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class BlueVisualTest {
    @Test public void blueToggleInBothStatesAndTvScreensRender() throws Exception {
        Application app=RuntimeEnvironment.getApplication();Prefs.init(app);Fonts.init(app);Brand.init(app);
        try(org.robolectric.android.controller.ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup().visible()){
            MainActivity a=controller.get();Shadows.shadowOf(Looper.getMainLooper()).idleFor(1500,TimeUnit.MILLISECONDS);BlueTelevision tv=(BlueTelevision)a.television();tv.repo.worker.submit(()->{}).get(10,TimeUnit.SECONDS);settle(a);
            Toggle toggle=new Toggle(a);toggle.measure(View.MeasureSpec.makeMeasureSpec(58,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(38,View.MeasureSpec.EXACTLY));toggle.layout(0,0,58,38);
            Bitmap off=Bitmap.createBitmap(58,38,Bitmap.Config.ARGB_8888);toggle.draw(new Canvas(off));int pixel=off.getPixel(40,19);assertTrue(Color.blue(pixel)>Color.red(pixel));
            toggle.setChecked(true);Bitmap on=Bitmap.createBitmap(58,38,Bitmap.Config.ARGB_8888);toggle.draw(new Canvas(on));pixel=on.getPixel(14,19);assertTrue(Color.blue(pixel)>Color.red(pixel));
            capture(off,"toggle-off.png");capture(on,"toggle-on.png");
            a.showTvSection("Channels");settle(a);capture(a.getWindow().getDecorView(),"tv-channels.png");
            a.showTvSection("Guide");settle(a);capture(a.getWindow().getDecorView(),"tv-guide.png");
            a.getFragmentManager().beginTransaction().replace(R.id.content,new SettingsFragment()).commit();settle(a);
            View title=BlueIntegrationTest.text(a.getWindow().getDecorView(),"TV Providers");assertNotNull(title);
            ScrollView scroll=findScroll(a.getWindow().getDecorView());assertNotNull(scroll);android.graphics.Rect r=new android.graphics.Rect();title.getDrawingRect(r);scroll.offsetDescendantRectToMyCoords(title,r);scroll.scrollTo(0,r.top);settle(a);capture(a.getWindow().getDecorView(),"tv-unified-settings.png");
        }
    }
    private static ScrollView findScroll(View v){if(v instanceof ScrollView)return (ScrollView)v;if(v instanceof ViewGroup)for(int n=0;n<((ViewGroup)v).getChildCount();n++){ScrollView got=findScroll(((ViewGroup)v).getChildAt(n));if(got!=null)return got;}return null;}
    @Test public void tvWatchSpacingAndCornerPlayerRender() throws Exception {
        Application app=RuntimeEnvironment.getApplication();Prefs.init(app);Fonts.init(app);Brand.init(app);
        try(org.robolectric.android.controller.ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup().visible()){
            MainActivity a=controller.get();Shadows.shadowOf(Looper.getMainLooper()).idleFor(1500,TimeUnit.MILLISECONDS);
            BlueTelevision tv=(BlueTelevision)a.television();tv.repo.worker.submit(()->{}).get(10,TimeUnit.SECONDS);settle(a);
            tv.session.current=tv.repo.channels.get(0);tv.session.selected=true;tv.session.status="Paused";
            a.showTvNowPlaying();settle(a);capture(a.getWindow().getDecorView(),"tv-now-playing.png");
            View pause=BlueIntegrationTest.text(a.getWindow().getDecorView(),"Play");assertNotNull(pause);assertTrue(pause.getHeight()>=48);
            a.getFragmentManager().popBackStackImmediate();settle(a);TvMini mini=(TvMini)tv.mini();mini.place(false);settle(a);
            capture(a.getWindow().getDecorView(),"tv-corner-mini.png");assertTrue(mini.getY()+mini.getHeight()<=a.findViewById(R.id.bottom_bars).getTop()-10);
            long time=SystemClock.uptimeMillis();float x=mini.getWidth()/4f,y=mini.getHeight()/2f;
            MotionEvent down=MotionEvent.obtain(time,time,MotionEvent.ACTION_DOWN,x,y,0);mini.dispatchTouchEvent(down);down.recycle();
            MotionEvent move=MotionEvent.obtain(time,time+30,MotionEvent.ACTION_MOVE,x-220,y-100,0);mini.dispatchTouchEvent(move);move.recycle();
            MotionEvent up=MotionEvent.obtain(time,time+60,MotionEvent.ACTION_UP,x-220,y-100,0);mini.dispatchTouchEvent(up);up.recycle();
            assertTrue(tv.repo.prefs.getBoolean("miniLeft",false));mini.place(false);settle(a);assertEquals(12f,mini.getX(),1f);
            capture(a.getWindow().getDecorView(),"tv-corner-mini-dragged.png");tv.session.stop();
        }
    }
    private static void settle(MainActivity a){Shadows.shadowOf(Looper.getMainLooper()).idle();a.getFragmentManager().executePendingTransactions();View v=a.getWindow().getDecorView();for(int i=0;i<3;i++){v.measure(View.MeasureSpec.makeMeasureSpec(411,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(914,View.MeasureSpec.EXACTLY));v.layout(0,0,411,914);Shadows.shadowOf(Looper.getMainLooper()).idle();}v.getViewTreeObserver().dispatchOnPreDraw();}
    private static void capture(View v,String name)throws Exception{Bitmap b=Bitmap.createBitmap(v.getWidth(),v.getHeight(),Bitmap.Config.ARGB_8888);v.draw(new Canvas(b));capture(b,name);}
    private static void capture(Bitmap b,String name)throws Exception{File f=new File("build/reports/blue-beta3/"+name);f.getParentFile().mkdirs();try(FileOutputStream out=new FileOutputStream(f)){b.compress(Bitmap.CompressFormat.PNG,100,out);}}
}
