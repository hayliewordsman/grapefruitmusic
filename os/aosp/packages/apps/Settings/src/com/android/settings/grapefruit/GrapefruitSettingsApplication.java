// SPDX-License-Identifier: Apache-2.0
package com.android.settings.grapefruit;

import android.app.Activity;
import android.app.Application;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.ViewTreeObserver;
import org.grapefruit.platform.GrapefruitStyle;
import org.grapefruit.platform.Palette;
import org.grapefruit.platform.ThemeContract;
import java.util.WeakHashMap;

/** Includes legacy standalone Settings activities; native preference and service logic is retained. */
public final class GrapefruitSettingsApplication extends Application implements Application.ActivityLifecycleCallbacks {
    private final WeakHashMap<Activity,Boolean> activities = new WeakHashMap<Activity,Boolean>();
    private Palette palette;
    @Override public void onCreate() {
        super.onCreate(); palette=new Palette(ThemeContract.read(this));registerActivityLifecycleCallbacks(this);
        getContentResolver().registerContentObserver(ThemeContract.URI,false,new ContentObserver(new Handler()) {
            @Override public void onChange(boolean self) {
                palette=new Palette(ThemeContract.read(GrapefruitSettingsApplication.this));
                for(Activity a:activities.keySet()) {
                    View content=a.findViewById(android.R.id.content);
                    if(content!=null){content.setBackgroundColor(palette.background);GrapefruitStyle.apply(content,palette);}
                }
            }
        });
    }
    public void onActivityCreated(final Activity a,Bundle state) {
        activities.put(a,Boolean.TRUE);
        a.getWindow().getDecorView().getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            public void onGlobalLayout() {
                View content=a.findViewById(android.R.id.content);
                if(content!=null){content.setBackgroundColor(palette.background);GrapefruitStyle.apply(content,palette);}
            }
        });
    }
    public void onActivityResumed(Activity a) { View content=a.findViewById(android.R.id.content); if(content!=null)GrapefruitStyle.apply(content,palette); }
    public void onActivityDestroyed(Activity a) { activities.remove(a); }
    public void onActivityStarted(Activity a) {}
    public void onActivityPaused(Activity a) {}
    public void onActivityStopped(Activity a) {}
    public void onActivitySaveInstanceState(Activity a,Bundle b) {}
}
