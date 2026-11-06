package com.grapefruitsettings;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** A real chevron, as in Maps: it does not depend on the installed font's glyphs. */
final class BackIcon extends Drawable {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path=new Path();
    private final int size;
    BackIcon(int color,int pixels){size=pixels;paint.setColor(color);paint.setStyle(Paint.Style.STROKE);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);}
    @Override public void draw(Canvas canvas){Rect b=getBounds();float s=Math.min(b.width(),b.height());paint.setStrokeWidth(Math.max(2,s/10));path.reset();path.moveTo(b.left+s*.64f,b.top+s*.22f);path.lineTo(b.left+s*.36f,b.top+s*.5f);path.lineTo(b.left+s*.64f,b.top+s*.78f);canvas.drawPath(path,paint);}
    @Override public int getIntrinsicWidth(){return size;}
    @Override public int getIntrinsicHeight(){return size;}
    @Override public void setAlpha(int alpha){paint.setAlpha(alpha);invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
