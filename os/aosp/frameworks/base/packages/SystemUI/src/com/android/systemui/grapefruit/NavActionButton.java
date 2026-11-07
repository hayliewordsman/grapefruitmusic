// SPDX-License-Identifier: Apache-2.0
package com.android.systemui.grapefruit;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.media.AudioManager;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageButton;

/** Screenshot and volume actions reuse SystemUI's screenshot service and Android AudioService. */
public final class NavActionButton extends ImageButton implements View.OnClickListener {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private int foreground=0xfffff1e8;
    public NavActionButton(Context c,AttributeSet attrs){super(c,attrs);setOnClickListener(this);setBackgroundColor(0);}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);float x=getWidth()/2f,y=getHeight()/2f,s=12*getResources().getDisplayMetrics().density;
        paint.setColor(foreground);paint.setStrokeWidth(s/6);paint.setStyle(Paint.Style.STROKE);
        canvas.drawRoundRect(new android.graphics.RectF(x-s,y-s*0.7f,x+s,y+s*0.7f),s/3,s/3,paint);
        if("screenshot".equals(getTag()))canvas.drawCircle(x,y,s/3,paint);
        else {canvas.drawLine(x-s/2,y,x+s/2,y,paint);if("volume_up".equals(getTag()))canvas.drawLine(x,y-s/2,x,y+s/2,paint);}
    }
    public void setForegroundColor(int color){foreground=color;invalidate();}
    public void onClick(View v){
        if("screenshot".equals(getTag())){ScreenshotRequest.take(getContext());return;}
        AudioManager audio=(AudioManager)getContext().getSystemService(Context.AUDIO_SERVICE);
        audio.adjustSuggestedStreamVolume("volume_up".equals(getTag())?AudioManager.ADJUST_RAISE:AudioManager.ADJUST_LOWER,AudioManager.USE_DEFAULT_STREAM_TYPE,AudioManager.FLAG_SHOW_UI|AudioManager.FLAG_PLAY_SOUND);
    }
}
