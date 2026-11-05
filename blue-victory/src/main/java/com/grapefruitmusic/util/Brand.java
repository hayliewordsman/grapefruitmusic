package com.grapefruitmusic.util;

import android.content.Context;

/** Edition names for the app's own copy. Never apply this to media metadata or user input. */
public final class Brand {
    private static volatile boolean blue;
    private Brand() { }

    public static void init(Context context) {
        blue = context.getResources().getBoolean(com.grapefruitmusic.R.bool.blue_victory);
    }

    public static boolean isBlue(){return blue;}

    /** Used only around the app's original string literals, preserving the standard edition. */
    public static String text(String original) {
        if (!blue) return original;
        return original.replaceAll("(?<!Blue )\\bGrapefruit(?: Audio| Music| TV & Movies| TV| FAST)?\\b", "Blue Grapefruit");
    }

    /** Product token used by servers and Android's media-session diagnostics. */
    public static String clientName() { return blue ? "BlueGrapefruit" : "GrapefruitMusic"; }
}
