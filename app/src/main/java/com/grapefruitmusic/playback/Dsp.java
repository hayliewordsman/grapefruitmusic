package com.grapefruitmusic.playback;

/**
 * Grapefruit's own sound processing, for Volume Boost and the Equalizer: an equalizer filter, gain,
 * and a look-ahead limiter that turns peaks down just before they'd clip, so boosted music gets
 * louder instead of distorting (the idea behind Viper4Android's eXtra-Loud). Pure Java (unit tested).
 *
 * <p>The settings may change from any thread while audio is being processed.
 */
public final class Dsp {
    public static final int BOOST_OFF = 0;
    public static final int BOOST_MILD = 1;
    public static final int BOOST_MEDIUM = 2;
    public static final int BOOST_STRONG = 3;
    public static final int BOOST_EXTREME = 4;
    public static final String[] BOOST_NAMES = {"Off", "Mild", "Medium", "Strong", "Extreme"};
    /** Gain in dB for each Volume Boost level. */
    static final double[] BOOST_DB = {0, 4, 7, 10, 14};

    public static final int EQ_NONE = 0;
    public static final int EQ_BASS_BOOSTER = 1;
    public static final int EQ_BASS_REDUCER = 2;
    public static final int EQ_VOCAL_BOOSTER = 3;
    public static final int EQ_VOCAL_REDUCER = 4;
    public static final String[] EQ_NAMES = {"None", "Bass Booster", "Bass Reducer", "Vocal Booster", "Vocal Reducer"};

    /** Bass Booster / Reducer: a low shelf this many dB up or down, below about this frequency. */
    static final double BASS_DB = 8;
    static final double BASS_HZ = 90;
    /** Vocal Booster / Reducer: a broad bump or dip this many dB, around where voices carry. */
    static final double VOCAL_DB = 6;
    static final double VOCAL_HZ = 1500;
    private static final double VOCAL_Q = 0.5;

    /** Peaks are held under this (about -0.5 dBFS). */
    static final double CEILING = 0.944;
    private static final double LOOKAHEAD_S = 0.004;
    private static final double RELEASE_S = 0.15;

    private volatile int boostLevel;
    private volatile int eq;
    private volatile float volume = 1f;

    private int rate;
    private int channels;

    // Equalizer: one biquad per preset (b0, b1, b2, a1, a2; none for EQ_NONE), run in direct form I
    // with per-channel state x1, x2, y1, y2.
    private double[][] eqFilters;
    private double[][] eqState;
    private int eqRunning;

    // Limiter. The sound is delayed by lookahead frames, so the gain can come down before a peak
    // reaches the output. needs holds the gain each delayed frame needs to stay under the ceiling.
    private int lookahead;
    private double[] delay;
    private double[] needs;
    private int pos;
    private long frameNo;
    private double gain = 1;
    private double attack;
    private double release;
    private double[] out;
    // Sliding minimum of the needs of the frames in the delay line (a monotonic queue).
    private double[] qValue;
    private long[] qFrame;
    private int qHead;
    private int qSize;

    /** Is any effect on? (If not, Android's own player plays the music untouched.) */
    public static boolean anyOn(int boostLevel, int eq) {
        return boostLevel > BOOST_OFF || eq != EQ_NONE;
    }

    public void setBoost(int level) {
        boostLevel = Math.max(BOOST_OFF, Math.min(BOOST_EXTREME, level));
    }

    public void setEq(int preset) {
        eq = Math.max(EQ_NONE, Math.min(EQ_VOCAL_REDUCER, preset));
    }

    /** Volume Match and ducking, 0 to 1, applied before the boost. */
    public void setVolume(float v) {
        volume = v;
    }

    public void setFormat(int sampleRate, int channelCount) {
        rate = sampleRate;
        channels = Math.max(1, channelCount);
        eqFilters = new double[][]{
                null,
                lowShelf(BASS_DB, BASS_HZ),
                lowShelf(-BASS_DB, BASS_HZ),
                peaking(VOCAL_DB, VOCAL_HZ, VOCAL_Q),
                peaking(-VOCAL_DB, VOCAL_HZ, VOCAL_Q)};
        eqState = new double[channels][4];
        eqRunning = EQ_NONE;

        lookahead = Math.max(1, (int) Math.round(LOOKAHEAD_S * rate));
        delay = new double[lookahead * channels];
        needs = new double[lookahead];
        java.util.Arrays.fill(needs, 1);
        out = new double[channels];
        qValue = new double[lookahead + 1];
        qFrame = new long[lookahead + 1];
        qHead = 0;
        qSize = 0;
        pos = 0;
        frameNo = 0;
        gain = 1;
        // Down within the look-ahead; back up over RELEASE_S.
        attack = Math.exp(-3.0 / lookahead);
        release = Math.exp(-1.0 / (RELEASE_S * rate));
    }

