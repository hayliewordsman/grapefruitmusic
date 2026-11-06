package com.grapefruit.legacy;

import android.content.Context;
import android.preference.PreferenceManager;
import android.view.inputmethod.InputMethodSubtype;
import com.android.inputmethod.latin.SubtypeLocale;

/** Preferences and eligibility for the reference QWERTY arrangement. */
public final class KeyboardLayout {
    public static final String PREF_NUMBER_ROW="grapefruit_number_row";
    private KeyboardLayout() {}
    public static boolean numberRow(Context context) {
        Object value=PreferenceManager.getDefaultSharedPreferences(context).getAll().get(PREF_NUMBER_ROW);
        return Boolean.TRUE.equals(value);
    }
    public static boolean usesReferenceLayout(InputMethodSubtype subtype) {
        String language=SubtypeLocale.getSubtypeLocale(subtype).getLanguage();
        return "qwerty".equals(SubtypeLocale.getKeyboardLayoutSetName(subtype))
                && ("en".equals(language) || "zz".equals(language));
    }
}
