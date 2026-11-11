// SPDX-License-Identifier: GPL-3.0-or-later
package com.grapefruitmaps.barlow;

import android.app.Application;
import android.content.*;
import android.database.*;
import android.graphics.PointF;
import android.net.Uri;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import com.grapefruitmusic.ui.UniversalTheme;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowContentResolver;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={23,28,35},application=Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class UniversalThemeAndVictoryTest {
    private static final String AUTHORITY="io.github.hayliewordsman.grapefruitsettings.theme";
    private static final int[] IDS={0,1,2,3};
    private Application app;
    private SharedPreferences prefs;
    private ThemeProvider provider;
    @Before public void setup() {
        app=RuntimeEnvironment.getApplication();prefs=app.getSharedPreferences("maps",0);
        prefs.edit().clear().putInt("theme",2).commit();
        provider=Robolectric.buildContentProvider(ThemeProvider.class).create(AUTHORITY).get();
        ShadowContentResolver.registerProviderInternal(AUTHORITY,provider);
    }
    @Test public void mapsFollowsAllFourGeneralThemesIncludingBlueInsteadOfTheMediaFallback() {
        provider.mediaTheme=1;
        for(int id=0;id<4;id++) {
            provider.theme=id;provider.revision="selection-"+id;
            assertTrue(UniversalTheme.sync(app,prefs,false,IDS));
            assertEquals(id,prefs.getInt("theme",-1));
        }
    }
    @Test public void localChoiceLastsUntilANewUniversalRevisionAndUnavailableProvidersKeepIt() {
        provider.theme=3;provider.revision="blue";assertTrue(UniversalTheme.sync(app,prefs,false,IDS));
        prefs.edit().putInt("theme",1).commit();
        assertFalse(UniversalTheme.sync(app,prefs,false,IDS));assertEquals(1,prefs.getInt("theme",-1));
        provider.theme=0;provider.revision="dark";assertTrue(UniversalTheme.sync(app,prefs,false,IDS));
        provider.theme=99;provider.revision="invalid";assertFalse(UniversalTheme.sync(app,prefs,false,IDS));
        provider.available=false;assertFalse(UniversalTheme.sync(app,prefs,false,IDS));
        provider.available=true;provider.badSchema=true;assertFalse(UniversalTheme.sync(app,prefs,false,IDS));
        assertEquals(0,prefs.getInt("theme",-1));assertEquals("dark",prefs.getString("universal_revision",""));
    }
    @Test public void resumingAfterUniversalThemeChangeClearsTheBlueOnlyGame() {
        provider.theme=3;provider.revision="blue";
        try(ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup().visible()) {
            MainActivity a=c.get();assertEquals(3,prefs.getInt("theme",-1));a.show("egg");assertNotNull(a.egg);
            EggView old=a.egg;old.performClick();assertEquals(1,old.count);
            c.pause();provider.theme=0;provider.revision="dark";c.resume();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertEquals(0,prefs.getInt("theme",-1));assertEquals(0,old.count);
        }
        try(ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup().visible()) {
            c.get().show("egg");assertNull(c.get().egg);
        }
    }
    @Test public void dropsStartAtTheMouthLandOnAmericaAndStayBoundedAndHeadOnly() {
        EggView scene=new EggView(app,new Brand(app,3));scene.layout(0,0,328,382);scene.drop();
        PointF point=new PointF();scene.position(0,0,point);
        assertEquals(scene.mouthX(),point.x,.001f);assertEquals(scene.mouthY(),point.y,.001f);
        scene.position(0,30,point);assertTrue(point.x>scene.mouthX());
        scene.position(0,EggView.FLIGHT_MS,point);assertTrue(scene.onLand(point));
        for(int i=0;i<110;i++)scene.drop();
        assertEquals(111,scene.count);assertEquals(100,scene.retainedDrops());
        for(int i=0;i<scene.retainedDrops();i++){scene.position(i,EggView.FLIGHT_MS,point);assertTrue(scene.onLand(point));}
        scene.clear();assertEquals(0,scene.count);assertEquals(0,scene.retainedDrops());
        scene.setOnClickListener(v->scene.drop());
        touch(scene,MotionEvent.ACTION_DOWN,scene.mouthX(),scene.mouthY());touch(scene,MotionEvent.ACTION_UP,scene.mouthX(),scene.mouthY());assertEquals(1,scene.count);
        touch(scene,MotionEvent.ACTION_DOWN,point.x,point.y);touch(scene,MotionEvent.ACTION_UP,point.x,point.y);assertEquals(1,scene.count);
    }
    private void touch(EggView scene,int action,float x,float y){long now=SystemClock.uptimeMillis();MotionEvent e=MotionEvent.obtain(now,now,action,x,y,0);scene.onTouchEvent(e);e.recycle();}

    public static final class ThemeProvider extends ContentProvider {
        int theme,mediaTheme=1;
        String revision="initial";
        boolean available=true,badSchema;
        @Override public boolean onCreate(){return true;}
        @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort) {
            if(!available)return null;
            if(badSchema){MatrixCursor bad=new MatrixCursor(new String[]{"theme"});bad.addRow(new Object[]{theme});return bad;}
            MatrixCursor result=new MatrixCursor(new String[]{"theme","media_theme","revision","media_revision"});
            result.addRow(new Object[]{theme,mediaTheme,revision,"media-fallback"});return result;
        }
        @Override public String getType(Uri uri){return "vnd.android.cursor.item/vnd.grapefruit.theme";}
        @Override public Uri insert(Uri uri,ContentValues values){throw new SecurityException("Read only");}
        @Override public int update(Uri uri,ContentValues values,String where,String[] args){throw new SecurityException("Read only");}
        @Override public int delete(Uri uri,String where,String[] args){throw new SecurityException("Read only");}
    }
}
