package com.grapefruitmusic.playback;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import org.junit.BeforeClass;
import org.junit.Test;

public class LiveStageTest {
    private static LiveCrowd crowd;

    @BeforeClass
    public static void crowd() throws IOException {
        for (String path : new String[] {"src/main/assets/live_crowd.bin", "app/src/main/assets/live_crowd.bin"}) {
            File f = new File(path);
            if (f.exists()) crowd = LiveCrowd.load(new FileInputStream(f));
        }
        assertNotNull("live_crowd.bin not found", crowd);
    }

    /** A steady "song": chords in stereo with a centre melody, at about -16dBFS. */
    private static short[] song(int rate, double seconds, double level) {
        int n = (int) (rate * seconds);
        short[] pcm = new short[n * 2];
        for (int i = 0; i < n; i++) {
            double t = i / (double) rate;
            double voice = Math.sin(2 * Math.PI * 220 * t) * 0.15 + Math.sin(2 * Math.PI * 660 * t) * 0.06;
            double l = voice + Math.sin(2 * Math.PI * 330 * t) * 0.1;
            double r = voice + Math.sin(2 * Math.PI * 415 * t) * 0.1;
            pcm[i * 2] = (short) Math.round(l * level * 32767);
            pcm[i * 2 + 1] = (short) Math.round(r * level * 32767);
        }
        return pcm;
    }

    private static double rms(short[] pcm, int from, int to) {
        double sum = 0;
        for (int i = from; i < to; i++) sum += (pcm[i] / 32768.0) * (pcm[i] / 32768.0);
        return Math.sqrt(sum / Math.max(1, to - from));
    }

    private static Dsp live(float strength, int rate) {
        Dsp d = new Dsp();
        d.setLive(true, strength);
        d.setFormat(rate, 2);
        return d;
    }

