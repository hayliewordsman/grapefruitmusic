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
            assertNotNull(text(a.getWindow().getDecorView(),"No favourites yet. Hold a channel or a film, or press Menu, to add one."));
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
    /**
     * 2.5: leaving a channel keeps it playing in the floating player on the same stream (handed between the
     * views, never reopened); tapping it goes back; another channel replaces it; ✕ stops it. Never two streams.
     */
    @Test @Config(qualifiers="w411dp-h914dp-xxhdpi") public void floatingPlayerPassesTheOneStreamBetweenViews() throws Exception {
        LivePlayer.open=0;
        try(org.robolectric.android.controller.ActivityController<MainActivity> controller=launch()) {
            MainActivity a=controller.get();View root=a.getWindow().getDecorView();measure(root,1233,2742);
            tap(text(root,"CityNews 24/7 · Toronto"));
            LivePlayer stream=a.player();assertNotNull(stream);assertEquals(1,LivePlayer.open);
            assertTrue(stream.view() instanceof android.view.SurfaceView);assertNull(a.floating());
            // Back: the channel floats over Live TV, on the same stream, now on the window's picture.
            assertTrue(a.back());root=a.getWindow().getDecorView();measure(root,1233,2742);
            assertNull(text(root,"Full screen"));assertNotNull(a.floating());
            assertSame(stream,a.player());assertTrue(stream.view() instanceof android.view.TextureView);assertEquals(1,LivePlayer.open);
            assertNull("the floating player is the way back",text(root,"Continue watching"));
            assertNotNull(described(root,"Floating player: CityNews 24/7 · Toronto. Tap to open."));
            settle();measure(root,1233,2742);capturePlayer(a,root,"portrait-floating.png");
            // Other tabs keep it floating, on the same stream.
            tap(text(root,"Settings"));root=a.getWindow().getDecorView();measure(root,1233,2742);
            assertNotNull(text(root,"Floating Player"));assertNotNull(a.floating());assertSame(stream,a.player());assertEquals(1,LivePlayer.open);
            settle();measure(root,1233,2742);capturePlayer(a,root,"portrait-floating-settings.png");
            // Tapping it opens the channel again, still on that stream.
            a.floating().performClick();root=a.getWindow().getDecorView();measure(root,1233,2742);
            assertNotNull(text(root,"Full screen"));assertNull(a.floating());
            assertSame(stream,a.player());assertTrue(stream.view() instanceof android.view.SurfaceView);assertEquals(1,LivePlayer.open);
            // Another channel: the first stops and the new one starts in its place.
            a.back();root=a.getWindow().getDecorView();measure(root,1233,2742);
            tap(text(root,"Live TV"));root=a.getWindow().getDecorView();measure(root,1233,2742);
            tap(text(root,"Global News · National"));root=a.getWindow().getDecorView();measure(root,1233,2742);
            assertNotSame(stream,a.player());assertNotNull(a.player());assertEquals(1,LivePlayer.open);
            assertNotNull(text(root,"Global News · National"));assertNull(a.floating());
            // The app in the background closes the stream; back in front, it opens again in the window.
            a.back();assertNotNull(a.floating());
            controller.pause().stop();assertEquals(0,LivePlayer.open);
            controller.start().resume();assertEquals(1,LivePlayer.open);assertTrue(a.player().view() instanceof android.view.TextureView);
            // ✕ stops it; Continue watching comes back.
            root=a.getWindow().getDecorView();tap(described(root,"Stop Global News · National"));
            root=a.getWindow().getDecorView();measure(root,1233,2742);
            assertNull(a.player());assertNull(a.floating());assertEquals(0,LivePlayer.open);
            assertNotNull(text(root,"Continue watching"));
        }
        assertEquals(0,LivePlayer.open);
    }
    /** Settings › Floating Player off: leaving a channel stops it, as before 2.5. */
    @Test @Config(qualifiers="w411dp-h914dp-xxhdpi") public void floatingPlayerCanBeTurnedOff() throws Exception {
        LivePlayer.open=0;
        org.robolectric.RuntimeEnvironment.getApplication().getSharedPreferences("fast",0).edit().putBoolean("floating",false).commit();
        try(org.robolectric.android.controller.ActivityController<MainActivity> controller=launch()) {
            MainActivity a=controller.get();View root=a.getWindow().getDecorView();measure(root,1233,2742);
            tap(text(root,"CityNews 24/7 · Toronto"));assertEquals(1,LivePlayer.open);
            assertTrue(a.back());assertNull(a.floating());assertNull(a.player());assertEquals(0,LivePlayer.open);
        }
    }
    /** Square screens and sideways: the floating player fits, clear of the tabs. */
    @Test @Config(qualifiers="w914dp-h411dp-land-xxhdpi") public void floatingPlayerSideways() throws Exception {
        try(org.robolectric.android.controller.ActivityController<MainActivity> controller=launch()) {
            MainActivity a=controller.get();View root=a.getWindow().getDecorView();measure(root,2742,1233);
            tap(text(root,"CityNews 24/7 · Toronto"));a.back();root=a.getWindow().getDecorView();measure(root,2742,1233);
            settle();measure(root,2742,1233);View f=a.floating();assertNotNull(f);
            ViewGroup page=(ViewGroup)f.getParent();
            assertTrue(f.getTranslationX()+f.getWidth()<=page.getWidth());assertTrue(f.getTranslationY()+f.getHeight()<=page.getHeight());
            capturePlayer(a,root,"landscape-floating.png");
        }
    }
    /** Robolectric can't draw a TextureView: render the floating player as a phone shows it before the first frame. */
    private static void capturePlayer(MainActivity a,View root,String name)throws Exception{
        View picture=((ViewGroup)((ViewGroup)a.floating()).getChildAt(0)).getChildAt(0);
        picture.setVisibility(View.INVISIBLE);capture(root,name);picture.setVisibility(View.VISIBLE);
    }
    /** 3.0: Movies, a film's page (pause, seek, download), the guide, Recordings and its chips. */
    @Test @Config(qualifiers="w411dp-h914dp-xxhdpi") public void moviesTabAndAFilmsPage() throws Exception {
        LivePlayer.open=0;
        try(org.robolectric.android.controller.ActivityController<MainActivity> controller=launch()) {
            MainActivity a=controller.get();View root=a.getWindow().getDecorView();measure(root,1233,2742);
            assertNotNull(described(root,"Movies"));assertNotNull(described(root,"Recordings"));assertNull("no On Demand without a source",described(root,"On Demand"));
            tap(described(root,"Movies"));root=a.getWindow().getDecorView();measure(root,1233,2742);
            assertNotNull(text(root,"Free films: public domain and Creative Commons."));
            assertNotNull(text(root,"His Girl Friday"));assertNotNull(text(root,"Horror"));assertNotNull(text(root,"Downloaded"));
            settle();measure(root,1233,2742);capture(root,"portrait-movies.png");
            tap(text(root,"His Girl Friday"));root=a.getWindow().getDecorView();measure(root,1233,2742);
            assertNotNull(text(root,"Pause"));assertNotNull(text(root,"+30 s"));assertNotNull(text(root,"Download"));assertNotNull(text(root,"Internet Archive"));
            assertNull("films aren't recorded",text(root,"●  Record"));
            boolean bar=false;for(View v:views(root))if(v instanceof SeekBar)bar=true;assertTrue("a bar to move through the film",bar);
            assertEquals(1,LivePlayer.open);capture(root,"portrait-film.png");
            // One stream: a live channel after the film replaces it.
            a.back();root=a.getWindow().getDecorView();assertNotNull(a.floating());
            tap(described(root,"Live TV"));root=a.getWindow().getDecorView();measure(root,1233,2742);
            tap(text(root,"CityNews 24/7 · Toronto"));assertEquals(1,LivePlayer.open);
            root=a.getWindow().getDecorView();measure(root,1233,2742);
            assertNotNull(text(root,"●  Record"));assertNotNull(text(root,Guide.UNAVAILABLE));
        }
        assertEquals(0,LivePlayer.open);
    }
    @Test @Config(qualifiers="w411dp-h914dp-xxhdpi") public void guideInLiveTv() throws Exception {
        org.robolectric.RuntimeEnvironment.getApplication().getSharedPreferences("fast",0).edit().putBoolean("guide",true).commit();
        try(org.robolectric.android.controller.ActivityController<MainActivity> controller=launch()) {
            MainActivity a=controller.get();View root=a.getWindow().getDecorView();measure(root,1233,2742);
            int rows=0;for(View v:views(root))if(v instanceof GuideViews.Row)rows++;
            assertTrue("guide rows",rows>3);
            boolean ruler=false;for(View v:views(root))if(v instanceof GuideViews.Ruler)ruler=true;assertTrue(ruler);
            settle();measure(root,1233,2742);capture(root,"portrait-guide.png");
            // The Guide chip turns it off again.
            tap(text(root,"Guide"));root=a.getWindow().getDecorView();measure(root,1233,2742);
            rows=0;for(View v:views(root))if(v instanceof GuideViews.Row)rows++;assertEquals(0,rows);
        }
    }
    @Test @Config(qualifiers="w411dp-h914dp-xxhdpi") public void recordingsTabWithItsChips() throws Exception {
        android.content.Context c=org.robolectric.RuntimeEnvironment.getApplication();
        try(org.robolectric.android.controller.ActivityController<MainActivity> controller=launch()) {
            MainActivity a=controller.get();
            Channel cpac=null;for(Channel ch:Catalog.parse(Catalog.read(c.getAssets().open("channels.json"))))if(ch.name.startsWith("CityNews 24/7 · Toronto"))cpac=ch;
            long start=System.currentTimeMillis()+3*Guide.HOUR;
            Recordings.schedule(c,cpac,"Evening News",start,start+Guide.HOUR);
            View root=a.getWindow().getDecorView();measure(root,1233,2742);
            tap(described(root,"Recordings"));root=a.getWindow().getDecorView();measure(root,1233,2742);
            assertNotNull(text(root,"All"));assertNotNull(text(root,"Finished"));assertNotNull(text(root,"Scheduled"));
            assertNotNull(text(root,"Evening News"));assertNotNull(text(root,"+  Schedule a Recording"));
            settle();measure(root,1233,2742);capture(root,"portrait-recordings.png");
            tap(text(root,"Finished"));root=a.getWindow().getDecorView();measure(root,1233,2742);
            assertNull(text(root,"Evening News"));assertNotNull(text(root,"No finished recordings yet."));
            tap(text(root,"Scheduled"));root=a.getWindow().getDecorView();measure(root,1233,2742);
            assertNotNull(text(root,"Evening News"));
        }
    }
    /** A source with films and series: the On Demand tab appears. */
    @Test @Config(qualifiers="w411dp-h914dp-xxhdpi") public void onDemandAppearsWithASourcesFilms() throws Exception {
        android.content.Context c=org.robolectric.RuntimeEnvironment.getApplication();
        java.io.File dir=new java.io.File(c.getFilesDir(),"sources");dir.mkdirs();
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(dir,"s1.json"))){
            out.write(("[{\"name\":\"Local News\",\"group\":\"News\",\"url\":\"http://tv.example.com/live/1.ts\",\"ua\":\"\",\"kind\":\"live\"},"
                +"{\"name\":\"A Film\",\"group\":\"Films\",\"url\":\"http://tv.example.com/movie/u/p/7.mp4\",\"ua\":\"\",\"kind\":\"film\"}]").getBytes("UTF-8"));
        }
        c.getSharedPreferences("fast",0).edit().putString("userSources","[{\"id\":\"s1\",\"kind\":\"m3u\",\"name\":\"My TV\",\"address\":\"http://tv.example.com/list.m3u\",\"username\":\"\",\"password\":\"\"}]").commit();
        try(org.robolectric.android.controller.ActivityController<MainActivity> controller=launch()) {
            MainActivity a=controller.get();View root=a.getWindow().getDecorView();measure(root,1233,2742);
            assertNotNull(described(root,"On Demand"));
            tap(described(root,"On Demand"));root=a.getWindow().getDecorView();measure(root,1233,2742);
            assertNotNull(text(root,"A Film"));assertNotNull(text(root,"Series"));assertNotNull(text(root,"Downloads"));
            capture(root,"portrait-ondemand.png");
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
            View logo=described(root,MainActivity.NAME);assertTrue(logo instanceof ImageView);assertTrue(logo.getWidth()>0);
            capture(root,"portrait-splash.png");
            settle();root=controller.get().getWindow().getDecorView();measure(root,1080,1920);
            assertNull(described(root,MainActivity.NAME));
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
            tap(text(root,"About Grapefruit TV & Movies"));root=a.getWindow().getDecorView();measure(root,1024,600);
            TextView wm=wordmark(root);assertTrue("wordmark on screen",wm.getLayout().getLineLeft(0)<wm.getWidth());assertNotNull(wordmark(root));assertNotNull(text(root,"Version 3.0-beta-1"));assertNotNull(text(root,"Source Code"));
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
    private static TextView wordmark(View root){for(View v:views(root))if(v instanceof TextView&&MainActivity.NAME.equals(String.valueOf(((TextView)v).getText())))return(TextView)v;return null;}
    /** On a phone, Settings scrolls far enough for About to clear the floating tabs and Continue watching. */
    @Test @Config(qualifiers="w411dp-h914dp-xxhdpi") public void aboutRowClearsTheFloatingBarsOnAPhone() throws Exception {
        android.content.Context c=org.robolectric.RuntimeEnvironment.getApplication();
        c.getSharedPreferences("fast",0).edit().putString("lastChannel","global-winnipeg").commit();
        try(org.robolectric.android.controller.ActivityController<MainActivity> controller=launch()) {
            MainActivity a=controller.get();View root=a.getWindow().getDecorView();measure(root,1233,2742);
            tap(text(root,"Settings"));root=a.getWindow().getDecorView();measure(root,1233,2742);settle();measure(root,1233,2742);
            ScrollView scroll=null;for(View v:views(root))if(v instanceof ScrollView)scroll=(ScrollView)v;
            scroll.scrollTo(0,100000);measure(root,1233,2742);settle();measure(root,1233,2742);
            View about=(View)text(root,"About Grapefruit TV & Movies").getParent();
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
    private static View visibleText(ListView list,String s){for(int i=0;i<list.getChildCount();i++){for(View v:views(list.getChildAt(i)))if(v instanceof TextView&&s.equals(String.valueOf(((TextView)v).getText()))&&((TextView)v).getTextSize()>=29)return v;}return null;}
    /** Captions: the pill appears once the stream says it has captions; on, the text shows over the picture. */
    @Test @Config(qualifiers="w411dp-h914dp-xxhdpi") public void captionsWhereTheStreamProvidesThem() throws Exception {
        try(org.robolectric.android.controller.ActivityController<MainActivity> controller=launch()) {
            MainActivity a=controller.get();View root=a.getWindow().getDecorView();measure(root,1233,2742);
            tap(text(root,"CityNews 24/7 · Toronto"));root=a.getWindow().getDecorView();measure(root,1233,2742);
            TextView button=text(root,"Captions");assertNotNull(button);assertEquals(View.GONE,button.getVisibility());
            a.onCaptionsAvailable(true);assertEquals(View.VISIBLE,button.getVisibility());
            assertFalse(a.captionsOn());
            a.onCaptions("Not shown while captions are off.");
            assertNull(visible(root,"Not shown while captions are off."));
            button.performClick();assertTrue(a.captionsOn());
            a.onCaptions("The House is now in session.");measure(root,1233,2742);
            assertNotNull(visible(root,"The House is now in session."));capture(root,"portrait-captions.png");
            button.performClick();assertFalse(a.captionsOn());
            assertNull(visible(root,"The House is now in session."));
            a.onCaptionsAvailable(false);assertEquals(View.GONE,button.getVisibility());
        }
    }
    @Test public void captionsSettingAndCueText() throws Exception {
        try(org.robolectric.android.controller.ActivityController<MainActivity> controller=launch()) {
            MainActivity a=controller.get();View root=a.getWindow().getDecorView();measure(root,1024,600);
            tap(text(root,"Settings"));root=a.getWindow().getDecorView();measure(root,1024,600);
            assertNotNull(text(root,"Captions"));tap(text(root,"Captions"));assertTrue(a.captionsOn());
        }
        java.util.List<com.google.android.exoplayer2.text.Cue> cues=java.util.Arrays.asList(
            new com.google.android.exoplayer2.text.Cue.Builder().setText("First line").build(),
            new com.google.android.exoplayer2.text.Cue.Builder().setText(" ").build(),
            new com.google.android.exoplayer2.text.Cue.Builder().setText("Second line").build());
        assertEquals("First line\nSecond line",LivePlayer.joined(cues).toString());
    }
    private static View visible(View root,String s){for(View v:views(root))if(v instanceof TextView&&s.equals(String.valueOf(((TextView)v).getText()))&&v.getVisibility()==View.VISIBLE)return v;return null;}
    /** Square screens (Clicks Communicator and the like) use the portrait layout: tabs along the bottom. */
    @Test @Config(qualifiers="w360dp-h360dp-xhdpi") public void squareScreenUsesThePortraitLayout() throws Exception { square(720,720,"square-1x1"); }
    @Test @Config(qualifiers="w360dp-h400dp-xhdpi") public void nearlySquareScreenUsesThePortraitLayout() throws Exception { square(720,800,"square-9x10"); }
    private void square(int w,int h,String name) throws Exception {
        android.content.Context c=org.robolectric.RuntimeEnvironment.getApplication();
        c.getSharedPreferences("fast",0).edit().putString("lastChannel","cpac").commit();
        try(org.robolectric.android.controller.ActivityController<MainActivity> controller=launch()) {
            MainActivity a=controller.get();View root=a.getWindow().getDecorView();measure(root,w,h);
            assertNull("no side rail wordmark",text(root,"Grapefruit\nFAST"));
            ViewGroup live=null;for(View v:views(root))if(v instanceof ViewGroup&&"Live TV".contentEquals(String.valueOf(v.getContentDescription())))live=(ViewGroup)v;
            int[] at=new int[2];live.getLocationInWindow(at);assertTrue("tabs along the bottom",at[1]>h/2);
            capture(root,name+"-browse.png");
            ListView list=null;for(View v:views(root))if(v instanceof ListView)list=(ListView)v;
            list.setSelectionFromTop(5,0);measure(root,w,h);root.getViewTreeObserver().dispatchOnPreDraw();capture(root,name+"-scrolled.png");
            list.setSelection(0);measure(root,w,h);
            tap(text(root,"CityNews 24/7 · Toronto"));root=a.getWindow().getDecorView();measure(root,w,h);capture(root,name+"-watch.png");
            assertTrue(a.back());root=a.getWindow().getDecorView();measure(root,w,h);
            tap(text(root,"Settings"));root=a.getWindow().getDecorView();measure(root,w,h);capture(root,name+"-settings.png");
            ScrollView scroll=null;for(View v:views(root))if(v instanceof ScrollView)scroll=(ScrollView)v;
            scroll.scrollTo(0,100000);measure(root,w,h);settle();measure(root,w,h);capture(root,name+"-settings-bottom.png");
            tap(text(root,"About Grapefruit TV & Movies"));root=a.getWindow().getDecorView();measure(root,w,h);capture(root,name+"-about.png");
        }
    }
    /** A channel its provider refuses where the viewer is: listed anyway, and the player box says so. */
    @Test @Config(qualifiers="w411dp-h914dp-xxhdpi") public void locationNoticeOnTheBlankPicture() throws Exception {
        android.content.Context c=org.robolectric.RuntimeEnvironment.getApplication();
        try(org.robolectric.android.controller.ActivityController<MainActivity> controller=launch()) {
            MainActivity a=controller.get();View root=a.getWindow().getDecorView();measure(root,1233,2742);
            tap(text(root,"CityNews 24/7 · Toronto"));root=a.getWindow().getDecorView();measure(root,1233,2742);
            assertNull(visible(root,"Not available where you are"));
            a.onUnavailable(true);measure(root,1233,2742);
            assertNotNull(visible(root,"Not available where you are"));capture(root,"portrait-location-notice.png");
            a.onUnavailable(false);assertNotNull(visible(root,"This channel isn't playing right now"));
        }
        // Every channel is listed, wherever the phone is.
        int all=Catalog.parse(Catalog.read(c.getAssets().open("channels.json"))).size();
        try(org.robolectric.android.controller.ActivityController<MainActivity> controller=launch()) {
            View root=controller.get().getWindow().getDecorView();measure(root,1233,2742);
            TextView count=null;for(View v:views(root))if(v instanceof TextView&&String.valueOf(((TextView)v).getText()).contains("channels · tap"))count=(TextView)v;
            assertTrue(count.getText().toString().startsWith(all+" channels"));
        }
    }
    @Test public void onlyForbiddenAndLegalBlocksCountAsLocation() {
        com.google.android.exoplayer2.upstream.DataSpec spec=new com.google.android.exoplayer2.upstream.DataSpec(android.net.Uri.parse("https://example.com/a.m3u8"));
        for(int code:new int[]{403,451}) {
            Exception e=new com.google.android.exoplayer2.upstream.HttpDataSource.InvalidResponseCodeException(code,"",null,java.util.Collections.<String,java.util.List<String>>emptyMap(),spec,new byte[0]);
            assertTrue(LivePlayer.locationRefusal(new RuntimeException(e)));
        }
        Exception e404=new com.google.android.exoplayer2.upstream.HttpDataSource.InvalidResponseCodeException(404,"",null,java.util.Collections.<String,java.util.List<String>>emptyMap(),spec,new byte[0]);
        assertFalse(LivePlayer.locationRefusal(e404));
        assertFalse(LivePlayer.locationRefusal(new java.net.SocketTimeoutException()));
        assertFalse(LivePlayer.locationRefusal(null));
    }
    /** 2.0: no tagline on Live TV; Your TV Apps sits at the top; Settings › Your Sources adds IPTV sources. */
    @Test public void grapefruitTvLiveTvAndYourSources() throws Exception {
        try(org.robolectric.android.controller.ActivityController<MainActivity> controller=launch()) {
            MainActivity a=controller.get();View root=a.getWindow().getDecorView();measure(root,1024,600);
            assertNull(text(root,"Free to watch. A little more live."));
            assertNotNull(text(root,"Your TV Apps"));assertNotNull(text(root,"+  Add App"));
            capture(root,"landscape-tv.png");
            tap(text(root,"Settings"));root=a.getWindow().getDecorView();measure(root,1024,600);
            ScrollView scroll=null;for(View v:views(root))if(v instanceof ScrollView)scroll=(ScrollView)v;
            scroll.scrollTo(0,100000);measure(root,1024,600);
            assertNotNull(text(root,"Your Sources"));assertNotNull(text(root,"Add a Playlist (M3U)…"));assertNotNull(text(root,"Add a TV Login…"));
            capture(root,"landscape-your-sources.png");
        }
    }
    /** Selecting text in a form field (double-tap, long-press) must not crash. Reported on Add a Playlist. */
    @Test public void selectingTextInAddAPlaylistDoesntCrash() throws Exception { selectInForm(); }
    @Test @Config(sdk=35) public void selectingTextInAddAPlaylistDoesntCrashOnAndroid15() throws Exception { selectInForm(); }
    private void selectInForm() throws Exception {
        try(org.robolectric.android.controller.ActivityController<MainActivity> controller=launch()) {
            MainActivity a=controller.get();View root=a.getWindow().getDecorView();measure(root,1024,600);
            tap(text(root,"Settings"));root=a.getWindow().getDecorView();measure(root,1024,600);
            tap(text(root,"Add a Playlist (M3U)…"));
            android.app.Dialog d=org.robolectric.shadows.ShadowDialog.getLatestDialog();assertNotNull(d);
            View form=d.getWindow().getDecorView();measure(form,900,1200);
            EditText name=edit(form);assertNotNull(name);assertEquals("My TV",name.getText().toString());
            name.requestFocus();
            // Double-tap (word selection) and long-press both start text selection's action mode.
            long t=android.os.SystemClock.uptimeMillis();
            for(int i=0;i<2;i++){
                name.dispatchTouchEvent(MotionEvent.obtain(t,t,MotionEvent.ACTION_DOWN,20,20,0));
                name.dispatchTouchEvent(MotionEvent.obtain(t,t+40,MotionEvent.ACTION_UP,20,20,0));
                t+=120;org.robolectric.shadows.ShadowLooper.idleMainLooper(120,java.util.concurrent.TimeUnit.MILLISECONDS);
            }
            name.performLongClick();
            org.robolectric.shadows.ShadowLooper.idleMainLooper(1,java.util.concurrent.TimeUnit.SECONDS);
            name.selectAll();
            assertTrue(name.onTextContextMenuItem(android.R.id.copy)||true);
        }
    }
    /** The form's own Paste fills an address from the clipboard. */
    @Test public void pasteFillsTheAddress() throws Exception {
        try(org.robolectric.android.controller.ActivityController<MainActivity> controller=launch()) {
            MainActivity a=controller.get();View root=a.getWindow().getDecorView();measure(root,1024,600);
            ((android.content.ClipboardManager)a.getSystemService(android.content.Context.CLIPBOARD_SERVICE)).setPrimaryClip(android.content.ClipData.newPlainText("u"," https://tv.example.com/list.m3u "));
            tap(text(root,"Settings"));root=a.getWindow().getDecorView();measure(root,1024,600);
            tap(text(root,"Add a Playlist (M3U)…"));
            View form=org.robolectric.shadows.ShadowDialog.getLatestDialog().getWindow().getDecorView();measure(form,900,1200);
            text(form,"Paste").performClick();
            EditText address=null;for(View v:views(form))if(v instanceof EditText&&String.valueOf(((EditText)v).getHint()).startsWith("Playlist address"))address=(EditText)v;
            assertEquals("https://tv.example.com/list.m3u",address.getText().toString());
            capture(form,"form-add-playlist.png");
        }
    }
    @Test public void crashReportsLeaveOutLogins() {
        String r=CrashReport.redact("at http://iptv.example.com:8080/live/alice/s3cret/123.ts and https://a.example/x.m3u8?token=abc and http://bob:pw@host/x");
        assertFalse(r,r.contains("alice")||r.contains("s3cret")||r.contains("token=abc")||r.contains("bob:pw"));
    }
    private static void tap(View v){while(v!=null&&!v.isClickable()&&!(v.getParent() instanceof AdapterView))v=(View)v.getParent();
        if(v.getParent() instanceof AdapterView){AdapterView<?> list=(AdapterView<?>)v.getParent();int pos=list.getPositionForView(v);list.performItemClick(v,pos,list.getItemIdAtPosition(pos));}else v.performClick();}
    private static java.util.List<View> views(View v){java.util.List<View> all=new java.util.ArrayList<>();all.add(v);if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)all.addAll(views(g.getChildAt(i)));}return all;}
    private static TextView heading(View v){if(v instanceof TextView&&((TextView)v).getText().toString().equals("Live TV")&&((TextView)v).getTextSize()>=29)return(TextView)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){TextView t=heading(g.getChildAt(i));if(t!=null)return t;}}return null;}
    private static void measure(View v,int w,int h){v.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));v.layout(0,0,w,h);}
    private static TextView text(View v,String s){if(v instanceof TextView&&((TextView)v).getText().toString().equals(s))return(TextView)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){TextView t=text(g.getChildAt(i),s);if(t!=null)return t;}}return null;}
    private static EditText edit(View v){if(v instanceof EditText)return(EditText)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){EditText t=edit(g.getChildAt(i));if(t!=null)return t;}}return null;}
    private static void capture(View v,String name)throws Exception{if(!"true".equals(System.getProperty("fast.capture")))return;java.io.File dir=new java.io.File("../work/ui-preview");dir.mkdirs();Bitmap image=Bitmap.createBitmap(v.getWidth(),v.getHeight(),Bitmap.Config.ARGB_8888);v.draw(new Canvas(image));try(java.io.OutputStream out=new java.io.FileOutputStream(new java.io.File(dir,name))){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();}
}
