package com.grapefruitmusic.ui;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.*;

/** One movable picture surface with letterboxing and the TV app's caption overlay. */
final class TvVideoView extends FrameLayout {
    final TextureView texture;
    final TextView captions;
    private float ratio=16f/9f;
    TvVideoView(Context c,TvStyle b){
        super(c);setBackgroundColor(Color.BLACK);
        texture=new TextureView(c);addView(texture,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));
        captions=b.text("",15,false);captions.setTextColor(Color.WHITE);captions.setGravity(Gravity.CENTER);
        captions.setPadding(b.dp(10),b.dp(5),b.dp(10),b.dp(5));captions.setBackground(b.shape(0xc0000000,12,false));
        FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
        cp.leftMargin=cp.rightMargin=b.dp(12);cp.bottomMargin=b.dp(12);addView(captions,cp);captions.setVisibility(GONE);
    }
    void update(TvSession s){
        if(Math.abs(ratio-s.aspect)>0.001f){ratio=s.aspect>0?s.aspect:16f/9f;requestLayout();}
        captions.setText(s.captions);captions.setVisibility(s.captions.length()>0?VISIBLE:GONE);
    }
    @Override protected void onMeasure(int ws,int hs){
        int w=MeasureSpec.getSize(ws),h=MeasureSpec.getSize(hs);
        if(MeasureSpec.getMode(hs)!=MeasureSpec.EXACTLY)h=Math.min(h==0?Integer.MAX_VALUE:h,Math.round(w*9f/16f));
        super.onMeasure(MeasureSpec.makeMeasureSpec(w,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(h,MeasureSpec.EXACTLY));
        int vw=w,vh=Math.round(w/ratio);if(vh>h){vh=h;vw=Math.round(h*ratio);}
        texture.measure(MeasureSpec.makeMeasureSpec(vw,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(vh,MeasureSpec.EXACTLY));
    }
}
