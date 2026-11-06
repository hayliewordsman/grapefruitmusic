/* Modified for Grapefruit LegacyHolo: Audio-style spell-check preferences.
 *
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.android.inputmethod.latin.spellcheck;

import android.os.Bundle;
import android.preference.PreferenceFragment;

import com.android.inputmethod.latin.R;

/**
 * Preference screen.
 */
public final class SpellCheckerSettingsFragment extends PreferenceFragment {
    private com.grapefruit.legacy.ui.GrapefruitPreferences mGrapefruitSettings;
    @Override public android.view.View onCreateView(android.view.LayoutInflater inflater,
            android.view.ViewGroup parent,Bundle state) {
        com.grapefruitmusic.ui.Fonts.init(getActivity());
        mGrapefruitSettings=new com.grapefruit.legacy.ui.GrapefruitPreferences(this);
        mGrapefruitSettings.setRootTitle(getText(R.string.android_spell_checker_settings));
        return mGrapefruitSettings.createView(false);
    }
    @Override public void onDestroyView() {
        if(mGrapefruitSettings!=null)mGrapefruitSettings.close();mGrapefruitSettings=null;super.onDestroyView();
    }
    /**
     * Empty constructor for fragment generation.
     */
    public SpellCheckerSettingsFragment() {
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        addPreferencesFromResource(R.xml.spell_checker_settings);
        mGrapefruitSettings.bind(savedInstanceState);
    }
}
