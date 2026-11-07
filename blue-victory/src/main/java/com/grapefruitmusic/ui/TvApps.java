package com.grapefruitmusic.ui;
import com.grapefruitmusic.R;
import android.content.*;
import android.content.pm.*;
import android.graphics.drawable.Drawable;
import android.os.Build;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Your TV Apps: the streaming and TV-provider apps installed on this device, opened with a tap. Blue Grapefruit
 * only launches them; it never signs in to them or plays their streams (those stay in their own apps,
 * which hold the sign-ins and DRM). Apps are recognised by name, since their package IDs vary by country
 * and device; the viewer can pin any other app, or take one off the row.
 */
public final class TvApps {
    /** Names of live TV, streaming and TV-provider apps (any app whose name matches is suggested). */
    static final Pattern KNOWN=Pattern.compile(
        "\\b(crave|citytv|river ?tv|sling|hulu|paramount|peacock|fubo|philo|youtube tv|directv|dish|xfinity|"
        +"spectrum tv|fibe tv|bell fibe|ignite tv|rogers|shaw|stream ?tv|freedom tv|vmedia|telus|optik|"
        +"cbc gem|ctv|global tv|stackt?v|tou\\.tv|tva\\+|noovo|tsn|sportsnet|dazn|espn|max|netflix|"
        +"disney\\+|prime video|apple tv|pluto|tubi|plex|roku channel|samsung tv plus|lg channels|xumo|"
        +"bbc iplayer|itvx|channel 4|my5|7plus|9now|10 ?play|sbs on demand|abc iview|binge|kayo|foxtel)\\b",
        Pattern.CASE_INSENSITIVE);

    public static final class App {
        public final String pkg, label; public final Drawable icon;
        App(String pkg,String label,Drawable icon){this.pkg=pkg;this.label=label;this.icon=icon;}
    }
    private TvApps() {}

    static boolean suggested(String label) { return label!=null&&KNOWN.matcher(label).find(); }

    /** Every launchable app here (phone and TV launchers), by name, without Blue Grapefruit itself. */
    public static List<App> installed(Context c) {
        PackageManager pm=c.getPackageManager();
        Map<String,App> apps=new TreeMap<>();
        List<String> categories=new ArrayList<>(Collections.singletonList(Intent.CATEGORY_LAUNCHER));
        if(Build.VERSION.SDK_INT>=21) categories.add(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for(String category:categories) {
            Intent main=new Intent(Intent.ACTION_MAIN).addCategory(category);
            for(ResolveInfo r:pm.queryIntentActivities(main,0)) {
                String pkg=r.activityInfo.packageName;
                if(pkg.equals(c.getPackageName())||apps.containsKey(pkg))continue;
                CharSequence label=r.loadLabel(pm);
                apps.put(pkg,new App(pkg,label==null?pkg:label.toString(),r.loadIcon(pm)));
            }
        }
        List<App> out=new ArrayList<>(apps.values());
        Collections.sort(out,(a,b)->a.label.compareToIgnoreCase(b.label));
        return out;
    }

    /** The row: suggested apps the viewer hasn't removed, and the ones they pinned. */
    public static List<App> row(Context c,SharedPreferences prefs) {
        Set<String> pinned=prefs.getStringSet("tvAppsPinned",Collections.<String>emptySet());
        Set<String> removed=prefs.getStringSet("tvAppsRemoved",Collections.<String>emptySet());
        List<App> out=new ArrayList<>();
        for(App a:installed(c)) if(pinned.contains(a.pkg)||(suggested(a.label)&&!removed.contains(a.pkg))) out.add(a);
        return out;
    }
    public static void pin(SharedPreferences prefs,String pkg) {
        Set<String> pinned=new HashSet<>(prefs.getStringSet("tvAppsPinned",Collections.<String>emptySet()));pinned.add(pkg);
        Set<String> removed=new HashSet<>(prefs.getStringSet("tvAppsRemoved",Collections.<String>emptySet()));removed.remove(pkg);
        prefs.edit().putStringSet("tvAppsPinned",pinned).putStringSet("tvAppsRemoved",removed).apply();
    }
    public static void remove(SharedPreferences prefs,String pkg) {
        Set<String> pinned=new HashSet<>(prefs.getStringSet("tvAppsPinned",Collections.<String>emptySet()));pinned.remove(pkg);
        Set<String> removed=new HashSet<>(prefs.getStringSet("tvAppsRemoved",Collections.<String>emptySet()));removed.add(pkg);
        prefs.edit().putStringSet("tvAppsPinned",pinned).putStringSet("tvAppsRemoved",removed).apply();
    }
    /** Opens the app: its TV launcher on a TV, else its phone launcher. False if it can't be opened. */
    public static boolean open(Context c,String pkg) {
        PackageManager pm=c.getPackageManager();
        Intent i=null;
        if(Build.VERSION.SDK_INT>=21&&pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) i=pm.getLeanbackLaunchIntentForPackage(pkg);
        if(i==null) i=pm.getLaunchIntentForPackage(pkg);
        if(i==null) return false;
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try{c.startActivity(i);return true;}catch(ActivityNotFoundException|SecurityException e){return false;}
    }
}
