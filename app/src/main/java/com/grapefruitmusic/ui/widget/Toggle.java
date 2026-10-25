package com.grapefruitmusic.ui.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.DecelerateInterpolator;
import android.widget.Checkable;

/**
 * Grapefruit's on/off switch. The track fills with the app icon's grapefruit gradient when on,
 * and the knob is a grapefruit slice that rolls across: plain white when off, pink inside when
 * on. Tap it or drag the knob. Drawn by hand, so it looks the same on every Android version.
 */
public class Toggle extends View implements Checkable {
    public interface Listener {
        /** The user switched it (not called for setChecked). */
        void onToggled(Toggle toggle, boolean on);
    }

    // The app icon's gradient.
    private static final int ON_TOP = 0xffff8c6b;
    private static final int ON_BOTTOM = 0xffec485c;
    private static final int KNOB = 0xffffffff;
    private static final int FLESH_ON = 0xffffd3c7;
    private static final int SEGMENTS_ON = 0xffffffff;
    private static final int SEGMENTS = 8;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float density;
    private final int touchSlop;
    private final float trackWidth;
    private final float trackHeight;
    private int offTrack = 0xffe3e3e8;
    private int offSegments = 0xffdcdce0;
    private Shader onShader;
    private float shaderTop = Float.NaN;

    private boolean checked;
    /** Knob position, 0 (off) to 1 (on); animated. */
    private float position;
    /** 0 to 1 while a finger is down; animated. */
    private float press;
    private ValueAnimator positionAnimator;
    private ValueAnimator pressAnimator;
    private Listener listener;

    private float downX;
    private float downPosition;
    private boolean dragging;

    public Toggle(Context context) {
        this(context, null);
    }

    public Toggle(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        trackWidth = 50 * density;
        trackHeight = 30 * density;
        int pad = Math.round(4 * density);
        setPadding(pad, pad, pad, pad);
        setClickable(true);
        setFocusable(true);
    }

    /** Colors of the track and the knob's segment lines when off (they depend on the theme). */
    public void setOffColors(int track, int segments) {
        offTrack = track;
        offSegments = segments;
        invalidate();
    }

    public void setListener(Listener l) {
        listener = l;
    }

    @Override
    public boolean isChecked() {
        return checked;
    }

    /** Sets the state without telling the listener; jumps there unless the toggle is on screen. */
    @Override
    public void setChecked(boolean on) {
        if (on == checked) return;
        checked = on;
        if (getWindowToken() != null && isShown()) {
            animatePosition(on ? 1f : 0f);
        } else {
            if (positionAnimator != null) positionAnimator.cancel();
            position = on ? 1f : 0f;
            invalidate();
        }
        refreshDrawableState();
    }

    @Override
    public void toggle() {
        setChecked(!checked);
    }

