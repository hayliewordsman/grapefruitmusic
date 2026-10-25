package com.grapefruitmusic.util;

import java.util.Locale;

/** Pure-Java formatting helpers (unit tested). */
public final class Format {
    private Format() {
    }

    /** 187000 -> "3:07", 3723000 -> "1:02:03". */
    public static String duration(long ms) {
        if (ms < 0) ms = 0;
        long total = ms / 1000;
        long h = total / 3600;
        long m = (total % 3600) / 60;
        long s = total % 60;
        if (h > 0) return String.format(Locale.US, "%d:%02d:%02d", h, m, s);
        return String.format(Locale.US, "%d:%02d", m, s);
    }

    /** Remaining time the way iOS shows it: "-1:23". */
    public static String remaining(long positionMs, long durationMs) {
        return "-" + duration(Math.max(0, durationMs - positionMs));
    }

    public static String bytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(Locale.US, "%.0f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb);
        return String.format(Locale.US, "%.2f GB", mb / 1024.0);
    }

    /** "12 songs, 47 minutes". */
    public static String songsSummary(int songs, long totalMs) {
        long minutes = Math.round(totalMs / 60000.0);
        String s = songs + (songs == 1 ? " song" : " songs");
        if (totalMs <= 0) return s;
        return s + ", " + minutes + (minutes == 1 ? " minute" : " minutes");
    }

    public static boolean isBlank(String s) {
        return s == null || s.trim().length() == 0;
    }

    public static String nonNull(String s) {
        return s == null ? "" : s;
    }
}
