package com.grapefruitlauncher;

import android.app.ActivityManager;
import android.content.*;
import android.content.pm.*;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import java.io.File;
import java.io.FileOutputStream;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.android.controller.ActivityController;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,qualifiers="w1024dp-h600dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class IconDiskCacheTest {
    private Context context;
    private IconPack icons;
    private final List<AppCatalog.App> apps=new ArrayList<>();
    private final List<AtomicInteger> draws=new ArrayList<>();
    @Before public void setup() {
        context=RuntimeEnvironment.getApplication();
        Shadows.shadowOf((ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE)).setMemoryClass(32);
        context.getSharedPreferences("launcher",0).edit().clear().commit();
        context.getSharedPreferences("icon-cache",0).edit().clear().commit();
        delete(new File(context.getFilesDir(),"launcher-icons"));
        icons=new IconPack(context);icons.screenChanged(0);
    }
    @After public void close(){icons.close();}
    private void add(int index,boolean launchable) {
        String pkg="test.persist"+index;ComponentName component=new ComponentName(pkg,pkg+".Main");
        apps.add(new AppCatalog.App(component,"App "+index));AtomicInteger count=new AtomicInteger();draws.add(count);
        setArtwork(index,Color.RED);
        if(launchable) {
            ResolveInfo r=new ResolveInfo();r.nonLocalizedLabel="App "+index;r.activityInfo=new ActivityInfo();
            r.activityInfo.packageName=pkg;r.activityInfo.name=component.getClassName();r.activityInfo.enabled=true;r.activityInfo.exported=true;
            r.activityInfo.applicationInfo=new ApplicationInfo();r.activityInfo.applicationInfo.enabled=true;r.activityInfo.applicationInfo.packageName=pkg;
            Shadows.shadowOf(context.getPackageManager()).addResolveInfoForIntent(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),r);
        }
    }
    private void setArtwork(int index,int color) {
        AtomicInteger count=draws.get(index);
        ColorDrawable art=new ColorDrawable(color){@Override public void draw(Canvas canvas){count.incrementAndGet();super.draw(canvas);}};
        Shadows.shadowOf(context.getPackageManager()).addActivityIcon(apps.get(index).component,art);
    }
    private ImageView bind(AppCatalog.App app,int theme) {
        ImageView view=new ImageView(context);icons.bind(view,app,new LauncherTheme(context,theme));
        assertTrue("Prepared icons must appear synchronously, without a loading plate",view.getDrawable() instanceof BitmapDrawable);
        return view;
    }
    @Test public void everyAppAndThemePersistsAcrossMemoryCleanupAndRestart()throws Exception {
        for(int i=0;i<40;i++)add(i,false);
        AtomicInteger progress=new AtomicInteger();assertEquals(0,icons.prepare(apps,(count,total)->{assertEquals(40,total);progress.set(count);}));
        assertEquals(40,progress.get());assertEquals(160,files().length);
        for(int i=0;i<40;i++)assertEquals(4,draws.get(i).get());
        for(int theme=0;theme<4;theme++){icons.screenChanged(theme);for(AppCatalog.App app:apps)bind(app,theme);}
        assertTrue(icons.cachedBytes()<=256*1024);
        icons.clearMemory();assertEquals(0,icons.cachedBytes());icons.close();icons=new IconPack(context);
        assertEquals(0,icons.prepare(apps,null));
        for(int theme=0;theme<4;theme++)for(AppCatalog.App app:apps)bind(app,theme);
        for(int i=0;i<40;i++)assertEquals("Restart/eviction must not redraw installed-app artwork",4,draws.get(i).get());
    }
    @Test public void additionsAndUpdatesRebuildOnlyTheAffectedApp()throws Exception {
        for(int i=0;i<3;i++)add(i,false);icons.prepare(apps,null);
        ImageView unaffected=bind(apps.get(1),0);Drawable image=unaffected.getDrawable();
        add(3,false);icons.packageChanged(apps.get(3).component.getPackageName());icons.prepare(apps,null);
        assertEquals(16,files().length);for(AtomicInteger count:draws)assertEquals(4,count.get());
        setArtwork(0,Color.BLUE);icons.packageChanged(apps.get(0).component.getPackageName());
        icons.bind(unaffected,apps.get(1),new LauncherTheme(context,0));assertSame(image,unaffected.getDrawable());
        icons.prepare(apps,null);ImageView changed=bind(apps.get(0),0);
        assertEquals(Color.BLUE,((BitmapDrawable)changed.getDrawable()).getBitmap().getPixel(29,29));
        assertEquals(8,draws.get(0).get());for(int i=1;i<4;i++)assertEquals(4,draws.get(i).get());
        assertEquals("Old package revisions must be removed from storage",16,files().length);
    }
    @Test public void uninstallRemovesOnlyObsoleteFiles()throws Exception {
        for(int i=0;i<3;i++)add(i,false);icons.prepare(apps,null);assertEquals(12,files().length);
        icons.packageChanged(apps.get(1).component.getPackageName());apps.remove(1);icons.prepare(apps,null);
        assertEquals(8,files().length);for(AtomicInteger count:draws)assertEquals(4,count.get());
        for(AppCatalog.App app:apps)bind(app,0);
    }
    @Test public void aCorruptedFileIsRebuiltDuringPreparation()throws Exception {
        add(0,false);icons.prepare(apps,null);assertEquals(4,draws.get(0).get());
        try(FileOutputStream out=new FileOutputStream(files()[0])){out.write(new byte[]{1,2,3});}
        assertEquals(0,icons.prepare(apps,null));assertEquals(5,draws.get(0).get());assertEquals(4,files().length);
        for(int theme=0;theme<4;theme++)bind(apps.get(0),theme);
    }
    @Test public void packageStampDetectsUpdatesWhileLauncherWasNotRunning()throws Exception {
        add(0,false);icons.prepare(apps,null);icons.close();setArtwork(0,Color.BLUE);
        AppCatalog.App old=apps.get(0);apps.set(0,new AppCatalog.App(old.component,old.label,"updated-version"));
        icons=new IconPack(context);icons.prepare(apps,null);ImageView view=bind(apps.get(0),0);
        assertEquals(Color.BLUE,((BitmapDrawable)view.getDrawable()).getBitmap().getPixel(29,29));
        assertEquals(8,draws.get(0).get());assertEquals(4,files().length);
    }
    @Test public void actualScrollingAndBackgroundInstallationUsePreparedIcons()throws Exception {
        icons.close();for(int i=0;i<80;i++)add(i,true);
        try(ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup().visible()) {
            MainActivity a=c.get();long deadline=System.currentTimeMillis()+15000;
            while(System.currentTimeMillis()<deadline) {
                Shadows.shadowOf(Looper.getMainLooper()).idleFor(40,TimeUnit.MILLISECONDS);
                GridView home=content(a).findViewById(android.R.id.list);
                if(home.getAdapter().getCount()>=12)break;Thread.sleep(10);
            }
            assertEquals(4,draws.get(79).get());
            ((ViewGroup)content(a).findViewById(android.R.id.tabs)).getChildAt(2).performClick();
            View root=content(a);GridView grid=root.findViewById(android.R.id.list);
            for(int position:new int[]{0,24,56,0,40,0}) {
                grid.setSelection(position);grid.requestLayout();measure(root);
                assertTrue(grid.getChildCount()>0);
                for(int i=0;i<grid.getChildCount();i++) {
                    ImageView icon=(ImageView)((ViewGroup)grid.getChildAt(i)).getChildAt(0);
                    assertTrue("Every visible icon must be ready in the same scroll frame",icon.getDrawable() instanceof BitmapDrawable);
                }
            }
            for(AtomicInteger count:draws)assertEquals("Scrolling must not reload/render original installed icons",4,count.get());
            c.pause().stop();add(80,true);
            context.sendBroadcast(new Intent(Intent.ACTION_PACKAGE_ADDED,android.net.Uri.parse("package:test.persist80")));
            deadline=System.currentTimeMillis()+10000;
            while(System.currentTimeMillis()<deadline) {
                Shadows.shadowOf(Looper.getMainLooper()).idleFor(40,TimeUnit.MILLISECONDS);
                if(draws.get(80).get()==4 && files().length>=328)break;Thread.sleep(10);
            }
            assertEquals("A new app is prepared while the launcher is in the background",4,draws.get(80).get());
            c.restart().start().resume().visible();
            deadline=System.currentTimeMillis()+10000;grid=content(a).findViewById(android.R.id.list);
            while(grid.getAdapter().getCount()<82 && System.currentTimeMillis()<deadline){Shadows.shadowOf(Looper.getMainLooper()).idleFor(40,TimeUnit.MILLISECONDS);Thread.sleep(10);}
            int added=-1;
            for(int i=0;i<grid.getAdapter().getCount();i++)if(((AppCatalog.App)grid.getAdapter().getItem(i)).component.equals(apps.get(80).component))added=i;
            assertTrue(added>=0);grid.setSelection(added);measure(content(a));
            for(int i=0;i<grid.getChildCount();i++)assertTrue(((ImageView)((ViewGroup)grid.getChildAt(i)).getChildAt(0)).getDrawable() instanceof BitmapDrawable);
            for(AtomicInteger count:draws)assertEquals("An install must not regenerate unrelated apps",4,count.get());
        }
    }
    private File[] files(){File[] files=new File(context.getFilesDir(),"launcher-icons").listFiles((dir,name)->name.endsWith(".png"));return files==null?new File[0]:files;}
    private static View content(MainActivity a){return ((ViewGroup)a.findViewById(android.R.id.content)).getChildAt(0);}
    private static void measure(View view){view.measure(View.MeasureSpec.makeMeasureSpec(1024,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(600,View.MeasureSpec.EXACTLY));view.layout(0,0,1024,600);}
    private static void delete(File file){File[] children=file.listFiles();if(children!=null)for(File child:children)delete(child);file.delete();}
}
