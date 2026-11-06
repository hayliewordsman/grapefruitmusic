package com.grapefruitsettings;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;

/** Keeps visible dialog sections evenly spaced, including after validation or security changes. */
final class SpacedColumn extends LinearLayout {
    private final int gap;
    SpacedColumn(Context context,int gap){super(context);this.gap=gap;setOrientation(VERTICAL);}
    @Override protected void onMeasure(int width,int height){
        boolean first=true;
        for(int i=0;i<getChildCount();i++){
            View child=getChildAt(i);LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams)child.getLayoutParams();
            lp.topMargin=child.getVisibility()==GONE||first?0:gap;
            lp.bottomMargin=0;
            if(child.getVisibility()!=GONE)first=false;
        }
        super.onMeasure(width,height);
    }
}
