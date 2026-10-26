package com.grapefruitmusic.playback;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * The 10-band equalizer: its bands, its presets, and the filters that shape the sound to a curve.
 * Pure Java (unit tested).
 */
public final class Equalizer {
    public static final int BANDS = 10;
    /** Band centres in Hz, an octave apart (as on most graphic equalizers). */
    public static final int[] FREQUENCIES = {31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000};
    /** Each band goes this many dB up or down. */
    public static final float MAX_DB = 12;

    // Preset ids are saved in settings: 0 to 4 are the presets from 2.1, so only add at the end.
    public static final int OFF = 0;
    public static final int BASS_BOOSTER = 1;
    public static final int BASS_REDUCER = 2;
    public static final int VOCAL_BOOSTER = 3;
    public static final int VOCAL_REDUCER = 4;
    /** The curve made with the sliders (saved separately). */
    public static final int CUSTOM = 5;
    public static final int ACOUSTIC = 6;
    public static final int CLASSICAL = 7;
    public static final int DANCE = 8;
    public static final int DEEP = 9;
    public static final int ELECTRONIC = 10;
    public static final int HIP_HOP = 11;
    public static final int JAZZ = 12;
    public static final int LATIN = 13;
    public static final int LOUDNESS = 14;
    public static final int LOUNGE = 15;
    public static final int PIANO = 16;
    public static final int POP = 17;
    public static final int R_AND_B = 18;
    public static final int ROCK = 19;
    public static final int SMALL_SPEAKERS = 20;
    public static final int SPOKEN_WORD = 21;
    public static final int TREBLE_BOOSTER = 22;
    public static final int TREBLE_REDUCER = 23;

    private static final String[] NAMES = {
            "Off", "Bass Booster", "Bass Reducer", "Vocal Booster", "Vocal Reducer", "Custom",
            "Acoustic", "Classical", "Dance", "Deep", "Electronic", "Hip-Hop", "Jazz", "Latin", "Loudness",
            "Lounge", "Piano", "Pop", "R&B", "Rock", "Small Speakers", "Spoken Word", "Treble Booster",
            "Treble Reducer"};

