package com.grapefruitsettings;

import android.content.Context;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.util.AttributeSet;

/** Covers text dialogs created inside the framework rather than the OEM Activity bytecode. */
public class LegacyEditTextPreference extends EditTextPreference {
    public LegacyEditTextPreference(Context c){super(c);}
    public LegacyEditTextPreference(Context c,AttributeSet a){super(c,a);}
    public LegacyEditTextPreference(Context c,AttributeSet a,int style){super(c,a,style);}
    @Override protected void showDialog(Bundle state){super.showDialog(state);LegacyTheme.prepareDialog(getDialog());}
}
