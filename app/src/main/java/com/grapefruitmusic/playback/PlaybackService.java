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
import android.graphics.Canvas;
import android.graphics.RectF;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.RemoteControlClient;
import android.os.Build;
import android.os.IBinder;
import android.util.SparseArray;
import android.widget.RemoteViews;

import com.grapefruitmusic.R;
import com.grapefruitmusic.model.MediaItem;
import com.grapefruitmusic.radio.Radio;
import com.grapefruitmusic.radio.RadioArt;
import com.grapefruitmusic.radio.StationText;
import com.grapefruitmusic.ui.AppIcon;
import com.grapefruitmusic.ui.MainActivity;
import com.grapefruitmusic.ui.widget.Icons;
import com.grapefruitmusic.util.ArtworkLoader;
import com.grapefruitmusic.util.Bg;

/**
 * Keeps music and the radio playing in the background and with the screen off: the notification
 * (with an X to stop and close Grapefruit), the lock screen and headset controls.
 */
public final class PlaybackService extends Service implements Player.Listener, Radio.Listener {
    public static final String ACTION_TOGGLE = "com.grapefruitmusic.action.TOGGLE";
    public static final String ACTION_PLAY = "com.grapefruitmusic.action.PLAY";
    public static final String ACTION_PAUSE = "com.grapefruitmusic.action.PAUSE";
    public static final String ACTION_NEXT = "com.grapefruitmusic.action.NEXT";
    public static final String ACTION_PREVIOUS = "com.grapefruitmusic.action.PREVIOUS";
    public static final String ACTION_STOP = "com.grapefruitmusic.action.STOP";
    /** The notification's X: stop, close Grapefruit and clear the notification. */
    public static final String ACTION_CLOSE = "com.grapefruitmusic.action.CLOSE";

    private static final int NOTIFICATION_ID = 1;
    private static final int ART_SIZE = 256;

    private Player player;
    private com.grapefruitmusic.compat.ModernSession modern;
    private Radio radio;
    private AudioManager audio;
    private NotificationManager notifications;
    private ComponentName mediaButtons;
    private RemoteControlClient remote;
    private boolean foreground;
    private String metadataFor;
    private String artFor;
    private Bitmap art;
    /** The artwork as the notification shows it: the whole picture, scaled to fit. */
    private Bitmap fittedArt;
    private Bitmap fittedFrom;
    private final SparseArray<Bitmap> buttons = new SparseArray<Bitmap>();

    private final BroadcastReceiver noisy = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Headphones unplugged (the radio stops itself: they're its antenna).
            player.pause();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        player = Player.get(this);
        radio = Radio.get(this);
        if (Build.VERSION.SDK_INT >= 21) modern = new com.grapefruitmusic.compat.ModernSession(this, player, radio);
        audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        notifications = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= 33)
            registerReceiver(noisy, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(noisy, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));

