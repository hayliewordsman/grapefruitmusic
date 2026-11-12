package io.github.hayliewordsman.grapefruitai.util;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/** Background work and posting back to the main thread. */
public final class Bg {
    private static final ExecutorService POOL = Executors.newCachedThreadPool(new ThreadFactory() {
        private int n;

        @Override
        public synchronized Thread newThread(Runnable r) {
            Thread t = new Thread(r, "gfai-" + (n++));
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        }
    });
    private static Handler main;

    private Bg() {
    }

    public static void run(Runnable r) {
        POOL.execute(r);
    }

    public static synchronized void ui(Runnable r) {
        if (main == null) main = new Handler(Looper.getMainLooper());
        main.post(r);
    }

    public static synchronized void later(Runnable r, long ms) {
        if (main == null) main = new Handler(Looper.getMainLooper());
        main.postDelayed(r, ms);
    }
}
