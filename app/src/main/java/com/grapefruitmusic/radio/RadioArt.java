package com.grapefruitmusic.radio;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Artwork for a radio station: its frequency, large, on a colourful gradient (the colour picked
 * by the frequency, so stations look different), with its name below once known.
 */
public final class RadioArt {
    /** Gradient pairs, top-left to bottom-right: grapefruit, pink, plum, ocean, teal, lime, gold. */
    private static final int[][] COLORS = {
            {0xffff8a5b, 0xffe83850}, {0xffff7eb3, 0xffd6336c}, {0xffb57bee, 0xff6f3fc4},
            {0xff5ab0ff, 0xff2f5fd0}, {0xff3fd1c0, 0xff16857f}, {0xff9fdc4f, 0xff3c9a3a},
            {0xffffc94d, 0xffe8792c}};

    private static final Map<String, Bitmap> CACHE = new LinkedHashMap<String, Bitmap>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Bitmap> eldest) {
            return size() > 8;
        }
    };

    private RadioArt() {
    }

    /** The colour pair for a station (stable for its frequency). */
    static int[] colors(int freq) {
        return COLORS[(freq / Radio.STEP) % COLORS.length];
    }

    /** A colour pair (top-left, bottom-right) for anything named seed: the same one each time. */
    public static int[] colorsFor(String seed) {
        return COLORS[(seed.hashCode() & 0x7fffffff) % COLORS.length];
    }

    /** A size x size picture of the station. Main thread only (it's cached). */
    public static Bitmap of(int freq, String name, int size) {
        size = Math.max(16, size);
        String key = freq + "|" + name + "|" + size;
        Bitmap cached = CACHE.get(key);
        if (cached != null) return cached;
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        int[] colors = colors(freq);
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setShader(new LinearGradient(0, 0, size, size, colors[0], colors[1], Shader.TileMode.CLAMP));
        c.drawRect(0, 0, size, size, bg);

        // Radio waves, faint, behind the numbers.
        drawWaves(c, size);
        float cx = size * 0.5f;

        TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        text.setColor(0xffffffff);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        text.setShadowLayer(size * 0.02f, 0, size * 0.008f, 0x33000000);
        String number = Radio.format(freq);
        text.setTextSize(size * (number.length() > 5 ? 0.25f : 0.3f));
        c.drawText(number, cx, size * 0.52f, text);

        text.setTextSize(size * 0.1f);
        text.setColor(0xd9ffffff);
        c.drawText("FM", cx, size * 0.66f, text);

        if (name != null && name.length() > 0) {
            text.setTypeface(Typeface.DEFAULT);
            text.setColor(0xffffffff);
            text.setTextSize(size * 0.085f);
            CharSequence fitted = TextUtils.ellipsize(name, text, size * 0.86f, TextUtils.TruncateAt.END);
            c.drawText(fitted, 0, fitted.length(), cx, size * 0.86f, text);
        }
        CACHE.put(key, b);
        return b;
    }

    private static void drawWaves(Canvas c, int size) {
        Paint waves = new Paint(Paint.ANTI_ALIAS_FLAG);
        waves.setStyle(Paint.Style.STROKE);
        waves.setColor(0x26ffffff);
        waves.setStrokeWidth(size * 0.035f);
        waves.setStrokeCap(Paint.Cap.ROUND);
        RectF oval = new RectF();
        float cx = size * 0.5f;
        float cy = size * 0.47f;
        for (int i = 1; i <= 3; i++) {
            float r = size * (0.22f + 0.13f * i);
            oval.set(cx - r, cy - r, cx + r, cy + r);
            c.drawArc(oval, -40, 80, false, waves);
            c.drawArc(oval, 140, 80, false, waves);
        }
    }

    /**
     * Artwork for an internet station: a big, square logo as it is; a small or oblong one on a
     * white card on a gradient; and with no logo, the station's name on the gradient. The colour
     * comes from seed, so it stays the same. Any thread (not cached here).
     */
    public static Bitmap station(String seed, String name, Bitmap logo, int size) {
        size = Math.max(16, size);
        if (logo != null) {
            int w = logo.getWidth();
            int h = logo.getHeight();
            float aspect = w / (float) Math.max(1, h);
            if (Math.min(w, h) >= Math.min(size, 160) && aspect > 0.85f && aspect < 1.18f) return logo;
        }
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        int[] colors = colorsFor(seed);
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setShader(new LinearGradient(0, 0, size, size, colors[0], colors[1], Shader.TileMode.CLAMP));
        c.drawRect(0, 0, size, size, bg);
        drawWaves(c, size);
        if (logo != null) {
            float card = size * 0.6f;
            float left = (size - card) / 2;
            float top = (size - card) / 2;
            Paint white = new Paint(Paint.ANTI_ALIAS_FLAG);
            white.setColor(0xffffffff);
            white.setShadowLayer(size * 0.03f, 0, size * 0.01f, 0x40000000);
            float radius = size * 0.08f;
            c.drawRoundRect(new RectF(left, top, left + card, top + card), radius, radius, white);
            float inner = card * 0.82f;
            float scale = Math.min(inner / logo.getWidth(), inner / logo.getHeight());
            float lw = logo.getWidth() * scale;
            float lh = logo.getHeight() * scale;
            Paint smooth = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
            c.drawBitmap(logo, new Rect(0, 0, logo.getWidth(), logo.getHeight()),
                    new RectF((size - lw) / 2, (size - lh) / 2, (size + lw) / 2, (size + lh) / 2), smooth);
            return b;
        }
        TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        text.setColor(0xffffffff);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        text.setShadowLayer(size * 0.02f, 0, size * 0.008f, 0x33000000);
        int width = (int) (size * 0.8f);
        String label = name == null || name.trim().length() == 0 ? "Radio" : name.trim();
        StaticLayout layout = null;
        // As big as fits in three lines.
        for (float t = 0.2f; t >= 0.07f; t -= 0.01f) {
            text.setTextSize(size * t);
            layout = new StaticLayout(label, text, width, Layout.Alignment.ALIGN_CENTER, 1f, 0f, false);
            boolean wordsFit = true;
            for (String word : label.split("\\s+")) {
                if (text.measureText(word) > width) wordsFit = false;
            }
            if (layout.getLineCount() <= 3 && wordsFit && layout.getHeight() < size * 0.7f) break;
        }
        c.save();
        c.translate((size - width) / 2f, (size - layout.getHeight()) / 2f);
        layout.draw(c);
        c.restore();
        return b;
    }
}
