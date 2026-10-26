package com.grapefruitmusic.radio;

import android.util.Log;

/** What the radio tried and what went wrong, for the diagnostics report. Thread-safe. */
public final class RadioLog {
    private static final String TAG = "GrapefruitRadio";
    private final StringBuilder text = new StringBuilder();

    public synchronized void add(String line) {
        Log.i(TAG, line);
        // Keep the start (how the radio was found) and stop before it grows without end.
        if (text.length() < 6000) text.append(line).append('\n');
    }

    public synchronized String text() {
        return text.toString();
    }
}
