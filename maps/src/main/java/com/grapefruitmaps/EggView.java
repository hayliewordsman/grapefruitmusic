// SPDX-License-Identifier: GPL-3.0-or-later
package com.grapefruitmaps;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import java.util.*;

/** One canvas keeps the bundled caricature, mouth and flying emoji in one coordinate space. */
final class EggView extends View {
    static final long FLIGHT_MS=850;
    private static final float MOUTH_X=.844f,MOUTH_Y=.688f;
    private static final class Drop {
        final float x,y;
        final long born=SystemClock.uptimeMillis();
        Drop(float x,float y){this.x=x;this.y=y;}
    }
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint spritePaint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final ArrayList<Drop> drops=new ArrayList<>();
    private final Path map=new Path();
    private final PointF flying=new PointF();
    private final Bitmap head;
    private final RectF headBounds=new RectF();
    private final Random random=new Random();
    private final Brand brand;
    private final float density;
    private float mapScale,mapLeft,mapTop,mouthX,mouthY;
    private boolean touchingHead;
    int count;

    private static final RectF SEA=new RectF(-3,-3,103,63), BASE=new RectF(-8,0,8,7), MIDDLE=new RectF(-6,-4,6,3), TOP=new RectF(-4,-7,4,0), LEFT_EYE=new RectF(-4,-1,-.5f,3), RIGHT_EYE=new RectF(.5f,-1,4,3), SMILE=new RectF(-2,2,2,5);
    private static final Path CURL=new Path();
    static {CURL.moveTo(-3,-4);CURL.cubicTo(-3,-10,5,-7,1,-12);CURL.cubicTo(10,-7,6,-3,2,-3);CURL.close();}
    // Original simplified mainland US outline, normalized to 100 x 60.
    private static final float[] USA={2,4,20,6,38,6,55,5,60,8,65,6,69,10,73,8,76,12,79,10,82,8,85,8,88,3,92,1,94,9,99,12,96,18,91,20,88,25,87,31,83,36,80,41,83,50,82,57,79,59,75,50,74,44,67,43,62,44,59,42,54,44,50,40,46,41,43,49,38,48,34,44,30,40,27,39,20,35,13,32,9,27,6,23,4,15};

