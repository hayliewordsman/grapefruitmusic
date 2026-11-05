// SPDX-License-Identifier: GPL-3.0-or-later
package com.grapefruitlauncher;

import android.view.View;
import android.view.DragEvent;
import android.content.ClipData;
import android.content.Context;
import android.widget.FrameLayout;
import java.lang.ref.WeakReference;

/** In-process Android drag session; external drag payloads cannot modify the launcher layout. */
final class LauncherDrag {
    /** Keep the platform drag-service boundary separate from workspace/drop handling. */
    static final class Root extends FrameLayout {
        interface Completion {void finished(LauncherDrag session);}
        private final Completion completion;
        Root(Context context,Completion completion){super(context);this.completion=completion;}
        boolean startAppDrag(LauncherDrag session,View.DragShadowBuilder shadow){
            return startDrag(ClipData.newPlainText("Grapefruit app",session.app.id()),shadow,session,0);
        }
        @Override public boolean dispatchDragEvent(DragEvent event){
            int action=event.getAction();Object state=event.getLocalState();
            boolean handled=super.dispatchDragEvent(event);
            // Observe terminal events even when a child consumes the drop. The activity
            // posts cleanup after dispatch, once that target has saved its changes.
            if((action==DragEvent.ACTION_DROP || action==DragEvent.ACTION_DRAG_ENDED) && state instanceof LauncherDrag)
                completion.finished((LauncherDrag)state);
            return handled;
        }
    }
    static final int HOME=0,DRAWER=1,DOCK=2;
    final AppCatalog.App app;
    final int origin,slot;
    final WeakReference<View> source;
    boolean accepted,options;
    LauncherDrag(AppCatalog.App app,int origin,int slot,View view){this.app=app;this.origin=origin;this.slot=slot;source=new WeakReference<>(view);}
}
