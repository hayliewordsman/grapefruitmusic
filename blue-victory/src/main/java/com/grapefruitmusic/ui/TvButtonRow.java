package com.grapefruitmusic.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/** TV actions wrap with equal gaps, so every button remains readable on narrow screens. */
final class TvButtonRow extends ViewGroup {
    private final int gap;
    TvButtonRow(Context context){super(context);gap=Ui.dp(context,8);setClipToPadding(false);}
    @Override protected LayoutParams generateDefaultLayoutParams(){return new LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT);}
    @Override protected void onMeasure(int ws,int hs){
        int mode=MeasureSpec.getMode(ws),limit=mode==MeasureSpec.UNSPECIFIED?Integer.MAX_VALUE/4:Math.max(0,MeasureSpec.getSize(ws)-getPaddingLeft()-getPaddingRight());
        int x=0,y=0,lineHeight=0,widest=0;
        for(int n=0;n<getChildCount();n++){
            View child=getChildAt(n);if(child.getVisibility()==GONE)continue;
            child.measure(MeasureSpec.makeMeasureSpec(limit,MeasureSpec.AT_MOST),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));
            int width=child.getMeasuredWidth(),height=child.getMeasuredHeight();
            if(x>0&&x+gap+width>limit){widest=Math.max(widest,x);y+=lineHeight+gap;x=0;lineHeight=0;}
            if(x>0)x+=gap;x+=width;lineHeight=Math.max(lineHeight,height);
        }
        widest=Math.max(widest,x);
        int wantedWidth=widest+getPaddingLeft()+getPaddingRight();
        setMeasuredDimension(resolveSize(wantedWidth,ws),resolveSize(y+lineHeight+getPaddingTop()+getPaddingBottom(),hs));
    }
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
        int limit=getWidth()-getPaddingRight(),left=getPaddingLeft(),x=left,y=getPaddingTop(),lineHeight=0;
        for(int n=0;n<getChildCount();n++){
            View child=getChildAt(n);if(child.getVisibility()==GONE)continue;
            int width=child.getMeasuredWidth(),height=child.getMeasuredHeight();
            if(x>left&&x+gap+width>limit){y+=lineHeight+gap;x=left;lineHeight=0;}
            if(x>left)x+=gap;child.layout(x,y,x+width,y+height);x+=width;lineHeight=Math.max(lineHeight,height);
        }
    }
}
