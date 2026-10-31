package com.grapefruitmusic.playback;

import org.junit.Test;
import static org.junit.Assert.*;

public class PendingSeekTest {
    @Test public void resumeHoldsTargetUntilCompletion() {
        PendingSeek s = new PendingSeek();
        assertEquals(3600000, s.request(3600000));
        assertEquals(3600000, s.position(0));
        assertEquals(-1, s.complete());
        assertEquals(3600123, s.position(3600123));
    }
    @Test public void rapidSeeksCoalesceAndKeepLatestProgress() {
        PendingSeek s = new PendingSeek();
        assertEquals(1000, s.request(1000));
        assertEquals(-1, s.request(2000));
        assertEquals(-1, s.request(3000));
        assertEquals(3000, s.position(500));
        assertEquals(3000, s.complete());
        assertEquals(3000, s.position(1000));
        assertEquals(-1, s.complete());
        assertEquals(3100, s.position(3100));
    }
    @Test public void seekBackToZeroIsPendingToo() {
        PendingSeek s = new PendingSeek();
        assertEquals(0, s.request(0));
        assertEquals(0, s.position(90000));
        assertEquals(-1, s.complete());
        assertEquals(0, s.position(-1));
    }
    @Test public void repeatedSameTargetNeedsNoSecondSeek() {
        PendingSeek s = new PendingSeek();
        s.request(1000);
        assertEquals(-1, s.request(1000));
        assertEquals(-1, s.complete());
        assertEquals(1500, s.position(1500));
    }
    @Test public void newSeekDuringQueuedSeekStillWins() {
        PendingSeek s = new PendingSeek();
        s.request(1000);
        s.request(2000);
        assertEquals(2000, s.complete());
        s.request(500);
        assertEquals(500, s.complete());
        assertEquals(-1, s.complete());
        assertEquals(600, s.position(600));
    }
}
