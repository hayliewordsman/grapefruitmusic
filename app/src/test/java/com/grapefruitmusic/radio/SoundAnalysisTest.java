package com.grapefruitmusic.radio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.Random;

public class SoundAnalysisTest {
    private static final int RATE = 44100;

    /**
     * Ten seconds of a "station": programme (tones that come and go, like speech), hiss, and
     * optionally mains hum at f0 with weaker harmonics.
     */
    private static short[] station(int humHz, double humLevel) {
        short[] s = new short[RATE * 10];
        Random random = new Random(7);
        for (int i = 0; i < s.length; i++) {
            double t = (double) i / RATE;
            // On for 0.3 s, off for 0.2 s: the gaps are where the noise underneath shows.
            boolean talking = (t % 0.5) < 0.3;
            double v = talking ? 0.25 * Math.sin(2 * Math.PI * 440 * t) + 0.12 * Math.sin(2 * Math.PI * 1210 * t) : 0;
            v += 0.01 * random.nextGaussian();
            for (int h = 1; h <= 5 && humHz > 0; h++) {
                v += humLevel / h * Math.sin(2 * Math.PI * humHz * h * t);
            }
            s[i] = (short) Math.max(-32768, Math.min(32767, Math.round(v * 32767)));
        }
        return s;
    }

    @Test
    public void findsMainsHumAndItsHarmonics() {
        SoundAnalysis a = SoundAnalysis.of(station(60, 0.02), RATE);
        System.out.print(a.describe());
        assertEquals(60, a.humHz);
        assertTrue("several harmonics", a.humPeaks.size() >= 3);
        assertEquals(60, a.humPeaks.get(0).hz, 0.01);
        assertTrue(a.humDb > 20);
        // The programme's tones are narrow peaks too, but not hum.
        boolean tone = false;
        for (SoundAnalysis.Peak p : a.otherPeaks) tone |= Math.abs(p.hz - 440) < 4;
        assertTrue("the 440 Hz tone is listed as another peak", tone);
    }

    @Test
    public void tellsFiftyFromSixty() {
        SoundAnalysis a = SoundAnalysis.of(station(50, 0.02), RATE);
        assertEquals(50, a.humHz);
        assertEquals(50, a.humPeaks.get(0).hz, 0.01);
    }

    @Test
    public void noHumWhenThereIsNone() {
        SoundAnalysis a = SoundAnalysis.of(station(0, 0), RATE);
        System.out.print(a.describe());
        assertEquals(0, a.humHz);
        assertTrue(a.describe().contains("no 50 or 60 Hz hum stands out"));
    }

    @Test
    public void quietMomentsShowTheNoiseUnderneath() {
        SoundAnalysis a = SoundAnalysis.of(station(0, 0), RATE);
        // Hiss at 0.01 (about -40 dBFS) between "words"; the programme around -13 dBFS.
        assertEquals(-40, a.quietDb, 2);
        assertTrue(a.medianDb > -20);
    }

    @Test
    public void writesAPlayableWav() throws Exception {
        short[] s = {0, 1000, -1000, 32767, -32768};
        File f = File.createTempFile("clip", ".wav");
        try {
            SoundAnalysis.writeWav(f, s, 22050);
            assertEquals(44 + 10, f.length());
            RandomAccessFile r = new RandomAccessFile(f, "r");
            byte[] head = new byte[44];
            r.readFully(head);
            assertEquals("RIFF", new String(head, 0, 4, "US-ASCII"));
            assertEquals("WAVE", new String(head, 8, 4, "US-ASCII"));
            r.close();
            assertEquals("data", new String(head, 36, 4, "US-ASCII"));
            assertEquals(22050, (head[24] & 0xff) | (head[25] & 0xff) << 8 | (head[26] & 0xff) << 16);
            assertEquals(0, readShortAt(f, 44));
            assertEquals(1000, readShortAt(f, 46));
            assertEquals(-32768, readShortAt(f, 52));
        } finally {
            f.delete();
        }
    }

    private static int readShortAt(File f, int at) throws Exception {
        RandomAccessFile r = new RandomAccessFile(f, "r");
        try {
            r.seek(at);
            int lo = r.read();
            int hi = r.read();
            return (short) (lo | hi << 8);
        } finally {
            r.close();
        }
    }
}