    /** dB for each band, 31 Hz to 16 kHz. */
    private static final float[][] CURVES = {
            {0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
            {6, 5, 4, 2.5f, 1, 0, 0, 0, 0, 0},
            {-6, -5, -4, -2.5f, -1, 0, 0, 0, 0, 0},
            {-1.5f, -1.5f, -1, 0.5f, 2.5f, 4.5f, 5, 3, 1, 0},
            {1, 1, 0.5f, -0.5f, -2.5f, -4.5f, -5, -3, -1, 0},
            null,
            {5, 5, 4, 1, 2, 1.5f, 3.5f, 4, 3.5f, 2},
            {4.5f, 3.5f, 3, 2.5f, -1.5f, -1.5f, 0, 2, 3, 3.5f},
            {3.5f, 6.5f, 5, 0, 2, 3.5f, 5, 4.5f, 3.5f, 0},
            {5, 3.5f, 1.5f, 1, 3, 2.5f, 1.5f, -2, -3.5f, -4.5f},
            {4.5f, 4, 1, 0, -2, 2, 1, 1.5f, 4, 5},
            {5, 4.5f, 1.5f, 3, -1, -1, 1.5f, -0.5f, 2, 3},
            {4, 3, 1.5f, 2, -1.5f, -1.5f, 0, 1.5f, 2.5f, 3},
            {4.5f, 3, 0, 0, -1.5f, -1.5f, -1.5f, 0, 3, 4.5f},
            {6, 5, 3, 1, 0, 0, 0.5f, 1.5f, 3, 4},
            {-3, -1.5f, -0.5f, 1.5f, 4, 2.5f, 0, -1.5f, 2, 1},
            {3, 2, 0, 2.5f, 3, 1.5f, 3.5f, 4.5f, 3, 3.5f},
            {-1.5f, -1, 0, 2, 4, 4, 2, 0, -1, -1.5f},
            {2.5f, 7, 5.5f, 1.5f, -2, -1.5f, 2.5f, 3, 3, 4},
            {5, 4, 3, 1.5f, -0.5f, -1, 0.5f, 2.5f, 3.5f, 4.5f},
            {5.5f, 4.5f, 3.5f, 2.5f, 1, 0, -1, -2.5f, -3.5f, -4.5f},
            {-3.5f, -0.5f, 0, 0.5f, 3.5f, 4.5f, 4.5f, 4, 2.5f, 0},
            {0, 0, 0, 0, 0, 1.5f, 2.5f, 3.5f, 4.5f, 5.5f},
            {0, 0, 0, 0, 0, -1.5f, -2.5f, -3.5f, -4.5f, -5.5f}};

    /** How broad each band is; with the gains solved together, broad bands give a smooth curve. */
    private static final double Q = 0.9;
    /** Bands with less than this to do are skipped. */
    private static final double SILENT_DB = 0.05;

    private Equalizer() {
    }

    public static int presetCount() {
        return NAMES.length;
    }

    public static String name(int preset) {
        return preset >= 0 && preset < NAMES.length ? NAMES[preset] : NAMES[OFF];
    }

    /** A preset's curve (a copy), or null for Custom, whose curve is saved separately. */
    public static float[] curve(int preset) {
        if (preset < 0 || preset >= CURVES.length) preset = OFF;
        return CURVES[preset] != null ? CURVES[preset].clone() : null;
    }

    /** Presets as listed: Off, Custom, then the rest A to Z. */
    public static List<Integer> listOrder() {
        List<Integer> order = new ArrayList<Integer>();
        for (int i = 0; i < NAMES.length; i++) {
            if (i != OFF && i != CUSTOM) order.add(i);
        }
        Collections.sort(order, new Comparator<Integer>() {
            @Override
            public int compare(Integer a, Integer b) {
                return NAMES[a].compareTo(NAMES[b]);
            }
        });
        order.add(0, CUSTOM);
        order.add(0, OFF);
        return order;
    }

    /** Does the curve change the sound at all? */
    public static boolean isOn(float[] curve) {
        if (curve == null) return false;
        for (float g : curve) {
            if (Math.abs(g) >= SILENT_DB) return true;
        }
        return false;
    }

    /**
     * Filters that shape the sound to curve (dB at each band's centre) at sampleRate: a peaking
     * filter per band. Neighbouring bands overlap, so their gains are solved for together (a few
     * Newton steps on the response at the band centres), and each band lands where its slider is.
     *
     * @return coefficients (b0, b1, b2, a1, a2) for each band; null for a band with nothing to do,
     *     or one too close to half the sample rate to filter
     */
    static double[][] design(float[] curve, int sampleRate) {
        int[] bands = new int[BANDS];
        int n = 0;
        for (int i = 0; i < BANDS; i++) {
            if (FREQUENCIES[i] < 0.45 * sampleRate) bands[n++] = i;
        }
        // How much each band's filter, at +1 dB, lifts each band's centre.
        double[][] spread = new double[n][n];
        for (int c = 0; c < n; c++) {
            double[][] one = {peaking(1, FREQUENCIES[bands[c]], sampleRate)};
            for (int r = 0; r < n; r++) spread[r][c] = responseDb(one, FREQUENCIES[bands[r]], sampleRate);
        }
        double[] gains = new double[BANDS];
        double[][] filters = new double[BANDS][];
        for (int step = 0; step < 4; step++) {
            double[] miss = new double[n];
            boolean done = true;
            for (int r = 0; r < n; r++) {
                int b = bands[r];
                miss[r] = clamp(curve[b], MAX_DB) - responseDb(filters, FREQUENCIES[b], sampleRate);
                if (Math.abs(miss[r]) > 0.01) done = false;
            }
            if (done) break;
            double[] change = solve(spread, miss);
            for (int r = 0; r < n; r++) {
                int b = bands[r];
                gains[b] = clamp(gains[b] + change[r], 2 * MAX_DB);
                filters[b] = Math.abs(gains[b]) < SILENT_DB ? null : peaking(gains[b], FREQUENCIES[b], sampleRate);
            }
        }
        return filters;
    }

    /** The filters' combined response at hz, in dB. */
    static double responseDb(double[][] filters, double hz, int sampleRate) {
        double w = 2 * Math.PI * hz / sampleRate;
        double cos1 = Math.cos(w);
        double sin1 = Math.sin(w);
        double cos2 = Math.cos(2 * w);
        double sin2 = Math.sin(2 * w);
        double db = 0;
        for (double[] k : filters) {
            if (k == null) continue;
            double nr = k[0] + k[1] * cos1 + k[2] * cos2;
            double ni = k[1] * sin1 + k[2] * sin2;
            double dr = 1 + k[3] * cos1 + k[4] * cos2;
            double di = k[3] * sin1 + k[4] * sin2;
            db += 10 * Math.log10((nr * nr + ni * ni) / (dr * dr + di * di));
        }
        return db;
    }

    /** RBJ cookbook peaking filter: db up or down around hz. */
    private static double[] peaking(double db, double hz, int sampleRate) {
        double a = Math.pow(10, db / 40);
        double w0 = 2 * Math.PI * hz / sampleRate;
        double cos = Math.cos(w0);
        double alpha = Math.sin(w0) / (2 * Q);
        double a0 = 1 + alpha / a;
        return new double[]{
                (1 + alpha * a) / a0,
                -2 * cos / a0,
                (1 - alpha * a) / a0,
                -2 * cos / a0,
                (1 - alpha / a) / a0};
    }

    private static double clamp(double v, double limit) {
        return Math.max(-limit, Math.min(limit, v));
    }

    /** Solves m x = v (Gaussian elimination with partial pivoting); m is left as it was. */
    private static double[] solve(double[][] m, double[] v) {
        int n = v.length;
        double[][] a = new double[n][];
        for (int i = 0; i < n; i++) {
            a[i] = Arrays.copyOf(m[i], n + 1);
            a[i][n] = v[i];
        }
        for (int col = 0; col < n; col++) {
            int pivot = col;
            for (int r = col + 1; r < n; r++) {
                if (Math.abs(a[r][col]) > Math.abs(a[pivot][col])) pivot = r;
            }
            double[] t = a[col];
            a[col] = a[pivot];
            a[pivot] = t;
            for (int r = 0; r < n; r++) {
                if (r == col) continue;
                double f = a[r][col] / a[col][col];
                for (int k = col; k <= n; k++) a[r][k] -= f * a[col][k];
            }
        }
        double[] x = new double[n];
        for (int i = 0; i < n; i++) x[i] = a[i][n] / a[i][i];
        return x;
    }
}
