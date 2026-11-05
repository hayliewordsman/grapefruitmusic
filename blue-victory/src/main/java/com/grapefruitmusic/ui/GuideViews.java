package com.grapefruitmusic.ui;
import android.content.Context;
import android.graphics.*;
import android.text.TextPaint;
import android.view.*;
import android.widget.ListView;
import com.grapefruitmusic.ui.Fonts;
import java.util.*;

/**
 * Live TV's guide: each channel's row, with the channel's name on the left and its programmes laid out in
 * time beside it, the times along the top, and a list that scrolls up and down through channels and,
 * dragged sideways, through time. Drawn in code (one view per row), so it stays smooth on old phones.
 */
final class GuideViews {
    private GuideViews() {}
    interface Host {
        TvStyle brand();
        Guide guide();
        /** The time at the left edge of the programmes, and how wide a minute is (px). */
        long start(); float minute();
        /** The width of the channel-name column (px). */
        int column();
        Channel playing();
        void scrollBy(float px);
        void tune(Channel c);
        void program(Channel c,Guide.Program p);
        void details(Channel c);
    }

    /** One channel's row of programmes. */
    static final class Row extends View {
        private final Host host;private Channel channel;
        private final Paint fill=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint title=new TextPaint(Paint.ANTI_ALIAS_FLAG),small=new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r=new RectF();
        private java.util.List<Guide.Program> shown=Collections.emptyList();
        private final GestureDetector taps;
        Row(Context c,Host host){
            super(c);this.host=host;
            title.setTypeface(Fonts.medium());small.setTypeface(Fonts.regular());
            setFocusable(true);setClickable(true);
            taps=new GestureDetector(c,new GestureDetector.SimpleOnGestureListener(){
                @Override public boolean onDown(MotionEvent e){return true;}
                @Override public boolean onSingleTapUp(MotionEvent e){tap(e.getX());return true;}
                @Override public void onLongPress(MotionEvent e){if(channel!=null)host.details(channel);}
            });
        }
        void bind(Channel c){channel=c;setContentDescription(c.name);invalidate();}
        Channel channel(){return channel;}
        @Override protected void onMeasure(int w,int h){setMeasuredDimension(MeasureSpec.getSize(w),host.brand().dp(64));}
        @Override public boolean onTouchEvent(MotionEvent e){
            boolean handled=taps.onTouchEvent(e);
            if(e.getActionMasked()==MotionEvent.ACTION_DOWN)setPressed(true);
            if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL)setPressed(false);
            return handled||super.onTouchEvent(e);
        }
        @Override public boolean performClick(){super.performClick();if(channel!=null)host.tune(channel);return true;}
        private void tap(float x){
            if(channel==null)return;
            if(x<host.column()){host.tune(channel);return;}
            long at=host.start()+(long)((x-host.column())/host.minute()*Guide.MINUTE);
            for(Guide.Program p:shown)if(p.start<=at&&at<p.end){host.program(channel,p);return;}
            host.tune(channel);
        }
        @Override protected void onDraw(Canvas c){
            if(channel==null)return;
            TvStyle b=host.brand();int h=getHeight(),col=host.column(),gap=b.dp(3),pad=b.dp(10);
            float radius=b.dp(14);
            long now=System.currentTimeMillis(),start=host.start();float minute=host.minute();
            long end=start+(long)((getWidth()-col)/minute*Guide.MINUTE);
            // The programmes: each a rounded block; what's on now a little brighter; placeholders quieter.
            shown=Guide.fill(channel,host.guide().known(channel),start,end);
            c.save();c.clipRect(col,0,getWidth(),h);
            for(Guide.Program p:shown){
                float left=col+(p.start-start)/(float)Guide.MINUTE*minute,right=col+(p.end-start)/(float)Guide.MINUTE*minute;
                r.set(Math.max(left,col-radius)+gap,gap,right-gap,h-gap);
                boolean on=p.on(now);
                fill.setColor(on?b.pressed:b.control);c.drawRoundRect(r,radius,radius,fill);
                if(on){fill.setStyle(Paint.Style.STROKE);fill.setStrokeWidth(b.dp(1.5f));fill.setColor(b.accent);c.drawRoundRect(r,radius,radius,fill);fill.setStyle(Paint.Style.FILL);}
                float x=Math.max(r.left,col)+pad,room=r.right-pad-x;
                if(room<b.dp(18))continue;
                title.setTextSize(b.dp(13));title.setColor(p.placeholder?b.secondary:b.text);
                small.setTextSize(b.dp(11));small.setColor(b.secondary);
                String name=p.title;
                CharSequence line=fit(name,title,room);
                c.drawText(line,0,line.length(),x,h/2f-b.dp(2),title);
                CharSequence when=fit(times(p),small,room);
                c.drawText(when,0,when.length(),x,h/2f+b.dp(14),small);
            }
            // Now: a line in the accent.
            float nowX=col+(now-start)/(float)Guide.MINUTE*minute;
            if(nowX>=col){fill.setColor(b.accent);c.drawRect(nowX-b.dp(1),0,nowX+b.dp(1),h,fill);}
            c.restore();
            // The channel: its initials on the gradient, then its name.
            float cy=h/2f,rad=b.dp(18);
            Shader s=new LinearGradient(pad,cy-rad,pad+2*rad,cy+rad,b.accent,b.end,Shader.TileMode.CLAMP);
            fill.setShader(s);c.drawCircle(pad+rad,cy,rad,fill);fill.setShader(null);
            String initials=channel.initials();
            title.setTextSize(b.dp(initials.length()>2?10:13));title.setColor(b.onAccent);
            c.drawText(initials,pad+rad-title.measureText(initials)/2,cy+title.getTextSize()/3,title);
            boolean live=host.playing()!=null&&host.playing().id.equals(channel.id);
            title.setTextSize(b.dp(13));title.setColor(live?b.accent:b.text);
            float nx=pad+2*rad+b.dp(8),room=col-nx-b.dp(6);
            // Up to two lines of the name.
            String nm=channel.name;int fit=title.breakText(nm,true,room,null);
            c.save();c.clipRect(0,0,col-b.dp(4),h);
            if(fit<nm.length()){
                int space=nm.lastIndexOf(' ',fit);if(space>0)fit=space;
                c.drawText(nm.substring(0,fit).trim(),nx,cy-b.dp(3),title);
                String rest=fit(nm.substring(fit).trim(),title,room);
                c.drawText(rest,nx,cy+b.dp(13),title);
            } else c.drawText(nm,nx,cy+b.dp(5),title);
            c.restore();
            if(isFocused()||isPressed()){
                fill.setStyle(Paint.Style.STROKE);fill.setStrokeWidth(b.dp(isFocused()?3:1));fill.setColor(b.accent);
                r.set(b.dp(1.5f),b.dp(1.5f),col-b.dp(1.5f),h-b.dp(1.5f));c.drawRoundRect(r,h/2f,h/2f,fill);fill.setStyle(Paint.Style.FILL);
            }
        }
        @Override protected void drawableStateChanged(){super.drawableStateChanged();invalidate();}
        /** As much of text as fits in width, ending in "…" if it was cut. */
        static String fit(String text,Paint p,float width){
            if(p.measureText(text)<=width)return text;
            int n=p.breakText(text,true,Math.max(0,width-p.measureText("…")),null);
            return n<=0?"":text.substring(0,n).trim()+"…";
        }
        private String times(Guide.Program p){
            java.text.DateFormat f=android.text.format.DateFormat.getTimeFormat(getContext());
            return f.format(new Date(p.start))+" – "+f.format(new Date(p.end));
        }
    }

    /** The times along the top of the guide, every half hour. */
    static final class Ruler extends View {
        private final Host host;private final TextPaint text=new TextPaint(Paint.ANTI_ALIAS_FLAG);private final Paint line=new Paint(Paint.ANTI_ALIAS_FLAG);
        Ruler(Context c,Host host){super(c);this.host=host;text.setTypeface(Fonts.medium());}
        @Override protected void onMeasure(int w,int h){setMeasuredDimension(MeasureSpec.getSize(w),host.brand().dp(26));}
        @Override protected void onDraw(Canvas c){
            TvStyle b=host.brand();int col=host.column();float minute=host.minute();long start=host.start();
            text.setTextSize(b.dp(12));text.setColor(b.secondary);line.setColor(b.outline);
            java.text.DateFormat f=android.text.format.DateFormat.getTimeFormat(getContext());
            long end=start+(long)((getWidth()-col)/minute*Guide.MINUTE);
            c.save();c.clipRect(col,0,getWidth(),getHeight());
            for(long t=Guide.ceil(start,30*Guide.MINUTE);t<end;t+=30*Guide.MINUTE){
                float x=col+(t-start)/(float)Guide.MINUTE*minute;
                c.drawRect(x,getHeight()-b.dp(8),x+Math.max(1,b.dp(1)),getHeight(),line);
                c.drawText(f.format(new Date(t)),x+b.dp(5),getHeight()-b.dp(10),text);
            }
            long now=System.currentTimeMillis();float nx=col+(now-start)/(float)Guide.MINUTE*minute;
            if(nx>=col){line.setColor(b.accent);c.drawRect(nx-b.dp(1),getHeight()-b.dp(8),nx+b.dp(1),getHeight(),line);}
            c.restore();
            text.setColor(b.accent);
            String day=new java.text.SimpleDateFormat("EEE d MMM",Locale.getDefault()).format(new Date(Math.max(start,now)));
            c.drawText(day,b.dp(10),getHeight()-b.dp(10),text);
        }
    }

    /** The guide's list: up and down through channels; dragged sideways, through time. */
    static final class List extends ListView {
        private final Host host;private float downX,downY,lastX;private boolean sideways;private final int slop;
        private android.view.VelocityTracker velocity;private final android.widget.Scroller fling;
        List(Context c,Host host){super(c);this.host=host;slop=ViewConfiguration.get(c).getScaledTouchSlop();fling=new android.widget.Scroller(c);}
        @Override public boolean onInterceptTouchEvent(MotionEvent e){
            switch(e.getActionMasked()){
                case MotionEvent.ACTION_DOWN:downX=lastX=e.getX();downY=e.getY();sideways=false;fling.forceFinished(true);break;
                case MotionEvent.ACTION_MOVE:
                    if(!sideways&&Math.abs(e.getX()-downX)>slop&&Math.abs(e.getX()-downX)>Math.abs(e.getY()-downY)*1.5f){sideways=true;lastX=e.getX();return true;}
                    break;
            }
            return !sideways&&super.onInterceptTouchEvent(e);
        }
        @Override public boolean onTouchEvent(MotionEvent e){
            if(!sideways)return super.onTouchEvent(e);
            if(velocity==null)velocity=android.view.VelocityTracker.obtain();velocity.addMovement(e);
            switch(e.getActionMasked()){
                case MotionEvent.ACTION_MOVE:host.scrollBy(lastX-e.getX());lastX=e.getX();break;
                case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:
                    velocity.computeCurrentVelocity(1000);float vx=velocity.getXVelocity();velocity.recycle();velocity=null;sideways=false;
                    fling.fling(0,0,(int)-vx,0,-100000,100000,0,0);lastFling=0;post(flinging);break;
            }
            return true;
        }
        private int lastFling;
        private final Runnable flinging=new Runnable(){public void run(){
            if(fling.computeScrollOffset()){host.scrollBy(fling.getCurrX()-lastFling);lastFling=fling.getCurrX();post(this);}
        }};
    }
}
