package com.grapefruitmusic.ui.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.AbsListView;
import android.widget.FrameLayout;

/**
 * The iOS 10 Now Playing card: rounded top corners over a blurred copy of the artwork, and a
 * pull-down gesture to dismiss it.
 */
public class NowPlayingCard extends FrameLayout {
    public interface DragListener {
        /** offset >= 0, how far the card has been pulled down. */
        void onDrag(float offset);

        /** Released: dismiss or spring back. */
        void onRelease(boolean dismiss);
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint scrimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path shape = new Path();
    private final RectF rect = new RectF();
    private final Matrix matrix = new Matrix();
    private final float radius;
    private final int touchSlop;
    private Bitmap background;
    private BitmapShader shader;
    private int baseColor = 0xffffffff;
    private int scrimColor = 0xe6ffffff;

    private DragListener dragListener;
    private View dragLimit;
    private AbsListView scrollingChild;
    private float downX;
    private float downY;
    private float dragStartRawY;
    private boolean dragging;
    private VelocityTracker velocity;

    public NowPlayingCard(Context context) {
        this(context, null);
    }

    public NowPlayingCard(Context context, AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
        radius = 12 * getResources().getDisplayMetrics().density;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public void setColors(int base, int scrim) {
        baseColor = base;
        scrimColor = scrim;
        invalidate();
    }

    public void setBlurredArtwork(Bitmap blurred) {
        background = blurred;
        shader = blurred == null ? null : new BitmapShader(blurred, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        invalidate();
    }

    /** Drags must start above this view (e.g. the scrubber). */
    public void setDragLimit(View limit) {
        dragLimit = limit;
    }

    /** A visible list that should scroll instead of dragging the card, unless it's at the top. */
    public void setScrollingChild(AbsListView list) {
        scrollingChild = list;
    }

    public void setDragListener(DragListener l) {
        dragListener = l;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        shape.reset();
        rect.set(0, 0, w, h);
        shape.addRoundRect(rect, new float[]{radius, radius, radius, radius, 0, 0, 0, 0}, Path.Direction.CW);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        paint.setShader(null);
        paint.setColor(baseColor);
        canvas.drawPath(shape, paint);
        if (shader != null) {
            float scale = Math.max(getWidth() / (float) background.getWidth(),
                    getHeight() / (float) background.getHeight());
            matrix.setScale(scale, scale);
            matrix.postTranslate((getWidth() - background.getWidth() * scale) / 2f,
                    (getHeight() - background.getHeight() * scale) / 2f);
            shader.setLocalMatrix(matrix);
            paint.setShader(shader);
            canvas.drawPath(shape, paint);
            paint.setShader(null);
        }
        scrimPaint.setColor(scrimColor);
        canvas.drawPath(shape, scrimPaint);
    }

    private boolean startsInDragArea(float y) {
        if (dragLimit == null) return true;
        int[] me = new int[2];
        int[] limit = new int[2];
        getLocationOnScreen(me);
        dragLimit.getLocationOnScreen(limit);
        return y < limit[1] - me[1];
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downY = e.getY();
                dragging = false;
                break;
            case MotionEvent.ACTION_MOVE:
                float dy = e.getY() - downY;
                float dx = e.getX() - downX;
                boolean listScrolls = scrollingChild != null && scrollingChild.getVisibility() == VISIBLE
                        && scrollingChild.canScrollVertically(-1);
                if (dy > touchSlop && dy > Math.abs(dx) * 1.5f && startsInDragArea(downY) && !listScrolls) {
                    dragging = true;
                    dragStartRawY = e.getRawY();
                    return true;
                }
                break;
            default:
                break;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!dragging) return true; // The card swallows taps that miss its controls.
        // The card moves while dragging, so track screen coordinates.
        if (velocity == null) velocity = VelocityTracker.obtain();
        MotionEvent screen = MotionEvent.obtain(e);
        screen.setLocation(e.getRawX(), e.getRawY());
        velocity.addMovement(screen);
        screen.recycle();
        float offset = Math.max(0, e.getRawY() - dragStartRawY);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                if (dragListener != null) dragListener.onDrag(offset);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                velocity.computeCurrentVelocity(1000);
                float v = velocity.getYVelocity();
                velocity.recycle();
                velocity = null;
                dragging = false;
                float fling = 1000 * getResources().getDisplayMetrics().density;
                if (dragListener != null) dragListener.onRelease(offset > getHeight() * 0.25f || v > fling);
                return true;
            default:
                return true;
        }
    }
}
