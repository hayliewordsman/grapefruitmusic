package com.grapefruitmusic.ui.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import com.grapefruitmusic.playback.Equalizer;

import java.util.Locale;

/**
 * The equalizer's bands as sliders: drag a knob up or down (or tap a column to jump there). A
 * smooth curve joins the knobs, filled toward 0 dB in the app icon's gradient.
 */
public class EqualizerView extends View {
    public interface Listener {
        /** The user moved band to db (in half-dB steps). */
        void onBandChanged(EqualizerView view, int band, float db);
    }

    // The app icon's gradient, as on the toggles.
    private static final int TOP = 0xffff8c6b;
    private static final int BOTTOM = 0xffec485c;
    private static final String[] LABELS = {"31", "62", "125", "250", "500", "1K", "2K", "4K", "8K", "16K"};

    private final float density;
    private final float maxDb;
    private final float[] gains;
    /** What's drawn: gains, or on the way there after setGains(.., true). */
    private final float[] shown;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path curve = new Path();
    private final Path area = new Path();
    private final RectF rect = new RectF();
    private Shader gradient;
    private float gradientTop = Float.NaN;
    private int textColor = 0xff8e8e93;
    private int lineColor = 0xffc8c7cc;
    private int knobColor = 0xffffffff;
    private Listener listener;
    private ValueAnimator animator;

    /** The band being dragged, or -1. */
    private int active = -1;
    /** Where on the knob the finger is, so grabbing it doesn't make it jump. */
    private float grab;

    public EqualizerView(Context context) {
        this(context, null);
    }

