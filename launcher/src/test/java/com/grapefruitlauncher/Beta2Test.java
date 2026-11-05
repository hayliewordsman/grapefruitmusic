package com.grapefruitlauncher;

import android.app.ActivityManager;
import android.content.*;
import android.content.pm.*;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.android.controller.ActivityController;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,qualifiers="w411dp-h820dp-mdpi")
public class Beta2Test {
    private Context context;
    private static final String AUDIO="io.github.hayliewordsman.grapefruitmusic";
    @Before public void setup() {
        context=RuntimeEnvironment.getApplication();context.getSharedPreferences("launcher",0).edit().clear().commit();
        Shadows.shadowOf((ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE)).setMemoryClass(32);
        add("com.android.dialer","Phone");add("com.android.mms","Messages");add("com.android.chrome","Chrome");
        add(AUDIO,"Grapefruit Audio");add("test.maps","Maps");add("test.long","A very long application name to wrap across multiple lines");
        handler("com.android.dialer",new Intent(Intent.ACTION_DIAL,android.net.Uri.parse("tel:")));
        handler("com.android.mms",new Intent(Intent.ACTION_SENDTO,android.net.Uri.parse("smsto:")));
    }
    private void add(String pkg,String label) {
        ResolveInfo r=info(pkg);r.nonLocalizedLabel=label;
        Shadows.shadowOf(context.getPackageManager()).addResolveInfoForIntent(launcherIntent(),r);
        Shadows.shadowOf(context.getPackageManager()).addActivityIcon(new ComponentName(pkg,pkg+".Main"),new ColorDrawable(Color.RED));
    }
    private ResolveInfo info(String pkg) {
        ResolveInfo r=new ResolveInfo();r.activityInfo=new ActivityInfo();r.activityInfo.packageName=pkg;r.activityInfo.name=pkg+".Main";
        r.activityInfo.enabled=true;r.activityInfo.exported=true;r.activityInfo.applicationInfo=new ApplicationInfo();
        r.activityInfo.applicationInfo.packageName=pkg;r.activityInfo.applicationInfo.enabled=true;r.isDefault=true;return r;
    }
    private void handler(String pkg,Intent intent){Shadows.shadowOf(context.getPackageManager()).addResolveInfoForIntent(intent,info(pkg));}
    private Intent launcherIntent(){return new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);}
    private String id(String pkg){return pkg+"/"+pkg+".Main";}

    @Test public void requestedDefaultsKeepTheirPositionsAndMissingAppsLeaveHoles() {
        List<AppCatalog.App> apps=AppCatalog.load(context);
        assertArrayEquals(new String[]{id("com.android.dialer"),id("com.android.mms"),id("com.android.chrome"),id(AUDIO)},AppCatalog.suggestedDock(context,apps));
        List<AppCatalog.App> missing=new ArrayList<>();
        for(AppCatalog.App app:apps)if(!app.component.getPackageName().equals("com.android.dialer") && !app.component.getPackageName().equals("com.android.chrome"))missing.add(app);
        assertArrayEquals(new String[]{"",id("com.android.mms"),"",id(AUDIO)},AppCatalog.suggestedDock(context,missing));
        assertArrayEquals(new String[]{"","","",""},AppCatalog.suggestedDock(context,Collections.emptyList()));
    }
    @Test public void upgradeMigratesOnceAndAllUserReplacementsAndBlankSlotsSurviveRefresh() {
        SharedPreferences p=context.getSharedPreferences("launcher",0);
        p.edit().putBoolean("seeded",true).putString("home",id("test.maps")).putInt("theme",3).putString("dock_0",id("test.maps")).commit();
        LauncherStore s=new LauncherStore(p);String[] defaults=AppCatalog.suggestedDock(context,AppCatalog.load(context));
        s.suggestedDock(defaults);
        for(int i=0;i<4;i++)assertEquals(defaults[i],s.dock(i));
        assertEquals(3,s.theme());assertEquals(Collections.singletonList(id("test.maps")),s.home());
        for(int i=0;i<4;i++){s.dock(i,id("test.maps"));assertEquals(id("test.maps"),s.dock(i));}
        s.dock(1,"");s.suggestedDock(defaults);
        assertEquals(id("test.maps"),s.dock(3));assertEquals("",s.dock(1));assertEquals("",s.dock(0));
        s.resetDock(defaults);for(int i=0;i<4;i++)assertEquals(defaults[i],s.dock(i));
        s.dock(0,"");s.suggestedDock(defaults);assertEquals("",s.dock(0));
    }
    @Test public void anUntouchedBlankCanFillWhenAnAppIsInstalled() {
        LauncherStore s=new LauncherStore(context.getSharedPreferences("launcher",0));
        s.suggestedDock(new String[]{"","","",""});
        s.dock(1,""); // The user intentionally kept Messages blank.
        String[] defaults=AppCatalog.suggestedDock(context,AppCatalog.load(context));s.suggestedDock(defaults);
        assertEquals(defaults[0],s.dock(0));assertEquals("",s.dock(1));assertEquals(defaults[2],s.dock(2));
    }
    @Test public void duplicateRequestsShareOneRenderedBitmap() throws Exception {
        AtomicInteger draws=new AtomicInteger();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        ColorDrawable drawable=new ColorDrawable(Color.BLUE){
            @Override public void draw(Canvas canvas){draws.incrementAndGet();entered.countDown();try{release.await(3,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}super.draw(canvas);}
        };
        ComponentName component=new ComponentName("test.counted","test.counted.Main");
        Shadows.shadowOf(context.getPackageManager()).addActivityIcon(component,drawable);
        IconPack icons=new IconPack(context);LauncherTheme theme=new LauncherTheme(context,0);icons.screenChanged(0);
        List<ImageView> targets=new ArrayList<>();AppCatalog.App app=new AppCatalog.App(component,"Counted");
        try {
            ImageView first=new ImageView(context);targets.add(first);icons.bind(first,app,theme);
            assertTrue(entered.await(3,TimeUnit.SECONDS));
            for(int i=0;i<40;i++){ImageView image=new ImageView(context);targets.add(image);icons.bind(image,app,theme);}
            release.countDown();waitImages(targets);
            Bitmap same=((BitmapDrawable)first.getDrawable()).getBitmap();
            for(ImageView image:targets)assertSame(same,((BitmapDrawable)image.getDrawable()).getBitmap());
            assertEquals(1,draws.get());
        }finally{release.countDown();icons.close();}
    }
    @Test public void lowMemoryCacheIsBoundedAndOldThemeEntriesAreReleased() throws Exception {
        MemoryPolicy policy=new MemoryPolicy(context);assertTrue(policy.lowRam);assertEquals(256*1024,policy.cacheBytes);
        IconPack icons=new IconPack(context);LauncherTheme theme=new LauncherTheme(context,0);icons.screenChanged(0);
        List<ImageView> targets=new ArrayList<>();
        try {
            for(int i=0;i<64;i++) {
                ImageView image=new ImageView(context);targets.add(image);
                ComponentName c=new ComponentName("test.cache"+i,"test.cache"+i+".Main");
                Shadows.shadowOf(context.getPackageManager()).addActivityIcon(c,new ColorDrawable(Color.GREEN));
                icons.bind(image,new AppCatalog.App(c,"App "+i),theme);
            }
            waitImages(targets);assertTrue(icons.cachedBytes()>0);assertTrue(icons.cachedBytes()<=256*1024);
            for(ImageView image:targets){Bitmap b=((BitmapDrawable)image.getDrawable()).getBitmap();assertTrue(b.getWidth()<=96);}
            icons.screenChanged(3);assertEquals(0,icons.cachedBytes());
        }finally{icons.close();}
    }
    @Test public void rapidRecyclingSkipsObsoleteWorkAndTheWorkerExitsWhenIdle() throws Exception {
        AtomicInteger draws=new AtomicInteger();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        ColorDrawable source=new ColorDrawable(Color.BLUE){
            @Override public void draw(Canvas canvas){
                if(draws.incrementAndGet()==1){entered.countDown();try{release.await(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
                super.draw(canvas);
            }
        };
        IconPack icons=new IconPack(context);LauncherTheme theme=new LauncherTheme(context,0);icons.screenChanged(0);
        ImageView recycled=new ImageView(context);
        try {
            for(int i=0;i<200;i++) {
                ComponentName component=new ComponentName("test.scroll"+i,"test.scroll"+i+".Main");
                Shadows.shadowOf(context.getPackageManager()).addActivityIcon(component,source);
                icons.bind(recycled,new AppCatalog.App(component,"App "+i),theme);
                if(i==0)assertTrue(entered.await(3,TimeUnit.SECONDS));
            }
            java.lang.reflect.Field jobs=IconPack.class.getDeclaredField("pending");jobs.setAccessible(true);
            assertTrue(((Map<?,?>)jobs.get(icons)).size()<=64);
            release.countDown();waitImages(Collections.singletonList(recycled));
            assertEquals("Only the first in-flight icon and the final visible icon render",2,draws.get());
            java.lang.reflect.Field field=IconPack.class.getDeclaredField("worker");field.setAccessible(true);
            java.util.concurrent.ThreadPoolExecutor worker=(java.util.concurrent.ThreadPoolExecutor)field.get(icons);
            long end=System.currentTimeMillis()+4000;
            while(worker.getPoolSize()!=0 && System.currentTimeMillis()<end)Thread.sleep(20);
            assertEquals("No idle icon thread remains",0,worker.getPoolSize());
        }finally{release.countDown();icons.close();}
    }
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void chipsWrapAndBackgroundingReleasesImagesThenRestoresTheDock() throws Exception {
        try(ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup().visible()) {
            MainActivity a=c.get();awaitCatalog(a);View root=content(a);measure(root);
            TextView label=text(root,"Grapefruit Audio");assertNotNull(label);assertTrue(label.getLineCount()>1);
            assertEquals(0,label.getLayout().getEllipsisCount(label.getLineCount()-1));
            TextView shortLabel=text(root,"Phone");assertNotNull(shortLabel);assertEquals(1,shortLabel.getLineCount());
            ViewGroup dock=root.findViewById(android.R.id.tabs);
            assertTrue(dock.getChildAt(0).getContentDescription().toString().startsWith("Phone"));
            assertTrue(dock.getChildAt(1).getContentDescription().toString().startsWith("Messages"));
            assertEquals("All Apps",dock.getChildAt(2).getContentDescription());
            assertTrue(dock.getChildAt(3).getContentDescription().toString().startsWith("Chrome"));
            assertTrue(dock.getChildAt(4).getContentDescription().toString().startsWith("Grapefruit Audio"));
            java.lang.reflect.Field f=MainActivity.class.getDeclaredField("icons");f.setAccessible(true);IconPack icons=(IconPack)f.get(a);
            c.pause().stop();assertEquals(0,icons.cachedBytes());
            assertEquals(0,((ViewGroup)((HomeSurface)root.findViewById(android.R.id.list)).getChildAt(0)).getChildCount());assertNull(root.findViewById(android.R.id.tabs));
            c.restart().start().resume().visible();awaitCatalog(a);measure(content(a));
            assertEquals(5,((ViewGroup)content(a).findViewById(android.R.id.tabs)).getChildCount());
            assertNotNull(text(content(a),"Grapefruit Audio"));
        }
    }
    private static void waitImages(List<ImageView> images)throws Exception {
        long end=System.currentTimeMillis()+6000;
        while(System.currentTimeMillis()<end){Shadows.shadowOf(Looper.getMainLooper()).idleFor(40,TimeUnit.MILLISECONDS);
            boolean ready=true;for(ImageView image:images)ready&=image.getDrawable() instanceof BitmapDrawable;
            if(ready)return;Thread.sleep(10);}
        fail("Icon rendering did not finish");
    }
    private static View content(MainActivity a){return ((ViewGroup)a.findViewById(android.R.id.content)).getChildAt(0);}
    private static void measure(View v){v.measure(View.MeasureSpec.makeMeasureSpec(411,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(820,View.MeasureSpec.EXACTLY));v.layout(0,0,411,820);}
    private static void awaitCatalog(MainActivity a)throws Exception {
        long end=System.currentTimeMillis()+5000;
        while(System.currentTimeMillis()<end){Shadows.shadowOf(Looper.getMainLooper()).idle();HomeSurface home=content(a).findViewById(android.R.id.list);
            if(home.appCount()>=6)return;Thread.sleep(10);}
        fail("Catalog not loaded");
    }
    private static TextView text(View v,String value){if(v instanceof TextView && value.contentEquals(((TextView)v).getText()))return(TextView)v;
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){TextView found=text(((ViewGroup)v).getChildAt(i),value);if(found!=null)return found;}return null;}
}
