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
        App(ComponentName c,String title) {component=c;label=title;}
        String id() {return component.flattenToString();}
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
}