    @Override
    public boolean performClick() {
        toggle();
        boolean handled = super.performClick();
        if (listener != null) listener.onToggled(this, checked);
        return handled;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = Math.round(trackWidth) + getPaddingLeft() + getPaddingRight();
        int h = Math.round(trackHeight) + getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(resolveSize(w, widthSpec), resolveSize(h, heightSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float left = getPaddingLeft() + (getWidth() - getPaddingLeft() - getPaddingRight() - trackWidth) / 2f;
        float top = getPaddingTop() + (getHeight() - getPaddingTop() - getPaddingBottom() - trackHeight) / 2f;
        float r = trackHeight / 2f;
        rect.set(left, top, left + trackWidth, top + trackHeight);

        // Track: gray, with the grapefruit gradient fading in as the knob moves across.
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        paint.setColor(offTrack);
        canvas.drawRoundRect(rect, r, r, paint);
        if (position > 0f) {
            if (onShader == null || shaderTop != top) {
                onShader = new LinearGradient(0, top, 0, top + trackHeight, ON_TOP, ON_BOTTOM, Shader.TileMode.CLAMP);
                shaderTop = top;
            }
            paint.setShader(onShader);
            paint.setAlpha(Math.round(255 * position));
            canvas.drawRoundRect(rect, r, r, paint);
            paint.setShader(null);
            paint.setAlpha(255);
        }

        // Knob: a grapefruit slice, a little smaller while pressed.
        float knobR = (r - 3 * density) * (1f - 0.07f * press);
        float travel = trackWidth - trackHeight;
        float cx = left + r + travel * position;
        float cy = top + r;
        paint.setColor(0x26000000);
        canvas.drawCircle(cx, cy + 0.8f * density, knobR + 0.6f * density, paint);
        paint.setColor(KNOB);
        canvas.drawCircle(cx, cy, knobR, paint);
        float flesh = knobR * 0.72f;
        paint.setColor(blend(KNOB, FLESH_ON, position));
        canvas.drawCircle(cx, cy, flesh, paint);

        // Segments turn as the slice rolls along the track.
        double turn = travel * position / knobR;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(1.1f * density);
        paint.setColor(blend(offSegments, SEGMENTS_ON, position));
        float inner = flesh * 0.16f;
        float outer = flesh * 0.86f;
        for (int i = 0; i < SEGMENTS; i++) {
            double a = turn + i * 2 * Math.PI / SEGMENTS - Math.PI / 2;
            float dx = (float) Math.cos(a);
            float dy = (float) Math.sin(a);
            canvas.drawLine(cx + dx * inner, cy + dy * inner, cx + dx * outer, cy + dy * outer, paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!isEnabled()) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downPosition = position;
                dragging = false;
                setPressed(true);
                animatePress(1f);
                return true;
            case MotionEvent.ACTION_MOVE: {
                float dx = e.getX() - downX;
                if (!dragging && Math.abs(dx) > touchSlop) {
                    dragging = true;
                    if (positionAnimator != null) positionAnimator.cancel();
                    // Sideways: keep a scrolling page from taking the gesture.
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                }
                if (dragging) {
                    position = Math.max(0f, Math.min(1f, downPosition + dx / (trackWidth - trackHeight)));
                    invalidate();
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
                setPressed(false);
                animatePress(0f);
                if (dragging) {
                    boolean on = position >= 0.5f;
                    if (on != checked) {
                        performClick();
                    } else {
                        animatePosition(checked ? 1f : 0f);
                    }
                } else {
                    performClick();
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                setPressed(false);
                animatePress(0f);
                animatePosition(checked ? 1f : 0f);
                return true;
            default:
                return true;
        }
    }

    private void animatePosition(float to) {
        if (positionAnimator != null) positionAnimator.cancel();
        positionAnimator = ValueAnimator.ofFloat(position, to);
        positionAnimator.setDuration(Math.max(80, Math.round(240 * Math.abs(to - position))));
        positionAnimator.setInterpolator(new DecelerateInterpolator(1.4f));
        positionAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                position = (Float) a.getAnimatedValue();
                invalidate();
            }
        });
        positionAnimator.start();
    }

    private void animatePress(float to) {
        if (pressAnimator != null) pressAnimator.cancel();
        pressAnimator = ValueAnimator.ofFloat(press, to);
        pressAnimator.setDuration(120);
        pressAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                press = (Float) a.getAnimatedValue();
                invalidate();
            }
        });
        pressAnimator.start();
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        setAlpha(enabled ? 1f : 0.4f);
    }

    private static int blend(int from, int to, float t) {
        int a = Math.round(((from >>> 24) & 0xff) + (((to >>> 24) & 0xff) - ((from >>> 24) & 0xff)) * t);
        int r = Math.round(((from >> 16) & 0xff) + (((to >> 16) & 0xff) - ((from >> 16) & 0xff)) * t);
        int g = Math.round(((from >> 8) & 0xff) + (((to >> 8) & 0xff) - ((from >> 8) & 0xff)) * t);
        int b = Math.round((from & 0xff) + ((to & 0xff) - (from & 0xff)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    // Read out as a switch by TalkBack.

    @Override
    public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
        super.onInitializeAccessibilityEvent(event);
        event.setClassName("android.widget.Switch");
        event.setChecked(checked);
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.Switch");
        info.setCheckable(true);
        info.setChecked(checked);
    }
}
