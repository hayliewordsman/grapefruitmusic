package com.grapefruitmusic.ui.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.ListView;

/**
 * A list with Grapefruit's own fast-scroll thumb (instead of Android's, which looks different on
 * every version): a grapefruit pill on the right edge that shows while a long list scrolls. Drag
 * it to jump through the list; a bubble shows the letter you're at.
 */
public class FastScrollListView extends ListView {
    /** The bubble's text for a list position (headers included), or null for none. */
    public interface Labeler {
        String label(int position);
    }

    private static final long HIDE_DELAY = 1300;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float density;
    private final float thumbHeight;
    private final float touchWidth;
    private int color = 0xfff2545b;
    private Labeler labeler;
    /** 0 (hidden) to 1; animated. */
    private float shown;
    private ValueAnimator fade;
    private int lastOffset = -1;
    private boolean dragging;
    private float grabOffset;
    private float dragFraction;
    private String label;

    private final Runnable hide = new Runnable() {
        @Override
        public void run() {
            if (!dragging) fadeTo(0f);
        }
    };

    public FastScrollListView(Context context) {
        this(context, null);
    }

    public FastScrollListView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        thumbHeight = 46 * density;
        touchWidth = 40 * density;
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextSize(28 * getResources().getDisplayMetrics().scaledDensity);
    }

    public void setThumbColor(int color) {
        this.color = color;
        invalidate();
    }

    public void setLabeler(Labeler labeler) {
        this.labeler = labeler;
    }

    /** Only long lists (several screens) get the thumb. */
    private boolean isLong() {
        return getChildCount() > 0 && getCount() > getChildCount() * 3;
    }

    private float usableHeight() {
        return getHeight() - getPaddingTop() - getPaddingBottom() - thumbHeight;
    }

    private float thumbTop() {
        float fraction;
        if (dragging) {
            fraction = dragFraction;
        } else {
            int range = computeVerticalScrollRange() - computeVerticalScrollExtent();
            fraction = range > 0 ? Math.min(1f, computeVerticalScrollOffset() / (float) range) : 0f;
        }
        return getPaddingTop() + fraction * usableHeight();
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        // ListView also calls this after every layout; only a real scroll shows the thumb.
        int offset = computeVerticalScrollOffset();
        if (offset == lastOffset) return;
        boolean first = lastOffset < 0;
        lastOffset = offset;
        if (first || dragging || !isLong()) return;
        fadeTo(1f);
        removeCallbacks(hide);
        postDelayed(hide, HIDE_DELAY);
    }

    private void fadeTo(float to) {
        if (fade != null) fade.cancel();
        if (shown == to) return;
        fade = ValueAnimator.ofFloat(shown, to);
        fade.setDuration(to > shown ? 120 : 300);
        fade.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                shown = (Float) a.getAnimatedValue();
                invalidate();
            }
        });
        fade.start();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (shown <= 0f) return;
        float width = (dragging ? 8 : 5) * density;
        float right = getWidth() - 3 * density;
        float top = thumbTop();
        rect.set(right - width, top, right, top + thumbHeight);
        paint.setColor(color);
        paint.setAlpha(Math.round(230 * shown));
        canvas.drawRoundRect(rect, width / 2, width / 2, paint);
        if (dragging && label != null) {
            // The letter bubble, level with the thumb.
            float r = 32 * density;
            float cx = right - touchWidth - r;
            float cy = Math.max(getPaddingTop() + r, Math.min(getHeight() - getPaddingBottom() - r,
                    top + thumbHeight / 2));
            paint.setAlpha(240);
            canvas.drawCircle(cx, cy, r, paint);
            paint.setColor(0xffffffff);
            canvas.drawText(label, cx, cy - (paint.descent() + paint.ascent()) / 2, paint);
        }
    }

    private boolean hitsThumb(float x, float y) {
        if (shown < 0.5f || !isLong() || x < getWidth() - touchWidth) return false;
        float top = thumbTop();
        float slop = 14 * density;
        return y >= top - slop && y <= top + thumbHeight + slop;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN && hitsThumb(e.getX(), e.getY())) {
            startDrag(e.getY());
            return true;
        }
        return super.onInterceptTouchEvent(e);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!dragging && e.getActionMasked() == MotionEvent.ACTION_DOWN && hitsThumb(e.getX(), e.getY())) {
            startDrag(e.getY());
            return true;
        }
        if (!dragging) return super.onTouchEvent(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                dragTo(e.getY());
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                label = null;
                lastOffset = computeVerticalScrollOffset();
                removeCallbacks(hide);
                postDelayed(hide, HIDE_DELAY);
                invalidate();
                return true;
            default:
                return true;
        }
    }

    private void startDrag(float y) {
        float top = thumbTop();
        dragFraction = usableHeight() > 0 ? (top - getPaddingTop()) / usableHeight() : 0f;
        grabOffset = y - top;
        dragging = true;
        removeCallbacks(hide);
        fadeTo(1f);
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
        dragTo(y);
    }

    private void dragTo(float y) {
        float usable = usableHeight();
        dragFraction = usable > 0 ? Math.max(0f, Math.min(1f, (y - grabOffset - getPaddingTop()) / usable)) : 0f;
        int count = getCount();
        if (count == 0) return;
        int position = Math.round(dragFraction * (count - 1));
        setSelectionFromTop(position, 0);
        label = labeler != null ? labeler.label(position) : null;
        invalidate();
    }
}
