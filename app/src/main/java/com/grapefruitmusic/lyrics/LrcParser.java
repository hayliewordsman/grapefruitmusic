package com.grapefruitmusic.lyrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses LRC lyrics ("[01:23.45]words"). Pure Java (unit tested). */
public final class LrcParser {
    private static final Pattern TIME = Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?\\]");
    private static final Pattern TAG = Pattern.compile("^\\[([a-zA-Z#]+):(.*)\\]$");
    /** Word-level timing from "enhanced" LRC: <01:23.45>. */
    private static final Pattern WORD_TIME = Pattern.compile("<\\d{1,3}:\\d{1,2}(?:[.:]\\d{1,3})?>");

    private LrcParser() {
    }

    /** Returns synced lyrics if the text has timestamps, otherwise plain lines. Null for blank input. */
    public static Lyrics parse(String text, String credit) {
        if (text == null || text.trim().length() == 0) return null;
        String[] rows = text.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        long offset = 0;
        List<Lyrics.Line> timed = new ArrayList<Lyrics.Line>();
        List<Lyrics.Line> plain = new ArrayList<Lyrics.Line>();
        for (String raw : rows) {
            String row = raw.trim();
            Matcher tag = TAG.matcher(row);
            if (tag.matches() && !TIME.matcher(row).lookingAt()) {
                if (tag.group(1).toLowerCase(Locale.US).equals("offset")) {
                    try {
                        offset = Long.parseLong(tag.group(2).trim().replace("+", ""));
                    } catch (NumberFormatException ignored) {
                        // Ignore a malformed offset.
                    }
                }
                continue;
            }
            Matcher m = TIME.matcher(row);
            List<Long> times = new ArrayList<Long>();
            int end = 0;
            while (m.find() && m.start() == end) {
                times.add(toMs(m.group(1), m.group(2), m.group(3)));
                end = m.end();
            }
            String words = WORD_TIME.matcher(row.substring(end)).replaceAll("").trim();
            if (times.isEmpty()) {
                plain.add(new Lyrics.Line(0, words));
            } else {
                for (Long t : times) timed.add(new Lyrics.Line(t, words));
            }
        }
        if (!timed.isEmpty()) {
            // A positive offset makes lyrics appear sooner.
            List<Lyrics.Line> shifted = new ArrayList<Lyrics.Line>(timed.size());
            for (Lyrics.Line l : timed) shifted.add(new Lyrics.Line(Math.max(0, l.timeMs - offset), l.text));
            Collections.sort(shifted, new Comparator<Lyrics.Line>() {
                @Override
                public int compare(Lyrics.Line a, Lyrics.Line b) {
                    return a.timeMs < b.timeMs ? -1 : (a.timeMs == b.timeMs ? 0 : 1);
                }
            });
            return new Lyrics(true, shifted, credit);
        }
        // Drop leading/trailing blank lines of plain lyrics.
        while (!plain.isEmpty() && plain.get(0).text.length() == 0) plain.remove(0);
        while (!plain.isEmpty() && plain.get(plain.size() - 1).text.length() == 0) plain.remove(plain.size() - 1);
        if (plain.isEmpty()) return null;
        return new Lyrics(false, plain, credit);
    }

    private static long toMs(String min, String sec, String frac) {
        long ms = Long.parseLong(min) * 60000L + Long.parseLong(sec) * 1000L;
        if (frac != null) {
            if (frac.length() == 1) ms += Long.parseLong(frac) * 100;
            else if (frac.length() == 2) ms += Long.parseLong(frac) * 10;
            else ms += Long.parseLong(frac);
        }
        return ms;
    }

    /** 83450 -> "[01:23.45]". */
    public static String timestamp(long ms) {
        long cs = (ms % 1000) / 10;
        long s = (ms / 1000) % 60;
        long m = ms / 60000;
        return String.format(Locale.US, "[%02d:%02d.%02d]", m, s, cs);
    }
}
