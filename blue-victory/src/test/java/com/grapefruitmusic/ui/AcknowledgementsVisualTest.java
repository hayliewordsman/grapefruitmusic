package com.grapefruitmusic.ui;

import android.app.*;
import android.graphics.*;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import com.grapefruitmusic.R;
import com.grapefruitmusic.radio.RadioArt;
import com.grapefruitmusic.util.*;
import java.io.*;
import java.util.*;
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
public class AcknowledgementsVisualTest {
    @Before public void init(){Application a=RuntimeEnvironment.getApplication();Prefs.init(a);Fonts.init(a);Brand.init(a);}
    @Test public void flagsDrawCorrectBandsWithoutEmojiFonts() throws Exception {
        Application a=RuntimeEnvironment.getApplication();
        Bitmap bi=render(new PrideFlagView(a,"bisexual"),100,100);assertEquals(0xffd60270,bi.getPixel(50,20));assertEquals(0xff9b4f96,bi.getPixel(50,50));assertEquals(0xff0038a8,bi.getPixel(50,80));
        Bitmap trans=render(new PrideFlagView(a,"transgender"),100,100);assertEquals(0xff5bcefa,trans.getPixel(50,10));assertEquals(0xfff5a9b8,trans.getPixel(50,30));assertEquals(0xffffffff,trans.getPixel(50,50));assertEquals(trans.getPixel(50,10),trans.getPixel(50,90));
        Bitmap rainbow=render(new PrideFlagView(a,"rainbow"),120,120);assertEquals(0xffe40303,rainbow.getPixel(60,10));assertEquals(0xff750787,rainbow.getPixel(60,110));
        save(bi,"bisexual-flag.png");save(trans,"transgender-flag.png");save(rainbow,"rainbow-flag.png");
    }
    @Test public void radioUsesVariedBlueBackgroundsAndPreservesTheStationLogo() throws Exception {
        Set<Integer> shades=new HashSet<Integer>();for(int i=0;i<100;i++){int[] colors=RadioArt.colorsFor("genre:"+i);for(int color:colors){assertBlue(color);shades.add(color);}}assertTrue(shades.size()>=12);
        Bitmap fm=RadioArt.of(90100,"My FM Station",200);assertBlue(fm.getPixel(2,2));
        Bitmap logo=Bitmap.createBitmap(200,200,Bitmap.Config.ARGB_8888);logo.eraseColor(Color.RED);Bitmap internet=RadioArt.station("saved station","My Station",logo,200);assertNotSame(logo,internet);assertBlue(internet.getPixel(2,2));assertEquals(Color.RED,internet.getPixel(100,100));save(fm,"fm-blue.png");save(internet,"internet-blue.png");
        ReflectionHelpers.setStaticField(Brand.class,"blue",false);try{assertSame(logo,RadioArt.station("saved station","My Station",logo,200));boolean warm=false;for(int i=0;i<100;i++){int color=RadioArt.colorsFor("genre:"+i)[0];warm|=Color.red(color)>Color.blue(color);}assertTrue("Standard edition retains varied colours",warm);}finally{Brand.init(RuntimeEnvironment.getApplication());}
    }
    @Test public void settingsOpensRosterAndClosingParagraphIsInTheSameList() throws Exception {
        try(org.robolectric.android.controller.ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup().visible()){
            MainActivity a=controller.get();Shadows.shadowOf(Looper.getMainLooper()).idleFor(1500,java.util.concurrent.TimeUnit.MILLISECONDS);a.getFragmentManager().beginTransaction().replace(R.id.content,new SettingsFragment()).commit();settle(a);assertNotNull(BlueIntegrationTest.text(a.getWindow().getDecorView(),"Victory Roll Call"));
            AcknowledgementsFragment f=new AcknowledgementsFragment();AcknowledgementsRoster roster=AcknowledgementsRoster.load(a);ReflectionHelpers.setField(f,"roster",roster);ReflectionHelpers.setField(f,"repository",new RollCallRepository(a,()->{throw new IOException("Offline test");}));a.push(f);settle(a);ListView list=(ListView)f.getView();assertTrue(list.getCount()>roster.members.size());assertEquals(0,list.getFooterViewsCount());assertNull("Closing message must not appear above the results",BlueIntegrationTest.text(list,"\n"+AcknowledgementsRoster.CLOSING_MESSAGE));save(render(a.getWindow().getDecorView(),411,914),"acknowledgements-top.png");
            EditText search=ReflectionHelpers.getField(f,"search");search.setText("Allison Montgomery");list.setSelection(0);settle(a);View name=BlueIntegrationTest.text(list,"State Representative Allison Montgomery, D-AL");assertNotNull(name);assertNotNull("Loaded search results end with the closing message",BlueIntegrationTest.text(list,"\n"+AcknowledgementsRoster.CLOSING_MESSAGE));ViewGroup row=(ViewGroup)name.getParent().getParent();boolean flag=false;for(int n=0;n<row.getChildCount();n++)flag|=row.getChildAt(n) instanceof PrideFlagView;assertTrue("Allison's row displays the bundled flag",flag);save(render(a.getWindow().getDecorView(),411,914),"roll-call-allison.png");search.setText("");settle(a);
            list.setSelection(list.getCount()-1);settle(a);TextView closing=(TextView)BlueIntegrationTest.text(list,"\n"+AcknowledgementsRoster.CLOSING_MESSAGE);assertNotNull(closing);assertTrue(closing.isShown());assertNull(closing.getEllipsize());assertTrue(closing.getLineCount()>3);save(render(a.getWindow().getDecorView(),411,914),"acknowledgements-closing.png");
        }
    }
    @Test public void closingMessageWaitsForTheRosterToLoad() throws Exception {
        try(org.robolectric.android.controller.ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup().visible()){
            MainActivity a=controller.get();settle(a);AcknowledgementsFragment f=new AcknowledgementsFragment();ReflectionHelpers.setField(f,"repository",new RollCallRepository(a,()->{throw new IOException("Offline test");}));a.push(f);a.getFragmentManager().executePendingTransactions();ListView list=(ListView)f.getView();
            // The worker can finish, but its main-thread callback is still queued here.
            assertEquals(1,list.getCount());assertEquals(0,list.getFooterViewsCount());assertNull(BlueIntegrationTest.text(list,"\n"+AcknowledgementsRoster.CLOSING_MESSAGE));
            ReflectionHelpers.setField(f,"roster",AcknowledgementsRoster.load(a));ReflectionHelpers.callInstanceMethod(f,"loaded");settle(a);assertTrue(list.getCount()>3000);assertNull(BlueIntegrationTest.text(list,"\n"+AcknowledgementsRoster.CLOSING_MESSAGE));
            list.setSelection(list.getCount()-1);settle(a);assertNotNull(BlueIntegrationTest.text(list,"\n"+AcknowledgementsRoster.CLOSING_MESSAGE));
        }
    }
    private static void assertBlue(int c){assertTrue("blue background "+Integer.toHexString(c),Color.blue(c)>Color.red(c)&&Color.blue(c)>Color.green(c));}
    private static Bitmap render(View v,int w,int h){v.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));v.layout(0,0,w,h);Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);v.draw(new Canvas(b));return b;}
    private static void settle(MainActivity a){Shadows.shadowOf(Looper.getMainLooper()).idle();a.getFragmentManager().executePendingTransactions();for(int i=0;i<3;i++){View v=a.getWindow().getDecorView();v.measure(View.MeasureSpec.makeMeasureSpec(411,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(914,View.MeasureSpec.EXACTLY));v.layout(0,0,411,914);Shadows.shadowOf(Looper.getMainLooper()).idle();}}
    private static void save(Bitmap b,String name)throws Exception{File f=new File("build/reports/blue-beta4/"+name);f.getParentFile().mkdirs();try(FileOutputStream out=new FileOutputStream(f)){b.compress(Bitmap.CompressFormat.PNG,100,out);}}
}
