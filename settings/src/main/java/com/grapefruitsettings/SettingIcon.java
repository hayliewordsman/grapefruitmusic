package com.grapefruitsettings;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Grapefruit slice badges with small, readable settings symbols. No vector drawable dependency. */
final class SettingIcon extends View {
    private final String kind;
    private final Palette palette;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    SettingIcon(Context c,Palette colors,String type) { super(c);palette=colors;kind=type;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
    @Override protected void onDraw(Canvas c) {
        float scale=Math.min(getWidth(),getHeight())/48f;
        c.save();c.translate((getWidth()-48*scale)/2,(getHeight()-48*scale)/2);c.scale(scale,scale);
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(5,0,43,48,palette.accent,palette.end,Shader.TileMode.CLAMP));
        c.drawCircle(24,24,22,p);p.setShader(null);
        p.setColor(palette.onAccent);p.setAlpha(34);
        for(int i=0;i<8;i++) { c.save();c.rotate(i*45,24,24);c.drawRect(23.5f,5,24.5f,10,p);c.restore(); }
        p.setAlpha(255);p.setColor(palette.onAccent);p.setStrokeWidth(2);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);p.setStyle(Paint.Style.STROKE);
        switch(kind) {
            case "wifi":
                c.drawArc(new RectF(11,15,37,41),226,88,false,p);c.drawArc(new RectF(16,21,32,37),225,90,false,p);dot(c,24,31,1.7f);break;
            case "bluetooth": path(c,23,12,31,19,18,30);path(c,23,36,31,29,18,18);line(c,23,12,23,36);break;
            case "sound": path(c,13,21,19,21,25,15,25,33,19,27,13,27,13,21);c.drawArc(new RectF(21,16,35,32),-60,120,false,p);break;
            case "display":
                c.drawCircle(24,24,5,p);for(int i=0;i<8;i++){c.save();c.rotate(i*45,24,24);line(c,24,12,24,15);c.restore();}break;
            case "storage": c.drawRoundRect(new RectF(14,14,34,34),3,3,p);line(c,14,25,34,25);dot(c,29,29,1);break;
            case "battery": c.drawRoundRect(new RectF(13,17,33,31),2,2,p);line(c,36,22,36,26);line(c,18,21,18,27);line(c,23,21,23,27);break;
            case "apps": for(int y=15;y<=26;y+=11)for(int x=15;x<=26;x+=11)c.drawRoundRect(new RectF(x,y,x+7,y+7),2,2,p);break;
            case "security": path(c,24,12,34,16,32,28,24,36,16,28,14,16,24,12);path(c,19,24,23,28,29,21);break;
            case "keyboard": c.drawRoundRect(new RectF(12,17,36,32),2,2,p);for(int x=17;x<34;x+=5)dot(c,x,22,0.6f);line(c,18,27,30,27);break;
            case "account": c.drawCircle(24,18,4,p);c.drawArc(new RectF(15,25,33,39),180,180,false,p);break;
            case "time": c.drawCircle(24,24,11,p);path(c,24,17,24,24,29,27);break;
            case "accessibility": c.drawCircle(24,14,2,p);line(c,14,20,34,20);path(c,24,20,24,26,18,35);line(c,24,26,30,35);break;
            case "developer": path(c,19,17,12,24,19,31);path(c,29,17,36,24,29,31);line(c,26,15,22,33);break;
            case "about": c.drawCircle(24,24,11,p);line(c,24,23,24,30);dot(c,24,18,1);break;
            case "theme":
                c.drawCircle(24,24,11,p);for(int i=0;i<8;i++){c.save();c.rotate(i*45,24,24);line(c,24,16,24,21);c.restore();}break;
            default:
                c.drawCircle(24,24,9,p);for(int i=0;i<8;i++){c.save();c.rotate(i*45,24,24);line(c,24,10,24,14);c.restore();}c.drawCircle(24,24,3,p);
        }
        c.restore();
    }
    private void line(Canvas c,float x,float y,float a,float b){c.drawLine(x,y,a,b,p);}
    private void path(Canvas c,float... xy){Path path=new Path();path.moveTo(xy[0],xy[1]);for(int i=2;i<xy.length;i+=2)path.lineTo(xy[i],xy[i+1]);c.drawPath(path,p);}
    private void dot(Canvas c,float x,float y,float r){p.setStyle(Paint.Style.FILL);c.drawCircle(x,y,r,p);p.setStyle(Paint.Style.STROKE);}
}
