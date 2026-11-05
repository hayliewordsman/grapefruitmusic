package com.grapefruitsettings;

import android.content.Context;
import android.preference.CheckBoxPreference;
import android.util.AttributeSet;

/** Preserves CheckBoxPreference persistence, listeners and OEM casts. */
public class LegacyCheckBoxPreference extends CheckBoxPreference {
    public LegacyCheckBoxPreference(Context c){super(c);}
    public LegacyCheckBoxPreference(Context c,AttributeSet a){super(c,a);}
    public LegacyCheckBoxPreference(Context c,AttributeSet a,int style){super(c,a,style);}
    @Override protected void onBindView(android.view.View view){LegacyWidgets.replace(view);super.onBindView(view);}
}
