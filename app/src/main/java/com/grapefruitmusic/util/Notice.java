package com.grapefruitmusic.util;

import android.content.Context;
import android.os.Looper;
import android.widget.Toast;

/**
 * Short messages ("Added to Road Trip"). While Grapefruit is on screen they appear in its own
 * style (see MainActivity); otherwise, e.g. from playback in the background, as a system toast.
 */
public final class Notice {
    /** Shows notices inside the app. */
    public interface Sink {
        void showNotice(CharSequence text, boolean longer);
    }

    private static Sink sink;

    private Notice() {
    }

    /** Main thread. */
    public static void setSink(Sink s) {
        sink = s;
    }

    /** Main thread. */
    public static void clearSink(Sink s) {
        if (sink == s) sink = null;
    }

    public static void show(Context context, CharSequence text) {
        show(context, text, false);
    }

    /** For messages that take longer to read, such as errors. */
    public static void showLong(Context context, CharSequence text) {
        show(context, text, true);
    }

    private static void show(Context context, final CharSequence text, final boolean longer) {
        final Context app = context.getApplicationContext();
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Bg.main(new Runnable() {
                @Override
                public void run() {
                    show(app, text, longer);
                }
            });
            return;
        }
        if (sink != null) {
            sink.showNotice(text, longer);
        } else {
            Toast.makeText(app, text, longer ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show();
        }
    }
}
