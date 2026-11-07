package com.grapefruitmusic.ui;
import com.grapefruitmusic.R;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * After a restart, an app update or a change of the clock, sets the next recording's alarm again
 * (Android forgets alarms on a restart) and carries on any download. Nothing else runs at startup.
 */
public final class TvBootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent intent) {
        final PendingResult done=goAsync();
        new Thread(()->{try{TvRecordings.wake(c.getApplicationContext());}finally{done.finish();}},"reschedule").start();
    }
}
