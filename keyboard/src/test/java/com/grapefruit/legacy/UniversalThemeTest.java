package com.grapefruit.legacy;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.ProviderInfo;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.preference.PreferenceManager;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowContentResolver;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={17,19})
public class UniversalThemeTest {
    public static class ThemeProviderStub extends ContentProvider {
        String choice,revision;
        boolean denied;
        @Override public boolean onCreate(){return true;}
        @Override public Cursor query(Uri uri,String[] p,String s,String[] a,String sort) {
            if(denied)throw new SecurityException("Unavailable companion");
            MatrixCursor cursor=new MatrixCursor(new String[]{"theme","media_theme","revision","media_revision"});
            cursor.addRow(new Object[]{choice,"2",revision,"media-only"});return cursor;
        }
        @Override public String getType(Uri uri){return null;}
        @Override public Uri insert(Uri uri,ContentValues v){throw new SecurityException("Read only");}
        @Override public int update(Uri uri,ContentValues v,String s,String[] a){throw new SecurityException("Read only");}
        @Override public int delete(Uri uri,String s,String[] a){throw new SecurityException("Read only");}
    }
    private Context context;
    private SharedPreferences prefs;
    private ThemeProviderStub provider;
    @Before public void setup() {
        context=RuntimeEnvironment.getApplication();
        prefs=PreferenceManager.getDefaultSharedPreferences(context);prefs.edit().clear().commit();
        provider=new ThemeProviderStub();ProviderInfo info=new ProviderInfo();
        info.authority=UniversalTheme.URI.getAuthority();info.exported=true;
        provider.attachInfo(context,info);
        ShadowContentResolver.registerProviderInternal(info.authority,provider);
    }
    @Test public void allFourCanonicalThemesApplyIncludingBlue() {
        for(int id=0;id<4;id++) {
            provider.choice=""+id;provider.revision="revision-"+id;
            UniversalTheme.sync(context);
            assertEquals(id,new GrapefruitTheme(context).id);
            assertEquals(provider.revision,prefs.getString(UniversalTheme.PREF_REVISION,null));
        }
    }
    @Test public void localOverrideLastsUntilAnotherUniversalSelection() {
        provider.choice="3";provider.revision="one";assertTrue(UniversalTheme.sync(context));
        prefs.edit().putString(GrapefruitTheme.PREF_THEME,"1").commit();
        assertFalse(UniversalTheme.sync(context));assertEquals(1,new GrapefruitTheme(context).id);
        provider.revision="two";assertTrue(UniversalTheme.sync(context));
        assertEquals(3,new GrapefruitTheme(context).id);
    }
    @Test public void absentOrDeniedCompanionPreservesTheLocalTheme() {
        prefs.edit().putString(GrapefruitTheme.PREF_THEME,"3").commit();
        assertFalse(UniversalTheme.sync(context));assertEquals(3,new GrapefruitTheme(context).id);
        provider.denied=true;
        assertFalse(UniversalTheme.sync(context));assertEquals(3,new GrapefruitTheme(context).id);
    }
    @Test public void invalidRemoteValuesDoNotConsumeARevisionOrChangeOtherSettings() {
        prefs.edit().putString(GrapefruitTheme.PREF_THEME,"1").putString("grapefruit_key_height","140").commit();
        provider.choice="99";provider.revision="new";
        assertFalse(UniversalTheme.sync(context));assertEquals(1,new GrapefruitTheme(context).id);
        assertFalse(prefs.contains(UniversalTheme.PREF_REVISION));
        provider.choice="0";assertTrue(UniversalTheme.sync(context));
        assertEquals("140",prefs.getString("grapefruit_key_height",null));
    }
}
