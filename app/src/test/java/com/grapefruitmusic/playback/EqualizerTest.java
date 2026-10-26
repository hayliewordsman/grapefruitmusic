package com.grapefruitmusic.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class EqualizerTest {
    @Test
    public void presetsKeepTheirIdsAndNames() {
        // Ids are saved in settings, so 2.1's presets must stay where they were.
        assertEquals("Off", Equalizer.name(Equalizer.OFF));
        assertEquals("Bass Booster", Equalizer.name(Equalizer.BASS_BOOSTER));
        assertEquals("Bass Reducer", Equalizer.name(Equalizer.BASS_REDUCER));
        assertEquals("Vocal Booster", Equalizer.name(Equalizer.VOCAL_BOOSTER));
        assertEquals("Vocal Reducer", Equalizer.name(Equalizer.VOCAL_REDUCER));
        assertEquals("Custom", Equalizer.name(Equalizer.CUSTOM));
        assertTrue(Equalizer.presetCount() >= 12);
        assertEquals("unknown id", "Off", Equalizer.name(999));
    }

    @Test
    public void everyPresetHasACurveWithinRange() {
        for (int p = 0; p < Equalizer.presetCount(); p++) {
            float[] curve = Equalizer.curve(p);
            if (p == Equalizer.CUSTOM) {
                assertNull(curve);
                continue;
            }
            assertEquals(Equalizer.name(p), Equalizer.BANDS, curve.length);
            for (float g : curve) assertTrue(Equalizer.name(p), Math.abs(g) <= Equalizer.MAX_DB);
            assertEquals(Equalizer.name(p), p != Equalizer.OFF, Equalizer.isOn(curve));
        }
    }

    @Test
    public void listedOffFirstThenCustomThenAToZ() {
        List<Integer> order = Equalizer.listOrder();
        assertEquals(Equalizer.presetCount(), order.size());
        assertEquals(Equalizer.OFF, (int) order.get(0));
        assertEquals(Equalizer.CUSTOM, (int) order.get(1));
        List<String> rest = new ArrayList<String>();
        for (int i = 2; i < order.size(); i++) rest.add(Equalizer.name(order.get(i)));
        List<String> sorted = new ArrayList<String>(rest);
        java.util.Collections.sort(sorted);
        assertEquals(sorted, rest);
    }

    @Test
    public void curveCopiesCantChangeThePresets() {
        Equalizer.curve(Equalizer.ROCK)[0] = 99;
        assertEquals(5f, Equalizer.curve(Equalizer.ROCK)[0], 0);
    }

    @Test
    public void eachBandLandsWhereItsSliderIs() {
        for (int rate : new int[]{44100, 48000}) {
            for (int p = 0; p < Equalizer.presetCount(); p++) {
                float[] curve = Equalizer.curve(p);
                if (curve == null) continue;
                double[][] filters = Equalizer.design(curve, rate);
                for (int b = 0; b < Equalizer.BANDS; b++) {
                    assertEquals(Equalizer.name(p) + " " + Equalizer.FREQUENCIES[b] + " Hz at " + rate, curve[b],
                            Equalizer.responseDb(filters, Equalizer.FREQUENCIES[b], rate), 0.1);
                }
            }
        }
    }

    @Test
    public void aFlatCurveNeedsNoFilters() {
        for (double[] f : Equalizer.design(new float[Equalizer.BANDS], 44100)) assertNull(f);
    }

    @Test
    public void oneSliderMovesItsOwnBand() {
        float[] curve = new float[Equalizer.BANDS];
        curve[4] = 12; // 500 Hz
        double[][] filters = Equalizer.design(curve, 44100);
        assertEquals(12, Equalizer.responseDb(filters, 500, 44100), 0.1);
        // Two octaves away and beyond, the sound is barely touched.
        for (int b : new int[]{0, 1, 2, 6, 7, 8, 9}) {
            assertEquals(Equalizer.FREQUENCIES[b] + " Hz", 0,
                    Equalizer.responseDb(filters, Equalizer.FREQUENCIES[b], 44100), 1.0);
        }
    }

    @Test
    public void aFlatBoostStaysSmooth() {
        float[] curve = new float[Equalizer.BANDS];
        Arrays.fill(curve, 6);
        double[][] filters = Equalizer.design(curve, 44100);
        for (double hz = 40; hz < 14000; hz *= 1.05) {
            double db = Equalizer.responseDb(filters, hz, 44100);
            assertTrue(hz + " Hz: " + db, db > 4 && db < 7);
        }
    }

    @Test
    public void bandsAboveHalfTheSampleRateAreSkipped() {
        // A 22 kHz recording can't hold 16 kHz: that band is left out, the rest still land.
        float[] curve = Equalizer.curve(Equalizer.TREBLE_BOOSTER);
        double[][] filters = Equalizer.design(curve, 22050);
        assertNull(filters[9]);
        for (int b = 0; b < 9; b++) {
            assertEquals(curve[b], Equalizer.responseDb(filters, Equalizer.FREQUENCIES[b], 22050), 0.1);
        }
    }
}
