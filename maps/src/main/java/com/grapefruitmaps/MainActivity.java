// SPDX-License-Identifier: GPL-3.0-or-later
package com.grapefruitmaps;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.*;
import android.net.Uri;
import android.os.*;
import android.text.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import com.grapefruitmusic.ui.Fonts;
import org.json.*;
import org.mapsforge.core.model.LatLong;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    static final String[] THEMES={"Grapefruit Dark","Grapefruit Light","Pitch Black","Victory Blue"};
    Brand b;
    android.content.SharedPreferences prefs;
    LinearLayout root, tabs;
    FrameLayout content;
    MapPanel map;
    MapDownloads downloads;
    EggView egg;
    final EggState eggState=new EggState();
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private LocationManager locationManager;
    private LocationListener locationListener;
    private TextView downloadStatus;
    private BaseAdapter downloadAdapter;
    private String page="map", country;
    private boolean satellite, dead;
    private int request;
    private LatLong center=new LatLong(43.6532,-79.3832);
    private byte zoom=12;
    private JSONObject lastRoute;
    private String fromText="",toText="";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs=getSharedPreferences("maps",MODE_PRIVATE);
        Fonts.init(this);
        b=new Brand(this,prefs.getInt("theme",0));
        downloads=MapDownloads.get(this);
        downloads.listener=this::refreshDownloads;
        if(Build.VERSION.SDK_INT>=21) { getWindow().setStatusBarColor(b.bg); getWindow().setNavigationBarColor(b.bg); }
        center=new LatLong(Double.longBitsToDouble(prefs.getLong("lat",Double.doubleToLongBits(center.latitude))),Double.longBitsToDouble(prefs.getLong("lon",Double.doubleToLongBits(center.longitude))));
        zoom=(byte)prefs.getInt("zoom",12);
        page=state==null?"map":state.getString("page","map");
        // Easter egg state and drops are deliberately never restored.
        if("egg".equals(page)) page="about";
        country=state==null?null:state.getString("country");
        satellite=state!=null&&state.getBoolean("satellite",false);
        fromText=state==null?"":state.getString("from",""); toText=state==null?"":state.getString("to","");
        try { lastRoute=new JSONObject(prefs.getString("last_route","")); } catch(JSONException ignored) {}
        root=b.column(); root.setBackgroundColor(b.bg);
        content=new FrameLayout(this); root.addView(content,new LinearLayout.LayoutParams(-1,0,1));
        tabs=b.row(); tabs.setPadding(b.dp(6),b.dp(6),b.dp(6),b.dp(6)); root.addView(tabs,new LinearLayout.LayoutParams(-1,-2));
        // Respect system bars on Android 15/16 without using APIs absent from Jelly Bean.
        if(Build.VERSION.SDK_INT>=21) root.setOnApplyWindowInsetsListener((v,insets)->{
            root.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom()); return insets.consumeSystemWindowInsets();
        });
        setContentView(root); show(page);
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("page",page); state.putString("country",country); state.putString("from",fromText); state.putString("to",toText);
        state.putBoolean("satellite",satellite);
        super.onSaveInstanceState(state);
    }
    private void rememberMap() {
        if(map==null) return;
        center=map.view.getModel().mapViewPosition.getCenter(); zoom=map.view.getModel().mapViewPosition.getZoomLevel();
        prefs.edit().putLong("lat",Double.doubleToLongBits(center.latitude)).putLong("lon",Double.doubleToLongBits(center.longitude)).putInt("zoom",zoom).apply();
    }
    void show(String next) {
        request++;
        if(egg!=null) { egg.clear(); egg=null; }
        if(!next.equals("about")) eggState.reset();
        rememberMap();
        if(map!=null) { map.destroy(); map=null; }
        content.removeAllViews(); downloadStatus=null; downloadAdapter=null; page=next;
        tabs.removeAllViews();
        String[] labels={"Map","Directions","Offline","Settings"}, pages={"map","directions","offline","settings"};
        String selected=next.equals("about")||next.equals("egg")||next.equals("licenses")?"settings":next.equals("country")?"offline":next;
        for(int i=0;i<labels.length;i++) {
            final String target=pages[i]; TextView tab=b.button(labels[i],selected.equals(target),v->show(target));
            tab.setTextSize(12); tab.setPadding(b.dp(2),0,b.dp(2),0);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,b.dp(48),1); lp.setMargins(b.dp(3),0,b.dp(3),0); tabs.addView(tab,lp);
        }
        switch(next) {
            case "settings": settings(); break;
            case "about": about(); break;
            case "licenses": licenses(); break;
            case "egg": easterEgg(); break;
            case "offline": offline(null); break;
            case "country": offline(country); break;
            case "directions": directions(); break;
            case "search": search(); break;
            default: page="map"; mapPage();
        }
    }
    private LinearLayout column(String title,String subtitle) {
        LinearLayout col=b.column(); col.setPadding(b.dp(16),b.dp(12),b.dp(16),b.dp(16));
        TextView t=b.text(title,32,true); t.setPadding(0,b.dp(4),0,b.dp(8)); col.addView(t);
        if(subtitle!=null) { TextView sub=b.label(subtitle,13,b.secondary); sub.setPadding(0,0,0,b.dp(12)); col.addView(sub); }
        return col;
    }
    private LinearLayout scrollPage(String title,String subtitle,String back) {
        ScrollView s=new ScrollView(this); s.setFillViewport(true); s.setClipToPadding(false);
        LinearLayout outer=b.column(); s.addView(outer); content.addView(s);
        if(back!=null) { TextView button=b.button("‹ "+back,false,v->onBackPressed()); margin(outer,button,12,8); }
        LinearLayout col=column(title,subtitle); outer.addView(col); return col;
    }
    private void margin(LinearLayout parent,View v,int x,int y) {
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.setMargins(b.dp(x),b.dp(y),b.dp(x),b.dp(y)); parent.addView(v,lp);
    }
    private void section(LinearLayout col,String text) {
        TextView t=b.label(text,15,b.accent); t.setTypeface(Fonts.semiBold()); t.setPadding(b.dp(4),b.dp(22),0,b.dp(6)); col.addView(t);
    }
    private void note(LinearLayout col,String text) {
        TextView t=b.label(text,13,b.secondary); t.setPadding(b.dp(4),b.dp(8),b.dp(4),b.dp(4)); col.addView(t);
    }
    private LinearLayout row(LinearLayout col,String label,String value,View.OnClickListener click) {
        LinearLayout row=b.row(); row.setMinimumHeight(b.dp(56)); row.setPadding(b.dp(22),b.dp(12),b.dp(18),b.dp(12));
        row.setBackground(b.interactive(false,Brand.PILL));
        TextView left=b.text(label,16,false); row.addView(left,new LinearLayout.LayoutParams(0,-2,1));
        TextView right=b.label(value,14,b.secondary); right.setGravity(Gravity.END|Gravity.CENTER_VERTICAL); right.setPadding(b.dp(8),0,0,0); right.setMaxWidth(b.dp(180));
        row.addView(right); margin(col,row,0,4);
        if(click!=null) { row.setOnClickListener(click); row.setFocusable(true); }
        return row;
    }
    private EditText field(String hint,String value) {
        EditText e=new EditText(this); e.setTextSize(16); e.setTextColor(b.text); e.setHintTextColor(b.secondary); e.setTypeface(Fonts.regular());
        e.setSingleLine(true); e.setHint(hint); e.setText(value); e.setPadding(b.dp(18),b.dp(12),b.dp(18),b.dp(12)); e.setBackground(b.shape(b.control,24,true));
        return e;
    }
    interface Choice { void accept(int index); }
    void choose(String title,String[] choices,Choice chosen) {
        Dialog dialog=new Dialog(this); dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout body=column(title,null); body.setBackground(b.shape(b.surface,24,true));
        for(int i=0;i<choices.length;i++) { final int index=i; margin(body,b.button(choices[i],false,v->{dialog.dismiss();chosen.accept(index);}),0,4); }
        margin(body,b.button("Cancel",false,v->dialog.dismiss()),0,8);
        ScrollView scroll=new ScrollView(this); scroll.addView(body); dialog.setContentView(scroll);
        Window w=dialog.getWindow(); if(w!=null) { w.setBackgroundDrawableResource(android.R.color.transparent); w.setLayout(Math.min(getResources().getDisplayMetrics().widthPixels-b.dp(24),b.dp(460)),-2); }
        dialog.show(); if(w!=null) w.setLayout(Math.min(getResources().getDisplayMetrics().widthPixels-b.dp(24),b.dp(460)),-2);
    }
    void message(String title,String text) {
        Dialog dialog=new Dialog(this); dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout body=column(title,null); body.setBackground(b.shape(b.surface,24,true)); note(body,text);
        margin(body,b.button("OK",true,v->dialog.dismiss()),0,12); ScrollView s=new ScrollView(this); s.addView(body); dialog.setContentView(s); dialog.show();
        if(dialog.getWindow()!=null) { dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent); dialog.getWindow().setLayout(Math.min(getResources().getDisplayMetrics().widthPixels-b.dp(24),b.dp(460)),-2); }
    }
    private boolean offlineOnly() { return prefs.getBoolean("offline_only",false); }
    private void mapPage() {
        LinearLayout col=b.column(); content.addView(col);
        LinearLayout head=column("Maps",null); col.addView(head);
        LinearLayout tools=b.row();
        tools.addView(b.button("Search",false,v->show("search")),new LinearLayout.LayoutParams(0,b.dp(44),1));
        tools.addView(b.button(satellite?"Satellite ▾":"2D map ▾",true,v->choose("Map view",new String[]{"2D map","Satellite"},i->{
            if(i==1&&offlineOnly()) { message("Offline mode","Satellite imagery needs a connection. Your downloaded country maps work in 2D."); return; }
            satellite=i==1; show("map");
        })),new LinearLayout.LayoutParams(0,b.dp(44),1)); head.addView(tools);
        FrameLayout frame=new FrameLayout(this); col.addView(frame,new LinearLayout.LayoutParams(-1,0,1));
        File offline=null; String saved=prefs.getString("offline_file","");
        if(!satellite&&!saved.isEmpty()) { File candidate=new File(downloads.directory,saved); if(candidate.isFile()) offline=candidate; }
        if(offlineOnly() && (satellite||offline==null)) {
            LinearLayout empty=column("You're offline","Choose a downloaded 2D map to explore.");
            empty.addView(b.button("Downloaded maps",true,v->show("offline"))); frame.addView(empty); return;
        }
        try {
            map=new MapPanel(this,satellite,offline,center,zoom,b.accent,point->{
                map.mark(point,b.accent);
                choose("Dropped pin",new String[]{"Directions from here","Directions to here","Copy coordinates"},i->{
                    String value=coordinateText(point);
                    if(i==2) { ((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Coordinates",value)); }
                    else { if(i==0) fromText=value; else toText=value; show("directions"); }
                });
            });
            frame.addView(map.view);
            if(lastRoute!=null) map.route(lastRoute,b.accent);
        } catch(Exception e) { if(map!=null) { map.destroy(); map=null; } message("Map unavailable",String.valueOf(e.getMessage())); }
        LinearLayout controls=b.column();
        margin(controls,b.button("+",false,v->{if(map!=null)map.view.getModel().mapViewPosition.zoomIn();}),0,3);
        margin(controls,b.button("−",false,v->{if(map!=null)map.view.getModel().mapViewPosition.zoomOut();}),0,3);
        margin(controls,b.button("Locate",false,v->locate()),0,3);
        FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(b.dp(88),-2,Gravity.END|Gravity.TOP); cp.setMargins(0,b.dp(12),b.dp(12),0); frame.addView(controls,cp);
        TextView attribution=b.label(satellite?"NASA Blue Marble · satellite composite · regional detail":"© OpenStreetMap contributors · ODbL"+(offline!=null?" · offline":""),11,b.text);
        attribution.setBackground(b.shape(b.surface,12,true)); attribution.setPadding(b.dp(10),b.dp(8),b.dp(10),b.dp(8));
        attribution.setOnClickListener(v->show("about"));
        FrameLayout.LayoutParams ap=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM); ap.setMargins(b.dp(8),0,b.dp(8),b.dp(8)); frame.addView(attribution,ap);
        TextView hint=b.label(satellite?"Satellite imagery up to zoom 8. Switch to 2D for streets.":"Touch and hold the map to drop a pin for directions.",12,b.secondary);
        hint.setPadding(b.dp(16),b.dp(8),b.dp(16),b.dp(8)); col.addView(hint);
    }
    static String coordinateText(LatLong p) { return String.format(Locale.US,"%.6f, %.6f",p.latitude,p.longitude); }
    private void search() {
        LinearLayout col=scrollPage("Find a place","Search by name, address, or latitude, longitude.","Map");
        EditText query=field("Place or coordinates",""); col.addView(query);
        LinearLayout results=b.column(); TextView status=b.label("Search uses OpenStreetMap's Nominatim service.",13,b.secondary);
        margin(col,b.button("Search",true,v->{
            String q=query.getText().toString().trim(); if(q.isEmpty())return;
            LatLong coordinate=Net.coordinate(q);if(coordinate==null)coordinate=PlaceNames.find(q);
            if(coordinate!=null) { center=coordinate;zoom=(byte)(coordinate.equals(PlaceNames.GULF_POINT)?5:coordinate.equals(PlaceNames.LAKE_POINT)?8:15);show("map");if(map!=null)map.mark(coordinate,b.accent);return; }
            if(offlineOnly()) {message("Offline mode","Enter coordinates to find a point offline. Place search needs a connection.");return;}
            status.setText("Searching…"); results.removeAllViews(); final int ticket=request;
            worker.execute(()->{
                try {
                    JSONArray found=Net.search(q,prefs.getString("search_url",Net.SEARCH));
                    main.post(()->{ if(dead||ticket!=request)return; status.setText(found.length()==0?"No places found.":"Choose a result.");
                        for(int i=0;i<found.length();i++) try {
                            JSONObject item=found.getJSONObject(i); LatLong point=new LatLong(item.getDouble("lat"),item.getDouble("lon"));
                            margin(results,b.button(PlaceNames.canonicalResult(item),false,w->{center=point;zoom=15;show("map");if(map!=null)map.mark(point,b.accent);}),0,4);
                        } catch(JSONException ignored) {}
                    });
                } catch(Exception e) { main.post(()->{if(!dead&&ticket==request)status.setText("Search unavailable: "+e.getMessage());}); }
            });
        }),0,10); col.addView(status); col.addView(results);
    }
    private void directions() {
        LinearLayout col=scrollPage("Directions","Driving routes and a written route overview.",null);
        EditText from=field("From: place or lat, lon",fromText), to=field("To: place or lat, lon",toText);
        margin(col,from,0,4); margin(col,to,0,4);
        TextWatcher watcher=new TextWatcher() { public void beforeTextChanged(CharSequence s,int a,int count,int after){} public void onTextChanged(CharSequence s,int start,int before,int count){fromText=from.getText().toString();toText=to.getText().toString();} public void afterTextChanged(Editable e){} };
        from.addTextChangedListener(watcher);to.addTextChangedListener(watcher);
        TextView status=b.label("Routes use OSRM and OpenStreetMap. Locations are sent when you request directions.",13,b.secondary);
        margin(col,b.button("Get driving directions",true,v->{
            if(offlineOnly()) { message("Offline mode","New directions need a connection. Your last route remains available below and on the map.");return; }
            if(fromText.trim().isEmpty()||toText.trim().isEmpty()) {status.setText("Enter both a start and a destination.");return;}
            ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(to.getWindowToken(),0);
            final String origin=fromText.trim(),destination=toText.trim(); final int ticket=request; status.setText("Finding a driving route…"); v.setEnabled(false);
            worker.execute(()->{
                try {
                    String searchServer=prefs.getString("search_url",Net.SEARCH);
                    LatLong a=Net.resolve(origin,searchServer),z=Net.resolve(destination,searchServer);JSONObject route=Net.route(a,z,prefs.getString("route_url",Net.ROUTING));
                    main.post(()->{ if(dead||ticket!=request)return;lastRoute=route; prefs.edit().putString("last_route",route.toString()).apply();center=new LatLong((a.latitude+z.latitude)/2,(a.longitude+z.longitude)/2);
                        double span=Math.max(Math.abs(a.latitude-z.latitude),Math.abs(a.longitude-z.longitude)); zoom=(byte)Math.max(2,Math.min(15,Math.floor(Math.log(180/Math.max(.01,span))/Math.log(2))));show("directions"); });
                } catch(Exception e) {main.post(()->{if(!dead&&ticket==request){status.setText("Directions unavailable: "+e.getMessage());v.setEnabled(true);}});}
            });
        }),0,10);col.addView(status);
        note(col,"Beta 1 supports driving directions. Check road conditions and restrictions before travel. Turn-by-turn navigation is planned for a later version.");
        if(lastRoute!=null) {
            section(col,"Last route · saved on this device");
            row(col,String.format(Locale.US,"%.1f km",lastRoute.optDouble("distance")/1000),Math.round(lastRoute.optDouble("duration")/60)+" min",null);
            margin(col,b.button("Show route on map",true,v->show("map")),0,6);
            margin(col,b.button("Clear saved route",false,v->{lastRoute=null;prefs.edit().remove("last_route").apply();show("directions");}),0,6);
            try {
                JSONArray legs=lastRoute.getJSONArray("legs");
                int number=1;
                for(int l=0;l<legs.length();l++) {
                    JSONArray steps=legs.getJSONObject(l).getJSONArray("steps");
                    for(int i=0;i<steps.length();i++) {
                        JSONObject step=steps.getJSONObject(i), m=step.getJSONObject("maneuver");
                        String text=m.optString("type","continue").replace('_',' ')+" "+m.optString("modifier","");
                        text=Character.toUpperCase(text.charAt(0))+text.substring(1);
                        String road=step.optString("name",""); if(!road.isEmpty()) text+=" · "+road;
                        note(col,(number++)+". "+text+String.format(Locale.US," · %.0f m",step.optDouble("distance")));
                    }
                }
            } catch(JSONException e) {note(col,"Saved route details could not be read.");}
        }
    }
    private void settings() {
        LinearLayout col=scrollPage("Settings",null,null);
        section(col,"Appearance");
        row(col,"Theme",THEMES[prefs.getInt("theme",0)],v->choose("Theme",THEMES,i->{prefs.edit().putInt("theme",i).apply();recreate();}));
        section(col,"Maps");
        row(col,"Map source",prefs.getString("offline_file","").isEmpty()?"Online":"Downloaded map",v->choose("2D map source",new String[]{"Online OpenStreetMap","Downloaded maps"},i->{if(i==0){prefs.edit().remove("offline_file").apply();show("settings");}else show("offline");}));
        LinearLayout toggle=row(col,"Offline mode","",null);Toggle sw=new Toggle(this);sw.setOffColors(b.toggleOff,b.toggleOffSegments);sw.setChecked(offlineOnly());
        if(prefs.getInt("theme",0)==3)sw.setOnColors(b.accent,b.end,0xffBDDDF6);
        sw.setContentDescription("Offline mode");sw.setListener((t,on)->prefs.edit().putBoolean("offline_only",on).apply());toggle.addView(sw);toggle.setOnClickListener(v->sw.performClick());
        note(col,"Offline mode uses your selected country map and saved route. Search by coordinates works offline. New place searches, directions and satellite imagery need the internet.");
        row(col,"Offline maps","Countries ›",v->show("offline"));
        note(col,"Grapefruit uses Gulf of Mexico and Lake Ontario. Its own labels and offline water-body names preserve these names. Text baked into online raster tiles remains provider-controlled.");
        section(col,"Online services");
        row(col,"Place search","Nominatim ›",v->server("Place search","search_url",Net.SEARCH));
        row(col,"Driving routes","OSRM ›",v->server("Driving routes","route_url",Net.ROUTING));
        note(col,"You can use your own compatible HTTPS search and routing servers. Community services have usage limits.");
        section(col,"About");
        row(col,"About Grapefruit Maps","Beta 1 ›",v->show("about"));
        note(col,"Grapefruit Maps is free software. No ads, tracking SDKs, accounts or Google Play services.");
    }
    private void about() {
        LinearLayout col=scrollPage("Grapefruit Maps","Version 1.0 · beta 1","Settings");
        section(col,"License");row(col,"GNU GPL","Version 3 or later",v->open("https://www.gnu.org/licenses/gpl-3.0.html"));
        row(col,"Source code","GitHub ›",v->open("https://github.com/hayliewordsman/grapefruitmusic/tree/maps-beta-1/maps"));
        note(col,"Free software: you can share and change this app under the GNU General Public License, version 3 or later. It comes with no warranty.");
        section(col,"Developer");row(col,"Developer","Haylie",v->{if(eggState.tap(prefs.getInt("theme",0)))show("egg");});
        section(col,"Map data & services");
        row(col,"OpenStreetMap","ODbL 1.0 ›",v->open("https://www.openstreetmap.org/copyright"));
        row(col,"Country downloads","Mapsforge ›",v->open("https://download.mapsforge.org/"));
        row(col,"Satellite composite","NASA Blue Marble ›",v->open("https://earthobservatory.nasa.gov/features/BlueMarble"));
        note(col,"NASA Blue Marble satellite composite, served by NASA EOSDIS GIBS. Public-domain NASA imagery. Regional detail, up to zoom 8; this is a static composite, not live imagery. Satellite downloads are not included in beta 1.");
        row(col,"Place search","Nominatim ›",v->open("https://operations.osmfoundation.org/policies/nominatim/"));
        row(col,"Driving directions","OSRM ›",v->open("https://project-osrm.org/"));
        note(col,"Online map views send the visible tile area to OpenStreetMap or NASA. Search sends the submitted query to Nominatim; directions send endpoints to OSRM. Public services have usage limits and may be unavailable. No location is sent in the background.");
        section(col,"Open-source components");
        row(col,"Open-source licenses","Read ›",v->show("licenses"));
        note(col,"Mapsforge 0.21.0 · LGPL-3.0 with exception. AndroidSVG 1.4 · Apache-2.0. Conscrypt 2.5.3 · Apache-2.0, with bundled BoringSSL notices. Geist by Vercel · SIL OFL 1.1. Library licenses and source links ship in assets/licenses; the original game artwork is GPL-3.0-or-later.");
    }
    private void open(String url) { try {startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));} catch(ActivityNotFoundException e){message("No browser",url);} }
    private void server(String title,String key,String standard) {
        Dialog dialog=new Dialog(this);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout body=column(title,"Compatible HTTPS server address");body.setBackground(b.shape(b.surface,24,true));
        EditText address=field("https://",prefs.getString(key,standard));body.addView(address);TextView error=b.label("",13,b.secondary);body.addView(error);
        margin(body,b.button("Save",true,v->{String value=address.getText().toString().trim();while(value.endsWith("/"))value=value.substring(0,value.length()-1);
            try {java.net.URL url=new java.net.URL(value);if(!"https".equals(url.getProtocol())||url.getHost().isEmpty()||url.getQuery()!=null||url.getRef()!=null||url.getUserInfo()!=null)throw new IllegalArgumentException();prefs.edit().putString(key,value).apply();dialog.dismiss();}
            catch(Exception e){error.setText("Enter an HTTPS server address without query parameters or credentials.");}
        }),0,6);
        margin(body,b.button("Reset to default",false,v->{prefs.edit().remove(key).apply();dialog.dismiss();}),0,6);margin(body,b.button("Cancel",false,v->dialog.dismiss()),0,6);
        dialog.setContentView(body);dialog.show();if(dialog.getWindow()!=null){dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);dialog.getWindow().setLayout(Math.min(getResources().getDisplayMetrics().widthPixels-b.dp(24),b.dp(460)),-2);}
    }
    private void licenses() {
        LinearLayout col=scrollPage("Licenses","Free software and open data acknowledgements.","About");
        try {for(String file:getAssets().list("licenses"))row(col,file,"Read ›",v->{try(InputStream in=getAssets().open("licenses/"+file);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);message(file,out.toString("UTF-8"));}catch(IOException e){message("License unavailable",e.getMessage());}});}
        catch(IOException e){note(col,e.getMessage());}
    }
    private void easterEgg() {
        if(prefs.getInt("theme",0)!=3) {show("about");return;}
        LinearLayout col=scrollPage("Victory break","Tap the red cap. Make your mark.",null);
        margin(col,b.button("‹ About",false,v->show("about")),0,4);
        EggView.Hat hat=new EggView.Hat(this);col.addView(hat,new LinearLayout.LayoutParams(-1,b.dp(112)));
        egg=new EggView(this,b);col.addView(egg,new LinearLayout.LayoutParams(-1,b.dp(Math.min(280,getResources().getConfiguration().screenWidthDp*.65f))));
        TextView counter=b.label("0 drops",14,b.secondary);counter.setGravity(Gravity.CENTER);col.addView(counter);
        hat.setOnClickListener(v->{egg.drop();counter.setText(egg.count+" drops");});
        note(col,"Drops clear when you go back.");
    }
    private void offline(String selectedCountry) {
        LinearLayout col=column(selectedCountry==null?"Offline maps":selectedCountry,"Country maps from OpenStreetMap · Mapsforge");content.addView(col);
        if(selectedCountry!=null)margin(col,b.button("‹ Countries",false,v->show("offline")),0,4);
        EditText filter=field(selectedCountry==null?"Find a country or territory":"Find a region","");col.addView(filter);
        downloadStatus=b.label("",13,b.secondary);downloadStatus.setPadding(0,b.dp(10),0,b.dp(10));col.addView(downloadStatus);
        downloadStatus.setOnClickListener(v->{if(downloads.active!=null)choose("Download in progress",new String[]{"Pause download"},i->downloads.cancel());});
        ListView list=new ListView(this);list.setDivider(null);list.setCacheColorHint(Color.TRANSPARENT);col.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        ArrayList<String> countries=new ArrayList<>(new TreeSet<String>() {{for(MapDownloads.Entry e:downloads.entries)add(e.country);}});
        ArrayList<Object> visible=new ArrayList<>();
        BaseAdapter adapter=new BaseAdapter() {
            public int getCount(){return visible.size();} public Object getItem(int i){return visible.get(i);} public long getItemId(int i){return i;}
            public View getView(int i,View old,ViewGroup parent){
                Object item=visible.get(i); String title,sub;
                if(item instanceof String) {title=(String)item;int installed=0,total=0;for(MapDownloads.Entry e:downloads.entries)if(e.country.equals(title)){total++;if(downloads.file(e).isFile())installed++;}sub=installed>0?installed+" downloaded · "+total+" maps":total+" map"+(total==1?"":"s")+" ›";}
                else {MapDownloads.Entry e=(MapDownloads.Entry)item;title=e.region;sub=downloads.file(e).isFile()?"Downloaded · tap to open":e.path.equals(downloads.active)?"Downloading…":e.size+"B · download";}
                LinearLayout box=b.column();box.setPadding(b.dp(16),b.dp(12),b.dp(16),b.dp(12));box.setMinimumHeight(b.dp(66));box.setBackground(b.interactive(false,24));box.addView(b.text(title,16,true));TextView small=b.label(sub,13,b.secondary);small.setPadding(0,b.dp(4),0,0);box.addView(small);
                FrameLayout wrap=new FrameLayout(MainActivity.this);FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-1,-2);lp.setMargins(0,b.dp(3),0,b.dp(3));wrap.addView(box,lp);return wrap;
            }
        };
        Runnable fill=()->{visible.clear();String q=filter.getText().toString().trim().toLowerCase(Locale.ROOT);if(selectedCountry==null){for(String c:countries)if(c.toLowerCase(Locale.ROOT).contains(q))visible.add(c);}else for(MapDownloads.Entry e:downloads.entries)if(e.country.equals(selectedCountry)&&e.region.toLowerCase(Locale.ROOT).contains(q))visible.add(e);adapter.notifyDataSetChanged();};
        filter.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int after){}public void onTextChanged(CharSequence s,int a,int before,int count){fill.run();}public void afterTextChanged(Editable e){}});
        list.setAdapter(adapter);downloadAdapter=adapter;fill.run();refreshDownloads();
        list.setOnItemClickListener((parent,view,i,id)->{Object item=visible.get(i);if(item instanceof String){country=(String)item;show("country");}else mapAction((MapDownloads.Entry)item);});
        note(col,"Downloads contain 2D maps. Tap a downloaded map to open or remove it. Keep the app open during downloads; interrupted transfers can be resumed. Sizes are approximate.");
    }
    private void refreshDownloads() {
        if(downloadStatus!=null) downloadStatus.setText(downloads.active!=null?downloads.status+" · tap to pause":downloads.error!=null?downloads.error:downloads.status.isEmpty()?"Choose a country to see available downloads.":downloads.status);
        if(downloadAdapter!=null)downloadAdapter.notifyDataSetChanged();
    }
    private void mapAction(MapDownloads.Entry e) {
        File file=downloads.file(e);
        if(file.isFile()) choose(e.region,new String[]{"Open offline map","Remove download"},i->{
            if(i==0) {
                try {org.mapsforge.map.reader.MapFile data=new org.mapsforge.map.reader.MapFile(file);center=data.boundingBox().getCenterPoint();data.close();zoom=8;satellite=false;prefs.edit().putString("offline_file",file.getName()).apply();show("map");}
                catch(RuntimeException ex){message("Map unavailable","Remove this file and download it again. "+ex.getMessage());}
            } else {if(file.delete()){if(file.getName().equals(prefs.getString("offline_file","")))prefs.edit().remove("offline_file").apply();refreshDownloads();}else message("Cannot remove map","Storage could not be updated.");}
        });
        else if(e.path.equals(downloads.active))choose(e.region,new String[]{"Pause download"},i->downloads.cancel());
        else {
            File partial=new File(file+".part");
            choose(e.region+" · "+e.size+"B",partial.isFile()?new String[]{"Resume download","Discard partial download"}:new String[]{"Download country / region map"},i->{
                if(i==1){if(partial.delete()){new File(file+".http").delete();downloads.status="Partial download removed.";downloads.error=null;refreshDownloads();}else message("Cannot remove partial map","Storage could not be updated.");return;}
                if(offlineOnly()){message("Offline mode","Turn off Offline mode in Settings to download maps.");return;}
                if(!downloads.start(e))message("Download in progress","Pause or finish the current download first.");
            });
        }
    }
    private void locate() {
        if(Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED&&checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},10);return;
        }
        try {
            locationManager=(LocationManager)getSystemService(LOCATION_SERVICE);
            Location best=null;
            for(String provider:locationManager.getProviders(true)) {Location l=locationManager.getLastKnownLocation(provider);if(l!=null&&(best==null||l.getTime()>best.getTime()))best=l;}
            if(best!=null&&System.currentTimeMillis()-best.getTime()<15*60*1000){useLocation(best);return;}
            if(locationListener!=null)locationManager.removeUpdates(locationListener);
            String provider=locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)?LocationManager.GPS_PROVIDER:LocationManager.NETWORK_PROVIDER;
            if(!locationManager.isProviderEnabled(provider)){message("Location is off","Enable location in Android Settings, then tap Locate again.");return;}
            locationListener=new LocationListener(){public void onLocationChanged(Location l){useLocation(l);stopLocation();}public void onStatusChanged(String p,int status,Bundle e){}public void onProviderEnabled(String p){}public void onProviderDisabled(String p){}};
            locationManager.requestLocationUpdates(provider,1000,0,locationListener);main.postDelayed(this::stopLocation,30000);message("Finding your location","Waiting for a location fix, for up to 30 seconds.");
        } catch(SecurityException|IllegalArgumentException e){message("Location unavailable","Permission or a working location provider is needed. You can search for coordinates instead.");}
    }
    private void useLocation(Location l) {if(map!=null){LatLong p=new LatLong(l.getLatitude(),l.getLongitude());map.view.setCenter(p);map.mark(p,b.accent);}}
    private void stopLocation(){if(locationManager!=null&&locationListener!=null){try{locationManager.removeUpdates(locationListener);}catch(SecurityException ignored){}locationListener=null;}}
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grants){super.onRequestPermissionsResult(requestCode,permissions,grants);if(requestCode==10&&grants.length>0){for(int g:grants)if(g==PackageManager.PERMISSION_GRANTED){locate();return;}message("Location permission","You can still browse maps and search by place or coordinates.");}}
    @Override public void onBackPressed(){if(page.equals("egg")||page.equals("licenses"))show("about");else if(page.equals("about"))show("settings");else if(page.equals("country"))show("offline");else if(!page.equals("map"))show("map");else super.onBackPressed();}
    @Override protected void onResume(){super.onResume();if(map!=null)map.resume();}
    @Override protected void onPause(){rememberMap();stopLocation();if(map!=null)map.pause();super.onPause();}
    @Override protected void onDestroy(){dead=true;downloads.listener=null;stopLocation();worker.shutdownNow();main.removeCallbacksAndMessages(null);if(map!=null){map.destroy();map=null;}if(egg!=null)egg.clear();super.onDestroy();}
}
