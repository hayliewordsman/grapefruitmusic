// SPDX-License-Identifier: GPL-3.0-or-later
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
    private LauncherDrag.Root root;
    private GridView grid;
    private HomeSurface home,dragHome;
    private FrameLayout body;
    private View searchRow;
    private ViewGroup dragStrip;
    private LauncherDrag activeDrag;
    private boolean editing;
    private String settingsPage="";
    private AppAdapter adapter;
    private TextView subtitle, empty,titleView,editControl;
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
        syncUniversal();
        icons=new IconPack(this);
        if(state!=null) {
            screen=state.getInt("screen",HOME);dockSlot=state.getInt("slot",-1);query=state.getString("query","");
            settingsPage=state.getString("settingsPage","");editing=state.getBoolean("editing",false);
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
        if(syncUniversal())build();
        if(uiReleased) {
            uiReleased=false;
            if(grid!=null){
                grid.setAdapter(adapter);updateApps();
                if(Build.VERSION.SDK_INT>=21)grid.setSelectionFromTop(savedPosition,savedTop);
                else grid.setSelection(savedPosition);
            }
            if(home!=null)updateApps();
            updateDock();
        }
        refresh();
    }
    private boolean syncUniversal() {
        return com.grapefruitmusic.ui.UniversalTheme.sync(this,getSharedPreferences("launcher",MODE_PRIVATE),false,new int[]{0,1,2,3});
    }
    @Override protected void onStop() {finishDrag(false);resumed=false;releaseBackgroundUi();super.onStop();}
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
        if(home!=null){savedTop=home.getScrollY();home.releaseImages();}
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
        out.putString("settingsPage",settingsPage);out.putBoolean("editing",editing);
    }
    @Override protected void onNewIntent(Intent intent) {super.onNewIntent(intent);setIntent(intent);go(HOME);}
    @Override protected void onDestroy() {
        destroyed=true;if(registered)unregisterReceiver(packageChanges);
        if(home!=null)home.dispose();
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
        if(screen==HOME && next==HOME){savedPosition=0;savedTop=0;if(home!=null)home.scrollTo(0,0);updateApps();return;}
        screen=next;query="";settingsPage="";if(next!=PICK_DOCK)dockSlot=-1;build();
        if(next==APPS)refresh();
    }
    private void hideKeyboard() {
        View focused=getCurrentFocus();
        if(focused!=null)((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(focused.getWindowToken(),0);
    }
    @Override public void onBackPressed() {
        if(activeDrag!=null){finishDrag(false);return;}
        if(screen==SETTINGS && !settingsPage.isEmpty()){settingsPage="";build();return;}
        if(screen==HOME && editing){editing=false;updateEditing();return;}
        go(HOME);
    }

    private void build() {
        // Drop references to the previous hierarchy, including Settings' hidden app grid.
        if(home!=null)home.dispose();
        grid=null;adapter=null;home=null;body=null;empty=null;dockContainer=null;subtitle=null;titleView=null;searchRow=null;editControl=null;uiReleased=false;
        savedPosition=0;savedTop=0;
        theme=new LauncherTheme(this,store.theme());
        icons.screenChanged(theme.id);
        root=new LauncherDrag.Root(this);root.setBackgroundColor(theme.bg);root.setFocusableInTouchMode(true);
        if(Build.VERSION.SDK_INT>=30) {
            root.setOnApplyWindowInsetsListener((v,insets)->{
                android.graphics.Insets i=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()|WindowInsets.Type.ime());
                v.setPadding(i.left,i.top,i.right,i.bottom);return WindowInsets.CONSUMED;
            });
        } else root.setFitsSystemWindows(true);
        boolean wide=landscape();LinearLayout shell=wide?row():column();shell.setGravity(Gravity.FILL);
        if(wide)shell.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        root.addView(shell,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout page=column();shell.addView(page,wide?new LinearLayout.LayoutParams(0,-1,1):new LinearLayout.LayoutParams(-1,0,1));
        header(page);
        if(screen==SETTINGS)settings(page);
        else {
            if(screen==APPS || screen==PICK_DOCK)search(page);
            body=new FrameLayout(this);page.addView(body,new LinearLayout.LayoutParams(-1,0,1));
            if(screen==HOME) {
                home=homeSurface();home.setId(android.R.id.list);body.addView(home,new FrameLayout.LayoutParams(-1,-1));
            } else {
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
                if(screen==PICK_DOCK){store.dock(dockSlot,app.id());go(HOME);}else if(editing)appMenu(app,-1);else open(app);
            });
            grid.setOnItemLongClickListener((p,v,pos,id)->{if(!beginDrag(adapter.visible.get(pos),LauncherDrag.DRAWER,-1,v))appMenu(adapter.visible.get(pos),-1);return true;});
            body.addView(grid,new FrameLayout.LayoutParams(-1,-1));
            }
            empty=theme.text("",16,false);empty.setGravity(Gravity.CENTER);empty.setTextColor(theme.secondary);
            empty.setPadding(dp(28),dp(20),dp(28),dp(20));body.addView(empty,new FrameLayout.LayoutParams(-1,-1));
            updateApps();
            dockContainer=new DockBox();shell.addView(dockContainer,wide?new LinearLayout.LayoutParams(dp(92),-1):new LinearLayout.LayoutParams(-1,-2));updateDock();
        }
        root.setOnDragListener((v,event)->{
            if(event.getLocalState()!=activeDrag || activeDrag==null)return false;
            if(event.getAction()==DragEvent.ACTION_DRAG_ENDED)main.post(()->finishDrag(true));
            return event.getAction()==DragEvent.ACTION_DRAG_STARTED || event.getAction()==DragEvent.ACTION_DRAG_ENDED;
        });
        setContentView(root);windowColours();
    }
    private boolean landscape(){android.content.res.Configuration c=getResources().getConfiguration();return c.screenWidthDp>c.screenHeightDp;}
    private int columns() {
        float fontScale=getResources().getConfiguration().fontScale;
        int width=getResources().getConfiguration().screenWidthDp-24-(landscape()?92:0);
        return Math.max(2,Math.min(8,(int)(width/Math.max(88,80*fontScale))));
    }
    private void header(LinearLayout parent) {
        LinearLayout head=column();head.setPadding(dp(22),dp(18),dp(22),dp(6));parent.addView(head);
        LinearLayout tools=row();
        if(screen!=HOME)tools.addView(theme.chip(screen==SETTINGS&&!settingsPage.isEmpty()?"‹  Settings":"‹  Home",false,v->{if(screen==SETTINGS&&!settingsPage.isEmpty()){settingsPage="";build();}else go(HOME);}));
        else {
            TextView brand=theme.text("Grapefruit Launcher",14,true);brand.setTextColor(theme.accent);
            tools.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
            editControl=theme.chip(editing?"Done":"Edit",editing,v->{editing=!editing;updateEditing();});tools.addView(editControl);
        }
        if(screen!=SETTINGS) {
            if(screen!=HOME)tools.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
            TextView settings=theme.chip("Settings",false,v->go(SETTINGS));settings.setContentDescription("Launcher settings");tools.addView(settings);
        }
        head.addView(tools,new LinearLayout.LayoutParams(-1,-2));
        if(screen==SETTINGS && settingsPage.equals("about"))return;
        TextView title=theme.text(screen==HOME?"Home":screen==SETTINGS?"Settings":screen==PICK_DOCK?"Choose a dock app":"All Apps",32,true);
        titleView=title;title.setPadding(0,dp(20),0,dp(8));head.addView(title);
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
        searchRow=row;
    }
    private void updateApps() {
        if(screen==SETTINGS)return;
        if(home!=null)home.reload(apps,editing);
        List<AppCatalog.App> visible=new ArrayList<>();
        if(screen==HOME){for(String id:store.home()){AppCatalog.App a=AppCatalog.find(apps,id);if(a!=null)visible.add(a);}}
        else visible=AppCatalog.filter(apps,query);
        if(adapter!=null){adapter.visible=visible;adapter.notifyDataSetChanged();}
        if(screen!=HOME)subtitle.setText(screen==PICK_DOCK?"Slot "+(dockSlot+1)+" · tap an app to place it":apps.size()+" apps · hold and drag to place on Home");
        if(empty!=null) {
            empty.setText(loading?"Loading your apps…":screen==HOME?"Your Home is ready.\nOpen All Apps, then hold and drag an app here.":"No apps found.");
            empty.setVisibility(visible.isEmpty()?View.VISIBLE:View.GONE);
        }
    }
    private int iconCellHeight() {
        android.graphics.Paint font=new android.graphics.Paint();font.setTypeface(Fonts.medium());
        font.setTextSize(12*getResources().getDisplayMetrics().scaledDensity);
        return dp(58+9+12+16)+(int)Math.ceil(font.getFontSpacing()*3);
    }
    private View appCell(AppCatalog.App app,View recycled,boolean onHome) {
        LinearLayout cell;
        if(recycled instanceof LinearLayout)cell=(LinearLayout)recycled;
        else {
            cell=column();cell.setGravity(Gravity.CENTER_HORIZONTAL);cell.setPadding(dp(3),dp(6),dp(3),dp(6));
            cell.setLayoutParams(new AbsListView.LayoutParams(-1,iconCellHeight()));
            android.graphics.drawable.StateListDrawable states=new android.graphics.drawable.StateListDrawable();
            states.addState(new int[]{android.R.attr.state_pressed},theme.shape(theme.pressed,24,false));
            states.addState(new int[]{android.R.attr.state_focused},theme.shape(theme.control,24,true));
            states.addState(new int[]{},new ColorDrawable(0));cell.setBackground(states);
            ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            cell.addView(image,new LinearLayout.LayoutParams(dp(58),dp(58)));
            TextView label=theme.text("",12,false);label.setTypeface(Fonts.medium());label.setGravity(Gravity.CENTER);
            label.setSingleLine(false);label.setMaxLines(3);label.setEllipsize(TextUtils.TruncateAt.END);
            label.setPadding(dp(10),dp(8),dp(10),dp(8));label.setBackground(theme.shape(theme.control,100000,true));
            label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.topMargin=dp(9);cell.addView(label,lp);
        }
        cell.setTag(app);cell.setAlpha(1);cell.setContentDescription(app.label);
        ((TextView)cell.getChildAt(1)).setText(app.label);icons.bind((ImageView)cell.getChildAt(0),app,theme);
        if(onHome) {
            cell.setFocusable(true);
            cell.setOnClickListener(v->{AppCatalog.App selected=(AppCatalog.App)v.getTag();if(editing)appMenu(selected,-1);else open(selected);});
            cell.setOnLongClickListener(v->{AppCatalog.App selected=(AppCatalog.App)v.getTag();if(!beginDrag(selected,LauncherDrag.HOME,-1,v))appMenu(selected,-1);return true;});
        } else {cell.setOnClickListener(null);cell.setOnLongClickListener(null);cell.setClickable(false);cell.setLongClickable(false);cell.setFocusable(false);}
        return cell;
    }
    private final class AppAdapter extends BaseAdapter {
        List<AppCatalog.App> visible=new ArrayList<>();
        @Override public int getCount(){return visible.size();}
        @Override public Object getItem(int p){return visible.get(p);}
        @Override public long getItemId(int p){return p;}
        @Override public View getView(int p,View recycled,ViewGroup parent){return appCell(visible.get(p),recycled,false);}
    }
    private HomeSurface homeSurface() {
        HomeSurface surface=new HomeSurface(this,store,theme,landscape()?"landscape":"portrait",new HomeSurface.Callbacks(){
            public View cell(AppCatalog.App app,View recycled){return appCell(app,recycled,true);}
            public void dropped(LauncherDrag drag){if(drag.origin==LauncherDrag.DOCK)store.dock(drag.slot,"");}
        });
        surface.reload(apps,editing);return surface;
    }
    private void updateEditing() {
        if(editControl!=null){editControl.setText(editing?"Done":"Edit");editControl.setSelected(editing);editControl.setBackground(theme.interactive(editing));editControl.setTextColor(editing?theme.onAccent:theme.text);}
        if(home!=null)home.editing(editing);
        if(screen==HOME && subtitle!=null)subtitle.setText(editing?"Hold and drag to move. Tap an app for options.":DateFormat.getDateInstance(DateFormat.FULL).format(new Date()));
    }
    private LinearLayout dockContainer;
    private final class DockBox extends LinearLayout {
        DockBox(){super(MainActivity.this);setOrientation(VERTICAL);}
        @Override protected void onMeasure(int w,int h) {
            if(landscape() && getChildCount()>0) {
                ViewGroup dock=(ViewGroup)getChildAt(0);int room=Math.max(dp(100),MeasureSpec.getSize(h)-getPaddingTop()-getPaddingBottom());
                dock.getLayoutParams().height=Math.min(dp(400),room);
                int size=Math.max(dp(20),Math.min(dp(48),(dock.getLayoutParams().height-dp(10))/5-dp(8)));
                for(int i=0;i<dock.getChildCount();i++) {
                    View image=((ViewGroup)dock.getChildAt(i)).getChildAt(0);image.getLayoutParams().width=size;image.getLayoutParams().height=size;
                }
            }
            super.onMeasure(w,h);
        }
    }
    private void updateDock() {
        if(dockContainer==null || screen==SETTINGS)return;
        boolean wide=landscape();dockContainer.removeAllViews();dockContainer.setGravity(Gravity.CENTER);
        dockContainer.setPadding(dp(wide?6:16),dp(wide?12:18),dp(wide?12:16),dp(12));
        int clear=theme.bg&0x00ffffff;
        dockContainer.setBackground(new GradientDrawable(wide?GradientDrawable.Orientation.LEFT_RIGHT:GradientDrawable.Orientation.TOP_BOTTOM,new int[]{clear,theme.bg}));
        LinearLayout dock=wide?column():row();dock.setId(android.R.id.tabs);dock.setGravity(Gravity.CENTER);
        dock.setPadding(dp(5),dp(5),dp(5),dp(5));dock.setBackground(theme.shape(theme.surface,100000,true));
        if(Build.VERSION.SDK_INT>=21)dock.setElevation(dp(6));
        int width=Math.min(dp(400),getResources().getDisplayMetrics().widthPixels-dp(32));
        dockContainer.addView(dock,new LinearLayout.LayoutParams(wide?dp(74):width,wide?dp(400):dp(74)));
        for(int position=0;position<5;position++) {
            int slot=position<2?position:position-1;
            FrameLayout touch=new FrameLayout(this);touch.setFocusable(true);touch.setClickable(true);touch.setBackground(dockTouch());
            dock.addView(touch,wide?new LinearLayout.LayoutParams(-1,0,1):new LinearLayout.LayoutParams(0,-1,1));
            ImageView icon=new ImageView(this);icon.setScaleType(ImageView.ScaleType.FIT_CENTER);icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            touch.addView(icon,new FrameLayout.LayoutParams(dp(48),dp(48),Gravity.CENTER));
            if(position==2) {
                icon.setImageDrawable(new IconPack.Plate(theme,null,1));touch.setContentDescription("All Apps");touch.setOnClickListener(v->go(screen==APPS?HOME:APPS));
            } else {
                AppCatalog.App app=AppCatalog.find(apps,store.dock(slot));
                if(app==null){icon.setImageDrawable(new IconPack.Plate(theme,null,2));touch.setContentDescription("Add dock app, slot "+(slot+1));touch.setOnClickListener(v->pickDock(slot));}
                else {
                    icons.bind(icon,app,theme);touch.setContentDescription(app.label+", dock slot "+(slot+1));
                    touch.setOnClickListener(v->{if(editing)appMenu(app,slot);else open(app);});
                }
                touch.setOnLongClickListener(v->{if(app==null)pickDock(slot);else if(!beginDrag(app,LauncherDrag.DOCK,slot,v))appMenu(app,slot);return true;});
                touch.setOnDragListener((v,event)->{
                    if(!(event.getLocalState() instanceof LauncherDrag) || event.getLocalState()!=activeDrag)return false;
                    if(event.getAction()==DragEvent.ACTION_DRAG_STARTED)return true;
                    if(event.getAction()==DragEvent.ACTION_DRAG_ENTERED){v.setAlpha(.65f);return true;}
                    if(event.getAction()==DragEvent.ACTION_DRAG_EXITED || event.getAction()==DragEvent.ACTION_DRAG_ENDED){v.setAlpha(1);return true;}
                    if(event.getAction()==DragEvent.ACTION_DROP){dropDock(activeDrag,slot);v.setAlpha(1);return true;}
                    return true;
                });
            }
        }
    }
    private android.graphics.drawable.Drawable dockTouch() {
        android.graphics.drawable.StateListDrawable d=new android.graphics.drawable.StateListDrawable();
        d.addState(new int[]{android.R.attr.state_focused},theme.shape(theme.control,100000,true));
        d.addState(new int[]{android.R.attr.state_pressed},theme.shape(theme.pressed,100000,false));
        d.addState(new int[]{},new ColorDrawable(0));return d;
    }
    private boolean beginDrag(AppCatalog.App app,int origin,int slot,View source) {
        if(activeDrag!=null || body==null)return false;
        hideKeyboard();dismissDialog();LauncherDrag session=new LauncherDrag(app,origin,slot,source);activeDrag=session;
        android.graphics.drawable.Drawable image=null;
        if(source instanceof ViewGroup && ((ViewGroup)source).getChildAt(0) instanceof ImageView)image=((ImageView)((ViewGroup)source).getChildAt(0)).getDrawable();
        final android.graphics.drawable.Drawable shadow=image==null?new IconPack.Plate(theme,null,0):image;
        if(screen!=HOME) {
            dragHome=homeSurface();body.addView(dragHome,new FrameLayout.LayoutParams(-1,-1));
            if(grid!=null)grid.setVisibility(View.INVISIBLE);if(empty!=null)empty.setVisibility(View.GONE);if(searchRow!=null)searchRow.setVisibility(View.GONE);
            if(titleView!=null)titleView.setText("Place on Home");
            if(body.getWidth()>0 && body.getHeight()>0){dragHome.measure(View.MeasureSpec.makeMeasureSpec(body.getWidth(),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(body.getHeight(),View.MeasureSpec.EXACTLY));dragHome.layout(0,0,body.getWidth(),body.getHeight());}
        }
        LinearLayout strip=row();strip.setPadding(dp(14),dp(4),dp(14),dp(4));dragStrip=strip;
        for(int action=0;action<2;action++) {
            final boolean options=action==1;String label=options?"App options":origin==LauncherDrag.DRAWER?"Cancel":"Remove shortcut";
            TextView target=theme.chip(label,false,v->{});target.setContentDescription("Drop: "+label);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(48),1);lp.leftMargin=dp(4);strip.addView(target,lp);
            target.setOnDragListener((v,event)->{
                if(event.getLocalState()!=activeDrag || activeDrag==null)return false;
                if(event.getAction()==DragEvent.ACTION_DRAG_STARTED)return true;
                if(event.getAction()==DragEvent.ACTION_DRAG_ENTERED){v.setAlpha(.65f);return true;}
                if(event.getAction()==DragEvent.ACTION_DRAG_EXITED || event.getAction()==DragEvent.ACTION_DRAG_ENDED){v.setAlpha(1);return true;}
                if(event.getAction()==DragEvent.ACTION_DROP) {
                    if(options)activeDrag.options=true;
                    else if(activeDrag.origin==LauncherDrag.HOME){store.remove(activeDrag.app.id());activeDrag.accepted=true;}
                    else if(activeDrag.origin==LauncherDrag.DOCK){store.dock(activeDrag.slot,"");activeDrag.accepted=true;}
                    return true;
                }
                return true;
            });
        }
        root.addView(strip,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));source.setAlpha(.35f);
        View.DragShadowBuilder builder=new View.DragShadowBuilder(source){
            @Override public void onProvideShadowMetrics(android.graphics.Point size,android.graphics.Point touch){size.set(dp(72),dp(72));touch.set(dp(36),dp(36));}
            @Override public void onDrawShadow(android.graphics.Canvas canvas){android.graphics.Rect old=new android.graphics.Rect(shadow.getBounds());shadow.setBounds(dp(4),dp(4),dp(68),dp(68));shadow.draw(canvas);shadow.setBounds(old);}
        };
        boolean started=root.startAppDrag(session,builder);
        if(!started)finishDrag(false);return started;
    }
    private void dropDock(LauncherDrag drag,int target) {
        if(drag.origin==LauncherDrag.DOCK)store.swapDock(drag.slot,target);else store.dock(target,drag.app.id());
        if(drag.origin==LauncherDrag.HOME)store.remove(drag.app.id());drag.accepted=true;
    }
    private void finishDrag(boolean navigate) {
        LauncherDrag session=activeDrag;if(session==null)return;activeDrag=null;
        View source=session.source.get();if(source!=null)source.setAlpha(1);
        if(dragStrip!=null){root.removeView(dragStrip);dragStrip=null;}
        if(dragHome!=null){dragHome.dispose();if(dragHome.getParent() instanceof ViewGroup)((ViewGroup)dragHome.getParent()).removeView(dragHome);dragHome=null;}
        if(grid!=null)grid.setVisibility(View.VISIBLE);if(searchRow!=null)searchRow.setVisibility(View.VISIBLE);
        if(titleView!=null && screen!=HOME)titleView.setText(screen==PICK_DOCK?"Choose a dock app":"All Apps");
        if(!navigate)return;
        if(session.options){updateApps();appMenu(session.app,session.origin==LauncherDrag.DOCK?session.slot:-1);}
        else if(session.accepted){if(screen!=HOME)go(HOME);else {updateApps();updateDock();}}
        else updateApps();
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
            String[] directions={"Move left","Move right","Move up","Move down"};int[] dx={-1,1,0,0},dy={0,0,-1,1};
            for(int i=0;i<4;i++){final int index=i;labels.add(directions[i]);actions.add(()->{
                int step=store.subgrid()?1:2;if(home==null || !home.nudge(app.id(),dx[index]*step,dy[index]*step))notice("That space is occupied or outside Home.");
            });}
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
    private void actions(String title,List<String> labels,List<Runnable> choices) {actions(title,labels,choices,-1);}
    private void actions(String title,List<String> labels,List<Runnable> choices,int chosen) {
        dismissDialog();dialog=new Dialog(this,R.style.LauncherDialogWindow);
        LinearLayout card=column();card.setPadding(dp(18),dp(22),dp(18),dp(16));card.setBackground(theme.shape(theme.surface,28,true));
        TextView name=theme.text(title,24,true);name.setPadding(dp(8),0,dp(8),dp(12));card.addView(name);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);LinearLayout body=column();scroll.addView(body);
        for(int i=0;i<labels.size();i++) {
            final Runnable choice=choices.get(i);TextView button=theme.chip(labels.get(i),i==chosen,v->{dismissDialog();choice.run();});
            button.setContentDescription(labels.get(i)+(i==chosen?", selected":""));
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
        ScrollView scroll=new ScrollView(this);scroll.setVerticalScrollBarEnabled(false);scroll.setFillViewport(true);
        LinearLayout page=new LinearLayout(this){
            @Override protected void onMeasure(int w,int h){int side=Math.max(0,(View.MeasureSpec.getSize(w)-dp(640))/2);setPadding(side,dp(8),side,dp(32));super.onMeasure(w,h);}
        };
        page.setOrientation(LinearLayout.VERTICAL);scroll.addView(page);parent.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        if(settingsPage.equals("about")){about(page);return;}
        settingSection(page,"Appearance");
        settingValue(page,"Theme",LauncherTheme.NAMES[theme.id],this::themePicker);
        settingFooter(page,"Your theme colors the background, icon plates, label chips and floating dock. No wallpaper is displayed.");
        settingSection(page,"Home screen");
        settingValue(page,"Icon placement",store.subgrid()?"Half-cell subgrid":"Grid",()->{
            List<String> names=Arrays.asList("Grid","Half-cell subgrid");
            actions("Icon placement",names,Arrays.asList(()->{store.subgrid(false);build();},()->{store.subgrid(true);build();}),store.subgrid()?1:0);
        });
        settingValue(page,"Landscape dock","Right side",null);
        settingValue(page,"Reset dock to suggested apps",null,()->{store.resetDock(AppCatalog.suggestedDock(getApplicationContext(),apps));notice("Dock reset to available Phone, Messages, Chrome and Grapefruit Audio apps.");});
        settingFooter(page,"Hold and drag an app to any free Home space or dock slot. A single occupied Home space swaps with the icon you move. Half-cell subgrid allows finer placement. Portrait and landscape arrangements are saved separately.");
        settingSection(page,"Using your launcher");
        settingValue(page,"Set as default launcher",null,this::defaultHome);
        settingValue(page,"Arranging apps","How to",()->showText("Arranging apps","Hold an app to drag it. Drag from All Apps to add a Home shortcut. Move Home and dock shortcuts between those areas, or swap dock positions. All Apps stays fixed in the middle.\n\nDrop on App options for app info or uninstall. Drop on Remove shortcut to remove a Home or dock shortcut. An uninstall always opens Android’s confirmation.\n\nTap Edit on Home, then tap an app for options and directional movement controls. Tap Done to finish. Drag near the top or bottom edge to scroll Home. Empty spaces stay empty.\n\nGrid uses full cells; Half-cell subgrid adds half-cell snapping without overlapping icons. Switching off subgrid temporarily projects half-cell positions onto the normal grid. Your saved fine positions return when you turn it back on."));
        settingFooter(page,"Icons are cached on this device in all four themes. The small RAM cache and visible-area-only Home cells keep background memory use down.");
        settingSection(page,"About");
        settingValue(page,"About Grapefruit Launcher","Version "+versionName(),()->{settingsPage="about";build();});
        settingValue(page,"Privacy","On this device",()->showText("Privacy","Grapefruit Launcher has no Internet permission, accounts, advertisements, analytics, telemetry or tracking SDKs. It does not send the installed-app list anywhere.\n\nHome positions, dock choices, theme settings and prepared icon PNGs stay in this app’s private internal storage. Removing the app or clearing its storage removes them.\n\nSource and support links open your browser only when you choose them. App launching and uninstall confirmations are handled by Android."));
        settingFooter(page,"Free and open-source software · GNU GPL version 3 or later.");
    }
    private void themePicker() {
        List<Runnable> choices=new ArrayList<>();
        for(int i=0;i<LauncherTheme.NAMES.length;i++){final int selected=i;choices.add(()->{store.theme(selected);build();});}
        actions("Theme",Arrays.asList(LauncherTheme.NAMES),choices,theme.id);
    }
    private void about(LinearLayout page) {
        LinearLayout hero=column();hero.setGravity(Gravity.CENTER_HORIZONTAL);hero.setPadding(dp(16),dp(8),dp(16),dp(12));
        ImageView icon=new ImageView(this);icon.setImageResource(R.mipmap.ic_launcher_foreground);icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        hero.addView(icon,new LinearLayout.LayoutParams(dp(88),dp(88)));
        TextView name=LauncherBrand.wordmark(this,theme);name.setPadding(0,dp(12),0,dp(10));hero.addView(name,new LinearLayout.LayoutParams(-1,-2));
        TextView version=theme.text("Version "+versionName(),15,false);version.setTextColor(theme.secondary);version.setGravity(Gravity.CENTER);hero.addView(version);
        TextView copyright=theme.text("Copyright © 2026 MagicFlute LLC",13,false);copyright.setTextColor(theme.secondary);copyright.setGravity(Gravity.CENTER);copyright.setPadding(0,dp(6),0,0);hero.addView(copyright);
        page.addView(hero);
        settingSection(page,"License");
        settingValue(page,"GNU GPL","Version 3 or later",()->assetText("GNU General Public License","GPL-3.0.txt"));
        settingLink(page,"Source Code","View on GitHub",LauncherBrand.SOURCE);
        settingFooter(page,"Grapefruit Launcher is free software: you can use, study, share and change it under the GNU General Public License, version 3 or any later version. It comes with no warranty, including no implied warranty of merchantability or fitness for a particular purpose.");
        settingSection(page,"Developer");
        settingValue(page,"Developer","Haylie",null);
        settingLink(page,"Report an issue","GitHub","https://github.com/hayliewordsman/grapefruitmusic/issues");
        settingFooter(page,"Tap links to open them, or touch and hold to copy.");
        settingSection(page,"Acknowledgements");
        settingValue(page,"Typeface and wordmark","SIL OFL 1.1",()->assetText("Geist font licence","fonts/Geist-OFL.txt"));
        settingFooter(page,"Geist and Geist Pixel Triangle are by Vercel. This app has its own Triangle wordmark subset for Grapefruit Launcher. Its original branding, teardrop plates and launcher code are provided in the GPL source. Installed app icons and names belong to their respective authors and are displayed locally.");
        settingValue(page,"Open Source Licences",null,()->assetText("Open Source Licences","NOTICE.txt","fonts/Geist-OFL.txt","GPL-3.0.txt"));
        settingFooter(page,"The runtime uses the Android framework and bundled open-source fonts. No proprietary SDK or online service is required.");
    }
    private String versionName(){try{return getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(Exception e){return "1.0-beta5";}}
    private void settingSection(LinearLayout page,String title){TextView text=theme.text(title,15,true);text.setTextColor(theme.accent);text.setPadding(dp(18),dp(24),dp(16),dp(6));page.addView(text);}
    private void settingFooter(LinearLayout page,String value){TextView text=theme.text(value,13,false);text.setTextColor(theme.secondary);text.setLineSpacing(dp(2),1);text.setPadding(dp(18),dp(6),dp(18),0);page.addView(text);}
    private View settingValue(LinearLayout page,String label,String value,Runnable click) {
        LinearLayout row=row();row.setMinimumHeight(dp(56));row.setPadding(dp(22),dp(8),dp(16),dp(8));row.setBackground(theme.interactive(false));
        TextView name=theme.text(label,16,false);name.setMaxLines(3);row.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        if(value!=null){TextView detail=theme.text(value,15,false);detail.setTextColor(theme.secondary);detail.setPadding(dp(8),0,0,0);detail.setMaxLines(2);row.addView(detail);}
        if(click!=null){TextView arrow=theme.text("›",22,false);arrow.setTextColor(theme.secondary);arrow.setPadding(dp(8),0,0,0);row.addView(arrow);row.setFocusable(true);row.setOnClickListener(v->click.run());}
        row.setContentDescription(label+(value==null?"":", "+value));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(dp(12),dp(4),dp(12),dp(4));page.addView(row,lp);return row;
    }
    private void settingLink(LinearLayout page,String label,String value,String url) {
        View row=settingValue(page,label,value,()->external(new Intent(Intent.ACTION_VIEW,Uri.parse(url))));
        row.setOnLongClickListener(v->{((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText(label,url));notice("Link copied.");return true;});
    }
    private void assetText(String title,String... paths) {
        StringBuilder content=new StringBuilder();
        try {
            for(String path:paths)try(java.io.InputStream in=getAssets().open(path);java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()) {
                byte[] buffer=new byte[4096];int count;while((count=in.read(buffer))!=-1)out.write(buffer,0,count);content.append(out.toString("UTF-8")).append("\n\n");
            }
        }catch(java.io.IOException e){notice("The licence could not be opened.");return;}
        showText(title,content.toString());
    }
    private void showText(String title,String value) {
        dismissDialog();dialog=new Dialog(this,R.style.LauncherDialogWindow);LinearLayout card=column();card.setPadding(dp(18),dp(20),dp(18),dp(16));card.setBackground(theme.shape(theme.surface,28,true));
        TextView heading=theme.text(title,22,true);heading.setPadding(0,0,0,dp(12));card.addView(heading);
        ScrollView scroll=new ScrollView(this);TextView text=theme.text(value,14,false);text.setLineSpacing(dp(2),1);scroll.addView(text);
        card.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));card.addView(theme.chip("Close",false,v->dismissDialog()));
        dialog.setContentView(card);dialog.show();
        if(dialog.getWindow()!=null)dialog.getWindow().setLayout(Math.min(dp(520),getResources().getDisplayMetrics().widthPixels-dp(28)),(int)(getResources().getDisplayMetrics().heightPixels*.8f));
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
        static void register(MainActivity a){if(Build.VERSION.SDK_INT>=33)a.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,()->a.onBackPressed());}
    }
}
