// SPDX-License-Identifier: Apache-2.0
package com.android.systemui.grapefruit;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

/** Android 4.2.2 TakeScreenshotService Messenger protocol; no framebuffer or shell access. */
final class ScreenshotRequest implements ServiceConnection {
    private static ScreenshotRequest active;
    private final Context context;
    private final Handler handler=new Handler();
    private boolean bound;
    private final Runnable timeout=new Runnable(){public void run(){finish();}};
    private ScreenshotRequest(Context c){context=c.getApplicationContext();}
    static void take(Context c){
        if(active!=null)return;
        ScreenshotRequest request=new ScreenshotRequest(c);active=request;
        Intent intent=new Intent().setComponent(new ComponentName("com.android.systemui","com.android.systemui.screenshot.TakeScreenshotService"));
        try {
            request.bound=request.context.bindService(intent,request,Context.BIND_AUTO_CREATE);
            if(request.bound)request.handler.postDelayed(request.timeout,10000);else request.finish();
        }catch(RuntimeException failure){Log.w("GrapefruitScreenshot","Service unavailable",failure);request.finish();}
    }
    public void onServiceConnected(ComponentName name,IBinder service){
        Message message=Message.obtain(null,1);message.arg1=1;message.arg2=1;
        message.replyTo=new Messenger(new Handler(){@Override public void handleMessage(Message reply){finish();}});
        try{new Messenger(service).send(message);}catch(RemoteException failure){finish();}
    }
    public void onServiceDisconnected(ComponentName name){finish();}
    private void finish(){handler.removeCallbacks(timeout);if(bound){bound=false;context.unbindService(this);}if(active==this)active=null;}
}
