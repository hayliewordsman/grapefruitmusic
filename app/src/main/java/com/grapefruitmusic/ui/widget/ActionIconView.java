package com.grapefruitmusic.ui.widget;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;

import com.grapefruitmusic.R;
import com.grapefruitmusic.ui.Ui;

/** Shared labelled action tile, retaining IconView's interaction and accessibility behavior. */
public final class ActionIconView extends IconView {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int tint;
    private boolean labelVisible = true;

    public ActionIconView(Context c) {
        super(c);
        init();
    }

    public ActionIconView(Context c, AttributeSet a) {
        super(c, a);
        init();
    }

    private void init() {
        setFocusable(true);
        setPadding(
                Ui.dp(getContext(), 13),
                Ui.dp(getContext(), 7),
                Ui.dp(getContext(), 13),
                Ui.dp(getContext(), 24));
    }

    /** Keep content descriptions for accessibility when the visual label is hidden. */
    public void setLabelVisible(boolean visible) {
        labelVisible = visible;
        int horizontal = Ui.dp(getContext(), 13);
        setPadding(
                horizontal,
                Ui.dp(getContext(), visible ? 7 : 13),
                horizontal,
                Ui.dp(getContext(), visible ? 24 : 13));
        invalidate();
    }

    @Override
    public void setColor(int c) {
        tint = c;
        super.setColor(c);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Context c = getContext();
        paint.setColor(
                tint == Ui.accent(c)
                        ? (tint & 0x00ffffff) | 0x40000000
                        : Ui.color(c, R.attr.gfControl));
        float gap = Ui.dp(c, 3), radius = Ui.dp(c, 12);
        canvas.drawRoundRect(
                new RectF(gap, 0, getWidth() - gap, getHeight()), radius, radius, paint);
        super.onDraw(canvas);
        CharSequence label = getContentDescription();
        if (labelVisible && label != null) {
            paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            paint.setTextSize(10 * getResources().getDisplayMetrics().scaledDensity);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setColor(tint);
            String text = label.toString();
            if (text.equals("Live Mode")) text = "Live";
            if (text.equals("Karaoke Mode")) text = "Karaoke";
            if (text.startsWith("Add to ")) text = "Save";
            if (text.startsWith("Remove from ")) text = "Saved";
            float available = Math.max(1, getWidth() - Ui.dp(c, 6));
            float measured = paint.measureText(text);
            if (measured > available) paint.setTextSize(paint.getTextSize() * available / measured);
            canvas.drawText(text, getWidth() / 2f, getHeight() - Ui.dp(c, 8), paint);
        }
    }
}
