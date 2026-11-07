// SPDX-License-Identifier: Apache-2.0
package com.android.settings.grapefruit;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.widget.Toast;
import org.grapefruit.platform.ThemeContract;

public final class AppearanceSettings extends PreferenceFragment {
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        PreferenceScreen screen=getPreferenceManager().createPreferenceScreen(getActivity());setPreferenceScreen(screen);
        final Preference theme=new Preference(getActivity());theme.setTitle("Universal theme");
        theme.setSummary(ThemeContract.NAMES[ThemeContract.read(getActivity())]);screen.addPreference(theme);
        theme.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference ignored) {
                new AlertDialog.Builder(getActivity()).setTitle("Universal theme")
                    .setSingleChoiceItems(ThemeContract.NAMES,ThemeContract.read(getActivity()),new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog,int which) {
                            if(ThemeProvider.select(getActivity(),which)) {theme.setSummary(ThemeContract.NAMES[which]);getActivity().recreate();}
                            else Toast.makeText(getActivity(),"Theme could not be saved",Toast.LENGTH_LONG).show();
                            dialog.dismiss();
                        }
                    }).setNegativeButton(android.R.string.cancel,null).show();return true;
            }
        });
        Preference quick=new Preference(getActivity());quick.setTitle("Edit Quick Settings");
        quick.setSummary("Add, remove, reorder or reset tiles");screen.addPreference(quick);
        quick.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference ignored) {startActivity(new Intent(getActivity(),QuickSettingsEditor.class));return true;}
        });
    }
}
