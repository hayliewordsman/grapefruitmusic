package com.grapefruitmusic.ui;

import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import com.grapefruitmusic.R;
import com.grapefruitmusic.ui.widget.IconView;
import com.grapefruitmusic.ui.widget.Icons;

/** A movable 16:9 TV picture, snapped to a corner above the tabs on the existing player. */
final class TvMini extends FrameLayout {
    final BlueTelevision tv;
    final TvVideoView video;
    final TextView title,status,live;
    final IconView pause,close;
    private View boundsView,bars;
    private boolean dragging;
    private float downX,downY,fromX,fromY;
    private final int slop;
    private final View.OnLayoutChangeListener layout=(v,l,t,r,b,ol,ot,or,ob)->place(false);

    TvMini(BlueTelevision tv){
        super(tv.host);this.tv=tv;TvStyle b=tv.style;slop=ViewConfiguration.get(tv.host).getScaledTouchSlop();
        setBackgroundColor(Color.BLACK);setForeground(b.shape(Color.TRANSPARENT,14,true));setClickable(true);setFocusable(true);
        video=new TvVideoView(tv.host,b);video.captions.setTextSize(10);video.captions.setMaxLines(2);video.captions.setEllipsize(TextUtils.TruncateAt.END);((FrameLayout.LayoutParams)video.captions.getLayoutParams()).bottomMargin=b.dp(48);addView(video,new FrameLayout.LayoutParams(-1,-1));
        // Clip the picture on new Android; paint the corners on 4.x, matching the TV app.
        if(Build.VERSION.SDK_INT>=21)Modern.clip(this,b.dp(14));
        View fade=new View(tv.host);fade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0,0xd9000000}));
        addView(fade,new FrameLayout.LayoutParams(-1,b.dp(52),Gravity.BOTTOM));
        title=b.text("",12,true);title.setTextColor(Color.WHITE);title.setSingleLine();title.setEllipsize(TextUtils.TruncateAt.END);
        FrameLayout.LayoutParams tp=new FrameLayout.LayoutParams(-1,b.dp(44),Gravity.BOTTOM);tp.leftMargin=b.dp(12);tp.rightMargin=b.dp(60);tp.bottomMargin=b.dp(4);addView(title,tp);
        status=b.label("",12,Color.WHITE);status.setGravity(Gravity.CENTER);status.setMaxLines(3);status.setEllipsize(TextUtils.TruncateAt.END);status.setPadding(b.dp(12),0,b.dp(12),0);
        FrameLayout.LayoutParams sp=new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER);sp.leftMargin=sp.rightMargin=b.dp(12);addView(status,sp);
        live=b.text("LIVE",10,true);live.setTextColor(b.onAccent);live.setBackground(b.gradient(TvStyle.PILL));live.setPadding(b.dp(8),b.dp(4),b.dp(8),b.dp(4));
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.LEFT);lp.topMargin=lp.leftMargin=b.dp(10);addView(live,lp);
        pause=control(Icons.PAUSE,"Pause TV",()->tv.session.toggle());FrameLayout.LayoutParams pp=new FrameLayout.LayoutParams(b.dp(44),b.dp(44),Gravity.BOTTOM|Gravity.RIGHT);pp.bottomMargin=pp.rightMargin=b.dp(4);addView(pause,pp);
        close=control(Icons.CLOSE,"Stop TV",()->tv.session.stop());FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(b.dp(44),b.dp(44),Gravity.TOP|Gravity.RIGHT);cp.topMargin=cp.rightMargin=b.dp(4);addView(close,cp);
        if(Build.VERSION.SDK_INT<21)addView(new Corners(tv,b.dp(14)),new FrameLayout.LayoutParams(-1,-1));
        setOnClickListener(v->tv.host.showTvNowPlaying());b.lift(this,10);
    }
    private IconView control(int icon,String label,Runnable action){
        IconView button=new IconView(getContext());button.setIcon(icon);button.setColor(Color.WHITE);int pad=tv.style.dp(12);button.setPadding(pad,pad,pad,pad);
        button.setBackground(tv.style.circle(0x99000000));button.setContentDescription(label);button.setFocusable(true);button.setOnClickListener(v->action.run());return button;
    }
    @Override protected void onAttachedToWindow(){
        super.onAttachedToWindow();boundsView=(View)getParent();bars=tv.host.findViewById(R.id.bottom_bars);boundsView.addOnLayoutChangeListener(layout);bars.addOnLayoutChangeListener(layout);post(()->place(false));
    }
    @Override protected void onDetachedFromWindow(){
        if(boundsView!=null)boundsView.removeOnLayoutChangeListener(layout);if(bars!=null)bars.removeOnLayoutChangeListener(layout);boundsView=null;bars=null;super.onDetachedFromWindow();
    }
    @Override protected void onVisibilityChanged(View changed,int visibility){super.onVisibilityChanged(changed,visibility);if(visibility==VISIBLE&&tv!=null)post(()->place(false));}
    /** Size and position are relative to the content column, which already excludes the side rail. */
    void place(boolean animate){
        if(boundsView==null||boundsView.getWidth()==0||dragging)return;
        int available=boundsView.getWidth(),margin=tv.style.dp(12);
        int width=Math.min(tv.style.dp(280),Math.max(tv.style.dp(184),Math.round(available*0.52f)));
        width=Math.min(width,Math.max(0,available-2*margin));
        int ceiling=bars!=null&&bars.getHeight()>0?bars.getTop():boundsView.getHeight();
        int height=Math.round(width*9f/16f);
        if(height>ceiling-2*margin){height=Math.max(1,ceiling-2*margin);width=Math.round(height*16f/9f);}
        FrameLayout.LayoutParams params=(FrameLayout.LayoutParams)getLayoutParams();
        if(params.width!=width||params.height!=height){params.width=width;params.height=height;setLayoutParams(params);}
        float right=Math.max(margin,available-margin-width),bottom=Math.max(margin,ceiling-margin-height);
        float x=tv.repo.prefs.getBoolean("miniLeft",false)?margin:right;
        float fraction=Math.max(0,Math.min(1,tv.repo.prefs.getFloat("miniY",1)));
        float y=margin+(bottom-margin)*fraction;
        if(animate)animate().translationX(x).translationY(y).setDuration(180).start();else{animate().cancel();setTranslationX(x);setTranslationY(y);}
    }
    private void begin(MotionEvent e){downX=e.getRawX();downY=e.getRawY();fromX=getTranslationX();fromY=getTranslationY();dragging=false;animate().cancel();}
    @Override public boolean onInterceptTouchEvent(MotionEvent e){
        if(e.getActionMasked()==MotionEvent.ACTION_DOWN)begin(e);
        if(e.getActionMasked()==MotionEvent.ACTION_MOVE&&Math.hypot(e.getRawX()-downX,e.getRawY()-downY)>slop){dragging=true;getParent().requestDisallowInterceptTouchEvent(true);return true;}
        return super.onInterceptTouchEvent(e);
    }
    @Override public boolean onTouchEvent(MotionEvent e){
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:begin(e);setPressed(true);return true;
            case MotionEvent.ACTION_MOVE:
                if(Math.hypot(e.getRawX()-downX,e.getRawY()-downY)>slop)dragging=true;
                if(dragging){setPressed(false);setTranslationX(fromX+e.getRawX()-downX);setTranslationY(fromY+e.getRawY()-downY);}return true;
            case MotionEvent.ACTION_UP:
                setPressed(false);if(!dragging){performClick();return true;}
                saveCorner();dragging=false;place(true);return true;
            case MotionEvent.ACTION_CANCEL:setPressed(false);dragging=false;place(false);return true;
            default:return super.onTouchEvent(e);
        }
    }
    private void saveCorner(){
        if(boundsView==null)return;int margin=tv.style.dp(12),ceiling=bars!=null&&bars.getHeight()>0?bars.getTop():boundsView.getHeight();
        float bottom=Math.max(margin,ceiling-margin-getHeight()),fraction=bottom>margin?(getTranslationY()-margin)/(bottom-margin):1;
        tv.repo.prefs.edit().putBoolean("miniLeft",getTranslationX()+getWidth()/2f<boundsView.getWidth()/2f).putFloat("miniY",Math.max(0,Math.min(1,fraction))).apply();
    }
    void update(){
        TvSession s=tv.session;if(s.current==null)return;
        title.setText(s.current.name);setContentDescription("Floating TV player: "+s.current.name+". Tap to open.");
        boolean ready="LIVE".equals(s.status)||"PLAYING".equals(s.status);
        status.setText(s.status);status.setVisibility(ready?GONE:VISIBLE);live.setVisibility(s.playing()&&s.current.live()&&ready?VISIBLE:GONE);
        pause.setIcon(s.playing()?Icons.PAUSE:Icons.PLAY);pause.setContentDescription(s.playing()?"Pause TV":"Play TV");video.update(s);
        if(!s.watchVisible&&s.active())s.attach(video.texture);
    }
    @android.annotation.TargetApi(21) private static final class Modern {
        static void clip(View v,int radius){v.setOutlineProvider(new ViewOutlineProvider(){public void getOutline(View view,Outline outline){outline.setRoundRect(0,0,view.getWidth(),view.getHeight(),radius);}});v.setClipToOutline(true);}
    }
    private static final class Corners extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final Path path=new Path();private final int radius;
        Corners(BlueTelevision tv,int radius){super(tv.host);this.radius=radius;paint.setColor(tv.style.bg);}
        @Override protected void onSizeChanged(int w,int h,int ow,int oh){path.reset();path.setFillType(Path.FillType.EVEN_ODD);path.addRect(0,0,w,h,Path.Direction.CW);path.addRoundRect(new RectF(0,0,w,h),radius,radius,Path.Direction.CW);}
        @Override protected void onDraw(Canvas c){c.drawPath(path,paint);}
    }
}
