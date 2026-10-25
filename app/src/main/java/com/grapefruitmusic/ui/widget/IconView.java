package com.grapefruitmusic.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/** A tappable vector icon. Dims while pressed, like iOS. */
public class IconView extends View {
    private final RectF bounds = new RectF();
    private int icon;
    private int color = 0xff000000;
    private float iconScale = 1f;

    public IconView(Context context) {
        super(context);
    }

    public IconView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setIcon(int icon) {
        if (this.icon != icon) {
            this.icon = icon;
            invalidate();
        }
    }

    public void setColor(int color) {
        if (this.color != color) {
            this.color = color;
            invalidate();
        }
    }

    /** Fraction of the view's size the glyph fills (default 1, minus padding). */
    public void setIconScale(float scale) {
        iconScale = scale;
        invalidate();
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (icon == 0) return;
        float w = getWidth() - getPaddingLeft() - getPaddingRight();
        float h = getHeight() - getPaddingTop() - getPaddingBottom();
        float size = Math.min(w, h) * iconScale;
        float cx = getPaddingLeft() + w / 2f;
        float cy = getPaddingTop() + h / 2f;
        bounds.set(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f);
        int c = color;
        if (isPressed()) c = (c & 0x00ffffff) | ((((c >>> 24) * 35) / 100) << 24);
        else if (!isEnabled()) c = (c & 0x00ffffff) | ((((c >>> 24) * 30) / 100) << 24);
        Icons.draw(canvas, icon, bounds, c);
    }
}
