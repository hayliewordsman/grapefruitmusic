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
 * pull-down gesture to dismiss it. In landscape it's a panel at the right instead, rounded all
 * round and swiped right to dismiss.
 */
public class NowPlayingCard extends FrameLayout {
    public interface DragListener {
        /** offset >= 0, how far the card has been pulled down (right, at the side). */
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
    private boolean side;
    private float downX;
    private float downY;
    private float dragStartRaw;
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

    /** The landscape panel at the right (true), or the card from the bottom. */
    public void setSide(boolean side) {
        this.side = side;
        buildShape(getWidth(), getHeight());
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        buildShape(w, h);
    }

    private void buildShape(int w, int h) {
        shape.reset();
        rect.set(0, 0, w, h);
        float bottom = side ? radius : 0;
        shape.addRoundRect(rect, new float[]{radius, radius, radius, radius, bottom, bottom, bottom, bottom},
                Path.Direction.CW);
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

    /**
     * A pull down (right, at the side) that started above the drag limit, and isn't a list
     * scrolling back up.
     */
    private boolean isPullAway(MotionEvent e) {
        float along = side ? e.getX() - downX : e.getY() - downY;
        float across = side ? e.getY() - downY : e.getX() - downX;
        boolean listScrolls = !side && scrollingChild != null && scrollingChild.getVisibility() == VISIBLE
                && scrollingChild.canScrollVertically(-1);
        return along > touchSlop && along > Math.abs(across) * 1.5f && startsInDragArea(downY) && !listScrolls;
    }

    private void startDrag(MotionEvent e) {
        dragging = true;
        dragStartRaw = side ? e.getRawX() : e.getRawY();
        if (velocity != null) velocity.clear();
    }

    /** Pulls that start on a control (the grabber, a button) are taken over here. */
    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downY = e.getY();
                dragging = false;
                break;
            case MotionEvent.ACTION_MOVE:
                if (!dragging && isPullAway(e)) {
                    startDrag(e);
                    return true;
                }
                break;
            default:
                break;
        }
        return false;
    }

    /**
     * Pulls that start on something that isn't a control (artwork, empty space around the
     * grabber) arrive here directly, without onInterceptTouchEvent, so detect them here too.
     */
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            downX = e.getX();
            downY = e.getY();
            dragging = false;
            return true; // The card swallows touches that miss its controls.
        }
        if (!dragging) {
            if (action == MotionEvent.ACTION_MOVE && isPullAway(e)) startDrag(e);
            else return true;
        }
        // The card moves while dragging, so track screen coordinates.
        if (velocity == null) velocity = VelocityTracker.obtain();
        MotionEvent screen = MotionEvent.obtain(e);
        screen.setLocation(e.getRawX(), e.getRawY());
        velocity.addMovement(screen);
        screen.recycle();
        float offset = Math.max(0, (side ? e.getRawX() : e.getRawY()) - dragStartRaw);
        switch (action) {
            case MotionEvent.ACTION_MOVE:
                if (dragListener != null) dragListener.onDrag(offset);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                velocity.computeCurrentVelocity(1000);
                float v = side ? velocity.getXVelocity() : velocity.getYVelocity();
                velocity.recycle();
                velocity = null;
                dragging = false;
                float fling = 1000 * getResources().getDisplayMetrics().density;
                if (dragListener != null) {
                    dragListener.onRelease(action == MotionEvent.ACTION_UP
                            && (offset > (side ? getWidth() : getHeight()) * 0.25f || v > fling));
                }
                return true;
            default:
                return true;
        }
    }
}
