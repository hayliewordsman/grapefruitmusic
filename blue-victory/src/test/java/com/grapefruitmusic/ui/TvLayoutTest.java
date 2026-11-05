package com.grapefruitmusic.ui;

import android.app.*;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import com.grapefruitmusic.R;
import com.grapefruitmusic.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,application=Application.class,qualifiers="w320dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class TvLayoutTest {
    @Test public void narrowScreenKeepsAllTvActionsReadableWithEqualGaps() throws Exception {
        Application app=RuntimeEnvironment.getApplication();Prefs.init(app);Fonts.init(app);Brand.init(app);
        try(org.robolectric.android.controller.ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup().visible()){
            MainActivity a=controller.get();BlueTelevision tv=(BlueTelevision)a.television();tv.repo.worker.submit(()->{}).get(10,TimeUnit.SECONDS);settle(a,320,640);
            a.showTvSection("Guide");settle(a,320,640);checkRows(a.findViewById(R.id.content));
            tv.session.current=tv.repo.channels.get(0);tv.session.selected=true;tv.session.status="Paused";
            a.showTvNowPlaying();settle(a,320,640);checkRows(a.findViewById(R.id.content));
            View full=BlueIntegrationTest.text(a.getWindow().getDecorView(),"Full screen");assertNotNull(full);assertTrue("Full screen is visible",full.isShown());assertTrue("Full screen width="+full.getWidth(),full.getWidth()>80);
            View retry=BlueIntegrationTest.text(a.getWindow().getDecorView(),"Retry");assertNotNull(retry);assertTrue(retry.getHeight()>=48);
            a.getFragmentManager().popBackStackImmediate();settle(a,320,640);TvMini mini=(TvMini)tv.mini();mini.place(false);settle(a,320,640);assertFits(a,mini);
            tv.session.stop();
        }
    }
    @Test @Config(qualifiers="w800dp-h400dp-land-mdpi") public void landscapeCornerStaysBesideTheRailAndAboveTheBottomEdge() throws Exception {
        Application app=RuntimeEnvironment.getApplication();Prefs.init(app);Fonts.init(app);Brand.init(app);
        try(org.robolectric.android.controller.ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup().visible()){
            MainActivity a=controller.get();BlueTelevision tv=(BlueTelevision)a.television();tv.repo.worker.submit(()->{}).get(10,TimeUnit.SECONDS);settle(a,800,400);
            tv.session.current=tv.repo.channels.get(0);tv.session.selected=true;tv.session.status="Paused";tv.session.changed();settle(a,800,400);
            TvMini mini=(TvMini)tv.mini();mini.place(false);settle(a,800,400);assertFits(a,mini);assertTrue(a.findViewById(R.id.column).getLeft()>0);tv.session.stop();
        }
    }
    @Test public void largeTextButtonsWrapWithoutCuttingOffTheirLabels(){
        Application app=RuntimeEnvironment.getApplication();Prefs.init(app);Fonts.init(app);Brand.init(app);
        try(org.robolectric.android.controller.ActivityController<Activity> controller=Robolectric.buildActivity(Activity.class)){
            Activity a=controller.get();Ui.applyTheme(a);controller.setup();TvStyle b=new TvStyle(a,0);TvButtonRow row=new TvButtonRow(a);
            for(String label:new String[]{"Play / Pause","Previous","Next","Playback Quality","Full screen"}){TextView button=b.button(label,false,v->{});button.setTextSize(28);row.addView(button);}
            row.measure(View.MeasureSpec.makeMeasureSpec(288,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));row.layout(0,0,288,row.getMeasuredHeight());checkRows(row);
            for(int n=0;n<row.getChildCount();n++){TextView button=(TextView)row.getChildAt(n);assertEquals(0,button.getLayout().getEllipsisCount(0));assertTrue(button.getBottom()<=row.getHeight());}
        }
    }
    static void checkRows(View view){
        if(view instanceof TvButtonRow){ViewGroup row=(ViewGroup)view;View previous=null;
            for(int n=0;n<row.getChildCount();n++){View child=row.getChildAt(n);if(child.getVisibility()==View.GONE)continue;assertTrue(child.getLeft()>=0);assertTrue(child.getRight()<=row.getWidth());assertTrue(child.getHeight()>=48);
                if(previous!=null){if(child.getTop()==previous.getTop())assertEquals(8,child.getLeft()-previous.getRight());else assertTrue(child.getTop()-previous.getBottom()>=8);}previous=child;
            }
        }
        if(view instanceof ViewGroup)for(int n=0;n<((ViewGroup)view).getChildCount();n++)checkRows(((ViewGroup)view).getChildAt(n));
    }
    private static void assertFits(MainActivity a,TvMini mini){View column=a.findViewById(R.id.column),bars=a.findViewById(R.id.bottom_bars);assertSame(column,mini.getParent());assertTrue(mini.getX()>=10);assertTrue(mini.getX()+mini.getWidth()<=column.getWidth()-10);int ceiling=bars.getHeight()>0?bars.getTop():column.getHeight();assertTrue(mini.getY()+mini.getHeight()<=ceiling-10);assertEquals(16f/9f,mini.getWidth()/(float)mini.getHeight(),0.02f);}
    private static void settle(MainActivity a,int w,int h){Shadows.shadowOf(Looper.getMainLooper()).idle();a.getFragmentManager().executePendingTransactions();View v=a.getWindow().getDecorView();for(int i=0;i<3;i++){v.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));v.layout(0,0,w,h);Shadows.shadowOf(Looper.getMainLooper()).idle();}}
}
