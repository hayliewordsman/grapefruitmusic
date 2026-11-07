package com.grapefruitmusic.ui;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.os.Build;
import android.view.*;
import android.widget.*;
import com.grapefruitmusic.ui.Fonts;

/** Blue TV controls with the audio app's palette, floating pills and Geist typography. */
public final class TvStyle {
    /** Past any control's height: GradientDrawable keeps a radius to half the shorter side, so ends are semicircles. */
    public static final int PILL=100000;
    public final Context context;
    public final int bg, surface, control, pressed, text, secondary, accent, end, outline, onAccent, toggleOff, toggleOffSegments;
    public final boolean light;
    public TvStyle(Context c, int theme) {
        context=c; light=Ui.theme(c)==com.grapefruitmusic.util.Prefs.THEME_GRAPEFRUIT_LIGHT;
        bg=Ui.color(c,com.grapefruitmusic.R.attr.gfBackground);
        surface=Ui.color(c,com.grapefruitmusic.R.attr.gfSurface);
        control=Ui.color(c,com.grapefruitmusic.R.attr.gfControl);
        pressed=Ui.color(c,com.grapefruitmusic.R.attr.gfControlPressed);
        text=Ui.color(c,com.grapefruitmusic.R.attr.gfTextPrimary);
        secondary=Ui.color(c,com.grapefruitmusic.R.attr.gfTextSecondary);
        accent=Ui.color(c,com.grapefruitmusic.R.attr.gfAccent);
        end=Ui.color(c,com.grapefruitmusic.R.attr.gfAccentEnd);
        outline=Ui.color(c,com.grapefruitmusic.R.attr.gfOutline);
        onAccent=Ui.color(c,com.grapefruitmusic.R.attr.gfOnAccent);
        toggleOff=Ui.color(c,com.grapefruitmusic.R.attr.gfToggleOff);
        toggleOffSegments=Ui.color(c,com.grapefruitmusic.R.attr.gfToggleOffSegments);
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
        t.setGravity(Gravity.CENTER);t.setMinHeight(dp(48));t.setPadding(dp(18),dp(10),dp(18),dp(10));
        t.setBackground(interactive(selected,PILL));t.setFocusable(true);t.setOnClickListener(action);
        return t;
    }
    /** A round icon button (back, favourite). */
    public TextView round(String glyph,View.OnClickListener action) {
        TextView t=button(glyph,false,action);t.setTextSize(20);t.setMinHeight(dp(44));t.setPadding(0,0,0,0);return t;
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
