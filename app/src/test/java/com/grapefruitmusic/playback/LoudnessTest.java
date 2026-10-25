package com.grapefruitmusic.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LoudnessTest {
    /** Interleaved stereo (or mono) sine, amplitude relative to full scale. */
    private static short[] sine(int rate, int channels, double seconds, double amplitude, double freq) {
        int frames = (int) (rate * seconds);
        short[] out = new short[frames * channels];
        for (int f = 0; f < frames; f++) {
            short v = (short) Math.round(Math.sin(2 * Math.PI * freq * f / rate) * amplitude * 32767);
            for (int c = 0; c < channels; c++) out[f * channels + c] = v;
        }
        return out;
    }

    private static double measure(int rate, int channels, short[]... parts) {
        Loudness l = new Loudness();
        l.setFormat(rate, channels);
        for (short[] p : parts) l.add(p, p.length);
        return l.integrated();
    }

    @Test
    public void stereoSineAtMinus20dBFSIsMinus20Lufs() {
        // BS.1770: a 1 kHz sine in both channels reads about its peak level in dBFS.
        assertEquals(-20.0, measure(48000, 2, sine(48000, 2, 10, 0.1, 1000)), 0.1);
        assertEquals(-20.0, measure(44100, 2, sine(44100, 2, 10, 0.1, 1000)), 0.1);
    }

    @Test
    public void monoIsThreeDecibelsQuieterThanTheSameInBothChannels() {
        assertEquals(-23.0, measure(44100, 1, sine(44100, 1, 10, 0.1, 1000)), 0.1);
    }

    @Test
    public void louderSineMeasuresLouder() {
        assertEquals(-6.0, measure(44100, 2, sine(44100, 2, 10, 0.5, 1000)), 0.1);
    }

    @Test
    public void lowFrequenciesCountLess() {
        // K-weighting rolls off the bass: a 40 Hz sine reads quieter than a 1 kHz one.
        double bass = measure(44100, 2, sine(44100, 2, 10, 0.1, 40));
        assertTrue("bass " + bass, bass < -21);
    }

    @Test
    public void silenceIsLeftOut() {
        short[] tone = sine(44100, 2, 10, 0.1, 1000);
        short[] silence = new short[44100 * 2 * 10];
        assertEquals(-20.0, measure(44100, 2, silence, tone, silence), 0.15);
    }

    @Test
    public void quietPassagesBelowTheRelativeGateAreLeftOut() {
        // A passage 20 dB down is under the -10 LU relative gate.
        short[] loud = sine(44100, 2, 10, 0.1, 1000);
        short[] quiet = sine(44100, 2, 10, 0.01, 1000);
        assertEquals(-20.0, measure(44100, 2, loud, quiet), 0.2);
    }

    @Test
    public void measuringInPiecesMatchesTheWhole() {
        Loudness l = new Loudness();
        l.setFormat(44100, 2);
        for (int i = 0; i < 6; i++) {
            short[] part = sine(44100, 2, 4, 0.1, 1000);
            l.discontinuity();
            l.add(part, part.length);
        }
        assertEquals(-20.0, l.integrated(), 0.2);
        assertTrue(l.blockCount() > 200);
    }

    @Test
    public void silenceOnlyHasNoLoudness() {
        assertTrue(Double.isNaN(measure(44100, 2, new short[44100 * 2 * 3])));
    }

    @Test
    public void adjustmentTurnsLoudSongsDownToTheTargetOnly() {
        // A loud master (-8 LUFS) comes down 8 dB to -16.
        assertEquals(-8.0, Loudness.adjustmentDb(-8), 1e-9);
        // A quiet song is never turned up.
        assertEquals(0.0, Loudness.adjustmentDb(-22), 1e-9);
        // At most 20 dB down.
        assertEquals(-20.0, Loudness.adjustmentDb(10), 1e-9);
        assertEquals(0.0, Loudness.adjustmentDb(Double.NaN), 1e-9);
        assertEquals(0.5012f, Loudness.volume(-6), 1e-3f);
        assertEquals(1f, Loudness.volume(0), 1e-6f);
    }

    @Test
    public void replayGainIsRelativeToMinus18Lufs() {
        assertEquals(-10.5, Loudness.fromReplayGain(-7.5), 1e-9);
        assertEquals(-5.5, Loudness.adjustmentDb(Loudness.fromReplayGain(-7.5)), 1e-9);
    }
}