        mediaButtons = new ComponentName(this, MediaButtonReceiver.class);
        audio.registerMediaButtonEventReceiver(mediaButtons);
        Intent buttonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON).setComponent(mediaButtons);
        remote = new RemoteControlClient(PendingIntent.getBroadcast(this, 0, buttonIntent, com.grapefruitmusic.compat.Platform.pendingFlags(0)));
        remote.setTransportControlFlags(RemoteControlClient.FLAG_KEY_MEDIA_PLAY_PAUSE
                | RemoteControlClient.FLAG_KEY_MEDIA_PLAY | RemoteControlClient.FLAG_KEY_MEDIA_PAUSE
                | RemoteControlClient.FLAG_KEY_MEDIA_NEXT | RemoteControlClient.FLAG_KEY_MEDIA_PREVIOUS);
        audio.registerRemoteControlClient(remote);

        player.addListener(this);
        radio.addListener(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        foreground = true;
        startForeground(1, com.grapefruitmusic.compat.Platform.notification(this, "playback", "Playback")
                .setSmallIcon(R.drawable.ic_stat_grapefruit).setContentTitle("Grapefruit Music").build());
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_CLOSE.equals(action)) {
            close();
            return START_NOT_STICKY;
        }
        if (radio.inSession()) {
            // The same buttons work the radio: next and previous find the next station.
            if (ACTION_TOGGLE.equals(action)) radio.togglePlay();
            else if (ACTION_PLAY.equals(action)) radio.play();
            else if (ACTION_PAUSE.equals(action)) radio.stop();
            else if (ACTION_NEXT.equals(action)) radio.seek(true);
            else if (ACTION_PREVIOUS.equals(action)) radio.seek(false);
            else if (ACTION_STOP.equals(action)) radio.close();
        } else {
            if (ACTION_TOGGLE.equals(action)) player.togglePlay();
            else if (ACTION_PLAY.equals(action)) player.resume();
            else if (ACTION_PAUSE.equals(action)) player.pause();
            else if (ACTION_NEXT.equals(action)) player.next();
            else if (ACTION_PREVIOUS.equals(action)) player.previous();
            else if (ACTION_STOP.equals(action)) player.stop();
        }
        update();
        return START_NOT_STICKY;
    }

    /** The X: stops the music and the radio, closes Grapefruit and clears the notification. */
    private void close() {
        radio.close();
        player.stop();
        MainActivity.closeIfOpen();
        stopForeground(true);
        foreground = false;
        notifications.cancel(NOTIFICATION_ID);
        remote.setPlaybackState(RemoteControlClient.PLAYSTATE_STOPPED);
        stopSelf();
    }

    @Override
    public void onPlayerChanged() {
        update();
    }

    /** Radio changes come thick and fast while the dial turns: the notification follows a moment later. */
    private final Runnable radioUpdate = new Runnable() {
        @Override
        public void run() {
            update();
        }
    };

    @Override
    public void onRadioChanged() {
        Bg.cancelMain(radioUpdate);
        Bg.mainDelayed(radioUpdate, 250);
    }

    private void update() {
        boolean onRadio = radio.inSession();
        final MediaItem item = onRadio ? null : player.current();
        if (!onRadio && item == null) {
            stopForeground(true);
            foreground = false;
            notifications.cancel(NOTIFICATION_ID);
            remote.setPlaybackState(RemoteControlClient.PLAYSTATE_STOPPED);
            stopSelf();
            return;
        }
        String title;
        String text;
        String text2;
        String metadataKey;
        boolean playing;
        if (onRadio) {
            art = RadioArt.of(radio.frequency(), radio.name(), ART_SIZE);
            artFor = null;
            title = radio.title();
            text = radio.subtitle();
            String freq = Radio.format(radio.frequency()) + " FM";
            text2 = text.equals(freq) ? "FM Radio" : freq;
            playing = radio.wantsToPlay();
            metadataKey = "radio|" + title + "|" + text;
        } else if (item.live) {
            loadArt(item);
            String[] lines = StationText.nowPlaying(item.title, item.artist, player.liveTitle());
            title = lines[0];
            text = lines[1];
            text2 = "Internet Radio";
            playing = player.wantsToPlay();
            metadataKey = item.key() + "|" + title + "|" + text;
        } else {
            loadArt(item);
            title = item.title;
            text = item.artist != null ? item.artist : "";
            text2 = item.album != null ? item.album : "";
            playing = player.wantsToPlay();
            metadataKey = item.key();
        }

        // A live station stops rather than pauses, and steps between stations, like FM.
        if (modern != null) modern.update(title, text, text2, playing);
        Notification n = buildNotification(title, text, text2, playing, onRadio || item.live);
        if (playing) {
            startForeground(NOTIFICATION_ID, n);
            foreground = true;
        } else {
            if (foreground) stopForeground(false);
            foreground = false;
            notifications.notify(NOTIFICATION_ID, n);
        }

        remote.setPlaybackState(!onRadio && player.isBuffering() ? RemoteControlClient.PLAYSTATE_BUFFERING
                : playing ? RemoteControlClient.PLAYSTATE_PLAYING : RemoteControlClient.PLAYSTATE_PAUSED);
        if (!metadataKey.equals(metadataFor)) {
            metadataFor = metadataKey;
            RemoteControlClient.MetadataEditor ed = remote.editMetadata(true);
            ed.putString(MediaMetadataRetriever.METADATA_KEY_TITLE, title);
            boolean live = onRadio || item.live;
            ed.putString(MediaMetadataRetriever.METADATA_KEY_ARTIST, live ? text : item.artist);
            ed.putString(MediaMetadataRetriever.METADATA_KEY_ALBUM, live ? text2 : item.album);
            if (!live) ed.putLong(MediaMetadataRetriever.METADATA_KEY_DURATION, item.durationMs);
            if (art != null) {
                // The lock screen recycles bitmaps it's given, so hand it a copy.
                Bitmap copy = art.copy(Bitmap.Config.ARGB_8888, false);
                if (copy != null) ed.putBitmap(RemoteControlClient.MetadataEditor.BITMAP_KEY_ARTWORK, copy);
            }
            ed.apply();
        }
    }

    private void loadArt(final MediaItem item) {
        if (item.key().equals(artFor)) return;
        artFor = item.key();
        art = ArtworkLoader.get(this).cached(item, ART_SIZE);
        if (art != null) return;
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

    private PendingIntent action(String action, int requestCode) {
        Intent i = new Intent(this, PlaybackService.class).setAction(action);
        return PendingIntent.getService(this, requestCode, i, com.grapefruitmusic.compat.Platform.pendingFlags(PendingIntent.FLAG_UPDATE_CURRENT));
    }

    private Notification buildNotification(String title, String text, String text2, boolean playing, boolean onRadio) {
        Intent open = new Intent(this, MainActivity.class)
                .putExtra(MainActivity.EXTRA_OPEN_NOW_PLAYING, true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        String line = text;
        if (text2.length() > 0 && !onRadio) line = line.length() == 0 ? text2 : line + " — " + text2;
        RemoteViews small = new RemoteViews(getPackageName(), R.layout.notification_player);
        RemoteViews big = new RemoteViews(getPackageName(), R.layout.notification_player_big);
        fill(small, title, line, playing, onRadio);
        fill(big, title, text, playing, onRadio);
        big.setTextViewText(R.id.n_text2, text2);
        Notification.Builder b = com.grapefruitmusic.compat.Platform.notification(this, "playback", "Playback")
                .setSmallIcon(R.drawable.ic_stat_grapefruit)
                .setContentTitle(title)
                .setContentText(line)
                .setContent(small)
                .setOngoing(playing)
                .setShowWhen(false)
                .setPriority(Notification.PRIORITY_DEFAULT)
                .setContentIntent(PendingIntent.getActivity(this, 0, open, com.grapefruitmusic.compat.Platform.pendingFlags(PendingIntent.FLAG_UPDATE_CURRENT)))
                .setDeleteIntent(action(ACTION_STOP, 5));
        if (modern != null) modern.style(b);
        Notification n = b.build();
        n.bigContentView = big;
        return n;
    }

    private void fill(RemoteViews v, String title, String text, boolean playing, boolean onRadio) {
        v.setTextViewText(R.id.n_title, title);
        v.setTextViewText(R.id.n_text, text);
        Bitmap fitted = fittedArt();
        if (fitted != null) v.setImageViewBitmap(R.id.n_art, fitted);
        else v.setImageViewResource(R.id.n_art, AppIcon.launcher(this));
        v.setImageViewBitmap(R.id.n_prev, button(Icons.PREVIOUS));
        v.setImageViewBitmap(R.id.n_play, button(playing ? (onRadio ? Icons.STOP : Icons.PAUSE) : Icons.PLAY));
        v.setImageViewBitmap(R.id.n_next, button(Icons.NEXT));
        v.setImageViewBitmap(R.id.n_close, button(Icons.CLOSE));
        v.setOnClickPendingIntent(R.id.n_prev, action(ACTION_PREVIOUS, 1));
        v.setOnClickPendingIntent(R.id.n_play, action(ACTION_TOGGLE, 2));
        v.setOnClickPendingIntent(R.id.n_next, action(ACTION_NEXT, 3));
        v.setOnClickPendingIntent(R.id.n_close, action(ACTION_CLOSE, 6));
        v.setContentDescription(R.id.n_prev, onRadio ? "Previous station" : "Previous");
        v.setContentDescription(R.id.n_play, playing ? (onRadio ? "Stop" : "Pause") : "Play");
        v.setContentDescription(R.id.n_next, onRadio ? "Next station" : "Next");
        v.setContentDescription(R.id.n_close, "Stop and close Grapefruit");
    }

    /**
     * The artwork at the unfolded notification's size, the whole picture: Android 4 shows a
     * notification's large icon unscaled and cropped to its middle, so it's scaled here instead.
     */
    private Bitmap fittedArt() {
        if (art == null) return null;
        if (art == fittedFrom && fittedArt != null) return fittedArt;
        fittedFrom = art;
        fittedArt = fit(art, Math.round(128 * getResources().getDisplayMetrics().density));
        return fittedArt;
    }

    /** bitmap scaled down so its longer side is at most size, keeping its shape (all of it shows). */
    public static Bitmap fit(Bitmap bitmap, int size) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        float scale = Math.min(1f, size / (float) Math.max(w, h));
        if (scale >= 1f) return bitmap;
        return Bitmap.createScaledBitmap(bitmap, Math.max(1, Math.round(w * scale)), Math.max(1, Math.round(h * scale)),
                true);
    }

    /** A button's icon in Grapefruit's own style, light on Android 4's dark notifications. */
    private Bitmap button(int icon) {
        Bitmap b = buttons.get(icon);
        if (b != null) return b;
        int size = Math.round(32 * getResources().getDisplayMetrics().density);
        b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        int color = Build.VERSION.SDK_INT >= 21 ? 0xff5f6368 : 0xffffffff;
        Icons.draw(new Canvas(b), icon, new RectF(0, 0, size, size), color);
        buttons.put(icon, b);
        return b;
    }

    @Override
    public void onDestroy() {
        if (modern != null) modern.close();
        Bg.cancelMain(radioUpdate);
        player.removeListener(this);
        radio.removeListener(this);
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
