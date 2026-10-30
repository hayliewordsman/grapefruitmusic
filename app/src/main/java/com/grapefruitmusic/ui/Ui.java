package com.grapefruitmusic.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.grapefruitmusic.R;
import com.grapefruitmusic.ui.widget.ArtworkView;
import com.grapefruitmusic.ui.widget.IconView;
import com.grapefruitmusic.ui.widget.Icons;
import com.grapefruitmusic.util.Prefs;

/** Theme and small view-building helpers for the iOS 10 look. */
public final class Ui {
    private Ui() {
    }

    /** The theme showing (a Prefs.THEME_* other than THEME_SYSTEM). */
    public static int theme(Context c) {
        int t = Prefs.theme();
        return t >= Prefs.THEME_STARLIGHT && t <= Prefs.THEME_GRAPEFRUIT_DARK ? t : Prefs.THEME_GRAPEFRUIT_DARK;
    }

    public static void applyTheme(Activity a) {
        switch (theme(a)) {
            case Prefs.THEME_STARLIGHT:
                a.setTheme(R.style.Theme_Grapefruit_Light);
                break;
            case Prefs.THEME_PITCH_BLACK:
                a.setTheme(R.style.Theme_Grapefruit_Dark);
                break;
            case Prefs.THEME_GRAPEFRUIT_DARK:
                a.setTheme(R.style.Theme_Grapefruit_GrapefruitDark);
                break;
            default:
                a.setTheme(R.style.Theme_Grapefruit_GrapefruitLight);
                break;
        }
    }

    /** Grapefruit's own dialog (card or bottom sheet), the same on every Android version. */
    public static GrapefruitDialog.Builder dialog(Context c) {
        return new GrapefruitDialog.Builder(c);
    }

    public static int color(Context c, int attr) {
        TypedValue tv = new TypedValue();
        c.getTheme().resolveAttribute(attr, tv, true);
        return tv.data;
    }

    public static int resource(Context c, int attr) {
        TypedValue tv = new TypedValue();
        c.getTheme().resolveAttribute(attr, tv, true);
        return tv.resourceId;
    }

    public static int accent(Context c) {
        return color(c, R.attr.gfAccent);
    }

    public static int dp(Context c, float dp) {
        return Math.round(dp * c.getResources().getDisplayMetrics().density);
    }