    EggView(Context context,Brand brand) {
        super(context);this.brand=brand;density=getResources().getDisplayMetrics().density;
        head=BitmapFactory.decodeResource(getResources(),R.drawable.victory_head);
        map.moveTo(USA[0],USA[1]);for(int i=2;i<USA.length;i+=2)map.lineTo(USA[i],USA[i+1]);map.close();
        setClickable(true);setFocusable(true);description();
    }
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh) {
        float stage=Math.min(148*density,h*.48f),size=Math.min(w*.55f,stage-4*density);
        headBounds.set((w-size)/2,(stage-size)/2,(w+size)/2,(stage+size)/2);
        mouthX=headBounds.left+headBounds.width()*MOUTH_X;
        mouthY=headBounds.top+headBounds.height()*MOUTH_Y;
        mapScale=Math.max(.01f,Math.min(w/108f,(h-stage)/68f));
        mapLeft=(w-100*mapScale)/2;mapTop=stage+(h-stage-60*mapScale)/2;
    }
    void drop() {
        float x,y;do{x=5+random.nextFloat()*89;y=5+random.nextFloat()*48;}while(!inside(x,y));
        if(drops.size()==100)drops.remove(0);
        drops.add(new Drop(x,y));count++;description();invalidate();
    }
    void clear(){drops.clear();count=0;description();invalidate();}
    private void description(){setContentDescription("Victory Break. Cartoon Donald Trump in a red cap. Tap the head to drop poop from his mouth onto America. "+count+(count==1?" drop.":" drops."));}
    private static boolean inside(float x,float y) {
        boolean hit=false;
        for(int i=0,j=USA.length-2;i<USA.length;j=i,i+=2)
            if((USA[i+1]>y)!=(USA[j+1]>y)&&x<(USA[j]-USA[i])*(y-USA[i+1])/(USA[j+1]-USA[i+1])+USA[i])hit=!hit;
        return hit;
    }
    /** Actual render trajectory, also sampled by the animation checks. */
    void position(int index,long elapsed,PointF out) {
        Drop drop=drops.get(index);
        float t=Math.max(0,Math.min(1,elapsed/(float)FLIGHT_MS)),u=1-t;
        float x=mapLeft+drop.x*mapScale,y=mapTop+drop.y*mapScale;
        float firstX=Math.min(getWidth()-10*density,mouthX+48*density),firstY=mouthY-16*density;
        float secondX=x+(x>=mouthX?12:-8)*density,secondY=y-36*density;
        out.set(u*u*u*mouthX+3*u*u*t*firstX+3*u*t*t*secondX+t*t*t*x,
                u*u*u*mouthY+3*u*u*t*firstY+3*u*t*t*secondY+t*t*t*y);
    }
    float mouthX(){return mouthX;}
    float mouthY(){return mouthY;}
    boolean onLand(PointF p){return inside((p.x-mapLeft)/mapScale,(p.y-mapTop)/mapScale);}
    int retainedDrops(){return drops.size();}
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        spritePaint.setAlpha(isPressed()?230:255);
        if(head!=null)canvas.drawBitmap(head,null,headBounds,spritePaint);
        canvas.save();canvas.translate(mapLeft,mapTop);canvas.scale(mapScale,mapScale);
        paint.setColor(0xff102541);canvas.drawRoundRect(SEA,5,5,paint);
        paint.setColor(0xff163250);paint.setStrokeWidth(.25f);
        for(int i=0;i<=100;i+=10)canvas.drawLine(i,0,i,60,paint);
        for(int i=0;i<=60;i+=10)canvas.drawLine(0,i,100,i,paint);
        paint.setColor(0xffBDDDF6);canvas.drawPath(map,paint);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(.6f);paint.setColor(brand.accent);canvas.drawPath(map,paint);paint.setStyle(Paint.Style.FILL);
        canvas.restore();
        boolean flyingNow=false;long now=SystemClock.uptimeMillis();
        for(int i=0;i<drops.size();i++) {
            long age=now-drops.get(i).born;float t=Math.max(0,Math.min(1,age/(float)FLIGHT_MS));
            if(age<FLIGHT_MS)flyingNow=true;
            position(i,age,flying);
            float size=(.45f+.5f*t)*density;
            canvas.save();canvas.translate(flying.x,flying.y);canvas.scale(size,size);poop(canvas,paint);canvas.restore();
        }
        if(flyingNow)postInvalidateDelayed(16);
    }
    static void poop(Canvas canvas,Paint p) {
        p.setColor(0xff78432d);canvas.drawOval(BASE,p);canvas.drawOval(MIDDLE,p);canvas.drawOval(TOP,p);canvas.drawPath(CURL,p);
        p.setColor(Color.WHITE);canvas.drawOval(LEFT_EYE,p);canvas.drawOval(RIGHT_EYE,p);
        p.setColor(0xff251713);canvas.drawCircle(-2,1,.8f,p);canvas.drawCircle(2,1,.8f,p);
        p.setColor(Color.WHITE);canvas.drawArc(SMILE,0,180,true,p);
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if(!isEnabled())return false;
        switch(event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                touchingHead=headBounds.contains(event.getX(),event.getY());setPressed(touchingHead);return touchingHead;
            case MotionEvent.ACTION_MOVE:
                if(!touchingHead)return false;
                setPressed(headBounds.contains(event.getX(),event.getY()));return true;
            case MotionEvent.ACTION_UP:
                if(!touchingHead)return false;
                boolean click=isPressed()&&headBounds.contains(event.getX(),event.getY());touchingHead=false;setPressed(false);
                if(click)performClick();return true;
            case MotionEvent.ACTION_CANCEL:
                touchingHead=false;setPressed(false);return true;
            default:return touchingHead;
        }
    }
    @Override public boolean performClick(){return super.performClick();}
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);info.setClassName(Button.class.getName());}
}
