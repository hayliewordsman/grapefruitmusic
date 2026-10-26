package com.grapefruitmusic.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.OverScroller;

/**
 * A radio dial: a strip of tick marks, one per 0.1 MHz with the MHz numbered, sliding under a
 * fixed needle. Drag or fling it to tune; it settles on the nearest step.
 */
public class TuningDial extends View {
    public interface Listener {
        /** The needle passed a new frequency while the dial moves. */
        void onTuning(int freq);

        /** The dial came to rest on freq. */
        void onTuned(int freq);
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF needle = new RectF();
    private final OverScroller scroller;
    private final float density;
    private final int touchSlop;
    private final int minFling;
    private final int maxFling;
    private int min = 8750;
    private int max = 10800;
    private int step = 10;
    /** Pixels between neighbouring steps. */
    private float spacing;
    /** How far the dial is turned: pixels from min to the needle. */
    private float position;
    private int tickColor = 0xff8e8e93;
    private int needleColor = 0xffe83850;
    private Listener listener;
    private VelocityTracker velocity;
    private float downX;
    private float lastX;
    private boolean dragging;
    /** Moving by itself (a fling or the settle onto a step). */
    private boolean moving;
    private boolean settling;
    private int reported = -1;

    public TuningDial(Context context) {
        this(context, null);
    }

    public TuningDial(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        spacing = 10 * density;
        scroller = new OverScroller(context);
        ViewConfiguration vc = ViewConfiguration.get(context);
        touchSlop = vc.getScaledTouchSlop();
        minFling = vc.getScaledMinimumFlingVelocity();
        maxFling = vc.getScaledMaximumFlingVelocity();
        setFocusable(true);
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public void setColors(int ticks, int needle) {
        tickColor = ticks;
        needleColor = needle;
        invalidate();
    }

    /** The band, in units of 10 kHz, and the step between ticks. */
    public void setRange(int min, int max, int step) {
        this.min = min;
        this.max = max;
        this.step = step;
        invalidate();
    }

    /** Shows freq under the needle (ignored while the dial is being turned). */
    public void setFrequency(int freq) {
        if (dragging || moving) return;
        position = positionOf(freq);
        reported = freq;
        setContentDescription("Tuning dial, " + (freq / 100) + "." + ((freq / 10) % 10) + " FM");
        invalidate();
    }

    public int getFrequency() {
        return frequencyAt(position);
    }

    public boolean isTurning() {
        return dragging || moving;
    }

    private float positionOf(int freq) {
        return (Math.max(min, Math.min(max, freq)) - min) / (float) step * spacing;
    }

    private int frequencyAt(float pos) {
        int steps = Math.round(pos / spacing);
        return Math.max(min, Math.min(max, min + steps * step));
    }

    private float maxPosition() {
        return (max - min) / (float) step * spacing;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cx = getWidth() / 2f;
        float h = getHeight();
        float baseline = h * 0.62f;
        int first = (int) Math.floor((position - cx) / spacing) - 1;
        int last = (int) Math.ceil((position + cx) / spacing) + 1;
        int steps = (max - min) / step;
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(12 * density);
        for (int i = Math.max(0, first); i <= Math.min(steps, last); i++) {
            float x = cx + i * spacing - position;
            // Fade toward the ends, like a drum turning away.
            float fade = 1f - Math.min(1f, Math.abs(x - cx) / Math.max(1f, cx));
            int alpha = (int) (255 * (0.15f + 0.85f * fade * fade));
            int freq = min + i * step;
            boolean mhz = freq % 100 == 0;
            boolean half = freq % 50 == 0;
            float len = mhz ? h * 0.32f : half ? h * 0.22f : h * 0.13f;
            paint.setColor(withAlpha(tickColor, alpha));
            paint.setStrokeWidth((mhz ? 1.6f : 1f) * density);
            canvas.drawLine(x, baseline - len, x, baseline, paint);
            if (mhz) {
                paint.setStrokeWidth(0);
                canvas.drawText(String.valueOf(freq / 100), x, baseline + 15 * density, paint);
            }
        }
        float w = 1.5f * density;
        needle.set(cx - w, h * 0.12f, cx + w, baseline + 2 * density);
        paint.setColor(needleColor);
        canvas.drawRoundRect(needle, w, w, paint);
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00ffffff) | ((((color >>> 24) * alpha) / 255) << 24);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!isEnabled()) return false;
        if (velocity == null) velocity = VelocityTracker.obtain();
        velocity.addMovement(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                // Keep the Now Playing card's swipe-to-close out of it.
                getParent().requestDisallowInterceptTouchEvent(true);
                scroller.forceFinished(true);
                moving = false;
                settling = false;
                downX = e.getX();
                lastX = e.getX();
                dragging = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!dragging && Math.abs(e.getX() - downX) > touchSlop) {
                    dragging = true;
                    lastX = e.getX();
                }
                if (dragging) {
                    moveTo(position - (e.getX() - lastX));
                    lastX = e.getX();
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                velocity.computeCurrentVelocity(1000, maxFling);
                float v = velocity.getXVelocity();
                velocity.recycle();
                velocity = null;
                boolean wasDragging = dragging;
                dragging = false;
                if (wasDragging && Math.abs(v) > minFling) {
                    moving = true;
                    scroller.fling(Math.round(position), 0, -Math.round(v), 0, 0, Math.round(maxPosition()), 0, 0);
                    postInvalidateOnAnimation();
                } else if (!wasDragging && e.getActionMasked() == MotionEvent.ACTION_UP) {
                    // A tap beside the needle nudges one step that way.
                    float cx = getWidth() / 2f;
                    moveTo(positionOf(frequencyAt(position) + (e.getX() < cx ? -step : step)));
                    settle();
                } else {
                    settle();
                }
                return true;
            default:
                return true;
        }
    }

    private void moveTo(float pos) {
        position = Math.max(0, Math.min(maxPosition(), pos));
        int f = frequencyAt(position);
        if (f != reported) {
            reported = f;
            if (listener != null) listener.onTuning(f);
        }
        invalidate();
    }

    /** Glides onto the nearest step, then reports where the dial came to rest. */
    private void settle() {
        float target = positionOf(frequencyAt(position));
        int dx = Math.round(target - position);
        if (Math.abs(dx) < 1) {
            position = target;
            moving = false;
            settling = false;
            invalidate();
            int f = frequencyAt(position);
            reported = f;
            setContentDescription("Tuning dial, " + (f / 100) + "." + ((f / 10) % 10) + " FM");
            if (listener != null) listener.onTuned(f);
            return;
        }
        moving = true;
        settling = true;
        scroller.startScroll(Math.round(position), 0, dx, 0, 160);
        postInvalidateOnAnimation();
    }

    @Override
    public void computeScroll() {
        if (!moving) return;
        if (scroller.computeScrollOffset()) {
            moveTo(scroller.getCurrX());
            postInvalidateOnAnimation();
            return;
        }
        if (settling) {
            position = scroller.getFinalX();
            settling = false;
            moving = false;
            settle();
        } else {
            settle();
        }
    }
}
