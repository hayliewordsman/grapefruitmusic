package com.grapefruitmusic.util;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/** Tiny background-work helper: run on a pool, deliver the result on the main thread. */
public final class Bg {
    public interface Work<T> {
        T run() throws Exception;
    }

    public interface Done<T> {
        void done(T result, Exception error);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService POOL = Executors.newFixedThreadPool(4, named("gf-bg"));

    private Bg() {
    }

    public static ThreadFactory named(final String prefix) {
        final AtomicInteger n = new AtomicInteger();
        return new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, prefix + "-" + n.incrementAndGet());
                t.setPriority(Thread.NORM_PRIORITY - 1);
                return t;
            }
        };
    }

    public static <T> void run(final Work<T> work, final Done<T> done) {
        POOL.execute(new Runnable() {
            @Override
            public void run() {
                T result = null;
                Exception error = null;
                try {
                    result = work.run();
                } catch (Exception e) {
                    error = e;
                }
                final T r = result;
                final Exception err = error;
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        done.done(r, err);
                    }
                });
            }
        });
    }

    public static void execute(Runnable r) {
        POOL.execute(r);
    }

    public static void main(Runnable r) {
        MAIN.post(r);
    }

    public static void mainDelayed(Runnable r, long delayMs) {
        MAIN.postDelayed(r, delayMs);
    }

    public static void cancelMain(Runnable r) {
        MAIN.removeCallbacks(r);
    }
}
