package com.grapefruitmusic.ui.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.FrameLayout;

/**
 * Holds a list whose rows can be dragged by a handle. Once a drag starts, this frame takes the
 * rest of the gesture: the list redraws its rows while they move, and Android cancels touches
 * on rows it redraws, so the row's handle can't keep it.
 */
public class DragFrame extends FrameLayout {
    public interface Callback {
        boolean isDragging();

        void onDragMove(float rawY);

        void onDragEnd();
    }

    private Callback callback;

    public DragFrame(Context context) {
        super(context);
    }

    public DragFrame(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    private boolean dragging() {
        return callback != null && callback.isDragging();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        if (!dragging()) return false;
        // The event that is intercepted doesn't reach onTouchEvent, so handle it here.
        handle(e);
        return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!dragging()) return false;
        handle(e);
        return true;
    }

    private void handle(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                callback.onDragMove(e.getRawY());
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                callback.onDragEnd();
                break;
            default:
                break;
        }
    }
}
