package com.grapefruitmusic.playback;

/** Serializes asynchronous MediaPlayer seeks on old Android; the latest target wins. */
final class PendingSeek {
    private int target = -1;
    private int active = -1;

    /** Returns the target to issue now, or -1 while an earlier seek is running. */
    int request(int ms) {
        target = Math.max(0, ms);
        if (active >= 0) return -1;
        active = target;
        return active;
    }

    /** Returns a queued target to issue, or -1 when all seeks have completed. */
    int complete() {
        if (target != active) {
            active = target;
            return active;
        }
        target = active = -1;
        return -1;
    }

    int position(int actual) {
        return target >= 0 ? target : Math.max(0, actual);
    }
}
