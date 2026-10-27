package com.grapefruitmusic.radio;

import com.grapefruitmusic.playback.Fft;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * What's in a recording of a station: how loud it is, how loud the quietest moments are (the
 * static and hiss left between words), and narrow peaks in its spectrum, such as mains hum at 50 or
 * 60 Hz and its harmonics, which a filter for the station would have to take out.
 */
final class SoundAnalysis {
    /** A narrow peak: its frequency and how far it stands above the spectrum around it. */
    static final class Peak {
        final double hz;
        final double prominenceDb;

        Peak(double hz, double prominenceDb) {
            this.hz = hz;
            this.prominenceDb = prominenceDb;
        }
    }

    /** Median level of 50 ms frames, dBFS. */
    double medianDb;
    /** The quietest tenth of frames (between words: the noise underneath), dBFS. */
    double quietDb;
    /** 50 or 60 if there's mains hum (the stronger family), else 0. */
    int humHz;
    /** How far the hum's strongest harmonic stands out, dB. */
    double humDb;
    /** Hum harmonics found (multiples of humHz). */
    final List<Peak> humPeaks = new ArrayList<Peak>();
    /** Other narrow peaks (whines, carriers), strongest first. */
    final List<Peak> otherPeaks = new ArrayList<Peak>();

    /** A peak at least this far above its surroundings counts. */
    static final double PEAK_DB = 10;

    static SoundAnalysis of(short[] samples, int rate) {
        SoundAnalysis a = new SoundAnalysis();
        a.levels(samples, rate);
        a.spectrum(samples, rate);
        return a;
    }

    static double levelDb(short[] s, int from, int to) {
        double sum = 0;
        for (int i = from; i < to; i++) sum += (double) s[i] * s[i];
        double rms = Math.sqrt(sum / Math.max(1, to - from));
        return 20 * Math.log10(Math.max(rms, 1e-3) / 32768.0);
    }

    static double levelDb(short[] s) {
        return levelDb(s, 0, s.length);
    }

    private void levels(short[] s, int rate) {
        int frame = Math.max(1, rate / 20);
        int n = s.length / frame;
        if (n == 0) {
            medianDb = quietDb = levelDb(s);
            return;
        }
        double[] db = new double[n];
        for (int i = 0; i < n; i++) db[i] = levelDb(s, i * frame, (i + 1) * frame);
        Arrays.sort(db);
        medianDb = db[n / 2];
        quietDb = db[n / 10];
    }

    private void spectrum(short[] s, int rate) {
        // About 2.7 Hz per bin at 44.1 kHz: enough to tell 50 Hz hum from 60 Hz, and harmonics apart.
        int size = Integer.highestOneBit(Math.max(1024, rate * 3 / 8));
        if (s.length < size) return;
        Fft fft = new Fft(size);
        float[] window = new float[size];
        for (int i = 0; i < size; i++) window[i] = (float) (0.5 - 0.5 * Math.cos(2 * Math.PI * i / (size - 1)));
        double[] power = new double[size / 2];
        float[] re = new float[size];
        float[] im = new float[size];
        int frames = 0;
        for (int start = 0; start + size <= s.length; start += size / 2) {
            for (int i = 0; i < size; i++) {
                re[i] = s[start + i] / 32768f * window[i];
                im[i] = 0;
            }
            fft.transform(re, im, false);
            for (int k = 0; k < size / 2; k++) power[k] += (double) re[k] * re[k] + (double) im[k] * im[k];
            frames++;
        }
        for (int k = 0; k < power.length; k++) power[k] /= frames;
        double hzPerBin = (double) rate / size;

        // Mains hum: 50 Hz (most of the world) or 60 Hz (the Americas), and their harmonics. Hum is
        // strongest low down, so it takes two of the first four; a lone high "harmonic" is more
        // likely the programme (a note at 450 Hz isn't 50 Hz hum).
        double best = 0;
        for (int f0 : new int[]{50, 60}) {
            List<Peak> found = new ArrayList<Peak>();
            double score = 0;
            double strongest = 0;
            int low = 0;
            for (int h = 1; h <= 10; h++) {
                int bin = (int) Math.round(h * f0 / hzPerBin);
                double p = prominence(power, bin, 1);
                if (p >= PEAK_DB) {
                    found.add(new Peak(h * f0, p));
                    score += p;
                    strongest = Math.max(strongest, p);
                    if (h <= 4) low++;
                }
            }
            if (low >= 2 && score > best) {
                best = score;
                humHz = f0;
                humDb = strongest;
                humPeaks.clear();
                humPeaks.addAll(found);
            }
        }

        // Other narrow peaks up to 4 kHz, not hum harmonics.
        int top = Math.min(power.length - 30, (int) (4000 / hzPerBin));
        for (int k = (int) Math.ceil(30 / hzPerBin); k < top; k++) {
            if (power[k] < power[k - 1] || power[k] < power[k + 1]) continue;
            double p = prominence(power, k, 0);
            if (p < PEAK_DB + 2) continue;
            double hz = k * hzPerBin;
            if (humHz > 0 && Math.abs(hz / humHz - Math.round(hz / humHz)) * humHz < 2 * hzPerBin
                    && Math.round(hz / humHz) <= 10) {
                continue;
            }
            otherPeaks.add(new Peak(hz, p));
        }
        java.util.Collections.sort(otherPeaks, new java.util.Comparator<Peak>() {
            @Override
            public int compare(Peak a, Peak b) {
                return Double.compare(b.prominenceDb, a.prominenceDb);
            }
        });
        while (otherPeaks.size() > 6) otherPeaks.remove(otherPeaks.size() - 1);
    }

