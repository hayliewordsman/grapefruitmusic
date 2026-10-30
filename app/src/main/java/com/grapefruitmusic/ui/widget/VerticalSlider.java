package com.grapefruitmusic.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * A wide upright slider like iOS Control Center's: a rounded pill that fills from the bottom,
 * with an icon at its foot and the value as a percentage above it. Slide up for more.
 */
public class VerticalSlider extends View {
    public interface Listener {
        /** value 0 to 1, while the finger moves. */
        void onSlide(float value);

        /** The finger lifted at value. */
        void onSlideEnd(float value);
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF pill = new RectF();
    private final RectF fill = new RectF();
    private final RectF iconBounds = new RectF();
    private final Path clip = new Path();
    private final float density;
    private float value;
    private int icon;
    private int trackColor = 0x33808080;
    private int fillColor = 0xffe83850;
    private int labelColor = 0xff8e8e93;
    private int iconOnFill = 0xffffffff;
    private boolean sliding;
    private Listener listener;

    public VerticalSlider(Context context) {
        this(context, null);
    }

    public VerticalSlider(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        setFocusable(true);
        // clipPath needs a software layer before Android 4.3.
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public void setIcon(int icon) {
        this.icon = icon;
        invalidate();
    }

    public void setColors(int track, int fill, int label) {
        trackColor = track;
        fillColor = fill;
        labelColor = label;
        invalidate();
    }

    public void setValue(float v) {
        if (sliding) return;
        value = Math.max(0, Math.min(1, v));
        invalidate();
    }

    public float getValue() {
        return value;
    }

    public boolean isSliding() {
        return sliding;
    }

    private float labelHeight() {
        return 22 * density;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        // Fit short landscape screens, while keeping the upright control compact.
        int height = Math.min(MeasureSpec.getSize(heightSpec), Math.round(240 * density));
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth() - getPaddingLeft() - getPaddingRight();
        float left = getPaddingLeft();
        float top = getPaddingTop() + labelHeight();
        float bottom = getHeight() - getPaddingBottom();
        pill.set(left, top, left + w, bottom);
        float r = Math.min(w / 2.6f, 14 * density);

        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(13 * density);
        paint.setColor(sliding ? fillColor : labelColor);
        paint.setFakeBoldText(true);
        canvas.drawText(Math.round(value * 100) + "%", pill.centerX(), getPaddingTop() + 15 * density, paint);
        paint.setFakeBoldText(false);

        paint.setColor(trackColor);
        canvas.drawRoundRect(pill, r, r, paint);
        canvas.save();
        clip.reset();
        clip.addRoundRect(pill, r, r, Path.Direction.CW);
        canvas.clipPath(clip);
        fill.set(pill.left, pill.bottom - pill.height() * value, pill.right, pill.bottom);
        paint.setColor(fillColor);
        canvas.drawRect(fill, paint);
        canvas.restore();

        if (icon != 0) {
            float size = Math.min(w * 0.55f, 24 * density);
            float cy = pill.bottom - w / 2f;
            iconBounds.set(pill.centerX() - size / 2f, cy - size / 2f, pill.centerX() + size / 2f, cy + size / 2f);
            boolean covered = fill.top < iconBounds.centerY();
            Icons.draw(canvas, icon, iconBounds, covered ? iconOnFill : labelColor);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!isEnabled()) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                // Up and down here mustn't pull the Now Playing card closed.
                getParent().requestDisallowInterceptTouchEvent(true);
                sliding = true;
                track(e.getY());
                return true;
            case MotionEvent.ACTION_MOVE:
                track(e.getY());
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                track(e.getY());
                sliding = false;
                invalidate();
                if (listener != null) listener.onSlideEnd(value);
                return true;
            default:
                return super.onTouchEvent(e);
        }
    }

    private void track(float y) {
        float top = getPaddingTop() + labelHeight();
        float height = Math.max(1f, getHeight() - getPaddingBottom() - top);
        value = Math.max(0f, Math.min(1f, 1f - (y - top) / height));
        // Whole percents, like the label.
        value = Math.round(value * 100) / 100f;
        invalidate();
        if (listener != null) listener.onSlide(value);
    }
}
