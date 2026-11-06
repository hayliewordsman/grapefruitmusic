/* Modified for Grapefruit LegacyHolo: Audio-style rows and subtype forms.
 *
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.inputmethod.latin;

import static com.android.inputmethod.latin.Constants.Subtype.ExtraValue.ASCII_CAPABLE;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.DialogPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodSubtype;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.Toast;

import com.android.inputmethod.compat.CompatUtils;

import java.util.ArrayList;
import java.util.TreeSet;

public final class AdditionalSubtypeSettings extends PreferenceFragment {
    private SharedPreferences mPrefs;
    private SubtypeLocaleAdapter mSubtypeLocaleAdapter;
    private KeyboardLayoutSetAdapter mKeyboardLayoutSetAdapter;

    private boolean mIsAddingNewSubtype;
    private Dialog mSubtypeEnablerNotificationDialog;
    private com.grapefruit.legacy.ui.GrapefruitPreferences mGrapefruitSettings;
    private String mSubtypePreferenceKeyForSubtypeEnabler;

    private static final int MENU_ADD_SUBTYPE = Menu.FIRST;
    private static final String KEY_IS_ADDING_NEW_SUBTYPE = "is_adding_new_subtype";
    private static final String KEY_IS_SUBTYPE_ENABLER_NOTIFICATION_DIALOG_OPEN =
            "is_subtype_enabler_notification_dialog_open";
    private static final String KEY_SUBTYPE_FOR_SUBTYPE_ENABLER = "subtype_for_subtype_enabler";
    static final class SubtypeLocaleItem extends Pair<String, String>
            implements Comparable<SubtypeLocaleItem> {
        public SubtypeLocaleItem(final String localeString, final String displayName) {
            super(localeString, displayName);
        }

        public SubtypeLocaleItem(final String localeString) {
            this(localeString, SubtypeLocale.getSubtypeLocaleDisplayName(localeString));
        }

        @Override
        public String toString() {
            return second;
        }

        @Override
        public int compareTo(final SubtypeLocaleItem o) {
            return first.compareTo(o.first);
        }
    }

    static final class SubtypeLocaleAdapter extends ArrayAdapter<SubtypeLocaleItem> {
        private static final String TAG = SubtypeLocaleAdapter.class.getSimpleName();
        private static final boolean DEBUG_SUBTYPE_ID = false;

        public SubtypeLocaleAdapter(final Context context) {
            super(context, android.R.layout.simple_spinner_item);
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

            final TreeSet<SubtypeLocaleItem> items = CollectionUtils.newTreeSet();
            final InputMethodInfo imi = ImfUtils.getInputMethodInfoOfThisIme(context);
            final int count = imi.getSubtypeCount();
            for (int i = 0; i < count; i++) {
                final InputMethodSubtype subtype = imi.getSubtypeAt(i);
                if (DEBUG_SUBTYPE_ID) {
                    android.util.Log.d(TAG, String.format("%-6s 0x%08x %11d %s",
                            subtype.getLocale(), subtype.hashCode(), subtype.hashCode(),
                            SubtypeLocale.getSubtypeDisplayName(subtype, context.getResources())));
                }
                if (subtype.containsExtraValueKey(ASCII_CAPABLE)) {
                    items.add(createItem(context, subtype.getLocale()));
                }
            }
            // TODO: Should filter out already existing combinations of locale and layout.
            addAll(items);
        }

        public static SubtypeLocaleItem createItem(final Context context,
                final String localeString) {
            if (localeString.equals(SubtypeLocale.NO_LANGUAGE)) {
                final String displayName = context.getString(R.string.subtype_no_language);
                return new SubtypeLocaleItem(localeString, displayName);
            } else {
                return new SubtypeLocaleItem(localeString);
            }
        }
    }

    static final class KeyboardLayoutSetItem extends Pair<String, String> {
        public KeyboardLayoutSetItem(final InputMethodSubtype subtype) {
            super(SubtypeLocale.getKeyboardLayoutSetName(subtype),
                    SubtypeLocale.getKeyboardLayoutSetDisplayName(subtype));
        }

        @Override
        public String toString() {
            return second;
        }
    }

    static final class KeyboardLayoutSetAdapter extends ArrayAdapter<KeyboardLayoutSetItem> {
        public KeyboardLayoutSetAdapter(final Context context) {
            super(context, android.R.layout.simple_spinner_item);
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

            // TODO: Should filter out already existing combinations of locale and layout.
            for (final String layout : SubtypeLocale.getPredefinedKeyboardLayoutSet()) {
                // This is a dummy subtype with NO_LANGUAGE, only for display.
                final InputMethodSubtype subtype = AdditionalSubtype.createAdditionalSubtype(
                        SubtypeLocale.NO_LANGUAGE, layout, null);
                add(new KeyboardLayoutSetItem(subtype));
            }
        }
    }

    private interface SubtypeDialogProxy {
        public void onRemovePressed(SubtypePreference subtypePref);
        public void onSavePressed(SubtypePreference subtypePref);
        public void onAddPressed(SubtypePreference subtypePref);
        public SubtypeLocaleAdapter getSubtypeLocaleAdapter();
        public KeyboardLayoutSetAdapter getKeyboardLayoutSetAdapter();
    }

    static final class SubtypePreference extends DialogPreference
            implements DialogInterface.OnCancelListener {
        private static final String KEY_PREFIX = "subtype_pref_";
        private static final String KEY_NEW_SUBTYPE = KEY_PREFIX + "new";

        private InputMethodSubtype mSubtype;
        private InputMethodSubtype mPreviousSubtype;

        private final SubtypeDialogProxy mProxy;
        private Spinner mSubtypeLocaleSpinner;
        private Spinner mKeyboardLayoutSetSpinner;
        private Dialog mGrapefruitDialog,mChoiceDialog;
        private android.widget.TextView mLocaleButton,mLayoutButton;

        public static SubtypePreference newIncompleteSubtypePreference(final Context context,
                final SubtypeDialogProxy proxy) {
            return new SubtypePreference(context, null, proxy);
        }

        public SubtypePreference(final Context context, final InputMethodSubtype subtype,
                final SubtypeDialogProxy proxy) {
            super(context, null);
            setDialogLayoutResource(R.layout.additional_subtype_dialog);
            setPersistent(false);
            mProxy = proxy;
            setSubtype(subtype);
            setOnPreferenceClickListener(pref->{show();return true;});
        }

        public void show() {showDialog(null);}
        @Override public Dialog getDialog() {return mGrapefruitDialog;}
        public void closeGrapefruitDialogs() {
            if(mChoiceDialog!=null)mChoiceDialog.dismiss();
            if(mGrapefruitDialog!=null)mGrapefruitDialog.dismiss();
        }
        private void updateButtons() {
            if(mLocaleButton!=null)mLocaleButton.setText(getContext().getString(R.string.subtype_locale)
                    +"  ·  "+mSubtypeLocaleSpinner.getSelectedItem());
            if(mLayoutButton!=null)mLayoutButton.setText(getContext().getString(R.string.keyboard_layout_set)
                    +"  ·  "+mKeyboardLayoutSetSpinner.getSelectedItem());
        }
        private void choose(Spinner data,CharSequence title) {
            CharSequence[] choices=new CharSequence[data.getAdapter().getCount()];
            for(int i=0;i<choices.length;i++)choices[i]=data.getItemAtPosition(i).toString();
            mChoiceDialog=com.grapefruit.legacy.ui.SettingsDialog.choices(getContext(),title,choices,
                    data.getSelectedItemPosition(),which->{data.setSelection(which);updateButtons();});
        }
        @Override protected void showDialog(Bundle state) {
            if(mGrapefruitDialog!=null && mGrapefruitDialog.isShowing())return;
            // The existing adapters and selection model are retained. Native spinner widgets
            // are never added to a window; Audio-style rows open Grapefruit choice sheets.
            onCreateDialogView();
            if(mSubtype!=null) {
                setSpinnerPosition(mSubtypeLocaleSpinner,SubtypeLocaleAdapter.createItem(getContext(),mSubtype.getLocale()));
                setSpinnerPosition(mKeyboardLayoutSetSpinner,new KeyboardLayoutSetItem(mSubtype));
            }
            Context c=getContext();com.grapefruit.legacy.GrapefruitTheme t=new com.grapefruit.legacy.GrapefruitTheme(c);
            android.widget.LinearLayout body=com.grapefruit.legacy.ui.SettingsUi.vertical(c);
            mLocaleButton=com.grapefruit.legacy.ui.SettingsUi.button(c,"",false,
                    v->choose(mSubtypeLocaleSpinner,c.getText(R.string.subtype_locale)));
            mLayoutButton=com.grapefruit.legacy.ui.SettingsUi.button(c,"",false,
                    v->choose(mKeyboardLayoutSetSpinner,c.getText(R.string.keyboard_layout_set)));
            body.addView(mLocaleButton,com.grapefruit.legacy.ui.SettingsUi.match());
            android.widget.LinearLayout.LayoutParams lp=com.grapefruit.legacy.ui.SettingsUi.match();lp.topMargin=t.dp(10);
            body.addView(mLayoutButton,lp);updateButtons();
            mGrapefruitDialog=com.grapefruit.legacy.ui.SettingsDialog.form(c,getDialogTitle(),body,
                    c.getText(isIncomplete()?R.string.add:R.string.save),()->onClick(null,DialogInterface.BUTTON_POSITIVE),
                    c.getText(isIncomplete()?android.R.string.cancel:R.string.remove),()->onClick(null,DialogInterface.BUTTON_NEGATIVE));
            mGrapefruitDialog.setOnCancelListener(this);
            if(!isIncomplete()) {
                android.widget.LinearLayout.LayoutParams cancel=com.grapefruit.legacy.ui.SettingsUi.match();
                cancel.topMargin=t.dp(10);
                body.addView(com.grapefruit.legacy.ui.SettingsUi.button(c,c.getText(android.R.string.cancel),false,
                        v->mGrapefruitDialog.dismiss()),cancel);
            }
        }

        public final boolean isIncomplete() {
            return mSubtype == null;
        }

        public InputMethodSubtype getSubtype() {
            return mSubtype;
        }

        public void setSubtype(final InputMethodSubtype subtype) {
            mPreviousSubtype = mSubtype;
            mSubtype = subtype;
            if (isIncomplete()) {
                setTitle(null);
                setDialogTitle(R.string.add_style);
                setKey(KEY_NEW_SUBTYPE);
            } else {
                final String displayName = SubtypeLocale.getSubtypeDisplayName(
                        subtype, getContext().getResources());
                setTitle(displayName);
                setDialogTitle(displayName);
                setKey(KEY_PREFIX + subtype.getLocale() + "_"
                        + SubtypeLocale.getKeyboardLayoutSetName(subtype));
            }
        }

        public void revert() {
            setSubtype(mPreviousSubtype);
        }

        public boolean hasBeenModified() {
            return mSubtype != null && !mSubtype.equals(mPreviousSubtype);
        }

        @Override
        protected View onCreateDialogView() {
            final View v = new View(getContext());
            mSubtypeLocaleSpinner = new Spinner(getContext());
            mSubtypeLocaleSpinner.setAdapter(mProxy.getSubtypeLocaleAdapter());
            mKeyboardLayoutSetSpinner = new Spinner(getContext());
            mKeyboardLayoutSetSpinner.setAdapter(mProxy.getKeyboardLayoutSetAdapter());
            return v;
        }

        @Override
        protected void onPrepareDialogBuilder(final AlertDialog.Builder builder) {
            final Context context = builder.getContext();
            builder.setCancelable(true).setOnCancelListener(this);
            if (isIncomplete()) {
                builder.setPositiveButton(R.string.add, this)
                        .setNegativeButton(android.R.string.cancel, this);
            } else {
                builder.setPositiveButton(R.string.save, this)
                        .setNeutralButton(android.R.string.cancel, this)
                        .setNegativeButton(R.string.remove, this);
                final SubtypeLocaleItem localeItem = SubtypeLocaleAdapter.createItem(
                        context, mSubtype.getLocale());
                final KeyboardLayoutSetItem layoutItem = new KeyboardLayoutSetItem(mSubtype);
                setSpinnerPosition(mSubtypeLocaleSpinner, localeItem);
                setSpinnerPosition(mKeyboardLayoutSetSpinner, layoutItem);
            }
        }

        private static void setSpinnerPosition(final Spinner spinner, final Object itemToSelect) {
            final SpinnerAdapter adapter = spinner.getAdapter();
            final int count = adapter.getCount();
            for (int i = 0; i < count; i++) {
                final Object item = spinner.getItemAtPosition(i);
                if (item.equals(itemToSelect)) {
                    spinner.setSelection(i);
                    return;
                }
            }
        }

        @Override
        public void onCancel(final DialogInterface dialog) {
            if (isIncomplete()) {
                mProxy.onRemovePressed(this);
            }
        }

        @Override
        public void onClick(final DialogInterface dialog, final int which) {
            super.onClick(dialog, which);
            switch (which) {
            case DialogInterface.BUTTON_POSITIVE:
                final boolean isEditing = !isIncomplete();
                final SubtypeLocaleItem locale =
                        (SubtypeLocaleItem) mSubtypeLocaleSpinner.getSelectedItem();
                final KeyboardLayoutSetItem layout =
                        (KeyboardLayoutSetItem) mKeyboardLayoutSetSpinner.getSelectedItem();
                final InputMethodSubtype subtype = AdditionalSubtype.createAdditionalSubtype(
                        locale.first, layout.first, ASCII_CAPABLE);
                setSubtype(subtype);
                notifyChanged();
                if (isEditing) {
                    mProxy.onSavePressed(this);
                } else {
                    mProxy.onAddPressed(this);
                }
                break;
            case DialogInterface.BUTTON_NEUTRAL:
                // Nothing to do
                break;
            case DialogInterface.BUTTON_NEGATIVE:
                mProxy.onRemovePressed(this);
                break;
            }
        }

        private static int getSpinnerPosition(final Spinner spinner) {
            if (spinner == null) return -1;
            return spinner.getSelectedItemPosition();
        }

        private static void setSpinnerPosition(final Spinner spinner, final int position) {
            if (spinner == null || position < 0) return;
            spinner.setSelection(position);
        }

        @Override
        protected Parcelable onSaveInstanceState() {
            final Parcelable superState = super.onSaveInstanceState();
            final Dialog dialog = getDialog();
            if (dialog == null || !dialog.isShowing()) {
                return superState;
            }

            final SavedState myState = new SavedState(superState);
            myState.mSubtype = mSubtype;
            myState.mSubtypeLocaleSelectedPos = getSpinnerPosition(mSubtypeLocaleSpinner);
            myState.mKeyboardLayoutSetSelectedPos = getSpinnerPosition(mKeyboardLayoutSetSpinner);
            return myState;
        }

        @Override
        protected void onRestoreInstanceState(final Parcelable state) {
            if (!(state instanceof SavedState)) {
                super.onRestoreInstanceState(state);
                return;
            }

            final SavedState myState = (SavedState) state;
            super.onRestoreInstanceState(myState.getSuperState());
            setSubtype(myState.mSubtype);
            showDialog(null);
            setSpinnerPosition(mSubtypeLocaleSpinner, myState.mSubtypeLocaleSelectedPos);
            setSpinnerPosition(mKeyboardLayoutSetSpinner, myState.mKeyboardLayoutSetSelectedPos);
            updateButtons();
        }

        static final class SavedState extends Preference.BaseSavedState {
            InputMethodSubtype mSubtype;
            int mSubtypeLocaleSelectedPos;
            int mKeyboardLayoutSetSelectedPos;

            public SavedState(final Parcelable superState) {
                super(superState);
            }

            @Override
            public void writeToParcel(final Parcel dest, final int flags) {
                super.writeToParcel(dest, flags);
                dest.writeInt(mSubtypeLocaleSelectedPos);
                dest.writeInt(mKeyboardLayoutSetSelectedPos);
                dest.writeParcelable(mSubtype, 0);
            }

            public SavedState(final Parcel source) {
                super(source);
                mSubtypeLocaleSelectedPos = source.readInt();
                mKeyboardLayoutSetSelectedPos = source.readInt();
                mSubtype = (InputMethodSubtype)source.readParcelable(null);
            }

            @SuppressWarnings("hiding")
            public static final Parcelable.Creator<SavedState> CREATOR =
                    new Parcelable.Creator<SavedState>() {
                        @Override
                        public SavedState createFromParcel(final Parcel source) {
                            return new SavedState(source);
                        }

                        @Override
                        public SavedState[] newArray(final int size) {
                            return new SavedState[size];
                        }
                    };
        }
    }

    public AdditionalSubtypeSettings() {
        // Empty constructor for fragment generation.
    }

    @Override public View onCreateView(android.view.LayoutInflater inflater,android.view.ViewGroup parent,Bundle state) {
        mGrapefruitSettings=new com.grapefruit.legacy.ui.GrapefruitPreferences(this);
        mGrapefruitSettings.setRootTitle(getText(R.string.custom_input_styles_title));
        mGrapefruitSettings.setHeaderAction(getText(R.string.add_style),()->addSubtype());
        return mGrapefruitSettings.createView(false);
    }
    @Override public void onDestroyView() {
        if(mGrapefruitSettings!=null)mGrapefruitSettings.close();mGrapefruitSettings=null;
        if(mSubtypeEnablerNotificationDialog!=null)mSubtypeEnablerNotificationDialog.dismiss();
        PreferenceGroup group=getPreferenceScreen();
        for(int i=0;i<group.getPreferenceCount();i++) {
            Preference pref=group.getPreference(i);
            if(pref instanceof SubtypePreference)((SubtypePreference)pref).closeGrapefruitDialogs();
        }
        super.onDestroyView();
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SubtypeLocale.init(getActivity());

        addPreferencesFromResource(R.xml.additional_subtype_settings);
        setHasOptionsMenu(false);

        mPrefs = getPreferenceManager().getSharedPreferences();
    }

    @Override
    public void onActivityCreated(final Bundle savedInstanceState) {
        final Context context = getActivity();
        mSubtypeLocaleAdapter = new SubtypeLocaleAdapter(context);
        mKeyboardLayoutSetAdapter = new KeyboardLayoutSetAdapter(context);

        final String prefSubtypes =
                SettingsValues.getPrefAdditionalSubtypes(mPrefs, getResources());
        setPrefSubtypes(prefSubtypes, context);

        mIsAddingNewSubtype = (savedInstanceState != null)
                && savedInstanceState.containsKey(KEY_IS_ADDING_NEW_SUBTYPE);
        if (mIsAddingNewSubtype) {
            getPreferenceScreen().addPreference(
                    SubtypePreference.newIncompleteSubtypePreference(context, mSubtypeProxy));
        }

        super.onActivityCreated(savedInstanceState);
        mGrapefruitSettings.bind(savedInstanceState);

        if (savedInstanceState != null && savedInstanceState.containsKey(
                KEY_IS_SUBTYPE_ENABLER_NOTIFICATION_DIALOG_OPEN)) {
            mSubtypePreferenceKeyForSubtypeEnabler = savedInstanceState.getString(
                    KEY_SUBTYPE_FOR_SUBTYPE_ENABLER);
            final SubtypePreference subtypePref = (SubtypePreference)findPreference(
                    mSubtypePreferenceKeyForSubtypeEnabler);
            mSubtypeEnablerNotificationDialog = createDialog(subtypePref);
            mSubtypeEnablerNotificationDialog.show();
        }
    }

    @Override
    public void onSaveInstanceState(final Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mIsAddingNewSubtype) {
            outState.putBoolean(KEY_IS_ADDING_NEW_SUBTYPE, true);
        }
        if (mSubtypeEnablerNotificationDialog != null
                && mSubtypeEnablerNotificationDialog.isShowing()) {
            outState.putBoolean(KEY_IS_SUBTYPE_ENABLER_NOTIFICATION_DIALOG_OPEN, true);
            outState.putString(
                    KEY_SUBTYPE_FOR_SUBTYPE_ENABLER, mSubtypePreferenceKeyForSubtypeEnabler);
        }
    }

    private final SubtypeDialogProxy mSubtypeProxy = new SubtypeDialogProxy() {
        @Override
        public void onRemovePressed(final SubtypePreference subtypePref) {
            mIsAddingNewSubtype = false;
            final PreferenceGroup group = getPreferenceScreen();
            group.removePreference(subtypePref);
            ImfUtils.setAdditionalInputMethodSubtypes(getActivity(), getSubtypes());
        }

        @Override
        public void onSavePressed(final SubtypePreference subtypePref) {
            final InputMethodSubtype subtype = subtypePref.getSubtype();
            if (!subtypePref.hasBeenModified()) {
                return;
            }
            if (findDuplicatedSubtype(subtype) == null) {
                ImfUtils.setAdditionalInputMethodSubtypes(getActivity(), getSubtypes());
                return;
            }

            // Saved subtype is duplicated.
            final PreferenceGroup group = getPreferenceScreen();
            group.removePreference(subtypePref);
            subtypePref.revert();
            group.addPreference(subtypePref);
            showSubtypeAlreadyExistsToast(subtype);
        }

        @Override
        public void onAddPressed(final SubtypePreference subtypePref) {
            mIsAddingNewSubtype = false;
            final InputMethodSubtype subtype = subtypePref.getSubtype();
            if (findDuplicatedSubtype(subtype) == null) {
                ImfUtils.setAdditionalInputMethodSubtypes(getActivity(), getSubtypes());
                mSubtypePreferenceKeyForSubtypeEnabler = subtypePref.getKey();
                mSubtypeEnablerNotificationDialog = createDialog(subtypePref);
                mSubtypeEnablerNotificationDialog.show();
                return;
            }

            // Newly added subtype is duplicated.
            final PreferenceGroup group = getPreferenceScreen();
            group.removePreference(subtypePref);
            showSubtypeAlreadyExistsToast(subtype);
        }

        @Override
        public SubtypeLocaleAdapter getSubtypeLocaleAdapter() {
            return mSubtypeLocaleAdapter;
        }

        @Override
        public KeyboardLayoutSetAdapter getKeyboardLayoutSetAdapter() {
            return mKeyboardLayoutSetAdapter;
        }
    };

    private void showSubtypeAlreadyExistsToast(final InputMethodSubtype subtype) {
        final Context context = getActivity();
        final Resources res = context.getResources();
        final String message = res.getString(R.string.custom_input_style_already_exists,
                SubtypeLocale.getSubtypeDisplayName(subtype, res));
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    private InputMethodSubtype findDuplicatedSubtype(final InputMethodSubtype subtype) {
        final String localeString = subtype.getLocale();
        final String keyboardLayoutSetName = SubtypeLocale.getKeyboardLayoutSetName(subtype);
        return ImfUtils.findSubtypeByLocaleAndKeyboardLayoutSet(
                getActivity(), localeString, keyboardLayoutSetName);
    }

    private Dialog createDialog(final SubtypePreference subtypePref) {
        final Context c=getActivity();com.grapefruit.legacy.GrapefruitTheme t=new com.grapefruit.legacy.GrapefruitTheme(c);
        android.widget.TextView message=com.grapefruit.legacy.ui.SettingsUi.text(c,
                getText(R.string.custom_input_style_note_message),15,t.secondary,false);
        return com.grapefruit.legacy.ui.SettingsDialog.form(c,getText(R.string.custom_input_styles_title),message,
                getText(R.string.enable),()->{
                    final Intent intent=CompatUtils.getInputLanguageSelectionIntent(ImfUtils.getInputMethodIdOfThisIme(c),
                            Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED|Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(intent);
                },getText(R.string.not_now),null);
    }

    private void setPrefSubtypes(final String prefSubtypes, final Context context) {
        final PreferenceGroup group = getPreferenceScreen();
        group.removeAll();
        final InputMethodSubtype[] subtypesArray =
                AdditionalSubtype.createAdditionalSubtypesArray(prefSubtypes);
        for (final InputMethodSubtype subtype : subtypesArray) {
            final SubtypePreference pref = new SubtypePreference(
                    context, subtype, mSubtypeProxy);
            group.addPreference(pref);
        }
    }

    private InputMethodSubtype[] getSubtypes() {
        final PreferenceGroup group = getPreferenceScreen();
        final ArrayList<InputMethodSubtype> subtypes = CollectionUtils.newArrayList();
        final int count = group.getPreferenceCount();
        for (int i = 0; i < count; i++) {
            final Preference pref = group.getPreference(i);
            if (pref instanceof SubtypePreference) {
                final SubtypePreference subtypePref = (SubtypePreference)pref;
                // We should not save newly adding subtype to preference because it is incomplete.
                if (subtypePref.isIncomplete()) continue;
                subtypes.add(subtypePref.getSubtype());
            }
        }
        return subtypes.toArray(new InputMethodSubtype[subtypes.size()]);
    }

    @Override
    public void onPause() {
        super.onPause();
        final String oldSubtypes = SettingsValues.getPrefAdditionalSubtypes(mPrefs, getResources());
        final InputMethodSubtype[] subtypes = getSubtypes();
        final String prefSubtypes = AdditionalSubtype.createPrefSubtypes(subtypes);
        if (prefSubtypes.equals(oldSubtypes)) {
            return;
        }

        final SharedPreferences.Editor editor = mPrefs.edit();
        try {
            editor.putString(Settings.PREF_CUSTOM_INPUT_STYLES, prefSubtypes);
        } finally {
            editor.apply();
        }
        ImfUtils.setAdditionalInputMethodSubtypes(getActivity(), subtypes);
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        final MenuItem addSubtypeMenu = menu.add(0, MENU_ADD_SUBTYPE, 0, R.string.add_style);
        addSubtypeMenu.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
    }

    private void addSubtype() {
        final SubtypePreference pref=SubtypePreference.newIncompleteSubtypePreference(getActivity(),mSubtypeProxy);
        getPreferenceScreen().addPreference(pref);mIsAddingNewSubtype=true;pref.show();
    }
    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == MENU_ADD_SUBTYPE) {
            addSubtype();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
