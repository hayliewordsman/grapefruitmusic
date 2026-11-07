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

/**
 * Theme and small view-building helpers. Since 5.0: big left-aligned titles, round buttons, pill
 * chips and buttons, and the accent as a gradient (accent to gfAccentEnd) for what's chosen or
 * playing. Everything is drawn with GradientDrawables and Grapefruit's own views, the same on
 * every Android version from 4.2.
 */
public final class Ui {
    private Ui() {
    }

    /**
     * The theme showing: Pitch Black, Grapefruit Light or Grapefruit Dark (the default). Starlight
     * was retired in 5.0 beta 9; anyone who had it gets Grapefruit Light, its nearest.
     */
    public static int theme(Context c) {
        int t = Prefs.theme();
        if (t == Prefs.THEME_STARLIGHT) return Prefs.THEME_GRAPEFRUIT_LIGHT;
        return t == Prefs.THEME_VICTORY_BLUE || t == Prefs.THEME_PITCH_BLACK || t == Prefs.THEME_GRAPEFRUIT_LIGHT || t == Prefs.THEME_GRAPEFRUIT_DARK
                ? t : Prefs.THEME_GRAPEFRUIT_DARK;
    }

    public static void applyTheme(Activity a) {
        switch (theme(a)) {
            case Prefs.THEME_VICTORY_BLUE:
                a.setTheme(R.style.Theme_Grapefruit_VictoryBlue);
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

    /**
     * A wide or square screen (a phone on its side, a tablet, a Unihertz Titan): the tabs are a rail
     * at the side, and Now Playing has two columns.
     */
    public static boolean wide(android.content.res.Configuration c) {
        return c.screenWidthDp >= c.screenHeightDp * 0.85f;
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
        t.setTypeface(Fonts.of(sp, bold));
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    /**
     * A corner radius past any control's height: GradientDrawable keeps a radius to half the shorter
     * side (on Android 4.2 too), so the ends come out as semicircles. Buttons, chips, fields and
     * bars are pills whatever their height, even when their text wraps.
     */
    public static final float PILL = 100000f;

    /** A pill-shaped row that darkens while pressed (Settings rows and the like). */
    public static android.graphics.drawable.Drawable actionBackground(Context c) {
        return pressable(c, color(c, R.attr.gfControl), dp(c, 28), false);
    }

    /** fill, rounded by radius (px), a shade darker while pressed; with a hairline if outlined. */
    static android.graphics.drawable.Drawable pressable(Context c, int fill, float radius, boolean outlined) {
        android.graphics.drawable.StateListDrawable states = new android.graphics.drawable.StateListDrawable();
        int[][] pressed = {{android.R.attr.state_pressed}, {android.R.attr.state_focused}};
        for (int[] state : pressed) {
            states.addState(state, shape(c, color(c, R.attr.gfControlPressed), radius, outlined, false));
        }
        states.addState(new int[]{}, shape(c, fill, radius, outlined, false));
        return states;
    }

    /** A rounded rectangle (or an oval), filled, with an optional hairline outline. */
    static android.graphics.drawable.GradientDrawable shape(Context c, int fill, float radius, boolean outlined,
                                                            boolean oval) {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        if (oval) g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        else g.setCornerRadius(radius);
        g.setColor(fill);
        if (outlined) g.setStroke(Math.max(1, dp(c, 1)), color(c, R.attr.gfOutline));
        return g;
    }

    /** The accent as a gradient (top left to bottom right), rounded by radius, or an oval. */
    public static android.graphics.drawable.GradientDrawable accentFill(Context c, float radius, boolean oval) {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                new int[]{accent(c), color(c, R.attr.gfAccentEnd)});
        if (oval) g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        else g.setCornerRadius(radius);
        return g;
    }

    /** The accent gradient, a little darker while pressed. */
    static android.graphics.drawable.Drawable accentPressable(Context c, float radius, boolean oval) {
        android.graphics.drawable.StateListDrawable states = new android.graphics.drawable.StateListDrawable();
        android.graphics.drawable.GradientDrawable down = accentFill(c, radius, oval);
        down.setAlpha(200);
        states.addState(new int[]{android.R.attr.state_pressed}, down);
        states.addState(new int[]{}, accentFill(c, radius, oval));
        return states;
    }

    /** Text and icons on the accent: white on the light themes, near-black on the dark ones. */
    public static int onAccent(Context c) {
        return color(c, R.attr.gfOnAccent);
    }

    /** A rounded secondary button: label on a pill with a hairline (podcast, radio and other pages). */
    public static TextView button(Context c, String label, View.OnClickListener action) {
        TextView t = text(c, 15, R.attr.gfTextPrimary, true);
        t.setText(label);
        t.setSingleLine(false);
        t.setEllipsize(null);
        t.setGravity(android.view.Gravity.CENTER);
        t.setMinHeight(dp(c, 48));
        t.setPadding(dp(c, 12), dp(c, 10), dp(c, 12), dp(c, 10));
        t.setBackground(pressable(c, color(c, R.attr.gfControl), PILL, true));
        t.setOnClickListener(action);
        return t;
    }

    /**
     * A pill chip ("Playlists", "Headphones"): the accent gradient when chosen, a hairline pill
     * otherwise.
     */
    public static TextView chip(Context c, String label, boolean chosen) {
        TextView t = text(c, 14, R.attr.gfTextPrimary, false);
        t.setTypeface(Fonts.medium());
        t.setText(label);
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(dp(c, 38));
        t.setMinWidth(dp(c, 56));
        t.setPadding(dp(c, 16), dp(c, 7), dp(c, 16), dp(c, 7));
        setChipChosen(t, chosen);
        return t;
    }

    /** Shows a chip as chosen or not. */
    public static void setChipChosen(TextView chip, boolean chosen) {
        Context c = chip.getContext();
        chip.setSelected(chosen);
        chip.setBackground(chosen ? accentPressable(c, PILL, false)
                : pressable(c, color(c, R.attr.gfControl), PILL, true));
        chip.setTextColor(chosen ? onAccent(c) : color(c, R.attr.gfTextPrimary));
    }

    /** A row of chips that scrolls sideways when they don't fit; add them to row(chips). */
    public static android.widget.HorizontalScrollView chipRow(Context c) {
        android.widget.HorizontalScrollView scroll = new android.widget.HorizontalScrollView(c);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setFillViewport(false);
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(c, 16), dp(c, 6), dp(c, 8), dp(c, 6));
        scroll.addView(row);
        return scroll;
    }

    /** Adds a chip to a chipRow(). */
    public static void addChip(android.widget.HorizontalScrollView chips, View chip) {
        LinearLayout row = (LinearLayout) chips.getChildAt(0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(c(chips), 8);
        row.addView(chip, lp);
    }

    private static Context c(View v) {
        return v.getContext();
    }

    /** A round button: an icon on a filled circle with a hairline ("‹", "⋯", search). */
    public static IconView circleButton(Context c, int icon, int sizeDp, String description) {
        IconView b = new IconView(c);
        b.setIcon(icon);
        b.setColor(color(c, R.attr.gfTextPrimary));
        int pad = Math.round(dp(c, sizeDp) * 0.27f);
        b.setPadding(pad, pad, pad, pad);
        styleCircle(b, false);
        b.setContentDescription(description);
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return b;
    }

    /** Makes a view round: a hairline circle (filled with the control colour), or the accent. */
    public static void styleCircle(View v, boolean accentFilled) {
        Context c = v.getContext();
        if (accentFilled) {
            v.setBackground(accentPressable(c, 0, true));
        } else {
            android.graphics.drawable.StateListDrawable states = new android.graphics.drawable.StateListDrawable();
            states.addState(new int[]{android.R.attr.state_pressed},
                    shape(c, color(c, R.attr.gfControlPressed), 0, true, true));
            states.addState(new int[]{}, shape(c, color(c, R.attr.gfControl), 0, true, true));
            v.setBackground(states);
        }
        if (v instanceof IconView) ((IconView) v).setColor(accentFilled ? onAccent(c) : color(c, R.attr.gfTextPrimary));
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

    /** The "⋯" button at the end of a song row (same menu as a long press): a small round button. */
    public static IconView moreButton(Context c) {
        IconView more = new IconView(c);
        more.setIcon(Icons.MORE);
        more.setColor(color(c, R.attr.gfTextSecondary));
        // 32dp round, centred in a 40dp touch area, the dots across the middle half of it.
        int inset = dp(c, 4);
        android.graphics.drawable.StateListDrawable states = new android.graphics.drawable.StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, new android.graphics.drawable.InsetDrawable(
                shape(c, color(c, R.attr.gfControlPressed), 0, true, true), inset));
        states.addState(new int[]{}, new android.graphics.drawable.InsetDrawable(
                shape(c, color(c, R.attr.gfControl), 0, true, true), inset));
        more.setBackground(states);
        // After the background: an inset background brings its inset as padding, which would draw
        // the dots as big as the circle.
        int pad = dp(c, 10);
        more.setPadding(pad, pad, pad, pad);
        more.setContentDescription("More");
        more.setLayoutParams(new LinearLayout.LayoutParams(dp(c, 40), dp(c, 40)));
        return more;
    }

    /**
     * A fill in color that fades to clear over its last fadePx, at the bottom (or the right): what
     * floating chips and bars sit on, so the page shows through as it scrolls under them.
     * setAlpha() fades the whole fill in and out.
     */
    static android.graphics.drawable.Drawable fadingFill(final int color, final int fadePx, final boolean toRight) {
        return new android.graphics.drawable.Drawable() {
            private final android.graphics.Paint paint = new android.graphics.Paint();
            private int alpha = 255;

            @Override
            protected void onBoundsChange(android.graphics.Rect b) {
                int clear = color & 0x00ffffff;
                paint.setShader(toRight
                        ? new android.graphics.LinearGradient(b.right - fadePx, 0, b.right, 0, color, clear,
                                android.graphics.Shader.TileMode.CLAMP)
                        : new android.graphics.LinearGradient(0, b.bottom - fadePx, 0, b.bottom, color, clear,
                                android.graphics.Shader.TileMode.CLAMP));
            }

            @Override
            public void draw(android.graphics.Canvas canvas) {
                if (alpha == 0) return;
                paint.setAlpha(alpha);
                canvas.drawRect(getBounds(), paint);
            }

            @Override
            public void setAlpha(int a) {
                if (a == alpha) return;
                alpha = a;
                invalidateSelf();
            }

            @Override
            public void setColorFilter(android.graphics.ColorFilter filter) {
                paint.setColorFilter(filter);
            }

            @Override
            public int getOpacity() {
                return android.graphics.PixelFormat.TRANSLUCENT;
            }
        };
    }

    /**
     * A list row's background: nothing, and a pill while pressed, inset from the row's ends (a bar
     * the full width of the screen was the old look).
     */
    public static android.graphics.drawable.Drawable rowBackground(Context c) {
        android.graphics.drawable.StateListDrawable states = new android.graphics.drawable.StateListDrawable();
        int side = dp(c, 6);
        int edge = dp(c, 1);
        states.addState(new int[]{android.R.attr.state_pressed}, new android.graphics.drawable.InsetDrawable(
                shape(c, color(c, R.attr.gfControlPressed), PILL, false, false), side, edge, side, edge) {
            @Override
            public boolean getPadding(android.graphics.Rect padding) {
                // The inset is only for drawing: the row keeps its own padding.
                padding.set(0, 0, 0, 0);
                return false;
            }
        });
        states.addState(new int[]{}, new android.graphics.drawable.ColorDrawable(0));
        return states;
    }

    /**
     * What a card (artwork over its title) shows while pressed: a rounded square like its artwork.
     * Rows get a pill (rowBackground).
     */
    public static android.graphics.drawable.Drawable cardBackground(Context c) {
        return pressable(c, 0, dp(c, 18), false);
    }

    public static ArtworkView artwork(Context c, int sizeDp) {
        ArtworkView a = new ArtworkView(c);
        a.setPlaceholderColors(color(c, R.attr.gfFill), color(c, R.attr.gfTextSecondary));
        a.setCornerRadiusDp(sizeDp > 0 && sizeDp <= 64 ? 10 : 14);
        if (sizeDp > 0) a.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return a;
    }

    /**
     * Page header: a round back button (when there's somewhere to go back to), then the page's title,
     * big and bold, on a row where headerAction() puts round buttons at the right. Pages add more
     * below it. The back button is the header's first child (AlbumFragment moves it to its own bar).
     */
    public static LinearLayout header(Context c, String backLabel, String title, View.OnClickListener onBack) {
        LinearLayout h = new LinearLayout(c);
        h.setOrientation(LinearLayout.VERTICAL);
        h.setPadding(dp(c, 16), dp(c, backLabel != null ? 8 : 20), dp(c, 16), dp(c, 8));
        if (backLabel != null) {
            IconView back = circleButton(c, Icons.CHEVRON_LEFT, 40, "Back to " + backLabel);
            back.setOnClickListener(onBack);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(c, 40), dp(c, 40));
            lp.bottomMargin = dp(c, 6);
            h.addView(back, lp);
        }
        if (title != null) {
            LinearLayout row = new LinearLayout(c);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setTag(HEADER_ROW);
            TextView t = text(c, 30, R.attr.gfTextPrimary, true);
            t.setSingleLine(false);
            t.setMaxLines(2);
            t.setText(title);
            row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            h.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        return h;
    }

    private static final String HEADER_ROW = "gf-header-row";

    /** Puts a round button (or any view) at the right of a header's title. */
    public static void headerAction(LinearLayout header, View action) {
        View row = header.findViewWithTag(HEADER_ROW);
        if (!(row instanceof LinearLayout)) return;
        // Keep a round button's own size: a plain View asked to wrap its content takes all the room.
        ViewGroup.LayoutParams own = action.getLayoutParams();
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                own != null ? own.width : ViewGroup.LayoutParams.WRAP_CONTENT,
                own != null ? own.height : ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(header.getContext(), 8);
        ((LinearLayout) row).addView(action, lp);
    }

    /** A small pill for a page's top line ("Edit", "Done", "Cancel"): the accent gradient if bold. */
    public static TextView textButton(Context c, String label, boolean bold) {
        TextView t = text(c, 15, R.attr.gfTextPrimary, true);
        t.setText(label);
        t.setMinHeight(dp(c, 38));
        t.setMinWidth(dp(c, 64));
        t.setSingleLine(false);
        t.setEllipsize(null);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 16), dp(c, 6), dp(c, 16), dp(c, 6));
        if (bold) {
            t.setBackground(accentPressable(c, PILL, false));
            t.setTextColor(onAccent(c));
        } else {
            t.setBackground(pressable(c, color(c, R.attr.gfControl), PILL, true));
        }
        return t;
    }

