package com.grapefruitlauncher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

final class AppCatalog {
    static final class App {
        final ComponentName component;
        final String label;
        private final String id;
        App(ComponentName c,String title) {component=c;label=title;id=c.flattenToString();}
        String id() {return id;}
        Intent launch() {
            return new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(component)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        }
    }
    static List<App> load(Context context) {
        PackageManager pm=context.getPackageManager();
        Intent launcher=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        LinkedHashMap<String,App> unique=new LinkedHashMap<>();
        for(ResolveInfo r:pm.queryIntentActivities(launcher,0)) {
            ActivityInfo a=r.activityInfo;
            if(a==null || !a.enabled || !a.exported || (a.applicationInfo!=null && !a.applicationInfo.enabled))continue;
            ComponentName component=new ComponentName(a.packageName,a.name);
            CharSequence title;
            try {title=r.loadLabel(pm);} catch(RuntimeException e) {title=null;}
            App app=new App(component,title==null||title.length()==0?a.packageName:title.toString());
            unique.put(app.id(),app);
        }
        List<App> apps=new ArrayList<>(unique.values());
        Collator collator=Collator.getInstance();
        Collections.sort(apps,(a,b)->{int n=collator.compare(a.label,b.label);return n==0?a.id().compareTo(b.id()):n;});
        return apps;
    }
    static List<App> filter(List<App> apps,String query) {
        String needle=query.trim().toLowerCase(Locale.ROOT);
        if(needle.isEmpty())return new ArrayList<>(apps);
        List<App> result=new ArrayList<>();
        for(App a:apps) if(a.label.toLowerCase(Locale.ROOT).contains(needle)
                ||a.component.getPackageName().toLowerCase(Locale.ROOT).contains(needle)) result.add(a);
        return result;
    }
    static App find(List<App> apps,String id) {for(App a:apps)if(a.id().equals(id))return a;return null;}

    /** Phone, Messages, Chrome, Audio; a missing app deliberately leaves its own position blank. */
    static String[] suggestedDock(Context context,List<App> apps) {
        PackageManager pm=context.getPackageManager();
        return new String[]{handler(pm,apps,new Intent(Intent.ACTION_DIAL,android.net.Uri.parse("tel:"))),
                handler(pm,apps,new Intent(Intent.ACTION_SENDTO,android.net.Uri.parse("smsto:"))),
                packageId(apps,"com.android.chrome"),
                packageId(apps,"io.github.hayliewordsman.grapefruitmusic","com.grapefruitmusic")};
    }
    private static String handler(PackageManager pm,List<App> apps,Intent intent) {
        try {
            String chosen=launchable(apps,pm.resolveActivity(intent,PackageManager.MATCH_DEFAULT_ONLY));
            if(!chosen.isEmpty())return chosen;
            // An unset default can resolve to Android's chooser, which is not an app to pin.
            for(ResolveInfo r:pm.queryIntentActivities(intent,0)) {
                chosen=launchable(apps,r);if(!chosen.isEmpty())return chosen;
            }
        }catch(RuntimeException ignored) { /* A missing/disabled handler is an empty slot. */ }
        return "";
    }
    private static String launchable(List<App> apps,ResolveInfo resolved) {
        if(resolved==null || resolved.activityInfo==null || !resolved.activityInfo.enabled)return "";
        ActivityInfo a=resolved.activityInfo;
        App exact=find(apps,new ComponentName(a.packageName,a.name).flattenToString());
        return exact!=null?exact.id():packageId(apps,a.packageName);
    }
    private static String packageId(List<App> apps,String... packages) {
        for(String pkg:packages)for(App app:apps)if(pkg.equals(app.component.getPackageName()))return app.id();
        return "";
    }
}
