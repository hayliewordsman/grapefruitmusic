package com.grapefruitlauncher;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;
import com.grapefruitmusic.ui.Fonts;

/** Audio's palette roles and pill geometry, with Blue Grapefruit's palette as Victory Blue. */
final class LauncherTheme {
    static final String[] NAMES = {"Grapefruit Dark", "Grapefruit Light", "Pitch Black", "Victory Blue"};
    final int id, bg, surface, control, pressed, text, secondary, accent, end, outline, onAccent;
    final boolean light;
    private final Context context;

    LauncherTheme(Context c, int choice) {
        context = c.getApplicationContext();
        id = choice >= 0 && choice < NAMES.length ? choice : 0;
        light = id == 1;
        // Columns: background, surface, control, pressed, text, secondary, accent, accent end,
        // outline, on-accent. Values match Audio's colors.xml and Blue Victory f066424.
        int[][] palettes = {
            {0xff1c0a0e, 0xff2e1218, 0xff421c24, 0xff522631, 0xfffff1e8, 0xffb88a8f, 0xffff7f6b, 0xfff2546b, 0xff46202a, 0xff2a0a10},
            {0xfffce4d9, 0xfffffaf6, 0xfffadccf, 0xfff3c5b3, 0xff4a1622, 0xff9a5c63, 0xffe83850, 0xffd92b4b, 0xfff3cdbf, 0xffffffff},
            {0xff000000, 0xff180a0e, 0xff261116, 0xff36181f, 0xfffff3ee, 0xffb8949a, 0xffff7f6b, 0xfff2546b, 0xff33181f, 0xff1a0a0d},
            {0xff07162b, 0xff102541, 0xff1c3b60, 0xff284f78, 0xffeaf4ff, 0xffa2bad8, 0xff67c7ff, 0xff398bff, 0xff294665, 0xff06172c}
        };
        int[] p = palettes[id];
        bg=p[0]; surface=p[1]; control=p[2]; pressed=p[3]; text=p[4]; secondary=p[5];
        accent=p[6]; end=p[7]; outline=p[8]; onAccent=p[9];
    }

    int dp(float n) { return Math.round(n * context.getResources().getDisplayMetrics().density); }
    GradientDrawable shape(int fill, int radius, boolean border) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill); d.setCornerRadius(radius >= 10000 ? radius : dp(radius));
        if (border) d.setStroke(Math.max(1, dp(1)), outline);
        return d;
    }
    GradientDrawable gradient() {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{accent,end});
        d.setCornerRadius(100000); return d;
    }
    StateListDrawable interactive(boolean selected) {
        StateListDrawable d = new StateListDrawable();
        GradientDrawable focus = selected ? gradient() : shape(control,100000,true);
        focus.setStroke(dp(2),accent);
        d.addState(new int[]{android.R.attr.state_focused},focus);
        d.addState(new int[]{android.R.attr.state_pressed},shape(pressed,100000,true));
        d.addState(new int[]{},selected ? gradient() : shape(control,100000,true));
        return d;
    }
    TextView text(String value, int sp, boolean bold) {
        TextView t = new TextView(context); t.setText(value); t.setTextSize(sp); t.setTextColor(text);
        t.setTypeface(Fonts.of(sp,bold)); t.setIncludeFontPadding(false); return t;
    }
    TextView chip(String value, boolean selected, View.OnClickListener action) {
        TextView t = text(value,14,false); t.setTypeface(Fonts.medium()); t.setGravity(Gravity.CENTER);
        t.setMinHeight(dp(48)); t.setPadding(dp(16),dp(10),dp(16),dp(10));
        t.setTextColor(selected ? onAccent : text); t.setBackground(interactive(selected));
        t.setSelected(selected); t.setOnClickListener(action); return t;
    }
}
