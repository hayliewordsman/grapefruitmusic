// SPDX-License-Identifier: GPL-3.0-or-later
package com.grapefruitmaps;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;
import java.util.*;

/** Original Canvas artwork: no Unicode emoji, emoji fonts, downloads or external images. */
final class EggView extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final class Drop { final float x,y; final long born=SystemClock.uptimeMillis(); Drop(float x,float y){this.x=x;this.y=y;} }
    private final ArrayList<Drop> drops=new ArrayList<>();
    private final Path map=new Path();
    private static final RectF SEA=new RectF(-3,-3,103,63), BASE=new RectF(-8,0,8,7), MIDDLE=new RectF(-6,-4,6,3), TOP=new RectF(-4,-7,4,0), LEFT_EYE=new RectF(-4,-1,-.5f,3), RIGHT_EYE=new RectF(.5f,-1,4,3), SMILE=new RectF(-2,2,2,5);
    private static final Path CURL=new Path();
    static { CURL.moveTo(-3,-4);CURL.cubicTo(-3,-10,5,-7,1,-12);CURL.cubicTo(10,-7,6,-3,2,-3);CURL.close(); }
    private final Random random=new Random();
    private final Brand brand;
    int count;
    // Original simplified outline of the contiguous United States, normalized to 100 x 60.
    private static final float[] USA={2,4,20,6,38,6,55,5,60,8,65,6,69,10,73,8,76,12,79,10,82,8,85,8,88,3,92,1,94,9,99,12,96,18,91,20,88,25,87,31,83,36,80,41,83,50,82,57,79,59,75,50,74,44,67,43,62,44,59,42,54,44,50,40,46,41,43,49,38,48,34,44,30,40,27,39,20,35,13,32,9,27,6,23,4,15};
    EggView(Context c, Brand b) { super(c); brand=b; setContentDescription("Map of America. No drops yet."); map.moveTo(USA[0],USA[1]);for(int i=2;i<USA.length;i+=2)map.lineTo(USA[i],USA[i+1]);map.close(); }
    void drop() {
        // Only select positions inside the mainland silhouette; keep memory bounded on old phones.
        float x,y; do { x=5+random.nextFloat()*89; y=5+random.nextFloat()*48; } while(!inside(x,y));
        if(drops.size()==100) drops.remove(0);
        drops.add(new Drop(x,y)); count++;
        setContentDescription("Map of America. "+count+" poop drops."); invalidate();
    }
    void clear() { drops.clear(); count=0; invalidate(); }
    private static boolean inside(float x,float y) {
        boolean hit=false;
        for(int i=0,j=USA.length-2;i<USA.length;j=i,i+=2) {
            if((USA[i+1]>y)!=(USA[j+1]>y) && x<(USA[j]-USA[i])*(y-USA[i+1])/(USA[j+1]-USA[i+1])+USA[i]) hit=!hit;
        }
        return hit;
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float scale=Math.min(getWidth()/108f,getHeight()/68f), left=(getWidth()-100*scale)/2, top=(getHeight()-60*scale)/2;
        canvas.save(); canvas.translate(left,top); canvas.scale(scale,scale);
        p.setColor(0xff102541); canvas.drawRoundRect(SEA,5,5,p);
        p.setColor(0xff163250); p.setStrokeWidth(.25f);
        for(int i=0;i<=100;i+=10) canvas.drawLine(i,0,i,60,p);
        for(int i=0;i<=60;i+=10) canvas.drawLine(0,i,100,i,p);
        p.setColor(0xffBDDDF6); canvas.drawPath(map,p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(.6f); p.setColor(brand.accent); canvas.drawPath(map,p); p.setStyle(Paint.Style.FILL);
        boolean falling=false;
        for(Drop d:drops) {
            float age=(SystemClock.uptimeMillis()-d.born)/650f; if(age<1) falling=true;
            canvas.save(); canvas.translate(d.x,d.y-(1-Math.min(1,age))*40); canvas.scale(.32f,.32f); poop(canvas,p); canvas.restore();
        }
        canvas.restore(); if(falling) postInvalidateDelayed(16);
    }
    static void poop(Canvas c,Paint p) {
        p.setColor(0xff78432d);
        c.drawOval(BASE,p); c.drawOval(MIDDLE,p); c.drawOval(TOP,p); c.drawPath(CURL,p);
        p.setColor(Color.WHITE); c.drawOval(LEFT_EYE,p); c.drawOval(RIGHT_EYE,p);
        p.setColor(0xff251713); c.drawCircle(-2,1,.8f,p); c.drawCircle(2,1,.8f,p);
        p.setColor(Color.WHITE); c.drawArc(SMILE,0,180,true,p);
    }
    static final class Hat extends View {
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF dome=new RectF(-35,-28,35,32), panel=new RectF(-33,-28,14,32), brim=new RectF(-38,-3,54,14), edge=new RectF(-38,5,54,15);
        Hat(Context c) { super(c); setContentDescription("Red baseball cap. Tap to drop poop on America."); setFocusable(true); }
        @Override protected void onDraw(Canvas c) {
            float scale=Math.min(getWidth()/130f,getHeight()/85f); c.save(); c.translate(getWidth()/2f,getHeight()/2f); c.scale(scale,scale);
            p.setColor(0xffb61e2e); c.drawArc(dome,180,180,true,p);
            p.setColor(0xffee3546); c.drawArc(panel,180,180,true,p);
            p.setColor(0xffd42638); c.drawOval(brim,p);
            p.setColor(0xff9e1a2a); c.drawOval(edge,p);
            p.setColor(0xffff8990); p.setStrokeWidth(1); c.drawLine(-8,-25,-8,-2,p); c.drawCircle(-7,-28,3,p); c.restore();
        }
    }
}
