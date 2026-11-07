package com.grapefruitlauncher;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Android 4.2/4.3 always use the small budget; newer versions also honor heap class and low-RAM flags. */
final class MemoryPolicy {
    final boolean lowRam;
    final int cacheBytes;
    MemoryPolicy(Context context) {
        ActivityManager am=(ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
        lowRam=Build.VERSION.SDK_INT<19 || am==null || am.getMemoryClass()<=64
                || (Build.VERSION.SDK_INT>=19 && am.isLowRamDevice());
        cacheBytes=lowRam?256*1024:1024*1024;
    }
    int iconPixels(LauncherTheme theme) {return Math.max(1,Math.min(theme.dp(58),lowRam?96:144));}
    static ThreadPoolExecutor worker(String name) {
        // At most one queued drain/query; no permanent thread stack after two idle seconds.
        return new ThreadPoolExecutor(0,1,2,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(1),r->
                new Thread(()->{android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);r.run();},name));
    }
}
