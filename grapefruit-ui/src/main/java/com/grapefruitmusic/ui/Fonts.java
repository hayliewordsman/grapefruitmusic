package com.grapefruitmusic.ui;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * Grapefruit's typeface, Geist (Vercel, SIL Open Font License 1.1: assets/fonts/Geist-OFL.txt), in
 * the weights it uses: Regular for text, Medium for small labels, SemiBold for bold text and Bold for
 * big titles. Static fonts from the app's assets, which every Android version can load; characters
 * Geist hasn't got come from Android's own fonts.
 */
public final class Fonts {
    private static final String TAG = "Fonts";
    /** Bold text this big (sp) or bigger is a title: Bold rather than SemiBold. */
    private static final float TITLE_SP = 26;

    private static Typeface regular = Typeface.DEFAULT;
    private static Typeface medium = Typeface.DEFAULT;
    private static Typeface semiBold = Typeface.DEFAULT_BOLD;
    private static Typeface bold = Typeface.DEFAULT_BOLD;

    private Fonts() {
    }

    /** Reads the fonts (once, at start). */
    public static void init(Context c) {
        try {
            regular = Typeface.createFromAsset(c.getAssets(), "fonts/Geist-Regular.ttf");
            medium = Typeface.createFromAsset(c.getAssets(), "fonts/Geist-Medium.ttf");
            semiBold = Typeface.createFromAsset(c.getAssets(), "fonts/Geist-SemiBold.ttf");
            bold = Typeface.createFromAsset(c.getAssets(), "fonts/Geist-Bold.ttf");
        } catch (RuntimeException e) {
            // Android's own font, then.
            Log.w(TAG, "can't load Geist", e);
        }
    }

    private static Typeface wordmark;

    /**
     * Geist Pixel in its Triangle shape (the variable font's ELSH 60), for the wordmark on the About
     * page: only the letters of the app's name are in it (tools/brand/logo.py). Read when first wanted.
     */
    public static synchronized Typeface wordmark(Context c) {
        if (wordmark == null) {
            try {
                wordmark = Typeface.createFromAsset(c.getAssets(), "fonts/GeistPixel-Triangle-Wordmark.ttf");
            } catch (RuntimeException e) {
                Log.w(TAG, "can't load Geist Pixel", e);
                wordmark = bold;
            }
        }
        return wordmark;
    }

    public static Typeface regular() {
        return regular;
    }

    public static Typeface medium() {
        return medium;
    }

    public static Typeface semiBold() {
        return semiBold;
    }

    public static Typeface bold() {
        return bold;
    }

    /** The weight for text of this size (sp), bold or not. */
    public static Typeface of(float sp, boolean isBold) {
        return !isBold ? regular : sp >= TITLE_SP ? bold : semiBold;
    }

    /** Geist for a view made elsewhere: the weight from its current style and size. */
    public static void apply(TextView t) {
        Typeface current = t.getTypeface();
        boolean isBold = current != null && current.isBold();
        float sp = t.getTextSize() / t.getResources().getDisplayMetrics().scaledDensity;
        t.setTypeface(of(sp, isBold), Typeface.NORMAL);
    }

    /** Geist for every TextView in a layout (once, after it's made). */
    public static void applyAll(View v) {
        if (v instanceof TextView) {
            apply((TextView) v);
        } else if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) applyAll(g.getChildAt(i));
        }
    }

    /** Geist for text drawn with a Paint. */
    public static void apply(Paint p, boolean isBold) {
        p.setTypeface(isBold ? semiBold : regular);
        p.setFakeBoldText(false);
    }
}
