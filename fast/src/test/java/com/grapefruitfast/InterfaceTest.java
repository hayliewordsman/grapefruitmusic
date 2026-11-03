package com.grapefruitfast;
import android.view.*;
import android.widget.*;
import android.graphics.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,qualifiers="w1024dp-h600dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class InterfaceTest {
    @Test public void browseFavouritesSearchAndSettings() throws Exception {
        try(org.robolectric.android.controller.ActivityController<MainActivity> controller=launch()) {
            MainActivity a=controller.get();
            View root=a.getWindow().getDecorView();measure(root,1024,600);
            assertNotNull(text(root,"Live TV"));assertNotNull(text(root,"CityNews 24/7 · Toronto"));
            capture(root,"landscape-dark.png");
            tap(text(root,"Favourites"));measure(a.getWindow().getDecorView(),1024,600);
            assertNotNull(text(a.getWindow().getDecorView(),"No favourites yet. Hold a channel, or press Menu, to add one."));
            tap(text(a.getWindow().getDecorView(),"Search"));root=a.getWindow().getDecorView();
            EditText search=edit(root);assertNotNull(search);search.setText("Winnipeg");measure(root,1024,600);
            assertNotNull(text(root,"Global News · Winnipeg"));capture(root,"landscape-search.png");
            tap(text(root,"Settings"));measure(a.getWindow().getDecorView(),1024,600);
            assertNotNull(text(a.getWindow().getDecorView(),"Appearance"));
            assertNotNull(text(a.getWindow().getDecorView(),"Grapefruit Dark"));capture(a.getWindow().getDecorView(),"landscape-settings.png");
        }
    }
    @Test @Config(qualifiers="w360dp-h640dp-mdpi") public void portraitUsesBottomNavigation() throws Exception {
        try(org.robolectric.android.controller.ActivityController<MainActivity> controller=launch()) {
            View root=controller.get().getWindow().getDecorView();measure(root,360,640);
            assertNotNull(text(root,"Live TV"));capture(root,"portrait-dark.png");
        }
    }
    @Test public void themesSurviveRecreation() throws Exception {
        android.content.Context c=org.robolectric.RuntimeEnvironment.getApplication();
        c.getSharedPreferences("fast",0).edit().putInt("theme",1).commit();
        try(org.robolectric.android.controller.ActivityController<MainActivity> controller=launch()) {
            View root=controller.get().getWindow().getDecorView();measure(root,1024,600);
            assertEquals(Color.parseColor("#4A1622"),heading(root).getCurrentTextColor());capture(root,"landscape-light.png");
        }
    }
    @Test @Config(qualifiers="w360dp-h640dp-mdpi") public void continueWatchingFloatsAboveTabsAndWatchPageOpens() throws Exception {
        android.content.Context c=org.robolectric.RuntimeEnvironment.getApplication();
        c.getSharedPreferences("fast",0).edit().putString("lastChannel","global-winnipeg").commit();
        try(org.robolectric.android.controller.ActivityController<MainActivity> controller=launch()) {
            MainActivity a=controller.get();View root=a.getWindow().getDecorView();measure(root,360,640);
            assertNotNull(text(root,"Continue watching"));capture(root,"portrait-continue.png");
            View pill=(View)text(root,"Continue watching").getParent().getParent();pill.performClick();
            root=a.getWindow().getDecorView();measure(root,360,640);
            assertNotNull(text(root,"Full screen"));assertNull(text(root,"Continue watching"));capture(root,"portrait-watch.png");
        }
    }
    @Test public void pressedRowOutlineHugsThePill() throws Exception {
        try(org.robolectric.android.controller.ActivityController<MainActivity> controller=launch()) {
            View root=controller.get().getWindow().getDecorView();measure(root,1024,600);
            View row=(View)text(root,"CityNews 24/7 · Toronto").getParent().getParent();
            ViewGroup item=(ViewGroup)row.getParent();
            // The gap between rows belongs to the item, so a pressed ring drawn on the row can't fall below it.
            assertNull(item.getBackground());assertTrue(row.isDuplicateParentStateEnabled());
            assertEquals(item.getHeight()-item.getPaddingBottom(),row.getBottom());
            item.setPressed(true);item.setSelected(true);root.invalidate();capture(root,"landscape-pressed.png");
        }
    }
    @Test @Config(qualifiers="w360dp-h640dp-mdpi") public void tabsAreIconsWithTheChosenOnesName() throws Exception {
        try(org.robolectric.android.controller.ActivityController<MainActivity> controller=launch()) {
            View root=controller.get().getWindow().getDecorView();measure(root,360,640);
            ViewGroup chosen=null,other=null;
            for(View v:views(root))if(v instanceof ViewGroup&&"Live TV".contentEquals(String.valueOf(v.getContentDescription())))chosen=(ViewGroup)v;
            for(View v:views(root))if(v instanceof ViewGroup&&"Search".contentEquals(String.valueOf(v.getContentDescription())))other=(ViewGroup)v;
            assertTrue(chosen.getChildAt(0) instanceof TabIcon);assertEquals(View.VISIBLE,chosen.getChildAt(1).getVisibility());
            assertTrue(other.getChildAt(0) instanceof TabIcon);assertEquals(View.GONE,other.getChildAt(1).getVisibility());
            capture(root,"portrait-tabs.png");
        }
    }
    /** Android 15 (edge to edge, predictive back): Back steps out of the watch page and tabs, then leaves. */
    @Test @Config(sdk=35,qualifiers="w411dp-h914dp-xxhdpi") public void modernAndroidBackSteps() throws Exception {
        try(org.robolectric.android.controller.ActivityController<MainActivity> controller=launch()) {
            MainActivity a=controller.get();View root=a.getWindow().getDecorView();measure(root,1233,2742);
            tap(text(root,"CityNews 24/7 · Toronto"));root=a.getWindow().getDecorView();measure(root,1233,2742);
            assertNotNull(text(root,"Full screen"));
            assertTrue(a.back());root=a.getWindow().getDecorView();assertNull(text(root,"Full screen"));
            tap(text(root,"Settings"));root=a.getWindow().getDecorView();assertNotNull(text(root,"Appearance"));
            assertTrue(a.back());assertFalse(a.back());
        }
    }
    private static org.robolectric.android.controller.ActivityController<MainActivity> launch(){
        MainActivity.splashShown=false;
        org.robolectric.android.controller.ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup();
        settle();return c;
    }
    /** Lets the splash screen fade and go. */
    private static void settle(){org.robolectric.shadows.ShadowLooper.idleMainLooper(2,java.util.concurrent.TimeUnit.SECONDS);}
    @Test @Config(qualifiers="w360dp-h640dp-xxhdpi") public void splashShowsTheWordmarkThenGoes() throws Exception {
        MainActivity.splashShown=false;
        try(org.robolectric.android.controller.ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()) {
            View root=controller.get().getWindow().getDecorView();measure(root,1080,1920);
            View logo=described(root,"Grapefruit FAST");assertTrue(logo instanceof ImageView);assertTrue(logo.getWidth()>0);
            capture(root,"portrait-splash.png");
            settle();root=controller.get().getWindow().getDecorView();measure(root,1080,1920);
            assertNull(described(root,"Grapefruit FAST"));
        }
    }
    @Test public void settingsLikeMusicWithAboutAndToggles() throws Exception {
        try(org.robolectric.android.controller.ActivityController<MainActivity> controller=launch()) {
            MainActivity a=controller.get();View root=a.getWindow().getDecorView();measure(root,1024,600);
            tap(text(root,"Settings"));root=a.getWindow().getDecorView();measure(root,1024,600);
            for(String s:new String[]{"Appearance","Watching","Channels","About","Theme","Playback Quality","Continue Watching","Keep Screen On"})assertNotNull(s,text(root,s));
            capture(root,"landscape-settings.png");
            Toggle keep=null;for(View v:views(root))if(v instanceof Toggle&&"Keep Screen On".contentEquals(v.getContentDescription()))keep=(Toggle)v;
            assertTrue(keep.isChecked());tap(text(root,"Keep Screen On"));assertFalse(keep.isChecked());
            assertFalse(org.robolectric.RuntimeEnvironment.getApplication().getSharedPreferences("fast",0).getBoolean("keepScreenOn",true));
            tap(text(root,"About Grapefruit FAST"));root=a.getWindow().getDecorView();measure(root,1024,600);
            TextView wm=wordmark(root);assertTrue("wordmark on screen",wm.getLayout().getLineLeft(0)<wm.getWidth());assertNotNull(wordmark(root));assertNotNull(text(root,"Version 1.0-beta-8"));assertNotNull(text(root,"Source Code"));
            capture(root,"landscape-about.png");
            assertTrue(a.back());root=a.getWindow().getDecorView();assertNotNull(text(root,"Appearance"));assertNull(text(root,"Source Code"));
        }
    }
    @Test @Config(qualifiers="w360dp-h640dp-mdpi") public void continueWatchingCanBeTurnedOff() throws Exception {
        android.content.Context c=org.robolectric.RuntimeEnvironment.getApplication();
        c.getSharedPreferences("fast",0).edit().putString("lastChannel","global-winnipeg").putBoolean("continue",false).commit();
        try(org.robolectric.android.controller.ActivityController<MainActivity> controller=launch()) {
            View root=controller.get().getWindow().getDecorView();measure(root,360,640);
            assertNull(text(root,"Continue watching"));
        }
    }
    private static TextView wordmark(View root){for(View v:views(root))if(v instanceof TextView&&"Grapefruit FAST".equals(String.valueOf(((TextView)v).getText())))return(TextView)v;return null;}
    /** On a phone, Settings scrolls far enough for About to clear the floating tabs and Continue watching. */
    @Test @Config(qualifiers="w411dp-h914dp-xxhdpi") public void aboutRowClearsTheFloatingBarsOnAPhone() throws Exception {
        android.content.Context c=org.robolectric.RuntimeEnvironment.getApplication();
        c.getSharedPreferences("fast",0).edit().putString("lastChannel","global-winnipeg").commit();
        try(org.robolectric.android.controller.ActivityController<MainActivity> controller=launch()) {
            MainActivity a=controller.get();View root=a.getWindow().getDecorView();measure(root,1233,2742);
            tap(text(root,"Settings"));root=a.getWindow().getDecorView();measure(root,1233,2742);settle();measure(root,1233,2742);
            ScrollView scroll=null;for(View v:views(root))if(v instanceof ScrollView)scroll=(ScrollView)v;
            scroll.scrollTo(0,100000);measure(root,1233,2742);settle();measure(root,1233,2742);
            View about=(View)text(root,"About Grapefruit FAST").getParent();
            int[] at=new int[2];about.getLocationInWindow(at);
            View cont=(View)text(root,"Continue watching").getParent().getParent();int[] bar=new int[2];cont.getLocationInWindow(bar);
            assertTrue("Settings scrolled",scroll.getScrollY()>0);
            capture(root,"portrait-settings-bottom.png");
            assertTrue("About row is hidden behind the floating bars",at[1]+about.getHeight()<=bar[1]);
        }
    }
    /** On a phone: Full screen turns the picture sideways; turning the phone sideways while watching goes full screen. */
    @Test @Config(qualifiers="w411dp-h914dp-port-xxhdpi") public void fullScreenInLandscapeOnAPhone() throws Exception {
        try(org.robolectric.android.controller.ActivityController<MainActivity> controller=launch()) {
            MainActivity a=controller.get();View root=a.getWindow().getDecorView();measure(root,1233,2742);
            tap(text(root,"CityNews 24/7 · Toronto"));root=a.getWindow().getDecorView();measure(root,1233,2742);
            tap(text(root,"Full screen"));
            assertEquals(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,a.getRequestedOrientation());
            assertNotNull(text(a.getWindow().getDecorView(),"Exit full screen"));
            assertTrue(a.back());
            assertEquals(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,a.getRequestedOrientation());
            assertNotNull(text(a.getWindow().getDecorView(),"Full screen"));
            // Turned sideways by hand: full screen. Upright again: back to the channel page.
            org.robolectric.RuntimeEnvironment.setQualifiers("w914dp-h411dp-land-xxhdpi");
            controller.configurationChange(a.getResources().getConfiguration());
            root=a.getWindow().getDecorView();measure(root,2742,1233);
            assertNotNull(text(root,"Exit full screen"));capture(root,"landscape-phone-fullscreen.png");
            org.robolectric.RuntimeEnvironment.setQualifiers("w411dp-h914dp-port-xxhdpi");
            controller.configurationChange(a.getResources().getConfiguration());
            assertNotNull(text(a.getWindow().getDecorView(),"Full screen"));
        }
    }
    private static View described(View root,String d){for(View v:views(root))if(d.contentEquals(String.valueOf(v.getContentDescription())))return v;return null;}
    /** As Music: the title scrolls away; the chips float at the top once they get there. */
    @Test @Config(qualifiers="w411dp-h914dp-xxhdpi") public void chipsFloatOnceTheTitleScrollsAway() throws Exception {
        try(org.robolectric.android.controller.ActivityController<MainActivity> controller=launch()) {
            MainActivity a=controller.get();View root=a.getWindow().getDecorView();measure(root,1233,2742);
            View news=text(root,"News");View bar=(View)news.getParent().getParent().getParent();
            ListView list=null;for(View v:views(root))if(v instanceof ListView)list=(ListView)v;
            float before=bar.getTranslationY();assertTrue("chips start under the title",before>0);
            list.setSelectionFromTop(6,0);measure(root,1233,2742);root.getViewTreeObserver().dispatchOnPreDraw();
            assertEquals(0f,bar.getTranslationY(),0.5f);
            assertNull("title scrolled away",visibleText(list,"Live TV"));
            capture(root,"portrait-scrolled.png");
        }
    }
    /** Outside the US, channels only Xumo carries (a US service) aren't listed; Settings › Region can change it. */
    @Test public void regionHidesUsOnlyChannelsElsewhere() throws Exception {
        android.content.Context c=org.robolectric.RuntimeEnvironment.getApplication();
        c.getSharedPreferences("fast",0).edit().putString("region","CA").commit();
        try(org.robolectric.android.controller.ActivityController<MainActivity> controller=launch()) {
            View root=controller.get().getWindow().getDecorView();measure(root,1024,600);
            assertNull(text(root,"Baywatch"));assertNotNull(text(root,"CityNews 24/7 · Toronto"));
            TextView count=null;for(View v:views(root))if(v instanceof TextView&&String.valueOf(((TextView)v).getText()).contains("channels · tap"))count=(TextView)v;
            int offered=0;for(Channel ch:Catalog.parse(Catalog.read(c.getAssets().open("channels.json"))))if(Region.offered(ch,"CA"))offered++;
            assertTrue(count.getText().toString(),count.getText().toString().startsWith(offered+" channels"));
            assertTrue(offered<58);
        }
        assertTrue(Region.offered(new Channel("x","DW","DE","News","","https://x","https://x",new String[]{"https://x"},"Worldwide"),"GB"));
        assertEquals("EU",Region.group("FR"));assertEquals("XX",Region.group("JP"));
    }
    private static View visibleText(ListView list,String s){for(int i=0;i<list.getChildCount();i++){for(View v:views(list.getChildAt(i)))if(v instanceof TextView&&s.equals(String.valueOf(((TextView)v).getText()))&&((TextView)v).getTextSize()>=29)return v;}return null;}
    private static void tap(View v){while(v!=null&&!v.isClickable()&&!(v.getParent() instanceof AdapterView))v=(View)v.getParent();
        if(v.getParent() instanceof AdapterView){AdapterView<?> list=(AdapterView<?>)v.getParent();int pos=list.getPositionForView(v);list.performItemClick(v,pos,list.getItemIdAtPosition(pos));}else v.performClick();}
    private static java.util.List<View> views(View v){java.util.List<View> all=new java.util.ArrayList<>();all.add(v);if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)all.addAll(views(g.getChildAt(i)));}return all;}
    private static TextView heading(View v){if(v instanceof TextView&&((TextView)v).getText().toString().equals("Live TV")&&((TextView)v).getTextSize()>=29)return(TextView)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){TextView t=heading(g.getChildAt(i));if(t!=null)return t;}}return null;}
    private static void measure(View v,int w,int h){v.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));v.layout(0,0,w,h);}
    private static TextView text(View v,String s){if(v instanceof TextView&&((TextView)v).getText().toString().equals(s))return(TextView)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){TextView t=text(g.getChildAt(i),s);if(t!=null)return t;}}return null;}
    private static EditText edit(View v){if(v instanceof EditText)return(EditText)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){EditText t=edit(g.getChildAt(i));if(t!=null)return t;}}return null;}
    private static void capture(View v,String name)throws Exception{if(!"true".equals(System.getProperty("fast.capture")))return;java.io.File dir=new java.io.File("../work/ui-preview");dir.mkdirs();Bitmap image=Bitmap.createBitmap(v.getWidth(),v.getHeight(),Bitmap.Config.ARGB_8888);v.draw(new Canvas(image));try(java.io.OutputStream out=new java.io.FileOutputStream(new java.io.File(dir,name))){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();}
}
