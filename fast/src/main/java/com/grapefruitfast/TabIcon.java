package com.grapefruitfast;
import android.content.Context;
import android.graphics.*;
import android.view.View;

/**
 * The tab icons (and Settings' chevron), drawn in code on Music's 24x24 grid with its stroke weights (Search and Settings are
 * Music's own), so they're crisp at any density, take any theme colour and need no image resources.
 */
public final class TabIcon extends View {
    public static final int LIVE_TV=1, FAVOURITES=2, SEARCH=3, SETTINGS=4, CHEVRON=5, CHEVRON_LEFT=6;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path=new Path();
    private final RectF oval=new RectF();
    private final int icon;
    private int color;
    public TabIcon(Context c,int icon,int color) {super(c);this.icon=icon;this.color=color;}
    public void setColor(int color) {if(this.color!=color){this.color=color;invalidate();}}
    @Override protected void onDraw(Canvas c) {
        float size=Math.min(getWidth()-getPaddingLeft()-getPaddingRight(),getHeight()-getPaddingTop()-getPaddingBottom());
        if(size<=0)return;
        c.save();
        c.translate(getPaddingLeft()+(getWidth()-getPaddingLeft()-getPaddingRight()-size)/2f,getPaddingTop()+(getHeight()-getPaddingTop()-getPaddingBottom()-size)/2f);
        c.scale(size/24f,size/24f);
        p.reset();p.setAntiAlias(true);p.setColor(color);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
        path.reset();
        switch(icon) {
            case LIVE_TV:
                // A widescreen set on its stand, with the play triangle: the launcher icon's television.
                stroke(2.1f);
                oval.set(2.5f,4.5f,21.5f,17f);c.drawRoundRect(oval,3.2f,3.2f,p);
                c.drawLine(9,20.5f,15,20.5f,p);c.drawLine(12,17.5f,12,20.5f,p);
                p.setStyle(Paint.Style.FILL);
                path.moveTo(10.2f,8.1f);path.lineTo(15,10.75f);path.lineTo(10.2f,13.4f);path.close();c.drawPath(path,p);
                break;
            case FAVOURITES:
                // A rounded five-pointed star, like the ★ on favourite channels.
                stroke(2.1f);
                for(int i=0;i<10;i++) {
                    double a=Math.toRadians(-90+i*36);float r=i%2==0?9.6f:4.3f;
                    float x=12+(float)(r*Math.cos(a)),y=12.8f+(float)(r*Math.sin(a));
                    if(i==0)path.moveTo(x,y);else path.lineTo(x,y);
                }
                path.close();c.drawPath(path,p);
                break;
            case SEARCH:
                stroke(2.2f);
                c.drawCircle(10.5f,10.5f,6.5f,p);
                p.setStrokeWidth(2.6f);
                c.drawLine(15.5f,15.5f,20.5f,20.5f,p);
                break;
            case SETTINGS:
                stroke(3.4f);
                c.drawCircle(12,12,5.6f,p);
                p.setStyle(Paint.Style.FILL);
                for(int i=0;i<8;i++) {
                    c.save();c.rotate(i*45,12,12);
                    oval.set(10.4f,1.8f,13.6f,6.4f);c.drawRoundRect(oval,0.8f,0.8f,p);
                    c.restore();
                }
                break;
            case CHEVRON:
                // Music's CHEVRON_RIGHT: rows that open something.
                stroke(2.4f);
                path.moveTo(9,5);path.lineTo(16,12);path.lineTo(9,19);c.drawPath(path,p);
                break;
            case CHEVRON_LEFT:
                // Music's CHEVRON_LEFT: the round back button.
                stroke(2.6f);
                path.moveTo(15.5f,4);path.lineTo(7.5f,12);path.lineTo(15.5f,20);c.drawPath(path,p);
                break;
        }
        c.restore();
    }
    private void stroke(float width) {p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(width);}
}
