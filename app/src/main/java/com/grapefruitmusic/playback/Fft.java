package com.grapefruitmusic.playback;

/** In-place radix-2 complex FFT of a fixed size (a power of two). Not thread-safe per call. */
final class Fft {
    private final int n;
    private final int[] reverse;
    /** Each stage's twiddles in turn (stage of size s: s/2 of them), for going forward and back. */
    private final float[] cos;
    private final float[] sinForward;
    private final float[] sinInverse;
    /** For inverseReal: a transform of half the size, and e^(2 pi i k / n). */
    private Fft half;
    private float[] turnCos;
    private float[] turnSin;
    private float[] zr;
    private float[] zi;

    Fft(int n) {
        if (Integer.bitCount(n) != 1) throw new IllegalArgumentException("size must be a power of two: " + n);
        this.n = n;
        int bits = Integer.numberOfTrailingZeros(n);
        reverse = new int[n];
        for (int i = 0; i < n; i++) reverse[i] = bits == 0 ? 0 : Integer.reverse(i) >>> (32 - bits);
        cos = new float[Math.max(1, n - 1)];
        sinForward = new float[cos.length];
        sinInverse = new float[cos.length];
        int at = 0;
        for (int size = 2; size <= n; size <<= 1) {
            for (int k = 0; k < size / 2; k++) {
                double a = 2 * Math.PI * k / size;
                cos[at] = (float) Math.cos(a);
                sinForward[at] = (float) -Math.sin(a);
                sinInverse[at] = (float) Math.sin(a);
                at++;
            }
        }
    }

    /**
     * Forward: X[k] = sum x[j] e^(-2 pi i jk/n). Inverse: x[j] = sum X[k] e^(+2 pi i jk/n), without
     * the 1/n (the caller scales).
     */
    void transform(float[] re, float[] im, boolean inverse) {
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
        float[] sin = inverse ? sinInverse : sinForward;
        int stage = 0;
        for (int size = 2; size <= n; size <<= 1) {
            int half = size >> 1;
            for (int start = 0; start < n; start += size) {
                for (int k = 0; k < half; k++) {
                    float c = cos[stage + k];
                    float s = sin[stage + k];
                    int a = start + k;
                    int b = a + half;
                    float br = re[b];
                    float bi = im[b];
                    float tr = br * c - bi * s;
                    float ti = br * s + bi * c;
                    float ar = re[a];
                    float ai = im[a];
                    re[b] = ar - tr;
                    im[b] = ai - ti;
                    re[a] = ar + tr;
                    im[a] = ai + ti;
                }
            }
            stage += half;
        }
    }

    /**
     * The inverse of a real signal's spectrum: re/im hold X[0..n/2] (the rest is their mirror);
     * out gets x[0..n) = sum X[k] e^(+2 pi i jk/n), without the 1/n. Half the work of transform():
     * the even and odd samples come out of one complex transform of half the size.
     */
    void inverseReal(float[] re, float[] im, float[] out) {
        int m = n / 2;
        if (half == null) {
            half = new Fft(m);
            turnCos = new float[m];
            turnSin = new float[m];
            for (int k = 0; k < m; k++) {
                turnCos[k] = (float) Math.cos(2 * Math.PI * k / n);
                turnSin[k] = (float) Math.sin(2 * Math.PI * k / n);
            }
            zr = new float[m];
            zi = new float[m];
        }
        for (int k = 0; k < m; k++) {
            // A = X[k] + conj(X[m-k]) (the even samples'); B = (X[k] - conj(X[m-k])) e^(2 pi i k/n) (odd).
            float xr = re[k];
            float xi = im[k];
            float yr = re[m - k];
            float yi = -im[m - k];
            float ar = xr + yr;
            float ai = xi + yi;
            float dr = xr - yr;
            float di = xi - yi;
            float c = turnCos[k];
            float s = turnSin[k];
            float br = dr * c - di * s;
            float bi = dr * s + di * c;
            // Z = A + iB.
            zr[k] = ar - bi;
            zi[k] = ai + br;
        }
        half.transform(zr, zi, true);
        for (int j = 0; j < m; j++) {
            out[2 * j] = zr[j];
            out[2 * j + 1] = zi[j];
        }
    }
}
