package com.grapefruitmusic.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

/** Dates as servers send them. */
public final class Dates {
    private Dates() {
    }

    /**
     * An ISO 8601 date and time ("2024-05-01T12:34:56.789Z", "2024-05-01T12:34:56+02:00", Jellyfin's
     * "2024-05-01T12:34:56.1234567Z") as milliseconds since 1970, or 0 if it can't be read.
     */
    public static long parseIso(String s) {
        if (s == null || s.length() < 19) return 0;
        String head = s.substring(0, 19);
        String rest = s.substring(19);
        int millis = 0;
        if (rest.startsWith(".")) {
            int end = 1;
            while (end < rest.length() && Character.isDigit(rest.charAt(end))) end++;
            String fraction = (rest.substring(1, end) + "000").substring(0, 3);
            millis = Integer.parseInt(fraction);
            rest = rest.substring(end);
        }
        String zone;
        if (rest.length() == 0 || rest.equals("Z")) {
            zone = "GMT";
        } else if (rest.startsWith("+") || rest.startsWith("-")) {
            zone = "GMT" + rest.replace(":", "");
        } else {
            return 0;
        }
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone(zone.length() == 8 ? zone.substring(0, 6) + ":" + zone.substring(6) : zone));
        try {
            return f.parse(head).getTime() + millis;
        } catch (ParseException e) {
            return 0;
        }
    }
}
