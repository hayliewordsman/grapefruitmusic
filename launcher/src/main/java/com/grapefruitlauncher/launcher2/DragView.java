/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Modified for Grapefruit Launcher, 2026 MagicFlute LLC.
// See launcher/third_party/launcher2/README.md for the pinned source and changes.

package com.grapefruitlauncher.launcher2;

import android.graphics.*;
import android.view.View;
import android.widget.FrameLayout;

/** Launcher2 drag image, sized to one icon, with immediate deterministic removal. */
public final class DragView extends View {
    private final DragLayer layer;
    private Bitmap bitmap;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final int registrationX,registrationY;
    private final Rect destination=new Rect();
    private Rect region;
    private Point visualizeOffset;
    DragView(DragLayer layer,Bitmap bitmap,int registrationX,int registrationY,int left,int top,int width,int height,float scale){
        super(layer.getContext());this.layer=layer;this.bitmap=bitmap;
        int display=Math.round(58*getResources().getDisplayMetrics().density);float ratio=display/(float)bitmap.getWidth();
        this.registrationX=Math.round(registrationX*ratio);this.registrationY=Math.round(registrationY*ratio);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);setWillNotDraw(false);
        setLayoutParams(new FrameLayout.LayoutParams(display,display));
    }
    public void show(int x,int y){layer.addView(this);move(x,y);}
    public void move(int x,int y){setTranslationX(x-registrationX);setTranslationY(y-registrationY);}
    @Override protected void onSizeChanged(int w,int h,int ow,int oh){destination.set(0,0,w,h);}
    @Override protected void onDraw(Canvas canvas){if(bitmap!=null)canvas.drawBitmap(bitmap,null,destination,paint);}
    public void remove(){layer.removeView(this);if(bitmap!=null){bitmap.recycle();bitmap=null;}}
    public void setDragVisualizeOffset(Point point){visualizeOffset=point;}
    public Point getDragVisualizeOffset(){return visualizeOffset;}
    public void setDragRegion(Rect rect){region=rect;}
    public Rect getDragRegion(){return region;}
}
