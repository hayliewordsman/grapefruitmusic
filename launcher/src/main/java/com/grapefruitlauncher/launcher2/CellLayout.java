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

import android.content.Context;
import android.graphics.*;
import android.view.*;

/** Launcher2's cell geometry, span layout and occupancy model; cells use half-grid units. */
public final class CellLayout extends ViewGroup {
    private int mCountX=2,mCountY=2;
    private float mCellWidth,mCellHeight;
    private boolean[][] mOccupied=new boolean[2][2];
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect=new RectF();
    private boolean guides,subgrid,valid;
    private int targetX=-1,targetY=-1,accent,outline;
    public CellLayout(Context context){super(context);setWillNotDraw(false);}
    public void setGridSize(int x,int y){
        if(x!=mCountX || y!=mCountY){mCountX=x;mCountY=y;mOccupied=new boolean[x][y];}requestLayout();
    }
    public float unitWidth(){return mCellWidth;}
    public float unitHeight(){return mCellHeight;}
    public void clearOccupied(){for(boolean[] column:mOccupied)java.util.Arrays.fill(column,false);}
    public void markCells(int x,int y,int spanX,int spanY,boolean value){
        for(int i=x;i<x+spanX && i<mCountX;i++)for(int j=y;j<y+spanY && j<mCountY;j++)if(i>=0 && j>=0)mOccupied[i][j]=value;
    }
    public static boolean findVacantCell(int[] vacant,int spanX,int spanY,int countX,int countY,boolean[][] occupied){
        for(int y=0;y<=countY-spanY;y++)for(int x=0;x<=countX-spanX;x++){
            boolean available=true;
            for(int i=x;i<x+spanX && available;i++)for(int j=y;j<y+spanY;j++)if(occupied[i][j]){available=false;break;}
            if(available){vacant[0]=x;vacant[1]=y;return true;}
        }
        return false;
    }
    public void pointToCellExact(int x,int y,int[] result){
        result[0]=Math.max(0,Math.min(mCountX-1,(int)((x-getPaddingLeft())/Math.max(1,mCellWidth))));
        result[1]=Math.max(0,Math.min(mCountY-1,(int)((y-getPaddingTop())/Math.max(1,mCellHeight))));
    }
    public void cellToPoint(int x,int y,int[] result){
        result[0]=getPaddingLeft()+Math.round(x*mCellWidth);result[1]=getPaddingTop()+Math.round(y*mCellHeight);
    }
    public void guides(boolean show,boolean subgrid,int accent,int outline){this.guides=show;this.subgrid=subgrid;this.accent=accent;this.outline=outline;invalidate();}
    public void preview(int x,int y,boolean valid){targetX=x;targetY=y;this.valid=valid;invalidate();}
    @Override protected void onMeasure(int w,int h){
        int width=MeasureSpec.getSize(w),height=MeasureSpec.getSize(h);setMeasuredDimension(width,height);
        mCellWidth=(width-getPaddingLeft()-getPaddingRight())/(float)mCountX;
        mCellHeight=(height-getPaddingTop()-getPaddingBottom())/(float)mCountY;
        for(int i=0;i<getChildCount();i++){
            View child=getChildAt(i);LayoutParams lp=(LayoutParams)child.getLayoutParams();
            child.measure(MeasureSpec.makeMeasureSpec(Math.round(lp.cellHSpan*mCellWidth),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(Math.round(lp.cellVSpan*mCellHeight),MeasureSpec.EXACTLY));
        }
    }
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
        for(int i=0;i<getChildCount();i++){
            View child=getChildAt(i);LayoutParams lp=(LayoutParams)child.getLayoutParams();
            int x=getPaddingLeft()+Math.round(lp.cellX*mCellWidth),y=getPaddingTop()+Math.round(lp.cellY*mCellHeight);
            child.layout(x,y,x+child.getMeasuredWidth(),y+child.getMeasuredHeight());
        }
    }
    @Override public void cancelLongPress(){super.cancelLongPress();for(int i=0;i<getChildCount();i++)getChildAt(i).cancelLongPress();}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);float density=getResources().getDisplayMetrics().density;
        if(guides){paint.setColor(outline);paint.setAlpha(200);int step=subgrid?1:2;
            for(int y=0;y<=mCountY-2;y+=step)for(int x=0;x<=mCountX-2;x+=step)
                canvas.drawCircle(getPaddingLeft()+(x+1)*mCellWidth,getPaddingTop()+y*mCellHeight+35*density,2*density,paint);
        }
        if(targetX>=0){
            paint.setColor(valid?accent:outline);paint.setAlpha(70);
            float x=getPaddingLeft()+targetX*mCellWidth,y=getPaddingTop()+targetY*mCellHeight;
            rect.set(x+3*density,y+3*density,x+2*mCellWidth-3*density,y+2*mCellHeight-3*density);canvas.drawRoundRect(rect,22*density,22*density,paint);
        }
        paint.setAlpha(255);
    }
    public static final class LayoutParams extends ViewGroup.LayoutParams {
        public int cellX,cellY,cellHSpan,cellVSpan;
        public LayoutParams(int x,int y,int spanX,int spanY){super(-1,-1);cellX=x;cellY=y;cellHSpan=spanX;cellVSpan=spanY;}
    }
}
