// SPDX-License-Identifier: GPL-3.0-or-later
package com.grapefruitmaps.barlow;

import android.app.*;
import android.graphics.*;
import android.os.Looper;
import android.view.*;
import java.io.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.android.controller.ActivityController;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,application=Application.class,qualifiers="w360dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class MapsVisualTest {
    @Test public void captureThemesAndBundledGameArtwork() throws Exception {
        Application app=RuntimeEnvironment.getApplication();
        for(int theme=0;theme<4;theme++) {
            app.getSharedPreferences("maps",0).edit().clear().putInt("theme",theme).commit();
            try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup().visible()) {
                MainActivity a=controller.get();
                for(String page:new String[]{"map","street","directions","settings","about","licenses","search"}){a.show(page);layout(a,360,640);capture(a,page+"-"+theme+".png",360,640);}
                if(theme==3){a.show("egg");layout(a,360,640);a.egg.performClick();capture(a,"victory-mouth-start.png",360,640);Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(280));capture(a,"victory-mouth-flight.png",360,640);for(int i=0;i<8;i++)a.egg.performClick();Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(900));layout(a,360,640);capture(a,"victory-egg.png",360,640);assertTrue(a.egg.getHeight()>150);}
            }
        }
    }
    @Test @Config(qualifiers="w640dp-h360dp-land-mdpi") public void captureLandscape() throws Exception {
        Application app=RuntimeEnvironment.getApplication();
        for(int theme=0;theme<4;theme++) {
            app.getSharedPreferences("maps",0).edit().clear().putInt("theme",theme).commit();
            try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup().visible()) {
                MainActivity a=controller.get();
                for(String page:new String[]{"map","street","directions","settings","about"}){a.show(page);layout(a,640,360);capture(a,"land-"+page+"-"+theme+".png",640,360);}
            }
        }
    }
    /** The side controls, drawn exactly as the map page adds them, for review without a network map. */
    @Test @Config(qualifiers="w640dp-h360dp-land-mdpi") public void captureDrawnIcons() throws Exception {
        Application app=RuntimeEnvironment.getApplication();app.getSharedPreferences("maps",0).edit().clear().commit();
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup().visible()) {
            MainActivity a=controller.get();android.widget.LinearLayout strip=a.b.row();strip.setBackgroundColor(a.b.bg);
            for(Icon.Kind k:Icon.Kind.values()){View v=a.iconButton(k,k.name(),null);android.widget.LinearLayout.LayoutParams lp=new android.widget.LinearLayout.LayoutParams(44,44);lp.setMargins(4,4,4,4);strip.addView(v,lp);}
            strip.measure(View.MeasureSpec.makeMeasureSpec(640,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(52,View.MeasureSpec.EXACTLY));strip.layout(0,0,640,52);
            Bitmap bitmap=Bitmap.createBitmap(640,52,Bitmap.Config.ARGB_8888);strip.draw(new Canvas(bitmap));
            File f=new File("build/reports/maps-preview/icons.png");f.getParentFile().mkdirs();try(FileOutputStream out=new FileOutputStream(f)){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}
        }
    }
    private void layout(MainActivity a,int w,int h){View v=a.getWindow().getDecorView();v.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));v.layout(0,0,w,h);Shadows.shadowOf(Looper.getMainLooper()).idle();}
    private void capture(MainActivity a,String name,int w,int h)throws Exception{View v=a.getWindow().getDecorView();Bitmap bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);v.draw(new Canvas(bitmap));File f=new File("build/reports/maps-preview/"+name);f.getParentFile().mkdirs();try(FileOutputStream out=new FileOutputStream(f)){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}}
}
