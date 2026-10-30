package com.grapefruitmusic.podcast;

import com.grapefruitmusic.util.Format;

/** Immutable snapshot of a system-managed episode download; never persisted as live state. */
public final class PodcastDownload {
    // DownloadManager values, kept here so progress formatting is pure Java and unit-testable.
    public static final int MISSING = 0,
            QUEUED = 1,
            RUNNING = 2,
            PAUSED = 4,
            COMPLETE = 8,
            FAILED = 16;
    public final int status, reason;
    public final long received, total;

    public PodcastDownload(int status, int reason, long received, long total) {
        this.status = status;
        this.reason = reason;
        this.received = Math.max(0, received);
        this.total = total;
    }

    public boolean active() {
        return status == QUEUED || status == RUNNING || status == PAUSED;
    }

    public int percent() {
        if (status == COMPLETE) return 100;
        if (total <= 0) return -1;
        return (int) Math.max(0, Math.min(100, received * 100.0 / total));
    }

    public String text() {
        if (status == COMPLETE) return "Downloaded";
        if (status == MISSING) return "Download unavailable · Retry from the episode menu";
        if (status == FAILED) {
            if (reason == 1006) return "Download failed · Not enough storage";
            if (reason >= 400 && reason <= 599) return "Download failed · HTTP " + reason;
            return "Download failed · Retry from the episode menu";
        }
        String state =
                status == QUEUED
                        ? "Queued"
                        : status == PAUSED
                                ? reason == 3
                                        ? "Waiting for Wi-Fi"
                                        : reason == 2
                                                ? "Waiting for connection"
                                                : reason == 1 ? "Waiting to retry" : "Paused"
                                : "Downloading";
        String bytes = Format.bytes(received);
        if (total > 0)
            return state + " · " + percent() + "% · " + bytes + " / " + Format.bytes(total);
        return state + (received > 0 ? " · " + bytes : "");
    }
}
