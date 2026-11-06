package com.grapefruitsettings;

import android.app.*;
import android.content.*;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.*;
import android.widget.*;
import com.grapefruitmusic.ui.*;
import java.util.*;

/** Hooks used by the original JP5S Activity and dialog bytecode in the assembled OEM build. */
public final class LegacyTheme {
    private static final Map<Activity,Integer> showing=new WeakHashMap<>();
    private static final Set<Dialog> fixed=Collections.newSetFromMap(new WeakHashMap<Dialog,Boolean>());
    private LegacyTheme() {}
    static Palette palette(Context c){return new Palette(c,c.getSharedPreferences("settings",0).getInt("theme",0));}
    public static void apply(Activity a){
        DeviceSettings.incoming(a);
        UniversalTheme.sync(a,a.getSharedPreferences("settings",0),false,new int[]{0,1,2,3});
        Palette p=palette(a);int[] styles={R.style.GrapefruitLegacyDark,R.style.GrapefruitLegacyLight,R.style.GrapefruitLegacyBlack,R.style.GrapefruitLegacyBlue};
        a.setTheme(styles[p.id]);showing.put(a,p.id);Fonts.init(a);
        a.getWindow().setBackgroundDrawable(new ColorDrawable(p.bg));
        a.getWindow().getDecorView().post(()->{
            if(a.isFinishing())return;
            decorate(a.getWindow().getDecorView(),p);
            ActionBar bar=a.getActionBar();if(bar!=null)bar.setBackgroundDrawable(p.shape(p.surface,0,false));
        });
    }
    public static void prepareDialog(Dialog dialog){
        if(dialog==null||dialog.getWindow()==null)return;
        dialog.getWindow().getDecorView().post(()->fixDialog(dialog));
    }
    public static void show(Dialog dialog){dialog.show();prepareDialog(dialog);}
    public static AlertDialog show(AlertDialog.Builder builder){AlertDialog dialog=builder.show();prepareDialog(dialog);return dialog;}
    private static void fixDialog(Dialog d){
        if(!d.isShowing()||fixed.contains(d))return;
        fixed.add(d);Palette p=palette(d.getContext());
        View root=d.findViewById(android.R.id.content);if(root==null)return;
        decorate(root,p);List<EditText> fields=new ArrayList<>();fields(root,fields);
        for(EditText f:fields)DialogEditing.prepare(f);
        if(fields.isEmpty()||Build.VERSION.SDK_INT>=23||!(root instanceof FrameLayout))return;
        FrameLayout frame=(FrameLayout)root;LinearLayout wrapper=new LinearLayout(d.getContext());wrapper.setOrientation(LinearLayout.VERTICAL);
        FrameLayout body=new FrameLayout(d.getContext());
        while(frame.getChildCount()>0){View child=frame.getChildAt(0);frame.removeViewAt(0);body.addView(child);}
        wrapper.addView(body,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout tools=new LinearLayout(d.getContext());tools.setPadding(p.dp(4),p.dp(4),p.dp(4),p.dp(4));tools.setBackgroundColor(p.surface);
        String[] labels={"Select all","Copy","Cut","Paste"};
        for(int i=0;i<4;i++){
            final int action=i;TextView button=p.text(labels[i],12,true);button.setGravity(Gravity.CENTER);button.setMinHeight(p.dp(48));button.setBackground(p.interactive(false));
            button.setOnClickListener(v->{EditText f=fields.get(0);for(EditText field:fields)if(field.hasFocus()){f=field;break;}if(action==0)DialogEditing.selectAll(f);else if(action==3)DialogEditing.paste(f);else DialogEditing.copy(f,action==2);});
            tools.addView(button,new LinearLayout.LayoutParams(0,-2,1));
        }
        wrapper.addView(tools);frame.addView(wrapper);
    }
    private static void fields(View v,List<EditText> fields){if(v instanceof EditText)fields.add((EditText)v);if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)fields(g.getChildAt(i),fields);}}
    private static void decorate(View v,Palette p){
        if(v instanceof TextView){TextView t=(TextView)v;t.setTextColor(p.text);t.setTypeface(Fonts.of(t.getTextSize()/t.getResources().getDisplayMetrics().scaledDensity,false));}
        if(v instanceof Button && !(v instanceof CompoundButton))v.setBackground(p.interactive(false));
        if(v instanceof EditText){((EditText)v).setHintTextColor(p.secondary);}
        if(v instanceof ImageView){android.graphics.drawable.Drawable icon=((ImageView)v).getDrawable();if(icon!=null)icon.mutate().setColorFilter(p.accent,PorterDuff.Mode.SRC_IN);}
        if(v instanceof ViewGroup){ViewGroup group=(ViewGroup)v;for(int i=0;i<group.getChildCount();i++)decorate(group.getChildAt(i),p);}
    }
}
