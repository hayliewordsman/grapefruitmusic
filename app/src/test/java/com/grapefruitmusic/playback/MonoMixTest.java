package com.grapefruitmusic.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MonoMixTest {
    private static final int RATE = 44100;

    /** Mixes left and right; returns the output after the first second (well past the fade-in). */
    private static float[] mix(float[] left, float[] right) {
        MonoMix m = new MonoMix();
        float[] out = new float[left.length];
        for (int i = 0; i < left.length; i++) {
            m.process(left[i], right[i]);
            out[i] = m.out;
        }
        return java.util.Arrays.copyOfRange(out, RATE, out.length);
    }

    private static float[] tone(double hz, double amplitude, double phase) {
        float[] x = new float[RATE * 2];
        for (int i = 0; i < x.length; i++) x[i] = (float) (amplitude * Math.sin(2 * Math.PI * hz * i / RATE + phase));
        return x;
    }

    private static double power(float[] x, int from) {
        double sum = 0;
        for (int i = from; i < x.length; i++) sum += (double) x[i] * x[i];
        return sum / (x.length - from);
    }

    private static double db(double ratio) {
        return 10 * Math.log10(ratio);
    }

    @Test
    public void soundInTheMiddleComesOutExactlyAsItIs() {
        float[] x = tone(440, 0.5, 0);
        float[] y = tone(3000, 0.3, 1);
        for (int i = 0; i < x.length; i++) x[i] += y[i];
        float[] out = mix(x, x);
        for (int i = 0; i < out.length; i++) {
            assertEquals("sample " + i, x[RATE + i - MonoMix.N], out[i], 1e-5);
        }
    }

    @Test
    public void soundInOppositePhaseIsKeptNotCancelled() {
        // Plain (L+R)/2 of these is silence.
        float[] out = mix(tone(440, 0.5, 0), tone(440, 0.5, Math.PI));
        assertEquals(0, db(power(out, 0) / power(tone(440, 0.5, 0), 0)), 0.1);
    }

    @Test
    public void everyPhaseDifferenceKeepsTheSameLoudness() {
        // A time difference between the channels makes plain mixing cut notches; this stays level.
        for (int degrees = 0; degrees <= 180; degrees += 30) {
            float[] out = mix(tone(1000, 0.5, 0), tone(1000, 0.5, Math.toRadians(degrees)));
            assertEquals(degrees + " degrees", 0, db(power(out, 0) / power(tone(1000, 0.5, 0), 0)), 0.1);
        }
    }

    @Test
    public void soundOnOneSideIsSharedEquallyNotHalved() {
        // Left only: its power is split between the two ears (3 dB each), where plain mixing loses 6.
        float[] left = tone(440, 0.5, 0);
        float[] out = mix(left, new float[left.length]);
        assertEquals(-3.01, db(power(out, 0) / power(left, 0)), 0.1);
    }

    @Test
    public void differentSoundsInEachEarBothComeThrough() {
        // Different tones left and right: each keeps its share, as with the side test.
        float[] left = tone(440, 0.4, 0);
        float[] right = tone(660, 0.4, 0);
        float[] out = mix(left, right);
        assertEquals(0, db(power(out, 0) / ((power(left, 0) + power(right, 0)) / 2)), 0.2);
    }

    @Test
    public void silenceStaysSilent() {
        float[] silence = new float[RATE * 2];
        for (float y : mix(silence, silence)) assertEquals(0f, y, 0f);
        // And no NaN from a whisper on one side.
        float[] whisper = new float[RATE * 2];
        whisper[RATE + 100] = 1e-30f;
        for (float y : mix(whisper, silence)) assertTrue(!Float.isNaN(y) && !Float.isInfinite(y));
    }
}