    @Test
    public void crowdFileReadsAndRejectsOthers() throws IOException {
        assertEquals(16000, crowd.bed.rate);
        assertEquals(22050, crowd.opening.rate);
        assertEquals(22050 * 2, crowd.opening.marker);
        assertTrue(crowd.closing.frames > 22050 * 8);
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(0x47534331); // beta 3's cheer file
        out.writeInt(12000);
        try {
            LiveCrowd.read(new java.io.DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
            fail("read a file that isn't a crowd");
        } catch (IOException expected) {
            // Not a crowd.
        }
    }

    @Test
    public void zeroStrengthIsTheDrySong() {
        short[] a = song(44100, 3, 1), b = a.clone();
        Dsp wet = live(0, 44100);
        Dsp dry = new Dsp();
        dry.setFormat(44100, 2);
        wet.setSongPosition(0);
        assertEquals(0, wet.preRollFrames());
        wet.process(a, 0, a.length);
        dry.process(b, 0, b.length);
        assertArrayEquals(b, a);
    }

    @Test
    public void zeroStrengthWithTheSeparatorIsTheDrySongLater() throws IOException {
        Dsp d = new Dsp();
        d.setLive(true, 0);
        d.setKaraoke(false, 1, VocalRemoverTest.model());
        d.setFormat(44100, 2);
        assertTrue(d.liveUsesSeparator());
        short[] x = song(44100, 2, 1), in = x.clone();
        d.process(x, 0, x.length);
        // After the separator's own short fade-in.
        int late = d.latencyFrames();
        for (int i = (late + 4096) * 2; i < x.length; i++) assertEquals(in[i - late * 2], x[i]);
    }

    @Test
    public void theCrowdCheersBeforeTheFirstNote() {
        Dsp d = live(1, 44100);
        d.setSongPosition(0);
        int pre = d.preRollFrames();
        assertEquals(88200, pre);
        d.startPreRoll();
        short[] cheer = new short[pre * 2];
        d.process(cheer, 0, cheer.length);
        assertTrue("cheering before the song", rms(cheer, cheer.length / 4, cheer.length) > 0.05);
        // The cheer carries on into the song, then dies down to the crowd under the song.
        short[] start = new short[44100 * 2];
        d.process(start, 0, start.length);
        short[] later = new short[44100 * 2 * 6];
        d.process(later, 0, later.length);
        double opening = rms(start, 0, start.length);
        double settled = rms(later, later.length - 44100 * 2, later.length);
        assertTrue(opening > 0.04);
        assertTrue("the opening cheer dies down", settled < opening / 3);
        assertTrue("the crowd stays", settled > 0.002);
    }

    @Test
    public void noCheerBeforeASongStartedPartWay() {
        Dsp d = live(1, 44100);
        d.setSongPosition(60000);
        assertEquals(0, d.preRollFrames());
        short[] x = new short[44100 * 2];
        d.process(x, 0, x.length);
        // The crowd fades in under the song, without a cheer.
        assertTrue(rms(x, 0, 4410) < 0.003);
    }

    @Test
    public void seekingIntoTheStartPicksUpTheCheerWhereItWas() {
        Dsp d = live(1, 44100);
        d.setSongPosition(1000);
        short[] x = new short[44100];
        d.process(x, 0, x.length);
        assertTrue(rms(x, x.length / 2, x.length) > 0.02);
    }

    @Test
    public void theOvationStartsWhenAFadeOutDoes() {
        int rate = 22050;
        LiveStage s = new LiveStage(rate, crowd, 0, rate * 40L);
        // 30 seconds at full level, then a fade to silence over the last 10 (about 4dB a second).
        for (int i = 0; i < rate * 40; i++) {
            double t = i / (double) rate;
            double gain = t < 30 ? 1 : Math.pow(10, -2 * (t - 30) / 10);
            double x = Math.sin(2 * Math.PI * 330 * t) * 0.3 * gain;
            s.process(x, x, 0, false, 1);
        }
        // Noticed two or so seconds in; started late enough to carry on past the end of the file.
        double at = s.ovationFrame / (double) rate;
        assertTrue("ovation at " + at, at > 31 && at < 33.6);
        assertTrue(s.ovation());
        assertTrue(s.finish() > rate);
    }

    @Test
    public void theOvationStartsAsTheMusicStopsBeforeTheFileEnds() {
        int rate = 22050;
        LiveStage s = new LiveStage(rate, crowd, 0, rate * 30L);
        // The music stops at 26s; the file has 4 seconds of silence.
        for (int i = 0; i < rate * 30; i++) {
            double t = i / (double) rate;
            double x = t < 26 ? Math.sin(2 * Math.PI * 330 * t) * 0.3 : 0;
            s.process(x, x, 0, false, 1);
        }
        double at = s.ovationFrame / (double) rate;
        assertTrue("ovation at " + at, at > 26 && at < 27.8);
    }

    @Test
    public void theOvationWaitsForTheLastWords() {
        int rate = 22050;
        LiveStage s = new LiveStage(rate, crowd, 0, rate * 40L);
        // The band stops 8 seconds from the end; the singer goes on for 5 more (as at the end of
        // "Blank Space"); then 3 seconds of silence.
        for (int i = 0; i < rate * 40; i++) {
            double t = i / (double) rate;
            double band = t < 32 ? Math.sin(2 * Math.PI * 330 * t) * 0.4 : 0;
            float voice = t < 37 ? (float) (Math.sin(2 * Math.PI * 220 * t) * 0.1) : 0;
            s.process(band + voice, band + voice, voice, true, 1);
        }
        double at = s.ovationFrame / (double) rate;
        assertTrue("ovation at " + at, at > 37 && at < 38.5);
    }

    @Test
    public void aFadeOutWithTheSingerFadingToo() {
        int rate = 22050;
        LiveStage s = new LiveStage(rate, crowd, 0, rate * 40L);
        for (int i = 0; i < rate * 40; i++) {
            double t = i / (double) rate;
            double gain = t < 30 ? 1 : Math.pow(10, -2 * (t - 30) / 10);
            double band = Math.sin(2 * Math.PI * 330 * t) * 0.3 * gain;
            float voice = (float) (Math.sin(2 * Math.PI * 220 * t) * 0.15 * gain);
            s.process(band + voice, band + voice, voice, true, 1);
        }
        double at = s.ovationFrame / (double) rate;
        assertTrue("ovation at " + at, at > 31 && at < 34);
    }

    @Test
    public void aShortBreakBeforeTheLastChorusIsNotTheEnd() {
        int rate = 22050;
        LiveStage s = new LiveStage(rate, crowd, 0, rate * 40L);
        // A half-second stop 8 seconds from the end, then loud again.
        for (int i = 0; i < rate * 38; i++) {
            double t = i / (double) rate;
            double x = t > 32 && t < 32.5 ? 0 : Math.sin(2 * Math.PI * 330 * t) * 0.3;
            s.process(x, x, 0, false, 1);
        }
        assertFalse(s.ovation());
    }

    @Test
    public void withoutALengthTheOvationComesAtTheEnd() {
        Dsp d = live(1, 44100);
        d.setSongPosition(0);
        short[] x = song(44100, 2, 1);
        d.process(x, 0, x.length);
        int tail = d.finishSongFrames();
        assertTrue("an ovation of several seconds: " + tail, tail > 44100 * 8 && tail < 44100 * 10);
        short[] ovation = new short[44100 * 2 * 2];
        d.process(ovation, 0, ovation.length);
        assertTrue(rms(ovation, 0, ovation.length) > 0.05);
        d.setLive(false, 1);
        assertEquals(d.latencyFrames(), d.finishSongFrames());
    }

    @Test
    public void aBigMomentGetsACheer() {
        int rate = 22050;
        LiveStage s = new LiveStage(rate, crowd, 0, rate * 120L);
        for (int i = 0; i < rate * 40; i++) {
            double t = i / (double) rate;
            double x = Math.sin(2 * Math.PI * 330 * t) * (t < 20 ? 0.1 : 0.3);
            s.process(x, x, 0, false, 1);
        }
        assertEquals(1, s.reactions);
    }

    @Test
    public void theCrowdSingsWhenThereIsAVoice() {
        int rate = 44100;
        LiveStage with = new LiveStage(rate, null, 0, -1), without = new LiveStage(rate, null, 0, -1);
        double sung = 0, silent = 0;
        for (int i = 0; i < rate * 3; i++) {
            float voice = (float) (Math.sin(2 * Math.PI * 300 * i / rate) * 0.3);
            with.process(0, 0, voice, true, 1);
            without.process(0, 0, 0, true, 1);
            if (i > rate) {
                sung += with.outL * with.outL + with.outR * with.outR;
                silent += without.outL * without.outL + without.outR * without.outR;
            }
        }
        assertTrue(sung > 10);
        assertEquals(0, silent, 1e-12);
    }

    @Test
    public void strengthTurnsItUpSmoothly() {
        double previous = 0;
        for (float strength : new float[] {0, 0.01f, 0.25f, 0.5f, 1}) {
            LiveStage s = new LiveStage(22050, crowd, 22050 * 30L, -1);
            double sum = 0;
            for (int i = 0; i < 22050 * 3; i++) {
                double x = Math.sin(i * 0.1) * 0.2;
                s.process(x, x, 0, false, strength);
                sum += Math.abs(s.outL - x);
            }
            assertTrue(sum >= previous);
            previous = sum;
        }
    }

    @Test
    public void theStadiumRingsOutAndStops() {
        LiveStage s = new LiveStage(44100, null, 0, -1);
        double tail = 0;
        for (int i = 0; i < 44100 * 10; i++) {
            double x = i < 44100 ? Math.sin(i * 0.05) * 0.5 : 0;
            s.process(x, x, 0, false, 1);
            if (i > 44100 && i < 44100 * 2) tail += Math.abs(s.outL);
        }
        assertTrue("reverberation after the song stops", tail > 10);
        assertEquals(0, s.outL, 1e-4);
        assertEquals(0, s.outR, 1e-4);
    }

    @Test
    public void everyRateGivesBoundedStereo() {
        for (int rate : new int[] {8000, 16000, 22050, 44100, 48000, 96000}) {
            Dsp d = live(1, rate);
            d.setSongPosition(0);
            d.startPreRoll();
            short[] x = song(rate, 4, 2);
            d.process(x, 0, x.length);
            double stereo = 0;
            for (int i = 0; i < x.length; i += 2) {
                assertTrue(Math.abs(x[i]) <= 30934 && Math.abs(x[i + 1]) <= 30934);
                stereo += Math.abs(x[i] - x[i + 1]);
            }
            assertTrue("rate " + rate, stereo > 1000);
        }
    }

    @Test
    public void chunkingDoesNotChangeIt() throws IOException {
        short[] a = song(44100, 4, 1), b = a.clone();
        Dsp one = live(1, 44100), chunks = live(1, 44100);
        one.setKaraoke(false, 1, VocalRemoverTest.model());
        chunks.setKaraoke(false, 1, VocalRemoverTest.model());
        one.setFormat(44100, 2);
        chunks.setFormat(44100, 2);
        one.process(a, 0, a.length);
        for (int p = 0; p < b.length; p += 1024) chunks.process(b, p, Math.min(1024, b.length - p));
        assertArrayEquals(a, b);
    }

    @Test
    public void monoFilesAndMonoAudio() throws IOException {
        Dsp m = new Dsp();
        m.setLive(true, 1);
        m.setFormat(44100, 1);
        m.setSongPosition(0);
        m.startPreRoll();
        short[] x = new short[44100];
        m.process(x, 0, x.length);
        assertTrue(rms(x, 0, x.length) > 0.02);

        Dsp both = live(1, 44100);
        both.setMono(true);
        both.setKaraoke(true, 0.7f, VocalRemoverTest.model());
        both.setFormat(44100, 2);
        short[] y = song(44100, 2, 1);
        both.process(y, 0, y.length);
        for (int i = 0; i < y.length; i += 2) assertEquals(y[i], y[i + 1]);
    }

    @Test
    public void liveAloneWorksBeforeTheSeparatorIsLoaded() {
        Dsp d = live(1, 44100);
        assertFalse(d.karaokeRunning());
        assertEquals(176, d.latencyFrames());
        short[] x = song(44100, 3, 1), dry = x.clone();
        d.process(x, 0, x.length);
        double diff = 0;
        for (int i = x.length / 2; i < x.length; i++) {
            double delta = (x[i] - dry[Math.max(0, i - 352)]) / 32768.0;
            diff += delta * delta;
        }
        assertTrue(Math.sqrt(diff / (x.length / 2)) > 0.005);
    }

    @Test
    public void tooSlowSeparationFallsBackToTheCentre() throws IOException {
        try {
            Dsp.liveSeparationTooSlow = true;
            Dsp d = live(1, 44100);
            d.setKaraoke(false, 1, VocalRemoverTest.model());
            d.setFormat(44100, 2);
            assertFalse(d.liveUsesSeparator());
            assertFalse(d.karaokeRunning());
            // Karaoke Mode itself still runs.
            d.setKaraoke(true, 1, VocalRemoverTest.model());
            d.setFormat(44100, 2);
            assertTrue(d.karaokeRunning());
        } finally {
            Dsp.liveSeparationTooSlow = false;
        }
    }
}
