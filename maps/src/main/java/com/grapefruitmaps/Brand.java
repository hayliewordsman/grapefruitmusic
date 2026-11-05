// SPDX-License-Identifier: GPL-3.0-or-later
package com.grapefruitmaps;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.os.Build;
import android.view.*;
import android.widget.*;
import com.grapefruitmusic.ui.Fonts;

/** Music 5.x's peach/ruby/coral/cream palette, floating pills and Geist typography. */
public final class Brand {
    /** Past any control's height: GradientDrawable keeps a radius to half the shorter side, so ends are semicircles. */
    public static final int PILL=100000;
    public final Context context;
    public final int bg, surface, control, pressed, text, secondary, accent, end, outline, onAccent, toggleOff, toggleOffSegments;
    public final boolean light;
    public Brand(Context c, int theme) {
        context=c;
        light=theme==1; boolean black=theme==2; boolean blue=theme==3;
        bg=Color.parseColor(light?"#FCE4D9":black?"#000000":blue?"#07162B":"#1C0A0E");
        surface=Color.parseColor(light?"#FFFAF6":black?"#180A0E":blue?"#102541":"#2E1218");
        control=Color.parseColor(light?"#FADCCF":black?"#261116":blue?"#1C3B60":"#421C24");
        pressed=Color.parseColor(light?"#F3CDBF":black?"#3A1820":blue?"#284F78":"#55262F");
        text=Color.parseColor(light?"#4A1622":blue?"#EAF4FF":"#FFF1E8");
        secondary=Color.parseColor(light?"#9A5C63":blue?"#A2BAD8":"#B88A8F");
        accent=Color.parseColor(light?"#E83850":blue?"#67C7FF":"#FF7F6B");
        end=Color.parseColor(light?"#D92B4B":blue?"#398BFF":"#F2546B");
        outline=Color.parseColor(light?"#F3CDBF":blue?"#294665":"#46202A");
        onAccent=Color.parseColor(light?"#FFFFFF":blue?"#06172C":"#2A0A10");
        // Music's switch colours for each theme.
        toggleOff=Color.parseColor(light?"#F7D6C9":black?"#3A1D23":blue?"#294665":"#4A2229");
        toggleOffSegments=Color.parseColor(light?"#F1C8B9":blue?"#BDDDF6":"#F2D0C6");
    }
    private static Typeface pixel;
    /** Geist Pixel Triangle, cut down to the letters of "Grapefruit Maps" (tools/tv/wordmark.py). */
    public synchronized Typeface pixel() {
        if(pixel==null) {
            try {pixel=Typeface.createFromAsset(context.getAssets(),"fonts/GeistPixel-Triangle-TV.ttf");}
            catch(RuntimeException e){pixel=Fonts.bold();}
        }
        return pixel;
    }
    /**
     * The wordmark, as Music's: Geist Pixel Triangle, "Grapefruit" in the accent and "TV" in the text
     * colour. On one line it shrinks to fit (up to sp); stacked, the words go one over the other.
     */
    public TextView wordmark(float sp, boolean stacked) {
        final TextView t=new TextView(context);t.setTypeface(pixel());t.setTextSize(sp);t.setTextColor(text);
        t.setIncludeFontPadding(false);t.setGravity(Gravity.CENTER);t.setContentDescription("Grapefruit Maps");
        android.text.SpannableString mark=new android.text.SpannableString(stacked?"Grapefruit\nTV":"Grapefruit Maps");
        mark.setSpan(new android.text.style.ForegroundColorSpan(accent),0,"Grapefruit".length(),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        t.setText(mark);
        if(stacked){t.setLineSpacing(dp(4),1);return t;}
        final float max=sp*context.getResources().getDisplayMetrics().scaledDensity;
        // One line by fitting, not setSingleLine(): that lays text out a million pixels wide and centres it there.
        t.setMaxLines(1);
        t.addOnLayoutChangeListener((v,l,top,r,btm,ol,ot,or,ob)->{
            int room=(r-l)-t.getPaddingLeft()-t.getPaddingRight();
            if(room<=0||(r-l)==(or-ol))return;
            android.graphics.Paint p=new android.graphics.Paint(t.getPaint());p.setTextSize(max);
            float width=p.measureText(t.getText().toString());
            final float size=width>room?max*room/width*0.98f:max;
            if(Math.abs(size-t.getTextSize())>0.5f)t.post(()->t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,size));
        });
        return t;
    }
    public int dp(float n) { return Math.round(n*context.getResources().getDisplayMetrics().density); }
    /** Radii are in dp; PILL stays as it is. */
    private float radius(int r) { return r>=PILL?PILL:dp(r); }
    public TextView text(String value, int size, boolean bold) {
        TextView t=new TextView(context); t.setText(value); t.setTextColor(text);
        t.setTextSize(size); t.setTypeface(Fonts.of(size,bold)); t.setGravity(Gravity.CENTER_VERTICAL);
        t.setIncludeFontPadding(false);
        return t;
    }
    public TextView label(String value, int size, int color) {
        TextView t=text(value,size,false); t.setTextColor(color); return t;
    }
    public GradientDrawable shape(int fill,int r, boolean border) {
        GradientDrawable d=new GradientDrawable(); d.setColor(fill); d.setCornerRadius(radius(r));
        if(border) d.setStroke(Math.max(1,dp(1)),outline); return d;
    }
    public GradientDrawable circle(int fill) {
        GradientDrawable d=new GradientDrawable(); d.setShape(GradientDrawable.OVAL); d.setColor(fill); return d;
    }
    public GradientDrawable gradient(int r) {
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{accent,end});
        d.setCornerRadius(radius(r)); return d;
    }
    public GradientDrawable gradientCircle() {
        GradientDrawable d=gradient(0); d.setShape(GradientDrawable.OVAL); return d;
    }
    /**
     * Pressed darkens, a remote's focus adds an accent ring, selected is the accent gradient. The ring is
     * drawn on the control itself, so it always hugs the pill (never a margin around it).
     * fill 0 leaves an unselected control clear (tab bars), and outlined adds the hairline.
     */
    public StateListDrawable interactive(boolean selected, int r, int fill, boolean outlined) {
        StateListDrawable d=new StateListDrawable();
        GradientDrawable focus=selected?gradient(r):shape(fill==0?control:fill,r,false);
        focus.setStroke(dp(3),selected?text:accent);
        GradientDrawable down=selected?gradient(r):shape(pressed,r,false);
        if(selected) down.setAlpha(210);
        d.addState(new int[]{android.R.attr.state_focused},focus);
        d.addState(new int[]{android.R.attr.state_selected},focus);
        d.addState(new int[]{android.R.attr.state_pressed},down);
        d.addState(new int[]{},selected?gradient(r):fill==0?new ColorDrawable(Color.TRANSPARENT):shape(fill,r,outlined));
        return d;
    }
    public StateListDrawable interactive(boolean selected, int r) { return interactive(selected,r,control,true); }
    public TextView button(String label,boolean selected,View.OnClickListener action) {
        TextView t=text(label,14,true);t.setTextColor(selected?onAccent:text);
        t.setGravity(Gravity.CENTER);t.setMinHeight(dp(44));t.setPadding(dp(18),0,dp(18),0);
        t.setBackground(interactive(selected,PILL));t.setFocusable(true);t.setOnClickListener(action);
        return t;
    }
    /** A round icon button (back, favourite). */
    public TextView round(String glyph,View.OnClickListener action) {
        TextView t=button(glyph,false,action);t.setTextSize(20);t.setPadding(0,0,0,0);return t;
    }
    /** Lifts a floating surface: a shadow where Android draws them (5.0+), the hairline everywhere. */
    public void lift(View v,float dp) {
        if(Build.VERSION.SDK_INT>=21) v.setElevation(dp(dp));
    }
    /** The page's colour fading to clear at the top: what floating bars sit on, so content scrolls under them. */
    public Drawable fade() {
        int page=bg&0x00ffffff;
        return new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,new int[]{page|0xf2000000,page|0xb3000000,page});
    }
    /**
     * Music's fadingFill: the page colour, fading to clear over its last fadePx at the bottom, which
     * floating chips sit on so the page shows through as it scrolls under them. setAlpha() fades it all.
     */
    public Drawable topFade(final int fadePx) {
        final int color=bg;
        return new Drawable() {
            private final android.graphics.Paint paint=new android.graphics.Paint();
            private int alpha=255;
            @Override protected void onBoundsChange(android.graphics.Rect r) {
                paint.setShader(new android.graphics.LinearGradient(0,r.bottom-fadePx,0,r.bottom,color,color&0x00ffffff,android.graphics.Shader.TileMode.CLAMP));
            }
            @Override public void draw(android.graphics.Canvas canvas) {if(alpha==0)return;paint.setAlpha(alpha);canvas.drawRect(getBounds(),paint);}
            @Override public void setAlpha(int a) {if(a!=alpha){alpha=a;invalidateSelf();}}
            @Override public void setColorFilter(android.graphics.ColorFilter f) {}
            @Override public int getOpacity() {return android.graphics.PixelFormat.TRANSLUCENT;}
        };
    }
    public LinearLayout column() { LinearLayout l=new LinearLayout(context);l.setOrientation(LinearLayout.VERTICAL);return l; }
    public LinearLayout row() { LinearLayout l=new LinearLayout(context);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l; }
}
