package com.grapefruitmusic.compat;

import android.annotation.TargetApi;
import android.app.Notification;
import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;

import com.grapefruitmusic.playback.Player;
import com.grapefruitmusic.radio.Radio;

/** Modern lock-screen and headset controls, loaded only on API 21+. */
@TargetApi(21)
public final class ModernSession {
    private final MediaSession session;
    private final Player player;
    private final Radio radio;
    private final Context context;

    private void command(String action) {
        Platform.service(
                context,
                new android.content.Intent(
                                context, com.grapefruitmusic.playback.PlaybackService.class)
                        .setAction(action));
    }

    public ModernSession(Context c, Player p, Radio r) {
        context = c.getApplicationContext();
        player = p;
        radio = r;
        session = new MediaSession(c, "GrapefruitMusic");
        session.setCallback(
                new MediaSession.Callback() {
                    @Override
                    public void onPlay() {
                        command(com.grapefruitmusic.playback.PlaybackService.ACTION_PLAY);
                    }

                    @Override
                    public void onPause() {
                        command(com.grapefruitmusic.playback.PlaybackService.ACTION_PAUSE);
                    }

                    @Override
                    public void onStop() {
                        command(com.grapefruitmusic.playback.PlaybackService.ACTION_STOP);
                    }

                    @Override
                    public void onSkipToNext() {
                        command(com.grapefruitmusic.playback.PlaybackService.ACTION_NEXT);
                    }

                    @Override
                    public void onSkipToPrevious() {
                        command(com.grapefruitmusic.playback.PlaybackService.ACTION_PREVIOUS);
                    }

                    @Override
                    public void onSeekTo(long p) {
                        player.seekTo(p);
                    }
                });
        session.setFlags(
                MediaSession.FLAG_HANDLES_MEDIA_BUTTONS
                        | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setActive(true);
    }

    public void update(String title, String artist, String album, boolean playing) {
        session.setMetadata(
                new MediaMetadata.Builder()
                        .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                        .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
                        .putString(MediaMetadata.METADATA_KEY_ALBUM, album)
                        .putLong(MediaMetadata.METADATA_KEY_DURATION, player.durationMs())
                        .build());
        long actions =
                PlaybackState.ACTION_PLAY
                        | PlaybackState.ACTION_PAUSE
                        | PlaybackState.ACTION_STOP
                        | PlaybackState.ACTION_SKIP_TO_NEXT
                        | PlaybackState.ACTION_SKIP_TO_PREVIOUS;
        if (!radio.inSession() && !player.isLive()) actions |= PlaybackState.ACTION_SEEK_TO;
        session.setPlaybackState(
                new PlaybackState.Builder()
                        .setActions(actions)
                        .setState(
                                playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED,
                                player.positionMs(),
                                playing ? 1 : 0)
                        .build());
    }

    public void style(Notification.Builder b) {
        b.setStyle(new Notification.MediaStyle().setMediaSession(session.getSessionToken()));
    }

    public void close() {
        session.release();
    }
}
