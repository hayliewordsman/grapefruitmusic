package com.grapefruitlauncher;

import android.app.Activity;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.ColorDrawable;
import android.view.*;
import android.widget.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowDialog;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,qualifiers="w411dp-h820dp-mdpi")
public class LauncherTest {
    private Context context;
    @Before public void setup() {
        context=RuntimeEnvironment.getApplication();
        context.getSharedPreferences("launcher",0).edit().clear().commit();
        addApp("io.github.hayliewordsman.grapefruitmusic","Grapefruit Audio");addApp("test.camera","Camera");addApp("test.mail","Mail");
        addApp("test.browser","Browser");addApp("test.maps","Maps");addApp("test.long","A very long app label that must fit its chip");
        addApp("test.tv","Grapefruit TV");
        addApp("com.android.dialer","Phone");addApp("com.android.mms","Messages");addApp("com.android.chrome","Chrome");
        handler("com.android.dialer",new Intent(Intent.ACTION_DIAL,android.net.Uri.parse("tel:")));
        handler("com.android.mms",new Intent(Intent.ACTION_SENDTO,android.net.Uri.parse("smsto:")));
    }
    private void addApp(String pkg,String label) {
        ResolveInfo r=new ResolveInfo();r.nonLocalizedLabel=label;r.activityInfo=new ActivityInfo();
        r.activityInfo.name=pkg+".Main";r.activityInfo.packageName=pkg;r.activityInfo.enabled=true;r.activityInfo.exported=true;
        r.activityInfo.applicationInfo=new ApplicationInfo();r.activityInfo.applicationInfo.packageName=pkg;r.activityInfo.applicationInfo.enabled=true;
        Shadows.shadowOf(context.getPackageManager()).addResolveInfoForIntent(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),r);
        android.graphics.drawable.Drawable icon;
        if(pkg.endsWith("grapefruitmusic") || pkg.equals("test.tv")) {
            String module=pkg.endsWith("grapefruitmusic")?"app":"tv";
            icon=android.graphics.drawable.Drawable.createFromPath("../"+module+"/src/main/res/mipmap-xxxhdpi/ic_launcher.png");
        } else {
            int id=pkg.equals("test.camera")?android.R.drawable.ic_menu_camera:pkg.equals("test.mail")?android.R.drawable.ic_dialog_email:
                pkg.equals("test.maps")?android.R.drawable.ic_dialog_map:android.R.drawable.ic_menu_view;
            icon=context.getResources().getDrawable(id);
        }
        if(icon!=null)Shadows.shadowOf(context.getPackageManager()).addActivityIcon(new ComponentName(pkg,pkg+".Main"),icon);
    }
    private void handler(String pkg,Intent intent) {
        ResolveInfo r=new ResolveInfo();r.activityInfo=new ActivityInfo();r.activityInfo.packageName=pkg;
        r.activityInfo.name=pkg+".Main";r.activityInfo.enabled=true;r.activityInfo.exported=true;r.isDefault=true;
        Shadows.shadowOf(context.getPackageManager()).addResolveInfoForIntent(intent,r);
    }
    @Test public void allAppsSearchDockReplacementAndHomeReset() throws Exception {
        try(ActivityController<MainActivity> controller=launch()) {
            MainActivity a=controller.get();waitForApps(a);View root=content(a);measure(root,411,820);
            ViewGroup dock=root.findViewById(android.R.id.tabs);assertEquals(5,dock.getChildCount());
            assertEquals("All Apps",dock.getChildAt(2).getContentDescription());
            assertEquals("dock has no text labels",0,countText(dock));
            dock.getChildAt(2).performClick();root=content(a);measure(root,411,820);
            EditText search=findEdit(root);assertNotNull(search);search.setText("camera");
            GridView grid=root.findViewById(android.R.id.list);assertEquals(1,grid.getAdapter().getCount());
            assertEquals("Camera",((AppCatalog.App)grid.getAdapter().getItem(0)).label);
            search.setText("test.mail");assertEquals(1,grid.getAdapter().getCount());
            grid.performItemClick(grid.getChildAt(0),0,grid.getAdapter().getItemId(0));
            Intent launched=Shadows.shadowOf(a).getNextStartedActivity();assertEquals("test.mail",launched.getComponent().getPackageName());
            // An actual HOME intent closes search and restores the Home page.
            controller.newIntent(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME));
            assertNull(findEdit(content(a)));assertNotNull(text(content(a),"Home"));
            // Replace a populated dock slot using the searchable picker.
            text(content(a),"Edit").performClick();dock=content(a).findViewById(android.R.id.tabs);dock.getChildAt(0).performClick();
            text(ShadowDialog.getLatestDialog().getWindow().getDecorView(),"Replace dock app").performClick();
            root=content(a);findEdit(root).setText("Maps");grid=root.findViewById(android.R.id.list);
            grid.performItemClick(null,0,0);
            LauncherStore store=new LauncherStore(context.getSharedPreferences("launcher",0));
            assertEquals("test.maps/test.maps.Main",store.dock(0));
            dock=content(a).findViewById(android.R.id.tabs);assertEquals(5,dock.getChildCount());assertEquals(0,countText(dock));
            assertTrue(dock.getChildAt(0).getContentDescription().toString().startsWith("Maps"));
            controller.recreate();waitForApps(controller.get());
            assertEquals("test.maps/test.maps.Main",store.dock(0));
        }
    }
    @Test public void storeKeepsHomeOrderAndPreventsDuplicateDockApps() {
        LauncherStore s=new LauncherStore(context.getSharedPreferences("launcher",0));
        s.add("a/A");s.add("b/B");s.add("c/C");s.add("a/A");s.move("c/C",-1);
        assertEquals(java.util.Arrays.asList("a/A","c/C","b/B"),s.home());s.remove("c/C");
        s.dock(0,"a/A");s.dock(3,"a/A");assertEquals("",s.dock(0));assertEquals("a/A",s.dock(3));
        try{s.dock(4,"x/X");fail("A fifth app would displace All Apps");}catch(IllegalArgumentException expected){}
        s.theme(3);LauncherStore restored=new LauncherStore(context.getSharedPreferences("launcher",0));
        assertEquals(3,restored.theme());assertEquals(java.util.Arrays.asList("a/A","b/B"),restored.home());
        restored.seed(AppCatalog.load(context));assertEquals(java.util.Arrays.asList("a/A","b/B"),restored.home());
    }
    @Test public void removingHomeAppDoesNotRemoveItFromAllApps() throws Exception {
        try(ActivityController<MainActivity> controller=launch()) {
            MainActivity a=controller.get();waitForApps(a);LauncherStore store=new LauncherStore(context.getSharedPreferences("launcher",0));
            String id="io.github.hayliewordsman.grapefruitmusic/io.github.hayliewordsman.grapefruitmusic.Main";store.remove(id);
            controller.newIntent(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME));
            HomeSurface home=content(a).findViewById(android.R.id.list);assertFalse(home.contains(id));
            GridView grid;
            ((ViewGroup)content(a).findViewById(android.R.id.tabs)).getChildAt(2).performClick();
            findEdit(content(a)).setText("Audio");grid=content(a).findViewById(android.R.id.list);assertEquals(1,grid.getAdapter().getCount());
        }
    }
    @Test public void packageInstallAndRemovalRefreshTheOpenDrawer() throws Exception {
        try(ActivityController<MainActivity> controller=launch()) {
            MainActivity a=controller.get();waitForApps(a);
            ((ViewGroup)content(a).findViewById(android.R.id.tabs)).getChildAt(2).performClick();
            waitForApps(a);addApp("test.newapp","New app");
            context.sendBroadcast(new Intent(Intent.ACTION_PACKAGE_ADDED,android.net.Uri.parse("package:test.newapp")));
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            long deadline=System.currentTimeMillis()+5000;
            do {Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();Thread.sleep(10);findEdit(content(a)).setText("New app");}
            while(((GridView)content(a).findViewById(android.R.id.list)).getAdapter().getCount()==0 && System.currentTimeMillis()<deadline);
            assertEquals(1,((GridView)content(a).findViewById(android.R.id.list)).getAdapter().getCount());
            Shadows.shadowOf(context.getPackageManager()).removeResolveInfosForIntent(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),"test.newapp");
            context.sendBroadcast(new Intent(Intent.ACTION_PACKAGE_REMOVED,android.net.Uri.parse("package:test.newapp")));
            deadline=System.currentTimeMillis()+5000;
            do{Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();Thread.sleep(10);}
            while(((GridView)content(a).findViewById(android.R.id.list)).getAdapter().getCount()!=0 && System.currentTimeMillis()<deadline);
            assertEquals(0,((GridView)content(a).findViewById(android.R.id.list)).getAdapter().getCount());
        }
    }
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void everyThemeDrawsEveryIconOnATeardropPlate() {
        for(int id=0;id<4;id++) {
            LauncherTheme t=new LauncherTheme(context,id);
            Bitmap b=IconPack.render(t,new ColorDrawable(Color.GREEN),128);
            assertEquals("top-left stays outside the circle",0,Color.alpha(b.getPixel(2,2)));
            assertTrue("lower right extends beyond a circle",Color.alpha(b.getPixel(116,116))>0);
            assertEquals("original artwork is preserved",Color.GREEN,b.getPixel(64,64));
            assertTrue("plate surrounds artwork",Color.alpha(b.getPixel(64,8))>0);
            assertNotEquals("each palette colors the plate",Color.GREEN,b.getPixel(64,8));
        }
        assertEquals(0xff07162b,new LauncherTheme(context,3).bg);
        assertEquals(0xff000000,new LauncherTheme(context,2).bg);
        Bitmap adaptive=IconPack.render(new LauncherTheme(context,3),new android.graphics.drawable.AdaptiveIconDrawable(new ColorDrawable(Color.BLUE),new ColorDrawable(Color.RED)),128);
        assertTrue(Color.alpha(adaptive.getPixel(64,64))>0);
    }
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void themesPersistAndPortraitAndLandscapeRender() throws Exception {
        try(ActivityController<MainActivity> controller=launch()) {
            MainActivity a=controller.get();waitForApps(a);
            for(int id=0;id<4;id++) {
                described(content(a),"Launcher settings").performClick();
                ((View)text(content(a),"Theme").getParent()).performClick();
                String label=LauncherTheme.NAMES[id]+(id==new LauncherStore(context.getSharedPreferences("launcher",0)).theme()?", selected":"");
                described(ShadowDialog.getLatestDialog().getWindow().getDecorView(),label).performClick();described(content(a),"Back to Home").performClick();
                View root=content(a);measure(root,411,820);waitForImages(root);
                Bitmap b=Bitmap.createBitmap(411,820,Bitmap.Config.ARGB_8888);root.draw(new Canvas(b));
                assertEquals(new LauncherTheme(context,id).bg,b.getPixel(0,400));
                capture(root,"theme-"+id+".png");
            }
            controller.recreate();waitForApps(controller.get());
            assertEquals(3,new LauncherStore(context.getSharedPreferences("launcher",0)).theme());
            RuntimeEnvironment.setQualifiers("w820dp-h411dp-land-mdpi");controller.configurationChange();
            waitForApps(controller.get());measure(content(controller.get()),820,411);waitForImages(content(controller.get()));capture(content(controller.get()),"landscape.png");
            assertEquals(5,((ViewGroup)content(controller.get()).findViewById(android.R.id.tabs)).getChildCount());
        }
    }
    @Test @Config(sdk=21,qualifiers="w320dp-h480dp-mdpi")
    public void oldAndroidStartsAndCanOpenDrawerAndSettings() throws Exception {smoke();}
    @Test @Config(sdk=35)
    public void modernAndroidStartsAndCanOpenDrawerAndSettings() throws Exception {smoke();}
    private void smoke() throws Exception {
        try(ActivityController<MainActivity> c=launch()) {
            MainActivity a=c.get();waitForApps(a);measure(content(a),320,480);
            ((ViewGroup)content(a).findViewById(android.R.id.tabs)).getChildAt(2).performClick();assertNotNull(findEdit(content(a)));
            described(content(a),"Launcher settings").performClick();((View)text(content(a),"Theme").getParent()).performClick();assertNotNull(text(ShadowDialog.getLatestDialog().getWindow().getDecorView(),"Victory Blue"));
            a.onBackPressed();assertNotNull(text(content(a),"Home"));
        }
    }
    private ActivityController<MainActivity> launch(){return Robolectric.buildActivity(MainActivity.class).setup().visible();}
    private static View content(Activity a){return ((ViewGroup)a.findViewById(android.R.id.content)).getChildAt(0);}
    private static void waitForApps(MainActivity a)throws Exception {
        long deadline=System.currentTimeMillis()+5000;
        while(System.currentTimeMillis()<deadline){Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            View list=content(a).findViewById(android.R.id.list);if(list instanceof HomeSurface && ((HomeSurface)list).appCount()>=6)return;if(list instanceof GridView && ((GridView)list).getAdapter()!=null && ((GridView)list).getAdapter().getCount()>=6)return;Thread.sleep(10);}
        fail("App discovery did not finish");
    }
    private static void measure(View v,int w,int h){v.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));v.layout(0,0,w,h);}
    private static TextView text(View v,String value){if(v instanceof TextView&&value.contentEquals(((TextView)v).getText()))return(TextView)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){TextView t=text(((ViewGroup)v).getChildAt(i),value);if(t!=null)return t;}return null;}
    private static View described(View v,String value){if(value.contentEquals(String.valueOf(v.getContentDescription())))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View found=described(((ViewGroup)v).getChildAt(i),value);if(found!=null)return found;}return null;}
    private static EditText findEdit(View v){if(v instanceof EditText)return(EditText)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){EditText found=findEdit(((ViewGroup)v).getChildAt(i));if(found!=null)return found;}return null;}
    private static int countText(View v){int n=v instanceof TextView?1:0;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)n+=countText(((ViewGroup)v).getChildAt(i));return n;}
    private static void capture(View view,String name)throws Exception {
        java.io.File dir=new java.io.File("../work/launcher/previews");dir.mkdirs();
        Bitmap b=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);view.draw(new Canvas(b));
        try(java.io.OutputStream out=new java.io.FileOutputStream(new java.io.File(dir,name))){b.compress(Bitmap.CompressFormat.PNG,100,out);}
    }
    private static void waitForImages(View root)throws Exception {
        long end=System.currentTimeMillis()+5000;
        while(!imagesReady(root)&&System.currentTimeMillis()<end){Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();Thread.sleep(10);}
        assertTrue("Icon workers completed",imagesReady(root));
    }
    private static boolean imagesReady(View v) {
        if(v instanceof ImageView && v.getTag()!=null && !(((ImageView)v).getDrawable() instanceof android.graphics.drawable.BitmapDrawable))return false;
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)if(!imagesReady(((ViewGroup)v).getChildAt(i)))return false;
        return true;
    }
}
