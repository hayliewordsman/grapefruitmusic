package com.grapefruitmusic.podcast;

import static org.junit.Assert.*;

import org.junit.Test;

public class PodcastDownloadTest {
    @Test
    public void knownSizeHasPercentAndByteCounts() {
        PodcastDownload p = new PodcastDownload(PodcastDownload.RUNNING, 0, 512, 2048);
        assertEquals(25, p.percent());
        assertEquals("Downloading · 25% · 512 B / 2 KB", p.text());
        assertTrue(p.active());
    }

    @Test
    public void unknownSizeStaysIndeterminateAndShowsReceivedBytes() {
        PodcastDownload p = new PodcastDownload(PodcastDownload.RUNNING, 0, 2048, -1);
        assertEquals(-1, p.percent());
        assertEquals("Downloading · 2 KB", p.text());
        assertEquals("Queued", new PodcastDownload(PodcastDownload.QUEUED, 0, 0, -1).text());
    }

    @Test
    public void percentagesHandleOverflowAndInvalidCounts() {
        assertEquals(
                100,
                new PodcastDownload(PodcastDownload.RUNNING, 0, Long.MAX_VALUE, Long.MAX_VALUE)
                        .percent());
        assertEquals(100, new PodcastDownload(PodcastDownload.RUNNING, 0, 200, 100).percent());
        assertEquals(0, new PodcastDownload(PodcastDownload.RUNNING, 0, -10, 100).percent());
        assertEquals(-1, new PodcastDownload(PodcastDownload.RUNNING, 0, 0, 0).percent());
    }

    @Test
    public void pausedStatesExplainWhatTheDownloadIsWaitingFor() {
        assertTrue(new PodcastDownload(PodcastDownload.PAUSED, 3, 0, -1).text().contains("Wi-Fi"));
        assertTrue(
                new PodcastDownload(PodcastDownload.PAUSED, 2, 0, -1)
                        .text()
                        .contains("connection"));
        assertTrue(new PodcastDownload(PodcastDownload.PAUSED, 1, 0, -1).text().contains("retry"));
        assertTrue(new PodcastDownload(PodcastDownload.PAUSED, 4, 0, -1).active());
    }

    @Test
    public void completedAndFailedDownloadsDoNotPretendToBeActive() {
        PodcastDownload done = new PodcastDownload(PodcastDownload.COMPLETE, 0, 0, -1);
        assertEquals(100, done.percent());
        assertEquals("Downloaded", done.text());
        assertFalse(done.active());
        PodcastDownload failed = new PodcastDownload(PodcastDownload.FAILED, 1006, 0, -1);
        assertFalse(failed.active());
        assertTrue(failed.text().contains("storage"));
        assertTrue(
                new PodcastDownload(PodcastDownload.FAILED, 403, 0, -1)
                        .text()
                        .contains("HTTP 403"));
    }

    @Test
    public void missingSystemRecordsOfferRetryInsteadOfClaimingCompletion() {
        PodcastDownload p = new PodcastDownload(PodcastDownload.MISSING, 0, 0, -1);
        assertFalse(p.active());
        assertTrue(p.text().contains("unavailable"));
        assertTrue(p.text().contains("Retry"));
    }
}
