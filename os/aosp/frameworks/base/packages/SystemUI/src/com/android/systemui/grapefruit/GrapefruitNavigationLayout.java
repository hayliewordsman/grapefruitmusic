// SPDX-License-Identifier: Apache-2.0
package com.android.systemui.grapefruit;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import org.grapefruit.platform.NavigationGeometry;

/** Seven fixed logical slots; layout direction, disabled keys and IME state cannot shift Home. */
public final class GrapefruitNavigationLayout extends LinearLayout {
    public GrapefruitNavigationLayout(Context c,AttributeSet a){super(c,a);setOrientation(HORIZONTAL);setLayoutDirection(View.LAYOUT_DIRECTION_LTR);}
    @Override protected void onFinishInflate(){super.onFinishInflate();if(getChildCount()!=7)throw new IllegalStateException("Expected seven navigation slots");BarTheme.bind(this);}
    @Override protected void onMeasure(int w,int h){
        int width=MeasureSpec.getSize(w),height=MeasureSpec.getSize(h);
        setMeasuredDimension(width,height);
        for(int i=0;i<7;i++)getChildAt(i).measure(MeasureSpec.makeMeasureSpec(NavigationGeometry.boundary(width,i+1)-NavigationGeometry.boundary(width,i),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(height,MeasureSpec.EXACTLY));
    }
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
        int width=r-l;for(int i=0;i<7;i++)getChildAt(i).layout(NavigationGeometry.boundary(width,i),0,NavigationGeometry.boundary(width,i+1),b-t);
    }
}
