// SPDX-License-Identifier: GPL-3.0-or-later
package com.grapefruitlauncher;
import android.content.Context;
import android.graphics.*;
import android.view.View;
/** Canvas glyphs follow Grapefruit's round navigation controls on every supported Android. */
final class LauncherGlyph extends View {
    static final int BACK=0,SETTINGS=1;
    private final int type,color;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path=new Path();
    LauncherGlyph(Context context,int type,LauncherTheme theme){super(context);this.type=type;color=theme.text;}
    @Override protected void onDraw(Canvas canvas){
        float size=Math.min(getWidth(),getHeight()),cx=getWidth()/2f,cy=getHeight()/2f,u=size/44;
        paint.setColor(color);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2*u);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);
        if(type==BACK){path.reset();path.moveTo(cx+3*u,cy-7*u);path.lineTo(cx-4*u,cy);path.lineTo(cx+3*u,cy+7*u);canvas.drawPath(path,paint);}
        else {canvas.drawCircle(cx,cy,7*u,paint);canvas.drawCircle(cx,cy,2.5f*u,paint);for(int i=0;i<8;i++){double angle=i*Math.PI/4;canvas.drawLine(cx+(float)Math.cos(angle)*8*u,cy+(float)Math.sin(angle)*8*u,cx+(float)Math.cos(angle)*10*u,cy+(float)Math.sin(angle)*10*u,paint);}}
    }
}
