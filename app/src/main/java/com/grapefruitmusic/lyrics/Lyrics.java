package com.grapefruitmusic.lyrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Song lyrics, either time-synced (LRC) or plain text. Pure Java (unit tested). */
public final class Lyrics {
    public static final class Line {
        public final long timeMs;
        public final String text;

        public Line(long timeMs, String text) {
            this.timeMs = timeMs;
            this.text = text;
        }
    }

    public final boolean synced;
    public final List<Line> lines;
    /** Where the lyrics came from, shown under them ("LRCLIB", "Navidrome" ...). */
    public final String credit;

    public Lyrics(boolean synced, List<Line> lines, String credit) {
        this.synced = synced;
        this.lines = Collections.unmodifiableList(new ArrayList<Line>(lines));
        this.credit = credit;
    }

    public boolean isEmpty() {
        for (Line l : lines) {
            if (l.text.trim().length() > 0) return false;
        }
        return true;
    }

    /** Index of the line being sung at positionMs, or -1 before the first line. */
    public int indexAt(long positionMs) {
        if (!synced || lines.isEmpty()) return -1;
        int lo = 0;
        int hi = lines.size() - 1;
        int found = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (lines.get(mid).timeMs <= positionMs) {
                found = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return found;
    }

    public Lyrics withCredit(String newCredit) {
        return new Lyrics(synced, lines, newCredit);
    }

    /** Serializes back to LRC (synced) or plain text, for caching. */
    public String toText() {
        StringBuilder sb = new StringBuilder();
        for (Line l : lines) {
            if (synced) sb.append(LrcParser.timestamp(l.timeMs));
            sb.append(l.text).append('\n');
        }
        return sb.toString();
    }
}
