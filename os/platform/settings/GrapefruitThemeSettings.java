// SPDX-License-Identifier: Apache-2.0
package com.android.settings.grapefruit;

import android.os.Bundle;
import androidx.preference.ListPreference;
import android.app.settings.SettingsEnums;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

/** A page inside the stock Settings application, using its navigation and widgets. */
public final class GrapefruitThemeSettings extends SettingsPreferenceFragment {
    @Override public int getMetricsCategory() { return SettingsEnums.DISPLAY; }
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.grapefruit_theme_settings);
        ListPreference theme = findPreference("grapefruit_theme_choice");
        theme.setValue(Integer.toString(GrapefruitThemeState.selected(requireContext())));
        theme.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
        theme.setOnPreferenceChangeListener((preference, value) -> {
            GrapefruitThemeState.select(requireContext(), Integer.parseInt((String) value));
            return true;
        });
    }
}
