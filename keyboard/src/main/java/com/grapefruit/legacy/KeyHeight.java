package com.grapefruit.legacy;
import android.content.Context;
import android.preference.PreferenceManager;
public final class KeyHeight {
    public static final String PREF="grapefruit_key_height";
    public static int percent(Context c) {
        try {
            int value=Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(c).getString(PREF,"100"));
            return Math.max(70,Math.min(150,value));
        } catch(RuntimeException e) { return 100; }
    }
    public static int pixels(Context c,int base) {
        int available=c.getResources().getDisplayMetrics().heightPixels;
        int reserve=Math.round(96*c.getResources().getDisplayMetrics().density);
        return Math.max(1,Math.min(Math.round(base*percent(c)/100f),Math.max(base/2,available-reserve)));
    }
}
