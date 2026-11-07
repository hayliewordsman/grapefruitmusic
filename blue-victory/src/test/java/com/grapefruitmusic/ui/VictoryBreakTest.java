package com.grapefruitmusic.ui;

import android.app.Application;
import android.graphics.*;
import android.os.Looper;
import android.view.*;
import com.grapefruitmusic.R;
import com.grapefruitmusic.util.*;
import java.io.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,application=Application.class,qualifiers="w411dp-h914dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class VictoryBreakTest {
    @Before public void setup(){Application app=RuntimeEnvironment.getApplication();Prefs.init(app);Fonts.init(app);Brand.init(app);}
    private static void settle(MainActivity activity){Shadows.shadowOf(Looper.getMainLooper()).idle();activity.getFragmentManager().executePendingTransactions();View root=activity.getWindow().getDecorView();for(int n=0;n<3;n++){root.measure(View.MeasureSpec.makeMeasureSpec(411,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(914,View.MeasureSpec.EXACTLY));root.layout(0,0,411,914);Shadows.shadowOf(Looper.getMainLooper()).idle();}}
    @Test public void mapsRouteUnlocksOnExactlyFiveDeveloperTapsAndBackClearsDrops()throws Exception{
        try(org.robolectric.android.controller.ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup().visible()){
            MainActivity activity=controller.get();activity.push(new AboutFragment());settle(activity);View haylie=BlueIntegrationTest.text(activity.getWindow().getDecorView(),"Haylie");assertNotNull(haylie);View row=(View)haylie.getParent();
            for(int n=0;n<4;n++){row.performClick();settle(activity);assertTrue(activity.getFragmentManager().findFragmentById(R.id.content) instanceof AboutFragment);}
            row.performClick();settle(activity);assertTrue(activity.getFragmentManager().findFragmentById(R.id.content) instanceof VictoryBreakFragment);
            VictoryBreakFragment game=(VictoryBreakFragment)activity.getFragmentManager().findFragmentById(R.id.content);VictoryBreakView scene=ReflectionHelpers.getField(game,"scene");scene.performClick();settle(activity);assertEquals(1,scene.count);
            Bitmap image=Bitmap.createBitmap(411,914,Bitmap.Config.ARGB_8888);activity.getWindow().getDecorView().draw(new Canvas(image));File output=new File("build/reports/blue-1.1-beta1/victory-break.png");output.getParentFile().mkdirs();try(FileOutputStream out=new FileOutputStream(output)){image.compress(Bitmap.CompressFormat.PNG,100,out);}
            activity.getFragmentManager().popBackStackImmediate();settle(activity);assertEquals(0,scene.count);assertEquals(0,scene.retainedDrops());
        }
    }
    @Test public void bundledSceneStartsDropsAtTheMouthAndBoundsRetainedObjects(){
        VictoryBreakView view=new VictoryBreakView(RuntimeEnvironment.getApplication(),0xff64b5ff);view.measure(View.MeasureSpec.makeMeasureSpec(411,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(420,View.MeasureSpec.EXACTLY));view.layout(0,0,411,420);view.drop();PointF point=new PointF();view.position(0,0,point);assertEquals(view.mouthX(),point.x,.01f);assertEquals(view.mouthY(),point.y,.01f);view.position(0,VictoryBreakView.FLIGHT_MS,point);assertTrue(view.onLand(point));for(int n=0;n<120;n++)view.drop();assertEquals(100,view.retainedDrops());assertEquals(121,view.count);view.clear();assertEquals(0,view.count);
    }
}
