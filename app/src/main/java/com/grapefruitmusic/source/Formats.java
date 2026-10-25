package com.grapefruitmusic.source;

import java.util.Locale;

/** Audio formats Android 4.2's MediaPlayer can decode. Pure Java (unit tested). */
public final class Formats {
    private Formats() {
    }

    public static boolean playable(String suffix) {
        if (suffix == null) return false;
        String s = suffix.toLowerCase(Locale.US);
        return s.equals("mp3") || s.equals("m4a") || s.equals("aac") || s.equals("mp4")
                || s.equals("m4b") || s.equals("flac") || s.equals("ogg") || s.equals("oga")
                || s.equals("wav") || s.equals("3gp");
    }

    /** Container list for Jellyfin's universal audio endpoint ("container|codec"). */
    public static final String JELLYFIN_CONTAINERS =
            "mp3,aac,m4a|aac,m4b|aac,mp4|aac,flac,ogg|vorbis,oga|vorbis,wav";

    /** File extension for a Content-Type, or the fallback. */
    public static String extensionFor(String contentType, String fallback) {
        if (contentType == null) return fallback;
        String t = contentType.toLowerCase(Locale.US);
        int semi = t.indexOf(';');
        if (semi >= 0) t = t.substring(0, semi).trim();
        if (t.equals("audio/mpeg") || t.equals("audio/mp3")) return "mp3";
        if (t.equals("audio/flac") || t.equals("audio/x-flac")) return "flac";
        if (t.equals("audio/mp4") || t.equals("audio/x-m4a") || t.equals("audio/m4a")
                || t.equals("audio/aac") || t.equals("audio/aacp")) return "m4a";
        if (t.equals("audio/ogg") || t.equals("audio/vorbis") || t.equals("application/ogg")) return "ogg";
        if (t.equals("audio/wav") || t.equals("audio/x-wav") || t.equals("audio/wave")) return "wav";
        return fallback;
    }
}
