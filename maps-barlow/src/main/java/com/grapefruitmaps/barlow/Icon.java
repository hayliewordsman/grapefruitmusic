// SPDX-License-Identifier: GPL-3.0-or-later
package com.grapefruitmaps.barlow;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Drawn toolbar, rail and map-control icons: no icon font, vector XML or bitmap packs. */
final class Icon extends Drawable {
    enum Kind { MAP, STREET, DIRECTIONS, SETTINGS, PLUS, MINUS, LOCATE, LAYERS, SEARCH, TURN_LEFT, TURN_RIGHT }
    private final Paint stroke=new Paint(Paint.ANTI_ALIAS_FLAG), fill=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path=new Path();
    private final Kind kind;
    private final int size;
    Icon(Kind kind,int color,int size) {
        this.kind=kind;this.size=size;
        stroke.setColor(color);stroke.setStyle(Paint.Style.STROKE);stroke.setStrokeWidth(Math.max(1.5f,size*.09f));
        stroke.setStrokeCap(Paint.Cap.ROUND);stroke.setStrokeJoin(Paint.Join.ROUND);
        fill.setColor(color);fill.setStyle(Paint.Style.FILL);
    }
    Kind kind(){return kind;}
    @Override public void draw(Canvas c) {
        Rect b=getBounds();float s=Math.min(b.width(),b.height()),x=b.exactCenterX()-s/2,y=b.exactCenterY()-s/2;
        c.save();c.translate(x,y);c.scale(s/24f,s/24f);
        float w=stroke.getStrokeWidth();stroke.setStrokeWidth(w*24f/s);
        path.reset();
        switch(kind) {
            case MAP: // folded map
                path.moveTo(3,6);path.lineTo(9,4);path.lineTo(15,6);path.lineTo(21,4);path.lineTo(21,18);path.lineTo(15,20);path.lineTo(9,18);path.lineTo(3,20);path.close();
                path.moveTo(9,4);path.lineTo(9,18);path.moveTo(15,6);path.lineTo(15,20);c.drawPath(path,stroke);break;
            case STREET: // person on a ground ring (Street View)
                c.drawCircle(12,5,2.6f,fill);
                path.moveTo(8.5f,15);path.lineTo(8.5f,10.5f);path.quadTo(8.5f,9,10,9);path.lineTo(14,9);path.quadTo(15.5f,9,15.5f,10.5f);path.lineTo(15.5f,15);path.close();c.drawPath(path,fill);
                c.drawRect(10.3f,14,13.7f,18.5f,fill);
                c.drawOval(new RectF(4,17.5f,20,22.5f),stroke);break;
            case DIRECTIONS: // turn arrow in a diamond
                path.moveTo(12,2.5f);path.lineTo(21.5f,12);path.lineTo(12,21.5f);path.lineTo(2.5f,12);path.close();c.drawPath(path,stroke);
                path.reset();path.moveTo(9,15.5f);path.lineTo(9,11.5f);path.quadTo(9,10,10.5f,10);path.lineTo(15,10);
                path.moveTo(13,7.8f);path.lineTo(15.2f,10);path.lineTo(13,12.2f);c.drawPath(path,stroke);break;
            case SETTINGS: { // gear
                for(int i=0;i<8;i++){c.save();c.rotate(i*45,12,12);c.drawRoundRect(new RectF(10.4f,1.8f,13.6f,6),1,1,fill);c.restore();}
                c.drawCircle(12,12,6.6f,stroke);c.drawCircle(12,12,2.6f,stroke);break;
            }
            case PLUS: path.moveTo(12,5);path.lineTo(12,19);path.moveTo(5,12);path.lineTo(19,12);c.drawPath(path,stroke);break;
            case MINUS: path.moveTo(5,12);path.lineTo(19,12);c.drawPath(path,stroke);break;
            case LOCATE: // crosshair
                c.drawCircle(12,12,6.5f,stroke);c.drawCircle(12,12,2.4f,fill);
                path.moveTo(12,2);path.lineTo(12,5);path.moveTo(12,19);path.lineTo(12,22);path.moveTo(2,12);path.lineTo(5,12);path.moveTo(19,12);path.lineTo(22,12);c.drawPath(path,stroke);break;
            case LAYERS: // stacked sheets (map type)
                path.moveTo(12,3.5f);path.lineTo(21,8.5f);path.lineTo(12,13.5f);path.lineTo(3,8.5f);path.close();
                path.moveTo(3,12.5f);path.lineTo(12,17.5f);path.lineTo(21,12.5f);path.moveTo(3,16.5f);path.lineTo(12,21.5f);path.lineTo(21,16.5f);c.drawPath(path,stroke);break;
            case TURN_LEFT: case TURN_RIGHT: { // look around: an arc with an arrowhead
                if(kind==Kind.TURN_RIGHT){c.scale(-1,1,12,12);}
                path.addArc(new RectF(5,5,19,19),200,250);
                path.moveTo(5.6f,5.2f);path.lineTo(5.4f,10.4f);path.lineTo(10.4f,9.6f);c.drawPath(path,stroke);break;
            }
            case SEARCH: c.drawCircle(10.5f,10.5f,6,stroke);path.moveTo(15,15);path.lineTo(20.5f,20.5f);c.drawPath(path,stroke);break;
        }
        stroke.setStrokeWidth(w);c.restore();
    }
    @Override public int getIntrinsicWidth(){return size;}
    @Override public int getIntrinsicHeight(){return size;}
    @Override public void setAlpha(int alpha){stroke.setAlpha(alpha);fill.setAlpha(alpha);invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){stroke.setColorFilter(filter);fill.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