    /** Rounded gray text field. */
    public static EditText field(Context c, float sp, boolean bold, String hint) {
        EditText e = new EditText(c);
        e.setBackgroundResource(resource(c, R.attr.gfFieldBackground));
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        e.setTextColor(color(c, R.attr.gfTextPrimary));
        e.setHintTextColor(color(c, R.attr.gfTextSecondary));
        e.setTypeface(Fonts.of(sp, bold));
        e.setHint(hint);
        return e;
    }

    /** Section title ("Recently Added"). */
    public static TextView sectionTitle(Context c, String title) {
        TextView t = text(c, 19, R.attr.gfTextPrimary, true);
        t.setText(title);
        t.setPadding(dp(c, 16), dp(c, 20), dp(c, 16), dp(c, 10));
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

    /** A pill button with an icon ("Shuffle", "Find Stations"): hairline, primary text, accent icon. */
    public static LinearLayout pill(Context c, int icon, String label) {
        return pill(c, icon, label, false);
    }

    /** A pill button; the main one on its page ("Play") in the accent gradient. */
    public static LinearLayout pill(Context c, int icon, String label, boolean main) {
        LinearLayout p = new LinearLayout(c);
        p.setOrientation(LinearLayout.HORIZONTAL);
        p.setBaselineAligned(false);
        p.setMinimumHeight(dp(c, 48));
        p.setGravity(Gravity.CENTER);
        p.setBackground(main ? accentPressable(c, PILL, false)
                : pressable(c, color(c, R.attr.gfControl), PILL, true));
        p.setPadding(dp(c, 16), dp(c, 10), dp(c, 16), dp(c, 10));
        IconView i = icon(c, icon, main ? onAccent(c) : accent(c), 18);
        p.addView(i);
        TextView t = text(c, 16, R.attr.gfTextPrimary, true);
        if (main) t.setTextColor(onAccent(c));
        t.setText(label);
        t.setSingleLine(false);
        t.setEllipsize(null);
        t.setPadding(dp(c, 8), 0, 0, 0);
        p.addView(t);
        return p;
    }
}
