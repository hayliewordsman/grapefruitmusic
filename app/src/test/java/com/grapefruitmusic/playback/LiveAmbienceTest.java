package com.grapefruitmusic.playback;

import org.junit.Test;
import static org.junit.Assert.*;

public class LiveAmbienceTest {
    @Test public void zeroStrengthIsExactlyDry() {
        LiveAmbience a = new LiveAmbience(44100);
        for (int i = 0; i < 10000; i++) {
            double x = Math.sin(i * 0.1) * 0.2;
            a.process(x, -x, x, -x, 0);
            assertEquals(x, a.outL, 0);
            assertEquals(-x, a.outR, 0);
        }
    }
    @Test public void silenceDoesNotCreateAnAudience() {
        LiveAmbience a = new LiveAmbience(48000);
        for (int i = 0; i < 48000; i++) {
            a.process(0, 0, 0, 0, 1);
            assertEquals(0, a.outL, 0);
            assertEquals(0, a.outR, 0);
        }
    }
    @Test public void crowdAndVenueWorkWithoutVocalsAtEveryRate() {
        for (int rate : new int[]{8000, 22050, 44100, 48000}) {
            LiveAmbience a = new LiveAmbience(rate);
            double effect = 0, stereo = 0;
            for (int i = 0; i < rate; i++) {
                double x = Math.sin(i * 440 * 2 * Math.PI / rate) * 0.2;
                a.process(x, x, 0, 0, 1);
                assertTrue(Double.isFinite(a.outL) && Double.isFinite(a.outR));
                assertTrue(Math.abs(a.outL) < 2 && Math.abs(a.outR) < 2);
                effect += Math.abs(a.outL - x);
                stereo += Math.abs(a.outL - a.outR);
            }
            assertTrue(effect > 1);
            assertTrue(stereo > 1);
        }
    }
    @Test public void voicesProduceASingalongEvenWithSilentVenueInput() {
        LiveAmbience a = new LiveAmbience(44100);
        double sum = 0;
        for (int i = 0; i < 44100; i++) {
            a.process(0, 0, Math.sin(i*0.1)*0.2, Math.sin(i*0.1)*0.2, 1);
            sum += Math.abs(a.outL);
        }
        assertTrue(sum > 1);
    }
    @Test public void strengthIsGranularAndMonotonic() {
        double previous = 0;
        for (float strength : new float[]{0, 0.01f, 0.25f, 0.5f, 1}) {
            LiveAmbience a = new LiveAmbience(22050);
            double sum = 0;
            for (int i = 0; i < 22050; i++) {
                double x = Math.sin(i*0.1)*0.2;
                a.process(x, x, x, x, strength);
                sum += Math.abs(a.outL-x);
            }
            assertTrue(sum >= previous);
            previous = sum;
        }
    }
    @Test public void tailsDecayAfterSilence() {
        LiveAmbience a = new LiveAmbience(22050);
        for (int i = 0; i < 22050*6; i++) {
            double x = i < 22050 ? Math.sin(i*0.1)*0.3 : 0;
            a.process(x, x, x, x, 1);
        }
        assertEquals(0, a.outL, 1e-5);
        assertEquals(0, a.outR, 1e-5);
    }
    private Dsp dsp(float strength, boolean mono) throws Exception {
        Dsp d = new Dsp();
        d.setLive(true, strength);
        d.setKaraoke(false, 1, VocalRemoverTest.model());
        d.setMono(mono);
        d.setFormat(44100, 2);
        return d;
    }
    private short[] input() {
        short[] x = new short[44100*4];
        for (int i = 0; i < x.length/2; i++) {
            x[i*2] = (short)(Math.sin(i*0.1)*28000);
            x[i*2+1] = (short)(Math.sin(i*0.13)*28000);
        }
        return x;
    }
    @Test public void zeroLiveStrengthMatchesDelayedOriginal() throws Exception {
        short[] a = input(), b = a.clone();
        Dsp live = dsp(0, false);
        Dsp dry = new Dsp();
        dry.setKaraoke(true, 0, VocalRemoverTest.model());
        dry.setFormat(44100, 2);
        live.process(a, 0, a.length); dry.process(b, 0, b.length);
        assertArrayEquals(b, a);
    }
    @Test public void chunkingDoesNotChangeOutputAndLimiterBoundsIt() throws Exception {
        short[] a = input(), b = a.clone();
        Dsp one = dsp(1, false), chunks = dsp(1, false);
        one.process(a, 0, a.length);
        for (int p = 0; p < b.length; p += 1024) chunks.process(b, p, Math.min(1024, b.length-p));
        assertArrayEquals(a,b);
        for (short x : a) assertTrue(Math.abs((int)x) <= 30934);
    }
    @Test public void seekResetClearsCrowdHistory() throws Exception {
        Dsp a = dsp(1, false), b = dsp(1, false);
        short[] warm = input(); a.process(warm,0,warm.length); a.reset();
        short[] x = input(), y=x.clone(); a.process(x,0,x.length); b.process(y,0,y.length);
        assertArrayEquals(y,x);
    }
    @Test public void liveAndKaraokeCanRunTogetherWithMono() throws Exception {
        Dsp a = dsp(1,true); a.setKaraoke(true,0.7f,VocalRemoverTest.model());
        short[] x=input(); a.process(x,0,x.length);
        for(int i=0;i<x.length;i+=2) assertEquals(x[i],x[i+1]);
    }
}
