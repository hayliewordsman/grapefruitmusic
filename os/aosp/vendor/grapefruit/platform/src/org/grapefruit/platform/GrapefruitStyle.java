// SPDX-License-Identifier: Apache-2.0
package org.grapefruit.platform;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;

/** Styles native widgets without replacing their listeners, values or service bindings. */
public final class GrapefruitStyle {
    private static Typeface regular;
    private static final java.util.WeakHashMap<View,Palette> styled = new java.util.WeakHashMap<View,Palette>();
    private GrapefruitStyle() {}
    public static int dp(Context c, int value) { return Math.round(value*c.getResources().getDisplayMetrics().density); }
    public static GradientDrawable pill(int color) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(100000); return d;
    }
    public static void apply(View root, Palette p) {
        if (regular == null) {
            try { regular = Typeface.createFromFile("/system/fonts/Geist-Regular.ttf"); }
            catch (RuntimeException absent) { regular = Typeface.SANS_SERIF; }
        }
        if (styled.get(root) != p) {
        styled.put(root,p);
        if (root instanceof TextView) {
            TextView text = (TextView)root;
            text.setTypeface(regular, text.getTypeface() == null ? 0 : text.getTypeface().getStyle());
            text.setTextColor(p.text);
        }
        if (root instanceof Button || root.getParent() instanceof AbsListView) root.setBackground(pill(p.surface));
        if (root instanceof Switch) {
            Switch toggle = (Switch)root;
            GradientDrawable knob = pill(p.text); knob.setSize(dp(root.getContext(),24),dp(root.getContext(),24));
            StateListDrawable track = new StateListDrawable();
            GradientDrawable on = pill(p.accent); on.setSize(dp(root.getContext(),48),dp(root.getContext(),28));
            GradientDrawable off = pill(p.control); off.setSize(dp(root.getContext(),48),dp(root.getContext(),28));
            track.addState(new int[]{android.R.attr.state_checked},on); track.addState(new int[]{},off);
            toggle.setThumbDrawable(knob); toggle.setTrackDrawable(track);
        }
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup)root;
            for (int i=0;i<group.getChildCount();i++) apply(group.getChildAt(i),p);
        }
    }
}
