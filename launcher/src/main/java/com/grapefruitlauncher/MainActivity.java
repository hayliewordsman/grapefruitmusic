package com.grapefruitlauncher;

import android.app.Activity;
import android.app.Dialog;
import android.content.*;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import com.grapefruitmusic.ui.Fonts;
import java.text.DateFormat;
import java.util.*;
import java.util.concurrent.ThreadPoolExecutor;

/** A native HOME activity. Every app, label, dialog and dock uses the selected Grapefruit theme. */
public final class MainActivity extends Activity {
    private static final int HOME=0, APPS=1, SETTINGS=2, PICK_DOCK=3;
    private LauncherStore store;
    private LauncherTheme theme;
    private IconPack icons;
    private final ThreadPoolExecutor loader=MemoryPolicy.worker("launcher-apps");
    private final Handler main=new Handler(Looper.getMainLooper());
    private List<AppCatalog.App> apps=new ArrayList<>();
    private int screen=HOME, dockSlot=-1, savedPosition, savedTop;
    private boolean destroyed, loading=true, registered, resumed, uiReleased, catalogDirty=true, refreshRunning;
    private static boolean fontsReady;
    private String query="";
    private FrameLayout root;
    private GridView grid;
    private AppAdapter adapter;
    private TextView subtitle, empty;
    private Dialog dialog;
    private final BroadcastReceiver packageChanges=new BroadcastReceiver() {
        @Override public void onReceive(Context c,Intent i) {
            String pkg=i.getData()==null?null:i.getData().getSchemeSpecificPart();
            if(pkg!=null)icons.packageChanged(pkg);
            else {
                String[] packages=i.getStringArrayExtra(Intent.EXTRA_CHANGED_PACKAGE_LIST);
                if(packages==null)icons.invalidate();else for(String name:packages)icons.packageChanged(name);
            }
            catalogDirty=true;refresh();
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if(!fontsReady){Fonts.init(getApplicationContext());fontsReady=true;}
        store=new LauncherStore(getSharedPreferences("launcher",MODE_PRIVATE));
        icons=new IconPack(this);
        if(state!=null) {
            screen=state.getInt("screen",HOME);dockSlot=state.getInt("slot",-1);query=state.getString("query","");
            if(screen==PICK_DOCK && (dockSlot<0 || dockSlot>=LauncherStore.DOCK_APPS))screen=HOME;
        }
        IntentFilter changes=new IntentFilter();changes.addAction(Intent.ACTION_PACKAGE_ADDED);
        changes.addAction(Intent.ACTION_PACKAGE_REMOVED);changes.addAction(Intent.ACTION_PACKAGE_CHANGED);
        changes.addAction(Intent.ACTION_PACKAGE_REPLACED);changes.addDataScheme("package");
        if(Build.VERSION.SDK_INT>=33)registerReceiver(packageChanges,changes,Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(packageChanges,changes);
        IntentFilter externalApps=new IntentFilter(Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE);
        externalApps.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE);
        if(Build.VERSION.SDK_INT>=33)registerReceiver(packageChanges,externalApps,Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(packageChanges,externalApps);
        registered=true;
        if(Build.VERSION.SDK_INT>=33)ModernBack.register(this);
        build();
    }
    @Override protected void onResume() {
        super.onResume();resumed=true;
        if(uiReleased) {
            uiReleased=false;
            if(grid!=null){
                grid.setAdapter(adapter);updateApps();
                if(Build.VERSION.SDK_INT>=21)grid.setSelectionFromTop(savedPosition,savedTop);
                else grid.setSelection(savedPosition);
            }
            updateDock();
        }
        refresh();
    }
    @Override protected void onStop() {resumed=false;releaseBackgroundUi();super.onStop();}
    @Override public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if(icons!=null){icons.trim();if(!resumed)releaseBackgroundUi();}
    }
    @Override public void onLowMemory() {
        super.onLowMemory();if(icons!=null){icons.trim();if(!resumed)releaseBackgroundUi();}
    }
    private void releaseBackgroundUi() {
        if(uiReleased)return;
        uiReleased=true;
        icons.clearMemory();
        if(grid!=null) {
            savedPosition=grid.getFirstVisiblePosition();View first=grid.getChildAt(0);
            savedTop=first==null?0:first.getTop()-grid.getPaddingTop();
            // Detach the adapter to release both visible icon bitmaps and GridView's recycled cells.
            grid.setAdapter(null);
        }
        if(dockContainer!=null)dockContainer.removeAllViews();
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);out.putInt("screen",screen);out.putInt("slot",dockSlot);out.putString("query",query);
    }
    @Override protected void onNewIntent(Intent intent) {super.onNewIntent(intent);setIntent(intent);go(HOME);}
    @Override protected void onDestroy() {
        destroyed=true;if(registered)unregisterReceiver(packageChanges);
        dismissDialog();loader.shutdownNow();icons.close();main.removeCallbacksAndMessages(null);super.onDestroy();
    }
    private void refresh() {
        if(destroyed || !catalogDirty || refreshRunning)return;
        catalogDirty=false;refreshRunning=true;
        loader.execute(()->{
            List<AppCatalog.App> result;int failed;
            try {
                result=AppCatalog.load(getApplicationContext());
                failed=icons.prepare(result,(count,total)->main.post(()->{
                    if(!destroyed && resumed && loading && empty!=null)empty.setText(getString(R.string.preparing_apps,count,total));
                }));
            }
            catch(InterruptedException e){Thread.currentThread().interrupt();return;}
            catch(RuntimeException e) {main.post(()->{
                refreshRunning=false;catalogDirty=true;
                if(!destroyed && resumed)notice("Apps could not be loaded. Open All Apps to try again.");
            });return;}
            main.post(()->{
                refreshRunning=false;if(destroyed)return;
                if(catalogDirty){refresh();return;}
                apps=result;loading=false;store.seed(apps);
                store.suggestedDock(AppCatalog.suggestedDock(getApplicationContext(),apps));
                if(screen!=SETTINGS && resumed){updateApps();updateDock();}
                if(failed>0 && resumed)notice("Some app icons could not be saved. Check available storage.");
            });
        });
    }
    private void go(int next) {
        hideKeyboard();dismissDialog();
        if(screen==HOME && next==HOME){savedPosition=0;savedTop=0;if(grid!=null)grid.setSelection(0);updateApps();return;}
        screen=next;query="";if(next!=PICK_DOCK)dockSlot=-1;build();
        if(next==APPS)refresh();
    }
    private void hideKeyboard() {
        View focused=getCurrentFocus();
        if(focused!=null)((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(focused.getWindowToken(),0);
    }
    @Override public void onBackPressed() {go(HOME);}

    private void build() {
        // Drop references to the previous hierarchy, including Settings' hidden app grid.
        grid=null;adapter=null;empty=null;dockContainer=null;subtitle=null;uiReleased=false;
        savedPosition=0;savedTop=0;
        theme=new LauncherTheme(this,store.theme());
        icons.screenChanged(theme.id);
        root=new FrameLayout(this);root.setBackgroundColor(theme.bg);root.setFocusableInTouchMode(true);
        if(Build.VERSION.SDK_INT>=30) {
            root.setOnApplyWindowInsetsListener((v,insets)->{
                android.graphics.Insets i=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()|WindowInsets.Type.ime());
                v.setPadding(i.left,i.top,i.right,i.bottom);return WindowInsets.CONSUMED;
            });
        } else root.setFitsSystemWindows(true);
        LinearLayout shell=column();root.addView(shell,new FrameLayout.LayoutParams(-1,-1));
        header(shell);
        if(screen==SETTINGS)settings(shell);
        else {
            if(screen==APPS || screen==PICK_DOCK)search(shell);
            FrameLayout body=new FrameLayout(this);
            shell.addView(body,new LinearLayout.LayoutParams(-1,0,1));
            grid=new GridView(this);grid.setId(android.R.id.list);grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
            grid.setHorizontalSpacing(dp(4));grid.setVerticalSpacing(dp(12));grid.setPadding(dp(12),dp(14),dp(12),dp(18));
            grid.setClipToPadding(false);grid.setSelector(new ColorDrawable(android.graphics.Color.TRANSPARENT));
            grid.setNumColumns(columns());grid.setVerticalScrollBarEnabled(false);
            grid.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{
                GridView resized=(GridView)v;int n=columns();if(resized.getNumColumns()!=n)resized.setNumColumns(n);
            });
            adapter=new AppAdapter();grid.setAdapter(adapter);
            grid.setOnItemClickListener((p,v,pos,id)->{
                AppCatalog.App app=adapter.visible.get(pos);
                if(screen==PICK_DOCK){store.dock(dockSlot,app.id());go(HOME);}else open(app);
            });
            grid.setOnItemLongClickListener((p,v,pos,id)->{appMenu(adapter.visible.get(pos),-1);return true;});
            body.addView(grid,new FrameLayout.LayoutParams(-1,-1));
            empty=theme.text("",16,false);empty.setGravity(Gravity.CENTER);empty.setTextColor(theme.secondary);
            empty.setPadding(dp(28),dp(20),dp(28),dp(20));body.addView(empty,new FrameLayout.LayoutParams(-1,-1));
            updateApps();
            dockContainer=column();shell.addView(dockContainer,new LinearLayout.LayoutParams(-1,-2));updateDock();
        }
        setContentView(root);windowColours();
    }
    private int columns() {
        float fontScale=getResources().getConfiguration().fontScale;
        int width=getResources().getConfiguration().screenWidthDp-24;
        return Math.max(2,Math.min(8,(int)(width/Math.max(88,80*fontScale))));
    }
    private void header(LinearLayout parent) {
        LinearLayout head=column();head.setPadding(dp(22),dp(18),dp(22),dp(6));parent.addView(head);
        LinearLayout tools=row();
        if(screen!=HOME)tools.addView(theme.chip("‹  Home",false,v->go(HOME)));
        else {
            TextView brand=theme.text("Grapefruit Launcher",14,true);brand.setTextColor(theme.accent);
            tools.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        }
        if(screen!=SETTINGS) {
            if(screen!=HOME)tools.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
            TextView settings=theme.chip("Settings",false,v->go(SETTINGS));settings.setContentDescription("Launcher settings");tools.addView(settings);
        }
        head.addView(tools,new LinearLayout.LayoutParams(-1,-2));
        TextView title=theme.text(screen==HOME?"Home":screen==SETTINGS?"Settings":screen==PICK_DOCK?"Choose a dock app":"All Apps",32,true);
        title.setPadding(0,dp(20),0,dp(8));head.addView(title);
        subtitle=theme.text(screen==HOME?DateFormat.getDateInstance(DateFormat.FULL).format(new Date()):"",14,false);
        subtitle.setTextColor(theme.secondary);subtitle.setPadding(0,0,0,dp(8));head.addView(subtitle);
        if(screen==SETTINGS)subtitle.setText(R.string.settings_tagline);
    }
    private void search(LinearLayout parent) {
        LinearLayout row=row();row.setPadding(dp(18),dp(4),dp(18),dp(4));
        EditText search=new EditText(this);search.setTextColor(theme.text);search.setHintTextColor(theme.secondary);
        search.setTextSize(16);search.setTypeface(Fonts.regular());search.setSingleLine(true);
        search.setHint("Search apps");search.setContentDescription("Search apps");
        search.setBackground(theme.shape(theme.control,100000,true));search.setPadding(dp(18),dp(12),dp(18),dp(12));
        search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        search.setText(query);search.setSelectAllOnFocus(false);
        search.setOnEditorActionListener((v,action,event)->{hideKeyboard();return true;});
        search.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){query=s.toString();updateApps();}
            public void afterTextChanged(Editable e){}
        });
        row.addView(search,new LinearLayout.LayoutParams(0,-2,1));
        TextView clear=theme.chip("Clear",false,v->search.setText(""));
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,-2);cp.leftMargin=dp(8);row.addView(clear,cp);
        parent.addView(row);
    }
    private void updateApps() {
        if(adapter==null || screen==SETTINGS)return;
        List<AppCatalog.App> visible=new ArrayList<>();
        if(screen==HOME){for(String id:store.home()){AppCatalog.App a=AppCatalog.find(apps,id);if(a!=null)visible.add(a);}}
        else visible=AppCatalog.filter(apps,query);
        adapter.visible=visible;adapter.notifyDataSetChanged();
        if(screen!=HOME)subtitle.setText(screen==PICK_DOCK?"Slot "+(dockSlot+1)+" · tap an app to place it":apps.size()+" apps · touch and hold for options");
        if(empty!=null) {
            empty.setText(loading?"Loading your apps…":screen==HOME?"Your Home is ready.\nOpen All Apps, then touch and hold an app to add it here.":"No apps found.");
            empty.setVisibility(visible.isEmpty()?View.VISIBLE:View.GONE);
        }
    }
    private final class AppAdapter extends BaseAdapter {
        List<AppCatalog.App> visible=new ArrayList<>();
        private final int cellHeight;
        AppAdapter() {
            android.graphics.Paint font=new android.graphics.Paint();font.setTypeface(Fonts.medium());
            font.setTextSize(12*getResources().getDisplayMetrics().scaledDensity);
            cellHeight=dp(58+9+12+16)+(int)Math.ceil(font.getFontSpacing()*3);
        }
        @Override public int getCount(){return visible.size();}
        @Override public Object getItem(int p){return visible.get(p);}
        @Override public long getItemId(int p){return p;}
        @Override public View getView(int p,View recycled,android.view.ViewGroup parent) {
            LinearLayout cell;
            if(recycled instanceof LinearLayout)cell=(LinearLayout)recycled;
            else {
                cell=column();cell.setGravity(Gravity.CENTER_HORIZONTAL);cell.setPadding(dp(3),dp(6),dp(3),dp(6));
                cell.setLayoutParams(new AbsListView.LayoutParams(-1,cellHeight));
                // Only the label is a chip: the enclosing app tile is transparent at rest.
                android.graphics.drawable.StateListDrawable states=new android.graphics.drawable.StateListDrawable();
                states.addState(new int[]{android.R.attr.state_pressed},theme.shape(theme.pressed,24,false));
                states.addState(new int[]{android.R.attr.state_focused},theme.shape(theme.control,24,true));
                states.addState(new int[]{},new ColorDrawable(0));cell.setBackground(states);
                ImageView image=new ImageView(MainActivity.this);image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                cell.addView(image,new LinearLayout.LayoutParams(dp(58),dp(58)));
                TextView label=theme.text("",12,false);label.setTypeface(Fonts.medium());label.setGravity(Gravity.CENTER);
                label.setSingleLine(false);label.setMaxLines(3);label.setEllipsize(TextUtils.TruncateAt.END);
                label.setPadding(dp(10),dp(8),dp(10),dp(8));
                label.setBackground(theme.shape(theme.control,100000,true));
                label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.topMargin=dp(9);cell.addView(label,lp);
            }
            AppCatalog.App app=visible.get(p);cell.setContentDescription(app.label);
            ((TextView)cell.getChildAt(1)).setText(app.label);icons.bind((ImageView)cell.getChildAt(0),app,theme);
            return cell;
        }
    }

    private LinearLayout dockContainer;
    private void updateDock() {
        if(dockContainer==null || screen==SETTINGS)return;
        dockContainer.removeAllViews();dockContainer.setGravity(Gravity.CENTER_HORIZONTAL);
        dockContainer.setPadding(dp(16),dp(18),dp(16),dp(12));
        int clear=theme.bg&0x00ffffff;
        dockContainer.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{clear,theme.bg}));
        LinearLayout dock=row();dock.setId(android.R.id.tabs);dock.setGravity(Gravity.CENTER);
        dock.setPadding(dp(5),dp(5),dp(5),dp(5));dock.setBackground(theme.shape(theme.surface,100000,true));
        if(Build.VERSION.SDK_INT>=21)dock.setElevation(dp(6));
        int width=Math.min(dp(400),getResources().getDisplayMetrics().widthPixels-dp(32));
        dockContainer.addView(dock,new LinearLayout.LayoutParams(width,dp(74)));
        for(int position=0;position<5;position++) {
            int slot=position<2?position:position-1;
            FrameLayout touch=new FrameLayout(this);touch.setFocusable(true);touch.setClickable(true);
            touch.setBackground(dockTouch());dock.addView(touch,new LinearLayout.LayoutParams(0,-1,1));
            ImageView icon=new ImageView(this);icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            touch.addView(icon,new FrameLayout.LayoutParams(dp(48),dp(48),Gravity.CENTER));
            if(position==2) {
                icon.setImageDrawable(new IconPack.Plate(theme,null,1));touch.setContentDescription("All Apps");
                touch.setOnClickListener(v->go(screen==APPS?HOME:APPS));
            } else {
                AppCatalog.App app=AppCatalog.find(apps,store.dock(slot));
                if(app==null){icon.setImageDrawable(new IconPack.Plate(theme,null,2));touch.setContentDescription("Add dock app, slot "+(slot+1));touch.setOnClickListener(v->pickDock(slot));}
                else {icons.bind(icon,app,theme);touch.setContentDescription(app.label+", dock slot "+(slot+1));touch.setOnClickListener(v->open(app));}
                touch.setOnLongClickListener(v->{if(app==null)pickDock(slot);else appMenu(app,slot);return true;});
            }
        }
    }
    private android.graphics.drawable.Drawable dockTouch() {
        android.graphics.drawable.StateListDrawable d=new android.graphics.drawable.StateListDrawable();
        d.addState(new int[]{android.R.attr.state_focused},theme.shape(theme.control,100000,true));
        d.addState(new int[]{android.R.attr.state_pressed},theme.shape(theme.pressed,100000,false));
        d.addState(new int[]{},new ColorDrawable(0));return d;
    }
    private void pickDock(int slot){dockSlot=slot;go(PICK_DOCK);}
    private void appMenu(AppCatalog.App app,int slot) {
        List<String> labels=new ArrayList<>();List<Runnable> actions=new ArrayList<>();
        labels.add("Open");actions.add(()->open(app));
        boolean onHome=store.home().contains(app.id());
        labels.add(onHome?"Remove from Home":"Add to Home");actions.add(()->{if(onHome)store.remove(app.id());else store.add(app.id());updateApps();});
        if(slot>=0) {
            labels.add("Replace dock app");actions.add(()->pickDock(slot));
            labels.add("Remove from dock");actions.add(()->{store.dock(slot,"");updateDock();});
        } else {
            labels.add("Place in dock…");actions.add(()->chooseDockSlot(app));
        }
        if(onHome && screen==HOME) {
            labels.add("Move earlier");actions.add(()->{store.move(app.id(),-1);updateApps();});
            labels.add("Move later");actions.add(()->{store.move(app.id(),1);updateApps();});
        }
        labels.add("App info");actions.add(()->external(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+app.component.getPackageName()))));
        if(!app.component.getPackageName().equals(getPackageName())) {
            labels.add("Uninstall…");actions.add(()->external(new Intent(Intent.ACTION_DELETE,Uri.parse("package:"+app.component.getPackageName()))));
        }
        actions(app.label,labels,actions);
    }
    private void chooseDockSlot(AppCatalog.App app) {
        List<String> labels=new ArrayList<>();List<Runnable> picks=new ArrayList<>();
        for(int i=0;i<LauncherStore.DOCK_APPS;i++) {
            final int slot=i;AppCatalog.App current=AppCatalog.find(apps,store.dock(i));
            labels.add("Slot "+(i+1)+" · "+(current==null?"Empty":current.label));
            picks.add(()->{store.dock(slot,app.id());updateDock();});
        }
        actions("Place in dock",labels,picks);
    }
    private void actions(String title,List<String> labels,List<Runnable> choices) {
        dismissDialog();dialog=new Dialog(this,R.style.LauncherDialogWindow);
        LinearLayout card=column();card.setPadding(dp(18),dp(22),dp(18),dp(16));card.setBackground(theme.shape(theme.surface,28,true));
        TextView name=theme.text(title,24,true);name.setPadding(dp(8),0,dp(8),dp(12));card.addView(name);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);LinearLayout body=column();scroll.addView(body);
        for(int i=0;i<labels.size();i++) {
            final Runnable choice=choices.get(i);TextView button=theme.chip(labels.get(i),false,v->{dismissDialog();choice.run();});
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(7);body.addView(button,lp);
        }
        TextView cancel=theme.chip("Cancel",false,v->dismissDialog());LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.topMargin=dp(14);body.addView(cancel,cp);
        card.addView(scroll,new LinearLayout.LayoutParams(-1,-2));dialog.setContentView(card);
        Window w=dialog.getWindow();if(w!=null){w.setBackgroundDrawable(new ColorDrawable(0));w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);}
        dialog.show();
        if(w!=null)w.setLayout(Math.min(dp(420),getResources().getDisplayMetrics().widthPixels-dp(28)),-2);
        // A long menu scrolls on Jelly Bean and on landscape/square screens too.
        int max=Math.max(dp(160),(int)(getResources().getDisplayMetrics().heightPixels*.72f)-dp(80));
        scroll.measure(View.MeasureSpec.makeMeasureSpec(dp(360),View.MeasureSpec.AT_MOST),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        if(scroll.getMeasuredHeight()>max)scroll.setLayoutParams(new LinearLayout.LayoutParams(-1,max));
    }
    private void dismissDialog(){if(dialog!=null){dialog.dismiss();dialog=null;}}

    private void settings(LinearLayout parent) {
        ScrollView scroll=new ScrollView(this);LinearLayout page=column();page.setPadding(dp(20),dp(12),dp(20),dp(28));
        scroll.addView(page);parent.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        section(page,"Theme");
        for(int i=0;i<LauncherTheme.NAMES.length;i++) {
            final int selected=i;LauncherTheme preview=new LauncherTheme(this,i);
            LinearLayout option=row();option.setPadding(dp(12),dp(10),dp(16),dp(10));option.setGravity(Gravity.CENTER_VERTICAL);
            option.setBackground(theme.interactive(i==theme.id));option.setFocusable(true);option.setClickable(true);
            option.setContentDescription(LauncherTheme.NAMES[i]+(i==theme.id?", selected":""));
            option.setOnClickListener(v->{store.theme(selected);build();});
            ImageView icon=new ImageView(this);icon.setImageDrawable(new IconPack.Plate(preview,null,1));
            option.addView(icon,new LinearLayout.LayoutParams(dp(48),dp(48)));
            TextView name=theme.text(LauncherTheme.NAMES[i]+(i==theme.id?"  ✓":""),16,true);
            name.setTextColor(i==theme.id?theme.onAccent:theme.text);name.setPadding(dp(14),0,0,0);
            option.addView(name,new LinearLayout.LayoutParams(0,-2,1));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(10);page.addView(option,lp);
        }
        section(page,"A place for every app");
        paragraph(page,"Your theme colors the background, label chips, dock and every app’s teardrop icon plate. Original app artwork stays recognizable. These four icon packs are exclusive to Grapefruit Launcher.");
        TextView setup=theme.chip("Set as default launcher",false,v->defaultHome());page.addView(setup,new LinearLayout.LayoutParams(-1,-2));
        paragraph(page,Build.VERSION.SDK_INT>=21?"Choose Grapefruit Launcher as your Home app in Android’s settings.":"Press Home, choose Grapefruit Launcher, then Always. If another launcher is already the default, clear its defaults in Android’s app settings first.");
        section(page,"Make it yours");
        paragraph(page,"Touch and hold an app to add it to Home, move it earlier or later, or choose one of four dock slots. Touch an empty dock slot to pick an app. All Apps always stays in the middle. Dock icons have no labels.");
        TextView reset=theme.chip("Reset dock to suggested apps",false,v->{
            store.resetDock(AppCatalog.suggestedDock(getApplicationContext(),apps));
            notice("Dock reset to available Phone, Messages, Chrome and Grapefruit Audio apps.");
        });page.addView(reset,new LinearLayout.LayoutParams(-1,-2));
        paragraph(page,"No wallpaper. No account. No ads. Android 4.2.2 and later.\n\nGeist by Vercel, shared with Grapefruit Audio and TV. Licensed under the SIL Open Font License 1.1.");
        TextView license=theme.chip("Font license",false,v->fontLicense());page.addView(license,new LinearLayout.LayoutParams(-1,-2));
    }
    private void fontLicense() {
        String license;
        try(java.io.InputStream in=getAssets().open("fonts/Geist-OFL.txt");java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()) {
            byte[] buffer=new byte[4096];int count;while((count=in.read(buffer))!=-1)out.write(buffer,0,count);
            license=out.toString("UTF-8");
        }catch(java.io.IOException e){notice("The font license could not be opened.");return;}
        dismissDialog();dialog=new Dialog(this,R.style.LauncherDialogWindow);ScrollView scroll=new ScrollView(this);
        TextView text=theme.text(license,14,false);text.setPadding(dp(20),dp(20),dp(20),dp(20));text.setBackgroundColor(theme.surface);
        scroll.addView(text);dialog.setContentView(scroll);dialog.show();
        if(dialog.getWindow()!=null)dialog.getWindow().setLayout(Math.min(dp(480),getResources().getDisplayMetrics().widthPixels-dp(28)),(int)(getResources().getDisplayMetrics().heightPixels*.75f));
    }
    private void defaultHome() {
        if(Build.VERSION.SDK_INT>=29 && ModernHome.request(this))return;
        if(Build.VERSION.SDK_INT>=21)external(new Intent(Settings.ACTION_HOME_SETTINGS));
        else external(new Intent(Settings.ACTION_SETTINGS));
    }
    private void section(LinearLayout p,String title){TextView t=theme.text(title,22,true);t.setPadding(dp(2),dp(20),0,dp(12));p.addView(t);}
    private void paragraph(LinearLayout p,String value){TextView t=theme.text(value,15,false);t.setTextColor(theme.secondary);t.setPadding(dp(2),dp(4),dp(2),dp(18));t.setLineSpacing(dp(3),1);p.addView(t);}
    private void open(AppCatalog.App app){external(app.launch());}
    private void external(Intent intent) {
        hideKeyboard();
        try{startActivity(intent);}catch(ActivityNotFoundException|SecurityException e){notice("This app or action is no longer available.");catalogDirty=true;refresh();}
    }
    private void notice(String value) {
        if(root==null || destroyed)return;
        TextView banner=theme.text(value,14,false);banner.setGravity(Gravity.CENTER);banner.setPadding(dp(18),dp(14),dp(18),dp(14));
        banner.setBackground(theme.shape(theme.control,24,true));FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);
        p.leftMargin=p.rightMargin=dp(20);p.bottomMargin=dp(110);root.addView(banner,p);
        main.postDelayed(()->{if(banner.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)banner.getParent()).removeView(banner);},4000);
    }
    private void windowColours() {
        Window w=getWindow();w.setBackgroundDrawable(new ColorDrawable(theme.bg));
        if(Build.VERSION.SDK_INT>=21){w.setStatusBarColor(theme.light&&Build.VERSION.SDK_INT<23?0xff4a1622:theme.bg);w.setNavigationBarColor(theme.light&&Build.VERSION.SDK_INT<26?0xff4a1622:theme.bg);}
        if(Build.VERSION.SDK_INT>=30) {
            WindowInsetsController bars=w.getInsetsController();
            int flags=WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            if(bars!=null)bars.setSystemBarsAppearance(theme.light?flags:0,flags);
        }else if(Build.VERSION.SDK_INT>=23) {
            int flags=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|(Build.VERSION.SDK_INT>=26?View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR:0);
            w.getDecorView().setSystemUiVisibility(theme.light?flags:0);
        }
    }
    private int dp(float n){return theme.dp(n);}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private static final class ModernHome {
        static boolean request(Activity a) {
            if(Build.VERSION.SDK_INT<29)return false;
            android.app.role.RoleManager roles=(android.app.role.RoleManager)a.getSystemService(Context.ROLE_SERVICE);
            if(roles==null || !roles.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME) || roles.isRoleHeld(android.app.role.RoleManager.ROLE_HOME))return false;
            try {a.startActivityForResult(roles.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME),10);return true;}
            catch(ActivityNotFoundException|SecurityException e){return false;}
        }
    }
    private static final class ModernBack {
        static void register(MainActivity a){if(Build.VERSION.SDK_INT>=33)a.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,()->a.go(HOME));}
    }
}
