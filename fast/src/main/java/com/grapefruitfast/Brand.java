package com.grapefruitfast;
import android.content.Context;
import android.graphics.Color;
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
    public final int bg, surface, control, pressed, text, secondary, accent, end, outline, onAccent;
    public final boolean light;
    public Brand(Context c, int theme) {
        context=c;
        light=theme==1; boolean black=theme==2;
        bg=Color.parseColor(light?"#FCE4D9":black?"#000000":"#1C0A0E");
        surface=Color.parseColor(light?"#FFFAF6":black?"#180A0E":"#2E1218");
        control=Color.parseColor(light?"#FADCCF":black?"#261116":"#421C24");
        pressed=Color.parseColor(light?"#F3CDBF":black?"#3A1820":"#55262F");
        text=Color.parseColor(light?"#4A1622":"#FFF1E8");
        secondary=Color.parseColor(light?"#9A5C63":"#B88A8F");
        accent=Color.parseColor(light?"#E83850":"#FF7F6B");
        end=Color.parseColor(light?"#D92B4B":"#F2546B");
        outline=Color.parseColor(light?"#F3CDBF":"#46202A");
        onAccent=Color.parseColor(light?"#FFFFFF":"#2A0A10");
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
    public LinearLayout column() { LinearLayout l=new LinearLayout(context);l.setOrientation(LinearLayout.VERTICAL);return l; }
    public LinearLayout row() { LinearLayout l=new LinearLayout(context);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l; }
}
