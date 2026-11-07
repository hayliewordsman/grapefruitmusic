// SPDX-License-Identifier: GPL-3.0-or-later
package com.android.settings.grapefruit;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import java.util.UUID;
import org.grapefruit.platform.ThemeContract;

/** Existing universal-theme contract, hosted by native com.android.settings. Public reads only. */
public final class ThemeProvider extends ContentProvider {
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("universal_theme",Context.MODE_PRIVATE); }
    static ThemeState state(Context c) {
        SharedPreferences p=prefs(c);
        return new ThemeState(p.getInt("theme",0),p.getInt("media_theme",0),
                p.getString("revision","grapefruit-os-1"),p.getString("media_revision","grapefruit-os-1"));
    }
    static boolean select(Context c,int id) {
        ThemeState s=state(c).select(id,UUID.randomUUID().toString());
        boolean saved=prefs(c).edit().putInt("theme",s.theme).putInt("media_theme",s.mediaTheme)
                .putString("revision",s.revision).putString("media_revision",s.mediaRevision).commit();
        if(saved)c.getContentResolver().notifyChange(ThemeContract.URI,null);
        return saved;
    }
    @Override public boolean onCreate() { return true; }
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort) {
        if(!ThemeContract.URI.equals(uri))throw new IllegalArgumentException("Unknown theme URI");
        ThemeState s=state(getContext());
        MatrixCursor result=new MatrixCursor(new String[]{"theme","media_theme","revision","media_revision"});
        result.addRow(new Object[]{s.theme,s.mediaTheme,s.revision,s.mediaRevision});
        result.setNotificationUri(getContext().getContentResolver(),ThemeContract.URI);return result;
    }
    @Override public String getType(Uri uri) { return "vnd.android.cursor.item/vnd.grapefruit.theme"; }
    @Override public Uri insert(Uri uri,ContentValues values) { throw new SecurityException("Read only"); }
    @Override public int update(Uri uri,ContentValues values,String s,String[] a) { throw new SecurityException("Read only"); }
    @Override public int delete(Uri uri,String s,String[] a) { throw new SecurityException("Read only"); }
}
