// SPDX-License-Identifier: GPL-3.0-or-later
package com.grapefruitlauncher;

import android.view.View;
import android.content.ClipData;
import android.content.Context;
import android.widget.FrameLayout;
import java.lang.ref.WeakReference;

/** In-process Android drag session; external drag payloads cannot modify the launcher layout. */
final class LauncherDrag {
    /** Keep the platform drag-service boundary separate from workspace/drop handling. */
    static final class Root extends FrameLayout {
        Root(Context context){super(context);}
        boolean startAppDrag(LauncherDrag session,View.DragShadowBuilder shadow){
            return startDrag(ClipData.newPlainText("Grapefruit app",session.app.id()),shadow,session,0);
        }
    }
    static final int HOME=0,DRAWER=1,DOCK=2;
    final AppCatalog.App app;
    final int origin,slot;
    final WeakReference<View> source;
    boolean accepted,options;
    LauncherDrag(AppCatalog.App app,int origin,int slot,View view){this.app=app;this.origin=origin;this.slot=slot;source=new WeakReference<>(view);}
}
