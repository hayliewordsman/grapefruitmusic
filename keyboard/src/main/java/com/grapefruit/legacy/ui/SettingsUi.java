package com.grapefruit.legacy.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.grapefruit.legacy.GrapefruitTheme;
import com.grapefruitmusic.ui.Fonts;

/** Audio's large headings, 56dp pill rows, Geist typography and palette-aware controls. */
public final class SettingsUi {
    private SettingsUi() {}
    public static TextView text(Context c,CharSequence value,int size,int color,boolean bold) {
        TextView t=new TextView(c);t.setText(value);t.setTextSize(size);t.setTextColor(color);
        t.setTypeface(Fonts.of(size,bold));t.setGravity(Gravity.CENTER_VERTICAL);
        t.setSoundEffectsEnabled(false);return t;
    }
    public static GradientDrawable shape(int color,float radius) {
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius);return d;
    }
    public static Drawable pressed(GrapefruitTheme t,int fill,float radius) {
        StateListDrawable d=new StateListDrawable();
        d.addState(new int[]{android.R.attr.state_pressed},shape(t.pressed,radius));
        d.addState(new int[]{android.R.attr.state_focused},shape(t.pressed,radius));
        d.addState(new int[]{},shape(fill,radius));return d;
    }
    public static LinearLayout row(Context c,View.OnClickListener click) {
        GrapefruitTheme t=new GrapefruitTheme(c);
        LinearLayout row=new LinearLayout(c);row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);row.setMinimumHeight(t.dp(56));
        row.setBackground(pressed(t,t.control,t.dp(28)));
        row.setPadding(t.dp(22),t.dp(8),t.dp(16),t.dp(8));row.setSoundEffectsEnabled(false);
        if(click!=null)row.setOnClickListener(click);return row;
    }
    public static TextView button(Context c,CharSequence title,boolean primary,View.OnClickListener click) {
        GrapefruitTheme t=new GrapefruitTheme(c);
        TextView b=text(c,title,16,primary?t.onAccent:t.text,true);
        b.setGravity(Gravity.CENTER);b.setMinHeight(t.dp(48));b.setPadding(t.dp(16),t.dp(10),t.dp(16),t.dp(10));
        if(primary) {
            StateListDrawable states=new StateListDrawable();
            GradientDrawable fill=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{t.accent,t.end});
            fill.setCornerRadius(t.dp(28));
            states.addState(new int[]{android.R.attr.state_pressed},shape(t.end,t.dp(28)));
            states.addState(new int[]{},fill);b.setBackground(states);
        } else b.setBackground(pressed(t,t.control,t.dp(28)));
        b.setFocusable(true);b.setOnClickListener(click);return b;
    }
    public static LinearLayout vertical(Context c) {
        LinearLayout v=new LinearLayout(c);v.setOrientation(LinearLayout.VERTICAL);return v;
    }
    public static LinearLayout.LayoutParams match() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
