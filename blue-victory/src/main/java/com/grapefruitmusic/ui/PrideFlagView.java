package com.grapefruitmusic.ui;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Compiled flag artwork: no emoji font, network image or Android vector support is required. */
final class PrideFlagView extends View {
    static final String RAINBOW="rainbow",BISEXUAL="bisexual",TRANSGENDER="transgender";
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect=new RectF();
    private final int[] colors;
    private final float[] bands;
    PrideFlagView(Context context,String kind){
        super(context);
        if(RAINBOW.equals(kind)){colors=new int[]{0xffe40303,0xffff8c00,0xffffed00,0xff008026,0xff004dff,0xff750787};bands=new float[]{1,1,1,1,1,1};setContentDescription("Rainbow pride flag: openly gay or lesbian");}
        else if(BISEXUAL.equals(kind)){colors=new int[]{0xffd60270,0xff9b4f96,0xff0038a8};bands=new float[]{2,1,2};setContentDescription("Bisexual pride flag");}
        else if(TRANSGENDER.equals(kind)){colors=new int[]{0xff5bcefa,0xfff5a9b8,0xffffffff,0xfff5a9b8,0xff5bcefa};bands=new float[]{1,1,1,1,1};setContentDescription("Transgender pride flag");}
        else throw new IllegalArgumentException("Unknown pride flag");
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);float left=getPaddingLeft(),top=getPaddingTop(),width=getWidth()-left-getPaddingRight(),height=getHeight()-top-getPaddingBottom();
        float total=0;for(float band:bands)total+=band;float at=top;
        paint.setStyle(Paint.Style.FILL);
        for(int n=0;n<colors.length;n++){float next=at+height*bands[n]/total;paint.setColor(colors[n]);canvas.drawRect(left,at,left+width,next,paint);at=next;}
        paint.setColor(0x66000000);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Math.max(1,getResources().getDisplayMetrics().density));rect.set(left+0.5f,top+0.5f,left+width-0.5f,top+height-0.5f);canvas.drawRect(rect,paint);paint.setStyle(Paint.Style.FILL);
    }
}
