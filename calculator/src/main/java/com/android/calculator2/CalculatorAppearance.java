package com.android.calculator2;

import android.app.Activity;
import android.app.AlertDialog;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Button;
import android.widget.ImageView;
import com.grapefruit.legacy.GrapefruitTheme;
import com.grapefruitmusic.ui.Fonts;

final class CalculatorAppearance {
    static void apply(View root) {
        GrapefruitTheme t=new GrapefruitTheme(root.getContext());
        apply(root,t);
        root.setBackgroundColor(t.bg);
    }
    private static void apply(View v,GrapefruitTheme t) {
        if(v instanceof Button || v instanceof android.widget.ImageButton) {
            boolean equal=v.getId()==R.id.equal;
            v.setBackground(t.key(equal));
            if(v instanceof TextView) {
                TextView label=(TextView)v;label.setTextColor(equal?t.onAccent:t.text);
                label.setTypeface(Fonts.medium());label.setShadowLayer(0,0,0,0);
            } else ((ImageView)v).setColorFilter(t.text);
        } else if(v instanceof TextView) {
            TextView text=(TextView)v;text.setTextColor(t.text);text.setTypeface(Fonts.regular());
            text.setBackgroundColor(t.surface);text.setShadowLayer(0,0,0,0);
        } else if(v instanceof ViewGroup) {
            v.setBackgroundColor(t.bg);
            ViewGroup group=(ViewGroup)v;
            for(int i=0;i<group.getChildCount();i++)apply(group.getChildAt(i),t);
        } else v.setBackgroundColor(t.bg);
        if(v.getId()==R.id.overflow_menu) {
            v.setContentDescription(v.getContext().getString(R.string.grapefruit_options));
            ((ImageView)v).setColorFilter(t.accent);
        }
    }
    static void chooseTheme(Activity activity) {
        GrapefruitTheme t=new GrapefruitTheme(activity);
        new AlertDialog.Builder(activity).setTitle(R.string.grapefruit_theme)
            .setSingleChoiceItems(GrapefruitTheme.NAMES,t.id,(dialog,which)->{
                if(which!=t.id) {
                    PreferenceManager.getDefaultSharedPreferences(activity).edit()
                        .putString(GrapefruitTheme.PREF_THEME,Integer.toString(which)).apply();
                    activity.recreate();
                }
                dialog.dismiss();
            }).setNegativeButton(android.R.string.cancel,null).show();
    }
}
