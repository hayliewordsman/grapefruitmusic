package io.github.hayliewordsman.grapefruitai.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/**
 * Line icons drawn with Canvas (Android 4.2 has no vector drawables), on a 24-unit grid, in the
 * rounded stroke style of Grapefruit's own icons.
 */
public final class IconView extends View {
    public static final int CHAT = 0, IMAGE = 1, CODE = 2, SETTINGS = 3, PLUS = 4, SEND = 5, STOP = 6,
            COPY = 7, BACK = 8, ATTACH = 9, DOWNLOAD = 10, CHECK = 11, CLOSE = 12, SEARCH = 13, SPARK = 14,
            FOLDER = 15, FILE = 16, HISTORY = 17, GLOBE = 18, REFRESH = 19, CHEVRON = 20, DOWN = 21, TRASH = 22;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF oval = new RectF();
    private int icon;

    public IconView(Context c) {
        super(c);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
    }

    public void setIcon(int icon) {
        this.icon = icon;
        invalidate();
    }

    public void setColor(int color) {
        paint.setColor(color);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth() - getPaddingLeft() - getPaddingRight();
        int h = getHeight() - getPaddingTop() - getPaddingBottom();
        float size = Math.min(w, h);
        if (size <= 0) return;
        float u = size / 24f;
        canvas.save();
        canvas.translate(getPaddingLeft() + (w - size) / 2f, getPaddingTop() + (h - size) / 2f);
        canvas.scale(u, u);
        paint.setStrokeWidth(2f);
        paint.setStyle(Paint.Style.STROKE);
        path.reset();
        switch (icon) {
            case CHAT:
                oval.set(3, 4, 21, 17);
                path.addRoundRect(oval, 5, 5, Path.Direction.CW);
                canvas.drawPath(path, paint);
                path.reset();
                path.moveTo(8, 17);
                path.lineTo(6, 21);
                path.lineTo(12, 17);
                canvas.drawPath(path, paint);
                break;
            case IMAGE:
                oval.set(3, 4, 21, 20);
                canvas.drawRoundRect(oval, 3, 3, paint);
                canvas.drawCircle(9, 9.5f, 1.8f, paint);
                path.moveTo(4, 18);
                path.lineTo(10, 13);
                path.lineTo(14, 16);
                path.lineTo(17, 13);
                path.lineTo(20, 16);
                canvas.drawPath(path, paint);
                break;
            case CODE:
                path.moveTo(8, 7);
                path.lineTo(3, 12);
                path.lineTo(8, 17);
                path.moveTo(16, 7);
                path.lineTo(21, 12);
                path.lineTo(16, 17);
                path.moveTo(13.5f, 5);
                path.lineTo(10.5f, 19);
                canvas.drawPath(path, paint);
                break;
            case SETTINGS:
                canvas.drawCircle(12, 12, 3, paint);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI / 4 * i;
                    canvas.drawLine(12 + 5.5f * (float) Math.cos(a), 12 + 5.5f * (float) Math.sin(a),
                            12 + 8.5f * (float) Math.cos(a), 12 + 8.5f * (float) Math.sin(a), paint);
                }
                canvas.drawCircle(12, 12, 5.5f, paint);
                break;
            case PLUS:
                canvas.drawLine(12, 5, 12, 19, paint);
                canvas.drawLine(5, 12, 19, 12, paint);
                break;
            case SEND:
                canvas.drawLine(12, 19, 12, 5, paint);
                path.moveTo(6, 11);
                path.lineTo(12, 5);
                path.lineTo(18, 11);
                canvas.drawPath(path, paint);
                break;
            case STOP:
                paint.setStyle(Paint.Style.FILL);
                oval.set(7, 7, 17, 17);
                canvas.drawRoundRect(oval, 2.5f, 2.5f, paint);
                break;
            case COPY:
                oval.set(8, 8, 20, 20);
                canvas.drawRoundRect(oval, 2.5f, 2.5f, paint);
                path.moveTo(5, 15);
                path.lineTo(4, 15);
                path.lineTo(4, 4);
                path.lineTo(15, 4);
                path.lineTo(15, 5);
                canvas.drawPath(path, paint);
                break;
            case BACK:
                path.moveTo(15, 5);
                path.lineTo(8, 12);
                path.lineTo(15, 19);
                canvas.drawPath(path, paint);
                break;
            case CHEVRON:
                path.moveTo(9, 5);
                path.lineTo(16, 12);
                path.lineTo(9, 19);
                canvas.drawPath(path, paint);
                break;
            case DOWN:
                path.moveTo(6, 9);
                path.lineTo(12, 15);
                path.lineTo(18, 9);
                canvas.drawPath(path, paint);
                break;
            case ATTACH:
                path.moveTo(19, 11.5f);
                path.lineTo(12, 18.5f);
                path.cubicTo(9.5f, 21, 5.5f, 21, 4.5f, 18.5f);
                path.cubicTo(3, 16, 4, 14, 5.5f, 12.5f);
                path.lineTo(13, 5);
                path.cubicTo(14.5f, 3.5f, 17, 3.5f, 18, 5);
                path.cubicTo(19.5f, 6.5f, 19, 8.5f, 17.5f, 10);
                path.lineTo(10.5f, 17);
                path.cubicTo(9.5f, 18, 8, 18, 7.5f, 17);
                path.cubicTo(6.5f, 16, 7, 15, 7.5f, 14.5f);
                path.lineTo(14, 8);
                canvas.drawPath(path, paint);
                break;
            case DOWNLOAD:
                canvas.drawLine(12, 4, 12, 15, paint);
                path.moveTo(7, 10);
                path.lineTo(12, 15);
                path.lineTo(17, 10);
                path.moveTo(5, 19);
                path.lineTo(19, 19);
                canvas.drawPath(path, paint);
                break;
            case CHECK:
                path.moveTo(5, 12.5f);
                path.lineTo(10, 17.5f);
                path.lineTo(19, 7);
                canvas.drawPath(path, paint);
                break;
            case CLOSE:
                canvas.drawLine(6, 6, 18, 18, paint);
                canvas.drawLine(18, 6, 6, 18, paint);
                break;
            case SEARCH:
                canvas.drawCircle(10.5f, 10.5f, 6, paint);
                canvas.drawLine(15, 15, 20, 20, paint);
                break;
            case SPARK:
                paint.setStyle(Paint.Style.FILL);
                path.moveTo(12, 3);
                path.lineTo(14, 10);
                path.lineTo(21, 12);
                path.lineTo(14, 14);
                path.lineTo(12, 21);
                path.lineTo(10, 14);
                path.lineTo(3, 12);
                path.lineTo(10, 10);
                path.close();
                canvas.drawPath(path, paint);
                break;
            case FOLDER:
                path.moveTo(3, 7);
                path.lineTo(3, 18);
                path.lineTo(21, 18);
                path.lineTo(21, 8);
                path.lineTo(12, 8);
                path.lineTo(10, 5.5f);
                path.lineTo(3, 5.5f);
                path.close();
                canvas.drawPath(path, paint);
                break;
            case FILE:
                path.moveTo(6, 3);
                path.lineTo(14, 3);
                path.lineTo(19, 8);
                path.lineTo(19, 21);
                path.lineTo(6, 21);
                path.close();
                path.moveTo(14, 3);
                path.lineTo(14, 8);
                path.lineTo(19, 8);
                canvas.drawPath(path, paint);
                break;
            case HISTORY:
                canvas.drawCircle(12, 12, 8, paint);
                path.moveTo(12, 7.5f);
                path.lineTo(12, 12);
                path.lineTo(15, 14);
                canvas.drawPath(path, paint);
                break;
            case GLOBE:
                canvas.drawCircle(12, 12, 8.5f, paint);
                oval.set(8, 3.5f, 16, 20.5f);
                canvas.drawOval(oval, paint);
                canvas.drawLine(3.5f, 12, 20.5f, 12, paint);
                break;
            case REFRESH:
                oval.set(4.5f, 4.5f, 19.5f, 19.5f);
                path.addArc(oval, -60, 300);
                canvas.drawPath(path, paint);
                path.reset();
                path.moveTo(15.5f, 3);
                path.lineTo(16.2f, 6.6f);
                path.lineTo(12.6f, 7.4f);
                canvas.drawPath(path, paint);
                break;
            case TRASH:
                path.moveTo(4, 7);
                path.lineTo(20, 7);
                path.moveTo(9, 7);
                path.lineTo(9, 4);
                path.lineTo(15, 4);
                path.lineTo(15, 7);
                path.moveTo(6, 7);
                path.lineTo(7, 20);
                path.lineTo(17, 20);
                path.lineTo(18, 7);
                canvas.drawPath(path, paint);
                break;
            default:
                break;
        }
        canvas.restore();
    }
}
