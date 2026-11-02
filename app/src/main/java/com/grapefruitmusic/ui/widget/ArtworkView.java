package com.grapefruitmusic.ui.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/** Square album art with rounded corners (or a circle), a hairline edge and an optional shadow. */
public class ArtworkView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix = new Matrix();
    private final RectF rect = new RectF();
    private final RectF iconRect = new RectF();
    private Bitmap bitmap;
    private BitmapShader shader;
    private float radius;
    private boolean circle;
    private float shadowSize;
    private int placeholderColor = 0xffeeeef0;
    private int iconColor = 0xffb0b0b5;

    public ArtworkView(Context context) {
        super(context);
        init();
    }

    public ArtworkView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        radius = 4 * getResources().getDisplayMetrics().density;
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(Math.max(1f, getResources().getDisplayMetrics().density * 0.5f));
        edge.setColor(0x1a000000);
    }

    public void setBitmap(Bitmap b) {
        if (b == bitmap) return;
        bitmap = b;
        shader = b == null ? null : new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        invalidate();
    }

    public Bitmap getBitmap() {
        return bitmap;
    }

    public void setCornerRadiusDp(float dp) {
        radius = dp * getResources().getDisplayMetrics().density;
        invalidate();
    }

    public void setCircle(boolean circle) {
        this.circle = circle;
        invalidate();
    }

    public void setPlaceholderColors(int background, int icon) {
        placeholderColor = background;
        iconColor = icon;
        invalidate();
    }

    /**
     * Soft drop shadow (Now Playing). The blurred shape is drawn once into a mask (an alpha-only
     * picture, made again only when the artwork's size or corners change) and painted from there
     * in the shadow's colour: the view stays hardware-drawn. (Until 5.0 beta 9 it drew the shadow
     * with a software layer, which redrew the whole artwork on the CPU.)
     */
    public void setShadowDp(float dp) {
        shadowSize = dp * getResources().getDisplayMetrics().density;
        shadowMask = null;
        invalidate();
    }

    private int shadowColor = 0x55000000;
    /** The blurred artwork shape (ALPHA_8), for maskSize and maskRadius. */
    private Bitmap shadowMask;
    private float maskSize = -1;
    private float maskRadius = -1;
    private final Paint maskPaint = new Paint(Paint.FILTER_BITMAP_FLAG);

    /** The shadow's colour: a glow in the artwork's own colour, say. */
    public void setShadowColor(int color) {
        shadowColor = color;
        invalidate();
    }

    /** The shadow under rect (the artwork, size across, corners r), from the cached mask. */
    private void drawShadow(Canvas canvas, float size, float r) {
        float blur = shadowSize * 0.9f;
        if (shadowMask == null || maskSize != size || maskRadius != r) {
            int side = (int) Math.ceil(size + 2 * blur);
            if (side <= 0) return;
            Bitmap mask;
            try {
                mask = Bitmap.createBitmap(side, side, Bitmap.Config.ALPHA_8);
            } catch (OutOfMemoryError e) {
                return;
            }
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(0xff000000);
            if (blur > 0) p.setMaskFilter(new android.graphics.BlurMaskFilter(blur, android.graphics.BlurMaskFilter.Blur.NORMAL));
            new Canvas(mask).drawRoundRect(new RectF(blur, blur, blur + size, blur + size), r, r, p);
            // Not recycled: a frame being drawn may still use the old one.
            shadowMask = mask;
            maskSize = size;
            maskRadius = r;
        }
        // An alpha-only picture is painted in the paint's colour.
        maskPaint.setColor(shadowColor);
        canvas.drawBitmap(shadowMask, rect.left - blur, rect.top - blur + shadowSize * 0.3f, maskPaint);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec);
        int h = MeasureSpec.getSize(heightSpec);
        int wm = MeasureSpec.getMode(widthSpec);
        int hm = MeasureSpec.getMode(heightSpec);
        int size;
        if (wm == MeasureSpec.UNSPECIFIED && hm == MeasureSpec.UNSPECIFIED) size = 0;
        else if (wm == MeasureSpec.UNSPECIFIED) size = h;
        else if (hm == MeasureSpec.UNSPECIFIED) size = w;
        else size = Math.min(w, h);
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float pad = shadowSize;
        rect.set(pad, pad * 0.6f, getWidth() - pad, getHeight() - pad * 1.4f);
        float size = Math.min(rect.width(), rect.height());
        rect.set(rect.centerX() - size / 2f, rect.centerY() - size / 2f,
                rect.centerX() + size / 2f, rect.centerY() + size / 2f);
        float r = circle ? size / 2f : radius;
        if (shadowSize > 0) drawShadow(canvas, size, r);
        if (shader != null) {
            float scale = Math.max(size / bitmap.getWidth(), size / bitmap.getHeight());
            matrix.setScale(scale, scale);
            matrix.postTranslate(rect.left + (size - bitmap.getWidth() * scale) / 2f,
                    rect.top + (size - bitmap.getHeight() * scale) / 2f);
            shader.setLocalMatrix(matrix);
            paint.setShader(shader);
            canvas.drawRoundRect(rect, r, r, paint);
        } else {
            fill.setColor(placeholderColor);
            canvas.drawRoundRect(rect, r, r, fill);
            float icon = size * 0.42f;
            iconRect.set(rect.centerX() - icon / 2f, rect.centerY() - icon / 2f,
                    rect.centerX() + icon / 2f, rect.centerY() + icon / 2f);
            Icons.draw(canvas, Icons.NOTE, iconRect, iconColor);
        }
        canvas.drawRoundRect(rect, r, r, edge);
    }
}
