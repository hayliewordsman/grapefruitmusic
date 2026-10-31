package com.grapefruitmusic.playback;

import java.io.File;
import java.io.FileInputStream;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

public class StadiumCheerTest {
    @BeforeClass public static void clips() throws Exception {
        File f = new File("src/main/assets/stadium_cheers.pcm");
        if (!f.exists()) f = new File("app/src/main/assets/stadium_cheers.pcm");
        StadiumCheer.load(new FileInputStream(f));
    }
    @Test public void openingAndClosingCheerWithoutAnyMusic() {
        StadiumCheer c = new StadiumCheer(44100); c.position(0);
        double opening = 0, closing = 0;
        for (int i=0;i<44100*3;i++) { c.process(1); opening += c.outL*c.outL+c.outR*c.outR; }
        c.end();
        for (int i=0;i<44100*3;i++) { c.process(1); closing += c.outL*c.outL+c.outR*c.outR; }
        assertTrue(opening > 100); assertTrue(closing > 100);
        assertNotEquals(opening,closing,0.001);
    }
    @Test public void noCheerBeforeTimelineIsSetAndAfterClipEnds() {
        StadiumCheer c = new StadiumCheer(48000);
        c.process(1); assertEquals(0,c.outL,0);
        c.position(0);
        for(int i=0;i<48000*4;i++) c.process(1);
        assertEquals(0,c.outL,0); assertEquals(0,c.outR,0);
    }
    @Test public void seekingPastOpeningDoesNotRestartCheer() {
        StadiumCheer c = new StadiumCheer(44100); c.position(60000);
        for(int i=0;i<10000;i++) { c.process(1); assertEquals(0,c.outL,0); }
        c.end(); double energy=0;
        for(int i=0;i<44100;i++) { c.process(1); energy+=c.outL*c.outL; }
        assertTrue(energy>10);
        c.position(90000); c.process(1); assertEquals(0,c.outL,0);
    }
    @Test public void zeroStrengthAndDisabledTimelineDoNotQueueAnIntro() {
        StadiumCheer c=new StadiumCheer(44100); c.position(0);
        for(int i=0;i<44100*4;i++) { c.process(0); assertEquals(0,c.outL,0); }
        c.process(1); assertEquals(0,c.outL,0);
    }
    @Test public void allSampleRatesProduceBoundedStereoCheers() {
        for(int rate:new int[]{8000,22050,44100,48000,96000}) {
            StadiumCheer c=new StadiumCheer(rate); c.position(0);
            double stereo=0;
            for(int i=0;i<rate*4;i++) {
                c.process(1);
                assertTrue(Math.abs(c.outL)<=0.58 && Math.abs(c.outR)<=0.58);
                stereo+=Math.abs(c.outL-c.outR);
            }
            assertTrue(stereo>10); assertEquals(0,c.outL,0);
        }
    }
    @Test public void openingAndEndUseExistingLimiterAndHonorLiveOff() {
        Dsp d=new Dsp(); d.setLive(true,1); d.setFormat(44100,2); d.setSongPosition(0);
        short[] pcm=new short[44100*2]; d.process(pcm,0,pcm.length);
        long energy=0; for(short s:pcm) energy+=(long)s*s;
        assertTrue(energy>100000);
        assertTrue(d.finishSongFrames()>=44100*3);
        d.setLive(false,1);
        // After the effect fade, an ending must not request an added 3-second tail.
        assertTrue(d.finishSongFrames()<44100);
    }
    @Test public void echoesAndCheersCoexistAndSeekingCancelsTheOutro() {
        Dsp with=new Dsp(), without=new Dsp();
        with.setLive(true,0.5f); without.setLive(true,0.5f);
        with.setFormat(44100,2); without.setFormat(44100,2); with.setSongPosition(0);
        short[] a=new short[44100*2], b=new short[a.length];
        for(int i=0;i<a.length;i++) a[i]=b[i]=(short)(Math.sin(i*0.1)*3000);
        with.process(a,0,a.length); without.process(b,0,b.length);
        assertFalse(java.util.Arrays.equals(a,b));
        with.finishSongFrames(); with.reset(); with.setSongPosition(60000);
        short[] silence=new short[44100*2]; with.process(silence,0,silence.length);
        for(short s:silence) assertEquals(0,s);
    }
}
