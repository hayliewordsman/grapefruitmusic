package com.grapefruitsettings;

import android.app.Activity;
import android.content.*;
import android.widget.Toast;

/** All privileged operations remain in the JP5S's original, platform-signed frontend. */
final class StockSettings {
    private StockSettings() {}
    static Intent intent(String action) { return new Intent(action).setPackage("com.android.settings"); }
    static void original(Activity a) { a.startActivity(intent("android.settings.SETTINGS")); }
    static void open(Activity a,String action) {
        // The assembled JP5S APK contains the original frontend Activities under its own package.
        // The standalone Gradle preview has no OEM classes and falls back to stock Settings.
        try {
            Intent route=new Intent(a.getPackageName()+".route."+action).setPackage(a.getPackageName());
            android.content.pm.ResolveInfo resolved=a.getPackageManager().resolveActivity(route,0);
            if(resolved!=null){
                route.setClassName(a.getPackageName(),resolved.activityInfo.name).setAction(action);
                a.startActivity(route);return;
            }
        }
        catch(ActivityNotFoundException|SecurityException preview) {}
        try { a.startActivity(intent(action)); }
        catch(ActivityNotFoundException|SecurityException unavailable) {
            try { a.startActivity(new Intent().setClassName("com.android.settings","com.android.settings.Settings")); }
            catch(ActivityNotFoundException|SecurityException missing) { Toast.makeText(a,"Stock Settings is unavailable on this device.",Toast.LENGTH_LONG).show(); }
        }
    }
    static void hdmi(Activity a) {
        Intent i=new Intent().setClassName(a.getPackageName(),"com.android.settings.Settings")
                .putExtra(":android:show_fragment","com.android.settings.HdmiControllerActivity");
        try { a.startActivity(i); }
        catch(ActivityNotFoundException|SecurityException e) { open(a,"android.settings.DISPLAY_SETTINGS"); }
    }
}
