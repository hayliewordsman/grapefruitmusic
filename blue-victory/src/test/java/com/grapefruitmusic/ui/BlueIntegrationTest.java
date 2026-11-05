package com.grapefruitmusic.ui;

import android.app.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import com.grapefruitmusic.R;
import com.grapefruitmusic.util.*;
import com.grapefruitmusic.playback.PlaybackSwitch;
import com.google.android.exoplayer2.C;
import java.io.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={23,28,35,37},application=Application.class,qualifiers="w411dp-h914dp-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class BlueIntegrationTest {
    private org.robolectric.android.controller.ActivityController<MainActivity> controller;
    private MainActivity activity;
    private BlueTelevision tv;
    @Before public void launch() throws Exception {
        Application app=RuntimeEnvironment.getApplication();Prefs.init(app);Fonts.init(app);Brand.init(app);
        controller=Robolectric.buildActivity(MainActivity.class).setup().visible();activity=controller.get();tv=(BlueTelevision)activity.television();
        tv.repo.worker.submit(()->{}).get(10,TimeUnit.SECONDS);settle();
    }
    @After public void close(){if(tv!=null)tv.session.stop();if(controller!=null)controller.pause().stop().destroy();}
    private void settle(){Shadows.shadowOf(Looper.getMainLooper()).idle();activity.getFragmentManager().executePendingTransactions();View root=activity.getWindow().getDecorView();for(int pass=0;pass<3;pass++){root.measure(View.MeasureSpec.makeMeasureSpec(411,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(914,View.MeasureSpec.EXACTLY));root.layout(0,0,411,914);Shadows.shadowOf(Looper.getMainLooper()).idle();}}
    @Test public void tvChipsAndTabPopupMatchWithoutDvrAndSettingsStayUnified() throws Exception {
        assertNotNull(tv);assertEquals(59,tv.repo.channels.size());assertEquals(Arrays.asList("Channels","Guide"),tv.sections());
        activity.showTvSection("Channels");settle();View root=activity.getWindow().getDecorView();
        assertNotNull(text(root,"Guide"));assertNull(text(root,"On Demand"));assertNull(text(root,"DVR"));
        View tab=described(root,"TV");assertNotNull(tab);assertTrue(tab.performLongClick());settle();
        Field field=TabMenu.class.getDeclaredField("showing");field.setAccessible(true);PopupWindow popup=(PopupWindow)field.get(null);assertNotNull(popup);
        assertNotNull(text(popup.getContentView(),"Channels"));assertNotNull(text(popup.getContentView(),"Guide"));assertNull(text(popup.getContentView(),"On Demand"));popup.dismiss();
        activity.getFragmentManager().beginTransaction().replace(R.id.content,new SettingsFragment()).commit();settle();
        root=activity.getWindow().getDecorView();assertNotNull(text(root,"TV Providers"));assertNotNull(text(root,"Background TV Audio"));assertNotNull(text(root,"Add M3U Playlist"));assertNull(text(root,"TV Settings"));assertNull(text(root,"DVR"));
        TextView context=(TextView)activity.findViewById(R.id.np_context);assertEquals(android.text.TextUtils.TruncateAt.MARQUEE,context.getEllipsize());assertTrue(context.isSelected());
    }
    @Test public void oneTvStreamMovesToMiniPlayerAndBackgroundDisablesVideo() throws Exception {
        File file=new File(activity.getFilesDir(),"video.mp4");try(FileOutputStream out=new FileOutputStream(file)){out.write(new byte[16]);}
        Channel channel=new Channel("test","Test Film","","","","","",new String[]{file.toURI().toString()},"");channel.kind=Channel.FILM;
        assertTrue(tv.session.play(channel));settle();assertEquals(1,LivePlayer.open);LivePlayer same=tv.session.engine;
        activity.showTvNowPlaying();settle();assertTrue(tv.session.watchVisible);assertSame(same,tv.session.engine);assertEquals(1,LivePlayer.open);assertTrue(same.view() instanceof TextureView);
        activity.getFragmentManager().popBackStackImmediate();settle();assertFalse(tv.session.watchVisible);assertSame(same,tv.session.engine);assertEquals(View.GONE,activity.findViewById(R.id.mini_player).getVisibility());
        assertSame(((TvMini)tv.mini()).video.texture,same.view());
        TvMini floating=(TvMini)tv.mini();floating.place(false);settle();
        assertSame(activity.findViewById(R.id.column),floating.getParent());
        assertEquals(16f/9f,floating.getWidth()/(float)floating.getHeight(),0.02f);
        assertTrue(floating.getX()>0);
        assertTrue(floating.getY()+floating.getHeight()<=activity.findViewById(R.id.bottom_bars).getTop()-10);
        assertEquals("Pause TV",floating.pause.getContentDescription());

        tv.foreground(false);assertFalse(same.videoEnabled());assertNull(same.view());assertSame(same,tv.session.engine);
        Field selector=LivePlayer.class.getDeclaredField("selector");selector.setAccessible(true);
        com.google.android.exoplayer2.trackselection.DefaultTrackSelector tracks=(com.google.android.exoplayer2.trackselection.DefaultTrackSelector)selector.get(same);
        assertTrue(tracks.getParameters().disabledTrackTypes.contains(C.TRACK_TYPE_VIDEO));
        tv.foreground(true);assertFalse(tracks.getParameters().disabledTrackTypes.contains(C.TRACK_TYPE_VIDEO));
        floating.pause.performClick();settle();assertEquals("Play TV",floating.pause.getContentDescription());assertEquals(0,LivePlayer.open);
        floating.pause.performClick();settle();assertEquals("Pause TV",floating.pause.getContentDescription());assertEquals(1,LivePlayer.open);
        PlaybackSwitch.audioRequested();settle();assertEquals(0,LivePlayer.open);assertFalse(tv.active());
    }
    @Test public void onDemandAndDownloadsAppearOnlyWithContent() throws Exception {
        Field f=TvRepository.class.getDeclaredField("filmsBySource");f.setAccessible(true);
        Map<String,List<Channel>> map=(Map<String,List<Channel>>)f.get(tv.repo);
        Channel film=new Channel("vod-abc-4","Provider Film","","Added","Drama","","Provider",new String[]{"https://example.test/movie/4.mp4"},"","Provider","");film.kind=Channel.FILM;map.put("abc",Collections.singletonList(film));
        java.lang.reflect.Method merge=TvRepository.class.getDeclaredMethod("merge");merge.setAccessible(true);merge.invoke(tv.repo);
        assertEquals(Arrays.asList("Channels","Guide","On Demand","Downloads"),tv.sections());
        activity.showTvSection("On Demand");settle();assertNotNull(text(activity.getWindow().getDecorView(),"Provider Film"));
        TvDownloads.Item saved=new TvDownloads.Item();saved.id="finished";saved.title="Saved Film";saved.file=new File(activity.getFilesDir(),"saved.mp4").getPath();saved.state=TvDownloads.DONE;TvDownloads.put(activity,saved);
        map.clear();merge.invoke(tv.repo);tv.repo.changed();settle();assertEquals(Arrays.asList("Channels","Guide","Downloads"),tv.sections());
        TvDownloads.remove(activity,saved.id);settle();assertEquals(Arrays.asList("Channels","Guide"),tv.sections());
    }
    static TextView text(View root,String value){if(root instanceof TextView&&value.contentEquals(((TextView)root).getText()))return (TextView)root;if(root instanceof ViewGroup)for(int n=0;n<((ViewGroup)root).getChildCount();n++){TextView got=text(((ViewGroup)root).getChildAt(n),value);if(got!=null)return got;}return null;}
    static View described(View root,String value){if(value.equals(String.valueOf(root.getContentDescription())))return root;if(root instanceof ViewGroup)for(int n=0;n<((ViewGroup)root).getChildCount();n++){View got=described(((ViewGroup)root).getChildAt(n),value);if(got!=null)return got;}return null;}
}
