/* Modified for Grapefruit LegacyHolo: standalone build, Grapefruit appearance and legacy compatibility. */
/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.inputmethod.latin;

import android.content.Intent;
import android.preference.PreferenceActivity;

public final class SettingsActivity extends PreferenceActivity {
    private static final String DEFAULT_FRAGMENT = Settings.class.getName();
    @Override protected boolean isValidFragment(String fragmentName) {
        return DEFAULT_FRAGMENT.equals(fragmentName);
    }
    @Override public void onCreate(android.os.Bundle state) {
        com.grapefruit.legacy.AppearancePreferences.repair(this);
        com.grapefruit.legacy.UniversalTheme.sync(this);
        com.grapefruit.legacy.GrapefruitTheme t=new com.grapefruit.legacy.GrapefruitTheme(this);
        setTheme(t.id==1?R.style.GrapefruitLegacyLight:R.style.GrapefruitLegacy);
        com.grapefruitmusic.ui.Fonts.init(this);
        super.onCreate(state);
        setTitle(R.string.grapefruit_keyboard_name);
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(t.bg));
    }

    @Override public void onResume() {
        super.onResume();
        com.grapefruit.legacy.UniversalTheme.sync(this);
    }


    @Override
    public Intent getIntent() {
        final Intent intent = new Intent(super.getIntent());
        // Android 4.2 predates PreferenceActivity's fragment-validation hook.
        intent.putExtra(EXTRA_SHOW_FRAGMENT, DEFAULT_FRAGMENT);
        intent.putExtra(EXTRA_NO_HEADERS, true);
        return intent;
    }
}
