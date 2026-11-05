package com.grapefruitlauncher;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Looper;
import android.widget.ImageView;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,qualifiers="w1024dp-h600dp-mdpi")
public class IconReuseTest {
    private Context context;
    private IconPack icons;
    private LauncherTheme theme;
    private final List<ImageView> visible=new ArrayList<>();
    private final AtomicInteger firstDraws=new AtomicInteger();
    @Before public void setup() {
        context=RuntimeEnvironment.getApplication();
        Shadows.shadowOf((ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE)).setMemoryClass(32);
        icons=new IconPack(context);theme=new LauncherTheme(context,0);icons.screenChanged(0);
    }
    @After public void close(){icons.close();}
    private AppCatalog.App app(int index) {
        ComponentName c=new ComponentName("test.icons"+index,"test.icons"+index+".Main");
        return new AppCatalog.App(c,"App "+index);
    }
    private void saturateCache() throws Exception {
        // Keep the actual displayed bitmaps alive while overflowing the independent 256 KiB LRU.
        for(int i=0;i<64;i++) {
            final int index=i;
            ColorDrawable original=new ColorDrawable(Color.RED){
                @Override public void draw(Canvas canvas){if(index==0)firstDraws.incrementAndGet();super.draw(canvas);}
            };
            Shadows.shadowOf(context.getPackageManager()).addActivityIcon(app(i).component,original);
            ImageView view=new ImageView(context);visible.add(view);icons.bind(view,app(i),theme);
        }
        waitForImages(visible);
        assertTrue(icons.cachedBytes()<=256*1024);
        assertEquals(1,firstDraws.get());
    }
    @Test public void scrollingRebindKeepsAnAlreadyVisibleIconAfterLruEviction() throws Exception {
        saturateCache();ImageView first=visible.get(0);Drawable drawn=first.getDrawable();
        for(int frame=0;frame<20;frame++) {
            icons.bind(first,app(0),theme);
            assertSame("Rebinding the same app must never replace its image with a loading plate",drawn,first.getDrawable());
        }
        assertEquals("The visible icon must not be rendered again",1,firstDraws.get());
    }
    @Test public void recycledCellCanReuseABitmapStillDisplayedElsewhere() throws Exception {
        saturateCache();Bitmap original=((BitmapDrawable)visible.get(0).getDrawable()).getBitmap();
        ImageView recycled=new ImageView(context);icons.bind(recycled,app(0),theme);
        assertTrue("A live icon should be available immediately, without an async reload",recycled.getDrawable() instanceof BitmapDrawable);
        assertSame(original,((BitmapDrawable)recycled.getDrawable()).getBitmap());
        assertEquals(1,firstDraws.get());
        assertTrue(icons.cachedBytes()<=256*1024);
    }
    @Test public void memoryTrimDoesNotClearAnIconThatIsStillOnScreen() throws Exception {
        saturateCache();ImageView view=visible.get(0);Drawable drawn=view.getDrawable();
        icons.trim();assertEquals(0,icons.cachedBytes());icons.bind(view,app(0),theme);
        assertSame(drawn,view.getDrawable());
    }
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void packageInvalidationAndThemeChangesStillReplaceStaleImages() throws Exception {
        saturateCache();ImageView view=visible.get(0);
        Bitmap red=((BitmapDrawable)view.getDrawable()).getBitmap();
        Shadows.shadowOf(context.getPackageManager()).addActivityIcon(app(0).component,new ColorDrawable(Color.BLUE));
        icons.invalidate();icons.bind(view,app(0),theme);waitForImages(java.util.Collections.singletonList(view));
        Bitmap blue=((BitmapDrawable)view.getDrawable()).getBitmap();
        assertNotSame(red,blue);assertEquals(Color.BLUE,blue.getPixel(blue.getWidth()/2,blue.getHeight()/2));
        icons.screenChanged(3);icons.bind(view,app(0),new LauncherTheme(context,3));waitForImages(java.util.Collections.singletonList(view));
        Bitmap themed=((BitmapDrawable)view.getDrawable()).getBitmap();
        assertNotSame(blue,themed);assertNotEquals(blue.getPixel(blue.getWidth()/2,3),themed.getPixel(themed.getWidth()/2,3));
    }
    private static void waitForImages(List<ImageView> views)throws Exception {
        long end=System.currentTimeMillis()+6000;
        while(System.currentTimeMillis()<end) {
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(40,TimeUnit.MILLISECONDS);
            boolean loaded=true;for(ImageView view:views)loaded&=view.getDrawable() instanceof BitmapDrawable;
            if(loaded)return;Thread.sleep(10);
        }
        fail("Icon loading did not finish");
    }
}
