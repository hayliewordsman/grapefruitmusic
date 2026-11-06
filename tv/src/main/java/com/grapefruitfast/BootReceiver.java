package com.grapefruitfast;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * After a restart, an app update or a change of the clock, sets the next recording's alarm again
 * (Android forgets alarms on a restart) and carries on any download. Nothing else runs at startup.
 */
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent intent) {
        final PendingResult done=goAsync();
        new Thread(()->{try{Recordings.wake(c.getApplicationContext());}finally{done.finish();}},"reschedule").start();
    }
}
