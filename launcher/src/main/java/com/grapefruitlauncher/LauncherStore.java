package com.grapefruitlauncher;

import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Component names, not labels or package names: apps can expose more than one launchable activity. */
final class LauncherStore {
    static final int DOCK_APPS = 4; // The fifth position, in the middle, is always All Apps.
    private final SharedPreferences prefs;
    LauncherStore(SharedPreferences p) { prefs=p; }
    int theme() { return prefs.getInt("theme",0); }
    void theme(int id) { prefs.edit().putInt("theme",id).apply(); }
    List<String> home() {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (String id : prefs.getString("home", "").split("\n")) if (!id.isEmpty()) ids.add(id);
        return new ArrayList<>(ids);
    }
    void seed(List<AppCatalog.App> apps) {
        if (prefs.getBoolean("seeded", false) || apps.isEmpty()) return;
        if (!home().isEmpty()) { prefs.edit().putBoolean("seeded",true).apply(); return; }
        List<String> home = new ArrayList<>();
        // Keep the two companion apps near the start when installed.
        for (AppCatalog.App a:apps) if (a.component.getPackageName().startsWith("io.github.hayliewordsman.")
                && !a.component.getPackageName().endsWith("grapefruitlauncher")) home.add(a.id());
        for (AppCatalog.App a:apps) if (home.size()<12 && !home.contains(a.id())) home.add(a.id());
        writeHome(home);
        SharedPreferences.Editor dock=prefs.edit();
        for(int i=0;i<Math.min(DOCK_APPS,home.size());i++)dock.putString("dock_"+i,home.get(i));
        dock.apply();
        prefs.edit().putBoolean("seeded",true).apply();
    }
    void add(String id) { List<String> ids=home(); if (!ids.contains(id)) {ids.add(id);writeHome(ids);} }
    void remove(String id) { List<String> ids=home(); ids.remove(id);writeHome(ids); }
    void move(String id, int delta) {
        List<String> ids=home(); int from=ids.indexOf(id), to=from+delta;
        if (from>=0 && to>=0 && to<ids.size()) {ids.remove(from);ids.add(to,id);writeHome(ids);}
    }
    private void writeHome(List<String> ids) {
        StringBuilder out=new StringBuilder();
        for (String id:ids) {if(out.length()>0)out.append('\n');out.append(id);}
        prefs.edit().putString("home",out.toString()).apply();
    }
    String dock(int slot) { checkSlot(slot); return prefs.getString("dock_"+slot,""); }
    void dock(int slot,String id) {
        checkSlot(slot);
        SharedPreferences.Editor e=prefs.edit();
        // Moving to another dock slot frees the previous one rather than duplicating it.
        for(int i=0;i<DOCK_APPS;i++) if(!id.isEmpty() && id.equals(dock(i)))e.remove("dock_"+i);
        e.putString("dock_"+slot,id).apply();
    }
    private void checkSlot(int slot) {
        if(slot<0 || slot>=DOCK_APPS)throw new IllegalArgumentException("Dock app slot must be 0–3");
    }
}
