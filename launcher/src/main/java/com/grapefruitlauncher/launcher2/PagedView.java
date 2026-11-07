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
import android.view.*;
import android.widget.Scroller;

/** Launcher2's horizontal page/velocity model, reduced to public API 17 view APIs. */
public class PagedView extends ViewGroup {
    private static final int TOUCH_STATE_REST=0,TOUCH_STATE_SCROLLING=1;
    private final Scroller mScroller;
    private final int mTouchSlop,mMaximumVelocity,mMinimumVelocity;
    private VelocityTracker mVelocityTracker;
    private int mTouchState=TOUCH_STATE_REST,mCurrentPage;
    private float mDownMotionX,mDownMotionY,mLastMotionX;
    public PagedView(Context context){
        super(context);mScroller=new Scroller(context);
        ViewConfiguration config=ViewConfiguration.get(context);
        mTouchSlop=config.getScaledPagingTouchSlop();mMaximumVelocity=config.getScaledMaximumFlingVelocity();
        mMinimumVelocity=Math.round(500*getResources().getDisplayMetrics().density);
        setWillNotDraw(false);setClipChildren(true);
    }
    public int getCurrentPage(){return mCurrentPage;}
    public void setCurrentPage(int page){
        mScroller.abortAnimation();mCurrentPage=bound(page);scrollTo(mCurrentPage*getWidth(),0);onPageSwitched(mCurrentPage);
    }
    private int bound(int page){return Math.max(0,Math.min(Math.max(0,getChildCount()-1),page));}
    protected void onPageSwitched(int page){}
    protected void onPageProgress(){}
    public void snapToPage(int page){
        page=bound(page);int target=page*getWidth(),delta=target-getScrollX();mCurrentPage=page;onPageSwitched(page);
        mScroller.startScroll(getScrollX(),0,delta,0,Math.min(400,150+Math.abs(delta)/2));postInvalidateOnAnimation();
    }
    @Override protected void onMeasure(int w,int h){
        int width=MeasureSpec.getSize(w),height=MeasureSpec.getSize(h);setMeasuredDimension(width,height);
        for(int i=0;i<getChildCount();i++)getChildAt(i).measure(MeasureSpec.makeMeasureSpec(width,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(height,MeasureSpec.EXACTLY));
    }
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
        int width=r-l;for(int i=0;i<getChildCount();i++)getChildAt(i).layout(i*width,0,(i+1)*width,b-t);
        if(changed && mTouchState==TOUCH_STATE_REST && mScroller.isFinished())scrollTo(bound(mCurrentPage)*width,0);
    }
    private void acquireVelocityTrackerAndAddMovement(MotionEvent event){
        if(mVelocityTracker==null)mVelocityTracker=VelocityTracker.obtain();mVelocityTracker.addMovement(event);
    }
    private void releaseVelocityTracker(){if(mVelocityTracker!=null){mVelocityTracker.recycle();mVelocityTracker=null;}}
    private void determineScrollingStart(MotionEvent event){
        float dx=Math.abs(event.getX()-mDownMotionX),dy=Math.abs(event.getY()-mDownMotionY);
        if(dx>mTouchSlop && dx>dy){
            mTouchState=TOUCH_STATE_SCROLLING;mLastMotionX=event.getX();
            if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);
            for(int i=0;i<getChildCount();i++)getChildAt(i).cancelLongPress();
        }
    }
    @Override public boolean onInterceptTouchEvent(MotionEvent event){
        switch(event.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                acquireVelocityTrackerAndAddMovement(event);mDownMotionX=mLastMotionX=event.getX();mDownMotionY=event.getY();
                mTouchState=mScroller.isFinished()?TOUCH_STATE_REST:TOUCH_STATE_SCROLLING;mScroller.abortAnimation();break;
            case MotionEvent.ACTION_MOVE:if(mTouchState!=TOUCH_STATE_SCROLLING)determineScrollingStart(event);break;
            case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:
                mTouchState=TOUCH_STATE_REST;releaseVelocityTracker();break;
        }
        return mTouchState==TOUCH_STATE_SCROLLING;
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        acquireVelocityTrackerAndAddMovement(event);
        switch(event.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                mScroller.abortAnimation();mDownMotionX=mLastMotionX=event.getX();mDownMotionY=event.getY();return true;
            case MotionEvent.ACTION_MOVE:
                if(mTouchState!=TOUCH_STATE_SCROLLING)determineScrollingStart(event);
                if(mTouchState==TOUCH_STATE_SCROLLING){
                    float delta=mLastMotionX-event.getX();mLastMotionX=event.getX();
                    scrollTo(Math.max(0,Math.min(Math.max(0,getChildCount()-1)*getWidth(),getScrollX()+Math.round(delta))),0);
                }break;
            case MotionEvent.ACTION_UP:
                mVelocityTracker.computeCurrentVelocity(1000,mMaximumVelocity);float velocity=mVelocityTracker.getXVelocity();
                int nearest=Math.round(getScrollX()/(float)Math.max(1,getWidth()));
                if(Math.abs(velocity)>mMinimumVelocity && Math.abs(event.getX()-mDownMotionX)>mTouchSlop)
                    nearest=velocity>0?mCurrentPage-1:mCurrentPage+1;
                snapToPage(nearest);mTouchState=TOUCH_STATE_REST;releaseVelocityTracker();break;
            case MotionEvent.ACTION_CANCEL:
                snapToPage(Math.round(getScrollX()/(float)Math.max(1,getWidth())));mTouchState=TOUCH_STATE_REST;releaseVelocityTracker();break;
        }
        return true;
    }
    @Override public void computeScroll(){
        if(mScroller.computeScrollOffset()){scrollTo(mScroller.getCurrX(),0);postInvalidateOnAnimation();}
    }
    @Override protected void onScrollChanged(int l,int t,int ol,int ot){super.onScrollChanged(l,t,ol,ot);onPageProgress();invalidate();}
    public void releaseTouch(){mScroller.abortAnimation();mTouchState=TOUCH_STATE_REST;releaseVelocityTracker();}
    @Override protected void onDetachedFromWindow(){releaseTouch();super.onDetachedFromWindow();}
}
