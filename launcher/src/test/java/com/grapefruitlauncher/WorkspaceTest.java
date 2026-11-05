package com.grapefruitlauncher;

import android.app.Activity;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.ColorDrawable;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowViewGroup;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,qualifiers="w411dp-h820dp-mdpi",shadows=WorkspaceTest.DragRoot.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class WorkspaceTest {
    private Context context;
    private LauncherStore store;
    private final List<AppCatalog.App> apps=new ArrayList<>();
    @Before public void setup(){
        context=RuntimeEnvironment.getApplication();SharedPreferences prefs=context.getSharedPreferences("launcher",0);prefs.edit().clear().commit();
        store=new LauncherStore(prefs);for(int i=0;i<16;i++)add(i);
        StringBuilder home=new StringBuilder();for(int i=0;i<8;i++){if(i>0)home.append('\n');home.append(apps.get(i).id());}
        SharedPreferences.Editor e=prefs.edit().putBoolean("seeded",true).putString("home",home.toString()).putInt("dock_defaults_version",2);
        for(int i=0;i<4;i++)e.putString("dock_"+i,apps.get(i).id()).putBoolean("dock_custom_"+i,true);e.commit();
    }
    private void add(int index){
        String pkg="test.workspace"+index;ComponentName component=new ComponentName(pkg,pkg+".Main");
        AppCatalog.App app=new AppCatalog.App(component,"App "+index);apps.add(app);
        ResolveInfo r=new ResolveInfo();r.nonLocalizedLabel=app.label;r.activityInfo=new ActivityInfo();r.activityInfo.name=component.getClassName();r.activityInfo.packageName=pkg;
        r.activityInfo.enabled=true;r.activityInfo.exported=true;r.activityInfo.applicationInfo=new ApplicationInfo();r.activityInfo.applicationInfo.enabled=true;r.activityInfo.applicationInfo.packageName=pkg;
        Shadows.shadowOf(context.getPackageManager()).addResolveInfoForIntent(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),r);
        Shadows.shadowOf(context.getPackageManager()).addActivityIcon(component,new ColorDrawable(Color.GREEN));
    }
    /** Supply only Android's unavailable window-manager drag handshake; dispatch real framework drag events. */
    @Implements(LauncherDrag.Root.class)
    public static class DragRoot extends ShadowViewGroup {
        @RealObject private LauncherDrag.Root view;
        @Implementation protected boolean startAppDrag(LauncherDrag state,View.DragShadowBuilder shadow){view.dispatchDragEvent(event(DragEvent.ACTION_DRAG_STARTED,0,0,state));return true;}
    }
    private static DragEvent event(int action,float x,float y,Object state){
        DragEvent e=ReflectionHelpers.callConstructor(DragEvent.class);
        ReflectionHelpers.setField(e,"mAction",action);ReflectionHelpers.setField(e,"mX",x);ReflectionHelpers.setField(e,"mY",y);
        ReflectionHelpers.setField(e,"mLocalState",state);ReflectionHelpers.setField(e,"mClipData",ClipData.newPlainText("app","app"));
        ReflectionHelpers.setField(e,"mClipDescription",new ClipDescription("app",new String[]{"text/plain"}));return e;
    }
    private LauncherDrag session(MainActivity a){return ReflectionHelpers.getField(a,"activeDrag");}
    private void drop(MainActivity a,View target,float x,float y){
        LauncherDrag drag=session(a);assertNotNull("A long press starts a drag",drag);
        View root=content(a);int[] to=new int[2],from=new int[2];target.getLocationOnScreen(to);root.getLocationOnScreen(from);
        root.dispatchDragEvent(event(DragEvent.ACTION_DRAG_LOCATION,to[0]-from[0]+x,to[1]-from[1]+y,drag));
        root.dispatchDragEvent(event(DragEvent.ACTION_DROP,to[0]-from[0]+x,to[1]-from[1]+y,drag));
        root.dispatchDragEvent(event(DragEvent.ACTION_DRAG_ENDED,0,0,drag));Shadows.shadowOf(Looper.getMainLooper()).idle();
    }
    @Test public void homeDragLeavesASavedGapAndSurvivesRestart()throws Exception {
        try(ActivityController<MainActivity> c=launch()){
            MainActivity a=c.get();ready(a);measure(content(a),411,820);View cell=appCell(content(a),apps.get(0).id());assertNotNull(cell);
            int w=cell.getWidth(),h=cell.getHeight();cell.performLongClick();HomeSurface home=content(a).findViewById(android.R.id.list);
            drop(a,home.getChildAt(0),w*1.5f,h*3+35);measure(content(a),411,820);
            assertEquals(new HomeLayout.Cell(2,6),store.positions("portrait",store.home()).get(apps.get(0).id()));
            assertEquals(new HomeLayout.Cell(2,0),store.positions("portrait",store.home()).get(apps.get(1).id()));
            c.recreate();ready(c.get());measure(content(c.get()),411,820);View moved=appCell(content(c.get()),apps.get(0).id());
            assertNotNull(moved);assertEquals(h*3,moved.getTop());capture(content(c.get()),"home-placed.png");
        }
    }
    @Test public void drawerDragAddsAShortcutAtTheDropPoint()throws Exception {
        try(ActivityController<MainActivity> c=launch()){
            MainActivity a=c.get();ready(a);measure(content(a),411,820);((ViewGroup)content(a).findViewById(android.R.id.tabs)).getChildAt(2).performClick();measure(content(a),411,820);
            GridView grid=content(a).findViewById(android.R.id.list);int position=index(grid,apps.get(15).id());
            grid.setSelection(position);measure(content(a),411,820);View source=grid.getChildAt(position-grid.getFirstVisiblePosition());assertNotNull(source);
            grid.getOnItemLongClickListener().onItemLongClick(grid,source,position,position);assertNotNull(session(a));
            HomeSurface overlay=ReflectionHelpers.getField(a,"dragHome");measure(content(a),411,820);ViewGroup board=(ViewGroup)overlay.getChildAt(0);
            View sample=board.getChildAt(0);drop(a,board,sample.getWidth()*1.5f,sample.getHeight()*3+35);
            assertTrue(content(a).findViewById(android.R.id.list) instanceof HomeSurface);assertTrue(store.home().contains(apps.get(15).id()));
            assertEquals(new HomeLayout.Cell(2,6),store.positions("portrait",store.home()).get(apps.get(15).id()));
        }
    }
    @Test public void dockSwapAndHomeTransfersKeepAllAppsFixed()throws Exception {
        try(ActivityController<MainActivity> c=launch()){
            MainActivity a=c.get();ready(a);measure(content(a),411,820);ViewGroup dock=content(a).findViewById(android.R.id.tabs);
            dock.getChildAt(0).performLongClick();drop(a,dock.getChildAt(1),20,20);measure(content(a),411,820);
            assertEquals(apps.get(1).id(),store.dock(0));assertEquals(apps.get(0).id(),store.dock(1));
            dock=content(a).findViewById(android.R.id.tabs);assertEquals("All Apps",dock.getChildAt(2).getContentDescription());assertFalse(dock.getChildAt(2).performLongClick());
            View cell=appCell(content(a),apps.get(2).id());cell.performLongClick();drop(a,dock.getChildAt(4),20,20);measure(content(a),411,820);
            assertFalse(store.home().contains(apps.get(2).id()));assertEquals(apps.get(2).id(),store.dock(3));
            dock=content(a).findViewById(android.R.id.tabs);dock.getChildAt(4).performLongClick();
            HomeSurface home=content(a).findViewById(android.R.id.list);ViewGroup board=(ViewGroup)home.getChildAt(0);View sample=board.getChildAt(0);
            drop(a,board,sample.getWidth()*1.5f,sample.getHeight()*3+35);
            assertEquals("",store.dock(3));assertTrue(store.home().contains(apps.get(2).id()));
        }
    }
    @Test public void subgridAndOrientationCoordinatesPersistIndependently()throws Exception {
        Map<String,HomeLayout.Cell> p=new LinkedHashMap<>();p.put(apps.get(0).id(),new HomeLayout.Cell(1,5));store.positions("portrait",p);
        Map<String,HomeLayout.Cell> l=new LinkedHashMap<>();l.put(apps.get(0).id(),new HomeLayout.Cell(4,0));store.positions("landscape",l);
        try(ActivityController<MainActivity> c=launch()){
            MainActivity a=c.get();ready(a);measure(content(a),411,820);described(content(a),"Launcher settings").performClick();
            ((View)text(content(a),"Icon placement").getParent()).performClick();text(ShadowDialog.getLatestDialog().getWindow().getDecorView(),"Half-cell subgrid").performClick();
            text(content(a),"‹  Home").performClick();measure(content(a),411,820);
            View cell=appCell(content(a),apps.get(0).id());assertNotNull(cell);assertEquals(cell.getWidth()/2,cell.getLeft(),1);
            c.recreate();ready(c.get());assertTrue(new LauncherStore(context.getSharedPreferences("launcher",0)).subgrid());
            assertEquals(new HomeLayout.Cell(1,5),store.positions("portrait",store.home()).get(apps.get(0).id()));
            assertEquals(new HomeLayout.Cell(4,0),store.positions("landscape",store.home()).get(apps.get(0).id()));
        }
    }
    @Test @Config(qualifiers="w820dp-h411dp-land-mdpi") public void landscapeDockIsTallAndOnThePhysicalRight()throws Exception {rightDock();}
    @Test @Config(qualifiers="ldrtl-w820dp-h411dp-land-mdpi") public void rightDockDoesNotMoveToTheLeftInRtl()throws Exception {rightDock();}
    private void rightDock()throws Exception {
        try(ActivityController<MainActivity> c=launch()){
            MainActivity a=c.get();ready(a);View root=content(a);measure(root,820,411);LinearLayout dock=root.findViewById(android.R.id.tabs);
            assertEquals(LinearLayout.VERTICAL,dock.getOrientation());assertTrue(dock.getHeight()>dock.getWidth());assertEquals(5,dock.getChildCount());
            int[] at=new int[2];dock.getLocationInWindow(at);assertTrue("Dock stays on the right",at[0]>700);
            for(int i=1;i<5;i++)assertTrue(dock.getChildAt(i).getTop()>dock.getChildAt(i-1).getTop());capture(root,"landscape-right.png");
        }
    }
    @Test public void aboutShowsLauncherWordmarkGplSourcesAndOfflineLicences()throws Exception {
        try(ActivityController<MainActivity> c=launch()){
            MainActivity a=c.get();ready(a);described(content(a),"Launcher settings").performClick();
            ((View)text(content(a),"About Grapefruit Launcher").getParent()).performClick();View root=content(a);measure(root,411,820);
            TextView mark=(TextView)described(root,"Grapefruit Launcher wordmark");assertNotNull(mark);assertSame(LauncherBrand.pixel(context),mark.getTypeface());
            assertNotNull(text(root,"Version 1.0-beta5"));assertNotNull(text(root,"GNU GPL"));assertNotNull(text(root,"Source Code"));capture(root,"about.png");
            ((View)text(root,"Source Code").getParent()).performClick();Intent source=Shadows.shadowOf(a).getNextStartedActivity();assertEquals(LauncherBrand.SOURCE,source.getDataString());
            ((View)text(root,"Open Source Licences").getParent()).performClick();View licence=ShadowDialog.getLatestDialog().getWindow().getDecorView();
            assertTrue(allText(licence).contains("WITHOUT ANY WARRANTY"));assertTrue(allText(licence).contains("SIL OPEN FONT LICENSE"));assertTrue(allText(licence).contains("Version 3, 29 June 2007"));
        }
    }
    @Test public void homeOnlyKeepsNearbyCellsAlive()throws Exception {
        for(int i=16;i<80;i++)add(i);for(AppCatalog.App app:apps)store.add(app.id());
        try(ActivityController<MainActivity> c=launch()){
            MainActivity a=c.get();ready(a);HomeSurface home=content(a).findViewById(android.R.id.list);measure(content(a),411,820);
            ViewGroup board=(ViewGroup)home.getChildAt(0);assertTrue(board.getChildCount()<40);
            home.scrollTo(0,2000);measure(content(a),411,820);assertTrue(board.getChildCount()>0);assertTrue(board.getChildCount()<40);
        }
    }
    private ActivityController<MainActivity> launch(){return Robolectric.buildActivity(MainActivity.class).setup().visible();}
    private void ready(MainActivity a)throws Exception {long end=System.currentTimeMillis()+15000;while(System.currentTimeMillis()<end){Shadows.shadowOf(Looper.getMainLooper()).idleFor(40,TimeUnit.MILLISECONDS);HomeSurface home=content(a).findViewById(android.R.id.list);if(home.appCount()>=8)return;Thread.sleep(10);}fail("Home did not load");}
    private static View content(Activity a){return ((ViewGroup)a.findViewById(android.R.id.content)).getChildAt(0);}
    private static void measure(View view,int width,int height){view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));view.layout(0,0,width,height);}
    private static View appCell(View v,String id){Object tag=v.getTag();if(tag instanceof AppCatalog.App && ((AppCatalog.App)tag).id().equals(id))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View result=appCell(((ViewGroup)v).getChildAt(i),id);if(result!=null)return result;}return null;}
    private static int index(GridView grid,String id){for(int i=0;i<grid.getAdapter().getCount();i++)if(((AppCatalog.App)grid.getAdapter().getItem(i)).id().equals(id))return i;throw new AssertionError(id);}
    private static TextView text(View v,String text){if(v instanceof TextView && text.contentEquals(((TextView)v).getText()))return(TextView)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){TextView found=text(((ViewGroup)v).getChildAt(i),text);if(found!=null)return found;}return null;}
    private static View described(View v,String description){if(description.contentEquals(String.valueOf(v.getContentDescription())))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View found=described(((ViewGroup)v).getChildAt(i),description);if(found!=null)return found;}return null;}
    private static String allText(View v){StringBuilder text=new StringBuilder();if(v instanceof TextView)text.append(((TextView)v).getText());if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)text.append(allText(((ViewGroup)v).getChildAt(i)));return text.toString();}
    private static void capture(View view,String name)throws Exception{File dir=new File("../work/launcher/previews/beta5");dir.mkdirs();Bitmap bitmap=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);view.draw(new Canvas(bitmap));try(FileOutputStream out=new FileOutputStream(new File(dir,name))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();}
}
