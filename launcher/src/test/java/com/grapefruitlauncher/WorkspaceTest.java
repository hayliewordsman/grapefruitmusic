package com.grapefruitlauncher;

import android.app.Activity;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.ColorDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;
import com.grapefruitlauncher.launcher2.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,qualifiers="w411dp-h820dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class WorkspaceTest {
    private Context context;
    private LauncherStore store;
    private final List<AppCatalog.App> apps=new ArrayList<>();
    private long down;
    @Before public void setup(){
        context=RuntimeEnvironment.getApplication();SharedPreferences prefs=context.getSharedPreferences("launcher",0);prefs.edit().clear().commit();
        store=new LauncherStore(prefs);for(int i=0;i<16;i++)add(i);
        StringBuilder home=new StringBuilder();for(int i=0;i<8;i++){if(i>0)home.append('\n');home.append(apps.get(i).id());}
        SharedPreferences.Editor e=prefs.edit().putBoolean("seeded",true).putString("home",home.toString()).putInt("dock_defaults_version",2);
        for(int i=0;i<4;i++)e.putString("dock_"+i,apps.get(i).id()).putBoolean("dock_custom_"+i,true);e.commit();
    }
    private void add(int index){
        String pkg="test.workspace"+index;ComponentName component=new ComponentName(pkg,pkg+".Main");AppCatalog.App app=new AppCatalog.App(component,"App "+index);apps.add(app);
        ResolveInfo r=new ResolveInfo();r.nonLocalizedLabel=app.label;r.activityInfo=new ActivityInfo();r.activityInfo.name=component.getClassName();r.activityInfo.packageName=pkg;r.activityInfo.enabled=true;r.activityInfo.exported=true;r.activityInfo.applicationInfo=new ApplicationInfo();r.activityInfo.applicationInfo.enabled=true;r.activityInfo.applicationInfo.packageName=pkg;
        Shadows.shadowOf(context.getPackageManager()).addResolveInfoForIntent(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),r);Shadows.shadowOf(context.getPackageManager()).addActivityIcon(component,new ColorDrawable(0xff67c7ff));
    }
    private LauncherDrag session(MainActivity a){return ReflectionHelpers.getField(a,"activeDrag");}
    private DragLayer root(MainActivity a){return (DragLayer)content(a);}
    private void touch(MainActivity a,int action,View target,float x,float y){
        int[] to=new int[2],from=new int[2];target.getLocationOnScreen(to);root(a).getLocationOnScreen(from);
        MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,to[0]-from[0]+x,to[1]-from[1]+y,0);root(a).dispatchTouchEvent(event);event.recycle();
    }
    private void hold(MainActivity a,View source){
        down=SystemClock.uptimeMillis();touch(a,MotionEvent.ACTION_DOWN,source,source.getWidth()/2f,Math.min(35,source.getHeight()/2f));
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(ViewConfiguration.getLongPressTimeout()+50,TimeUnit.MILLISECONDS);
        assertNotNull("An actual finger long press starts Launcher2 drag",session(a));assertTrue(root(a).getDragController().isDragging());
    }
    private void release(MainActivity a,View target,float x,float y){
        touch(a,MotionEvent.ACTION_MOVE,target,x,y);touch(a,MotionEvent.ACTION_UP,target,x,y);Shadows.shadowOf(Looper.getMainLooper()).idle();measure(content(a),411,820);
        assertNull(session(a));assertFalse(root(a).getDragController().isDragging());assertNull(root(a).getDragController().getDragView());assertNull(ReflectionHelpers.getField(a,"dragStrip"));
    }
    private HomeSurface home(MainActivity a){return content(a).findViewById(android.R.id.list);}
    @Test public void fingerDragReleasesOnUpAndLeavesSavedGap()throws Exception {
        try(ActivityController<MainActivity> c=launch()){
            MainActivity a=c.get();ready(a);measure(content(a),411,820);View source=appCell(content(a),apps.get(0).id());int w=source.getWidth(),h=source.getHeight();
            hold(a,source);release(a,home(a).getChildAt(0),w*1.5f+12,h*2+47);
            assertEquals(1,source.getAlpha(),0);assertNotNull(text(content(a),"Home"));assertNotNull(text(content(a),"Edit"));assertNull(ReflectionHelpers.getField(home(a),"drag"));
            HomeLayout.Cell position=home(a).coordinates().get(apps.get(0).id());assertEquals(new HomeLayout.Cell(2,4),position);
            assertEquals(new HomeLayout.Cell(2,0),home(a).coordinates().get(apps.get(1).id()));
            c.recreate();ready(c.get());measure(content(c.get()),411,820);assertEquals(position,home(c.get()).coordinates().get(apps.get(0).id()));capture(content(c.get()),"home.png");
        }
    }
    @Test @Config(sdk=35) public void newerAndroidUsesTheSameFingerDragLifecycle()throws Exception {fingerDragReleasesOnUpAndLeavesSavedGap();}
    @Test public void releaseWithoutMoveStillCompletesAndDoesNotLaunchApp()throws Exception {
        try(ActivityController<MainActivity> c=launch()){
            MainActivity a=c.get();ready(a);measure(content(a),411,820);View source=appCell(content(a),apps.get(0).id());hold(a,source);
            touch(a,MotionEvent.ACTION_UP,source,source.getWidth()/2f,35);Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertNull(session(a));assertFalse(root(a).getDragController().isDragging());assertNull(Shadows.shadowOf(a).getNextStartedActivity());assertEquals(1,source.getAlpha(),0);
        }
    }
    @Test public void drawerFingerDragAddsShortcutAndRestoresHomeHeader()throws Exception {
        try(ActivityController<MainActivity> c=launch()){
            MainActivity a=c.get();ready(a);measure(content(a),411,820);((ViewGroup)content(a).findViewById(android.R.id.tabs)).getChildAt(2).performClick();measure(content(a),411,820);
            GridView grid=content(a).findViewById(android.R.id.list);int position=index(grid,apps.get(15).id());View source=grid.getChildAt(position-grid.getFirstVisiblePosition());assertNotNull(source);hold(a,source);
            HomeSurface overlay=ReflectionHelpers.getField(a,"dragHome");View page=overlay.getChildAt(0);View sample=((ViewGroup)page).getChildAt(0);
            release(a,page,sample.getWidth()*1.5f+12,sample.getHeight()*2+47);
            assertTrue(store.home().contains(apps.get(15).id()));assertNotNull(text(content(a),"Home"));assertNull(ReflectionHelpers.getField(a,"dragHome"));
        }
    }
    @Test public void rejectedDrawerDropAndCancelRestoreSearch()throws Exception {
        try(ActivityController<MainActivity> c=launch()){
            MainActivity a=c.get();ready(a);measure(content(a),411,820);((ViewGroup)content(a).findViewById(android.R.id.tabs)).getChildAt(2).performClick();
            View searchRow=ReflectionHelpers.getField(a,"searchRow");EditText search=(EditText)((ViewGroup)searchRow).getChildAt(0);search.setText("App 15");measure(content(a),411,820);
            GridView grid=content(a).findViewById(android.R.id.list);View source=grid.getChildAt(0);hold(a,source);
            HomeSurface overlay=ReflectionHelpers.getField(a,"dragHome");ViewGroup page=(ViewGroup)overlay.getChildAt(0);release(a,page,page.getChildAt(0).getWidth()/2f+12,47);
            assertFalse(store.home().contains(apps.get(15).id()));assertEquals(8,store.home().size());assertEquals(View.VISIBLE,grid.getVisibility());assertEquals(View.VISIBLE,searchRow.getVisibility());assertEquals("App 15",search.getText().toString());
            hold(a,grid.getChildAt(0));touch(a,MotionEvent.ACTION_CANCEL,root(a),0,0);Shadows.shadowOf(Looper.getMainLooper()).idle();assertNull(session(a));assertEquals("App 15",search.getText().toString());
        }
    }
    @Test public void dockSwapAndHomeTransfersUseSameTouchController()throws Exception {
        try(ActivityController<MainActivity> c=launch()){
            MainActivity a=c.get();ready(a);measure(content(a),411,820);ViewGroup dock=content(a).findViewById(android.R.id.tabs);
            hold(a,dock.getChildAt(0));release(a,dock.getChildAt(1),25,25);assertEquals(apps.get(1).id(),store.dock(0));assertEquals(apps.get(0).id(),store.dock(1));
            dock=content(a).findViewById(android.R.id.tabs);assertFalse(dock.getChildAt(2).performLongClick());
            hold(a,appCell(content(a),apps.get(2).id()));release(a,dock.getChildAt(4),25,25);assertFalse(store.home().contains(apps.get(2).id()));assertEquals(apps.get(2).id(),store.dock(3));
            dock=content(a).findViewById(android.R.id.tabs);hold(a,dock.getChildAt(4));ViewGroup page=(ViewGroup)home(a).getChildAt(0);View sample=page.getChildAt(0);release(a,page,sample.getWidth()*1.5f+12,sample.getHeight()*2+47);
            assertEquals("",store.dock(3));assertTrue(store.home().contains(apps.get(2).id()));
        }
    }
    @Test public void removeTargetAndConsecutiveDragsReleaseEverything()throws Exception {
        try(ActivityController<MainActivity> c=launch()){
            MainActivity a=c.get();ready(a);measure(content(a),411,820);hold(a,appCell(content(a),apps.get(0).id()));View remove=described(content(a),"Drop: Remove shortcut");release(a,remove,remove.getWidth()/2f,remove.getHeight()/2f);
            assertFalse(store.home().contains(apps.get(0).id()));hold(a,appCell(content(a),apps.get(1).id()));a.onBackPressed();Shadows.shadowOf(Looper.getMainLooper()).idle();assertNull(session(a));assertFalse(root(a).getDragController().isDragging());assertNull(ReflectionHelpers.getField(a,"dragStrip"));
        }
    }
    @Test public void settingsUseGrapefruitToggleAndRoundBackHeader()throws Exception {
        Map<String,HomeLayout.Cell> positions=new LinkedHashMap<>();positions.put(apps.get(0).id(),new HomeLayout.Cell(1,3));store.positions("portrait",positions);
        try(ActivityController<MainActivity> c=launch()){
            MainActivity a=c.get();ready(a);described(content(a),"Launcher settings").performClick();measure(content(a),411,820);
            View toggle=described(content(a),"Half-cell subgrid");assertTrue(toggle instanceof Toggle);toggle.performClick();assertTrue(store.subgrid());described(content(a),"Lock layout").performClick();assertTrue(store.locked());Shadows.shadowOf(Looper.getMainLooper()).idleFor(250,TimeUnit.MILLISECONDS);capture(content(a),"settings.png");
            described(content(a),"Back to Home").performClick();measure(content(a),411,820);assertEquals(new HomeLayout.Cell(1,3),home(a).coordinates().get(apps.get(0).id()));
            View source=appCell(content(a),apps.get(0).id());down=SystemClock.uptimeMillis();touch(a,MotionEvent.ACTION_DOWN,source,source.getWidth()/2f,35);Shadows.shadowOf(Looper.getMainLooper()).idleFor(600,TimeUnit.MILLISECONDS);assertNull(session(a));
            touch(a,MotionEvent.ACTION_CANCEL,root(a),0,0);c.recreate();ready(c.get());assertTrue(store.locked());assertTrue(store.subgrid());
        }
    }
    @Test public void workspacePagesSwipeAndBoundResidentViews()throws Exception {
        for(int i=16;i<80;i++)add(i);for(AppCatalog.App app:apps)store.add(app.id());
        try(ActivityController<MainActivity> c=launch()){
            MainActivity a=c.get();ready(a);measure(content(a),411,820);HomeSurface home=home(a);assertTrue(home.getChildCount()>1);
            int live=0;for(int i=0;i<home.getChildCount();i++)live+=((ViewGroup)home.getChildAt(i)).getChildCount();assertTrue(live<50);
            down=SystemClock.uptimeMillis();touch(a,MotionEvent.ACTION_DOWN,home,350,200);Shadows.shadowOf(Looper.getMainLooper()).idleFor(30,TimeUnit.MILLISECONDS);touch(a,MotionEvent.ACTION_MOVE,home,200,200);Shadows.shadowOf(Looper.getMainLooper()).idleFor(30,TimeUnit.MILLISECONDS);touch(a,MotionEvent.ACTION_MOVE,home,60,200);Shadows.shadowOf(Looper.getMainLooper()).idleFor(30,TimeUnit.MILLISECONDS);touch(a,MotionEvent.ACTION_UP,home,50,200);
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(500,TimeUnit.MILLISECONDS);home.computeScroll();assertEquals(1,home.getCurrentPage());assertNull(session(a));capture(content(a),"home-page-two.png");
        }
    }
    @Test public void edgeHoldChangesPageAndDropsUnderTheFinger()throws Exception {
        for(int i=16;i<48;i++)add(i);for(AppCatalog.App app:apps)store.add(app.id());
        try(ActivityController<MainActivity> c=launch()){
            MainActivity a=c.get();ready(a);measure(content(a),411,820);hold(a,appCell(content(a),apps.get(0).id()));HomeSurface home=home(a);
            touch(a,MotionEvent.ACTION_MOVE,home,home.getWidth()-5,120);
            int[] where=new int[2];home.getLocationInDragLayer(where);
            assertTrue("Home can page at the edge: "+where[0]+","+where[1]+" size "+home.getWidth()+","+home.getHeight(),home.onEnterScrollArea(home.getWidth()-5,where[1]+120,1));
            assertEquals("Controller scheduled edge scroll, pointer "+java.util.Arrays.toString((int[])ReflectionHelpers.getField(root(a).getDragController(),"mLastTouch")),1,(int)ReflectionHelpers.getField(root(a).getDragController(),"mScrollState"));
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(550,TimeUnit.MILLISECONDS);measure(content(a),411,820);
            assertEquals("Edge hold switches page",1,home.getCurrentPage());LauncherDrag drag=session(a);release(a,home,60,47);assertTrue("The drop is accepted",drag.accepted);assertEquals("The saved shortcut is on the visible page",1,home(a).coordinates().get(apps.get(0).id()).y/(home(a).rows()*2));
        }
    }
    @Test @Config(qualifiers="w820dp-h411dp-land-mdpi") public void landscapeDockIsTallAndOnThePhysicalRight()throws Exception {rightDock();}
    @Test @Config(qualifiers="ldrtl-w820dp-h411dp-land-mdpi") public void rtlKeepsDockOnPhysicalRight()throws Exception {rightDock();}
    private void rightDock()throws Exception {
        try(ActivityController<MainActivity> c=launch()){
            MainActivity a=c.get();ready(a);View root=content(a);measure(root,820,411);LinearLayout dock=root.findViewById(android.R.id.tabs);assertEquals(LinearLayout.VERTICAL,dock.getOrientation());assertTrue(dock.getHeight()>dock.getWidth());
            int[] at=new int[2];dock.getLocationInWindow(at);assertTrue(at[0]>700);for(int i=1;i<5;i++)assertTrue(dock.getChildAt(i).getTop()>dock.getChildAt(i-1).getTop());capture(root,"landscape.png");
        }
    }
    @Test public void aboutDeclaresUpstreamAndAllOfflineLicences()throws Exception {
        try(ActivityController<MainActivity> c=launch()){
            MainActivity a=c.get();ready(a);described(content(a),"Launcher settings").performClick();((View)text(content(a),"About Grapefruit Launcher").getParent()).performClick();measure(content(a),411,820);
            TextView mark=(TextView)described(content(a),"Grapefruit Launcher wordmark");assertSame(LauncherBrand.pixel(context),mark.getTypeface());assertNotNull(text(content(a),"Version 1.0-beta7"));assertNotNull(text(content(a),"AOSP Launcher2"));capture(content(a),"about.png");
            ((View)text(content(a),"Open Source Licences").getParent()).performClick();String licences=allText(ShadowDialog.getLatestDialog().getWindow().getDecorView());assertTrue(licences.contains("WITHOUT ANY WARRANTY"));assertTrue(licences.contains("Apache License"));assertTrue(licences.contains("SIL OPEN FONT LICENSE"));
        }
    }
    private ActivityController<MainActivity> launch(){return Robolectric.buildActivity(MainActivity.class).setup().visible();}
    private void ready(MainActivity a)throws Exception{long end=System.currentTimeMillis()+15000;while(System.currentTimeMillis()<end){Shadows.shadowOf(Looper.getMainLooper()).idleFor(40,TimeUnit.MILLISECONDS);HomeSurface home=home(a);if(home.appCount()>=8)return;Thread.sleep(10);}fail("Home did not load");}
    private static View content(Activity a){return ((ViewGroup)a.findViewById(android.R.id.content)).getChildAt(0);}
    private static void measure(View view,int width,int height){view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));view.layout(0,0,width,height);}
    private static View appCell(View view,String id){Object tag=view.getTag();if(tag instanceof AppCatalog.App && ((AppCatalog.App)tag).id().equals(id))return view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View found=appCell(((ViewGroup)view).getChildAt(i),id);if(found!=null)return found;}return null;}
    private static int index(GridView grid,String id){for(int i=0;i<grid.getAdapter().getCount();i++)if(((AppCatalog.App)grid.getAdapter().getItem(i)).id().equals(id))return i;throw new AssertionError(id);}
    private static TextView text(View view,String value){if(view instanceof TextView && value.contentEquals(((TextView)view).getText()))return (TextView)view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){TextView found=text(((ViewGroup)view).getChildAt(i),value);if(found!=null)return found;}return null;}
    private static View described(View view,String value){if(value.contentEquals(String.valueOf(view.getContentDescription())))return view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View found=described(((ViewGroup)view).getChildAt(i),value);if(found!=null)return found;}return null;}
    private static String allText(View view){StringBuilder out=new StringBuilder();if(view instanceof TextView)out.append(((TextView)view).getText());if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)out.append(allText(((ViewGroup)view).getChildAt(i)));return out.toString();}
    private static void capture(View view,String name)throws Exception{File dir=new File("../work/launcher/previews/beta7");dir.mkdirs();Bitmap bitmap=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);view.draw(new Canvas(bitmap));try(FileOutputStream out=new FileOutputStream(new File(dir,name))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();}
}
