package com.grapefruitfast;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;
import com.grapefruitmusic.ui.Fonts;

/** Music 5.x's peach/ruby/coral/cream palette, pill shapes and Geist typography. */
public final class Brand {
    public final Context context;
    public final int bg, surface, control, text, secondary, accent, end, outline, onAccent;
    public Brand(Context c, int theme) {
        context=c;
        boolean light=theme==1, black=theme==2;
        bg=Color.parseColor(light?"#FCE4D9":black?"#000000":"#1C0A0E");
        surface=Color.parseColor(light?"#FFFAF6":black?"#180A0E":"#2E1218");
        control=Color.parseColor(light?"#FADCCF":black?"#261116":"#421C24");
        text=Color.parseColor(light?"#4A1622":"#FFF1E8");
        secondary=Color.parseColor(light?"#9A5C63":"#B88A8F");
        accent=Color.parseColor(light?"#E83850":"#FF7F6B");
        end=Color.parseColor(light?"#D92B4B":"#F2546B");
        outline=Color.parseColor(light?"#F3CDBF":"#46202A");
        onAccent=Color.parseColor(light?"#FFFFFF":"#2A0A10");
    }
    public int dp(float n) { return Math.round(n*context.getResources().getDisplayMetrics().density); }
    public TextView text(String value, int size, boolean bold) {
        TextView t=new TextView(context); t.setText(value); t.setTextColor(text);
        t.setTextSize(size); t.setTypeface(Fonts.of(size,bold)); t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }
    public GradientDrawable shape(int fill,int radius, boolean border) {
        GradientDrawable d=new GradientDrawable(); d.setColor(fill); d.setCornerRadius(dp(radius));
        if(border) d.setStroke(dp(1),outline); return d;
    }
    public GradientDrawable gradient(int radius) {
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{accent,end});
        d.setCornerRadius(dp(radius)); return d;
    }
    public StateListDrawable interactive(boolean selected, int radius) {
        StateListDrawable d=new StateListDrawable();
        GradientDrawable focus=shape(control,radius,false); focus.setStroke(dp(3),accent);
        d.addState(new int[]{android.R.attr.state_focused},focus);
        d.addState(new int[]{android.R.attr.state_pressed},gradient(radius));
        d.addState(new int[]{},selected?gradient(radius):shape(control,radius,true)); return d;
    }
    public TextView button(String label,boolean selected,View.OnClickListener action) {
        TextView t=text(label,14,true);t.setTextColor(selected?onAccent:text);
        t.setGravity(Gravity.CENTER);t.setMinHeight(dp(48));t.setPadding(dp(18),dp(10),dp(18),dp(10));
        t.setBackground(interactive(selected,100));t.setFocusable(true);t.setOnClickListener(action);
        return t;
    }
    public LinearLayout column() { LinearLayout l=new LinearLayout(context);l.setOrientation(LinearLayout.VERTICAL);return l; }
    public LinearLayout row() { LinearLayout l=new LinearLayout(context);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l; }
}
