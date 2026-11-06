// SPDX-License-Identifier: GPL-3.0-or-later
package com.grapefruitlauncher;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.ImageView;
import com.grapefruitmusic.ui.Fonts;
import com.grapefruitlauncher.launcher2.*;
import java.util.*;

/** Launcher2 paged workspace with sparse coordinates and a bounded pool of icon views. */
final class HomeSurface extends PagedView implements DropTarget,DragScroller {
    interface Callbacks {View cell(AppCatalog.App app,View recycled);void dropped(LauncherDrag drag);}
    private final LauncherStore store;
    private final LauncherTheme theme;
    private final String profile;
    private final Callbacks callbacks;
    private final DragLayer layer;
    private final int iconHeight;
    private final Map<String,AppCatalog.App> apps=new LinkedHashMap<>();
    private final Map<String,View> cells=new LinkedHashMap<>();
    private final ArrayDeque<View> pool=new ArrayDeque<>();
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private Map<String,HomeLayout.Cell> positions=new LinkedHashMap<>();
    private int columns=1,rows=1,usedPages=1;
    private boolean editing,suspended,dirty=true,building;
    private LauncherDrag drag;
    HomeSurface(Context context,LauncherStore store,LauncherTheme theme,String profile,DragLayer layer,Callbacks callbacks){
        super(context);this.store=store;this.theme=theme;this.profile=profile;this.layer=layer;this.callbacks=callbacks;
        Paint font=new Paint();font.setTypeface(Fonts.medium());font.setTextSize(12*getResources().getDisplayMetrics().scaledDensity);
        iconHeight=theme.dp(58+9+12+16)+(int)Math.ceil(font.getFontSpacing()*3);
        setContentDescription("Home workspace. Swipe left or right to change page.");
    }
    int appCount(){return apps.size();}
    boolean contains(String id){return apps.containsKey(id);}
    int columns(){return columns;}
    int rows(){return rows;}
    String profile(){return profile;}
    Map<String,HomeLayout.Cell> coordinates(){return new LinkedHashMap<>(positions);}
    void reload(List<AppCatalog.App> installed,boolean edit){
        suspended=false;editing=edit;apps.clear();for(String id:store.home()){AppCatalog.App app=AppCatalog.find(installed,id);if(app!=null)apps.put(id,app);}
        dirty=true;requestLayout();
    }
    void editing(boolean value){editing=value;dirty=true;requestLayout();refreshGuides();}
    void startDrag(LauncherDrag session){drag=session;dirty=true;requestLayout();refreshGuides();}
    void endDrag(){drag=null;onExitScrollArea();dirty=true;requestLayout();refreshGuides();}
    void releaseImages(){suspended=true;endDrag();releaseTouch();for(View cell:cells.values())clearImage(cell);cells.clear();pool.clear();for(int i=0;i<getChildCount();i++)((CellLayout)getChildAt(i)).removeAllViews();}
    void dispose(){releaseImages();}
    boolean nudge(String id,int dx,int dy){
        HomeLayout.Cell from=positions.get(id);if(from==null)return false;
        HomeLayout.Cell local=HomeLayout.snap(from.x+dx,from.y%(rows*2)+dy,columns,store.subgrid());
        local=new HomeLayout.Cell(local.x,Math.min(rows*2-2,local.y));
        HomeLayout.Cell target=new HomeLayout.Cell(local.x,from.y/(rows*2)*(rows*2)+local.y);
        if(from.equals(target))return false;Map<String,HomeLayout.Cell> changed=HomeLayout.drop(id,target,positions);if(changed==null)return false;
        positions.putAll(changed);store.positions(profile,positions);dirty=true;requestLayout();return true;
    }
    @Override protected void onMeasure(int w,int h){
        int width=MeasureSpec.getSize(w),height=MeasureSpec.getSize(h);
        int nextColumns=Math.max(2,Math.min(8,(int)((width-theme.dp(24))/getResources().getDisplayMetrics().density/Math.max(88,80*getResources().getConfiguration().fontScale))));
        int nextRows=Math.max(1,(height-theme.dp(44))/Math.max(1,iconHeight));
        if(nextColumns!=columns || nextRows!=rows){columns=nextColumns;rows=nextRows;dirty=true;}
        building=true;
        if(dirty){
            Map<String,HomeLayout.Cell> saved=store.positions(profile,new ArrayList<>(apps.keySet()));
            positions=WorkspaceModel.resolve(new ArrayList<>(apps.keySet()),saved,columns,rows,store.subgrid());
            Map<String,HomeLayout.Cell> defaults=new LinkedHashMap<>();
            for(String id:positions.keySet())if(!saved.containsKey(id))defaults.put(id,positions.get(id));
            if(!defaults.isEmpty())store.positions(profile,defaults);
            usedPages=WorkspaceModel.pages(positions,rows);int pages=usedPages+(editing || drag!=null?1:0);
            while(getChildCount()>pages){CellLayout page=(CellLayout)getChildAt(getChildCount()-1);for(int i=page.getChildCount()-1;i>=0;i--)recycle(page.getChildAt(i));removeView(page);}
            while(getChildCount()<pages){CellLayout page=new CellLayout(getContext());page.setPadding(theme.dp(12),theme.dp(12),theme.dp(12),theme.dp(32));addView(page,new ViewGroup.LayoutParams(-1,-1));}
            for(int i=0;i<getChildCount();i++)((CellLayout)getChildAt(i)).setGridSize(columns*2,rows*2);
            dirty=false;refreshGuides();
        }
        virtualize(width);super.onMeasure(w,h);building=false;
        if(getCurrentPage()>=getChildCount())setCurrentPage(getChildCount()-1);
    }
    private void virtualize(int width){
        if(suspended || width<=0)return;
        int visible=Math.max(0,getScrollX()/width),left=Math.min(visible,getCurrentPage()),right=Math.max(visible,getCurrentPage())+1;
        Set<String> wanted=new LinkedHashSet<>();
        for(Map.Entry<String,HomeLayout.Cell> entry:positions.entrySet()){
            int page=entry.getValue().y/(rows*2);if(page>=left-1 && page<=right)wanted.add(entry.getKey());
        }
        for(String id:new ArrayList<>(cells.keySet()))if(!wanted.contains(id)){View cell=cells.remove(id);recycle(cell);}
        for(String id:wanted){
            AppCatalog.App app=apps.get(id);HomeLayout.Cell cell=positions.get(id);int index=cell.y/(rows*2);if(app==null || index>=getChildCount())continue;
            CellLayout page=(CellLayout)getChildAt(index);View view=cells.get(id);
            if(view==null){view=callbacks.cell(app,pool.pollFirst());cells.put(id,view);}
            else if(view.getTag()!=app)callbacks.cell(app,view);
            int localY=cell.y%(rows*2);
            if(view.getParent()!=page){
                if(view.getParent() instanceof ViewGroup)((ViewGroup)view.getParent()).removeView(view);
                page.addView(view,new CellLayout.LayoutParams(cell.x,localY,2,2));
            }else {
                CellLayout.LayoutParams lp=(CellLayout.LayoutParams)view.getLayoutParams();
                if(lp.cellX!=cell.x || lp.cellY!=localY){lp.cellX=cell.x;lp.cellY=localY;view.setLayoutParams(lp);}
            }
        }
    }
    private void recycle(View view){
        Object tag=view.getTag();if(tag instanceof AppCatalog.App)cells.remove(((AppCatalog.App)tag).id());
        if(view.getParent() instanceof ViewGroup)((ViewGroup)view.getParent()).removeView(view);
        clearImage(view);if(pool.size()<columns*2)pool.addLast(view);
    }
    private void clearImage(View view){view.setAlpha(1);view.setTag(null);if(view instanceof ViewGroup && ((ViewGroup)view).getChildAt(0) instanceof ImageView){ImageView image=(ImageView)((ViewGroup)view).getChildAt(0);image.setTag(null);image.setImageDrawable(null);}}
    private void refreshGuides(){for(int i=0;i<getChildCount();i++)((CellLayout)getChildAt(i)).guides(editing || drag!=null,store.subgrid(),theme.accent,theme.outline);invalidate();}
    @Override protected void onPageSwitched(int page){if(!building){virtualize(getWidth());requestLayout();}announceForAccessibility("Home page "+(page+1)+" of "+Math.max(1,getChildCount()));}
    @Override protected void onPageProgress(){if(!building && getWidth()>0){virtualize(getWidth());requestLayout();}}
    @Override protected void dispatchDraw(Canvas canvas){
        super.dispatchDraw(canvas);int count=getChildCount();if(count<=1)return;
        float step=theme.dp(14),start=getScrollX()+getWidth()/2f-(count-1)*step/2;
        int shown=Math.min(count,9),first=Math.max(0,Math.min(count-shown,getCurrentPage()-4));
        start=getScrollX()+getWidth()/2f-(shown-1)*step/2;
        for(int i=first;i<first+shown;i++){paint.setColor(i==getCurrentPage()?theme.accent:theme.outline);canvas.drawCircle(start+(i-first)*step,getHeight()-theme.dp(14),theme.dp(i==getCurrentPage()?3.5f:2.5f),paint);}
    }
    private HomeLayout.Cell point(int x,int y){
        int width=Math.max(1,getWidth());int globalX=x+getScrollX(),pageIndex=Math.max(0,Math.min(getChildCount()-1,globalX/width));
        CellLayout page=(CellLayout)getChildAt(pageIndex);
        float cw=Math.max(1,page.unitWidth()),ch=Math.max(1,page.unitHeight());
        HomeLayout.Cell local=HomeLayout.snap((globalX-pageIndex*width-page.getPaddingLeft()-cw)/cw,(y-page.getPaddingTop()-theme.dp(35))/ch,columns,store.subgrid());
        return new HomeLayout.Cell(local.x,pageIndex*rows*2+Math.min(rows*2-2,local.y));
    }
    public boolean isDropEnabled(){return !suspended && isShown() && getChildCount()>0;}
    public void getLocationInDragLayer(int[] loc){layer.getLocationInDragLayer(this,loc);}
    public DropTarget getDropTargetDelegate(DragObject object){return null;}
    public boolean acceptDrop(DragObject object){return object.dragInfo instanceof LauncherDrag && getChildCount()>0 && HomeLayout.drop(((LauncherDrag)object.dragInfo).app.id(),point(object.x,object.y),positions)!=null;}
    public void onDrop(DragObject object){
        LauncherDrag session=(LauncherDrag)object.dragInfo;Map<String,HomeLayout.Cell> changed=HomeLayout.drop(session.app.id(),point(object.x,object.y),positions);if(changed==null)return;
        positions.putAll(changed);store.add(session.app.id());store.positions(profile,positions);apps.put(session.app.id(),session.app);session.accepted=true;callbacks.dropped(session);dirty=true;requestLayout();
    }
    public void onDragEnter(DragObject object){onDragOver(object);}
    public void onDragOver(DragObject object){
        HomeLayout.Cell target=point(object.x,object.y);int page=target.y/(rows*2);boolean valid=acceptDrop(object);
        for(int i=0;i<getChildCount();i++)((CellLayout)getChildAt(i)).preview(i==page?target.x:-1,target.y%(rows*2),valid);
    }
    public void onDragExit(DragObject object){onExitScrollArea();}
    public void onFlingToDelete(DragObject object,int x,int y,PointF velocity){}
    public void scrollLeft(){if(drag!=null)setCurrentPage(getCurrentPage()-1);}
    public void scrollRight(){if(drag!=null)setCurrentPage(getCurrentPage()+1);}
    public boolean onEnterScrollArea(int x,int y,int direction){
        int[] loc=new int[2];getLocationInDragLayer(loc);
        return drag!=null && y>=loc[1] && y<loc[1]+getHeight() && x>=loc[0] && x<loc[0]+getWidth() && (direction==0?getCurrentPage()>0:getCurrentPage()<getChildCount()-1);
    }
    public boolean onExitScrollArea(){for(int i=0;i<getChildCount();i++)((CellLayout)getChildAt(i)).preview(-1,-1,false);return true;}
}
