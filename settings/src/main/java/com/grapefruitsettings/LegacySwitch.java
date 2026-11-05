package com.grapefruitsettings;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.widget.Switch;

/** Keeps the Switch API expected by the JP5S bytecode and paints the Grapefruit slice control. */
public final class LegacySwitch extends Switch {
    private Toggle renderer;
    public LegacySwitch(Context c){this(c,null);}
    public LegacySwitch(Context c,AttributeSet attrs){this(c,attrs,android.R.attr.switchStyle);}
    public LegacySwitch(Context c,AttributeSet attrs,int style){
        super(c,attrs,style);renderer=new Toggle(c);renderer.setPalette(LegacyTheme.palette(c));renderer.setChecked(isChecked());
    }
    @Override public void setChecked(boolean value){super.setChecked(value);if(renderer!=null){renderer.setChecked(value);invalidate();}}
    @Override public void onMeasure(int w,int h){super.onMeasure(w,h);float d=getResources().getDisplayMetrics().density;setMeasuredDimension(resolveSize(Math.max(getMeasuredWidth(),Math.round(60*d)),w),resolveSize(Math.max(getMeasuredHeight(),Math.round(48*d)),h));}
    @Override protected void onDraw(Canvas canvas){
        if(renderer==null)return;renderer.setEnabled(isEnabled());
        renderer.measure(MeasureSpec.makeMeasureSpec(getWidth(),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(getHeight(),MeasureSpec.EXACTLY));renderer.layout(0,0,getWidth(),getHeight());renderer.draw(canvas);
    }
}
