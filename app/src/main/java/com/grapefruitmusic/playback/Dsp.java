package com.grapefruitmusic.playback;

/**
 * Grapefruit's own sound processing, for Volume Boost and Bass Boost: a bass shelf, gain, and a
 * look-ahead limiter that turns peaks down just before they'd clip, so boosted music gets louder
 * instead of distorting (the idea behind Viper4Android's eXtra-Loud). Pure Java (unit tested).
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

    /** Bass Boost: a low shelf this many dB up, below about this frequency. */
    static final double BASS_DB = 8;
    static final double BASS_HZ = 90;

    /** Peaks are held under this (about -0.5 dBFS). */
    static final double CEILING = 0.944;
    private static final double LOOKAHEAD_S = 0.004;
    private static final double RELEASE_S = 0.15;

    private volatile int boostLevel;
    private volatile boolean bass;
    private volatile float volume = 1f;

    private int rate;
    private int channels;

    // Bass shelf (direct form I); per channel x1, x2, y1, y2.
    private double b0, b1, b2, a1, a2;
    private double[][] shelf;

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
    public static boolean anyOn(int boostLevel, boolean bass) {
        return boostLevel > BOOST_OFF || bass;
    }

    public void setBoost(int level) {
        boostLevel = Math.max(BOOST_OFF, Math.min(BOOST_EXTREME, level));
    }

    public void setBass(boolean on) {
        bass = on;
    }

    /** Volume Match and ducking, 0 to 1, applied before the boost. */
    public void setVolume(float v) {
        volume = v;
    }

    public void setFormat(int sampleRate, int channelCount) {
        rate = sampleRate;
        channels = Math.max(1, channelCount);
        // RBJ cookbook low shelf with a slope of 1.
        double a = Math.pow(10, BASS_DB / 40);
        double w0 = 2 * Math.PI * BASS_HZ / rate;
        double cos = Math.cos(w0);
        double alpha = Math.sin(w0) / 2 * Math.sqrt(2);
        double k = 2 * Math.sqrt(a) * alpha;
        double a0 = (a + 1) + (a - 1) * cos + k;
        b0 = a * ((a + 1) - (a - 1) * cos + k) / a0;
        b1 = 2 * a * ((a - 1) - (a + 1) * cos) / a0;
        b2 = a * ((a + 1) - (a - 1) * cos - k) / a0;
        a1 = -2 * ((a - 1) + (a + 1) * cos) / a0;
        a2 = ((a + 1) + (a - 1) * cos - k) / a0;
        shelf = new double[channels][4];

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
        boolean bassOn = bass;
        double g = volume * Math.pow(10, BOOST_DB[boostLevel] / 20);
        int frames = count / channels;
        int i = offset;
        for (int f = 0; f < frames; f++) {
            int base = pos * channels;
            double peak = 0;
            for (int c = 0; c < channels; c++) {
                double x = pcm[i + c] / 32768.0;
                if (bassOn) {
                    double[] s = shelf[c];
                    double y = b0 * x + b1 * s[0] + b2 * s[1] - a1 * s[2] - a2 * s[3];
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
