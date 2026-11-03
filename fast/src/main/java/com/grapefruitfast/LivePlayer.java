package com.grapefruitfast;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.view.SurfaceView;
import com.google.android.exoplayer2.*;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.upstream.*;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.util.MimeTypes;

/** ExoPlayer 2.18.7 supports Jelly Bean. Its Java HTTPS data source uses our Conscrypt TLS. */
public final class LivePlayer {
    public interface Listener {
        void status(String message); void format(String message); void aspect(float ratio);
        /** Whether the stream offers captions (closed captions in the video, or subtitle tracks). */
        void captionsAvailable(boolean available);
        /** The captions to show now ("" for none). */
        void captions(CharSequence text);
    }
    private final Context context; private final Listener listener; private final Handler handler=new Handler();
    private final DefaultTrackSelector selector;
    private final DefaultBandwidthMeter bandwidth;
    private ExoPlayer player;
    private Channel channel; private int sourceIndex, retries, generation; private boolean released;
    private SurfaceView surface; private int quality; private boolean captionsOn; private String captionLanguage="en";
    public LivePlayer(Context c,Listener l,int quality) {
        context=c;listener=l;this.quality=quality;
        bandwidth=new DefaultBandwidthMeter.Builder(c).setInitialBitrateEstimate(800_000).build();
        selector=new DefaultTrackSelector(c); configure();
    }
    private void configure() {
        DefaultTrackSelector.Parameters.Builder p=selector.buildUponParameters()
            .setPreferredVideoMimeTypes(MimeTypes.VIDEO_H264)
            .setPreferredAudioMimeTypes(MimeTypes.AUDIO_AAC)
            .setExceedVideoConstraintsIfNecessary(false);
        if(quality==2) p.setMaxVideoSize(640,360).setMaxVideoBitrate(800_000);
        else if(quality==1) p.setMaxVideoSize(854,480).setMaxVideoBitrate(1_400_000);
        else p.setMaxVideoSize(Build.VERSION.SDK_INT<21?1280:3840,Build.VERSION.SDK_INT<21?720:2160)
            .setMaxVideoBitrate(Integer.MAX_VALUE);
        if(Build.VERSION.SDK_INT<21) p.setMaxVideoFrameRate(30);
        // Captions: the viewer's language first, then any (live captions are often undetermined); off: no text track.
        p.setTrackTypeDisabled(C.TRACK_TYPE_TEXT,!captionsOn)
            .setPreferredTextLanguage(captionLanguage).setSelectUndeterminedTextLanguage(true)
            .setIgnoredTextSelectionFlags(0);
        selector.setParameters(p);
    }
    public void setQuality(int quality) { this.quality=quality;configure(); }
    /** Captions on or off, in the viewer's language where the stream has a choice. */
    public void setCaptions(boolean on,String language) {
        captionsOn=on;if(language!=null&&!language.isEmpty())captionLanguage=language;configure();
        if(!on)listener.captions("");
    }
    public void attach(SurfaceView view) {surface=view;if(player!=null) {player.setVideoSurfaceView(view);listener.status(player.getPlaybackState()==Player.STATE_READY?"LIVE":"Connecting…");report();}}
    public void tune(Channel c) {
        channel=c;sourceIndex=0;retries=0;generation++;handler.removeCallbacksAndMessages(null);
        if(player!=null) player.release();
        player=new ExoPlayer.Builder(context).setTrackSelector(selector).setBandwidthMeter(bandwidth)
            .setLoadControl(new DefaultLoadControl.Builder().setBufferDurationsMs(8000,25000,1500,3000)
                .setTargetBufferBytes(12*1024*1024).setPrioritizeTimeOverSizeThresholds(false).build()).build();
        player.setAudioAttributes(new com.google.android.exoplayer2.audio.AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),true);
        player.setHandleAudioBecomingNoisy(true);
        player.setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT);
        player.setVideoSurfaceView(surface);
        player.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int state) {
                if(state==Player.STATE_READY) { retries=0;listener.status("LIVE");report(); }
                else if(state==Player.STATE_BUFFERING) listener.status("Connecting…");
                else if(state==Player.STATE_ENDED) failed();
            }
            @Override public void onPlayerError(PlaybackException error) {failed();}
            @Override public void onTracksChanged(Tracks tracks) { listener.captionsAvailable(tracks.containsType(C.TRACK_TYPE_TEXT)); }
            @Override public void onCues(com.google.android.exoplayer2.text.CueGroup cues) { listener.captions(captionsOn?joined(cues.cues):""); }
            @Override public void onVideoSizeChanged(com.google.android.exoplayer2.video.VideoSize size) { if(size.height>0)listener.aspect(size.width*size.pixelWidthHeightRatio/size.height);report(); }
        });
        prepare();
    }
    /** The cues on screen now as lines of text (live captions are text; positions are left to the overlay). */
    static CharSequence joined(java.util.List<com.google.android.exoplayer2.text.Cue> cues) {
        android.text.SpannableStringBuilder out=new android.text.SpannableStringBuilder();
        for(com.google.android.exoplayer2.text.Cue cue:cues) {
            if(cue.text==null||cue.text.toString().trim().isEmpty())continue;
            if(out.length()>0)out.append('\n');
            out.append(cue.text);
        }
        return out;
    }
    private void report() {
        if(player==null) return;
        Format f=player.getVideoFormat();
        if(f!=null&&f.width>0&&f.height>0)listener.aspect(f.width*f.pixelWidthHeightRatio/f.height);
        if(player.getPlaybackState()==Player.STATE_READY&&!player.getCurrentTracks().isTypeSelected(C.TRACK_TYPE_VIDEO))
            listener.status("No compatible video at this quality. Try Auto or another channel.");
        String mode=quality==0?"Auto":quality==1?"Data saver":"Compatibility";
        listener.format(f==null?mode:mode+" · "+f.height+"p"+(f.bitrate>0?" · "+String.format(java.util.Locale.US,"%.1f Mbps",f.bitrate/1000000f):""));
    }
    private void prepare() {
        if(released||player==null) return;
        listener.status("Connecting…");
        DefaultHttpDataSource.Factory http=new DefaultHttpDataSource.Factory()
            .setUserAgent("GrapefruitFAST/1.0-beta-10 (Android)")
            .setConnectTimeoutMs(15000).setReadTimeoutMs(20000).setAllowCrossProtocolRedirects(false);
        player.setMediaSource(new HlsMediaSource.Factory(new DefaultDataSource.Factory(context,http))
            .createMediaSource(MediaItem.fromUri(channel.streams[sourceIndex])));
        player.prepare();player.setPlayWhenReady(true);
    }
    private void failed() {
        if(released) return;
        // Each source gets two bounded reconnects; stale requests cannot restart another channel.
        if(retries<2) {
            retries++;listener.status("Reconnecting… ("+retries+"/2)");
        } else if(sourceIndex+1<channel.streams.length) { sourceIndex++;retries=0; }
        else {
            listener.status("This stream is unavailable here. Try Retry or Official site.");return;
        }
        handler.removeCallbacksAndMessages(null);
        int token=generation;
        handler.postDelayed(()->{if(!released&&token==generation) prepare();},2000L*(retries+1));
    }
    public void release() {
        released=true;generation++;handler.removeCallbacksAndMessages(null);
        if(player!=null) {player.release();player=null;}
    }
}