    /**
     * How far the strongest bin within reach of bin stands above the median of the bins around it
     * (up to 20 each side, fewer near the bottom of the spectrum).
     */
    private static double prominence(double[] power, int bin, int reach) {
        if (bin - reach < 1 || bin + 22 >= power.length) return 0;
        double peak = 0;
        for (int k = bin - reach; k <= bin + reach; k++) peak = Math.max(peak, power[k]);
        double[] around = new double[40];
        int n = 0;
        for (int k = Math.max(1, bin - 22); k <= bin + 22; k++) {
            if (Math.abs(k - bin) > 2) around[n++] = power[k];
        }
        if (n < 10) return 0;
        Arrays.sort(around, 0, n);
        double floor = around[n / 2];
        return 10 * Math.log10(Math.max(peak, 1e-20) / Math.max(floor, 1e-20));
    }

    /** For Radio Details. */
    String describe() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.US, "Station level %.1f dBFS (median), %.1f dBFS in its quietest moments.%n",
                medianDb, quietDb));
        if (humHz > 0) {
            sb.append("Hum: ").append(humHz).append(" Hz mains hum, harmonics at");
            for (Peak p : humPeaks) sb.append(String.format(Locale.US, " %.0f Hz (+%.0f dB)", p.hz, p.prominenceDb));
            sb.append(".\n");
        } else {
            sb.append("Hum: no 50 or 60 Hz hum stands out.\n");
        }
        if (!otherPeaks.isEmpty()) {
            sb.append("Other narrow peaks:");
            for (Peak p : otherPeaks) sb.append(String.format(Locale.US, " %.0f Hz (+%.0f dB)", p.hz, p.prominenceDb));
            sb.append(".\n");
        }
        return sb.toString();
    }

    /** Writes mono 16-bit samples as a WAV file. */
    static void writeWav(File f, short[] samples, int rate) throws IOException {
        OutputStream out = new FileOutputStream(f);
        try {
            DataOutputStream d = new DataOutputStream(new java.io.BufferedOutputStream(out));
            int bytes = samples.length * 2;
            d.writeBytes("RIFF");
            d.writeInt(Integer.reverseBytes(36 + bytes));
            d.writeBytes("WAVEfmt ");
            d.writeInt(Integer.reverseBytes(16));
            d.writeShort(Short.reverseBytes((short) 1)); // PCM
            d.writeShort(Short.reverseBytes((short) 1)); // mono
            d.writeInt(Integer.reverseBytes(rate));
            d.writeInt(Integer.reverseBytes(rate * 2));
            d.writeShort(Short.reverseBytes((short) 2));
            d.writeShort(Short.reverseBytes((short) 16));
            d.writeBytes("data");
            d.writeInt(Integer.reverseBytes(bytes));
            for (short s : samples) d.writeShort(Short.reverseBytes(s));
            d.flush();
        } finally {
            out.close();
        }
    }
}
