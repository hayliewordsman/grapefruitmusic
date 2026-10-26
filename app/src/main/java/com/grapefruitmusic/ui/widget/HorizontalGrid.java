package com.grapefruitmusic.ui.widget;

import android.content.Context;
import android.database.DataSetObserver;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.OverScroller;

import java.util.ArrayList;
import java.util.List;

/**
 * A sideways-scrolling strip of columns from an adapter (landscape library pages): an optional
 * leading view (the Library menu) and a title over the first columns, then one column per
 * adapter item, all scrolling together. Only the columns on screen exist; the rest are recycled.
 *
 * <p>Column width follows the height: each column holds rows cards with a square picture and
 * textHeight of text under it.
 */
public class HorizontalGrid extends ViewGroup {
    private final float density;
    private final int touchSlop;
    private final int minFling;
    private final int maxFling;
    private final OverScroller scroller;
    private final SparseArray<View> columns = new SparseArray<View>();
    private final SparseArray<List<View>> scrap = new SparseArray<List<View>>();
    private BaseAdapter adapter;
    private final DataSetObserver observer = new DataSetObserver() {
        @Override
        public void onChanged() {
            recycleAll();
            clampOffset();
            requestLayout();
            invalidate();
        }

        @Override
        public void onInvalidated() {
            onChanged();
        }
    };
    private View leading;
    private View title;
    private int rows = 2;
    private int textHeight;
    private int columnWidth;
    private int gap;
    /** Space before the first column and after the last. */
    private int inset;
    /** How far the content has scrolled, in pixels. */
    private float offset;
    private int pendingItem = -1;

    private float downX;
    private float downY;
    private float lastX;
    private boolean dragging;
    private VelocityTracker velocity;

