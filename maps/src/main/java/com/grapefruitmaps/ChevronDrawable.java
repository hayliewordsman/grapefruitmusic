// SPDX-License-Identifier: GPL-3.0-or-later
package com.grapefruitmaps;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/** Drawn navigation icon: no Unicode fallback, vector XML or system font dependency. */
final class ChevronDrawable extends Drawable {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path=new Path();
    private final int size;
    private final boolean down;

    ChevronDrawable(int color,int size,boolean down) {
        this.size=size;this.down=down;
        paint.setColor(color);paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(size*.13f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);
    }
    @Override protected void onBoundsChange(Rect b) {
        float cx=b.exactCenterX(),cy=b.exactCenterY(),r=Math.min(b.width(),b.height());
        path.reset();
        if(down){path.moveTo(cx-r*.25f,cy-r*.12f);path.lineTo(cx,cy+r*.13f);path.lineTo(cx+r*.25f,cy-r*.12f);}
        else{path.moveTo(cx+r*.12f,cy-r*.25f);path.lineTo(cx-r*.13f,cy);path.lineTo(cx+r*.12f,cy+r*.25f);}
    }
    @Override public void draw(Canvas canvas){canvas.drawPath(path,paint);}
    @Override public int getIntrinsicWidth(){return size;}
    @Override public int getIntrinsicHeight(){return size;}
    @Override public void setAlpha(int alpha){paint.setAlpha(alpha);invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
