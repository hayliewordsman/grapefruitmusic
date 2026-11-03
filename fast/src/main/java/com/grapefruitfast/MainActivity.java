package com.grapefruitfast;

import android.app.Activity;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.text.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import com.grapefruitmusic.ui.Fonts;
import java.util.*;

/** Native touch + remote interface; no WebView, Play Services or account is required. */
public final class MainActivity extends Activity {
    private android.content.SharedPreferences prefs;
    private Brand b;
    private List<Channel> channels=Collections.emptyList();
    private final Set<String> favourites=new HashSet<>();
    private final List<Channel> visible=new ArrayList<>();
    private String tab="Live TV", category="All", country="All", query="", error;
    private Channel watching;
    private LivePlayer playback;
    private TextView state, qualityLabel, count;
    private FrameLayout root, body;
    private LinearLayout shell, page, nav;
    private ListView list;
    private ChannelAdapter adapter;
    private boolean fullScreen, started;
    private int listPosition;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        prefs=getSharedPreferences("fast",MODE_PRIVATE);
        favourites.addAll(prefs.getStringSet("favourites",Collections.<String>emptySet()));
        try { channels=Catalog.parse(Catalog.read(getAssets().open("channels.json"))); }
        catch(Exception e) {error="The channel list could not be opened.";}
        if(saved!=null) {
            tab=saved.getString("tab","Live TV"); category=saved.getString("category","All");
            country=saved.getString("country","All");query=saved.getString("query","");
            fullScreen=saved.getBoolean("fullscreen",false);
            watching=find(saved.getString("watching"));
        }
        build();
    }
    private Channel find(String id) {for(Channel c:channels) if(c.id.equals(id)) return c; return null;}
    private boolean wide() {Configuration c=getResources().getConfiguration();return c.screenWidthDp>=c.screenHeightDp*0.85f;}
    private int dp(float n) {return b.dp(n);}
    private void build() {
        b=new Brand(this,prefs.getInt("theme",0));
        root=new FrameLayout(this);root.setFitsSystemWindows(true);root.setBackgroundColor(b.bg);
        shell=wide()?b.row():b.column();root.addView(shell,new FrameLayout.LayoutParams(-1,-1));
        body=new FrameLayout(this);
        if(wide()) {nav=b.column();nav.setPadding(dp(14),dp(22),dp(14),dp(16));shell.addView(nav,new LinearLayout.LayoutParams(dp(148),-1));}
        shell.addView(body,new LinearLayout.LayoutParams(wide()?0:-1,wide()?-1:0,1));
        if(!wide()) {nav=b.row();nav.setPadding(dp(10),dp(8),dp(10),dp(10));shell.addView(nav,new LinearLayout.LayoutParams(-1,-2));}
        navigation();setContentView(root);
        if(watching!=null) watchPage();else if(tab.equals("Settings")) settings();else browse();
        fullscreen(fullScreen);
    }
    private void navigation() {
        nav.removeAllViews();
        if(wide()) {
            ImageView icon=new ImageView(this);icon.setImageResource(com.grapefruitfast.R.mipmap.ic_launcher);
            nav.addView(icon,new LinearLayout.LayoutParams(-1,dp(80)));
            TextView name=b.text("Grapefruit\nFAST",18,true);name.setTypeface(android.graphics.Typeface.createFromAsset(getAssets(),"fonts/GeistPixel-Triangle-FAST.ttf"));name.setTextColor(b.accent);
            name.setGravity(Gravity.CENTER);nav.addView(name,new LinearLayout.LayoutParams(-1,dp(68)));
        }
        for(String label:new String[]{"Live TV","Favourites","Search","Settings"}) {
            TextView button=b.button(label,tab.equals(label),v->{
                stop();tab=label;query="";listPosition=0;fullScreen=false;build();
            });
            if(!wide()) {button.setTextSize(11);button.setPadding(dp(6),dp(8),dp(6),dp(8));}
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(wide()?-1:0,dp(52),wide()?0:1);
            if(wide())p.bottomMargin=dp(10);else p.rightMargin=dp(4);
            nav.addView(button,p);
        }
    }
    private void newPage() {
        body.removeAllViews();page=b.column();page.setPadding(dp(18),dp(14),dp(18),0);body.addView(page,new FrameLayout.LayoutParams(-1,-1));
    }
    private void title(String title,String subtitle) {
        TextView heading=b.text(title,30,true);page.addView(heading);
        if(subtitle!=null) {TextView sub=b.text(subtitle,13,false);sub.setTextColor(b.secondary);sub.setPadding(0,dp(4),0,dp(14));page.addView(sub);}
    }
    private void browse() {
        newPage();
        title(tab.equals("Live TV")?"Live TV":tab,tab.equals("Favourites")?"Your channels, in one place.":"Free to watch. A little more live.");
        if(tab.equals("Search")) {
            EditText search=new EditText(this);search.setSingleLine();search.setTextColor(b.text);search.setHintTextColor(b.secondary);
            search.setTypeface(Fonts.regular());search.setTextSize(16);search.setHint("Search channels, cities or genres");
            search.setPadding(dp(18),0,dp(18),0);search.setBackground(b.shape(b.control,100,true));
            search.setText(query);page.addView(search,new LinearLayout.LayoutParams(-1,dp(48)));
            search.addTextChangedListener(new TextWatcher(){
                public void beforeTextChanged(CharSequence s,int start,int count,int after){}
                public void onTextChanged(CharSequence s,int start,int before,int count){query=s.toString();filter();}
                public void afterTextChanged(Editable e){}
            });
        }
        chips(new String[]{"All","News","Series","Reality","Lifestyle"},true);
        chips(new String[]{"All","Canada","United States"},false);
        count=b.text("",12,false);count.setTextColor(b.secondary);count.setPadding(dp(6),dp(4),0,dp(8));page.addView(count);
        addList(page);
        if(error!=null) note(error);
        Channel last=find(prefs.getString("lastChannel",""));
        if(last!=null) {
            TextView resume=b.button("Continue watching · "+last.name,false,v->tune(last));
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(54));p.topMargin=dp(8);p.bottomMargin=dp(8);page.addView(resume,p);
        }
    }
    private void chips(String[] labels,boolean categories) {
        HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row=b.row();row.setPadding(0,dp(8),0,dp(4));scroll.addView(row);
        for(String label:labels) {
            TextView chip=b.button(label,label.equals(categories?category:country),v->{
                if(categories)category=label;else country=label;
                listPosition=0;if(watching!=null)watchPage();else browse();
            });
            chip.setTextSize(13);chip.setMinHeight(dp(40));
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(42));p.rightMargin=dp(8);row.addView(chip,p);
        }
        page.addView(scroll,new LinearLayout.LayoutParams(-1,-2));
    }
    private void addList(LinearLayout parent) {
        list=new ListView(this);list.setDivider(null);list.setSelector(android.R.color.transparent);
        list.setCacheColorHint(Color.TRANSPARENT);list.setClipToPadding(false);list.setPadding(0,0,0,dp(8));
        list.setItemsCanFocus(false);
        adapter=new ChannelAdapter();list.setAdapter(adapter);
        list.setOnItemClickListener((p,v,pos,id)->{
            ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(v.getWindowToken(),0);
            listPosition=pos;tune(visible.get(pos));
        });
        list.setOnItemLongClickListener((p,v,pos,id)->{details(visible.get(pos));return true;});
        list.setOnKeyListener((v,key,event)->{
            if(key==KeyEvent.KEYCODE_MENU && event.getAction()==KeyEvent.ACTION_UP && list.getSelectedItemPosition()>=0) {
                details(visible.get(list.getSelectedItemPosition()));return true;
            } return false;
        });
        parent.addView(list,new LinearLayout.LayoutParams(-1,0,1));filter();list.setSelection(listPosition);
    }
    private void filter() {
        visible.clear();
        for(Channel c:channels) {
            if(tab.equals("Favourites")&&!favourites.contains(c.id))continue;
            if(!category.equals("All")&&!category.equals(c.category))continue;
            if(country.equals("Canada")&&!c.country.equals("CA"))continue;
            if(country.equals("United States")&&!c.country.equals("US"))continue;
            if(!c.matches(query))continue;
            visible.add(c);
        }
        if(adapter!=null)adapter.notifyDataSetChanged();
        if(count!=null)count.setText(visible.size()==0?"No channels match. Try another filter.":visible.size()+" channels · tap to watch · hold for options");
    }
    private class ChannelAdapter extends BaseAdapter {
        public int getCount(){return visible.size();}
        public Object getItem(int pos){return visible.get(pos);}
        public long getItemId(int pos){return pos;}
        public View getView(int pos,View reuse,android.view.ViewGroup parent) {
            Channel c=visible.get(pos);
            LinearLayout row=b.row();row.setPadding(dp(12),dp(10),dp(12),dp(10));
            row.setBackground(b.interactive(false,20));
            TextView logo=b.text(c.initials(),c.initials().length()>2?14:22,true);logo.setTextColor(b.onAccent);
            logo.setGravity(Gravity.CENTER);logo.setBackground(b.gradient(16));row.addView(logo,new LinearLayout.LayoutParams(dp(56),dp(56)));
            LinearLayout text=b.column();text.setPadding(dp(14),0,dp(8),0);
            TextView name=b.text(c.name,16,true);name.setMaxLines(2);text.addView(name);
            TextView meta=b.text((favourites.contains(c.id)?"★  ":"")+c.country+"  ·  "+c.category,12,false);meta.setTextColor(b.secondary);text.addView(meta);
            row.addView(text,new LinearLayout.LayoutParams(0,-2,1));
            TextView live=b.text(watching!=null&&watching.id.equals(c.id)?"●":"›",23,true);live.setTextColor(b.accent);row.addView(live);
            FrameLayout wrapper=new FrameLayout(MainActivity.this);
            FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-1,-2);p.bottomMargin=dp(8);wrapper.addView(row,p);
            // The selector follows the ListView's selection for remote / keyboard navigation.
            android.graphics.drawable.StateListDrawable selector=new android.graphics.drawable.StateListDrawable();
            android.graphics.drawable.GradientDrawable selected=b.shape(b.control,22,false); selected.setStroke(dp(3),b.accent);
            selector.addState(new int[]{android.R.attr.state_selected},selected);
            selector.addState(new int[]{android.R.attr.state_pressed},selected);
            selector.addState(new int[]{},b.shape(b.bg,22,false));wrapper.setBackground(selector);
            return wrapper;
        }
    }
    private void tune(Channel channel) {
        stop();watching=channel;prefs.edit().putString("lastChannel",channel.id).apply();
        watchPage();
    }
    private void watchPage() {
        body.removeAllViews();
        page=b.column();page.setPadding(dp(12),dp(10),dp(12),0);body.addView(page,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout header=b.row();
        header.addView(b.button("‹ Channels",false,v->{stop();fullScreen=false;build();}));
        TextView name=b.text(watching.name,18,true);name.setPadding(dp(14),0,0,0);name.setMaxLines(2);
        header.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        header.addView(b.button(favourites.contains(watching.id)?"★":"☆",false,v->{favourite(watching);watchPage();}));
        if(!fullScreen)page.addView(header,new LinearLayout.LayoutParams(-1,dp(56)));
        LinearLayout content=wide()&&!fullScreen?b.row():b.column();page.addView(content,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout stage=b.column();stage.setGravity(Gravity.CENTER_VERTICAL);
        if(fullScreen)content.addView(stage,new LinearLayout.LayoutParams(-1,0,1));
        else if(wide())content.addView(stage,new LinearLayout.LayoutParams(0,-1,1.5f));
        else content.addView(stage,new LinearLayout.LayoutParams(-1,-2));
        AspectFrame video=new AspectFrame(this);currentVideo=video;video.setBackgroundColor(Color.BLACK);
        SurfaceView surface=new SurfaceView(this);video.addView(surface,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));
        stage.addView(video,new LinearLayout.LayoutParams(-1,wide()||fullScreen?0:-2,wide()||fullScreen?1:0));
        state=b.text("Connecting…",14,true);state.setTextColor(b.accent);state.setPadding(dp(8),dp(8),dp(8),0);stage.addView(state);
        qualityLabel=b.text("Auto · adapts to your connection",12,false);qualityLabel.setTextColor(b.secondary);qualityLabel.setPadding(dp(8),dp(4),dp(8),dp(8));stage.addView(qualityLabel);
        HorizontalScrollView actionsScroll=new HorizontalScrollView(this);actionsScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout actions=b.row();actionsScroll.addView(actions);stage.addView(actionsScroll);
        action(actions,"Retry",()->tune(watching));
        action(actions,"Quality",this::qualityDialog);
        action(actions,fullScreen?"Exit full screen":"Full screen",()->{fullScreen=!fullScreen;watchPage();});
        action(actions,"Official site",()->website(watching));
        TextView description=b.text(watching.description,13,false);description.setTextColor(b.secondary);description.setPadding(dp(8),dp(10),dp(8),dp(10));if(!fullScreen)stage.addView(description);
        LinearLayout lineup=b.column();lineup.setPadding(wide()?dp(16):0,dp(8),0,0);
        if(!fullScreen)content.addView(lineup,new LinearLayout.LayoutParams(wide()?0:-1,wide()?-1:0,1));
        count=null;addList(lineup);
        if(playback==null && started)startPlayer(surface);else if(playback!=null){playback.attach(surface);pendingSurface=surface;}
        else pendingSurface=surface;
        fullscreen(fullScreen);
    }
    private SurfaceView pendingSurface;
    private AspectFrame currentVideo;
    private void startPlayer(SurfaceView surface) {
        playback=new LivePlayer(this,new LivePlayer.Listener(){
            public void status(String message){if(state!=null)state.setText(message);}
            public void format(String message){if(qualityLabel!=null)qualityLabel.setText(message);}
            public void aspect(float ratio){if(currentVideo!=null){currentVideo.ratio=ratio;currentVideo.requestLayout();}}
        },prefs.getInt("quality",0));pendingSurface=surface;playback.attach(surface);playback.tune(watching);
    }
    private void action(LinearLayout row,String label,Runnable action) {
        TextView button=b.button(label,false,v->action.run());
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(46));p.rightMargin=dp(6);row.addView(button,p);
    }
    private void favourite(Channel c) {
        if(!favourites.remove(c.id))favourites.add(c.id);
        prefs.edit().putStringSet("favourites",new HashSet<>(favourites)).apply();
        if(adapter!=null)adapter.notifyDataSetChanged();
    }
    private void stop() {if(playback!=null){playback.release();playback=null;}watching=null;pendingSurface=null;}
    private void details(Channel c) {
        dialog(c.name,c.description+"\n\n"+c.country+" · "+c.category+"\nChannels may be region restricted. Provider advertisements remain part of the stream.",
            new String[]{"Watch live",favourites.contains(c.id)?"Remove favourite":"Add favourite","Official site"},
            new Runnable[]{()->tune(c),()->{favourite(c);filter();},()->website(c)});
    }
    private void website(Channel c) {
        try {startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(c.website)));}
        catch(ActivityNotFoundException e){dialog("No browser installed","This device has no app to open "+c.website,new String[]{"OK"},new Runnable[]{()->{}});}
    }
    private void qualityDialog() {
        dialog("Playback quality","Auto changes resolution as bandwidth changes. On Android 4.x it stays at 720p or below and 30 fps. Your device still needs a supported video decoder.",
            new String[]{"Auto · adaptive","Data saver · up to 480p","Compatibility · up to 360p"},
            new Runnable[]{()->quality(0),()->quality(1),()->quality(2)});
    }
    private void quality(int value) {prefs.edit().putInt("quality",value).apply();if(playback!=null)playback.setQuality(value);}
    private void settings() {
        newPage();title("Settings","Make yourself comfortable.");
        ScrollView scroll=new ScrollView(this);LinearLayout settings=b.column();scroll.addView(settings);page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        setting(settings,"Theme",()->dialog("Choose your theme",null,new String[]{"Grapefruit Dark","Grapefruit Light","Pitch Black"},new Runnable[]{()->theme(0),()->theme(1),()->theme(2)}));
        setting(settings,"Playback quality",this::qualityDialog);
        setting(settings,"About Grapefruit FAST",()->dialog("Grapefruit FAST","1.0 beta 1 · build 1\n\nFree live TV in the Grapefruit family. Native Android 4.2.2 and later. Fonts: Geist and Geist Pixel Triangle by Vercel (SIL OFL 1.1). GPL-3.0-or-later. ExoPlayer and Conscrypt: Apache-2.0.\n\nCopyright © 2026 MagicFlute LLC. Not affiliated with the listed broadcasters or platforms.",new String[]{"OK"},new Runnable[]{()->{}}));
        setting(settings,"Channels & availability",()->dialog("Channels & availability","Channels connect directly to broadcaster or distributor CDNs. No subscriptions, DRM bypass, location spoofing or ad removal. Availability depends on your region and each provider.\n\nThe channel guide lists channels; programme schedules are not supplied in beta 1.\n\nCP24 is excluded because a public video stream could not be obtained from cp24.com. JP5S hardware playback still needs device testing.",new String[]{"OK"},new Runnable[]{()->{}}));
        setting(settings,"Open source licences",this::licences);
        note("Streams include provider ads. Grapefruit adds no tracking SDK or extra advertisements. Playback stops when you leave the app.");
    }
    private void licences() {
        try {
            String text=Catalog.read(getAssets().open("fast-licences.txt"))+"\n\n"+Catalog.read(getAssets().open("fonts/Geist-OFL.txt"))+"\n\n"+Catalog.read(getAssets().open("GPL-3.0.txt"));
            dialog("Open source licences",text,new String[]{"OK"},new Runnable[]{()->{}});
        } catch(Exception e){dialog("Licences","See LICENSE and fast/README.md in the source repository.",new String[]{"OK"},new Runnable[]{()->{}});}
    }
    private void theme(int t){prefs.edit().putInt("theme",t).apply();build();}
    private void setting(LinearLayout parent,String label,Runnable click) {
        TextView row=b.button(label+"   ›",false,v->click.run());row.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(60));p.bottomMargin=dp(10);parent.addView(row,p);
    }
    private void note(String message) {TextView t=b.text(message,12,false);t.setTextColor(b.secondary);t.setPadding(dp(8),dp(12),dp(8),dp(14));page.addView(t);}
    private void dialog(String title,String message,String[] labels,Runnable[] actions) {
        final android.app.Dialog d=new android.app.Dialog(this);d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout card=b.column();card.setPadding(dp(22),dp(20),dp(22),dp(20));card.setBackground(b.shape(b.surface,24,true));
        TextView heading=b.text(title,23,true);card.addView(heading);
        ScrollView scroll=new ScrollView(this);LinearLayout contents=b.column();scroll.addView(contents);
        if(message!=null){TextView t=b.text(message,15,false);t.setPadding(0,dp(14),0,dp(14));contents.addView(t);}
        for(int i=0;i<labels.length;i++) {final int pos=i;TextView button=b.button(labels[i],false,v->{d.dismiss();actions[pos].run();});
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(8);contents.addView(button,p);}
        card.addView(scroll,new LinearLayout.LayoutParams(-1,-2));d.setContentView(card);
        Window w=d.getWindow();if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.setLayout(Math.min(dp(520),getResources().getDisplayMetrics().widthPixels-dp(32)),-2);}
        d.show();if(w!=null){w.setLayout(Math.min(dp(520),getResources().getDisplayMetrics().widthPixels-dp(32)),Math.min(dp(520),getResources().getDisplayMetrics().heightPixels-dp(48)));}
    }
    private void fullscreen(boolean on) {
        nav.setVisibility(on&&watching!=null?View.GONE:View.VISIBLE);
        if(on&&watching!=null)getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if(watching!=null)getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    @Override protected void onStart(){super.onStart();started=true;if(watching!=null&&playback==null&&pendingSurface!=null)startPlayer(pendingSurface);}
    @Override protected void onStop(){started=false;if(playback!=null){playback.release();playback=null;}super.onStop();}
    @Override protected void onResume(){super.onResume();if(started&&watching!=null&&playback==null)watchPage();}
    @Override public void onConfigurationChanged(Configuration c){super.onConfigurationChanged(c);build();}
    @Override public void onBackPressed(){if(fullScreen){fullScreen=false;watchPage();}else if(watching!=null){stop();build();}else if(!tab.equals("Live TV")){tab="Live TV";build();}else super.onBackPressed();}
    @Override public boolean onKeyDown(int key,KeyEvent event) {
        if(watching!=null&&(key==KeyEvent.KEYCODE_CHANNEL_UP||key==KeyEvent.KEYCODE_CHANNEL_DOWN)){
            List<Channel> lineup=visible.isEmpty()?channels:visible;int pos=lineup.indexOf(watching);
            if(!lineup.isEmpty())tune(lineup.get((pos+(key==KeyEvent.KEYCODE_CHANNEL_UP?1:lineup.size()-1))%lineup.size()));return true;
        }
        return super.onKeyDown(key,event);
    }
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putString("tab",tab);out.putString("category",category);out.putString("country",country);out.putString("query",query);out.putString("watching",watching==null?null:watching.id);out.putBoolean("fullscreen",fullScreen);}
    private static class AspectFrame extends FrameLayout {
        float ratio=16f/9;
        AspectFrame(Context c){super(c);}
        @Override protected void onMeasure(int widthSpec,int heightSpec) {
            int width=MeasureSpec.getSize(widthSpec),height=width*9/16;
            if(MeasureSpec.getMode(heightSpec)!=MeasureSpec.UNSPECIFIED)height=Math.min(height,MeasureSpec.getSize(heightSpec));
            setMeasuredDimension(width,height);
            int vw=width,vh=Math.round(width/ratio);
            if(vh>height){vh=height;vw=Math.round(height*ratio);}
            if(getChildCount()>0){View child=getChildAt(0);FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)child.getLayoutParams();lp.width=vw;lp.height=vh;
                child.measure(MeasureSpec.makeMeasureSpec(vw,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(vh,MeasureSpec.EXACTLY));}
        }
    }
}
