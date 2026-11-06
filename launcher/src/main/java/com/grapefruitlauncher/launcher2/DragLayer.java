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

import android.content.Context;
import android.graphics.Rect;
import android.view.*;
import android.widget.FrameLayout;

/** Launcher2's in-window drag layer. All pointer events stay in this app. */
public final class DragLayer extends FrameLayout {
    private final DragController controller;
    private final int[] location = new int[2];
    private final int[] ownLocation = new int[2];
    public DragLayer(Context context) {
        super(context);setMotionEventSplittingEnabled(false);
        controller = new DragController(this);
    }
    public DragController getDragController(){return controller;}
    @Override public boolean onInterceptTouchEvent(MotionEvent event){
        boolean wasDragging=controller.isDragging();
        boolean intercept=controller.onInterceptTouchEvent(event);
        // Consume the release even though Launcher2 has already completed the drop.
        return wasDragging || intercept;
    }
    @Override public boolean onTouchEvent(MotionEvent event){return controller.onTouchEvent(event) || super.onTouchEvent(event);}
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        if(controller.isDragging() && event.getKeyCode()==KeyEvent.KEYCODE_BACK){
            if(event.getAction()==KeyEvent.ACTION_UP)controller.cancelDrag();return true;
        }
        return controller.dispatchKeyEvent(event) || super.dispatchKeyEvent(event);
    }
    public float getLocationInDragLayer(View child,int[] loc){
        getLocationOnScreen(ownLocation);child.getLocationOnScreen(location);
        loc[0]=location[0]-ownLocation[0];loc[1]=location[1]-ownLocation[1];return 1;
    }
    public void getDescendantRectRelativeToSelf(View child,Rect rect){
        getLocationInDragLayer(child,location);rect.set(location[0],location[1],location[0]+child.getWidth(),location[1]+child.getHeight());
    }
    public void onEnterScrollArea(int direction){}
    public void onExitScrollArea(){}
}