    public HorizontalGrid(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        ViewConfiguration vc = ViewConfiguration.get(context);
        touchSlop = vc.getScaledTouchSlop();
        minFling = vc.getScaledMinimumFlingVelocity();
        maxFling = vc.getScaledMaximumFlingVelocity();
        scroller = new OverScroller(context);
        gap = Math.round(14 * density);
        inset = Math.round(16 * density);
        setClipToPadding(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
    }

    public void setAdapter(BaseAdapter a) {
        if (adapter != null) adapter.unregisterDataSetObserver(observer);
        adapter = a;
        if (adapter != null) adapter.registerDataSetObserver(observer);
        recycleAll();
        scrap.clear();
        requestLayout();
    }

    /** Scrolls with the columns, before the first one (the Library menu). */
    public void setLeading(View v) {
        if (leading != null) removeView(leading);
        leading = v;
        if (v != null) addView(v);
    }

    /** Sits above the first columns and scrolls with them ("Recently Added"). */
    public void setTitle(View v) {
        if (title != null) removeView(title);
        title = v;
        if (v != null) addView(v);
    }

    /** Cards per column, and the height of the text under each card's picture. */
    public void setShape(int rows, int textHeight) {
        this.rows = Math.max(1, rows);
        this.textHeight = textHeight;
        requestLayout();
    }

    public int getRows() {
        return rows;
    }

    /** Width of a column, including the gap after it (known after layout). */
    public int getColumnWidth() {
        return columnWidth;
    }

    /** The first column (adapter position) at least partly on screen. */
    public int getFirstVisibleColumn() {
        if (columnWidth == 0) return pendingItem >= 0 ? pendingItem : 0;
        return Math.max(0, (int) ((offset - leadingWidth() - inset) / columnWidth));
    }

    /** Has it scrolled past the leading view (the Library menu)? */
    public boolean isPastLeading() {
        return leading != null && offset >= leadingWidth();
    }

    /** Scrolls so column is first on screen (after the next layout if needed). */
    public void scrollToColumn(int column) {
        if (columnWidth == 0) {
            pendingItem = column;
            return;
        }
        scroller.forceFinished(true);
        offset = column <= 0 ? 0 : leadingWidth() + inset + column * (float) columnWidth;
        clampOffset();
        layoutColumns();
    }

    private int leadingWidth() {
        return leading != null && leading.getVisibility() != GONE ? leading.getMeasuredWidth() : 0;
    }

    private int titleHeight() {
        return title != null && title.getVisibility() != GONE ? title.getMeasuredHeight() : 0;
    }

    private int count() {
        return adapter != null ? adapter.getCount() : 0;
    }

    private float maxOffset() {
        float content = leadingWidth() + 2 * inset + count() * columnWidth + getPaddingLeft() + getPaddingRight() - gap;
        return Math.max(0, content - getWidth());
    }

    private void clampOffset() {
        offset = Math.max(0, Math.min(offset, maxOffset()));
    }

    // ---- Measure and layout ----

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(width, height);
        int inner = height - getPaddingTop() - getPaddingBottom();
        if (leading != null) {
            LayoutParams lp = leading.getLayoutParams();
            leading.measure(lp != null && lp.width > 0 ? MeasureSpec.makeMeasureSpec(Math.min(lp.width, width),
                    MeasureSpec.EXACTLY) : MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST),
                    MeasureSpec.makeMeasureSpec(inner, MeasureSpec.EXACTLY));
        }
        if (title != null) {
            title.measure(MeasureSpec.makeMeasureSpec(Math.max(0, width - leadingWidth()), MeasureSpec.AT_MOST),
                    MeasureSpec.makeMeasureSpec(inner, MeasureSpec.AT_MOST));
        }
        int cells = inner - titleHeight() - (rows - 1) * gap;
        int picture = Math.max(Math.round(48 * density), cells / rows - textHeight);
        columnWidth = picture + gap;
        for (int i = 0; i < columns.size(); i++) measureColumn(columns.valueAt(i));
    }

    private void measureColumn(View column) {
        int inner = getMeasuredHeight() - getPaddingTop() - getPaddingBottom() - titleHeight();
        column.measure(MeasureSpec.makeMeasureSpec(columnWidth - gap, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(Math.max(0, inner), MeasureSpec.EXACTLY));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (pendingItem >= 0 && columnWidth > 0) {
            offset = pendingItem == 0 ? 0 : leadingWidth() + inset + pendingItem * (float) columnWidth;
            pendingItem = -1;
        }
        clampOffset();
        layoutColumns();
    }

    /** Places the leading view, title and the columns on screen; recycles the rest. */
    private void layoutColumns() {
        int left = getPaddingLeft() - Math.round(offset);
        int top = getPaddingTop();
        if (leading != null) {
            leading.layout(left, top, left + leading.getMeasuredWidth(), top + leading.getMeasuredHeight());
        }
        int start = left + leadingWidth();
        if (title != null) title.layout(start, top, start + title.getMeasuredWidth(), top + title.getMeasuredHeight());
        start += inset;
        int columnTop = top + titleHeight();
        int n = count();
        if (columnWidth <= 0 || n == 0) {
            recycleAll();
            return;
        }
        int first = Math.max(0, (-start) / columnWidth);
        int last = Math.min(n - 1, (getWidth() - start) / columnWidth);
        // Recycle columns that scrolled off.
        for (int i = columns.size() - 1; i >= 0; i--) {
            int position = columns.keyAt(i);
            if (position < first || position > last) {
                View v = columns.valueAt(i);
                columns.removeAt(i);
                removeViewInLayout(v);
                scrapList(adapter.getItemViewType(Math.min(position, n - 1))).add(v);
            }
        }
        for (int position = first; position <= last; position++) {
            View v = columns.get(position);
            if (v == null) {
                List<View> spare = scrapList(adapter.getItemViewType(position));
                View convert = spare.isEmpty() ? null : spare.remove(spare.size() - 1);
                v = adapter.getView(position, convert, this);
                LayoutParams lp = v.getLayoutParams();
                if (lp == null) lp = generateDefaultLayoutParams();
                addViewInLayout(v, -1, lp, true);
                measureColumn(v);
                columns.put(position, v);
            }
            int x = start + position * columnWidth;
            v.layout(x, columnTop, x + v.getMeasuredWidth(), columnTop + v.getMeasuredHeight());
        }
    }

    private List<View> scrapList(int type) {
        List<View> list = scrap.get(type);
        if (list == null) {
            list = new ArrayList<View>();
            scrap.put(type, list);
        }
        return list;
    }

    private void recycleAll() {
        for (int i = 0; i < columns.size(); i++) {
            View v = columns.valueAt(i);
            removeViewInLayout(v);
            // Data changed: views may not match the new items' types, so start fresh.
        }
        columns.clear();
        scrap.clear();
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
    }

    // ---- Scrolling ----

    private void scrollBy(float dx) {
        float before = offset;
        offset += dx;
        clampOffset();
        if (offset != before) {
            layoutColumns();
            invalidate();
        }
    }

    @Override
    public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            offset = scroller.getCurrX();
            clampOffset();
            layoutColumns();
            postInvalidateOnAnimation();
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = e.getX();
                downY = e.getY();
                dragging = !scroller.isFinished();
                scroller.forceFinished(true);
                track(e, true);
                return dragging;
            case MotionEvent.ACTION_MOVE:
                float dx = e.getX() - downX;
                float dy = e.getY() - downY;
                if (!dragging && Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy)) {
                    dragging = true;
                    lastX = e.getX();
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                }
                track(e, false);
                return dragging;
            default:
                return false;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = e.getX();
                downY = e.getY();
                scroller.forceFinished(true);
                track(e, true);
                return true;
            case MotionEvent.ACTION_MOVE:
                track(e, false);
                if (!dragging && Math.abs(e.getX() - downX) > touchSlop) {
                    dragging = true;
                    lastX = e.getX();
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                }
                if (dragging) {
                    scrollBy(lastX - e.getX());
                    lastX = e.getX();
                }
                return true;
            case MotionEvent.ACTION_UP:
                track(e, false);
                if (dragging && velocity != null) {
                    velocity.computeCurrentVelocity(1000, maxFling);
                    float v = velocity.getXVelocity();
                    if (Math.abs(v) > minFling) {
                        scroller.fling(Math.round(offset), 0, Math.round(-v), 0, 0, Math.round(maxOffset()), 0, 0);
                        postInvalidateOnAnimation();
                    }
                }
                endTouch();
                return true;
            case MotionEvent.ACTION_CANCEL:
                endTouch();
                return true;
            default:
                return true;
        }
    }

    private void track(MotionEvent e, boolean start) {
        if (velocity == null) velocity = VelocityTracker.obtain();
        if (start) velocity.clear();
        velocity.addMovement(e);
    }

    private void endTouch() {
        dragging = false;
        if (velocity != null) {
            velocity.recycle();
            velocity = null;
        }
    }

    @Override
    public boolean canScrollHorizontally(int direction) {
        return direction < 0 ? offset > 0 : offset < maxOffset();
    }
}
