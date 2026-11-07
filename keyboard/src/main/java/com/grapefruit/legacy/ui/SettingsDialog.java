package com.grapefruit.legacy.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import com.android.inputmethod.latin.R;
import com.grapefruit.legacy.GrapefruitTheme;

/** Audio-style choice sheets and form cards, with no platform radio buttons or dialog chrome. */
public final class SettingsDialog extends Dialog {
    public interface Choice { void selected(int position); }
    public interface Value { void selected(int value); }
    private final GrapefruitTheme theme;
    private SettingsDialog(Context c) {
        super(c,R.style.GrapefruitDialogWindow);theme=new GrapefruitTheme(c);
        setCanceledOnTouchOutside(true);
    }
    private void showContent(View content,boolean sheet) {
        setContentView(content);Window w=getWindow();w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        WindowManager.LayoutParams p=w.getAttributes();p.dimAmount=.45f;w.setAttributes(p);
        w.setGravity(sheet?(Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL):Gravity.CENTER);show();
        w.setLayout(Math.min(getContext().getResources().getDisplayMetrics().widthPixels-theme.dp(sheet?16:40),
                theme.dp(sheet?480:360)),ViewGroup.LayoutParams.WRAP_CONTENT);
    }
    private LinearLayout card(CharSequence title,boolean sheet) {
        LinearLayout card=SettingsUi.vertical(getContext());
        card.setBackground(SettingsUi.shape(theme.surface,theme.dp(24)));
        card.setPadding(theme.dp(20),theme.dp(sheet?10:22),theme.dp(20),theme.dp(16));
        if(sheet) {
            View handle=new View(getContext());handle.setBackground(SettingsUi.shape(theme.outline,theme.dp(2)));
            LinearLayout.LayoutParams h=new LinearLayout.LayoutParams(theme.dp(36),theme.dp(4));
            h.gravity=Gravity.CENTER_HORIZONTAL;h.bottomMargin=theme.dp(12);card.addView(handle,h);
        }
        TextView heading=SettingsUi.text(getContext(),title,18,theme.text,true);
        heading.setPadding(theme.dp(2),0,theme.dp(2),theme.dp(12));card.addView(heading,SettingsUi.match());
        return card;
    }
    public static SettingsDialog choices(Context c,CharSequence title,CharSequence[] entries,
            int selected,Choice listener) {
        SettingsDialog dialog=new SettingsDialog(c);GrapefruitTheme t=dialog.theme;
        LinearLayout card=dialog.card(title,true),rows=SettingsUi.vertical(c);
        for(int i=0;i<entries.length;i++) {
            final int position=i;
            LinearLayout row=new LinearLayout(c);row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(t.dp(52));row.setPadding(t.dp(4),t.dp(8),t.dp(8),t.dp(8));
            row.setBackground(SettingsUi.pressed(t,Color.TRANSPARENT,t.dp(22)));
            row.setSoundEffectsEnabled(false);row.setTag("choice:"+i);
            TextView label=SettingsUi.text(c,entries[i],16,i==selected?t.accent:t.text,false);
            row.addView(label,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
            if(i==selected) row.addView(SettingsUi.text(c,"✓",22,t.accent,true));
            row.setContentDescription(entries[i]+(i==selected?c.getString(R.string.grapefruit_selected):""));
            row.setOnClickListener(v->{listener.selected(position);dialog.dismiss();});rows.addView(row,SettingsUi.match());
        }
        ScrollView scroll=new ScrollView(c);scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);scroll.addView(rows);
        // Leave room for the sheet title, cancel pill, status bar and surrounding space.
        int height=Math.min(t.dp(entries.length*52),Math.max(t.dp(52),c.getResources().getDisplayMetrics().heightPixels-t.dp(210)));
        card.addView(scroll,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,height));
        LinearLayout.LayoutParams cancel=SettingsUi.match();cancel.topMargin=t.dp(12);
        card.addView(SettingsUi.button(c,c.getString(android.R.string.cancel),false,v->dialog.dismiss()),cancel);
        dialog.showContent(card,true);return dialog;
    }
    public static SettingsDialog slider(Context c,CharSequence title,int initial,int maximum,
            String suffix,int systemDefault,Value save,Value preview) {
        SettingsDialog dialog=new SettingsDialog(c);GrapefruitTheme t=dialog.theme;
        LinearLayout card=dialog.card(title,false);
        final boolean[] useDefault={initial<0};
        TextView amount=SettingsUi.text(c,initial<0?c.getText(R.string.grapefruit_system_default):initial+suffix,24,t.text,true);
        amount.setGravity(Gravity.CENTER);
        card.addView(amount,SettingsUi.match());
        SeekBar slider=new Slider(c);slider.setMax(maximum);slider.setProgress(Math.max(0,initial));
        slider.setContentDescription(title);slider.setTag("grapefruit-slider");
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onProgressChanged(SeekBar s,int progress,boolean user) {
                if(user)useDefault[0]=false;
                amount.setText(useDefault[0]?c.getText(R.string.grapefruit_system_default):progress+suffix);
            }
            @Override public void onStopTrackingTouch(SeekBar s) {if(preview!=null)preview.selected(s.getProgress());}
        });
        card.addView(slider,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,t.dp(52)));
        TextView defaults=SettingsUi.button(c,c.getText(R.string.grapefruit_system_default),false,v->{
            useDefault[0]=true;amount.setText(R.string.grapefruit_system_default);
        });
        defaults.setTag("grapefruit-system-default");card.addView(defaults,SettingsUi.match());
        LinearLayout buttons=new LinearLayout(c);LinearLayout.LayoutParams cell=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1);
        buttons.addView(SettingsUi.button(c,c.getString(android.R.string.cancel),false,v->dialog.dismiss()),cell);
        LinearLayout.LayoutParams ok=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1);ok.leftMargin=t.dp(10);
        buttons.addView(SettingsUi.button(c,c.getString(android.R.string.ok),true,v->{save.selected(useDefault[0]?systemDefault:slider.getProgress());dialog.dismiss();}),ok);
        LinearLayout.LayoutParams bar=SettingsUi.match();bar.topMargin=t.dp(16);card.addView(buttons,bar);
        dialog.showContent(card,false);return dialog;
    }
    public static SettingsDialog form(Context c,CharSequence title,View content,
            CharSequence positive,Runnable save,CharSequence negative,Runnable cancel) {
        SettingsDialog dialog=new SettingsDialog(c);GrapefruitTheme t=dialog.theme;
        LinearLayout card=dialog.card(title,false);card.addView(content,SettingsUi.match());
        LinearLayout buttons=new LinearLayout(c);
        LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1);
        buttons.addView(SettingsUi.button(c,negative,false,v->{if(cancel!=null)cancel.run();dialog.dismiss();}),left);
        LinearLayout.LayoutParams right=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1);right.leftMargin=t.dp(10);
        buttons.addView(SettingsUi.button(c,positive,true,v->{if(save!=null)save.run();dialog.dismiss();}),right);
        LinearLayout.LayoutParams bar=SettingsUi.match();bar.topMargin=t.dp(16);card.addView(buttons,bar);
        dialog.showContent(card,false);return dialog;
    }
    /** Framework seek/accessibility behavior with Audio's thin track and round thumb. */
    private static final class Slider extends SeekBar {
        private final android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.RectF track=new android.graphics.RectF();
        private final GrapefruitTheme t;
        Slider(Context c) {super(c);t=new GrapefruitTheme(c);setThumb(null);setProgressDrawable(null);
            setBackgroundColor(Color.TRANSPARENT);setPadding(t.dp(14),0,t.dp(14),0);setSoundEffectsEnabled(false);}
        @Override protected synchronized void onDraw(android.graphics.Canvas canvas) {
            float left=getPaddingLeft(),right=getWidth()-getPaddingRight(),middle=getHeight()/2f;
            float x=left+(right-left)*getProgress()/Math.max(1f,getMax()),r=t.dp(1.5f);
            paint.setColor(t.control);track.set(left,middle-r,right,middle+r);canvas.drawRoundRect(track,r,r,paint);
            paint.setColor(t.accent);track.right=x;canvas.drawRoundRect(track,r,r,paint);
            canvas.drawCircle(x,middle,t.dp(isPressed()?11:8),paint);
        }
    }
}
