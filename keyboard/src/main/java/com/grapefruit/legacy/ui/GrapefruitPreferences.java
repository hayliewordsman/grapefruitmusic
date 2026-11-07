package com.grapefruit.legacy.ui;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.database.DataSetObserver;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.SettingsActivity;
import com.grapefruit.legacy.GrapefruitTheme;
import java.util.ArrayList;

/** Keeps AOSP's preference storage/dependencies, renders every row and choice using Audio's UI. */
public final class GrapefruitPreferences {
    private final PreferenceFragment owner;
    private final ArrayList<Preference> entries=new ArrayList<>();
    private final ArrayList<PreferenceScreen> stack=new ArrayList<>();
    private final Rows rows=new Rows();
    private ListView list;
    private FrameLayout header;
    private EditText trial;
    private ListAdapter model;
    private Dialog dialog;
    private boolean refreshing;
    private CharSequence rootTitle,actionTitle;
    private Runnable action;
    public void setRootTitle(CharSequence title) {rootTitle=title;}
    public void setHeaderAction(CharSequence title,Runnable click) {actionTitle=title;action=click;}
    private final DataSetObserver changes=new DataSetObserver() {
        @Override public void onChanged() {refresh();}
    };
    public GrapefruitPreferences(PreferenceFragment fragment) {owner=fragment;}
    public View createView(boolean withTrial) {
        Context c=owner.getActivity();
        list=new ListView(c) {
            @Override protected void onSizeChanged(int w,int h,int oldW,int oldH) {
                super.onSizeChanged(w,h,oldW,oldH);
                GrapefruitTheme t=new GrapefruitTheme(getContext());
                int side=Math.max(0,(w-t.dp(640))/2);setPadding(side,0,side,t.dp(32));
            }
        };
        list.setId(android.R.id.list);list.setTag("grapefruit-settings-list");list.setItemsCanFocus(true);list.setDivider(null);
        list.setSelector(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        list.setCacheColorHint(Color.TRANSPARENT);list.setOverScrollMode(View.OVER_SCROLL_NEVER);
        list.setVerticalScrollBarEnabled(false);list.setSoundEffectsEnabled(false);
        header=new FrameLayout(c);list.addHeaderView(header,null,false);
        if(withTrial) {
            FrameLayout footer=new FrameLayout(c);GrapefruitTheme t=new GrapefruitTheme(c);
            footer.setPadding(t.dp(12),t.dp(20),t.dp(12),t.dp(4));
            trial=new EditText(c);trial.setId(R.id.grapefruit_trial);trial.setHint(R.string.grapefruit_try);
            trial.setTypeface(com.grapefruitmusic.ui.Fonts.regular());trial.setMinLines(2);
            trial.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            trial.setPadding(t.dp(20),t.dp(16),t.dp(20),t.dp(16));trial.setSoundEffectsEnabled(false);
            footer.addView(trial,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
            list.addFooterView(footer,null,false);
        }
        return list;
    }
    public void bind(Bundle saved) {
        ListAdapter next=owner.getPreferenceScreen().getRootAdapter();
        if(model!=null)model.unregisterDataSetObserver(changes);
        model=next;model.registerDataSetObserver(changes);
        stack.clear();stack.add(owner.getPreferenceScreen());
        if(saved!=null) {
            String key=saved.getString("grapefruit_settings_screen");Preference p=key==null?null:owner.findPreference(key);
            if(p instanceof PreferenceScreen)stack.add((PreferenceScreen)p);
        }
        list.setAdapter(rows);
        list.setOnItemClickListener((parent,view,position,id)->{
            Object item=parent.getItemAtPosition(position);
            if(item instanceof Preference)activate((Preference)item);
        });
        refresh();
    }
    public void save(Bundle state) {
        if(stack.size()>1)state.putString("grapefruit_settings_screen",screen().getKey());
    }
    public void close() {
        if(model!=null)model.unregisterDataSetObserver(changes);
        model=null;if(dialog!=null)dialog.dismiss();dialog=null;
    }
    private PreferenceScreen screen() {return stack.get(stack.size()-1);}
    public boolean back() {
        if(stack.size()<2)return false;stack.remove(stack.size()-1);refresh();list.setSelection(0);return true;
    }
    private void flatten(PreferenceGroup group) {
        for(int i=0;i<group.getPreferenceCount();i++) {
            Preference p=group.getPreference(i);entries.add(p);
            if(p instanceof PreferenceCategory)flatten((PreferenceGroup)p);
        }
    }
    public void refresh() {
        if(list==null || stack.isEmpty() || refreshing || owner.getActivity()==null)return;
        refreshing=true;
        try {
            Context c=owner.getActivity();GrapefruitTheme t=new GrapefruitTheme(c);
            list.setBackgroundColor(t.bg);entries.clear();flatten(screen());
            LinearLayout heading=SettingsUi.vertical(c);
            heading.setPadding(t.dp(16),t.dp(stack.size()>1?8:20),t.dp(16),t.dp(8));
            if(stack.size()>1) {
                TextView back=SettingsUi.button(c,"‹",false,v->back());back.setTextSize(28);
                back.setContentDescription(c.getString(R.string.grapefruit_back));
                LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(t.dp(44),t.dp(44));bp.bottomMargin=t.dp(6);
                heading.addView(back,bp);
            }
            CharSequence title=stack.size()>1?screen().getTitle():rootTitle!=null?rootTitle:c.getString(R.string.grapefruit_settings_title);
            TextView label=SettingsUi.text(c,title,30,t.text,true);label.setId(R.id.grapefruit_settings_title);
            heading.addView(label,SettingsUi.match());
            if(action!=null && stack.size()==1) {
                LinearLayout.LayoutParams button=SettingsUi.match();button.topMargin=t.dp(16);
                heading.addView(SettingsUi.button(c,actionTitle,true,v->action.run()),button);
            }
            header.removeAllViews();header.addView(heading);
            if(trial!=null) {
                trial.setTextColor(t.text);trial.setHintTextColor(t.secondary);trial.setHighlightColor(t.pressed);
                trial.setBackground(SettingsUi.shape(t.control,t.dp(24)));
                trial.setVisibility(stack.size()==1?View.VISIBLE:View.GONE);
            }
            rows.notifyDataSetChanged();
        } finally {refreshing=false;}
    }
    private boolean accepts(Preference p,Object value) {
        Preference.OnPreferenceChangeListener l=p.getOnPreferenceChangeListener();
        return l==null || l.onPreferenceChange(p,value);
    }
    private String listValue(ListPreference p) {
        Object stored=owner.getPreferenceManager().getSharedPreferences().getAll().get(p.getKey());
        return stored instanceof String?(String)stored:p.getValue();
    }
    private CharSequence value(Preference p) {
        if(p instanceof ListPreference) {
            ListPreference lp=(ListPreference)p;int i=lp.findIndexOfValue(listValue(lp));
            return i>=0 && lp.getEntries()!=null?lp.getEntries()[i]:p.getSummary();
        }
        return p.getSummary();
    }
    public void activate(Preference p) {
        if(!p.isEnabled() || !p.isSelectable())return;
        if(p instanceof CheckBoxPreference) {
            CheckBoxPreference check=(CheckBoxPreference)p;boolean on=!check.isChecked();
            if(accepts(p,on))check.setChecked(on);refresh();return;
        }
        if(p instanceof ListPreference) {
            ListPreference choice=(ListPreference)p;
            dialog=SettingsDialog.choices(owner.getActivity(),p.getTitle(),choice.getEntries(),
                    choice.findIndexOfValue(listValue(choice)),which->{
                        String selected=choice.getEntryValues()[which].toString();
                        if(accepts(p,selected))choice.setValue(selected);refresh();
                    });return;
        }
        Preference.OnPreferenceClickListener click=p.getOnPreferenceClickListener();
        if(click!=null && click.onPreferenceClick(p)) {refresh();return;}
        if(p.getIntent()!=null) {owner.startActivity(p.getIntent());return;}
        if(p.getFragment()!=null) {
            Intent intent=new Intent(owner.getActivity(),SettingsActivity.class);
            intent.putExtra(android.preference.PreferenceActivity.EXTRA_SHOW_FRAGMENT,p.getFragment());
            owner.startActivity(intent);return;
        }
        if(p instanceof PreferenceScreen && ((PreferenceScreen)p).getPreferenceCount()>0) {
            stack.add((PreferenceScreen)p);refresh();list.setSelection(0);
        }
    }
    private final class Rows extends BaseAdapter {
        @Override public int getCount() {return entries.size();}
        @Override public Preference getItem(int i) {return entries.get(i);}
        @Override public long getItemId(int i) {return i;}
        @Override public boolean areAllItemsEnabled() {return false;}
        @Override public boolean isEnabled(int i) {return !(getItem(i) instanceof PreferenceCategory) && getItem(i).isEnabled();}
        @Override public View getView(int i,View reusable,ViewGroup parent) {
            Context c=owner.getActivity();GrapefruitTheme t=new GrapefruitTheme(c);Preference p=getItem(i);
            if(p instanceof PreferenceCategory) {
                TextView section=SettingsUi.text(c,p.getTitle(),15,t.accent,true);
                section.setPadding(t.dp(18),t.dp(24),t.dp(16),t.dp(6));return section;
            }
            FrameLayout inset=new FrameLayout(c);inset.setPadding(t.dp(12),t.dp(4),t.dp(12),t.dp(4));
            LinearLayout row=SettingsUi.row(c,v->activate(p));row.setTag("pref:"+p.getKey());
            LinearLayout words=SettingsUi.vertical(c);
            words.addView(SettingsUi.text(c,p.getTitle(),16,t.text,false));
            CharSequence summary=p instanceof CheckBoxPreference?p.getSummary():null;
            if(summary!=null && summary.length()>0)words.addView(SettingsUi.text(c,summary,13,t.secondary,false));
            row.addView(words,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
            if(p instanceof CheckBoxPreference) {
                Toggle toggle=new Toggle(c);toggle.setChecked(((CheckBoxPreference)p).isChecked());
                toggle.setOffColors(t.surface,t.outline);toggle.setAccentColors(t.accent,t.end);
                toggle.setContentDescription(p.getTitle());toggle.setEnabled(p.isEnabled());
                toggle.setListener((view,on)->{
                    if(accepts(p,on))((CheckBoxPreference)p).setChecked(on);refresh();
                });row.addView(toggle);
            } else {
                CharSequence summaryValue=value(p);
                TextView right=SettingsUi.text(c,summaryValue==null?"›":summaryValue,15,t.secondary,false);
                right.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);right.setMaxWidth(Math.min(t.dp(220),(int)(c.getResources().getDisplayMetrics().widthPixels*.4f)));
                right.setPadding(t.dp(8),0,0,0);row.addView(right);
            }
            row.setEnabled(p.isEnabled());row.setAlpha(p.isEnabled()?1:.45f);
            inset.addView(row,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
            return inset;
        }
    }
}
