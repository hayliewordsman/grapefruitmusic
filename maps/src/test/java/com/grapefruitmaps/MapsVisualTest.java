// SPDX-License-Identifier: GPL-3.0-or-later
package com.grapefruitmaps;

import android.app.*;
import android.graphics.*;
import android.os.Looper;
import android.view.*;
import android.widget.ListView;
import java.io.*;
import java.nio.file.Files;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.android.controller.ActivityController;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,application=Application.class,qualifiers="w360dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class MapsVisualTest {
    @Test public void captureThemesAndBundledGameArtwork() throws Exception {
        Application app=RuntimeEnvironment.getApplication();
        for(int theme=0;theme<4;theme++) {
            app.getSharedPreferences("maps",0).edit().clear().putInt("theme",theme).putBoolean("offline_only",true).commit();
            try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup().visible()) {
                MainActivity a=controller.get();a.show("settings");layout(a);capture(a,"settings-"+theme+".png");
                a.show("about");layout(a);capture(a,"about-"+theme+".png");
                a.show("licenses");layout(a);capture(a,"licenses-"+theme+".png");
                a.show("search");layout(a);capture(a,"search-"+theme+".png");
                a.show("offline");layout(a);ListView countries=list(a.content);
                for(int i=0;i<countries.getCount();i++)if("United States".equals(countries.getItemAtPosition(i))){countries.performItemClick(null,i,i);break;}
                layout(a);capture(a,"country-"+theme+".png");
                if(theme==3){a.show("egg");layout(a);a.egg.performClick();capture(a,"victory-mouth-start.png");Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(280));capture(a,"victory-mouth-flight.png");for(int i=0;i<8;i++)a.egg.performClick();Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(900));layout(a);capture(a,"victory-egg.png");assertTrue(a.egg.getHeight()>150);}
            }
        }
    }
    @Test public void countryMapRendersWithoutNetwork() throws Exception {
        Application app=RuntimeEnvironment.getApplication();AndroidGraphicFactory.createInstance(app);
        MapDownloads d=MapDownloads.get(app);File map=new File(d.directory,"visual-andorra.map");
        Files.copy(new File(System.getProperty("maps.fixture")).toPath(),map.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        app.getSharedPreferences("maps",0).edit().clear().putBoolean("offline_only",true).putString("offline_file",map.getName()).putInt("zoom",12)
            .putLong("lat",Double.doubleToLongBits(42.5063)).putLong("lon",Double.doubleToLongBits(1.5218)).commit();
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup().visible()){
            MainActivity a=controller.get();assertNotNull(a.map);layout(a);
            for(int i=0;i<12;i++){capture(a,"offline-andorra.png");Thread.sleep(150);Shadows.shadowOf(Looper.getMainLooper()).idle();}
            layout(a);capture(a,"offline-andorra.png");
            Bitmap image=BitmapFactory.decodeFile("build/reports/maps-preview/offline-andorra.png");
            java.util.Set<Integer> colors=new java.util.HashSet<>();for(int y=280;y<480;y+=2)for(int x=20;x<230;x+=2)colors.add(image.getPixel(x,y));
            assertTrue("Offline tile pixels should contain map features, not a blank surface",colors.size()>50);
            a.show("directions");assertNull(a.map);
        } finally {map.delete();}
    }
    private ListView list(View v){if(v instanceof ListView)return (ListView)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){ListView l=list(((ViewGroup)v).getChildAt(i));if(l!=null)return l;}return null;}
    private void layout(MainActivity a){View v=a.getWindow().getDecorView();v.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(640,View.MeasureSpec.EXACTLY));v.layout(0,0,360,640);Shadows.shadowOf(Looper.getMainLooper()).idle();}
    private void capture(MainActivity a,String name)throws Exception{View v=a.getWindow().getDecorView();Bitmap bitmap=Bitmap.createBitmap(360,640,Bitmap.Config.ARGB_8888);v.draw(new Canvas(bitmap));File f=new File("build/reports/maps-preview/"+name);f.getParentFile().mkdirs();try(FileOutputStream out=new FileOutputStream(f)){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}}
}