    /** RBJ cookbook low shelf with a slope of 1. */
    private double[] lowShelf(double db, double hz) {
        double a = Math.pow(10, db / 40);
        double w0 = 2 * Math.PI * hz / rate;
        double cos = Math.cos(w0);
        double alpha = Math.sin(w0) / 2 * Math.sqrt(2);
        double k = 2 * Math.sqrt(a) * alpha;
        double a0 = (a + 1) + (a - 1) * cos + k;
        return new double[]{
                a * ((a + 1) - (a - 1) * cos + k) / a0,
                2 * a * ((a - 1) - (a + 1) * cos) / a0,
                a * ((a + 1) - (a - 1) * cos - k) / a0,
                -2 * ((a - 1) + (a + 1) * cos) / a0,
                ((a + 1) + (a - 1) * cos - k) / a0};
    }

    /** RBJ cookbook peaking filter: db up or down around hz; a smaller q is broader. */
    private double[] peaking(double db, double hz, double q) {
        double a = Math.pow(10, db / 40);
        double w0 = 2 * Math.PI * hz / rate;
        double cos = Math.cos(w0);
        double alpha = Math.sin(w0) / (2 * q);
        double a0 = 1 + alpha / a;
        return new double[]{
                (1 + alpha * a) / a0,
                -2 * cos / a0,
                (1 - alpha * a) / a0,
                -2 * cos / a0,
                (1 - alpha / a) / a0};
    }

    /** Clears filters and limiter (after a seek). */
    public void reset() {
        if (rate > 0) setFormat(rate, channels);
    }

    /** The delay the limiter adds, in frames. */
    public int latencyFrames() {
        return lookahead;
    }

    /** Processes interleaved 16-bit samples in place (delayed by latencyFrames()). */
    public void process(short[] pcm, int offset, int count) {
        if (rate == 0) throw new IllegalStateException("setFormat first");
        int preset = eq;
        if (preset != eqRunning) {
            // A different filter: start it from silence rather than another filter's state.
            for (double[] s : eqState) java.util.Arrays.fill(s, 0);
            eqRunning = preset;
        }
        double[] k = eqFilters[preset];
        double g = volume * Math.pow(10, BOOST_DB[boostLevel] / 20);
        int frames = count / channels;
        int i = offset;
        for (int f = 0; f < frames; f++) {
            int base = pos * channels;
            double peak = 0;
            for (int c = 0; c < channels; c++) {
                double x = pcm[i + c] / 32768.0;
                if (k != null) {
                    double[] s = eqState[c];
                    double y = k[0] * x + k[1] * s[0] + k[2] * s[1] - k[3] * s[2] - k[4] * s[3];
                    s[1] = s[0];
                    s[0] = x;
                    s[3] = s[2];
                    s[2] = y;
                    x = y;
                }
                x *= g;
                double abs = Math.abs(x);
                if (abs > peak) peak = abs;
                out[c] = delay[base + c];
                delay[base + c] = x;
            }
            double outNeed = needs[pos];
            double need = peak > CEILING ? CEILING / peak : 1;
            needs[pos] = need;
            pos = pos + 1 == lookahead ? 0 : pos + 1;

            // The lowest need among the frames about to come out.
            while (qSize > 0 && qValue[(qHead + qSize - 1) % qValue.length] >= need) qSize--;
            int back = (qHead + qSize) % qValue.length;
            qValue[back] = need;
            qFrame[back] = frameNo;
            qSize++;
            while (qFrame[qHead] <= frameNo - lookahead) {
                qHead = (qHead + 1) % qValue.length;
                qSize--;
            }
            double target = Math.min(qValue[qHead], outNeed);

            gain = target < gain ? target + (gain - target) * attack : target + (gain - target) * release;
            if (gain > outNeed) gain = outNeed; // never let the frame going out clip
            for (int c = 0; c < channels; c++) {
                double y = out[c] * gain;
                if (y > 0.9999) y = 0.9999;
                else if (y < -0.9999) y = -0.9999;
                pcm[i + c] = (short) Math.round(y * 32767);
            }
            frameNo++;
            i += channels;
        }
    }
}
