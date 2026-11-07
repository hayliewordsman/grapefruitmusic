package com.grapefruitmusic.util;
import android.content.Context;
/** ROM edition copy only; media metadata, package identity and storage keys are unchanged. */
public final class Brand {
    private Brand() {}
    public static void init(Context context) {}
    public static boolean isBlue(){return false;}
    public static String text(String original){return original.replaceAll("(?<![A-Za-z])(?:Blue Grapefruit|Grapefruit(?: Audio| Music| TV & Movies| TV| FAST)?)(?![A-Za-z])","Grapefruit Media");}
    public static String clientName(){return "GrapefruitMedia";}
}
