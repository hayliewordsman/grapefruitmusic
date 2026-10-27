package com.grapefruitmusic.playback;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Random;

public class FftTest {
    @Test
    public void forwardMatchesTheDefinition() {
        int n = 64;
        Fft fft = new Fft(n);
        Random r = new Random(2);
        float[] re = new float[n];
        float[] im = new float[n];
        double[] xr = new double[n];
        double[] xi = new double[n];
        for (int i = 0; i < n; i++) {
            re[i] = (float) (xr[i] = r.nextGaussian());
            im[i] = (float) (xi[i] = r.nextGaussian());
        }
        fft.transform(re, im, false);
        for (int k = 0; k < n; k++) {
            double sr = 0;
            double si = 0;
            for (int j = 0; j < n; j++) {
                double a = -2 * Math.PI * j * k / n;
                sr += xr[j] * Math.cos(a) - xi[j] * Math.sin(a);
                si += xr[j] * Math.sin(a) + xi[j] * Math.cos(a);
            }
            assertEquals(sr, re[k], 1e-3);
            assertEquals(si, im[k], 1e-3);
        }
    }

    @Test
    public void inverseRealMatchesTheFullInverse() {
        int n = 2048;
        Fft fft = new Fft(n);
        Random r = new Random(5);
        float[] x = new float[n];
        for (int i = 0; i < n; i++) x[i] = (float) r.nextGaussian();
        float[] re = x.clone();
        float[] im = new float[n];
        fft.transform(re, im, false);
        float[] out = new float[n];
        fft.inverseReal(re, im, out);
        for (int i = 0; i < n; i++) assertEquals(x[i] * n, out[i], 0.05);
    }
}
