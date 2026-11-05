package com.grapefruitsettings;
import android.content.Context;
import android.preference.SwitchPreference;
import android.util.AttributeSet;
public class LegacySwitchPreference extends SwitchPreference {
    public LegacySwitchPreference(Context c){super(c);}
    public LegacySwitchPreference(Context c,AttributeSet a){super(c,a);}
    public LegacySwitchPreference(Context c,AttributeSet a,int style){super(c,a,style);}
    @Override protected void onBindView(android.view.View view){LegacyWidgets.replace(view);super.onBindView(view);}
}
