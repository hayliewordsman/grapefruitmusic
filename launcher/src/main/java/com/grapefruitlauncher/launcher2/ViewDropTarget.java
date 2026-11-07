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

// Modified for Grapefruit Launcher; see third_party/launcher2/README.md.
package com.grapefruitlauncher.launcher2;
import android.graphics.*;
import android.view.View;
public abstract class ViewDropTarget implements DropTarget {
    protected final View view;
    private final DragLayer layer;
    public ViewDropTarget(DragLayer layer,View view){this.layer=layer;this.view=view;}
    public boolean isDropEnabled(){return view.isShown() && view.getWidth()>0;}
    public void getHitRect(Rect rect){view.getHitRect(rect);}
    public void getLocationInDragLayer(int[] loc){layer.getLocationInDragLayer(view,loc);}
    public int getLeft(){return view.getLeft();}
    public int getTop(){return view.getTop();}
    public DropTarget getDropTargetDelegate(DragObject d){return null;}
    public void onDragEnter(DragObject d){view.setAlpha(.7f);}
    public void onDragOver(DragObject d){}
    public void onDragExit(DragObject d){view.setAlpha(1);}
    public void onFlingToDelete(DragObject d,int x,int y,PointF velocity){}
}
