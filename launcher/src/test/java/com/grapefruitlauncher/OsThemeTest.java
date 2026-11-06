// SPDX-License-Identifier: GPL-3.0-or-later
package com.grapefruitlauncher;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.Settings;
import com.grapefruitmusic.ui.OsTheme;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowContentResolver;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class OsThemeTest {
    private Context context;
    private Provider provider;
    public static class Provider extends ContentProvider {
        int id;
        boolean os = true, fail;
        @Override public boolean onCreate() { return true; }
        @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
            if (fail) throw new SecurityException("Unavailable");
            // Deliberately return a different column order, as an older companion may do.
            MatrixCursor c = new MatrixCursor(os ? new String[]{"theme", "revision", "os"}
                    : new String[]{"theme", "revision"});
            c.addRow(os ? new Object[]{id, "revision", 1} : new Object[]{id, "revision"});
            return c;
        }
        @Override public String getType(Uri u) { return "vnd.android.cursor.item/vnd.grapefruit.theme"; }
        @Override public Uri insert(Uri u, ContentValues v) { throw new SecurityException(); }
        @Override public int update(Uri u, ContentValues v, String s, String[] a) { throw new SecurityException(); }
        @Override public int delete(Uri u, String s, String[] a) { throw new SecurityException(); }
    }
    @Before public void setup() {
        context = RuntimeEnvironment.getApplication();
        provider = new Provider();
        ProviderInfo info = new ProviderInfo();
        info.authority = "io.github.hayliewordsman.grapefruitsettings.theme";
        provider.attachInfo(context, info);
        ShadowContentResolver.registerProviderInternal(info.authority, provider);
    }
    @Test public void readsAllOsThemesByColumnName() {
        for (int id = 0; id < 4; id++) { provider.id = id; assertEquals(id, OsTheme.selected(context)); }
    }
    @Test public void standaloneCompanionIsNotMistakenForOs() {
        provider.os = false; provider.id = 1;
        assertEquals(-1, OsTheme.selected(context));
        assertFalse(OsTheme.openSettings(context));
    }
    @Test public void invalidOrInaccessibleProviderIsOptional() {
        provider.id = 99; assertEquals(-1, OsTheme.selected(context));
        provider.fail = true; assertEquals(-1, OsTheme.selected(context));
    }
    @Test public void osOpensTheStockDisplaySettings() {
        assertTrue(OsTheme.openSettings(context));
        Intent intent = Shadows.shadowOf(RuntimeEnvironment.getApplication()).getNextStartedActivity();
        assertEquals(Settings.ACTION_DISPLAY_SETTINGS, intent.getAction());
    }
}
