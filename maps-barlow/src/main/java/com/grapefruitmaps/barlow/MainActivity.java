// SPDX-License-Identifier: GPL-3.0-or-later
package com.grapefruitmaps.barlow;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.*;
import android.net.Uri;
import android.os.*;
import android.text.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import com.grapefruitmusic.ui.Fonts;
import com.grapefruitmusic.ui.UniversalTheme;
import org.json.*;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.core.model.BoundingBox;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    static final String[] THEMES={"Grapefruit Dark","Grapefruit Light","Pitch Black","Victory Blue"};
    static final String NAME="Grapefruit Maps: Barlow Edition";
    private static final int[] UNIVERSAL_THEME_IDS={0,1,2,3};
    private static final String[] TAB_LABELS={"Map","Street View","Directions","Settings"}, TAB_PAGES={"map","street","directions","settings"};
    private static final Icon.Kind[] TAB_ICONS={Icon.Kind.MAP,Icon.Kind.STREET,Icon.Kind.DIRECTIONS,Icon.Kind.SETTINGS};
    Brand b;
    android.content.SharedPreferences prefs;
    LinearLayout root, tabs;
    FrameLayout content;
    MapPanel map;
    EggView egg;
    /** True when the window is wider than tall: icon rail at the side and compact headers. */
    boolean landscape;
    final EggState eggState=new EggState();
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private LocationManager locationManager;
    private LocationListener locationListener;
    private TextView attribution;
    private String page="map";
    private int mapType;
    private boolean dead;
    private int request;
    private LatLong center=new LatLong(43.6532,-79.3832), streetPoint;
    private byte zoom=12;
    private int heading, pitch;
    private JSONObject lastRoute;
    private String fromText="",toText="";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs=getSharedPreferences("maps",MODE_PRIVATE);
        UniversalTheme.sync(this,prefs,false,UNIVERSAL_THEME_IDS);
        Fonts.init(this);
        b=new Brand(this,prefs.getInt("theme",0));
        if(Build.VERSION.SDK_INT>=21) { getWindow().setStatusBarColor(b.bg); getWindow().setNavigationBarColor(b.bg); }
        center=new LatLong(Double.longBitsToDouble(prefs.getLong("lat",Double.doubleToLongBits(center.latitude))),Double.longBitsToDouble(prefs.getLong("lon",Double.doubleToLongBits(center.longitude))));
        zoom=(byte)prefs.getInt("zoom",12);
        mapType=Math.max(0,Math.min(2,prefs.getInt("map_type",0)));
        page=state==null?"map":state.getString("page","map");
        // Easter egg state and drops are deliberately never restored.
        if("egg".equals(page)) page="about";
        fromText=state==null?"":state.getString("from",""); toText=state==null?"":state.getString("to","");
        heading=state==null?0:state.getInt("heading",0);
        if(state!=null&&state.containsKey("street_lat")) streetPoint=new LatLong(state.getDouble("street_lat"),state.getDouble("street_lon"));
        try { lastRoute=new JSONObject(prefs.getString("last_route","")); } catch(JSONException ignored) {}
        landscape=getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE;
        content=new FrameLayout(this);
        if(landscape) {
            // Landscape: the tabs become a rail of drawn icons down the left side, giving the map full height.
            root=b.row(); root.setGravity(Gravity.NO_GRAVITY); root.setBackgroundColor(b.bg);
            tabs=b.column(); tabs.setGravity(Gravity.CENTER_HORIZONTAL); tabs.setPadding(b.dp(6),b.dp(6),b.dp(6),b.dp(6));
            root.addView(tabs,new LinearLayout.LayoutParams(b.dp(80),-1));
            root.addView(content,new LinearLayout.LayoutParams(0,-1,1));
        } else {
            root=b.column(); root.setBackgroundColor(b.bg);
            root.addView(content,new LinearLayout.LayoutParams(-1,0,1));
            tabs=b.row(); tabs.setPadding(b.dp(6),b.dp(6),b.dp(6),b.dp(6)); root.addView(tabs,new LinearLayout.LayoutParams(-1,-2));
        }
        // Respect system bars on Android 15/16 without using APIs absent from Jelly Bean.
        if(Build.VERSION.SDK_INT>=21) root.setOnApplyWindowInsetsListener((v,insets)->{
            root.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom()); return insets.consumeSystemWindowInsets();
        });
        setContentView(root); show(page);
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("page",page); state.putString("from",fromText); state.putString("to",toText); state.putInt("heading",heading);
        if(streetPoint!=null) { state.putDouble("street_lat",streetPoint.latitude); state.putDouble("street_lon",streetPoint.longitude); }
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
        content.removeAllViews(); attribution=null; page=next;
        tabs.removeAllViews();
        String selected=next.equals("about")||next.equals("egg")||next.equals("licenses")?"settings":next.equals("search")?"map":next;
        for(int i=0;i<TAB_LABELS.length;i++) {
            final String target=TAB_PAGES[i]; boolean on=selected.equals(target);
            if(landscape) {
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,0,1); lp.setMargins(0,b.dp(3),0,b.dp(3));
                tabs.addView(railTab(TAB_LABELS[i],TAB_ICONS[i],on,v->show(target)),lp);
            } else {
                TextView tab=b.button(TAB_LABELS[i],on,v->show(target));
                tab.setTextSize(12); tab.setPadding(b.dp(2),0,b.dp(2),0);
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,b.dp(48),1); lp.setMargins(b.dp(3),0,b.dp(3),0); tabs.addView(tab,lp);
            }
        }
        switch(next) {
            case "settings": settings(); break;
            case "about": about(); break;
            case "licenses": licenses(); break;
            case "egg": easterEgg(); break;
            case "street": street(); break;
            case "directions": directions(); break;
            case "search": search(); break;
            default: page="map"; mapPage();
        }
    }
    /** A rail tab: drawn icon over a small label, the selected one on the accent gradient. */
    private View railTab(String label,Icon.Kind kind,boolean selected,View.OnClickListener click) {
        LinearLayout tab=b.column(); tab.setGravity(Gravity.CENTER); tab.setBackground(b.interactive(selected,20,0,false));
        tab.setFocusable(true); tab.setOnClickListener(click); tab.setContentDescription(label); tab.setMinimumHeight(b.dp(44));
        ImageView icon=new ImageView(this); icon.setImageDrawable(new Icon(kind,selected?b.onAccent:b.text,b.dp(22)));
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        tab.addView(icon,new LinearLayout.LayoutParams(b.dp(22),b.dp(22)));
        TextView text=b.label(label,10,selected?b.onAccent:b.secondary); text.setGravity(Gravity.CENTER); text.setPadding(0,b.dp(3),0,0); text.setMaxLines(1);
        text.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        tab.addView(text,new LinearLayout.LayoutParams(-2,-2));
        return tab;
    }
    /** A round, drawn-icon control for the sides of the map and Street View. */
    ImageButton iconButton(Icon.Kind kind,String description,View.OnClickListener click) {
        ImageButton button=new ImageButton(this); button.setBackground(b.interactive(false,Brand.PILL));
        button.setImageDrawable(new Icon(kind,b.text,b.dp(22))); button.setScaleType(ImageView.ScaleType.CENTER);
        button.setContentDescription(description); button.setFocusable(true); button.setOnClickListener(click);
        b.lift(button,3);
        return button;
    }
    private LinearLayout column(String title,String subtitle) {
        return column(title,subtitle,null);
    }
    /** Every page with a back action uses the same icon, spacing and title row; landscape uses a compact one. */
    private LinearLayout column(String title,String subtitle,String back) {
        int size=landscape?22:32, button=landscape?40:48;
        LinearLayout col=b.column();col.setPadding(b.dp(16),b.dp(landscape?6:12),b.dp(16),b.dp(landscape?8:16));
        if(back!=null) {
            LinearLayout header=b.row();
            ImageButton backButton=b.back(back,v->onBackPressed());
            header.addView(backButton,new LinearLayout.LayoutParams(b.dp(button),b.dp(button)));
            TextView heading=b.text(title,size,true);heading.setMaxLines(landscape?1:2);heading.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams titleParams=new LinearLayout.LayoutParams(0,-2,1);titleParams.leftMargin=b.dp(12);
            header.addView(heading,titleParams);col.addView(header);
            if(subtitle!=null) { TextView sub=b.label(subtitle,13,b.secondary);sub.setPadding(b.dp(button+12),b.dp(landscape?4:8),0,b.dp(landscape?6:12));col.addView(sub); }
        } else {
            TextView heading=b.text(title,size,true);heading.setPadding(0,b.dp(landscape?2:4),0,b.dp(landscape?4:8));col.addView(heading);
            if(subtitle!=null) { TextView sub=b.label(subtitle,13,b.secondary);sub.setPadding(0,0,0,b.dp(landscape?6:12));col.addView(sub); }
        }
        return col;
    }
    private LinearLayout scrollPage(String title,String subtitle,String back) {
        ScrollView s=new ScrollView(this);s.setFillViewport(true);s.setClipToPadding(false);
        LinearLayout outer=b.column();s.addView(outer);content.addView(s);
        LinearLayout col=column(title,subtitle,back);outer.addView(col);return col;
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
    private void notConfigured(FrameLayout frame) {
        LinearLayout empty=column("Google Maps isn't set up","This build has no Google Maps Platform API key. Builds made with MAPS_API_KEY show Google maps, satellite imagery, Street View, search and directions.");
        frame.addView(empty);
    }
    /** The Map page: map view chooser plus drawn controls; landscape gets a slim header and side controls. */
    private void mapPage() {
        LinearLayout col=b.column(); content.addView(col);
        TextView typeButton=b.button(MapPanel.TYPES[mapType],true,v->chooseMapType());
        typeButton.setCompoundDrawablePadding(b.dp(6));
        typeButton.setCompoundDrawablesWithIntrinsicBounds(null,null,new ChevronDrawable(b.onAccent,b.dp(16),true),null);
        typeButton.setContentDescription("Map view: "+MapPanel.TYPES[mapType]+". Choose map view.");
        if(landscape) {
            LinearLayout head=b.row(); head.setPadding(b.dp(16),b.dp(6),b.dp(12),b.dp(6));
            TextView title=b.text("Maps",20,true); head.addView(title,new LinearLayout.LayoutParams(0,-2,1));
            typeButton.setTextSize(12); typeButton.setMinHeight(b.dp(34)); typeButton.setPadding(b.dp(14),0,b.dp(14),0);
            head.addView(typeButton,new LinearLayout.LayoutParams(-2,b.dp(34)));
            col.addView(head);
        } else {
            LinearLayout head=column("Maps",null); col.addView(head);
            LinearLayout tools=b.row();
            tools.addView(b.button("Search",false,v->show("search")),new LinearLayout.LayoutParams(0,b.dp(44),1));
            tools.addView(typeButton,new LinearLayout.LayoutParams(0,b.dp(44),1));head.addView(tools);
        }
        FrameLayout frame=new FrameLayout(this); col.addView(frame,new LinearLayout.LayoutParams(-1,0,1));
        if(!Net.configured()) { notConfigured(frame); return; }
        TextView loading=b.label("Loading Google Maps…",14,b.secondary); loading.setGravity(Gravity.CENTER);
        frame.addView(loading,new FrameLayout.LayoutParams(-1,-1));
        // Drawn round controls float on the right side; landscape adds search, view and Street View there too.
        LinearLayout controls=b.column(); controls.setGravity(Gravity.CENTER_HORIZONTAL);
        int size=b.dp(landscape?44:48);
        if(landscape) {
            control(controls,iconButton(Icon.Kind.SEARCH,"Search",v->show("search")),size);
            control(controls,iconButton(Icon.Kind.LAYERS,"Choose map view",v->chooseMapType()),size);
        }
        control(controls,iconButton(Icon.Kind.STREET,"Street View at the map centre",v->{streetPoint=map==null?center:map.view.getModel().mapViewPosition.getCenter();heading=0;pitch=0;show("street");}),size);
        control(controls,iconButton(Icon.Kind.PLUS,"Zoom in",v->{if(map!=null)map.view.getModel().mapViewPosition.zoomIn();}),size);
        control(controls,iconButton(Icon.Kind.MINUS,"Zoom out",v->{if(map!=null)map.view.getModel().mapViewPosition.zoomOut();}),size);
        control(controls,iconButton(Icon.Kind.LOCATE,"Show my location",v->locate()),size);
        FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(-2,-2,Gravity.END|(landscape?Gravity.CENTER_VERTICAL:Gravity.TOP)); cp.setMargins(0,b.dp(8),b.dp(10),b.dp(8)); frame.addView(controls,cp);
        final int ticket=request, type=mapType;
        final JSONArray styles=night()?nightStyle():null;
        worker.execute(()->{
            try {
                String session=Net.session(this,prefs,type,styles);
                main.post(()->{ if(!dead&&ticket==request) openMap(frame,loading,type,session); });
            } catch(Exception e) { main.post(()->{ if(!dead&&ticket==request) loading.setText("Google Maps unavailable: "+e.getMessage()); }); }
        });
    }
    private boolean night() { return prefs.getInt("theme",0)!=1; }
    private JSONArray nightStyle() {
        try(InputStream in=getResources().openRawResource(R.raw.map_night)) { return new JSONArray(Net.read(in)); }
        catch(IOException|JSONException e) { return null; }
    }
    private void chooseMapType() {
        choose("Map view",MapPanel.TYPES,i->{ mapType=i; prefs.edit().putInt("map_type",i).apply(); show("map"); });
    }
    private void openMap(FrameLayout frame,View loading,int type,String session) {
        try {
            map=new MapPanel(this,type,session,center,zoom,point->{
                map.mark(point,b.accent);
                choose("Dropped pin",new String[]{"Street View here","Directions from here","Directions to here","Copy coordinates"},i->{
                    String value=coordinateText(point);
                    if(i==0) { streetPoint=point; heading=0; pitch=0; show("street"); }
                    else if(i==3) { ((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Coordinates",value)); }
                    else { if(i==1) fromText=value; else toText=value; show("directions"); }
                });
            });
            frame.removeView(loading); frame.addView(map.view,0);
            if(lastRoute!=null) map.route(lastRoute,b.accent);
            map.view.getModel().mapViewPosition.addObserver(this::scheduleAttribution);
        } catch(Exception e) { if(map!=null) { map.destroy(); map=null; } message("Map unavailable",String.valueOf(e.getMessage())); return; }
        attribution=b.label("Google",11,b.text);
        attribution.setBackground(b.shape(b.surface,12,true)); attribution.setPadding(b.dp(10),b.dp(6),b.dp(10),b.dp(6));
        attribution.setOnClickListener(v->show("about"));
        FrameLayout.LayoutParams ap=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.START); ap.setMargins(b.dp(8),0,b.dp(8),b.dp(8)); frame.addView(attribution,ap);
        scheduleAttribution();
        if(!landscape) {
            TextView hint=b.label(type==1?"Satellite imagery with labels. Touch and hold to drop a pin.":"Touch and hold the map to drop a pin for Street View or directions.",12,b.secondary);
            hint.setPadding(b.dp(16),b.dp(8),b.dp(16),b.dp(8)); ((LinearLayout)frame.getParent()).addView(hint);
        }
    }
    private void control(LinearLayout controls,View v,int size) {
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(size,size); lp.setMargins(0,b.dp(landscape?3:4),0,b.dp(landscape?3:4)); controls.addView(v,lp);
    }
    private final Runnable attributionUpdate=this::updateAttribution;
    private void scheduleAttribution() { main.removeCallbacks(attributionUpdate); main.postDelayed(attributionUpdate,1200); }
    /** Google requires its logo and the visible area's data providers over Map Tiles imagery. */
    private void updateAttribution() {
        if(map==null||attribution==null||map.view.getWidth()==0) return;
        final MapPanel current=map; final TextView label=attribution;
        final BoundingBox box=current.view.getBoundingBox(); final byte z=current.view.getModel().mapViewPosition.getZoomLevel();
        worker.execute(()->{
            try {
                String text=Net.attribution(this,current.session,z,Math.min(85,box.maxLatitude),Math.max(-85,box.minLatitude),box.maxLongitude,box.minLongitude);
                main.post(()->{ if(map==current&&attribution==label) label.setText(text.isEmpty()?"Google":"Google · "+text); });
            } catch(Exception ignored) {}
        });
    }
    static String coordinateText(LatLong p) { return String.format(Locale.US,"%.6f, %.6f",p.latitude,p.longitude); }
    private void search() {
        LinearLayout col=scrollPage("Find a place","Search by name, address, or latitude, longitude.","Map");
        EditText query=field("Place or coordinates",""); col.addView(query);
        LinearLayout results=b.column(); TextView status=b.label("Search uses Google's Geocoding service.",13,b.secondary);
        margin(col,b.button("Search",true,v->{
            String q=query.getText().toString().trim(); if(q.isEmpty())return;
            LatLong coordinate=Net.coordinate(q);if(coordinate==null)coordinate=PlaceNames.find(q);
            if(coordinate!=null) { center=coordinate;zoom=(byte)(coordinate.equals(PlaceNames.GULF_POINT)?5:coordinate.equals(PlaceNames.LAKE_POINT)?8:15);show("map");return; }
            status.setText("Searching…"); results.removeAllViews(); final int ticket=request;
            worker.execute(()->{
                try {
                    List<Net.Place> found=Net.search(this,q);
                    main.post(()->{ if(dead||ticket!=request)return; status.setText(found.isEmpty()?"No places found.":"Choose a result.");
                        for(Net.Place place:found) margin(results,b.button(place.name,false,w->{center=place.point;zoom=15;show("map");}),0,4);
                    });
                } catch(Exception e) { main.post(()->{if(!dead&&ticket==request)status.setText("Search unavailable: "+e.getMessage());}); }
            });
        }),0,10); col.addView(status); col.addView(results);
    }
    /**
     * Street View from Google's Street View Static API: one light image at a time, turned with the side
     * buttons or by dragging, so it works on Android 4.2 without Play services or WebView.
     */
    private void street() {
        LinearLayout col=b.column(); content.addView(col);
        if(landscape) {
            LinearLayout head=b.row(); head.setPadding(b.dp(16),b.dp(6),b.dp(12),b.dp(6));
            head.addView(b.text("Street View",20,true),new LinearLayout.LayoutParams(0,-2,1));
            TextView mapButton=b.button("Map",false,v->show("map")); mapButton.setTextSize(12); mapButton.setMinHeight(b.dp(34));
            head.addView(mapButton,new LinearLayout.LayoutParams(-2,b.dp(34)));
            col.addView(head);
        } else col.addView(column("Street View","Look around at the map centre or a dropped pin."));
        FrameLayout frame=new FrameLayout(this); col.addView(frame,new LinearLayout.LayoutParams(-1,0,1));
        if(!Net.configured()) { notConfigured(frame); return; }
        if(streetPoint==null) streetPoint=center;
        final ImageView image=new ImageView(this); image.setScaleType(ImageView.ScaleType.CENTER_CROP); image.setBackgroundColor(b.surface);
        image.setContentDescription("Street View image");
        frame.addView(image,new FrameLayout.LayoutParams(-1,-1));
        final TextView status=b.label("Finding Street View…",13,b.text);
        status.setBackground(b.shape(b.surface,12,true)); status.setPadding(b.dp(10),b.dp(6),b.dp(10),b.dp(6));
        FrameLayout.LayoutParams sp=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.START); sp.setMargins(b.dp(8),0,b.dp(8),b.dp(8)); frame.addView(status,sp);
        final LatLong[] pano={null};
        final Runnable load=()->loadStreet(image,status,pano[0]);
        int size=b.dp(landscape?44:48);
        // Turn buttons sit on both sides of the image.
        ImageButton left=iconButton(Icon.Kind.TURN_LEFT,"Turn left",v->{heading-=45;load.run();});
        ImageButton right=iconButton(Icon.Kind.TURN_RIGHT,"Turn right",v->{heading+=45;load.run();});
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(size,size,Gravity.START|Gravity.CENTER_VERTICAL); lp.leftMargin=b.dp(10); frame.addView(left,lp);
        LinearLayout side=b.column(); side.setGravity(Gravity.CENTER_HORIZONTAL);
        control(side,iconButton(Icon.Kind.PLUS,"Look up",v->{pitch=Math.min(60,pitch+20);load.run();}),size);
        control(side,right,size);
        control(side,iconButton(Icon.Kind.MINUS,"Look down",v->{pitch=Math.max(-60,pitch-20);load.run();}),size);
        FrameLayout.LayoutParams rp=new FrameLayout.LayoutParams(-2,-2,Gravity.END|Gravity.CENTER_VERTICAL); rp.rightMargin=b.dp(10); frame.addView(side,rp);
        image.setOnTouchListener(new View.OnTouchListener() {
            float startX; boolean dragging;
            @Override public boolean onTouch(View v,MotionEvent e) {
                if(e.getAction()==MotionEvent.ACTION_DOWN){startX=e.getX();dragging=false;return true;}
                if(e.getAction()==MotionEvent.ACTION_MOVE&&Math.abs(e.getX()-startX)>b.dp(24))dragging=true;
                if(e.getAction()==MotionEvent.ACTION_UP&&dragging&&v.getWidth()>0){heading-=Math.round((e.getX()-startX)/v.getWidth()*90);load.run();}
                return true;
            }
        });
        final int ticket=request; final LatLong near=streetPoint;
        worker.execute(()->{
            try {
                LatLong found=Net.streetView(this,near);
                main.post(()->{ if(dead||ticket!=request)return;
                    if(found==null){status.setText("No Street View imagery within 80 m. Drop a pin on a road to try there.");return;}
                    pano[0]=found; image.post(load);
                });
            } catch(Exception e) { main.post(()->{if(!dead&&ticket==request)status.setText("Street View unavailable: "+e.getMessage());}); }
        });
    }
    private void loadStreet(ImageView image,TextView status,LatLong at) {
        if(at==null) return;
        final int ticket=request, h=heading, p=pitch;
        // Request about the view's aspect within the API's 640 px limit; Android scales it up.
        int w=Math.max(1,image.getWidth()), ht=Math.max(1,image.getHeight());
        float scale=640f/Math.max(w,ht); final int rw=Math.max(64,Math.round(w*scale)), rh=Math.max(64,Math.round(ht*scale));
        status.setText("Loading…");
        worker.execute(()->{
            try {
                byte[] data=Net.bytes(this,Net.streetViewImage(at,h,p,90,rw,rh));
                Bitmap bitmap=BitmapFactory.decodeByteArray(data,0,data.length);
                main.post(()->{ if(dead||ticket!=request||bitmap==null)return; image.setImageBitmap(bitmap);
                    status.setText(String.format(Locale.US,"Google · %s · %s",compass(h),coordinateText(at))); });
            } catch(Exception e) { main.post(()->{if(!dead&&ticket==request)status.setText("Street View unavailable: "+e.getMessage());}); }
        });
    }
    static String compass(int heading) {
        String[] names={"North","Northeast","East","Southeast","South","Southwest","West","Northwest"};
        return names[(((Math.round(heading/45f))%8)+8)%8];
    }
    private void directions() {
        LinearLayout col=scrollPage("Directions","Driving routes from Google, with a written route overview.",null);
        EditText from=field("From: place or lat, lon",fromText), to=field("To: place or lat, lon",toText);
        margin(col,from,0,4); margin(col,to,0,4);
        TextWatcher watcher=new TextWatcher() { public void beforeTextChanged(CharSequence s,int a,int count,int after){} public void onTextChanged(CharSequence s,int start,int before,int count){fromText=from.getText().toString();toText=to.getText().toString();} public void afterTextChanged(Editable e){} };
        from.addTextChangedListener(watcher);to.addTextChangedListener(watcher);
        TextView status=b.label("Routes use Google's Routes service, with live traffic. Places are sent when you request directions.",13,b.secondary);
        margin(col,b.button("Get driving directions",true,v->{
            if(fromText.trim().isEmpty()||toText.trim().isEmpty()) {status.setText("Enter both a start and a destination.");return;}
            ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(to.getWindowToken(),0);
            final String origin=fromText.trim(),destination=toText.trim(); final int ticket=request; status.setText("Finding a driving route…"); v.setEnabled(false);
            worker.execute(()->{
                try {
                    JSONObject route=Net.route(this,origin,destination);
                    main.post(()->{ if(dead||ticket!=request)return;lastRoute=route; prefs.edit().putString("last_route",route.toString()).apply();fit(route);show("directions"); });
                } catch(Exception e) {main.post(()->{if(!dead&&ticket==request){status.setText("Directions unavailable: "+e.getMessage());v.setEnabled(true);}});}
            });
        }),0,10);col.addView(status);
        margin(col,b.button("Navigate in Google Maps",false,v->{
            if(fromText.trim().isEmpty()||toText.trim().isEmpty()) {status.setText("Enter both a start and a destination.");return;}
            try { open(Net.navigationUrl(fromText,toText)); } catch(UnsupportedEncodingException ignored) {}
        }),0,4);
        note(col,"Grapefruit shows the route and a written overview. For turn-by-turn voice guidance, Navigate hands the trip to the Google Maps app or website. Check road conditions and restrictions before travel.");
        if(lastRoute!=null) {
            section(col,"Last route · saved on this device");
            row(col,String.format(Locale.US,"%.1f km",lastRoute.optDouble("distance")/1000),Math.round(lastRoute.optDouble("duration")/60)+" min",null);
            margin(col,b.button("Show route on map",true,v->show("map")),0,6);
            margin(col,b.button("Clear saved route",false,v->{lastRoute=null;prefs.edit().remove("last_route").apply();show("directions");}),0,6);
            JSONArray steps=lastRoute.optJSONArray("steps");
            if(steps==null) note(col,"Saved route details could not be read.");
            else for(int i=0;i<steps.length();i++) {
                JSONObject step=steps.optJSONObject(i); if(step==null) continue;
                note(col,(i+1)+". "+step.optString("text","Continue")+String.format(Locale.US," · %.0f m",step.optDouble("distance")));
            }
        }
    }
    /** Centres the saved map view on a route and picks a zoom that shows it. */
    private void fit(JSONObject route) {
        JSONArray points=route.optJSONArray("points"); if(points==null||points.length()==0) return;
        double minLat=90,maxLat=-90,minLon=180,maxLon=-180;
        for(int i=0;i<points.length();i++){JSONArray p=points.optJSONArray(i);if(p==null)continue;double la=p.optDouble(0),lo=p.optDouble(1);minLat=Math.min(minLat,la);maxLat=Math.max(maxLat,la);minLon=Math.min(minLon,lo);maxLon=Math.max(maxLon,lo);}
        center=new LatLong((minLat+maxLat)/2,(minLon+maxLon)/2);
        double span=Math.max(maxLat-minLat,maxLon-minLon); zoom=(byte)Math.max(2,Math.min(15,Math.floor(Math.log(180/Math.max(.01,span))/Math.log(2))));
        prefs.edit().putLong("lat",Double.doubleToLongBits(center.latitude)).putLong("lon",Double.doubleToLongBits(center.longitude)).putInt("zoom",zoom).apply();
    }
    private void settings() {
        LinearLayout col=scrollPage("Settings",null,null);
        section(col,"Appearance");
        row(col,"Theme",THEMES[prefs.getInt("theme",0)],v->choose("Theme",THEMES,i->{prefs.edit().putInt("theme",i).apply();recreate();}));
        note(col,"A new universal theme selection in Grapefruit Settings also applies here. You can keep a different local theme until the next universal selection. Dark themes use a night-styled Google map.");
        section(col,"Maps");
        row(col,"Map view",MapPanel.TYPES[mapType]+" ›",v->choose("Map view",MapPanel.TYPES,i->{mapType=i;prefs.edit().putInt("map_type",i).apply();show("settings");}));
        row(col,"Street View","Open ›",v->show("street"));
        row(col,"Clear saved route",lastRoute==null?"None":"Clear",v->{lastRoute=null;prefs.edit().remove("last_route").apply();show("settings");});
        note(col,"Grapefruit uses Gulf of Mexico and Lake Ontario. Its own labels over the map and search results preserve these names. Text inside Google's map imagery is provider-controlled.");
        section(col,"Google Maps Platform");
        row(col,"Google services",Net.configured()?"Configured":"Not set up",null);
        note(col,"Map Tiles, Street View Static, Geocoding and Routes are called directly over HTTPS. No Google Play services, Google Maps SDK or WebView is needed, so this works on Android 4.2.2 and later.");
        section(col,"About");
        row(col,"About "+NAME,versionName()+" ›",v->show("about"));
        note(col,NAME+" is free software. No ads, tracking SDKs or accounts.");
    }
    private void about() {
        LinearLayout col=scrollPage("About",null,"Settings");
        // Match the media apps' spacious, centered About branding, below navigation.
        LinearLayout hero=b.column();hero.setGravity(Gravity.CENTER_HORIZONTAL);hero.setPadding(0,b.dp(landscape?4:16),0,b.dp(8));
        ImageView icon=new ImageView(this);icon.setImageResource(R.drawable.ic_about);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        hero.addView(icon,new LinearLayout.LayoutParams(b.dp(landscape?64:88),b.dp(landscape?64:88)));
        TextView wordmark=b.wordmark(landscape?30:40,!landscape);wordmark.setPadding(0,b.dp(14),0,0);
        hero.addView(wordmark,new LinearLayout.LayoutParams(-1,-2));
        TextView version=b.label("Version "+versionName(),15,b.secondary);version.setGravity(Gravity.CENTER);version.setPadding(0,b.dp(8),0,0);
        hero.addView(version,new LinearLayout.LayoutParams(-1,-2));col.addView(hero);
        section(col,"License");row(col,"GNU GPL","Version 3 or later",v->open("https://www.gnu.org/licenses/gpl-3.0.html"));
        row(col,"Source code","GitHub ›",v->open("https://github.com/hayliewordsman/grapefruitmusic/tree/main/maps-barlow"));
        note(col,"Free software: you can share and change this app under the GNU General Public License, version 3 or later. It comes with no warranty.");
        section(col,"Developer");row(col,"Developer","Haylie",v->{if(eggState.tap(prefs.getInt("theme",0)))show("egg");});
        section(col,"Map data & services");
        row(col,"Google Maps","Terms ›",v->open("https://maps.google.com/help/terms_maps/"));
        row(col,"Google privacy","Policy ›",v->open("https://policies.google.com/privacy"));
        note(col,"Maps, satellite and terrain imagery, Street View, search and driving directions are provided by Google Maps Platform. Map data and imagery are © Google and its data providers, shown over the map for the visible area.");
        note(col,"Online map views send the visible tile area to Google. Search sends the submitted query; directions send the start and destination; Street View sends the chosen point. No location is sent in the background.");
        section(col,"Open-source components");
        row(col,"Open-source licenses","Read ›",v->show("licenses"));
        note(col,"Mapsforge 0.21.0 (map view only) · LGPL-3.0 with exception. AndroidSVG 1.4 · Apache-2.0. Conscrypt 2.5.3 · Apache-2.0, with bundled BoringSSL notices. Geist by Vercel · SIL OFL 1.1. Library licenses and source links ship in assets/licenses; the original game artwork is GPL-3.0-or-later.");
    }
    private String versionName() {
        try { return getPackageManager().getPackageInfo(getPackageName(),0).versionName; }
        catch(PackageManager.NameNotFoundException e) { return "1.0"; }
    }
    private void open(String url) { try {startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));} catch(ActivityNotFoundException e){message("No browser",url);} }
    private void licenses() {
        LinearLayout col=scrollPage("Licenses","Free software and service acknowledgements.","About");
        try {for(String file:getAssets().list("licenses"))row(col,file,"Read ›",v->{try(InputStream in=getAssets().open("licenses/"+file);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);message(file,out.toString("UTF-8"));}catch(IOException e){message("License unavailable",e.getMessage());}});}
        catch(IOException e){note(col,e.getMessage());}
    }
    private void easterEgg() {
        if(prefs.getInt("theme",0)!=3) {show("about");return;}
        LinearLayout col=scrollPage("Victory break","Tap the head. Make your mark.","About");
        EggView scene=new EggView(this,b);egg=scene;col.addView(scene,new LinearLayout.LayoutParams(-1,b.dp(148+Math.min(280,getResources().getConfiguration().screenWidthDp*.65f))));
        TextView counter=b.label("0 drops",14,b.secondary);counter.setGravity(Gravity.CENTER);col.addView(counter);
        scene.setOnClickListener(v->{scene.drop();counter.setText(scene.count+(scene.count==1?" drop":" drops"));});
        note(col,"Drops clear when you go back.");
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
    @Override public void onBackPressed(){if(page.equals("egg")||page.equals("licenses"))show("about");else if(page.equals("about"))show("settings");else if(!page.equals("map"))show("map");else super.onBackPressed();}
    @Override protected void onResume(){super.onResume();if(UniversalTheme.sync(this,prefs,false,UNIVERSAL_THEME_IDS)){recreate();return;}if(map!=null)map.resume();}
    @Override protected void onPause(){rememberMap();stopLocation();if(map!=null)map.pause();super.onPause();}
    @Override protected void onDestroy(){dead=true;stopLocation();worker.shutdownNow();main.removeCallbacksAndMessages(null);if(map!=null){map.destroy();map=null;}if(egg!=null)egg.clear();super.onDestroy();}
}