    public EqualizerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        maxDb = Equalizer.MAX_DB;
        gains = new float[Equalizer.BANDS];
        shown = new float[Equalizer.BANDS];
        text.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11, getResources().getDisplayMetrics()));
        setContentDescription("Equalizer");
    }

    public void setListener(Listener l) {
        listener = l;
    }

    /** Label text, grid lines and knob colors (they depend on the theme). */
    public void setColors(int text, int line, int knob) {
        textColor = text;
        lineColor = line;
        knobColor = knob;
        invalidate();
    }

    /** Shows a curve (dB per band); animate glides the knobs there, as when picking a preset. */
    public void setGains(float[] db, boolean animate) {
        if (animator != null) animator.cancel();
        final float[] from = shown.clone();
        for (int i = 0; i < gains.length; i++) gains[i] = db != null && i < db.length ? clamp(db[i]) : 0;
        if (!animate || getWindowToken() == null) {
            System.arraycopy(gains, 0, shown, 0, gains.length);
            invalidate();
            return;
        }
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(260);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                float t = (Float) a.getAnimatedValue();
                for (int i = 0; i < gains.length; i++) shown[i] = from[i] + (gains[i] - from[i]) * t;
                invalidate();
            }
        });
        animator.start();
    }

    public float[] getGains() {
        return gains.clone();
    }

    private float clamp(float db) {
        return Math.max(-maxDb, Math.min(maxDb, db));
    }

    // ---- Geometry ----

    private float left() {
        return 34 * density;
    }

    private float columnWidth() {
        return (getWidth() - left() - 8 * density) / gains.length;
    }

    private float columnX(int band) {
        return left() + columnWidth() * (band + 0.5f);
    }

    private float knobRadius() {
        return 9 * density;
    }

    private float top() {
        return 26 * density;
    }

    private float bottom() {
        return getHeight() - 24 * density - knobRadius();
    }

    private float zeroY() {
        return (top() + bottom()) / 2;
    }

    private float yFor(float db) {
        return zeroY() - db / maxDb * (bottom() - zeroY());
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY
                ? MeasureSpec.getSize(heightMeasureSpec) : Math.round(240 * density);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (getWidth() == 0) return;
        float topY = top();
        float bottomY = bottom();
        float zero = zeroY();
        if (gradient == null || gradientTop != topY) {
            gradient = new LinearGradient(0, topY, 0, bottomY, TOP, BOTTOM, Shader.TileMode.CLAMP);
            gradientTop = topY;
        }

        // Grid: 0 dB, and lighter lines at +-6 and +-12, with labels down the left.
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        float hair = Math.max(1, density * 0.5f);
        for (int db = -12; db <= 12; db += 6) {
            float y = yFor(db * maxDb / 12);
            paint.setColor(lineColor);
            paint.setAlpha(db == 0 ? 255 : 110);
            canvas.drawRect(left(), y - hair / 2, getWidth() - 8 * density, y + hair / 2, paint);
        }
        text.setColor(textColor);
        text.setTypeface(Typeface.DEFAULT);
        text.setTextAlign(Paint.Align.RIGHT);
        float lift = (text.descent() + text.ascent()) / 2;
        canvas.drawText("+" + Math.round(maxDb), left() - 6 * density, yFor(maxDb) - lift, text);
        canvas.drawText("0", left() - 6 * density, zero - lift, text);
        canvas.drawText("−" + Math.round(maxDb), left() - 6 * density, yFor(-maxDb) - lift, text);

        // Tracks, and band labels underneath.
        text.setTextAlign(Paint.Align.CENTER);
        float trackHalf = 1.5f * density;
        for (int i = 0; i < gains.length; i++) {
            float x = columnX(i);
            paint.setColor(lineColor);
            paint.setAlpha(255);
            rect.set(x - trackHalf, topY, x + trackHalf, bottomY);
            canvas.drawRoundRect(rect, trackHalf, trackHalf, paint);
            canvas.drawText(LABELS[i], x, getHeight() - 7 * density, text);
        }

        // The curve through the knobs (Catmull-Rom, as cubic Beziers), and the area to 0 dB.
        curve.reset();
        float[] xs = new float[gains.length];
        float[] ys = new float[gains.length];
        for (int i = 0; i < gains.length; i++) {
            xs[i] = columnX(i);
            ys[i] = yFor(shown[i]);
        }
        curve.moveTo(xs[0], ys[0]);
        for (int i = 0; i < gains.length - 1; i++) {
            int p = Math.max(0, i - 1);
            int n = Math.min(gains.length - 1, i + 2);
            curve.cubicTo(xs[i] + (xs[i + 1] - xs[p]) / 6, ys[i] + (ys[i + 1] - ys[p]) / 6,
                    xs[i + 1] - (xs[n] - xs[i]) / 6, ys[i + 1] - (ys[n] - ys[i]) / 6, xs[i + 1], ys[i + 1]);
        }
        area.set(curve);
        area.lineTo(xs[gains.length - 1], zero);
        area.lineTo(xs[0], zero);
        area.close();
        paint.setShader(gradient);
        paint.setAlpha(48);
        canvas.drawPath(area, paint);
        paint.setAlpha(255);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.5f * density);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        canvas.drawPath(curve, paint);
        paint.setStyle(Paint.Style.FILL);

        // Each band's fill from 0 dB to its knob.
        for (int i = 0; i < gains.length; i++) {
            rect.set(xs[i] - trackHalf, Math.min(zero, ys[i]), xs[i] + trackHalf, Math.max(zero, ys[i]));
            canvas.drawRoundRect(rect, trackHalf, trackHalf, paint);
        }
        paint.setShader(null);

        // Knobs, with a soft shadow; the one being dragged is bigger and shows its value.
        for (int i = 0; i < gains.length; i++) {
            float r = knobRadius() * (i == active ? 1.25f : 1f);
            paint.setColor(0x33000000);
            canvas.drawCircle(xs[i], ys[i] + density, r + density * 0.75f, paint);
            paint.setColor(knobColor);
            canvas.drawCircle(xs[i], ys[i], r, paint);
        }
        if (active >= 0) {
            text.setColor(BOTTOM);
            text.setTypeface(Typeface.DEFAULT_BOLD);
            float db = gains[active];
            String label = (db > 0 ? "+" : db < 0 ? "−" : "")
                    + String.format(Locale.US, db == Math.round(db) ? "%.0f" : "%.1f", Math.abs(db)) + " dB";
            float y = ys[active] - knobRadius() * 1.25f - 6 * density;
            if (y + text.ascent() < 0) y = ys[active] + knobRadius() * 1.25f + 6 * density - text.ascent();
            canvas.drawText(label, xs[active], y, text);
        }
    }

    // ---- Touch ----

    private int bandAt(float x) {
        int band = (int) Math.floor((x - left()) / columnWidth());
        return Math.max(0, Math.min(gains.length - 1, band));
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (!isEnabled()) return false;
                if (animator != null) animator.cancel();
                System.arraycopy(gains, 0, shown, 0, gains.length);
                active = bandAt(e.getX());
                float knobY = yFor(gains[active]);
                // On the knob: drag it from where it is. Elsewhere in the column: jump there.
                grab = Math.abs(e.getY() - knobY) < 28 * density ? e.getY() - knobY : 0;
                // Sliders go up and down, so the page mustn't scroll instead.
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                moveTo(e.getY());
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (active >= 0) moveTo(e.getY());
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                active = -1;
                invalidate();
                return true;
            default:
                return true;
        }
    }

    private void moveTo(float y) {
        float db = (zeroY() - (y - grab)) / (bottom() - zeroY()) * maxDb;
        db = clamp(Math.round(db * 2) / 2f);
        if (db == gains[active]) return;
        gains[active] = db;
        shown[active] = db;
        invalidate();
        if (listener != null) listener.onBandChanged(this, active, db);
    }
}
