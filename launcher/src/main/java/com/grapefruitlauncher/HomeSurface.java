// SPDX-License-Identifier: GPL-3.0-or-later
package com.grapefruitlauncher;

import android.content.Context;
import android.graphics.*;
import android.os.Handler;
import android.view.*;
import android.widget.*;
import com.grapefruitmusic.ui.Fonts;
import java.util.*;

/** A sparse, scrollable Home workspace. Only visible cells and a small view pool occupy RAM. */
final class HomeSurface extends ScrollView {
    interface Callbacks {
        View cell(AppCatalog.App app,View recycled);
        void dropped(LauncherDrag drag);
    }
    private final LauncherStore store;
    private final LauncherTheme theme;
    private final String profile;
    private final Callbacks callbacks;
    private final Board board;
    private final Handler handler=new Handler();
    private final Map<String,AppCatalog.App> apps=new LinkedHashMap<>();
    private boolean editing,suspended;
    private LauncherDrag drag;
    private float pointerX,pointerY;
    private int direction;
    private final Runnable autoScroll=new Runnable(){
        @Override public void run(){
            if(drag==null || direction==0)return;
            if(direction>0 && getScrollY()+getHeight()>=board.getHeight()-theme.dp(100))board.extraRows++;
            scrollBy(0,direction*theme.dp(10));board.preview(pointerX,pointerY+getScrollY());board.requestLayout();
            handler.postDelayed(this,24);
        }
    };
    HomeSurface(Context context,LauncherStore store,LauncherTheme theme,String profile,Callbacks callbacks) {
        super(context);this.store=store;this.theme=theme;this.profile=profile;this.callbacks=callbacks;
        setFillViewport(true);setVerticalScrollBarEnabled(false);setClipToPadding(false);
        setPadding(theme.dp(12),theme.dp(14),theme.dp(12),theme.dp(18));
        board=new Board(context);addView(board,new ScrollView.LayoutParams(-1,-2));
        board.setOnDragListener((view,event)->dragEvent(event));
    }
    int appCount(){return apps.size();}
    boolean contains(String id){return apps.containsKey(id);}
    void reload(List<AppCatalog.App> installed,boolean edit) {
        suspended=false;editing=edit;apps.clear();
        for(String id:store.home()){AppCatalog.App app=AppCatalog.find(installed,id);if(app!=null)apps.put(id,app);}
        board.dirty=true;board.requestLayout();board.invalidate();
    }
    void editing(boolean value){editing=value;board.invalidate();}
    void releaseImages(){suspended=true;cancelDrag();board.release();}
    void dispose(){releaseImages();handler.removeCallbacksAndMessages(null);}
    boolean nudge(String id,int dx,int dy) {
        HomeLayout.Cell from=board.positions.get(id);if(from==null)return false;
        HomeLayout.Cell target=HomeLayout.snap(from.x+dx,from.y+dy,board.columns,store.subgrid());
        if(from.equals(target))return false;
        Map<String,HomeLayout.Cell> changed=HomeLayout.drop(id,target,board.positions);if(changed==null)return false;
        store.positions(profile,changed);board.dirty=true;board.requestLayout();return true;
    }
    private boolean dragEvent(DragEvent event) {
        if(!(event.getLocalState() instanceof LauncherDrag))return false;
        LauncherDrag session=(LauncherDrag)event.getLocalState();
        switch(event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:drag=session;board.invalidate();return true;
            case DragEvent.ACTION_DRAG_ENTERED:return true;
            case DragEvent.ACTION_DRAG_LOCATION:
                pointerX=event.getX();pointerY=event.getY()-getScrollY();board.preview(event.getX(),event.getY());
                int next=pointerY<theme.dp(52)?-1:pointerY>getHeight()-theme.dp(52)?1:0;
                if(next!=direction){handler.removeCallbacks(autoScroll);direction=next;if(next!=0)handler.post(autoScroll);}
                return true;
            case DragEvent.ACTION_DRAG_EXITED:direction=0;handler.removeCallbacks(autoScroll);board.target=null;board.invalidate();return true;
            case DragEvent.ACTION_DROP:
                HomeLayout.Cell target=board.point(event.getX(),event.getY());
                Map<String,HomeLayout.Cell> changed=HomeLayout.drop(session.app.id(),target,board.positions);
                if(changed==null)return false;
                store.add(session.app.id());store.positions(profile,changed);session.accepted=true;
                callbacks.dropped(session);board.dirty=true;board.requestLayout();return true;
            case DragEvent.ACTION_DRAG_ENDED:cancelDrag();return true;
            default:return false;
        }
    }
    private void cancelDrag(){drag=null;direction=0;handler.removeCallbacks(autoScroll);board.target=null;board.extraRows=2;board.invalidate();}
    @Override protected void onScrollChanged(int l,int t,int oldl,int oldt){super.onScrollChanged(l,t,oldl,oldt);if(board!=null)board.requestLayout();}
    private final class Board extends ViewGroup {
        private final Map<String,View> cells=new LinkedHashMap<>();
        private final ArrayDeque<View> pool=new ArrayDeque<>();
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private Map<String,HomeLayout.Cell> positions=new LinkedHashMap<>();
        private boolean dirty=true,valid;
        private int columns=1,extraRows=2;
        private final int cellHeight;
        private final RectF targetBounds=new RectF();
        private float cellWidth;
        private HomeLayout.Cell target;
        Board(Context context){
            super(context);setWillNotDraw(false);
            Paint font=new Paint();font.setTypeface(Fonts.medium());font.setTextSize(12*getResources().getDisplayMetrics().scaledDensity);
            cellHeight=theme.dp(58+9+12+16)+(int)Math.ceil(font.getFontSpacing()*3);
        }
        HomeLayout.Cell point(float x,float y) {
            if(cellWidth<=0 || cellHeight<=0)return new HomeLayout.Cell(0,0);
            return HomeLayout.snap((x-cellWidth/2)/(cellWidth/2),(y-theme.dp(35))/(cellHeight/2f),columns,store.subgrid());
        }
        void preview(float x,float y){target=point(x,y);valid=drag!=null && HomeLayout.drop(drag.app.id(),target,positions)!=null;invalidate();}
        @Override protected void onMeasure(int widthSpec,int heightSpec) {
            int width=Math.max(1,MeasureSpec.getSize(widthSpec));
            float density=getResources().getDisplayMetrics().density;
            int desired=Math.max(2,Math.min(8,(int)(width/density/Math.max(88,80*getResources().getConfiguration().fontScale))));
            if(columns!=desired){columns=desired;dirty=true;}
            cellWidth=width/(float)columns;
            if(dirty) {
                List<String> ids=new ArrayList<>(apps.keySet());Map<String,HomeLayout.Cell> saved=store.positions(profile,ids);
                positions=HomeLayout.resolve(ids,saved,columns,store.subgrid());
                Map<String,HomeLayout.Cell> defaults=new LinkedHashMap<>();
                for(String id:ids)if(!saved.containsKey(id) && positions.containsKey(id))defaults.put(id,positions.get(id));
                if(!defaults.isEmpty())store.positions(profile,defaults);
                dirty=false;
            }
            int bottom=2;for(HomeLayout.Cell cell:positions.values())bottom=Math.max(bottom,cell.y+2);
            int height=Math.max(MeasureSpec.getSize(heightSpec),Math.round((bottom/2f+extraRows)*cellHeight));
            setMeasuredDimension(width,height);virtualize();
            for(View cell:cells.values())cell.measure(MeasureSpec.makeMeasureSpec(Math.round(cellWidth),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(cellHeight,MeasureSpec.EXACTLY));
        }
        private void virtualize() {
            if(suspended)return;
            int viewport=HomeSurface.this.getHeight();if(viewport<=0)viewport=getResources().getDisplayMetrics().heightPixels;
            int top=Math.max(0,HomeSurface.this.getScrollY()-cellHeight),bottom=HomeSurface.this.getScrollY()+viewport+cellHeight;
            Set<String> wanted=new LinkedHashSet<>();
            for(Map.Entry<String,HomeLayout.Cell> entry:positions.entrySet()) {
                int y=Math.round(entry.getValue().y*cellHeight/2f);
                if(y+cellHeight>=top && y<=bottom)wanted.add(entry.getKey());
            }
            java.util.Iterator<Map.Entry<String,View>> iterator=cells.entrySet().iterator();
            while(iterator.hasNext()) {
                Map.Entry<String,View> entry=iterator.next();if(wanted.contains(entry.getKey()))continue;
                View cell=entry.getValue();removeViewInLayout(cell);clearImage(cell);iterator.remove();
                if(pool.size()<columns*2)pool.addLast(cell);
            }
            for(String id:wanted) {
                AppCatalog.App app=apps.get(id);if(app==null)continue;
                View cell=cells.get(id);
                if(cell==null) {
                    cell=callbacks.cell(app,pool.pollFirst());cells.put(id,cell);
                    addViewInLayout(cell,-1,new ViewGroup.LayoutParams(Math.round(cellWidth),cellHeight),true);
                } else if(cell.getTag()!=app)callbacks.cell(app,cell);
            }
        }
        @Override protected void onLayout(boolean changed,int l,int t,int r,int b) {
            for(Map.Entry<String,View> entry:cells.entrySet()) {
                HomeLayout.Cell position=positions.get(entry.getKey());if(position==null)continue;
                int x=Math.round(position.x*cellWidth/2),y=Math.round(position.y*cellHeight/2f);
                entry.getValue().layout(x,y,x+Math.round(cellWidth),y+cellHeight);
            }
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if(!editing && drag==null)return;
            paint.setColor(theme.outline);paint.setAlpha(180);
            int step=store.subgrid()?1:2;
            int first=Math.max(0,HomeSurface.this.getScrollY()/Math.max(1,cellHeight)-1)*2;
            int last=Math.min(HomeLayout.MAX_Y,first+(HomeSurface.this.getHeight()/Math.max(1,cellHeight)+4)*2);
            for(int y=first;y<=last;y+=step)for(int x=0;x<=2*(columns-1);x+=step)
                canvas.drawCircle((x+1)*cellWidth/2,y*cellHeight/2f+theme.dp(35),theme.dp(2),paint);
            if(target!=null) {
                paint.setColor(valid?theme.accent:theme.secondary);paint.setAlpha(65);
                float x=target.x*cellWidth/2,y=target.y*cellHeight/2f;
                targetBounds.set(x+2,y+2,x+cellWidth-2,y+cellHeight-2);
                canvas.drawRoundRect(targetBounds,theme.dp(22),theme.dp(22),paint);
            }
        }
        void release(){for(View cell:cells.values())clearImage(cell);cells.clear();pool.clear();removeAllViews();}
        private void clearImage(View cell) {
            cell.setAlpha(1);cell.setTag(null);
            if(cell instanceof ViewGroup && ((ViewGroup)cell).getChildAt(0) instanceof ImageView) {
                ImageView image=(ImageView)((ViewGroup)cell).getChildAt(0);image.setTag(null);image.setImageDrawable(null);
            }
        }
    }
}
