package com.grapefruitmusic.util;

import android.graphics.Bitmap;

/** Cheap blur for the Now Playing background: shrink, box-blur, let the GPU scale it up. */
public final class Blur {
    private static final int SIZE = 40;

    private Blur() {
    }

    public static Bitmap of(Bitmap src) {
        if (src == null) return null;
        Bitmap small = Bitmap.createScaledBitmap(src, SIZE, SIZE, true);
        int[] px = new int[SIZE * SIZE];
        small.getPixels(px, 0, SIZE, 0, 0, SIZE, SIZE);
        if (small != src) small.recycle();
        int[] tmp = new int[px.length];
        for (int pass = 0; pass < 3; pass++) {
            boxBlur(px, tmp, true);
            boxBlur(tmp, px, false);
        }
        return Bitmap.createBitmap(px, SIZE, SIZE, Bitmap.Config.ARGB_8888);
    }

    private static void boxBlur(int[] in, int[] out, boolean horizontal) {
        int r = 3;
        for (int a = 0; a < SIZE; a++) {
            for (int b = 0; b < SIZE; b++) {
                int sr = 0;
                int sg = 0;
                int sb = 0;
                int n = 0;
                for (int k = -r; k <= r; k++) {
                    int bb = Math.max(0, Math.min(SIZE - 1, b + k));
                    int p = horizontal ? in[a * SIZE + bb] : in[bb * SIZE + a];
                    sr += (p >> 16) & 0xff;
                    sg += (p >> 8) & 0xff;
                    sb += p & 0xff;
                    n++;
                }
                int idx = horizontal ? a * SIZE + b : b * SIZE + a;
                out[idx] = 0xff000000 | ((sr / n) << 16) | ((sg / n) << 8) | (sb / n);
            }
        }
    }
}
