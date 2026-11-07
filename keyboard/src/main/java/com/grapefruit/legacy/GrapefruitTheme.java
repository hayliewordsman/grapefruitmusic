package com.grapefruit.legacy;

import android.content.Context;
import android.preference.PreferenceManager;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;

/** Launcher palette and lower-right teardrop, drawn with API 17 Canvas operations. */
public final class GrapefruitTheme {
    public static final String PREF_THEME = "grapefruit_theme";
    public static final String[] NAMES = {"Grapefruit Dark", "Grapefruit Light", "Pitch Black", "Victory Blue"};
    public final int id, bg, surface, control, pressed, text, secondary, accent, end, outline, onAccent;
    private final float density;
    public GrapefruitTheme(Context c) {
        this(c, readChoice(c));
    }
    public GrapefruitTheme(Context c, int choice) {
        density=c.getResources().getDisplayMetrics().density;
        id=choice>=0 && choice<NAMES.length?choice:0;
        // Copied from LauncherTheme at 70302e6759e2a7e0fdb6fc511a6f91b22e13d4ed.
        int[][] palettes={
            {0xff1c0a0e,0xff2e1218,0xff421c24,0xff522631,0xfffff1e8,0xffb88a8f,0xffff7f6b,0xfff2546b,0xff46202a,0xff2a0a10},
            {0xfffce4d9,0xfffffaf6,0xfffadccf,0xfff3c5b3,0xff4a1622,0xff9a5c63,0xffe83850,0xffd92b4b,0xfff3cdbf,0xffffffff},
            {0xff000000,0xff180a0e,0xff261116,0xff36181f,0xfffff3ee,0xffb8949a,0xffff7f6b,0xfff2546b,0xff33181f,0xff1a0a0d},
            {0xff07162b,0xff102541,0xff1c3b60,0xff284f78,0xffeaf4ff,0xffa2bad8,0xff67c7ff,0xff398bff,0xff294665,0xff06172c}
        };
        int[] p=palettes[id]; bg=p[0];surface=p[1];control=p[2];pressed=p[3];text=p[4];
        secondary=p[5];accent=p[6];end=p[7];outline=p[8];onAccent=p[9];
    }
    private static int readChoice(Context c) {
        try { return Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(c).getString(PREF_THEME,"0")); }
        catch (RuntimeException invalid) { return 0; }
    }
    public int dp(float n) { return Math.round(n*density); }
    private static int blend(int a,int b,float amount) {
        return Color.rgb(Math.round(Color.red(a)*(1-amount)+Color.red(b)*amount),
                Math.round(Color.green(a)*(1-amount)+Color.green(b)*amount),
                Math.round(Color.blue(a)*(1-amount)+Color.blue(b)*amount));
    }
    public StateListDrawable key(boolean action) {
        StateListDrawable states=new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed},new Drop(
                action?blend(accent,bg,.15f):blend(pressed,accent,.28f),
                action?blend(end,bg,.15f):blend(control,accent,.18f),false,true));
        states.addState(new int[]{android.R.attr.state_focused},new Drop(pressed,control,false,false));
        states.addState(new int[]{},new Drop(action?accent:control,action?end:surface,false,false));
        return states;
    }
    public StateListDrawable spacebar() {
        StateListDrawable states=new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed},new Drop(
                blend(pressed,accent,.28f),blend(control,accent,.18f),true,true));
        states.addState(new int[]{android.R.attr.state_focused},new Drop(pressed,control,true,false));
        states.addState(new int[]{},new Drop(control,surface,true,false));return states;
    }
    /** Normalize Launcher's exact path to allow space/enter and calculator keys to stretch. */
    public static Path teardrop(float left,float top,float width,float height) {
        Path p=new Path(); p.moveTo(.5f,0);
        p.cubicTo(.776f,0,1,.224f,1,.5f); p.lineTo(1,.84f);
        p.quadTo(1,1,.84f,1);p.lineTo(.5f,1);
        p.cubicTo(.224f,1,0,.776f,0,.5f);p.cubicTo(0,.224f,.224f,0,.5f,0);p.close();
        Matrix m=new Matrix();m.setScale(width,height);m.postTranslate(left,top);p.transform(m);return p;
    }
    private final class Drop extends Drawable {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int start,finish;
        private int alpha=255;
        private final boolean pill,down;
        private Path path;
        private Shader shader;
        Drop(int a,int b,boolean rounded,boolean pressed){start=a;finish=b;pill=rounded;down=pressed;}
        @Override protected void onBoundsChange(Rect r) {
            float gap=Math.max(1,dp(down?3.5f:2));
            float w=Math.max(0,r.width()-gap*2), h=Math.max(0,r.height()-gap*2);
            if(pill) {
                path=new Path();
                path.addRoundRect(new RectF(r.left+gap,r.top+gap,r.right-gap,r.bottom-gap),
                        h/2,h/2,Path.Direction.CW);
            } else path=teardrop(r.left+gap,r.top+gap,w,h);
            shader=new LinearGradient(r.left,r.top,r.right,r.bottom,start,finish,Shader.TileMode.CLAMP);
        }
        @Override public void draw(Canvas c) {
            if(path==null)onBoundsChange(getBounds());
            paint.setStyle(Paint.Style.FILL);paint.setShader(shader);paint.setAlpha(alpha);c.drawPath(path,paint);
            paint.setShader(null);
        }
        @Override public void setAlpha(int a){alpha=a;invalidateSelf();}
        @Override public void setColorFilter(ColorFilter f){paint.setColorFilter(f);invalidateSelf();}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
        @Override public boolean getPadding(Rect p){p.set(dp(3),dp(3),dp(3),dp(3));return true;}
    }
}
