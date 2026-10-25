package com.grapefruitmusic.playback;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.RemoteControlClient;
import android.os.IBinder;

import com.grapefruitmusic.R;
import com.grapefruitmusic.model.MediaItem;
import com.grapefruitmusic.ui.MainActivity;
import com.grapefruitmusic.util.ArtworkLoader;

/** Keeps music playing in the background: notification, lock screen and headset controls. */
public final class PlaybackService extends Service implements Player.Listener {
    public static final String ACTION_TOGGLE = "com.grapefruitmusic.action.TOGGLE";
    public static final String ACTION_PLAY = "com.grapefruitmusic.action.PLAY";
    public static final String ACTION_PAUSE = "com.grapefruitmusic.action.PAUSE";
    public static final String ACTION_NEXT = "com.grapefruitmusic.action.NEXT";
    public static final String ACTION_PREVIOUS = "com.grapefruitmusic.action.PREVIOUS";
    public static final String ACTION_STOP = "com.grapefruitmusic.action.STOP";

    private static final int NOTIFICATION_ID = 1;
    private static final int ART_SIZE = 256;

    private Player player;
    private AudioManager audio;
    private NotificationManager notifications;
    private ComponentName mediaButtons;
    private RemoteControlClient remote;
    private boolean foreground;
    private String metadataFor;
    private String artFor;
    private Bitmap art;

    private final BroadcastReceiver noisy = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Headphones unplugged.
            player.pause();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        player = Player.get(this);
        audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        notifications = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        registerReceiver(noisy, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));

        mediaButtons = new ComponentName(this, MediaButtonReceiver.class);
        audio.registerMediaButtonEventReceiver(mediaButtons);
        Intent buttons = new Intent(Intent.ACTION_MEDIA_BUTTON).setComponent(mediaButtons);
        remote = new RemoteControlClient(PendingIntent.getBroadcast(this, 0, buttons, 0));
        remote.setTransportControlFlags(RemoteControlClient.FLAG_KEY_MEDIA_PLAY_PAUSE
                | RemoteControlClient.FLAG_KEY_MEDIA_PLAY | RemoteControlClient.FLAG_KEY_MEDIA_PAUSE
                | RemoteControlClient.FLAG_KEY_MEDIA_NEXT | RemoteControlClient.FLAG_KEY_MEDIA_PREVIOUS);
        audio.registerRemoteControlClient(remote);

        player.addListener(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_TOGGLE.equals(action)) player.togglePlay();
        else if (ACTION_PLAY.equals(action)) player.resume();
        else if (ACTION_PAUSE.equals(action)) player.pause();
        else if (ACTION_NEXT.equals(action)) player.next();
        else if (ACTION_PREVIOUS.equals(action)) player.previous();
        else if (ACTION_STOP.equals(action)) player.stop();
        update();
        return START_NOT_STICKY;
    }

    @Override
    public void onPlayerChanged() {
        update();
    }

    private void update() {
        final MediaItem item = player.current();
        if (item == null) {
            stopForeground(true);
            foreground = false;
            notifications.cancel(NOTIFICATION_ID);
            remote.setPlaybackState(RemoteControlClient.PLAYSTATE_STOPPED);
            stopSelf();
            return;
        }
        if (!item.key().equals(artFor)) {
            artFor = item.key();
            art = ArtworkLoader.get(this).cached(item, ART_SIZE);
            if (art == null) {
                ArtworkLoader.get(this).load(item, ART_SIZE, new ArtworkLoader.Callback() {
                    @Override
                    public void onArtwork(Bitmap bitmap) {
                        if (item.key().equals(artFor) && bitmap != null) {
                            art = bitmap;
                            metadataFor = null;
                            update();
                        }
                    }
                });
            }
        }

        boolean playing = player.wantsToPlay();
        Notification n = buildNotification(item, playing);
        if (playing) {
            startForeground(NOTIFICATION_ID, n);
            foreground = true;
        } else {
            if (foreground) stopForeground(false);
            foreground = false;
            notifications.notify(NOTIFICATION_ID, n);
        }

        remote.setPlaybackState(player.isBuffering() ? RemoteControlClient.PLAYSTATE_BUFFERING
                : playing ? RemoteControlClient.PLAYSTATE_PLAYING : RemoteControlClient.PLAYSTATE_PAUSED);
        if (!item.key().equals(metadataFor)) {
            metadataFor = item.key();
            RemoteControlClient.MetadataEditor ed = remote.editMetadata(true);
            ed.putString(MediaMetadataRetriever.METADATA_KEY_TITLE, item.title);
            ed.putString(MediaMetadataRetriever.METADATA_KEY_ARTIST, item.artist);
            ed.putString(MediaMetadataRetriever.METADATA_KEY_ALBUM, item.album);
            ed.putLong(MediaMetadataRetriever.METADATA_KEY_DURATION, item.durationMs);
            if (art != null) {
                // The lock screen recycles bitmaps it's given, so hand it a copy.
                Bitmap copy = art.copy(Bitmap.Config.ARGB_8888, false);
                if (copy != null) ed.putBitmap(RemoteControlClient.MetadataEditor.BITMAP_KEY_ARTWORK, copy);
            }
            ed.apply();
        }
    }

    private PendingIntent action(String action, int requestCode) {
        Intent i = new Intent(this, PlaybackService.class).setAction(action);
        return PendingIntent.getService(this, requestCode, i, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private Notification buildNotification(MediaItem item, boolean playing) {
        Intent open = new Intent(this, MainActivity.class)
                .putExtra(MainActivity.EXTRA_OPEN_NOW_PLAYING, true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        String text = item.artist == null ? "" : item.artist;
        if (item.album != null) text = text.length() == 0 ? item.album : text + " — " + item.album;
        Notification.Builder b = new Notification.Builder(this)
                .setSmallIcon(R.drawable.ic_stat_grapefruit)
                .setContentTitle(item.title)
                .setContentText(text)
                .setLargeIcon(art)
                .setOngoing(playing)
                .setShowWhen(false)
                .setPriority(Notification.PRIORITY_DEFAULT)
                .setContentIntent(PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT))
                .setDeleteIntent(action(ACTION_STOP, 5))
                .addAction(android.R.drawable.ic_media_previous, "", action(ACTION_PREVIOUS, 1))
                .addAction(playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play, "",
                        action(ACTION_TOGGLE, 2))
                .addAction(android.R.drawable.ic_media_next, "", action(ACTION_NEXT, 3));
        return b.build();
    }

    @Override
    public void onDestroy() {
        player.removeListener(this);
        unregisterReceiver(noisy);
        audio.unregisterRemoteControlClient(remote);
        audio.unregisterMediaButtonEventReceiver(mediaButtons);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
