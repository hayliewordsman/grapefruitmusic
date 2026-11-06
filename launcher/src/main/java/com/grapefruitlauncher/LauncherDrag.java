// SPDX-License-Identifier: GPL-3.0-or-later
package com.grapefruitlauncher;
import android.view.View;
import java.lang.ref.WeakReference;
/** In-window Launcher2 drag payload; never exported to Android's global drag service. */
final class LauncherDrag {
    static final int HOME=0,DRAWER=1,DOCK=2;
    final AppCatalog.App app;
    final int origin,slot;
    final WeakReference<View> source;
    boolean accepted,options;
    LauncherDrag(AppCatalog.App app,int origin,int slot,View source){this.app=app;this.origin=origin;this.slot=slot;this.source=new WeakReference<>(source);}
}
