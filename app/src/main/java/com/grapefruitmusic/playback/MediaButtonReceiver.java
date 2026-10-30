package com.grapefruitmusic.playback;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;

/** Headset buttons and lock screen controls. */
public final class MediaButtonReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        KeyEvent ev = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
        if (ev == null || ev.getAction() != KeyEvent.ACTION_DOWN || ev.getRepeatCount() > 0) return;
        String action;
        switch (ev.getKeyCode()) {
            case KeyEvent.KEYCODE_HEADSETHOOK:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                action = PlaybackService.ACTION_TOGGLE;
                break;
            case KeyEvent.KEYCODE_MEDIA_PLAY:
                action = PlaybackService.ACTION_PLAY;
                break;
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                action = PlaybackService.ACTION_PAUSE;
                break;
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                action = PlaybackService.ACTION_NEXT;
                break;
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                action = PlaybackService.ACTION_PREVIOUS;
                break;
            case KeyEvent.KEYCODE_MEDIA_STOP:
                action = PlaybackService.ACTION_PAUSE;
                break;
            default:
                return;
        }
        com.grapefruitmusic.compat.Platform.service(context, new Intent(context, PlaybackService.class).setAction(action));
        if (isOrderedBroadcast()) abortBroadcast();
    }
}
