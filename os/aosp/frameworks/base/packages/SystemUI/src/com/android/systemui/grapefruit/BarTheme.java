// SPDX-License-Identifier: Apache-2.0
package com.android.systemui.grapefruit;

import android.database.ContentObserver;
import android.graphics.PorterDuff;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import org.grapefruit.platform.GrapefruitStyle;
import org.grapefruit.platform.Palette;
import org.grapefruit.platform.ThemeContract;

/** Lifecycle-bound provider observer. Each bar updates immediately on a universal selection. */
public final class BarTheme extends ContentObserver implements View.OnAttachStateChangeListener {
    private final View root;
    private boolean observing;
    private BarTheme(View v){super(new Handler());root=v;v.addOnAttachStateChangeListener(this);apply();}
    public static void bind(View root){new BarTheme(root);}
    private void tint(View v,int color){
        if(v instanceof NavActionButton)((NavActionButton)v).setForegroundColor(color);
        if(v instanceof ImageView)((ImageView)v).setColorFilter(color,PorterDuff.Mode.SRC_IN);
        if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)tint(g.getChildAt(i),color);}
    }
    private void apply(){Palette p=new Palette(ThemeContract.read(root.getContext()));root.setBackgroundColor(p.background);GrapefruitStyle.apply(root,p);tint(root,p.text);}
    @Override public void onChange(boolean self){apply();}
    public void onViewAttachedToWindow(View v){if(!observing){v.getContext().getContentResolver().registerContentObserver(ThemeContract.URI,false,this);observing=true;}apply();}
    public void onViewDetachedFromWindow(View v){if(observing){v.getContext().getContentResolver().unregisterContentObserver(this);observing=false;}}
}
