package com.grapefruitmusic.ui.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.*;
import android.widget.FrameLayout;

import com.grapefruitmusic.R;
import com.grapefruitmusic.ui.Ui;

/** Same metadata on every window: stack when tall, two columns when short or square. */
public final class PlayerMetadata extends FrameLayout {
    public PlayerMetadata(Context c, AttributeSet a) {
        super(c, a);
    }

    private boolean columns(int w, int h) {
        int choice = com.grapefruitmusic.util.Prefs.raw().getInt("now_playing_layout", 0);
        return choice == 2 || (choice == 0 && w >= h * 1.05f);
    }

    @Override
    protected void onMeasure(int ws, int hs) {
        int w = MeasureSpec.getSize(ws), h = MeasureSpec.getSize(hs), gap = Ui.dp(getContext(), 16);
        View art = findViewById(R.id.np_art), labels = findViewById(R.id.np_metadata);
        boolean columns = columns(w, h);
        int aw =
                columns
                        ? Math.max(0, Math.min(h - gap * 2, (w - gap * 3) / 2))
                        : Math.max(0, Math.min(w - gap * 2, h - Ui.dp(getContext(), 90)));
        art.measure(
                MeasureSpec.makeMeasureSpec(aw, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(aw, MeasureSpec.EXACTLY));
        labels.measure(
                MeasureSpec.makeMeasureSpec(
                        Math.max(0, columns ? w - aw - gap * 3 : w - gap * 2), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(
                        Math.max(0, columns ? h - gap * 2 : h - aw - gap), MeasureSpec.AT_MOST));
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int w = r - l, h = b - t, gap = Ui.dp(getContext(), 16);
        View art = findViewById(R.id.np_art), labels = findViewById(R.id.np_metadata);
        boolean columns = columns(w, h);
        int aw = art.getMeasuredWidth(), lh = labels.getMeasuredHeight();
        int x = columns ? gap : (w - aw) / 2,
                y = columns ? (h - aw) / 2 : Math.max(0, (h - aw - lh - gap) / 2);
        art.layout(x, y, x + aw, y + aw);
        int tx = columns ? x + aw + gap : gap, ty = columns ? (h - lh) / 2 : y + aw + gap;
        labels.layout(tx, ty, tx + labels.getMeasuredWidth(), ty + lh);
    }
}
