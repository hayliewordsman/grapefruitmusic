package com.grapefruit.legacy;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import java.util.Map;

/** Repair only appearance preferences before platform ListPreference reads their types. */
public final class AppearancePreferences {
    private AppearancePreferences() {}

    public static void repair(Context context) {
        SharedPreferences prefs=PreferenceManager.getDefaultSharedPreferences(context);
        Map<String,?> values=prefs.getAll();
        normalize(prefs,values,GrapefruitTheme.PREF_THEME,"0",true);
        normalize(prefs,values,KeyHeight.PREF,"100",false);
    }

    private static void normalize(SharedPreferences prefs,Map<String,?> values,
            String key,String fallback,boolean theme) {
        if(!values.containsKey(key)) return;
        Object stored=values.get(key);
        String value=stored instanceof Number?Integer.toString(((Number)stored).intValue())
                :stored instanceof String?(String)stored:fallback;
        try {
            int number=Integer.parseInt(value);
            if(theme ? number<0 || number>=GrapefruitTheme.NAMES.length
                    :number<70 || number>150 || number%10!=0) value=fallback;
            else value=Integer.toString(number);
        } catch(NumberFormatException invalid) {
            value=fallback;
        }
        if(!(stored instanceof String) || !value.equals(stored)) {
            prefs.edit().putString(key,value).apply();
        }
    }
}
