package com.grapefruitmusic.playback;

/** In-place radix-2 complex FFT of a fixed size (a power of two). Not thread-safe per call. */
public final class Fft {
    private final int n;
    private final int[] reverse;
    private final float[] cos;
    private final float[] sin;

    public Fft(int n) {
        if (Integer.bitCount(n) != 1) throw new IllegalArgumentException("size must be a power of two: " + n);
        this.n = n;
        int bits = Integer.numberOfTrailingZeros(n);
        reverse = new int[n];
        for (int i = 0; i < n; i++) reverse[i] = Integer.reverse(i) >>> (32 - bits);
        cos = new float[n / 2];
        sin = new float[n / 2];
        for (int i = 0; i < n / 2; i++) {
            cos[i] = (float) Math.cos(2 * Math.PI * i / n);
            sin[i] = (float) Math.sin(2 * Math.PI * i / n);
        }
    }

    /**
     * Forward: X[k] = sum x[j] e^(-2 pi i jk/n). Inverse: x[j] = sum X[k] e^(+2 pi i jk/n), without
     * the 1/n (the caller scales).
     */
    public void transform(float[] re, float[] im, boolean inverse) {
        for (int i = 0; i < n; i++) {
            int j = reverse[i];
            if (j > i) {
                float t = re[i];
                re[i] = re[j];
                re[j] = t;
                t = im[i];
                im[i] = im[j];
                im[j] = t;
            }
        }
        for (int size = 2; size <= n; size <<= 1) {
            int half = size >> 1;
            int step = n / size;
            for (int start = 0; start < n; start += size) {
                for (int k = 0; k < half; k++) {
                    float c = cos[k * step];
                    float s = inverse ? sin[k * step] : -sin[k * step];
                    int a = start + k;
                    int b = a + half;
                    float tr = re[b] * c - im[b] * s;
                    float ti = re[b] * s + im[b] * c;
                    re[b] = re[a] - tr;
                    im[b] = im[a] - ti;
                    re[a] += tr;
                    im[a] += ti;
                }
            }
        }
    }
}
