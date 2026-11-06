package com.grapefruitfast;

import android.app.Activity;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.*;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.*;
import android.text.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import android.view.ViewTreeObserver;
import com.grapefruitmusic.ui.Fonts;
import java.util.*;

/** Native touch + remote interface; no WebView, Play Services or account is required. */
public final class MainActivity extends Activity {
    static final String VERSION="2.0 · build 16";
    static final String COPYRIGHT="Copyright © 2026 MagicFlute LLC";
    static final String DEVELOPER="Haylie", TELEGRAM="afishinauto", PAYPAL="paypal.me/fishglub", SOURCE="github.com/hayliewordsman/grapefruitmusic";
    private static final String[] TABS={"Live TV","Favourites","Search","Settings"};
    private static final int[] TAB_ICONS={TabIcon.LIVE_TV,TabIcon.FAVOURITES,TabIcon.SEARCH,TabIcon.SETTINGS};
    private static final String[] GENRES={"All","News","Series","Reality","Lifestyle"};
    private static final String[] THEMES={"Grapefruit Dark","Grapefruit Light","Pitch Black","Victory Blue"};
    private static final String[] QUALITIES={"Auto","Data Saver","Compatibility"};
    /** null for Settings itself, or "about" for Settings › About Grapefruit TV. */
    private String settingsPage;
    private android.content.SharedPreferences prefs;
    private Brand b;
    private List<Channel> channels=Collections.emptyList();
    /** The built-in catalog, and the viewer's own sources (Settings › Your Sources). */
    private List<Channel> builtIn=Collections.emptyList();
    private final Map<String,List<Channel>> added=new LinkedHashMap<>();
    private UserSources sources;
    /** Sources refresh once a run (and on Refresh), in the background. */
    private static boolean sourcesRefreshed;
    private final Set<String> favourites=new HashSet<>();
    private final List<Channel> visible=new ArrayList<>();
    private String tab="Live TV", category="All", country="All", query="", error;
    private Channel watching;
    private LivePlayer playback;
    private TextView state, qualityLabel, count;
    private FrameLayout root, body;
    private LinearLayout shell, page, nav, bars;
    private ListView list;
    /** The page's scrolling view, padded at the bottom so it scrolls out from under the floating bars. */
    private ViewGroup scroller;
    private int scrollerPadding, barsInset;
    private ChannelAdapter adapter;
    private boolean fullScreen, started;
    private int listPosition;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        prefs=getSharedPreferences("fast",MODE_PRIVATE);
        com.grapefruitmusic.ui.UniversalTheme.sync(this,prefs,true,new int[]{0,1,2,3});
        favourites.addAll(prefs.getStringSet("favourites",Collections.<String>emptySet()));
        try { builtIn=Catalog.parse(Catalog.read(getAssets().open("channels.json"))); }
        catch(Exception e) {error="The channel list could not be opened.";}
        sources=new UserSources(this,prefs);
        for(UserSources.Source s:sources.list()) added.put(s.id,sources.cached(s));
        mergeChannels();
        if(saved!=null) {
            tab=saved.getString("tab","Live TV"); category=saved.getString("category","All");
            country=saved.getString("country","All");query=saved.getString("query","");
            fullScreen=saved.getBoolean("fullscreen",false);
            watching=find(saved.getString("watching"));settingsPage=saved.getString("settingsPage");
        }
        build();
        splash();
        if(!sourcesRefreshed){sourcesRefreshed=true;refreshSources(null);}
        offerCrashReport();
    }
    /** The list everything browses: built-in channels, then each of the viewer's sources' channels. */
    private void mergeChannels() {
        List<Channel> all=new ArrayList<>(builtIn);
        for(List<Channel> l:added.values()) all.addAll(l);
        channels=all;
    }
    /**
     * Fetches the viewer's sources again in the background (all, or one), then updates the lists. A source
     * that fails keeps its last channels; done (if given) gets the failure message, or null.
     */
    private void refreshSources(final UserSources.Source only) {
        final List<UserSources.Source> list=only==null?sources.list():Collections.singletonList(only);
        if(list.isEmpty())return;
        final Handler main=new Handler(Looper.getMainLooper());
        new Thread(()->{
            for(final UserSources.Source s:list){
                String failure=null;List<Channel> got=null;
                try{got=sources.refresh(s,LivePlayer.USER_AGENT);}catch(Exception e){failure=e.getMessage()==null?"Couldn't connect.":e.getMessage();}
                final List<Channel> result=got;final String why=failure;
                main.post(()->{
                    if(result!=null&&sourceExists(s.id)){added.put(s.id,result);mergeChannels();}
                    if(only!=null){
                        if(why!=null)ok("Couldn't refresh "+s.name,why);
                        else Toast.makeText(this,s.name+": "+result.size()+" channels",Toast.LENGTH_SHORT).show();
                    }
                    if(watching==null){if(tab.equals("Settings"))settings();else if(adapter!=null)filter();}
                });
            }
        },"sources").start();
    }
    private boolean sourceExists(String id){for(UserSources.Source s:sources.list())if(s.id.equals(id))return true;return false;}
    private Channel find(String id) {for(Channel c:channels) if(c.id.equals(id)) return c; return null;}
    private boolean wide() {Configuration c=getResources().getConfiguration();return c.screenWidthDp>=c.screenHeightDp*1.2f;} // clearly landscape: square screens (Clicks Communicator) use the portrait layout
    private int dp(float n) {return b.dp(n);}
    /** A short portrait screen (square ones like the Clicks Communicator): less floats over the page. */
    private boolean compact() {return !wide()&&getResources().getConfiguration().screenHeightDp<480;}

    private void build() {
        b=new Brand(this,prefs.getInt("theme",0));
        root=new FrameLayout(this);root.setBackgroundColor(b.bg);
        if(Build.VERSION.SDK_INT>=30) {
            // Android 15+ draws apps edge to edge: keep the page clear of the bars, the camera cutout and the
            // keyboard (adjustResize no longer shrinks the window there). Hidden bars (full screen) inset nothing.
            root.setOnApplyWindowInsetsListener((v,insets)->{
                android.graphics.Insets i=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()|WindowInsets.Type.ime());
                v.setPadding(i.left,i.top,i.right,i.bottom);
                return WindowInsets.CONSUMED;
            });
        } else root.setFitsSystemWindows(true);
        shell=wide()?b.row():b.column();root.addView(shell,new FrameLayout.LayoutParams(-1,-1));
        if(wide()) {nav=b.column();nav.setGravity(Gravity.CENTER_HORIZONTAL);nav.setPadding(dp(14),dp(18),dp(6),dp(16));shell.addView(nav,new LinearLayout.LayoutParams(dp(164),-1));}
        body=new FrameLayout(this);
        shell.addView(body,new LinearLayout.LayoutParams(wide()?0:-1,-1,1));
        // The bars float over the bottom of the page: the tabs (portrait) and Continue watching.
        bars=b.column();bars.setGravity(Gravity.CENTER_HORIZONTAL);bars.setClickable(false);
        // Every layout of the bars re-pads the page, even at the same height: a page built since the last
        // change (Settings, say) must get room to scroll its last row out from under them.
        bars.addOnLayoutChangeListener((v,l,t,r,btm,ol,ot,or,ob)->{
            barsInset=v.getVisibility()==View.VISIBLE?btm-t:0;v.post(this::inset);
        });
        if(!wide()) nav=b.row();
        navigation();setContentView(root);
        // After setContentView: on Android 11+ the window's insets controller needs its decor view.
        windowColours();
        if(watching!=null) watchPage();else if(tab.equals("Settings")) settings();else browse();
        fullscreen(fullScreen);
    }
    private void windowColours() {
        Window w=getWindow();w.setBackgroundDrawable(new ColorDrawable(b.bg));
        if(Build.VERSION.SDK_INT>=21){w.setStatusBarColor(b.bg);w.setNavigationBarColor(b.bg);}
        if(Build.VERSION.SDK_INT>=30){
            WindowInsetsController bars=w.getInsetsController();
            int dark=WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            if(bars!=null)bars.setSystemBarsAppearance(b.light?dark:0,dark);
        } else if(Build.VERSION.SDK_INT>=23){
            View d=w.getDecorView();int f=d.getSystemUiVisibility();
            int light=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|(Build.VERSION.SDK_INT>=26?View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR:0);
            d.setSystemUiVisibility(b.light?f|light:f&~light);
        }
    }
    /** The tabs: a floating pill along the bottom, or down the side beside the wordmark. */
    private void navigation() {
        nav.removeAllViews();
        LinearLayout pill;
        if(wide()) {
            ImageView icon=new ImageView(this);icon.setImageResource(com.grapefruitfast.R.mipmap.ic_launcher);
            nav.addView(icon,new LinearLayout.LayoutParams(-1,dp(72)));
            TextView name=b.wordmark(22,true);name.setPadding(0,dp(8),0,dp(18));nav.addView(name,new LinearLayout.LayoutParams(-1,-2));
            pill=b.column();nav.addView(pill,new LinearLayout.LayoutParams(-1,-2));
        } else {
            // A pill only as wide as its tabs, floating in the middle, as in Music.
            pill=b.row();nav.setGravity(Gravity.CENTER_HORIZONTAL);
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-2);p.bottomMargin=dp(12);p.topMargin=dp(6);
            nav.addView(pill,p);
        }
        pill.setPadding(dp(5),dp(5),dp(5),dp(5));pill.setBackground(b.shape(b.surface,wide()?30:Brand.PILL,true));b.lift(pill,6);
        for(int i=0;i<TABS.length;i++) {
            String label=TABS[i];boolean chosen=tab.equals(label);
            // Like Music: an icon for each tab; the chosen one on the accent pill with its name beside it.
            // Down the side there's room for every name.
            LinearLayout item=b.row();item.setGravity(wide()?Gravity.CENTER_VERTICAL|Gravity.START:Gravity.CENTER);
            item.setPadding(dp(14),0,dp(wide()?10:14),0);item.setFocusable(true);item.setClickable(true);item.setContentDescription(label);
            item.setBackground(b.interactive(chosen,Brand.PILL,0,false));
            item.setOnClickListener(v->{stop();tab=label;query="";listPosition=0;fullScreen=false;settingsPage=null;build();});
            TabIcon icon=new TabIcon(this,TAB_ICONS[i],chosen?b.onAccent:b.secondary);item.addView(icon,new LinearLayout.LayoutParams(dp(22),dp(22)));
            TextView name=b.text(label,13,true);name.setTextColor(chosen?b.onAccent:b.secondary);name.setPadding(dp(wide()?10:8),0,0,0);
            name.setSingleLine();name.setVisibility(chosen||wide()?View.VISIBLE:View.GONE);
            item.addView(name,new LinearLayout.LayoutParams(-2,-2));
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(wide()?-1:-2,dp(46));
            if(i>0){if(wide())p.topMargin=dp(4);else p.leftMargin=dp(2);}
            pill.addView(item,p);
        }
    }
    /** Fills the floating bars for this page: Continue watching (browsing pages), then the tabs (portrait). */
    private void floatBars(boolean resume) {
        bars.removeAllViews();
        if(bars.getParent()!=null)((ViewGroup)bars.getParent()).removeView(bars);
        if(nav.getParent()!=null&&!wide())((ViewGroup)nav.getParent()).removeView(nav);
        Channel last=resume&&prefs.getBoolean("continue",true)?find(prefs.getString("lastChannel","")):null;
        // On a short screen Continue watching sits at the top of the list instead (browse), scrolling away.
        if(last!=null&&!compact()) bars.addView(continuePill(last));
        if(!wide()) bars.addView(nav,new LinearLayout.LayoutParams(-1,-2));
        bars.setBackground(bars.getChildCount()>0?b.fade():null);
        bars.setPadding(0,bars.getChildCount()>0?dp(22):0,0,wide()&&last!=null?dp(14):0);
        bars.setVisibility(bars.getChildCount()>0?View.VISIBLE:View.GONE);
        if(bars.getChildCount()==0)barsInset=0;
        body.addView(bars,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
    }
    /** Like Music's mini player: the last channel on a floating pill. */
    private View continuePill(Channel last) {
        LinearLayout pill=b.row();pill.setPadding(dp(8),dp(8),dp(8),dp(8));pill.setFocusable(true);
        pill.setBackground(b.interactive(false,Brand.PILL,b.surface,true));b.lift(pill,6);
        pill.setOnClickListener(v->tune(last));
        pill.addView(logo(last,44),new LinearLayout.LayoutParams(dp(44),dp(44)));
        LinearLayout words=b.column();words.setPadding(dp(12),0,dp(8),0);
        words.addView(b.label("Continue watching",11,b.secondary));
        TextView name=b.text(last.name,15,true);name.setSingleLine();name.setEllipsize(TextUtils.TruncateAt.END);name.setPadding(0,dp(3),0,0);words.addView(name);
        pill.addView(words,new LinearLayout.LayoutParams(0,-2,1));
        TextView play=b.text("▶",15,true);play.setGravity(Gravity.CENTER);play.setTextColor(b.onAccent);play.setBackground(b.gradientCircle());
        pill.addView(play,new LinearLayout.LayoutParams(dp(44),dp(44)));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(wide()?dp(460):-1,-2);
        p.leftMargin=p.rightMargin=dp(16);
        pill.setLayoutParams(p);return pill;
    }
    private TextView logo(Channel c,int size) {
        String initials=c.initials();
        TextView logo=b.text(initials,initials.length()>2?Math.max(11,size/4):size*2/5,true);logo.setTextColor(b.onAccent);
        logo.setGravity(Gravity.CENTER);logo.setBackground(b.gradientCircle());return logo;
    }
    /** Pads the page's scrolling view so its last rows can scroll up out from under the floating bars. */
    private void inset() {
        if(scroller==null)return;
        scroller.setClipToPadding(false);
        int want=scrollerPadding+(bars!=null&&bars.getVisibility()==View.VISIBLE?barsInset:0);
        if(scroller.getPaddingBottom()!=want){
            scroller.setPadding(scroller.getPaddingLeft(),scroller.getPaddingTop(),scroller.getPaddingRight(),want);
            scroller.requestLayout();
        }
    }
    private void setScroller(ViewGroup v,int ownPadding) {scroller=v;scrollerPadding=ownPadding;inset();}
    private void newPage() {
        body.removeAllViews();scroller=null;page=b.column();page.setPadding(dp(18),dp(16),dp(18),0);body.addView(page,new FrameLayout.LayoutParams(-1,-1));
    }
    private void title(String title,String subtitle) {
        TextView heading=b.text(title,30,true);page.addView(heading);
        if(subtitle!=null) {TextView sub=b.label(subtitle,13,b.secondary);sub.setPadding(0,dp(6),0,dp(10));page.addView(sub);}
    }
    /**
     * A browsing page, as Music's (SectionMenu): the title scrolls with the list; the chips (and Search's
     * field) sit under it and, once the list scrolls them up to the top, stay there, floating over the list
     * on a fade from the page's colour as the channels pass under them.
     */
    private void browse() {
        body.removeAllViews();scroller=null;
        LinearLayout header=b.column();header.setPadding(0,dp(16),0,0);page=header;
        title(tab,tab.equals("Favourites")?"Your channels, in one place.":tab.equals("Search")?"Channels, cities and genres.":null);
        LinearLayout bar=b.column();bar.setPadding(0,0,0,dp(FADE_DP));
        if(tab.equals("Search")) {
            EditText search=new EditText(this);search.setSingleLine();search.setTextColor(b.text);search.setHintTextColor(b.secondary);
            search.setTypeface(Fonts.regular());search.setTextSize(16);search.setHint("Search channels, cities or genres");
            search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
            search.setPadding(dp(20),0,dp(20),0);search.setBackground(b.interactive(false,Brand.PILL));
            search.setText(query);search.setSelection(query.length());
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(48));p.leftMargin=p.rightMargin=dp(18);p.bottomMargin=dp(4);bar.addView(search,p);
            search.addTextChangedListener(new TextWatcher(){
                public void beforeTextChanged(CharSequence s,int start,int count,int after){}
                public void onTextChanged(CharSequence s,int start,int before,int count){query=s.toString();filter();}
                public void afterTextChanged(Editable e){}
            });
        }
        chips(bar);
        LinearLayout notes=b.column();
        if(tab.equals("Live TV")) tvAppsRow(notes);
        Channel last=compact()&&prefs.getBoolean("continue",true)?find(prefs.getString("lastChannel","")):null;
        if(last!=null){View pill=continuePill(last);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(10);notes.addView(pill,lp);}
        count=b.label("",12,b.secondary);count.setPadding(dp(4),dp(2),0,dp(10));notes.addView(count);
        if(error!=null){TextView t=b.label(error,12,b.secondary);t.setPadding(dp(4),0,dp(4),dp(10));notes.addView(t);}
        // The slot in the list where the chips sit until they float: as tall as the bar without its fade.
        bar.measure(View.MeasureSpec.makeMeasureSpec(getResources().getDisplayMetrics().widthPixels,View.MeasureSpec.AT_MOST),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        View slot=new View(this);slot.setLayoutParams(new AbsListView.LayoutParams(-1,Math.max(0,bar.getMeasuredHeight()-dp(FADE_DP))));
        FloatingBar frame=new FloatingBar(bar,header,slot);
        body.addView(frame,new FrameLayout.LayoutParams(-1,-1));
        addList(frame,header,slot,notes);
        frame.addView(bar,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));
        floatBars(true);
    }
    /**
     * Your TV Apps: the streaming and TV-provider apps installed here, on pills that open them, and Add App
     * for any other. Grapefruit TV only opens them; their sign-ins and streams stay in their own apps.
     */
    private void tvAppsRow(LinearLayout parent) {
        TextView label=b.text("Your TV Apps",15,true);label.setTextColor(b.accent);label.setPadding(dp(4),dp(2),0,dp(8));parent.addView(label);
        HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.setHorizontalScrollBarEnabled(false);scroll.setClipToPadding(false);
        LinearLayout row=b.row();row.setPadding(0,0,dp(8),dp(12));scroll.addView(row);
        for(final TvApps.App app:TvApps.row(this,prefs)) {
            LinearLayout pill=b.row();pill.setPadding(dp(6),dp(6),dp(16),dp(6));pill.setFocusable(true);pill.setClickable(true);
            pill.setBackground(b.interactive(false,Brand.PILL));pill.setContentDescription("Open "+app.label);
            ImageView icon=new ImageView(this);icon.setImageDrawable(app.icon);pill.addView(icon,new LinearLayout.LayoutParams(dp(32),dp(32)));
            TextView name=b.text(app.label,14,true);name.setPadding(dp(10),0,0,0);name.setSingleLine();pill.addView(name);
            pill.setOnClickListener(v->{if(!TvApps.open(this,app.pkg))ok(app.label,"That app can't be opened from here.");});
            pill.setOnLongClickListener(v->{dialog(app.label,null,new String[]{"Open","Remove from Your TV Apps"},-1,
                new Runnable[]{()->TvApps.open(this,app.pkg),()->{TvApps.remove(prefs,app.pkg);browse();}});return true;});
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(44));lp.rightMargin=dp(8);row.addView(pill,lp);
        }
        TextView add=b.button("+  Add App",false,v->addTvApp());add.setTextSize(13);
        row.addView(add,new LinearLayout.LayoutParams(-2,dp(44)));
        parent.addView(scroll,new LinearLayout.LayoutParams(-1,-2));
    }
    private void addTvApp() {
        final List<TvApps.App> all=TvApps.installed(this);
        if(all.isEmpty()){ok("Add App","No other apps are installed.");return;}
        String[] names=new String[all.size()];Runnable[] picks=new Runnable[all.size()];
        for(int i=0;i<all.size();i++){final TvApps.App a=all.get(i);names[i]=a.label;picks[i]=()->{TvApps.pin(prefs,a.pkg);browse();};}
        dialog("Add to Your TV Apps","Pick an app to keep on the Live TV page. Grapefruit TV opens it; you watch and sign in there.",names,-1,picks);
    }
    /** How far the list fades out under the floating chips, as Music's (dp). */
    private static final int FADE_DP=20;
    /**
     * The list with the chip bar over it, kept where its slot is in the list, or at the top once the slot has
     * scrolled past; the fade behind the chips comes in as the page starts to pass under them.
     */
    private final class FloatingBar extends FrameLayout implements ViewTreeObserver.OnPreDrawListener {
        private final View bar, header, slot;private final android.graphics.drawable.Drawable fade;private final int fadeIn;private int alpha=-1;
        FloatingBar(View bar,View header,View slot){
            super(MainActivity.this);this.bar=bar;this.header=header;this.slot=slot;
            fade=b.topFade(dp(FADE_DP));fade.setAlpha(0);alpha=0;bar.setBackground(fade);fadeIn=dp(12);
        }
        @Override protected void onAttachedToWindow(){super.onAttachedToWindow();getViewTreeObserver().addOnPreDrawListener(this);}
        @Override protected void onDetachedFromWindow(){getViewTreeObserver().removeOnPreDrawListener(this);super.onDetachedFromWindow();}
        @Override public boolean onPreDraw(){
            if(list==null||list.getParent()!=this)return true;
            int y;
            if(list.indexOfChild(slot)>=0)y=list.getTop()+slot.getTop();
            else if(list.getFirstVisiblePosition()>1)y=-fadeIn;
            else y=list.getTop()+header.getHeight();
            float top=Math.max(0,y);
            if(bar.getTranslationY()!=top)bar.setTranslationY(top);
            int a=y>=0?0:Math.min(255,-y*255/fadeIn);
            if(a!=alpha){alpha=a;fade.setAlpha(a);}
            return true;
        }
    }
    /**
     * Genres and regions share one row that scrolls edge to edge. Genre chips pick one; a region chip
     * narrows to that region and, tapped again, goes back to all of them.
     */
    private void chips(LinearLayout parent) {
        HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        LinearLayout row=b.row();row.setPadding(dp(18),dp(6),dp(10),dp(6));scroll.addView(row);
        for(String label:GENRES) row.addView(chip(label,label.equals(category),()->category=label));
        // The viewer's own sources' channels, under one chip.
        if(!added.isEmpty()) row.addView(chip("Added",category.equals("Added"),()->category="Added"));
        View divider=new View(this);divider.setBackgroundColor(b.outline);
        LinearLayout.LayoutParams d=new LinearLayout.LayoutParams(Math.max(1,dp(1)),dp(24));d.rightMargin=dp(8);row.addView(divider,d);
        for(String label:Channel.REGIONS)
            row.addView(chip(label,label.equals(country),()->country=label.equals(country)?"All":label));
        parent.addView(scroll,new LinearLayout.LayoutParams(-1,-2));
    }
    private View chip(String label,boolean chosen,Runnable pick) {
        TextView chip=b.button(label,chosen,v->{pick.run();listPosition=0;if(watching!=null)watchPage();else browse();});
        chip.setTextSize(13);chip.setMinHeight(dp(40));chip.setPadding(dp(16),0,dp(16),0);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(40));p.rightMargin=dp(8);chip.setLayoutParams(p);
        return chip;
    }
    /** The channel list; headers (title, chip slot, notes) scroll with it. */
    private void addList(ViewGroup parent,View... headers) {
        list=new ListView(this);list.setDivider(null);list.setSelector(android.R.color.transparent);
        list.setCacheColorHint(Color.TRANSPARENT);list.setVerticalScrollBarEnabled(false);
        list.setItemsCanFocus(false);list.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        // Headers go in before the adapter (Android 4.2 takes them only then).
        for(View h:headers)list.addHeaderView(h,null,false);
        if(headers.length>0)list.setPadding(dp(18),0,dp(18),0);
        adapter=new ChannelAdapter();list.setAdapter(adapter);
        list.setOnItemClickListener((p,v,pos,id)->{
            Channel c=at(pos);if(c==null)return;
            ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(v.getWindowToken(),0);
            listPosition=pos-list.getHeaderViewsCount();tune(c);
        });
        list.setOnItemLongClickListener((p,v,pos,id)->{Channel c=at(pos);if(c!=null)details(c);return c!=null;});
        list.setOnKeyListener((v,key,event)->{
            if(key==KeyEvent.KEYCODE_MENU && event.getAction()==KeyEvent.ACTION_UP && at(list.getSelectedItemPosition())!=null) {
                details(at(list.getSelectedItemPosition()));return true;
            } return false;
        });
        if(parent instanceof LinearLayout)parent.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        else parent.addView(list,new FrameLayout.LayoutParams(-1,-1));
        filter();if(listPosition>0)list.setSelection(listPosition+list.getHeaderViewsCount());
        setScroller(list,dp(10));
    }
    /** The channel at a list position (headers count in positions), or null for a header. */
    private Channel at(int pos){int i=pos-(list==null?0:list.getHeaderViewsCount());return i>=0&&i<visible.size()?visible.get(i):null;}
    private void filter() {
        visible.clear();
        for(Channel c:channels) {
            if(tab.equals("Favourites")&&!favourites.contains(c.id))continue;
            if(!category.equals("All")&&!category.equals(c.category))continue;
            if(!country.equals("All")&&!country.equals(c.region()))continue;
            if(!c.matches(query))continue;
            visible.add(c);
        }
        if(adapter!=null)adapter.notifyDataSetChanged();
        if(count==null)return;
        if(!visible.isEmpty())count.setText(visible.size()+(visible.size()==1?" channel":" channels")+" · tap to watch · hold for options");
        else if(tab.equals("Favourites")&&favourites.isEmpty())count.setText("No favourites yet. Hold a channel, or press Menu, to add one.");
        else if(tab.equals("Search")&&query.trim().length()>0)count.setText("Nothing found for “"+query.trim()+"”.");
        else count.setText("No channels match. Try another filter.");
    }
    private class ChannelAdapter extends BaseAdapter {
        public int getCount(){return visible.size();}
        public Object getItem(int pos){return visible.get(pos);}
        public long getItemId(int pos){return pos;}
        public View getView(int pos,View reuse,ViewGroup parent) {
            Channel c=visible.get(pos);
            Holder h=reuse==null?null:(Holder)reuse.getTag();
            if(h==null)h=new Holder();
            String initials=c.initials();
            h.logo.setText(initials);h.logo.setTextSize(initials.length()>2?13:20);
            h.name.setText(c.name);
            h.meta.setText((favourites.contains(c.id)?"★  ":"")+(c.added()?c.source+(c.description.isEmpty()?"":"  ·  "+c.description):c.region()+"  ·  "+c.category));
            boolean live=watching!=null&&watching.id.equals(c.id);
            h.live.setVisibility(live?View.VISIBLE:View.GONE);h.more.setVisibility(live?View.GONE:View.VISIBLE);
            return h.wrapper;
        }
    }
    private final class Holder {
        final FrameLayout wrapper=new FrameLayout(MainActivity.this);
        final TextView logo, name, meta, live, more;
        Holder() {
            // The gap between rows is the wrapper's padding; the row itself takes the list's pressed and
            // selected states, so the focus ring is drawn on the pill and never on the gap below it.
            wrapper.setPadding(0,0,0,dp(8));wrapper.setTag(this);
            LinearLayout row=b.row();row.setPadding(dp(8),dp(8),dp(18),dp(8));row.setDuplicateParentStateEnabled(true);
            row.setBackground(b.interactive(false,Brand.PILL));
            logo=b.text("",20,true);logo.setTextColor(b.onAccent);logo.setGravity(Gravity.CENTER);logo.setBackground(b.gradientCircle());
            row.addView(logo,new LinearLayout.LayoutParams(dp(52),dp(52)));
            LinearLayout words=b.column();words.setPadding(dp(14),0,dp(8),0);
            name=b.text("",16,true);name.setMaxLines(2);name.setEllipsize(TextUtils.TruncateAt.END);words.addView(name);
            meta=b.label("",12,b.secondary);meta.setPadding(0,dp(4),0,0);words.addView(meta);
            row.addView(words,new LinearLayout.LayoutParams(0,-2,1));
            live=b.text("LIVE",11,true);live.setTextColor(b.onAccent);live.setGravity(Gravity.CENTER);live.setPadding(dp(10),0,dp(10),0);
            live.setBackground(b.gradient(Brand.PILL));row.addView(live,new LinearLayout.LayoutParams(-2,dp(24)));
            more=b.text("›",22,true);more.setTextColor(b.accent);row.addView(more);
            wrapper.addView(row,new FrameLayout.LayoutParams(-1,-2));
        }
    }
    private void tune(Channel channel) {
        stop();watching=channel;captionsAvailable=false;prefs.edit().putString("lastChannel",channel.id).apply();
        watchPage();
    }
    private void watchPage() {
        body.removeAllViews();scroller=null;
        root.setBackgroundColor(fullScreen?Color.BLACK:b.bg); // full screen is the picture on black, edge to edge
        page=b.column();page.setPadding(dp(fullScreen?0:14),dp(fullScreen?0:10),dp(fullScreen?0:14),0);body.addView(page,new FrameLayout.LayoutParams(-1,-1));
        if(!fullScreen) {
            LinearLayout header=b.row();
            header.addView(b.round("‹",v->{stop();fullScreen=false;build();}),new LinearLayout.LayoutParams(dp(44),dp(44)));
            TextView name=b.text(watching.name,18,true);name.setPadding(dp(12),0,dp(12),0);name.setMaxLines(2);name.setEllipsize(TextUtils.TruncateAt.END);
            header.addView(name,new LinearLayout.LayoutParams(0,-2,1));
            TextView star=b.round(favourites.contains(watching.id)?"★":"☆",v->{favourite(watching);watchPage();});
            if(favourites.contains(watching.id))star.setTextColor(b.accent);
            header.addView(star,new LinearLayout.LayoutParams(dp(44),dp(44)));
            LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);hp.bottomMargin=dp(10);page.addView(header,hp);
        }
        LinearLayout content=wide()&&!fullScreen?b.row():b.column();page.addView(content,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout stage=b.column();stage.setGravity(Gravity.CENTER_VERTICAL);
        if(fullScreen)content.addView(stage,new LinearLayout.LayoutParams(-1,0,1));
        else if(wide())content.addView(stage,new LinearLayout.LayoutParams(0,-1,1.5f));
        else content.addView(stage,new LinearLayout.LayoutParams(-1,-2));
        AspectFrame video=new AspectFrame(this);currentVideo=video;video.setBackgroundColor(Color.BLACK);
        SurfaceView surface=new SurfaceView(this);video.addView(surface,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));
        if(!fullScreen)video.addView(new Corners(this,b.bg,dp(22)),new FrameLayout.LayoutParams(-1,-1));
        // Captions, drawn over the picture: white on a dark pill, near the bottom, as Android draws them.
        FrameLayout captionLayer=new FrameLayout(this);
        caption=b.text("",captionSize(),false);caption.setTextColor(Color.WHITE);caption.setGravity(Gravity.CENTER);
        caption.setTypeface(Fonts.medium());caption.setLineSpacing(dp(2),1);caption.setPadding(dp(10),dp(5),dp(10),dp(5));
        caption.setBackground(b.shape(0xC0000000,12,false));caption.setVisibility(View.GONE);
        FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
        cp.bottomMargin=dp(fullScreen?28:12);cp.leftMargin=cp.rightMargin=dp(16);captionLayer.addView(caption,cp);
        video.addView(captionLayer,new FrameLayout.LayoutParams(-1,-1));
        // When nothing plays: a notice on the picture, saying whether it's the viewer's location.
        notice=b.column();notice.setGravity(Gravity.CENTER);notice.setPadding(dp(20),dp(12),dp(20),dp(12));
        notice.setBackgroundColor(0xD9000000);notice.setVisibility(View.GONE);notice.setClickable(false);
        noticeTitle=b.text("",fullScreen?20:16,true);noticeTitle.setTextColor(Color.WHITE);noticeTitle.setGravity(Gravity.CENTER);
        noticeDetail=b.label("",fullScreen?15:13,0xFFD9C9C4);noticeDetail.setGravity(Gravity.CENTER);noticeDetail.setPadding(0,dp(6),0,0);noticeDetail.setLineSpacing(dp(2),1);
        notice.addView(noticeTitle,new LinearLayout.LayoutParams(-1,-2));notice.addView(noticeDetail,new LinearLayout.LayoutParams(-1,-2));
        video.addView(notice,new FrameLayout.LayoutParams(-1,-1));
        if(compact()&&!fullScreen) {
            // A short screen: the picture at most 40% of the height (16:9, centred), leaving room for the lineup.
            int h=(int)(getResources().getDisplayMetrics().heightPixels*0.4f);
            LinearLayout.LayoutParams vp=new LinearLayout.LayoutParams(Math.min(h*16/9,getResources().getDisplayMetrics().widthPixels-dp(28)),h);
            vp.gravity=Gravity.CENTER_HORIZONTAL;stage.addView(video,vp);
        } else stage.addView(video,new LinearLayout.LayoutParams(-1,wide()||fullScreen?0:-2,wide()||fullScreen?1:0));
        LinearLayout info=b.row();info.setPadding(dp(4),dp(12),dp(4),dp(10));
        state=b.text("",12,true);info.addView(state,new LinearLayout.LayoutParams(-2,-2));
        qualityLabel=b.label("Auto · adapts to your connection",12,b.secondary);qualityLabel.setSingleLine();qualityLabel.setEllipsize(TextUtils.TruncateAt.END);qualityLabel.setPadding(dp(10),0,0,0);
        info.addView(qualityLabel,new LinearLayout.LayoutParams(0,-2,1));
        status("Connecting…");
        if(!fullScreen)stage.addView(info);
        HorizontalScrollView actionsScroll=new HorizontalScrollView(this);actionsScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout actions=b.row();actionsScroll.addView(actions);
        action(actions,"Retry",()->tune(watching));
        action(actions,"Quality",this::qualityDialog);
        // Only when the stream offers captions (the player says once it has read the stream's tracks).
        captionsButton=b.button("Captions",captionsOn(),v->{boolean on=!captionsOn();prefs.edit().putBoolean("captions",on).apply();applyCaptions();});
        captionsButton.setVisibility(captionsAvailable?View.VISIBLE:View.GONE);
        LinearLayout.LayoutParams cbp=new LinearLayout.LayoutParams(-2,dp(44));cbp.rightMargin=dp(6);actions.addView(captionsButton,cbp);
        action(actions,fullScreen?"Exit full screen":"Full screen",()->setFullScreen(!fullScreen));
        if(!watching.website.isEmpty()) action(actions,"Official site",()->website(watching));
        if(fullScreen) {
            // Over the picture, along the bottom: a way back out without a remote's Back key.
            FrameLayout.LayoutParams ap=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);ap.bottomMargin=dp(16);
            actions.setPadding(dp(6),dp(6),0,dp(6));actions.setBackground(b.shape(b.surface&0xe6ffffff,Brand.PILL,true));
            body.addView(actionsScroll,ap);actionsScroll.setVisibility(View.GONE);
            video.setOnClickListener(v->actionsScroll.setVisibility(actionsScroll.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE));
        } else {
            // Edge to edge, like the chips: the last pill scrolls in from the screen's edge rather than being cut short.
            actionsScroll.setClipToPadding(false);actions.setPadding(wide()?0:dp(14),0,dp(8),0);
            LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,-2);if(!wide())ap.leftMargin=ap.rightMargin=-dp(14);
            stage.addView(actionsScroll,ap);
        }
        if(!fullScreen&&!compact()){TextView description=b.label(watching.description,13,b.secondary);description.setLineSpacing(dp(2),1);description.setPadding(dp(4),dp(12),dp(4),dp(12));stage.addView(description);}
        LinearLayout lineup=b.column();lineup.setPadding(wide()?dp(16):0,wide()?0:dp(4),0,0);
        if(!fullScreen)content.addView(lineup,new LinearLayout.LayoutParams(wide()?0:-1,wide()?-1:0,1));
        count=null;addList(lineup);
        floatBars(false);
        // A short screen keeps the watch page for the picture, its controls and the lineup (Back has its button).
        if(compact()){bars.setVisibility(View.GONE);barsInset=0;}
        if(playback==null && started)startPlayer(surface);else if(playback!=null){playback.attach(surface);pendingSurface=surface;}
        else pendingSurface=surface;
        fullscreen(fullScreen);
    }
    /** LIVE on an accent pill; connecting and errors as plain accent text. */
    private void status(String message) {
        if(state==null)return;
        boolean live=message.equals("LIVE");
        state.setText(live?"●  LIVE":message);
        state.setTextColor(live?b.onAccent:b.accent);
        state.setBackground(live?b.gradient(Brand.PILL):null);
        state.setPadding(live?dp(10):0,live?dp(5):0,live?dp(10):0,live?dp(5):0);
        if(qualityLabel!=null)qualityLabel.setVisibility(message.length()>28?View.GONE:View.VISIBLE);
    }
    private TextView caption, captionsButton, noticeTitle, noticeDetail;
    private LinearLayout notice;
    /**
     * The player box's notice when no source played. location: every source's provider refused it for
     * where the viewer is (a 403 or 451), so the channel isn't offered there; otherwise a passing fault.
     */
    void onUnavailable(boolean location) {
        if(notice==null||watching==null) return;
        if(watching.added()){
            noticeTitle.setText(location?watching.source+" refused this channel":"This channel isn't playing right now");
            noticeDetail.setText(location?"Check your subscription or login with "+watching.source+", or whether it's offered where you are."
                :"Try Retry, or another channel. "+watching.source+" may be busy, or the channel may be off the air.");
            notice.setVisibility(View.VISIBLE);if(caption!=null)caption.setVisibility(View.GONE);
            return;
        }
        noticeTitle.setText(location?"Not available where you are":"This channel isn't playing right now");
        String where=watching.availability.isEmpty()?"":"\nOffered in: "+watching.availability+".";
        noticeDetail.setText(location?watching.name+"'s provider doesn't offer it in your location."+where
            :"Try Retry, or another channel. It may be a passing problem with the stream or your connection.");
        notice.setVisibility(View.VISIBLE);
        if(caption!=null)caption.setVisibility(View.GONE);
    }
    /** Whether the playing stream offers captions (set by the player). */
    private boolean captionsAvailable;
    /** Captions on? The viewer's choice; until they choose, Android's own captions setting (4.4+). */
    boolean captionsOn() {
        if(prefs.contains("captions"))return prefs.getBoolean("captions",false);
        if(Build.VERSION.SDK_INT>=19){
            android.view.accessibility.CaptioningManager m=(android.view.accessibility.CaptioningManager)getSystemService(CAPTIONING_SERVICE);
            return m!=null&&m.isEnabled();
        }
        return false;
    }
    /** Caption text size (sp), scaled by Android's caption size setting (4.4+). */
    private int captionSize() {
        float scale=1;
        if(Build.VERSION.SDK_INT>=19){
            android.view.accessibility.CaptioningManager m=(android.view.accessibility.CaptioningManager)getSystemService(CAPTIONING_SERVICE);
            if(m!=null)scale=m.getFontScale();
        }
        return Math.round((fullScreen?22:15)*scale);
    }
    /** Tells the player, and the Captions pill, what the viewer chose. */
    private void applyCaptions() {
        boolean on=captionsOn();
        if(playback!=null)playback.setCaptions(on,Locale.getDefault().getLanguage());
        if(captionsButton!=null){captionsButton.setBackground(b.interactive(on,Brand.PILL));captionsButton.setTextColor(on?b.onAccent:b.text);
            captionsButton.setContentDescription(on?"Captions on":"Captions off");}
        if(!on&&caption!=null)caption.setVisibility(View.GONE);
    }
    void onCaptionsAvailable(boolean available) {
        captionsAvailable=available;
        if(captionsButton!=null)captionsButton.setVisibility(available?View.VISIBLE:View.GONE);
    }
    void onCaptions(CharSequence text) {
        if(caption==null)return;
        boolean show=captionsOn()&&text!=null&&text.length()>0;
        caption.setText(show?text:"");caption.setVisibility(show?View.VISIBLE:View.GONE);
    }
    private SurfaceView pendingSurface;
    private AspectFrame currentVideo;
    private void startPlayer(SurfaceView surface) {
        playback=new LivePlayer(this,new LivePlayer.Listener(){
            public void status(String message){MainActivity.this.status(message);}
            public void format(String message){if(qualityLabel!=null)qualityLabel.setText(message);}
            public void captionsAvailable(boolean available){onCaptionsAvailable(available);}
            public void captions(CharSequence text){onCaptions(text);}
            public void unavailable(boolean location){onUnavailable(location);}
            public void aspect(float ratio){if(currentVideo!=null&&Math.abs(currentVideo.ratio-ratio)>0.001f){currentVideo.ratio=ratio;currentVideo.requestLayout();}}
        },prefs.getInt("quality",0));pendingSurface=surface;applyCaptions();playback.attach(surface);playback.tune(watching);
    }
    private void action(LinearLayout row,String label,Runnable action) {
        TextView button=b.button(label,false,v->action.run());
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(44));p.rightMargin=dp(6);row.addView(button,p);
    }
    private void favourite(Channel c) {
        if(!favourites.remove(c.id))favourites.add(c.id);
        prefs.edit().putStringSet("favourites",new HashSet<>(favourites)).apply();
        if(adapter!=null)adapter.notifyDataSetChanged();
    }
    private void stop() {if(playback!=null){playback.release();playback=null;}watching=null;pendingSurface=null;}
    private void details(Channel c) {
        if(c.added()){
            dialog(c.name,(c.description.isEmpty()?"":c.description+"\n\n")+"From "+c.source+", one of your sources.",
                new String[]{"Watch live",favourites.contains(c.id)?"Remove favourite":"Add favourite"},0,
                new Runnable[]{()->tune(c),()->{favourite(c);filter();if(watching!=null&&watching.id.equals(c.id))watchPage();}});
            return;
        }
        dialog(c.name,c.description+"\n\n"+c.countryName()+" · "+c.category
            +(c.availability.isEmpty()?"":"\nWatch from: "+c.availability)
            +"\nChannels may be region restricted. Provider advertisements remain part of the stream.",
            new String[]{"Watch live",favourites.contains(c.id)?"Remove favourite":"Add favourite","Official site"},0,
            new Runnable[]{()->tune(c),()->{favourite(c);filter();if(watching!=null&&watching.id.equals(c.id))watchPage();},()->website(c)});
    }
    private void website(Channel c) {
        try {startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(c.website)));}
        catch(ActivityNotFoundException e){ok("No browser installed","This device has no app to open "+c.website);}
    }
    private void qualityDialog() {
        dialog("Playback quality","Auto changes resolution as bandwidth changes. On Android 4.x it stays at 720p or below and 30 fps. Your device still needs a supported video decoder.",
            new String[]{"Auto · adaptive","Data saver · up to 480p","Compatibility · up to 360p"},prefs.getInt("quality",0),
            new Runnable[]{()->quality(0),()->quality(1),()->quality(2)});
    }
    private void quality(int value) {
        prefs.edit().putInt("quality",value).apply();if(playback!=null)playback.setQuality(value);
        if(watching==null&&tab.equals("Settings")&&settingsPage==null)settings();
    }
    /**
     * Settings, as Music's: a big title, then grouped sections (accent titles over rounded rows, a note
     * under each), on a readable width in the middle of a wide screen.
     */
    private void settings() {
        if("about".equals(settingsPage)){about();return;}
        LinearLayout g=groupedPage();
        TextView heading=b.text("Settings",30,true);heading.setPadding(dp(18),dp(16),dp(18),dp(4));g.addView(heading);
        int theme=prefs.getInt("theme",0);
        section(g,"Appearance");
        value(g,"Theme",THEMES[theme],()->dialog("Theme",null,THEMES,theme,new Runnable[]{()->theme(0),()->theme(1),()->theme(2)}));

        section(g,"Watching");
        value(g,"Playback Quality",QUALITIES[prefs.getInt("quality",0)],this::qualityDialog);
        toggle(g,"Continue Watching",prefs.getBoolean("continue",true),on->{prefs.edit().putBoolean("continue",on).apply();floatBars(true);});
        toggle(g,"Captions",captionsOn(),on->{prefs.edit().putBoolean("captions",on).apply();applyCaptions();});
        toggle(g,"Keep Screen On",prefs.getBoolean("keepScreenOn",true),on->prefs.edit().putBoolean("keepScreenOn",on).apply());
        footer(g,"Auto changes resolution as your connection changes. Continue Watching keeps your last channel a tap away above the tabs. Captions show when a channel provides them; until you choose, they follow Android's caption setting. Keep Screen On stops the screen sleeping while a channel plays.");

        section(g,"Channels");
        value(g,"Channels & Availability",channels.size()+" channels",()->ok("Channels & Availability",
            "Grapefruit TV brings live TV together in one place.\n\n"
            +"Built-in channels: free live channels from broadcasters and their official distributors, listed wherever you are. Some are offered only in certain countries; if a channel's provider doesn't offer it where you are, the player says so. Their own advertisements play as usual.\n\n"
            +"Your sources: add an IPTV service you subscribe to, with its M3U playlist address or its server, username and password (Settings › Your Sources). Its channels appear under Added. Grapefruit TV doesn't provide, sell or recommend any service. Only add services you're entitled to use.\n\n"
            +"Your TV Apps: streaming and TV-provider apps installed on this device (Crave, Sling, Fibe TV and others) sit on the Live TV page and open with a tap. You sign in and watch in those apps: their live TV is protected, so it can't play in Grapefruit TV.\n\n"
            +"Channel names only; no programme guide."));
        int saved=favourites.size();
        value(g,"Favourites",saved==0?"None":saved==1?"1 channel":saved+" channels",null);
        if(saved>0)action(g,"Clear Favourites",true,()->dialog("Clear Favourites?","Your "+(saved==1?"favourite channel":saved+" favourite channels")+" will be removed from Favourites.",
            new String[]{"Clear Favourites","Cancel"},-1,new Runnable[]{()->{favourites.clear();prefs.edit().putStringSet("favourites",new HashSet<String>()).apply();settings();},()->{}}));
        footer(g,"Streams include provider ads. Grapefruit adds no tracking SDK or extra advertisements. Playback stops when you leave the app.");

        section(g,"Your Sources");
        for(final UserSources.Source src:sources.list()) {
            List<Channel> got=added.get(src.id);int n=got==null?0:got.size();
            value(g,src.name,(UserSources.LOGIN.equals(src.kind)?"Login · ":"Playlist · ")+(n==1?"1 channel":n+" channels"),()->dialog(src.name,
                (UserSources.LOGIN.equals(src.kind)?"Server: "+src.address+"\nUsername: "+src.username:"Playlist: "+src.address),
                new String[]{"Refresh","Remove"},-1,new Runnable[]{()->refreshSources(src),()->dialog("Remove "+src.name+"?","Its channels will be removed from Grapefruit TV, and any of them in Favourites.",
                    new String[]{"Remove","Cancel"},-1,new Runnable[]{()->{sources.remove(src.id);added.remove(src.id);mergeChannels();settings();},()->{}})}));
        }
        action(g,"Add a Playlist (M3U)…",false,()->form("Add a Playlist","Your IPTV service's M3U or M3U8 playlist address.",
            new String[]{"Name","Playlist address (http:// or https://)"},new boolean[]{false,false},new String[]{"My TV",""},v->addSource(UserSources.M3U,v[0],v[1],"","")));
        action(g,"Add a TV Login…",false,()->form("Add a TV Login","Your IPTV service's server address, username and password.",
            new String[]{"Name","Server (e.g. http://example.com:8080)","Username","Password"},new boolean[]{false,false,false,true},new String[]{"My TV","","",""},
            v->addSource(UserSources.LOGIN,v[0],v[1],v[2],v[3])));
        footer(g,"Add an IPTV service you subscribe to. Grapefruit TV doesn't provide, sell or recommend any service: only add ones you're entitled to use. Channel names only; no programme guide. Logins are kept in this app's private storage on this device.");

        section(g,"About");
        value(g,"About Grapefruit TV","Version "+versionName(),()->{settingsPage="about";about();});
        floatBars(true);backCallback();
    }
    /** Settings › About Grapefruit TV: the logo and wordmark, then the licence, developer and credits. */
    private void about() {
        LinearLayout g=groupedPage();
        // Music's About page (AboutFragment), for Grapefruit TV: Music's header, with its round back button...
        backHeader(g,null);
        // ...the icon at exactly 88dp, the wordmark in Geist Pixel Triangle on one line, the version and copyright.
        LinearLayout hero=b.column();hero.setGravity(Gravity.CENTER_HORIZONTAL);hero.setPadding(0,dp(8),0,dp(8));
        ImageView icon=new ImageView(this);icon.setImageResource(R.drawable.ic_about);
        hero.addView(icon,new LinearLayout.LayoutParams(dp(88),dp(88)));
        TextView name=b.wordmark(24,false);name.setPadding(dp(16),dp(14),dp(16),dp(6));
        hero.addView(name,new LinearLayout.LayoutParams(-1,-2));
        TextView version=b.label("Version "+versionName(),15,b.secondary);version.setGravity(Gravity.CENTER);hero.addView(version,new LinearLayout.LayoutParams(-1,-2));
        TextView copyright=b.label(COPYRIGHT,13,b.secondary);copyright.setGravity(Gravity.CENTER);copyright.setPadding(0,dp(6),0,0);hero.addView(copyright,new LinearLayout.LayoutParams(-1,-2));
        g.addView(hero);

        section(g,"License");
        link(g,"GNU GPL","Version 3 or later","https://www.gnu.org/licenses/gpl-3.0.html");
        link(g,"Source Code",SOURCE,"https://"+SOURCE);
        footer(g,"Grapefruit TV is free software: you can share and change it under the GNU General Public License, version 3 or any later version. It comes with no warranty.");

        section(g,"Developer");
        value(g,"Developer",DEVELOPER,null);
        link(g,"Telegram","@"+TELEGRAM,"https://t.me/"+TELEGRAM);
        link(g,"Donate (PayPal)",PAYPAL,"https://"+PAYPAL);
        footer(g,"Tap to open, or touch and hold to copy.");

        section(g,"Acknowledgements");
        value(g,"ExoPlayer","Apache License 2.0",null);
        value(g,"Conscrypt","Apache License 2.0",null);
        value(g,"Geist & Geist Pixel","SIL Open Font License 1.1",null);
        value(g,"Open Source Licences",null,this::licences);
        footer(g,"The typeface is Geist, and the wordmark's lettering Geist Pixel, by Vercel (vercel.com/font). ExoPlayer plays the live streams and Conscrypt provides secure connections on older Android versions. Not affiliated with the listed broadcasters or platforms.");
        floatBars(true);backCallback();
    }
    /** Music's sub-page header: the round back button to Settings, then (if given) the page's title. */
    private void backHeader(LinearLayout g,String title) {
        LinearLayout top=b.column();top.setPadding(dp(16),dp(8),dp(16),dp(8));
        TabIcon back=new TabIcon(this,TabIcon.CHEVRON_LEFT,b.text);back.setPadding(dp(9),dp(9),dp(9),dp(9));
        back.setBackground(b.interactive(false,Brand.PILL,b.control,false));back.setFocusable(true);back.setClickable(true);
        back.setContentDescription("Back to Settings");back.setOnClickListener(v->back());
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(dp(40),dp(40));bp.bottomMargin=dp(6);top.addView(back,bp);
        if(title!=null){TextView t=b.text(title,30,true);top.addView(t);}
        g.addView(top);
    }
    /** The installed version, as Music shows it ("1.0-beta-5"). */
    String versionName(){
        try{return getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(Exception e){return VERSION;}
    }
    /** A fresh scrolling page for Settings; rows keep to a readable width, centred, on a wide screen. */
    private LinearLayout groupedPage() {
        body.removeAllViews();scroller=null;
        ScrollView scroll=new ScrollView(this);scroll.setVerticalScrollBarEnabled(false);scroll.setFillViewport(true);
        final int widest=dp(640);
        LinearLayout content=new LinearLayout(this){
            @Override protected void onMeasure(int w,int h){
                int side=Math.max(0,(View.MeasureSpec.getSize(w)-widest)/2);
                if(getPaddingLeft()!=side)setPadding(side,getPaddingTop(),side,getPaddingBottom());
                super.onMeasure(w,h);
            }
        };
        content.setOrientation(LinearLayout.VERTICAL);scroll.addView(content);
        body.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        page=content;setScroller(scroll,dp(32));
        return content;
    }
    private void section(LinearLayout g,String title){
        TextView t=b.text(title,15,true);t.setTextColor(b.accent);t.setPadding(dp(18),dp(24),dp(16),dp(6));g.addView(t);
    }
    private void footer(LinearLayout g,String text){
        TextView t=b.label(text,13,b.secondary);t.setLineSpacing(dp(2),1);t.setPadding(dp(18),dp(6),dp(18),0);g.addView(t);
    }
    /** A rounded row, as in Music: pressed darkens it, a remote's focus rings it. */
    private LinearLayout groupedRow(Runnable click){
        LinearLayout row=b.row();row.setMinimumHeight(dp(56));row.setPadding(dp(22),dp(8),dp(16),dp(8));
        row.setBackground(b.interactive(false,Brand.PILL,b.control,false));
        if(click!=null){row.setFocusable(true);row.setOnClickListener(v->click.run());}
        return row;
    }
    private void addRow(LinearLayout g,LinearLayout row){
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(dp(12),dp(4),dp(12),dp(4));g.addView(row,lp);
    }
    /** A label and its value in grey; with a › when it opens something. */
    private TextView value(LinearLayout g,String label,String value,Runnable click){
        LinearLayout row=groupedRow(click);
        row.addView(b.label(label,16,b.text),new LinearLayout.LayoutParams(0,-2,1));
        TextView v=b.label(value==null?"":value,15,b.secondary);v.setPadding(dp(8),0,0,0);row.addView(v);
        if(click!=null){TabIcon more=new TabIcon(this,TabIcon.CHEVRON,b.secondary);LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(dp(16),dp(16));mp.leftMargin=dp(6);row.addView(more,mp);}
        addRow(g,row);return v;
    }
    private void action(LinearLayout g,String label,boolean destructive,Runnable click){
        LinearLayout row=groupedRow(click);TextView l=b.text(label,16,true);l.setTextColor(destructive?0xffe5484d:b.accent);row.addView(l);addRow(g,row);
    }
    /** A label and Music's grapefruit switch; tapping anywhere on the row switches it. */
    private Toggle toggle(LinearLayout g,String label,boolean on,ToggleChange change){
        final Toggle t=new Toggle(this);
        LinearLayout row=groupedRow(t::performClick);
        row.addView(b.label(label,16,b.text),new LinearLayout.LayoutParams(0,-2,1));
        t.setOffColors(b.toggleOff,b.toggleOffSegments);t.setChecked(on);t.setContentDescription(label);
        t.setListener((toggle,checked)->change.changed(checked));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.rightMargin=-dp(4);row.addView(t,lp);
        addRow(g,row);return t;
    }
    private interface ToggleChange{void changed(boolean on);}
    /** A row whose value is a link, in the accent: tap opens it, touch and hold copies it. */
    private void link(LinearLayout g,String label,String shown,String url){
        LinearLayout row=groupedRow(()->open(url,shown));
        row.setOnLongClickListener(v->{copy(shown);return true;});
        row.addView(b.label(label,17,b.text),new LinearLayout.LayoutParams(0,-2,1));
        TextView v=b.label(shown,16,b.accent);v.setPadding(dp(8),0,0,0);v.setSingleLine();v.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        row.addView(v,new LinearLayout.LayoutParams(-2,-2));
        addRow(g,row);
    }
    private void open(String url,String fallbackCopy){
        try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(ActivityNotFoundException e){copy(fallbackCopy);}
    }
    private void copy(String text){
        android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText(text,text));
        Toast.makeText(this,"Copied "+text,Toast.LENGTH_SHORT).show();
    }
    /** Whether this run of the app has shown its splash screen. */
    static boolean splashShown;
    /**
     * The splash screen, as Music's: the TV grapefruit and the wordmark on the theme's colour for a moment as
     * the app starts, then fading away. Android 12+ draws its own splash first (the icon alone); 13+ is told
     * the theme's colour for next time.
     */
    private void splash() {
        if(Build.VERSION.SDK_INT>=33){
            int t=prefs.getInt("theme",0);
            getSplashScreen().setSplashScreenTheme(t==1?R.style.FastSplash_Light:t==2?R.style.FastSplash_Black:R.style.FastSplash_Dark);
        }
        if(splashShown)return;
        splashShown=true;
        final FrameLayout owner=root;
        final LinearLayout cover=b.column();cover.setGravity(Gravity.CENTER);cover.setBackgroundColor(b.bg);
        cover.setClickable(true); // nothing underneath answers a tap meanwhile
        // As Music's: the stacked logo, the television over the Geist Pixel Triangle wordmark, as one picture
        // (tools/tv/splash.py), the same one the window shows while the app starts.
        ImageView logo=new ImageView(this);logo.setImageResource(b.light?R.drawable.splash_logo_light:R.drawable.splash_logo_dark);
        logo.setAdjustViewBounds(true);logo.setContentDescription("Grapefruit TV");
        cover.addView(logo,new LinearLayout.LayoutParams(-2,-2));
        owner.addView(cover,new FrameLayout.LayoutParams(-1,-1));
        owner.postDelayed(()->cover.animate().alpha(0f).setDuration(300).withEndAction(()->owner.removeView(cover)).start(),
            Build.VERSION.SDK_INT>=31?500:800); // shorter after Android 12's own splash screen
    }
    private void licences() {
        try {
            String text=Catalog.read(getAssets().open("tv-licences.txt"))+"\n\n"+Catalog.read(getAssets().open("fonts/Geist-OFL.txt"))+"\n\n"+Catalog.read(getAssets().open("GPL-3.0.txt"));
            ok("Open Source Licences",text);
        } catch(Exception e){ok("Licences","See LICENSE and tv/README.md in the source repository.");}
    }
    private void theme(int t){prefs.edit().putInt("theme",t).apply();build();}
    private void note(String message) {TextView t=b.label(message,12,b.secondary);t.setPadding(dp(8),dp(12),dp(8),dp(14));page.addView(t);}
    private void ok(String title,String message){dialog(title,message,new String[]{"OK"},-1,new Runnable[]{()->{}});}
    /**
     * A floating sheet that is only as tall as it needs to be (up to the screen): a long message scrolls,
     * the buttons stay in sight below it. chosen marks the current option, or -1.
     */
    private void dialog(String title,String message,String[] labels,int chosen,Runnable[] actions) {
        final android.app.Dialog d=new android.app.Dialog(this,R.style.FastDialogWindow);
        final int maxHeight=getResources().getDisplayMetrics().heightPixels-dp(48);
        LinearLayout card=new LinearLayout(this){
            @Override protected void onMeasure(int w,int h){
                int size=View.MeasureSpec.getSize(h);
                super.onMeasure(w,View.MeasureSpec.makeMeasureSpec(Math.min(size>0?size:maxHeight,maxHeight),View.MeasureSpec.AT_MOST));
            }
        };
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(22),dp(22),dp(22),dp(18));card.setBackground(b.shape(b.surface,30,true));
        TextView heading=b.text(title,22,true);card.addView(heading);
        if(message!=null){
            ScrollView scroll=new ScrollView(this);scroll.setVerticalScrollBarEnabled(false);
            TextView t=b.label(message,15,b.text);t.setLineSpacing(dp(3),1);t.setPadding(0,dp(12),0,dp(8));scroll.addView(t);
            card.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        }
        LinearLayout buttons=b.column();buttons.setPadding(0,dp(6),0,0);
        for(int i=0;i<labels.length;i++) {final int pos=i;TextView button=b.button(labels[i],i==chosen,v->{d.dismiss();actions[pos].run();});
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(48));p.topMargin=dp(8);buttons.addView(button,p);}
        if(labels.length>6) {
            // A long list (Add App's apps): the options scroll.
            ScrollView list=new ScrollView(this);list.setVerticalScrollBarEnabled(false);list.addView(buttons);
            card.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        } else card.addView(buttons,new LinearLayout.LayoutParams(-1,-2));
        d.setContentView(card);
        Window w=d.getWindow();
        if(w!=null){w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));}
        d.show();
        if(w!=null)w.setLayout(Math.min(dp(480),getResources().getDisplayMetrics().widthPixels-dp(32)),WindowManager.LayoutParams.WRAP_CONTENT);
    }
    private interface FormDone{void done(String[] values);}
    /** A dialog with text fields (secret ones hidden), Save and Cancel. */
    private void form(String title,String note,String[] hints,boolean[] secret,String[] values,FormDone done) {
        final android.app.Dialog d=new android.app.Dialog(this,R.style.FastDialogWindow);
        LinearLayout card=b.column();card.setPadding(dp(22),dp(22),dp(22),dp(18));card.setBackground(b.shape(b.surface,30,true));
        card.addView(b.text(title,22,true));
        TextView n=b.label(note,14,b.secondary);n.setPadding(0,dp(8),0,dp(4));n.setLineSpacing(dp(2),1);card.addView(n);
        final EditText[] fields=new EditText[hints.length];
        for(int i=0;i<hints.length;i++){
            EditText f=new EditText(this);f.setSingleLine();f.setTextColor(b.text);f.setHintTextColor(b.secondary);f.setTypeface(Fonts.regular());f.setTextSize(15);
            f.setHint(hints[i]);f.setText(values[i]);f.setPadding(dp(18),0,dp(18),0);f.setBackground(b.interactive(false,Brand.PILL));
            f.setInputType(secret[i]?InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD:i>0?InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI:InputType.TYPE_CLASS_TEXT);
            if(i>1&&!secret[i])f.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            // A prefilled field ("My TV") selects its text on a tap, so typing replaces it.
            if(!values[i].isEmpty())f.setSelectAllOnFocus(true);
            noSelectionBar(f);
            fields[i]=f;
            if(i>0&&!secret[i]) {
                // Grapefruit's own Paste, beside addresses and usernames (on Android 4.x the system's paste pop-up is off in dialogs).
                LinearLayout row=b.row();
                row.addView(f,new LinearLayout.LayoutParams(0,dp(48),1));
                TextView paste=b.button("Paste",false,v->{CharSequence clip=clipboardText();if(clip!=null){f.setText(clip.toString().trim());f.setSelection(f.getText().length());}});
                paste.setTextSize(13);paste.setPadding(dp(14),0,dp(14),0);
                LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-2,dp(48));pp.leftMargin=dp(8);row.addView(paste,pp);
                LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(48));rp.topMargin=dp(10);card.addView(row,rp);
            } else {
                LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(48));p.topMargin=dp(10);card.addView(f,p);
            }
        }
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(48));bp.topMargin=dp(14);
        card.addView(b.button("Save",true,v->{String[] out=new String[fields.length];for(int i=0;i<out.length;i++)out[i]=fields[i].getText().toString();d.dismiss();done.done(out);}),bp);
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,dp(48));cp.topMargin=dp(8);
        card.addView(b.button("Cancel",false,v->d.dismiss()),cp);
        ScrollView scroll=new ScrollView(this);scroll.addView(card);d.setContentView(scroll);
        Window w=d.getWindow();if(w!=null)w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        d.show();if(w!=null)w.setLayout(Math.min(dp(480),getResources().getDisplayMetrics().widthPixels-dp(32)),WindowManager.LayoutParams.WRAP_CONTENT);
    }
    /**
     * Android 4.x shows text selection (a double-tap, a long press) as an action bar, which in a dialog's
     * window can take the app down. There, a dialog's fields don't open it: typing, the cursor and the
     * keyboard's own paste still work. Android 6 and later keep their floating Cut/Copy/Paste toolbar.
     */
    static void noSelectionBar(EditText f) {
        if(Build.VERSION.SDK_INT>=23) return;
        // No long-press pop-up (Paste, Select): Jelly Bean's crashes in a dialog. The form has its own Paste.
        f.setLongClickable(false);
        f.setCustomSelectionActionModeCallback(new android.view.ActionMode.Callback(){
            public boolean onCreateActionMode(android.view.ActionMode m,Menu menu){return false;}
            public boolean onPrepareActionMode(android.view.ActionMode m,Menu menu){return false;}
            public boolean onActionItemClicked(android.view.ActionMode m,MenuItem item){return false;}
            public void onDestroyActionMode(android.view.ActionMode m){}
        });
    }
    /** As Music: after a crash, offer to share its report (nothing is sent unless the person shares it). */
    private void offerCrashReport() {
        final String report=CrashReport.pending(this);
        if(report==null)return;
        new Handler(Looper.getMainLooper()).postDelayed(()->{
            if(isFinishing())return;
            dialog("Grapefruit TV closed unexpectedly","Last time, a fault in Grapefruit TV closed it. It kept a short report: the app and Android versions, this device's model, and where in Grapefruit TV's code it stopped. Logins and addresses are left out. Nothing has been sent. Sharing it with the developer helps get it fixed.",
                new String[]{"Share Report","Dismiss"},-1,new Runnable[]{()->{
                    CrashReport.clear(this);
                    Intent send=new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT,"Grapefruit TV crash report").putExtra(Intent.EXTRA_TEXT,report);
                    try{startActivity(Intent.createChooser(send,"Share Report"));}catch(ActivityNotFoundException e){copy(report);}
                },()->CrashReport.clear(this)});
        },1300);
    }
    /** The clipboard's text, or null. */
    private CharSequence clipboardText() {
        android.content.ClipboardManager cm=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        if(cm==null||!cm.hasPrimaryClip()||cm.getPrimaryClip().getItemCount()==0)return null;
        return cm.getPrimaryClip().getItemAt(0).coerceToText(this);
    }
    private void addSource(String kind,String name,String address,String user,String pass) {
        if(address.trim().isEmpty()||(UserSources.LOGIN.equals(kind)&&user.trim().isEmpty())){ok("Add a Source","Fill in the address"+(UserSources.LOGIN.equals(kind)?" and username":"")+".");return;}
        UserSources.Source s=sources.add(kind,name.trim().isEmpty()?"My TV":name.trim(),address,user,pass);
        added.put(s.id,new ArrayList<Channel>());mergeChannels();settings();
        refreshSources(s);
    }
    private void fullscreen(boolean on) {
        boolean full=on&&watching!=null;
        if(wide())nav.setVisibility(full?View.GONE:View.VISIBLE);
        if(bars!=null&&full)bars.setVisibility(View.GONE);
        if(Build.VERSION.SDK_INT>=30) {
            // FLAG_FULLSCREEN is ignored edge to edge: hide the status and navigation bars; a swipe shows them for a moment.
            WindowInsetsController bars=getWindow().getInsetsController();
            if(bars!=null){
                bars.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                if(full)bars.hide(WindowInsets.Type.systemBars());else bars.show(WindowInsets.Type.systemBars());
            }
        } else if(full)getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        backCallback();
        if(watching!=null&&prefs.getBoolean("keepScreenOn",true))getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    @Override protected void onStart(){super.onStart();started=true;if(watching!=null&&playback==null&&pendingSurface!=null)startPlayer(pendingSurface);}
    @Override protected void onStop(){started=false;if(playback!=null){playback.release();playback=null;}super.onStop();}
    @Override protected void onResume(){super.onResume();if(com.grapefruitmusic.ui.UniversalTheme.sync(this,prefs,true,new int[]{0,1,2,3}))build();if(started&&watching!=null&&playback==null)watchPage();}
    /** Whether the phone is being held sideways for full screen (turning it back ends it). */
    private boolean sideways;
    private boolean phone(){return getResources().getConfiguration().smallestScreenWidthDp<600;}
    /**
     * Full screen, as video apps do it on a phone: the picture turns sideways (and stays free to turn either
     * way); leaving full screen lets the phone turn back. Tablets and TVs stay as they are.
     */
    private void setFullScreen(boolean on){
        fullScreen=on;
        if(phone())setRequestedOrientation(on?android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE:android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        sideways=on&&getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE;
        watchPage();
    }
    @Override public void onConfigurationChanged(Configuration c){
        super.onConfigurationChanged(c);
        if(watching!=null&&phone()){
            boolean landscape=c.orientation==Configuration.ORIENTATION_LANDSCAPE;
            // Turning a phone sideways while watching goes full screen; turning it back upright comes out.
            if(landscape&&!fullScreen){fullScreen=true;sideways=true;}
            else if(landscape&&fullScreen)sideways=true;
            else if(!landscape&&fullScreen&&sideways&&getRequestedOrientation()!=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE){fullScreen=false;sideways=false;}
        }
        build();
    }
    @Override public void onBackPressed(){if(!back())super.onBackPressed();}
    /** One step back inside the app: out of full screen, off the watch page, back to Live TV. False at the start. */
    boolean back(){
        if(fullScreen){setFullScreen(false);}
        else if(watching!=null){stop();build();}
        else if(settingsPage!=null){settingsPage=null;settings();}
        else if(!tab.equals("Live TV")){tab="Live TV";query="";build();}
        else return false;
        return true;
    }
    private Object backCallback;
    /**
     * Android 13+ with predictive back (always on for apps targeting Android 16) never calls onBackPressed:
     * Back goes to whoever registered for it. Register while there's somewhere in the app to go back to,
     * and step aside on Live TV so Back shows the system's go-home animation.
     */
    private void backCallback() {
        if(Build.VERSION.SDK_INT<33)return;
        boolean inside=fullScreen||watching!=null||settingsPage!=null||!tab.equals("Live TV");
        android.window.OnBackInvokedDispatcher dispatcher=getOnBackInvokedDispatcher();
        if(inside&&backCallback==null){
            android.window.OnBackInvokedCallback callback=()->back();
            backCallback=callback;
            dispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,callback);
        } else if(!inside&&backCallback!=null){
            dispatcher.unregisterOnBackInvokedCallback((android.window.OnBackInvokedCallback)backCallback);
            backCallback=null;
        }
    }
    @Override public boolean onKeyDown(int key,KeyEvent event) {
        if(watching!=null&&(key==KeyEvent.KEYCODE_CHANNEL_UP||key==KeyEvent.KEYCODE_CHANNEL_DOWN)){
            List<Channel> lineup=visible.isEmpty()?channels:visible;int pos=lineup.indexOf(watching);
            if(!lineup.isEmpty())tune(lineup.get((pos+(key==KeyEvent.KEYCODE_CHANNEL_UP?1:lineup.size()-1))%lineup.size()));return true;
        }
        return super.onKeyDown(key,event);
    }
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putString("tab",tab);out.putString("category",category);out.putString("country",country);out.putString("query",query);out.putString("watching",watching==null?null:watching.id);out.putBoolean("fullscreen",fullScreen);out.putString("settingsPage",settingsPage);}
    /** Keeps the picture at its aspect ratio inside a 16:9 frame; other children (the corner mask) fill the frame. */
    private static class AspectFrame extends FrameLayout {
        float ratio=16f/9;
        AspectFrame(Context c){super(c);}
        @Override protected void onMeasure(int widthSpec,int heightSpec) {
            int width=MeasureSpec.getSize(widthSpec),height=width*9/16;
            if(MeasureSpec.getMode(heightSpec)!=MeasureSpec.UNSPECIFIED)height=Math.min(height,MeasureSpec.getSize(heightSpec));
            setMeasuredDimension(width,height);
            int vw=width,vh=Math.round(width/ratio);
            if(vh>height){vh=height;vw=Math.round(height*ratio);}
            for(int i=0;i<getChildCount();i++){
                View child=getChildAt(i);FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)child.getLayoutParams();
                int cw=i==0?vw:width,ch=i==0?vh:height;
                if(i==0){lp.width=vw;lp.height=vh;}
                child.measure(MeasureSpec.makeMeasureSpec(cw,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(ch,MeasureSpec.EXACTLY));
            }
        }
    }
    /**
     * Rounds the picture's corners by painting the page colour over them. A SurfaceView can't be clipped on
     * Android 4.x, but the window's own views draw over its hole, so this works on every version.
     */
    private static final class Corners extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final float radius;private final Path path=new Path();
        Corners(Context c,int color,float radius){super(c);paint.setColor(color);this.radius=radius;}
        @Override protected void onSizeChanged(int w,int h,int ow,int oh){
            path.reset();path.setFillType(Path.FillType.INVERSE_EVEN_ODD);
            path.addRoundRect(new RectF(0,0,w,h),radius,radius,Path.Direction.CW);
        }
        @Override protected void onDraw(Canvas canvas){canvas.drawPath(path,paint);}
    }
}
