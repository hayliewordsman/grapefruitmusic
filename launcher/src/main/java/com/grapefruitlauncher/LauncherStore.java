// SPDX-License-Identifier: GPL-3.0-or-later
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
    boolean subgrid(){return prefs.getBoolean("subgrid",false);}
    void subgrid(boolean value){prefs.edit().putBoolean("subgrid",value).apply();}
    java.util.Map<String,HomeLayout.Cell> positions(String profile,List<String> ids) {
        java.util.Map<String,HomeLayout.Cell> result=new java.util.LinkedHashMap<>();
        for(String id:ids) {
            String raw=prefs.getString("position_"+profile+"_"+id,"");String[] parts=raw.split(",");
            try{if(parts.length==2)result.put(id,new HomeLayout.Cell(Integer.parseInt(parts[0]),Integer.parseInt(parts[1])));}catch(NumberFormatException ignored){}
        }
        return result;
    }
    void positions(String profile,java.util.Map<String,HomeLayout.Cell> changed) {
        SharedPreferences.Editor e=prefs.edit();
        for(java.util.Map.Entry<String,HomeLayout.Cell> entry:changed.entrySet()) {
            HomeLayout.Cell cell=entry.getValue();
            if(cell.x>=0 && cell.y>=0 && cell.y<=HomeLayout.MAX_Y)e.putString("position_"+profile+"_"+entry.getKey(),cell.x+","+cell.y);
        }
        e.apply();
    }
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
        prefs.edit().putBoolean("seeded",true).apply();
    }
    void add(String id) { List<String> ids=home(); if (!ids.contains(id)) {ids.add(id);writeHome(ids);} }
    void remove(String id) {
        List<String> ids=home();ids.remove(id);writeHome(ids);
        prefs.edit().remove("position_portrait_"+id).remove("position_landscape_"+id).apply();
    }
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
        for(int i=0;i<DOCK_APPS;i++) if(i!=slot && !id.isEmpty() && id.equals(dock(i)))
            e.remove("dock_"+i).putBoolean("dock_custom_"+i,true);
        e.putString("dock_"+slot,id).putBoolean("dock_custom_"+slot,true).apply();
    }
    void suggestedDock(String[] defaults) {
        if(defaults.length!=DOCK_APPS)throw new IllegalArgumentException("Four default dock slots required");
        boolean migrate=prefs.getInt("dock_defaults_version",0)<2;
        SharedPreferences.Editor e=prefs.edit();
        for(int i=0;i<DOCK_APPS;i++) {
            if(migrate || !prefs.getBoolean("dock_custom_"+i,false))e.putString("dock_"+i,defaults[i]);
            if(migrate)e.putBoolean("dock_custom_"+i,false);
        }
        e.putInt("dock_defaults_version",2).apply();
    }
    void resetDock(String[] defaults) {
        prefs.edit().remove("dock_defaults_version").apply();suggestedDock(defaults);
    }
    void swapDock(int from,int to) {
        checkSlot(from);checkSlot(to);String first=dock(from),second=dock(to);
        prefs.edit().putString("dock_"+from,second).putString("dock_"+to,first)
                .putBoolean("dock_custom_"+from,true).putBoolean("dock_custom_"+to,true).apply();
    }
    private void checkSlot(int slot) {
        if(slot<0 || slot>=DOCK_APPS)throw new IllegalArgumentException("Dock app slot must be 0–3");
    }
}
