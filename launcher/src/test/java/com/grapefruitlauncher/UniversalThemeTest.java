package com.grapefruitlauncher;

import android.content.*;
import android.content.pm.ProviderInfo;
import android.database.*;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import com.grapefruitmusic.ui.UniversalTheme;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowContentResolver;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class UniversalThemeTest {
    private static final String AUTHORITY="io.github.hayliewordsman.grapefruitsettings.theme";
    private Context context;
    private SharedPreferences prefs;
    private Provider provider;
    public static class Provider extends ContentProvider {
        String theme,revision,media,mediaRevision;boolean fail;
        @Override public boolean onCreate(){return true;}
        @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order){
            if(fail)throw new SecurityException("Companion unavailable");
            assertEquals(UniversalTheme.URI,uri);MatrixCursor cursor=new MatrixCursor(new String[]{"theme","media_theme","revision","media_revision"});
            cursor.addRow(new Object[]{theme,media,revision,mediaRevision});return cursor;
        }
        @Override public String getType(Uri uri){return "vnd.android.cursor.item/vnd.grapefruit.theme";}
        @Override public Uri insert(Uri uri,ContentValues values){throw new SecurityException("Read only");}
        @Override public int update(Uri uri,ContentValues values,String selection,String[] args){throw new SecurityException("Read only");}
        @Override public int delete(Uri uri,String selection,String[] args){throw new SecurityException("Read only");}
    }
    @Before public void setup(){
        context=RuntimeEnvironment.getApplication();prefs=context.getSharedPreferences("launcher",0);prefs.edit().clear().commit();
        provider=new Provider();ProviderInfo info=new ProviderInfo();info.authority=AUTHORITY;provider.attachInfo(context,info);ShadowContentResolver.registerProviderInternal(AUTHORITY,provider);
    }
    private void select(int id,String revision){provider.theme=String.valueOf(id);provider.revision=revision;}
    private boolean sync(){return UniversalTheme.sync(context,prefs,false,new int[]{0,1,2,3});}
    @Test public void canonicalSelectionsIncludeVictoryBlue(){
        for(int id=0;id<4;id++){select(id,"selection-"+id);sync();assertEquals(id,prefs.getInt("theme",-1));assertEquals("selection-"+id,prefs.getString("universal_revision",""));}
    }
    @Test public void manualOverrideLastsUntilNextUniversalSelection(){
        select(1,"one");assertTrue(sync());prefs.edit().putInt("theme",3).commit();assertFalse(sync());assertEquals(3,prefs.getInt("theme",-1));
        select(1,"two");assertTrue(sync());assertEquals(1,prefs.getInt("theme",-1));
    }
    @Test public void blueUsesUniversalRevisionRatherThanMediaRevision(){
        select(3,"universal-blue");provider.media="1";provider.mediaRevision="earlier-light";assertTrue(sync());assertEquals(3,prefs.getInt("theme",-1));assertEquals("universal-blue",prefs.getString("universal_revision",""));
        SharedPreferences mediaPrefs=context.getSharedPreferences("test_media",0);assertTrue(UniversalTheme.sync(context,mediaPrefs,true,new int[]{0,1,2,-1}));assertEquals(1,mediaPrefs.getInt("theme",-1));
    }
    @Test public void absentInvalidAndUnreadableProviderPreserveLocalChoice(){
        prefs.edit().putInt("theme",3).commit();assertFalse(sync());assertEquals(3,prefs.getInt("theme",-1));
        select(7,"bad");assertFalse(sync());assertFalse(prefs.contains("universal_revision"));
        provider.fail=true;assertFalse(sync());assertEquals(3,prefs.getInt("theme",-1));
    }
    @Test public void sameColorNewRevisionStillAcknowledgesUniversalSelection(){
        prefs.edit().putInt("theme",1).commit();select(1,"new");assertFalse(sync());assertEquals("new",prefs.getString("universal_revision",""));
    }
    @Test public void launcherAdoptsOnResumeAndWhileVisible(){
        select(1,"initial");
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup().visible()){
            MainActivity a=controller.get();assertPalette(a,1);
            select(3,"live-blue");context.getContentResolver().notifyChange(UniversalTheme.URI,null);Shadows.shadowOf(Looper.getMainLooper()).idle();assertPalette(a,3);
            View same=content(a);prefs.edit().putInt("theme",0).commit();context.getContentResolver().notifyChange(UniversalTheme.URI,null);Shadows.shadowOf(Looper.getMainLooper()).idle();assertSame(same,content(a));assertEquals(0,prefs.getInt("theme",-1));
            controller.pause().stop();select(2,"background-black");context.getContentResolver().notifyChange(UniversalTheme.URI,null);controller.restart().start().resume().visible();Shadows.shadowOf(Looper.getMainLooper()).idle();assertPalette(a,2);
        }
    }
    private void assertPalette(MainActivity a,int id){assertEquals(id,prefs.getInt("theme",-1));assertEquals(new LauncherTheme(context,id).bg,((ColorDrawable)content(a).getBackground()).getColor());}
    private static View content(MainActivity a){return ((ViewGroup)a.findViewById(android.R.id.content)).getChildAt(0);}
}
