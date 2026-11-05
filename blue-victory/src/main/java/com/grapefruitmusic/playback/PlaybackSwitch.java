package com.grapefruitmusic.playback;

/** Blue's optional video player registers here. Standard Grapefruit Audio has no video dependency. */
public final class PlaybackSwitch {
    private PlaybackSwitch() { }
    public interface MediaKeys { boolean handle(int key); }
    public static volatile MediaKeys videoKeys;
    public static volatile Runnable pauseVideo;
    public static void audioRequested(){Runnable stop=pauseVideo;if(stop!=null)stop.run();}
}