    public static TextView text(Context c, float sp, int colorAttr, boolean bold) {
        TextView t = new TextView(c);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color(c, colorAttr));
        t.setTypeface(android.graphics.Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    public static android.graphics.drawable.Drawable actionBackground(Context c) {
        android.graphics.drawable.StateListDrawable states=new android.graphics.drawable.StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed},GrapefruitDialog.rounded(color(c,R.attr.gfControlPressed),dp(c,12)));
        states.addState(new int[]{android.R.attr.state_focused},GrapefruitDialog.rounded(color(c,R.attr.gfControlPressed),dp(c,12)));
        states.addState(new int[]{},GrapefruitDialog.rounded(color(c,R.attr.gfControl),dp(c,12)));
        return states;
    }

    /** Shared themed action for category, podcast and radio pages. */
    public static TextView button(Context c, String label, View.OnClickListener action) {
        TextView t = text(c, 15, R.attr.gfAccent, true);
        t.setText(label);
        t.setSingleLine(false);
        t.setEllipsize(null);
        t.setGravity(android.view.Gravity.CENTER);
        t.setMinHeight(dp(c, 48));
        t.setPadding(dp(c, 12), dp(c, 10), dp(c, 12), dp(c, 10));
        t.setBackground(actionBackground(c));
        t.setOnClickListener(action);
        return t;
    }

    /** Equal-height horizontal actions; labels may wrap without shifting their backgrounds. */
    public static LinearLayout actionRow(Context c) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBaselineAligned(false);
        return row;
    }

    public static View separator(Context c, int insetLeftDp) {
        View v = new View(c);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Math.max(1, dp(c, 0.5f)));
        lp.leftMargin = dp(c, insetLeftDp);
        v.setLayoutParams(lp);
        v.setBackgroundColor(color(c, R.attr.gfSeparator));
        return v;
    }

    public static IconView icon(Context c, int icon, int color, int sizeDp) {
        IconView v = new IconView(c);
        v.setIcon(icon);
        v.setColor(color);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return v;
    }

    /** The "⋯" button at the end of a song row (same menu as a long press). */
    public static IconView moreButton(Context c) {
        IconView more = new IconView(c);
        more.setIcon(Icons.MORE);
        more.setColor(color(c, R.attr.gfTextSecondary));
        int pad = dp(c, 9);
        more.setPadding(pad, pad, pad, pad);
        more.setContentDescription("More");
        more.setLayoutParams(new LinearLayout.LayoutParams(dp(c, 38), dp(c, 38)));
        return more;
    }

    public static ArtworkView artwork(Context c, int sizeDp) {
        ArtworkView a = new ArtworkView(c);
        a.setPlaceholderColors(color(c, R.attr.gfFill), color(c, R.attr.gfTextSecondary));
        if (sizeDp > 0) a.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return a;
    }

    /**
     * Page header: optional "‹ Back" link in the accent color, then a big bold title.
     */
    public static LinearLayout header(Context c, String backLabel, String title, View.OnClickListener onBack) {
        LinearLayout h = new LinearLayout(c);
        h.setOrientation(LinearLayout.VERTICAL);
        h.setPadding(dp(c, 16), dp(c, backLabel != null ? 4 : 18), dp(c, 16), dp(c, 6));
        if (backLabel != null) {
            LinearLayout back = new LinearLayout(c);
            back.setOrientation(LinearLayout.HORIZONTAL);
            back.setGravity(Gravity.CENTER_VERTICAL);
            back.setPadding(0, dp(c, 6), dp(c, 12), dp(c, 6));
            back.setBackgroundResource(resource(c, R.attr.gfRowBackground));
            IconView chevron = icon(c, Icons.CHEVRON_LEFT, accent(c), 22);
            back.addView(chevron);
            TextView label = text(c, 17, R.attr.gfAccent, false);
            label.setText(backLabel);
            back.addView(label);
            back.setOnClickListener(onBack);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = -dp(c, 6);
            h.addView(back, lp);
        }
        if (title != null) {
            TextView t = text(c, 32, R.attr.gfTextPrimary, true);
            t.setSingleLine(false);
            t.setMaxLines(2);
            t.setText(title);
            t.setPadding(0, dp(c, 4), 0, 0);
            h.addView(t);
        }
        return h;
    }

    /** Accent text button for a page's top line ("Edit", "Done", "Cancel"). */
    public static TextView textButton(Context c, String label, boolean bold) {
        TextView t = text(c, 17, R.attr.gfAccent, bold);
        t.setText(label);
        t.setMinHeight(dp(c, 48));
        t.setMinWidth(dp(c, 48));
        t.setSingleLine(false);
        t.setEllipsize(null);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(dp(c, 10), dp(c, 6), dp(c, 10), dp(c, 6));
        t.setBackgroundResource(resource(c, R.attr.gfRowBackground));
        return t;
    }

    /** Rounded gray text field. */
    public static EditText field(Context c, float sp, boolean bold, String hint) {
        EditText e = new EditText(c);
        e.setBackgroundResource(resource(c, R.attr.gfFieldBackground));
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        e.setTextColor(color(c, R.attr.gfTextPrimary));
        e.setHintTextColor(color(c, R.attr.gfTextSecondary));
        if (bold) e.setTypeface(Typeface.DEFAULT_BOLD);
        e.setHint(hint);
        return e;
    }

    /** Bold section title ("Recently Added"). */
    public static TextView sectionTitle(Context c, String title) {
        TextView t = text(c, 22, R.attr.gfTextPrimary, true);
        t.setText(title);
        t.setPadding(dp(c, 16), dp(c, 22), dp(c, 16), dp(c, 8));
        return t;
    }

    /** Gray text for loading/empty/error states. */
    public static TextView status(Context c) {
        TextView t = text(c, 15, R.attr.gfTextSecondary, false);
        t.setSingleLine(false);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 24), dp(c, 24), dp(c, 24), dp(c, 24));
        return t;
    }

    /** Rounded gray button with accent text and icon ("Play", "Shuffle"). */
    public static LinearLayout pill(Context c, int icon, String label) {
        LinearLayout p = new LinearLayout(c);
        p.setOrientation(LinearLayout.HORIZONTAL);
        p.setBaselineAligned(false);
        p.setMinimumHeight(dp(c, 48));
        p.setGravity(Gravity.CENTER);
        p.setBackgroundResource(resource(c, R.attr.gfPillBackground));
        p.setPadding(dp(c, 12), dp(c, 10), dp(c, 12), dp(c, 10));
        IconView i = icon(c, icon, accent(c), 18);
        p.addView(i);
        TextView t = text(c, 16, R.attr.gfAccent, true);
        t.setText(label);
        t.setSingleLine(false);
        t.setEllipsize(null);
        t.setPadding(dp(c, 8), 0, 0, 0);
        p.addView(t);
        return p;
    }
}
