package com.grapefruitmusic.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

/**
 * One line of text that, when too long for its width, scrolls by: a pause at the start, then the
 * whole line passes once, followed by its start again after a gap, a few times, then it rests at
 * the start. Like a TextView's marquee, but its own, because a TextView starts its marquee over
 * from the first letter whenever it is measured again, and on Android 4 it remeasures itself as
 * the marquee starts (swapping its "…" line for the scrolling one): the mini player's title would
 * move a letter and jump back. Here only a new text, a new width or setScrolls start it over.
 */
public final class MarqueeText extends View {
    /** Android's own marquee timings and speed. */
    private static final long PAUSE_MS = 1200;
    private static final float DP_PER_SECOND = 30f;
    /** About 30 frames a second while it moves, as Android 4.2's marquee: little work for old phones. */
    private static final long FRAME_MS = 33;

    private final TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private String text = "";
    private float textWidth;
    private int repeats = 3;
    private boolean scrolls = true;
    /** When the current round began (its pause first), and how many rounds have run. */
    private long roundStart;
    private int round;
    /** How far the line has moved, in px, as last drawn. */
    private float offset;

    public MarqueeText(Context context) {
        super(context);
        init();
    }

    public MarqueeText(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint.density = getResources().getDisplayMetrics().density;
        setHorizontalFadingEdgeEnabled(true);
        setFadingEdgeLength(Math.round(16 * getResources().getDisplayMetrics().density));
        restart();
    }

    /** Sets the text; the same text again leaves it scrolling where it is. */
    public void setText(CharSequence value) {
        String s = value == null ? "" : value.toString();
        if (s.equals(text)) return;
        text = s;
        textWidth = paint.measureText(text);
        setContentDescription(text);
        restart();
    }

    public CharSequence getText() {
        return text;
    }

    public void setTextSize(float sp) {
        paint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, getResources().getDisplayMetrics()));
        textWidth = paint.measureText(text);
        requestLayout();
        restart();
    }

    public void setTextColor(int color) {
        paint.setColor(color);
        invalidate();
    }

    public void setTypeface(Typeface typeface) {
        paint.setTypeface(typeface);
        textWidth = paint.measureText(text);
        restart();
    }

    /** How many times a long line passes by before it rests (Android's marqueeRepeatLimit). */
    public void setRepeats(int times) {
        repeats = Math.max(0, times);
        restart();
    }

    /** Whether a long line scrolls (off while it can't be seen): back to its start either way. */
    public void setScrolls(boolean on) {
        if (on == scrolls) return;
        scrolls = on;
        restart();
    }

    private void restart() {
        roundStart = SystemClock.uptimeMillis();
        round = 0;
        offset = 0;
        invalidate();
    }

    private int room() {
        return getWidth() - getPaddingLeft() - getPaddingRight();
    }

    private boolean overflows() {
        return room() > 0 && textWidth > room();
    }

    /** The gap after the line before its start comes round again: a third of the width, as Android's. */
    private float gap() {
        return room() / 3f;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        // As tall as a TextView's line (with its font padding), so it sits where one did.
        Paint.FontMetricsInt fm = paint.getFontMetricsInt();
        int height = fm.bottom - fm.top + getPaddingTop() + getPaddingBottom();
        int width = MeasureSpec.getMode(widthSpec) == MeasureSpec.UNSPECIFIED
                ? (int) Math.ceil(textWidth) + getPaddingLeft() + getPaddingRight()
                : MeasureSpec.getSize(widthSpec);
        setMeasuredDimension(width, resolveSize(height, heightSpec));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w != oldw) restart();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE) restart();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        restart();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int left = getPaddingLeft();
        Paint.FontMetricsInt fm = paint.getFontMetricsInt();
        float baseline = getPaddingTop() - fm.top;
        boolean moving = scrolls && overflows() && round < repeats;
        offset = 0;
        long next = -1;
        if (moving) {
            float distance = textWidth + gap();
            float pxPerMs = DP_PER_SECOND * getResources().getDisplayMetrics().density / 1000f;
            long travel = (long) Math.ceil(distance / pxPerMs);
            long now = SystemClock.uptimeMillis();
            long at = now - roundStart;
            // A frame can come late (or the view was off screen a while): skip whole rounds.
            while (at >= PAUSE_MS + travel && round < repeats) {
                round++;
                roundStart += PAUSE_MS + travel;
                at = now - roundStart;
            }
            if (round < repeats) {
                if (at < PAUSE_MS) {
                    next = PAUSE_MS - at;
                } else {
                    offset = Math.min(distance, (at - PAUSE_MS) * pxPerMs);
                    next = FRAME_MS;
                }
            }
        }
        canvas.save();
        canvas.clipRect(left, 0, getWidth() - getPaddingRight(), getHeight());
        canvas.drawText(text, left - offset, baseline, paint);
        if (offset > 0) canvas.drawText(text, left - offset + textWidth + gap(), baseline, paint);
        canvas.restore();
        if (next >= 0) postInvalidateDelayed(next);
    }

    @Override
    protected float getLeftFadingEdgeStrength() {
        return offset > 0 ? 1f : 0f;
    }

    @Override
    protected float getRightFadingEdgeStrength() {
        return overflows() ? 1f : 0f;
    }

    /** Where the line is, in px moved (tests). */
    float offset() {
        return offset;
    }
}
