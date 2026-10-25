package com.grapefruitmusic.ui.widget;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/** Vector icons drawn in code (a 24x24 grid), so they scale and take any theme color. */
public final class Icons {
    public static final int PLAY = 1;
    public static final int PAUSE = 2;
    public static final int NEXT = 3;
    public static final int PREVIOUS = 4;
    public static final int LIBRARY = 5;
    public static final int SEARCH = 6;
    public static final int SETTINGS = 7;
    public static final int CHEVRON_RIGHT = 8;
    public static final int CHEVRON_LEFT = 9;
    public static final int CHEVRON_DOWN = 10;
    public static final int LYRICS = 11;
    public static final int SHUFFLE = 12;
    public static final int REPEAT = 13;
    public static final int REPEAT_ONE = 14;
    public static final int QUEUE = 15;
    public static final int DOWNLOAD = 16;
    public static final int DOWNLOADED = 17;
    public static final int MORE = 18;
    public static final int NOTE = 19;
    public static final int PLUS = 20;
    public static final int SPEAKER_LOW = 21;
    public static final int SPEAKER_HIGH = 22;
    public static final int CHECK = 23;
    public static final int PLAYLIST = 24;
    public static final int HANDLE = 25;
    /** Filled circle with a white minus (the color is the circle's). */
    public static final int REMOVE = 26;

    private static final Paint PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path PATH = new Path();
    private static final RectF OVAL = new RectF();

    private Icons() {
    }

