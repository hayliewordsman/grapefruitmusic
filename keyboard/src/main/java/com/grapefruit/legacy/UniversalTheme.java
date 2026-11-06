package com.grapefruit.legacy;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.preference.PreferenceManager;

/** Adapter for Grapefruit Settings' public, read-only palette/revision contract. */
public final class UniversalTheme {
    public static final Uri URI=Uri.parse("content://io.github.hayliewordsman.grapefruitsettings.theme/theme");
    public static final String PREF_REVISION="grapefruit_universal_revision";
    private UniversalTheme() {}

    /** IDs: Dark=0, Light=1, Black=2, Blue=3. A local override lasts until a new revision. */
    public static boolean sync(Context context) {
        Cursor cursor=null;
        try {
            cursor=context.getContentResolver().query(URI,null,null,null,null);
            if(cursor==null || !cursor.moveToFirst()) return false;
            String value=cursor.getString(cursor.getColumnIndexOrThrow("theme"));
            String revision=cursor.getString(cursor.getColumnIndexOrThrow("revision"));
            if(value==null || revision==null || revision.length()==0) return false;
            int choice=Integer.parseInt(value);
            if(choice<0 || choice>=GrapefruitTheme.NAMES.length) return false;
            SharedPreferences prefs=PreferenceManager.getDefaultSharedPreferences(context);
            Object previousRevision=prefs.getAll().get(PREF_REVISION);
            if(revision.equals(previousRevision)) return false;
            String next=Integer.toString(choice);
            boolean changed=!next.equals(prefs.getAll().get(GrapefruitTheme.PREF_THEME));
            boolean saved=prefs.edit().putString(GrapefruitTheme.PREF_THEME,next)
                    .putString(PREF_REVISION,revision).commit();
            return saved && changed;
        } catch(RuntimeException unavailable) {
            // Companion absent, inaccessible or incompatible: keep the app's own choice.
            return false;
        } finally {
            if(cursor!=null) {
                try { cursor.close(); } catch(RuntimeException ignored) { /* Optional provider. */ }
            }
        }
    }
}
