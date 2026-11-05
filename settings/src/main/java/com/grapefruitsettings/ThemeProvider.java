package com.grapefruitsettings;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import com.grapefruitmusic.ui.UniversalTheme;
import java.util.UUID;

/** Public reads share only palette choices. No exported write path, files, device settings or data. */
public final class ThemeProvider extends ContentProvider {
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("universal_theme", Context.MODE_PRIVATE); }
    static ThemeState state(Context c) {
        SharedPreferences p=prefs(c);
        return new ThemeState(p.getInt("theme",-1),p.getInt("media_theme",-1),
                p.getString("revision",null),p.getString("media_revision",null));
    }
    static boolean select(Context c, int id) {
        ThemeState s=state(c).select(id,UUID.randomUUID().toString());
        boolean saved=prefs(c).edit().putInt("theme",s.theme).putInt("media_theme",s.mediaTheme)
                .putString("revision",s.revision).putString("media_revision",s.mediaRevision).commit();
        if(saved)c.getContentResolver().notifyChange(UniversalTheme.URI,null);
        return saved;
    }
    @Override public boolean onCreate() { return true; }
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort) {
        if(!UniversalTheme.URI.equals(uri))throw new IllegalArgumentException("Unknown theme URI");
        ThemeState s=state(getContext());
        MatrixCursor result=new MatrixCursor(new String[]{"theme","media_theme","revision","media_revision"});
        result.addRow(new Object[]{s.theme<0?null:s.theme,s.mediaTheme<0?null:s.mediaTheme,s.revision,s.mediaRevision});
        result.setNotificationUri(getContext().getContentResolver(),UniversalTheme.URI);
        return result;
    }
    @Override public String getType(Uri uri) { return "vnd.android.cursor.item/vnd.grapefruit.theme"; }
    @Override public Uri insert(Uri uri,ContentValues values) { throw new SecurityException("Read only"); }
    @Override public int update(Uri uri,ContentValues values,String s,String[] a) { throw new SecurityException("Read only"); }
    @Override public int delete(Uri uri,String s,String[] a) { throw new SecurityException("Read only"); }
}