    /** Draws icon into bounds (kept square, centered). Main thread only. */
    public static void draw(Canvas c, int icon, RectF bounds, int color) {
        float size = Math.min(bounds.width(), bounds.height());
        float s = size / 24f;
        c.save();
        c.translate(bounds.centerX() - size / 2f, bounds.centerY() - size / 2f);
        c.scale(s, s);
        Paint p = PAINT;
        p.reset();
        p.setAntiAlias(true);
        p.setColor(color);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        Path path = PATH;
        path.reset();
        switch (icon) {
            case PLAY:
                fillRounded(p);
                path.moveTo(7, 4.5f);
                path.lineTo(19.5f, 12);
                path.lineTo(7, 19.5f);
                path.close();
                c.drawPath(path, p);
                break;
            case PAUSE:
                p.setStyle(Paint.Style.FILL);
                OVAL.set(6, 4.5f, 10, 19.5f);
                c.drawRoundRect(OVAL, 1, 1, p);
                OVAL.set(14, 4.5f, 18, 19.5f);
                c.drawRoundRect(OVAL, 1, 1, p);
                break;
            case NEXT:
            case PREVIOUS:
                if (icon == PREVIOUS) {
                    c.scale(-1, 1, 12, 12);
                }
                fillRounded(p);
                path.moveTo(2.5f, 6);
                path.lineTo(11.5f, 12);
                path.lineTo(2.5f, 18);
                path.close();
                path.moveTo(12, 6);
                path.lineTo(21, 12);
                path.lineTo(12, 18);
                path.close();
                c.drawPath(path, p);
                break;
            case LIBRARY:
                stroke(p, 2.4f);
                c.drawLine(4.5f, 4, 4.5f, 20, p);
                c.drawLine(9, 4, 9, 20, p);
                c.drawLine(13.5f, 4, 13.5f, 20, p);
                c.drawLine(17, 4.8f, 20.5f, 19.5f, p);
                break;
            case SEARCH:
                stroke(p, 2.2f);
                c.drawCircle(10.5f, 10.5f, 6.5f, p);
                p.setStrokeWidth(2.6f);
                c.drawLine(15.5f, 15.5f, 20.5f, 20.5f, p);
                break;
            case SETTINGS:
                stroke(p, 3.4f);
                c.drawCircle(12, 12, 5.6f, p);
                p.setStyle(Paint.Style.FILL);
                for (int i = 0; i < 8; i++) {
                    c.save();
                    c.rotate(i * 45, 12, 12);
                    OVAL.set(10.4f, 1.8f, 13.6f, 6.4f);
                    c.drawRoundRect(OVAL, 0.8f, 0.8f, p);
                    c.restore();
                }
                break;
            case CHEVRON_RIGHT:
                stroke(p, 2.4f);
                path.moveTo(9, 5);
                path.lineTo(16, 12);
                path.lineTo(9, 19);
                c.drawPath(path, p);
                break;
            case CHEVRON_LEFT:
                stroke(p, 2.6f);
                path.moveTo(15.5f, 4);
                path.lineTo(7.5f, 12);
                path.lineTo(15.5f, 20);
                c.drawPath(path, p);
                break;
            case CHEVRON_DOWN:
                stroke(p, 2.4f);
                path.moveTo(5, 9);
                path.lineTo(12, 15.5f);
                path.lineTo(19, 9);
                c.drawPath(path, p);
                break;
            case LYRICS:
                stroke(p, 1.8f);
                OVAL.set(3, 4, 21, 17);
                c.drawRoundRect(OVAL, 3.5f, 3.5f, p);
                path.moveTo(8, 17);
                path.lineTo(6.5f, 21);
                path.lineTo(12, 17);
                c.drawPath(path, p);
                c.drawLine(7.5f, 9, 16.5f, 9, p);
                c.drawLine(7.5f, 12.5f, 13.5f, 12.5f, p);
                break;
            case SHUFFLE:
                stroke(p, 2);
                path.moveTo(3, 7);
                path.lineTo(7.5f, 7);
                path.lineTo(14.5f, 17);
                path.lineTo(20, 17);
                path.moveTo(3, 17);
                path.lineTo(7.5f, 17);
                path.lineTo(14.5f, 7);
                path.lineTo(20, 7);
                path.moveTo(17.5f, 4.5f);
                path.lineTo(20, 7);
                path.lineTo(17.5f, 9.5f);
                path.moveTo(17.5f, 14.5f);
                path.lineTo(20, 17);
                path.lineTo(17.5f, 19.5f);
                c.drawPath(path, p);
                break;
            case REPEAT:
            case REPEAT_ONE:
                stroke(p, 2);
                path.moveTo(4, 12.5f);
                path.lineTo(4, 9.5f);
                path.quadTo(4, 7, 6.5f, 7);
                path.lineTo(19, 7);
                path.moveTo(16.5f, 4.5f);
                path.lineTo(19, 7);
                path.lineTo(16.5f, 9.5f);
                path.moveTo(20, 11.5f);
                path.lineTo(20, 14.5f);
                path.quadTo(20, 17, 17.5f, 17);
                path.lineTo(5, 17);
                path.moveTo(7.5f, 14.5f);
                path.lineTo(5, 17);
                path.lineTo(7.5f, 19.5f);
                if (icon == REPEAT_ONE) {
                    path.moveTo(11, 10.8f);
                    path.lineTo(12.3f, 9.8f);
                    path.lineTo(12.3f, 14.2f);
                }
                c.drawPath(path, p);
                break;
            case QUEUE:
                stroke(p, 2);
                c.drawLine(9, 6, 20.5f, 6, p);
                c.drawLine(9, 12, 20.5f, 12, p);
                c.drawLine(9, 18, 20.5f, 18, p);
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(4.5f, 6, 1.4f, p);
                c.drawCircle(4.5f, 12, 1.4f, p);
                c.drawCircle(4.5f, 18, 1.4f, p);
                break;
            case DOWNLOAD:
                stroke(p, 1.8f);
                c.drawCircle(12, 12, 9, p);
                c.drawLine(12, 7, 12, 16, p);
                path.moveTo(8.5f, 12.8f);
                path.lineTo(12, 16.3f);
                path.lineTo(15.5f, 12.8f);
                c.drawPath(path, p);
                break;
            case DOWNLOADED:
                stroke(p, 1.8f);
                c.drawCircle(12, 12, 9, p);
                path.moveTo(7.8f, 12.3f);
                path.lineTo(10.8f, 15.3f);
                path.lineTo(16.4f, 9.3f);
                c.drawPath(path, p);
                break;
            case MORE:
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(5.5f, 12, 1.9f, p);
                c.drawCircle(12, 12, 1.9f, p);
                c.drawCircle(18.5f, 12, 1.9f, p);
                break;
            case NOTE:
                stroke(p, 1.6f);
                c.drawLine(9, 17, 9, 6, p);
                c.drawLine(19, 15, 19, 4, p);
                p.setStyle(Paint.Style.FILL);
                path.moveTo(9, 5.2f);
                path.lineTo(19, 3);
                path.lineTo(19, 6);
                path.lineTo(9, 8.2f);
                path.close();
                c.drawPath(path, p);
                OVAL.set(4.2f, 15.2f, 9.8f, 19.6f);
                c.drawOval(OVAL, p);
                OVAL.set(14.2f, 13.2f, 19.8f, 17.6f);
                c.drawOval(OVAL, p);
                break;
            case PLUS:
                stroke(p, 2.2f);
                c.drawLine(12, 5, 12, 19, p);
                c.drawLine(5, 12, 19, 12, p);
                break;
            case SPEAKER_LOW:
            case SPEAKER_HIGH:
                p.setStyle(Paint.Style.FILL);
                path.moveTo(3, 9.5f);
                path.lineTo(6.5f, 9.5f);
                path.lineTo(11, 5.5f);
                path.lineTo(11, 18.5f);
                path.lineTo(6.5f, 14.5f);
                path.lineTo(3, 14.5f);
                path.close();
                c.drawPath(path, p);
                if (icon == SPEAKER_HIGH) {
                    stroke(p, 1.7f);
                    OVAL.set(9, 8, 17, 16);
                    c.drawArc(OVAL, -50, 100, false, p);
                    OVAL.set(7, 4.5f, 22, 19.5f);
                    c.drawArc(OVAL, -50, 100, false, p);
                }
                break;
            case PLAYLIST:
                stroke(p, 2.2f);
                c.drawLine(3, 6, 16, 6, p);
                c.drawLine(3, 11, 16, 11, p);
                c.drawLine(3, 16, 11, 16, p);
                p.setStrokeWidth(1.8f);
                c.drawLine(19.6f, 8, 19.6f, 18.5f, p);
                c.drawLine(19.6f, 8, 22.5f, 9.6f, p);
                p.setStyle(Paint.Style.FILL);
                OVAL.set(14.3f, 16.4f, 20.3f, 20.6f);
                c.drawOval(OVAL, p);
                break;
            case HANDLE:
                stroke(p, 1.8f);
                c.drawLine(4, 8, 20, 8, p);
                c.drawLine(4, 12, 20, 12, p);
                c.drawLine(4, 16, 20, 16, p);
                break;
            case REMOVE:
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(12, 12, 10, p);
                p.setColor(0xffffffff);
                stroke(p, 2.4f);
                p.setStrokeCap(Paint.Cap.SQUARE);
                c.drawLine(7, 12, 17, 12, p);
                break;
            case CHECK:
                stroke(p, 2.4f);
                path.moveTo(5, 12.5f);
                path.lineTo(10, 17.5f);
                path.lineTo(19, 7);
                c.drawPath(path, p);
                break;
            default:
                break;
        }
        c.restore();
    }

    private static void stroke(Paint p, float width) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(width);
    }

    /** Filled shapes with slightly rounded corners. */
    private static void fillRounded(Paint p) {
        p.setStyle(Paint.Style.FILL_AND_STROKE);
        p.setStrokeWidth(1.5f);
    }
}
