package io.github.hayliewordsman.grapefruitai.ui;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Grapefruit's view vocabulary, built in code from the current {@link Palette}: big left-aligned
 * titles, pill buttons, chips and fields, round buttons, the accent as a gradient, and grouped
 * rows. GradientDrawables only, so it looks the same on every Android version from 4.2.
 */
public final class Ui {
    /** A radius past any height: GradientDrawable clamps it, so ends come out as semicircles. */
    public static final float PILL = 100000f;
    private static Palette palette = Palette.of(Palette.GRAPEFRUIT_DARK);

    private Ui() {
    }

    public static Palette p() {
        return palette;
    }

    public static void setPalette(Palette p) {
        palette = p;
    }

    public static int dp(Context c, float dp) {
        return Math.round(dp * c.getResources().getDisplayMetrics().density);
    }

    /** A wide screen (landscape, a tablet): tabs become a rail at the side. */
    public static boolean wide(Context c) {
        android.content.res.Configuration cfg = c.getResources().getConfiguration();
        return cfg.screenWidthDp >= 600 || cfg.screenWidthDp > cfg.screenHeightDp;
    }

    public static TextView text(Context c, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(Fonts.of(sp, bold));
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    public static TextView body(Context c, float sp, int color) {
        TextView t = text(c, sp, color, false);
        t.setSingleLine(false);
        t.setEllipsize(null);
        return t;
    }

    public static GradientDrawable shape(Context c, int fill, float radius, boolean outlined, boolean oval) {
        GradientDrawable g = new GradientDrawable();
        if (oval) g.setShape(GradientDrawable.OVAL);
        else g.setCornerRadius(radius);
        g.setColor(fill);
        if (outlined) g.setStroke(Math.max(1, dp(c, 1)), palette.outline);
        return g;
    }

    public static Drawable pressable(Context c, int fill, float radius, boolean outlined) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, shape(c, palette.controlPressed, radius, outlined, false));
        s.addState(new int[]{android.R.attr.state_focused}, shape(c, palette.controlPressed, radius, outlined, false));
        s.addState(new int[]{}, shape(c, fill, radius, outlined, false));
        return s;
    }

    public static GradientDrawable accentFill(Context c, float radius, boolean oval) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{palette.accent, palette.accentEnd});
        if (oval) g.setShape(GradientDrawable.OVAL);
        else g.setCornerRadius(radius);
        return g;
    }

    public static Drawable accentPressable(Context c, float radius, boolean oval) {
        StateListDrawable s = new StateListDrawable();
        GradientDrawable down = accentFill(c, radius, oval);
        down.setAlpha(200);
        s.addState(new int[]{android.R.attr.state_pressed}, down);
        s.addState(new int[]{-android.R.attr.state_enabled}, withAlpha(accentFill(c, radius, oval), 110));
        s.addState(new int[]{}, accentFill(c, radius, oval));
        return s;
    }

    private static GradientDrawable withAlpha(GradientDrawable g, int a) {
        g.setAlpha(a);
        return g;
    }

    /** A pill button; the page's main action in the accent gradient. */
    public static TextView button(Context c, String label, boolean main, View.OnClickListener click) {
        TextView t = text(c, 15, main ? palette.onAccent : palette.text, true);
        t.setText(label);
        t.setSingleLine(false);
        t.setEllipsize(null);
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(dp(c, 44));
        t.setMinWidth(dp(c, 64));
        t.setPadding(dp(c, 16), dp(c, 8), dp(c, 16), dp(c, 8));
        t.setBackground(main ? accentPressable(c, PILL, false) : pressable(c, palette.control, PILL, true));
        if (click != null) t.setOnClickListener(click);
        return t;
    }

    /** A pill chip: accent gradient when chosen, a hairline pill otherwise. */
    public static TextView chip(Context c, String label, boolean chosen) {
        TextView t = text(c, 14, palette.text, false);
        t.setTypeface(Fonts.medium());
        t.setText(label);
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(dp(c, 38));
        t.setMinWidth(dp(c, 48));
        t.setPadding(dp(c, 14), dp(c, 6), dp(c, 14), dp(c, 6));
        setChipChosen(t, chosen);
        return t;
    }

    public static void setChipChosen(TextView chip, boolean chosen) {
        Context c = chip.getContext();
        chip.setSelected(chosen);
        chip.setBackground(chosen ? accentPressable(c, PILL, false) : pressable(c, palette.control, PILL, true));
        chip.setTextColor(chosen ? palette.onAccent : palette.text);
    }

    public static HorizontalScrollView chipRow(Context c) {
        HorizontalScrollView scroll = new HorizontalScrollView(c);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(c, 12), dp(c, 4), dp(c, 8), dp(c, 4));
        scroll.addView(row);
        return scroll;
    }

    public static void addChip(HorizontalScrollView chips, View chip) {
        LinearLayout row = (LinearLayout) chips.getChildAt(0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(chips.getContext(), 8);
        row.addView(chip, lp);
    }

    public static IconView icon(Context c, int icon, int color, int sizeDp) {
        IconView v = new IconView(c);
        v.setIcon(icon);
        v.setColor(color);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return v;
    }

    /** A round button with an icon: hairline control circle, or the accent gradient. */
    public static IconView circle(Context c, int icon, int sizeDp, boolean accent, String description) {
        IconView b = new IconView(c);
        b.setIcon(icon);
        int pad = Math.round(dp(c, sizeDp) * 0.26f);
        b.setPadding(pad, pad, pad, pad);
        styleCircle(b, accent);
        b.setContentDescription(description);
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return b;
    }

    public static void styleCircle(IconView v, boolean accent) {
        Context c = v.getContext();
        if (accent) {
            v.setBackground(accentPressable(c, 0, true));
            v.setColor(palette.onAccent);
        } else {
            StateListDrawable s = new StateListDrawable();
            s.addState(new int[]{android.R.attr.state_pressed}, shape(c, palette.controlPressed, 0, true, true));
            s.addState(new int[]{}, shape(c, palette.control, 0, true, true));
            v.setBackground(s);
            v.setColor(palette.text);
        }
    }

    /** A rounded field, like Grapefruit's field_*.xml: control colour, pill or rounded box. */
    public static EditText field(Context c, String hint, boolean multiLine) {
        EditText e = new EditText(c);
        e.setBackground(shape(c, palette.control, multiLine ? dp(c, 22) : PILL, false, false));
        e.setPadding(dp(c, 16), dp(c, 10), dp(c, 16), dp(c, 10));
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        e.setTextColor(palette.text);
        e.setHintTextColor(palette.secondary);
        e.setTypeface(Fonts.regular());
        e.setHint(hint);
        e.setMinHeight(dp(c, 44));
        if (!multiLine) e.setSingleLine(true);
        return e;
    }

    public static View separator(Context c, int insetLeftDp) {
        View v = new View(c);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Math.max(1, dp(c, 0.5f)));
        lp.leftMargin = dp(c, insetLeftDp);
        v.setLayoutParams(lp);
        v.setBackgroundColor(palette.separator);
        return v;
    }

    /** A list row: nothing, and an inset pill while pressed. */
    public static Drawable rowBackground(Context c) {
        StateListDrawable s = new StateListDrawable();
        int side = dp(c, 4);
        s.addState(new int[]{android.R.attr.state_pressed}, new android.graphics.drawable.InsetDrawable(
                shape(c, palette.controlPressed, dp(c, 18), false, false), side, 0, side, 0) {
            @Override
            public boolean getPadding(android.graphics.Rect padding) {
                padding.set(0, 0, 0, 0);
                return false;
            }
        });
        s.addState(new int[]{}, new ColorDrawable(0));
        return s;
    }

    /** Page title: big and bold, left-aligned. */
    public static TextView title(Context c, String title) {
        TextView t = text(c, 28, palette.text, true);
        t.setText(title);
        return t;
    }

    public static TextView sectionTitle(Context c, String title) {
        TextView t = text(c, 15, palette.accent, true);
        t.setText(title);
        t.setPadding(dp(c, 18), dp(c, 20), dp(c, 16), dp(c, 6));
        return t;
    }

    /** Gray centred text for empty, loading and error states. */
    public static TextView status(Context c) {
        TextView t = body(c, 15, palette.secondary);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 24), dp(c, 32), dp(c, 24), dp(c, 24));
        return t;
    }

    /** A small status pill: a coloured dot and a label (provider/backend status). */
    public static LinearLayout statusPill(Context c) {
        LinearLayout p = new LinearLayout(c);
        p.setOrientation(LinearLayout.HORIZONTAL);
        p.setGravity(Gravity.CENTER_VERTICAL);
        p.setPadding(dp(c, 10), dp(c, 4), dp(c, 12), dp(c, 4));
        p.setBackground(shape(c, palette.control, PILL, true, false));
        View dot = new View(c);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(c, 8), dp(c, 8));
        lp.rightMargin = dp(c, 6);
        p.addView(dot, lp);
        TextView label = text(c, 12, palette.secondary, false);
        label.setTypeface(Fonts.medium());
        p.addView(label);
        setStatus(p, STATUS_UNKNOWN, "");
        return p;
    }

    public static final int STATUS_UNKNOWN = 0, STATUS_OK = 1, STATUS_BUSY = 2, STATUS_ERROR = 3;

    public static void setStatus(LinearLayout pill, int status, String label) {
        Context c = pill.getContext();
        int color;
        switch (status) {
            case STATUS_OK: color = 0xff3fbf6f; break;
            case STATUS_BUSY: color = 0xffe8b33c; break;
            case STATUS_ERROR: color = palette.danger; break;
            default: color = palette.secondary;
        }
        pill.getChildAt(0).setBackground(shape(c, color, 0, false, true));
        ((TextView) pill.getChildAt(1)).setText(label);
        pill.setContentDescription(label);
    }

    public static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    public static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    public static LinearLayout.LayoutParams weight(float w) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w);
    }

    public static LinearLayout vertical(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout horizontal(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.setBaselineAligned(false);
        return l;
    }

    public static void toast(Context c, String message) {
        android.widget.Toast.makeText(c, message, android.widget.Toast.LENGTH_SHORT).show();
    }

    public static void copy(Context c, String label, String text) {
        android.content.ClipboardManager cm =
                (android.content.ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(android.content.ClipData.newPlainText(label, text));
        toast(c, "Copied");
    }
}
