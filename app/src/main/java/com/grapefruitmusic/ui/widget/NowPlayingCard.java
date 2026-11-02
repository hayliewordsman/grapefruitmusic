package com.grapefruitmusic.ui.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
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
 * Now Playing: a page over everything, its background a gradient from the artwork's colour (at the
 * top) to the theme's, pulled down to dismiss. In landscape it's a panel at the right instead,
 * rounded all round and swiped right to dismiss.
 */
public class NowPlayingCard extends FrameLayout {
    public interface DragListener {
        /** offset >= 0, how far the card has been pulled down (right, at the side). */
        void onDrag(float offset);

        /** Released: dismiss or spring back. */
        void onRelease(boolean dismiss);
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Path shape = new Path();
    private final RectF rect = new RectF();
    private final float radius;
    private final int touchSlop;
    private int baseColor = 0xffffffff;
    private int scrimColor = 0xe6ffffff;

    private DragListener dragListener;
    private View dragLimit;
    private View dragHandle;
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
        radius = 20 * getResources().getDisplayMetrics().density;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    private int tint;
    private boolean darkTheme;
    private android.graphics.LinearGradient gradient;
    private int gradientHeight;

    /**
     * The artwork's colour (0 for none), for the background's gradient. Stronger on dark themes;
     * light themes only take a wash of it, so dark text stays easy to read.
     */
    public void setTint(int color) {
        if (color == tint) return;
        tint = color;
        gradient = null;
        invalidate();
    }

    /** The artwork's colour: the average of a small copy, livelier and kept to a middle shade. */
    public static int colourOf(Bitmap b) {
        if (b == null || b.getWidth() <= 0 || b.getHeight() <= 0) return 0;
        Bitmap small;
        try {
            small = Bitmap.createScaledBitmap(b, 12, 12, true);
        } catch (RuntimeException | OutOfMemoryError e) {
            return 0;
        }
        long r = 0, g = 0, bl = 0;
        int n = 0;
        for (int y = 0; y < small.getHeight(); y++) {
            for (int x = 0; x < small.getWidth(); x++) {
                int px = small.getPixel(x, y);
                r += (px >> 16) & 0xff;
                g += (px >> 8) & 0xff;
                bl += px & 0xff;
                n++;
            }
        }
        if (small != b) small.recycle();
        if (n == 0) return 0;
        float[] hsv = new float[3];
        android.graphics.Color.RGBToHSV((int) (r / n), (int) (g / n), (int) (bl / n), hsv);
        hsv[1] = Math.min(1f, hsv[1] * 1.35f + 0.08f);
        hsv[2] = Math.max(0.35f, Math.min(0.75f, hsv[2]));
        return android.graphics.Color.HSVToColor(hsv);
    }

    private static int mix(int a, int b, float t) {
        int ar = (a >> 16) & 0xff, ag = (a >> 8) & 0xff, ab = a & 0xff;
        int br = (b >> 16) & 0xff, bg = (b >> 8) & 0xff, bb = b & 0xff;
        return 0xff000000 | (Math.round(ar + (br - ar) * t) << 16) | (Math.round(ag + (bg - ag) * t) << 8)
                | Math.round(ab + (bb - ab) * t);
    }

    public void setColors(int base, int scrim) {
        darkTheme = ((base >> 16 & 0xff) * 299 + (base >> 8 & 0xff) * 587 + (base & 0xff) * 114) / 1000 < 128;
        gradient = null;
        baseColor = base;
        scrimColor = scrim;
        invalidate();
    }


    /** Drags must start above this view (e.g. the scrubber). */
    public void setDragLimit(View limit) {
        dragLimit = limit;
    }

    /** A child of the card that drags start on wherever it is (the landscape grab bar). */
    public void setDragHandle(View handle) {
        dragHandle = handle;
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
        // Full screen in portrait (square corners); the panel at the side is rounded all round.
        float r = side ? radius : 0;
        shape.addRoundRect(rect, new float[]{r, r, r, r, r, r, r, r}, Path.Direction.CW);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        paint.setShader(null);
        paint.setColor(baseColor);
        canvas.drawPath(shape, paint);
        if (tint != 0) {
            if (gradient == null || gradientHeight != getHeight()) {
                gradientHeight = getHeight();
                int top = mix(baseColor, tint, darkTheme ? 0.55f : 0.30f);
                int middle = mix(baseColor, tint, darkTheme ? 0.22f : 0.10f);
                gradient = new android.graphics.LinearGradient(0, 0, 0, gradientHeight,
                        new int[]{top, middle, baseColor}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
            }
            paint.setShader(gradient);
            canvas.drawPath(shape, paint);
            paint.setShader(null);
        }
    }

    private boolean startsInDragArea(float x, float y) {
        if (dragHandle != null && dragHandle.getVisibility() == VISIBLE && x >= dragHandle.getLeft()
                && x < dragHandle.getRight() && y >= dragHandle.getTop() && y < dragHandle.getBottom()) {
            return true;
        }
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
        return along > touchSlop && along > Math.abs(across) * 1.5f && startsInDragArea(downX, downY) && !listScrolls;
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
