package com.grapefruitmusic.ui.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Thin iOS-style slider for song position and volume. */
public class Scrubber extends View {
    public interface Listener {
        void onScrubStart();

        void onScrub(long value);

        void onScrubEnd(long value);

        default void onScrubCancel() { }
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF track = new RectF();
    private final float density;
    private long max = 1;
    private long value;
    private boolean dragging;
    private long dragStartValue;
    private int pointerId = -1;
    private float thumbRadius;
    private float dragThumbRadius;
    /** 0 at rest, 1 while touched; animated so the thumb grows and shrinks smoothly. */
    private float grow;
    private ValueAnimator growAnimator;
    private int trackColor = 0xffc8c7cc;
    private int fillColor = 0xff8e8e93;
    private int thumbColor = 0xff8e8e93;
    private boolean thumbShadow;
    private Listener listener;

    public Scrubber(Context context) {
        this(context, null);
    }

    public Scrubber(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        thumbRadius = 3.5f * density;
        dragThumbRadius = 9 * density;
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public void setColors(int track, int fill, int thumb) {
        trackColor = track;
        fillColor = fill;
        thumbColor = thumb;
        invalidate();
    }

    /** Thumb size in dp, at rest and while dragging. */
    public void setThumb(float restDp, float dragDp, boolean withShadow) {
        thumbRadius = restDp * density;
        dragThumbRadius = dragDp * density;
        thumbShadow = withShadow;
        if (withShadow) {
            setLayerType(LAYER_TYPE_SOFTWARE, null);
        }
        invalidate();
    }

    public void setMax(long max) {
        if (dragging) return;
        this.max = Math.max(1, max);
        value = Math.min(value, this.max);
        invalidate();
    }

    public void setValue(long v) {
        if (dragging) return;
        value = Math.max(0, Math.min(v, max));
        invalidate();
    }

    public boolean isDragging() {
        return dragging;
    }

    public long getMax() {
        return max;
    }

    public long getValue() {
        return value;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float pad = dragThumbRadius + density;
        float cy = getHeight() / 2f;
        float h = 1.5f * density;
        track.set(pad, cy - h, getWidth() - pad, cy + h);
        paint.clearShadowLayer();
        paint.setColor(trackColor);
        canvas.drawRoundRect(track, h, h, paint);
        float x = pad + (getWidth() - 2 * pad) * (value / (float) max);
        track.set(pad, cy - h, x, cy + h);
        paint.setColor(fillColor);
        canvas.drawRoundRect(track, h, h, paint);
        paint.setColor(thumbColor);
        if (thumbShadow) paint.setShadowLayer(2 * density, 0, density, 0x40000000);
        canvas.drawCircle(x, cy, thumbRadius + (dragThumbRadius - thumbRadius) * grow, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!isEnabled()) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                pointerId = e.getPointerId(0);
                dragStartValue = value;
                dragging = true;
                animateGrow(1f, 160);
                if (listener != null) listener.onScrubStart();
                track(e.getX());
                return true;
            case MotionEvent.ACTION_MOVE:
                int index = e.findPointerIndex(pointerId);
                if (dragging && index >= 0) track(e.getX(index));
                return true;
            case MotionEvent.ACTION_POINTER_UP:
                if (e.getPointerId(e.getActionIndex()) != pointerId) return true;
                // Losing the controlling finger cancels; another finger must not jump the thumb.
            case MotionEvent.ACTION_CANCEL:
                if (dragging) {
                    value = dragStartValue;
                    finishDrag();
                    if (listener != null) listener.onScrubCancel();
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (dragging) {
                    int upIndex = e.findPointerIndex(pointerId);
                    if (upIndex >= 0) track(e.getX(upIndex));
                    finishDrag();
                    performClick();
                    if (listener != null) listener.onScrubEnd(value);
                }
                return true;
            default:
                return super.onTouchEvent(e);
        }
    }

    private void finishDrag() {
        dragging = false;
        pointerId = -1;
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
        animateGrow(0f, 240);
        invalidate();
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private void animateGrow(float to, long duration) {
        if (growAnimator != null) growAnimator.cancel();
        growAnimator = ValueAnimator.ofFloat(grow, to);
        growAnimator.setDuration(duration);
        growAnimator.setInterpolator(new DecelerateInterpolator(1.5f));
        growAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                grow = (Float) a.getAnimatedValue();
                invalidate();
            }
        });
        growAnimator.start();
    }

    private void track(float x) {
        float pad = dragThumbRadius + density;
        float f = (x - pad) / Math.max(1f, getWidth() - 2 * pad);
        value = Math.round(Math.max(0f, Math.min(1f, f)) * max);
        invalidate();
        if (listener != null) listener.onScrub(value);
    }
}
