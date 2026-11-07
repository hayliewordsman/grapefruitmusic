package com.grapefruitmusic.ui;

import android.app.Activity;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import com.grapefruitmusic.R;
import com.grapefruitmusic.util.Prefs;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowContentResolver;
import static org.junit.Assert.*;

/** Behavioral theme selection on the intended Jelly Bean and future KitKat Android APIs. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk={17,19},application=Application.class)
public final class MediaThemeTest {
    public static class Provider extends ContentProvider {
        int id;String revision="initial";
        public boolean onCreate(){return true;}
        public Cursor query(Uri u,String[] p,String s,String[] a,String order){
            MatrixCursor c=new MatrixCursor(new String[]{"theme","media_theme","revision","media_revision"});
            c.addRow(new Object[]{id,id,revision,revision});return c;
        }
        public String getType(Uri u){return "vnd.android.cursor.item/vnd.grapefruit.theme";}
        public Uri insert(Uri u,ContentValues v){throw new SecurityException();}
        public int update(Uri u,ContentValues v,String s,String[] a){throw new SecurityException();}
        public int delete(Uri u,String s,String[] a){throw new SecurityException();}
    }
    @Test public void universalSelectionsApplyAllFourActualPalettesWithoutBlueLock(){
        Application app=RuntimeEnvironment.getApplication();Prefs.init(app);Prefs.raw().edit().clear().commit();
        Provider provider=Robolectric.buildContentProvider(Provider.class).create(UniversalTheme.URI.getAuthority()).get();
        ShadowContentResolver.registerProviderInternal(UniversalTheme.URI.getAuthority(),provider);
        int[] ids={4,3,2,5};int[] backgrounds={0xff1c0a0e,0xfffce4d9,0xff000000,0xff07162b};
        for(int i=0;i<4;i++){
            provider.id=i;provider.revision="choice-"+i;UniversalTheme.sync(app,Prefs.raw(),true,ids);
            Activity activity=Robolectric.buildActivity(Activity.class).get();Ui.applyTheme(activity);
            assertEquals(ids[i],Ui.theme(activity));assertEquals(backgrounds[i],Ui.color(activity,R.attr.gfBackground));
            assertEquals("Grapefruit Media",activity.getString(R.string.app_name));
        }
        Prefs.setTheme(Prefs.THEME_GRAPEFRUIT_LIGHT);
        assertFalse(UniversalTheme.sync(app,Prefs.raw(),true,ids));
        provider.revision="new-blue";assertTrue(UniversalTheme.sync(app,Prefs.raw(),true,ids));assertEquals(5,Prefs.theme());
    }
}
