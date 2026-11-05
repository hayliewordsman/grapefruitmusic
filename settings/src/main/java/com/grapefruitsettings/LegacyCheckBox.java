package com.grapefruitsettings;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.widget.CheckBox;

/** The OEM code still receives a CheckBox, with Grapefruit's themed switch as its visual. */
public final class LegacyCheckBox extends CheckBox {
    private Toggle renderer;
    public LegacyCheckBox(Context c){this(c,null);}
    public LegacyCheckBox(Context c,AttributeSet a){this(c,a,android.R.attr.checkboxStyle);}
    public LegacyCheckBox(Context c,AttributeSet a,int style){super(c,a,style);renderer=new Toggle(c);renderer.setPalette(LegacyTheme.palette(c));renderer.setChecked(isChecked());}
    @Override public void setChecked(boolean on){super.setChecked(on);if(renderer!=null){renderer.setChecked(on);invalidate();}}
    @Override public void onMeasure(int w,int h){super.onMeasure(w,h);float d=getResources().getDisplayMetrics().density;setMeasuredDimension(resolveSize(Math.max(getMeasuredWidth(),Math.round(60*d)),w),resolveSize(Math.max(getMeasuredHeight(),Math.round(48*d)),h));}
    @Override protected void onDraw(Canvas canvas){
        if(renderer==null)return;
        int labelWidth=0;
        if(length()>0){getPaint().setColor(LegacyTheme.palette(getContext()).text);labelWidth=Math.max(0,getWidth()-Math.round(60*getResources().getDisplayMetrics().density));canvas.drawText(getText().toString(),getPaddingLeft(),getHeight()/2f-(getPaint().ascent()+getPaint().descent())/2,getPaint());}
        int width=getWidth()-labelWidth;renderer.setEnabled(isEnabled());renderer.measure(MeasureSpec.makeMeasureSpec(width,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(getHeight(),MeasureSpec.EXACTLY));renderer.layout(0,0,width,getHeight());canvas.save();canvas.translate(labelWidth,0);renderer.draw(canvas);canvas.restore();
    }
}
