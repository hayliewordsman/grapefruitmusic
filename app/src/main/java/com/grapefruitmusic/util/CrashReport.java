package com.grapefruitmusic.util;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * When Grapefruit closes because of a fault in it, a short report of what went wrong, kept on the
 * device: the app and Android versions, the device model, where in the code it stopped and what
 * the app had just been doing. Nothing is sent anywhere: the next time Grapefruit starts, it offers
 * to share the report (MainActivity), and the person decides. Web addresses lose their query
 * strings (where sign-in tokens go) and file paths their names.
 */
public final class CrashReport {
    private static final String TAG = "GrapefruitCrash";
    private static final String FILE = "last-crash.txt";
    private static final int MAX_CHARS = 24 * 1024;
    private static final int NOTES = 16;
    /** The last few things the app did (see note()), oldest first. */
    private static final ArrayDeque<String> notes = new ArrayDeque<String>();
    private static final SimpleDateFormat CLOCK = new SimpleDateFormat("HH:mm:ss", Locale.US);
    /**
     * Memory kept back and let go as a report is written, so a crash for want of memory still
     * leaves one (writing it needs memory too).
     */
    private static byte[] reserve;

    private CrashReport() {
    }

    /** Keeps a report of any crash from now on (once, as the app starts). */
    public static void install(final Context context) {
        final Context app = context.getApplicationContext();
        final Thread.UncaughtExceptionHandler android = Thread.getDefaultUncaughtExceptionHandler();
        reserve = new byte[96 * 1024];
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable e) {
                reserve = null;
                try {
                    write(app, report(app, thread, e));
                } catch (Throwable full) {
                    // Too little memory for the whole report: at least what went wrong, and where.
                    try {
                        write(app, brief(e));
                    } catch (Throwable ignored) {
                        // Nothing more can be done: Android's own handler still closes the app.
                    }
                }
                if (android != null) android.uncaughtException(thread, e);
            }
        });
    }

    /** Remembers something the app just did, for a report (no titles or names: what, not which). */
    public static void note(String what) {
        String line;
        synchronized (CLOCK) {
            line = CLOCK.format(new Date()) + "  " + redact(what);
        }
        synchronized (notes) {
            if (notes.size() == NOTES) notes.removeFirst();
            notes.addLast(line);
        }
    }

    /** The report of the last crash, if there is one not yet shared or dismissed. */
    public static String pending(Context context) {
        File f = file(context);
        if (!f.isFile()) return null;
        try {
            FileInputStream in = new FileInputStream(f);
            try {
                String text = com.grapefruitmusic.net.Http.readAll(in);
                return text.length() > 0 ? text : null;
            } finally {
                in.close();
            }
        } catch (IOException e) {
            return null;
        }
    }

    /** Forgets the last crash (shared, or dismissed). */
    public static void clear(Context context) {
        if (!file(context).delete()) Log.d(TAG, "no report to clear");
    }

    private static File file(Context context) {
        return new File(context.getApplicationContext().getFilesDir(), FILE);
    }

    private static void write(Context context, String report) throws IOException {
        FileOutputStream out = new FileOutputStream(file(context));
        try {
            out.write(report.getBytes("UTF-8"));
        } finally {
            out.close();
        }
    }

    static String report(Context context, Thread thread, Throwable e) {
        StringBuilder b = new StringBuilder();
        b.append("Grapefruit Music & Podcasts crash report\n");
        String version = "?";
        try {
            PackageInfo p = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            version = p.versionName + " (" + p.versionCode + ")";
        } catch (Exception ignored) {
            // Unknown, then.
        }
        b.append("App: ").append(version).append('\n');
        b.append("Android: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        b.append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
        SimpleDateFormat when = new SimpleDateFormat("yyyy-MM-dd HH:mm 'UTC'", Locale.US);
        when.setTimeZone(TimeZone.getTimeZone("UTC"));
        b.append("When: ").append(when.format(new Date())).append('\n');
        b.append("Thread: ").append(thread != null ? thread.getName() : "?").append("\n\n");
        StringWriter trace = new StringWriter();
        e.printStackTrace(new PrintWriter(trace));
        b.append(redact(trace.toString()));
        synchronized (notes) {
            if (!notes.isEmpty()) {
                b.append("\nJust before:\n");
                for (String n : notes) b.append("  ").append(n).append('\n');
            }
        }
        return b.length() > MAX_CHARS ? b.substring(0, MAX_CHARS) + "\n[…]\n" : b.toString();
    }

    /** The fault and the first few places in the code, in as little memory as can be. */
    static String brief(Throwable e) {
        StringBuilder b = new StringBuilder(1024);
        b.append("Grapefruit Music & Podcasts crash report (short)\n");
        b.append("Android: ").append(Build.VERSION.RELEASE).append('\n');
        b.append(e.getClass().getName()).append('\n');
        StackTraceElement[] trace = e.getStackTrace();
        for (int i = 0; i < trace.length && i < 12; i++) b.append("  at ").append(trace[i]).append('\n');
        return b.toString();
    }

    /** Web addresses without their query strings or sign-in; file paths without their names. */
    static String redact(String text) {
        if (text == null) return "";
        return text
                .replaceAll("(https?://)[^/\\s@]*@", "$1")
                .replaceAll("(https?://[^\\s?#\"']*)[?#][^\\s\"']*", "$1?…")
                .replaceAll("(/(?:storage|sdcard|mnt|data/data|data/user)/)[^\\s:\"']*", "$1…");
    }
}
