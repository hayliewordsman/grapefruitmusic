package com.grapefruitmusic.playback;

import android.app.Application;
import android.content.Context;
import android.media.MediaPlayer;
import android.os.Looper;
import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={23,37},application=Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class MediaPlayerLoadingTest {
    @Test public void blockedPodcastSetupAndCancellationLeaveTheUiResponsive() throws Exception {
        SlowPlayer mp=new SlowPlayer();MediaPlayerEngine engine=new MediaPlayerEngine(RuntimeEnvironment.getApplication(),mp);AtomicInteger callbacks=observe(engine);
        try{
            engine.setDataSource("http://127.0.0.1/podcast");engine.prepareAsync();assertTrue(mp.entered.await(2,TimeUnit.SECONDS));assertFalse(mp.sourceOnMain);
            engine.setVolume(0.4f);assertEquals(0,mp.volumeCalls);assertFalse(engine.isPlaying());assertEquals(0,engine.getDuration());
            engine.release();engine.release();AtomicInteger ui=new AtomicInteger();new android.os.Handler(Looper.getMainLooper()).post(ui::incrementAndGet);Shadows.shadowOf(Looper.getMainLooper()).idle();assertEquals(1,ui.get());
            assertEquals("Source setup is still blocked",1,mp.proceed.getCount());mp.preparedListener.onPrepared(mp);assertEquals("Stale prepared events ignored",0,callbacks.get());
        }finally{mp.proceed.countDown();engine.release();}
        assertTrue(mp.closed.await(2,TimeUnit.SECONDS));assertEquals("Cancelled source never prepared",1,mp.prepared.getCount());assertFalse(mp.releaseOnMain);
    }
    @Test public void nextEpisodeCanPrepareWhilePreviousEpisodeIsBlocked() throws Exception {
        SlowPlayer oldMp=new SlowPlayer(),nextMp=new SlowPlayer();MediaPlayerEngine oldEngine=new MediaPlayerEngine(RuntimeEnvironment.getApplication(),oldMp),nextEngine=new MediaPlayerEngine(RuntimeEnvironment.getApplication(),nextMp);AtomicInteger oldEvents=observe(oldEngine),nextEvents=observe(nextEngine);
        try{
            oldEngine.setDataSource("http://127.0.0.1/slow");oldEngine.prepareAsync();assertTrue(oldMp.entered.await(2,TimeUnit.SECONDS));oldEngine.release();
            nextEngine.setDataSource("http://127.0.0.1/next");nextEngine.setVolume(0.6f);nextEngine.prepareAsync();nextMp.proceed.countDown();assertTrue(nextMp.prepared.await(2,TimeUnit.SECONDS));nextMp.preparedListener.onPrepared(nextMp);
            assertEquals(1,nextEvents.get());assertEquals(0,oldEvents.get());assertEquals(0.6f,nextMp.appliedVolume,0f);assertEquals(1,oldMp.proceed.getCount());
        }finally{oldMp.proceed.countDown();nextMp.proceed.countDown();oldEngine.release();nextEngine.release();}
        assertTrue(oldMp.closed.await(2,TimeUnit.SECONDS));assertTrue(nextMp.closed.await(2,TimeUnit.SECONDS));
    }
    @Test public void sourceErrorsReturnToTheMainThreadAndDoNotResurrectCancelledPlayback() throws Exception {
        SlowPlayer mp=new SlowPlayer();mp.fail=true;MediaPlayerEngine engine=new MediaPlayerEngine(RuntimeEnvironment.getApplication(),mp);AtomicInteger events=observe(engine);
        try{engine.setDataSource("http://127.0.0.1/fails");engine.prepareAsync();mp.proceed.countDown();assertTrue(mp.finished.await(2,TimeUnit.SECONDS));
            // Put a marker after the source task, so its failure callback has definitely been queued.
            java.util.concurrent.ExecutorService worker=org.robolectric.util.ReflectionHelpers.getField(engine,"sourceWorker");worker.submit(()->{}).get(2,TimeUnit.SECONDS);
            Shadows.shadowOf(Looper.getMainLooper()).idle();assertEquals(1,events.get());
        }finally{mp.proceed.countDown();engine.release();}assertTrue(mp.closed.await(2,TimeUnit.SECONDS));
    }
    private static AtomicInteger observe(MediaPlayerEngine engine){AtomicInteger events=new AtomicInteger();engine.setCallback(new Engine.Callback(){public void onPrepared(Engine e){assertSame(Looper.getMainLooper(),Looper.myLooper());events.incrementAndGet();}public void onCompletion(Engine e){events.incrementAndGet();}public void onError(Engine e,String message){assertSame(Looper.getMainLooper(),Looper.myLooper());events.incrementAndGet();}});return events;}
    private static final class SlowPlayer extends MediaPlayer {
        final CountDownLatch entered=new CountDownLatch(1),proceed=new CountDownLatch(1),prepared=new CountDownLatch(1),closed=new CountDownLatch(1),finished=new CountDownLatch(1);
        volatile boolean sourceOnMain,releaseOnMain,fail;int volumeCalls;float appliedVolume;OnPreparedListener preparedListener;
        @Override public void setAudioStreamType(int type){}
        @Override public void setWakeMode(Context c,int mode){}
        @Override public void setOnPreparedListener(OnPreparedListener listener){preparedListener=listener;}
        @Override public void setDataSource(String source)throws IOException{sourceOnMain=Looper.myLooper()==Looper.getMainLooper();entered.countDown();try{if(!proceed.await(3,TimeUnit.SECONDS))throw new IOException("Simulated slow response");if(fail)throw new IOException("Simulated failure");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException(e);}finally{finished.countDown();}}
        @Override public void prepareAsync(){prepared.countDown();}
        @Override public void setVolume(float left,float right){volumeCalls++;appliedVolume=left;}
        @Override public void reset(){}
        @Override public void release(){releaseOnMain=Looper.myLooper()==Looper.getMainLooper();closed.countDown();}
    }
}
