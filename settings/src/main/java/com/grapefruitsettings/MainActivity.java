package com.grapefruitsettings;

import android.app.*;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.AudioManager;
import android.net.wifi.*;
import android.os.*;
import android.provider.Settings;
import android.text.*;
import android.text.method.PasswordTransformationMethod;
import android.view.*;
import android.widget.*;
import com.grapefruitmusic.ui.DialogEditing;
import com.grapefruitmusic.ui.Fonts;
import com.grapefruitmusic.ui.UniversalTheme;
import java.util.*;

/** JP5S test frontend: public device controls, Grapefruit branding, and stock privileged pages. */
public final class MainActivity extends Activity {
    private SharedPreferences prefs;
    private Palette p;
    private LinearLayout content;
    private ScrollView scroll;
    private String page="home";
    private final Handler handler=new Handler(Looper.getMainLooper());
    private boolean listening;
    private Toggle wifiToggle, bluetoothToggle;
    private TextView wifiStatus,bluetoothStatus;
    private final BroadcastReceiver states=new BroadcastReceiver() {
        @Override public void onReceive(Context c,Intent i) { refreshRadios(); }
    };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);Fonts.init(this);
        prefs=getSharedPreferences("settings",MODE_PRIVATE);
        UniversalTheme.sync(this,prefs,false,new int[]{0,1,2,3});
        if(saved!=null)page=saved.getString("page","home");
        build();
    }
    @Override protected void onSaveInstanceState(Bundle state) { super.onSaveInstanceState(state);state.putString("page",page); }
    @Override protected void onResume() {
        super.onResume();
        if(UniversalTheme.sync(this,prefs,false,new int[]{0,1,2,3}))build();
        IntentFilter f=new IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION);
        f.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);f.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        if(Build.VERSION.SDK_INT>=33)registerReceiver(states,f,Context.RECEIVER_EXPORTED);else registerReceiver(states,f);
        listening=true;refreshRadios();
    }
    @Override protected void onPause() {
        if(listening){unregisterReceiver(states);listening=false;}
        handler.removeCallbacksAndMessages(null);super.onPause();
    }
    @Override public void onBackPressed() { if(!page.equals("home"))navigate("home");else super.onBackPressed(); }
    private int dp(float n){return p.dp(n);}
    private void navigate(String target){page=target;build();}

    private void build() {
        p=new Palette(this,prefs.getInt("theme",0));
        wifiToggle=null;bluetoothToggle=null;wifiStatus=null;bluetoothStatus=null;
        getWindow().setBackgroundDrawable(new ColorDrawable(p.bg));
        if(Build.VERSION.SDK_INT>=21){getWindow().setStatusBarColor(p.bg);getWindow().setNavigationBarColor(p.bg);}
        if(Build.VERSION.SDK_INT>=23)getWindow().getDecorView().setSystemUiVisibility(p.light?View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR:0);
        scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(p.bg);
        content=column();content.setPadding(dp(18),dp(20),dp(18),dp(28));scroll.addView(content);
        if(!page.equals("home"))add(p.chip("‹  Settings",false,v->navigate("home")));
        switch(page) {
            case "appearance": appearance(false);break;
            case "universal": appearance(true);break;
            case "wifi": wifi();break;
            case "bluetooth": bluetooth();break;
            case "sound": sound();break;
            case "display": display();break;
            case "storage": storage();break;
            case "battery": battery();break;
            case "apps": apps();break;
            case "about": about();break;
            default: page="home";home();
        }
        setContentView(scroll);refreshRadios();
    }
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout horizontal(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private TextView text(String s,int sp,boolean bold){TextView t=p.text(s,sp,bold);t.setTextColor(p.text);return t;}
    private void add(View v){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(8);content.addView(v,lp);}
    private void title(String s,String sub){add(text(s,30,true));if(sub!=null)note(sub);}
    private void note(String s){TextView t=text(s,14,false);t.setTextColor(p.secondary);t.setLineSpacing(dp(3),1);add(t);}
    private void section(String s){TextView t=text(s,13,true);t.setTextColor(p.accent);t.setPadding(0,dp(15),0,dp(3));add(t);}
    private void action(String title,View.OnClickListener click){add(p.chip(title,false,click));}
    private LinearLayout row(String kind,String title,String detail,View.OnClickListener click){
        LinearLayout r=horizontal();r.setPadding(dp(12),dp(12),dp(12),dp(12));r.setMinimumHeight(dp(72));
        r.setBackground(p.interactive(false));
        r.addView(new SettingIcon(this,p,kind),new LinearLayout.LayoutParams(dp(42),dp(42)));
        LinearLayout labels=column();labels.setPadding(dp(12),0,dp(6),0);
        labels.addView(text(title,16,true));
        if(detail!=null){TextView sub=text(detail,12,false);sub.setTextColor(p.secondary);labels.addView(sub);}
        r.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        if(click!=null){TextView arrow=text("›",26,false);arrow.setTextColor(p.secondary);r.addView(arrow);r.setOnClickListener(click);r.setFocusable(true);}
        add(r);return r;
    }
    private Toggle toggleRow(String kind,String title,boolean checked,Toggle.Listener listener){
        LinearLayout r=row(kind,title,null,null);
        Toggle t=new Toggle(this);t.setPalette(p);t.setChecked(checked);t.setContentDescription(title);t.setListener(listener);
        r.addView(t,new LinearLayout.LayoutParams(dp(60),dp(48)));return t;
    }
    private void home(){
        LinearLayout brand=horizontal();brand.addView(new SettingIcon(this,p,"settings"),new LinearLayout.LayoutParams(dp(58),dp(58)));
        TextView badge=text("  JP5S  /  TEST EDITION",12,true);badge.setTextColor(p.accent);brand.addView(badge);add(brand);
        title("Grapefruit\nSettings","Your JP5S, your way.");
        section("MAKE IT YOURS");
        row("theme","App appearance",Palette.NAMES[p.id],v->navigate("appearance"));
        int universal=ThemeProvider.state(this).theme;
        row("theme","Grapefruit Apps Theme",universal<0?"Choose a universal theme":Palette.NAMES[universal],v->navigate("universal"));
        section("CONNECTIONS");
        row("wifi","Wi-Fi","Network & saved connections",v->navigate("wifi"));
        row("bluetooth","Bluetooth","Devices & pairing",v->navigate("bluetooth"));
        row("settings","More connections","Airplane mode & network options",v->StockSettings.open(this,Settings.ACTION_WIRELESS_SETTINGS));
        section("DEVICE");
        row("sound","Sound","Volume & audio",v->navigate("sound"));
        row("display","Display","Brightness, rotation & sleep",v->navigate("display"));
        row("display","HDMI","JP5S display output",v->StockSettings.hdmi(this));
        row("storage","Storage","Internal & removable storage",v->navigate("storage"));
        row("battery","Battery","Charge & power usage",v->navigate("battery"));
        row("apps","Apps","Installed applications",v->navigate("apps"));
        section("PERSONAL");
        row("security","Security","Screen lock & credentials",v->StockSettings.open(this,Settings.ACTION_SECURITY_SETTINGS));
        row("keyboard","Language & input","Language, keyboard & dictionary",v->StockSettings.open(this,Settings.ACTION_INPUT_METHOD_SETTINGS));
        row("account","Accounts","Accounts & sync",v->StockSettings.open(this,Settings.ACTION_SYNC_SETTINGS));
        row("security","Backup & reset","Backup & factory reset",v->StockSettings.open(this,Settings.ACTION_PRIVACY_SETTINGS));
        section("SYSTEM");
        row("time","Date & time","Clock & time zone",v->StockSettings.open(this,Settings.ACTION_DATE_SETTINGS));
        row("accessibility","Accessibility","Reading & interaction",v->StockSettings.open(this,Settings.ACTION_ACCESSIBILITY_SETTINGS));
        row("developer","Developer options","Advanced device settings",v->StockSettings.open(this,Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
        row("about","About this JP5S","Device & Grapefruit Settings",v->navigate("about"));
        action("Open original Settings",v->StockSettings.original(this));
    }
    private void appearance(boolean universal){
        title(universal?"Grapefruit\nApps Theme":"App appearance",universal?"One choice across compatible Grapefruit apps.":"Four familiar themes. Pick your favourite.");
        if(universal){note("Victory Blue is not available in the standard Audio or TV/Movies apps. Their theme changes only when you select Grapefruit Dark, Grapefruit Light or Pitch Black universally.");}
        int selected=universal?ThemeProvider.state(this).theme:p.id;
        for(int i=0;i<Palette.NAMES.length;i++){
            final int id=i;Palette preview=new Palette(this,i);
            LinearLayout card=horizontal();card.setPadding(dp(14),dp(14),dp(14),dp(14));card.setBackground(preview.shape(preview.surface,24,true));
            SettingIcon icon=new SettingIcon(this,preview,"theme");card.addView(icon,new LinearLayout.LayoutParams(dp(44),dp(44)));
            TextView name=preview.text("  "+Palette.NAMES[i]+(selected==i?"  ✓":""),16,true);card.addView(name,new LinearLayout.LayoutParams(0,-2,1));
            Toggle demo=new Toggle(this);demo.setPalette(preview);demo.setChecked(true);demo.setEnabled(false);demo.setAlpha(1);demo.setFocusable(false);demo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            card.addView(demo,new LinearLayout.LayoutParams(dp(58),dp(48)));
            card.setContentDescription(Palette.NAMES[i]+(selected==i?", selected":""));card.setFocusable(true);
            card.setOnClickListener(v->{
                if(universal){if(!ThemeProvider.select(this,id)){toast("The theme could not be saved.");return;}UniversalTheme.sync(this,prefs,false,new int[]{0,1,2,3});}
                else prefs.edit().putInt("theme",id).commit();
                build();
            });add(card);
        }
        if(universal)note("Apps apply a universal selection when you return to them. You can still choose a theme inside each app until the next universal selection.");
        else action("Set a universal Apps Theme",v->navigate("universal"));
    }
    private WifiManager wifiManager(){return (WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);}
    private BluetoothAdapter bluetoothAdapter(){return BluetoothAdapter.getDefaultAdapter();}
    private void wifi(){
        title("Wi-Fi","Stay connected.");WifiManager w=wifiManager();
        if(w==null){note("Wi-Fi is unavailable.");return;}
        wifiToggle=toggleRow("wifi","Wi-Fi",w.isWifiEnabled(),(t,on)->{
            try{if(!w.setWifiEnabled(on)){t.setChecked(w.isWifiEnabled());toast("Use original Settings to change Wi-Fi.");}}
            catch(SecurityException e){t.setChecked(w.isWifiEnabled());StockSettings.open(this,Settings.ACTION_WIFI_SETTINGS);}
        });
        wifiStatus=text("",14,false);add(wifiStatus);
        action("Networks & saved connections  ›",v->StockSettings.open(this,Settings.ACTION_WIFI_SETTINGS));
        action("Add a network",v->networkDialog());
        action("Advanced Wi-Fi  ›",v->StockSettings.open(this,Settings.ACTION_WIFI_IP_SETTINGS));
    }
    private void bluetooth(){
        title("Bluetooth","Headphones, speakers & more.");BluetoothAdapter b=bluetoothAdapter();
        if(b==null){note("Bluetooth is unavailable.");return;}
        bluetoothToggle=toggleRow("bluetooth","Bluetooth",b.isEnabled(),(t,on)->{
            try{boolean accepted=on?b.enable():b.disable();if(!accepted){t.setChecked(b.isEnabled());toast("Use original Settings to change Bluetooth.");}}
            catch(SecurityException e){t.setChecked(b.isEnabled());StockSettings.open(this,Settings.ACTION_BLUETOOTH_SETTINGS);}
        });
        bluetoothStatus=text("",14,false);add(bluetoothStatus);
        action("Rename this device",v->renameBluetooth());
        section("PAIRED DEVICES");
        try{Set<BluetoothDevice> devices=b.getBondedDevices();if(devices.isEmpty())note("No paired devices yet.");
            for(BluetoothDevice d:devices)row("bluetooth",d.getName()==null?"Bluetooth device":d.getName(),d.getAddress(),v->StockSettings.open(this,Settings.ACTION_BLUETOOTH_SETTINGS));
        }catch(SecurityException e){note("Open Bluetooth settings to view paired devices.");}
        action("Pair & manage devices  ›",v->StockSettings.open(this,Settings.ACTION_BLUETOOTH_SETTINGS));
    }
    private void refreshRadios(){
        try{WifiManager w=wifiManager();if(w!=null){int state=w.getWifiState();if(wifiToggle!=null){wifiToggle.setChecked(state==WifiManager.WIFI_STATE_ENABLED||state==WifiManager.WIFI_STATE_ENABLING);wifiToggle.setEnabled(state!=WifiManager.WIFI_STATE_ENABLING&&state!=WifiManager.WIFI_STATE_DISABLING);}
            if(wifiStatus!=null){WifiInfo info=w.getConnectionInfo();String ssid=info==null?null:info.getSSID();boolean connected=w.isWifiEnabled()&&info!=null&&info.getNetworkId()>=0;wifiStatus.setText(connected?"Connected to "+(ssid==null?"Wi-Fi":ssid.replace("\"","")):(w.isWifiEnabled()?"Wi-Fi is on":"Wi-Fi is off"));}}
        }catch(SecurityException ignored){}
        try{BluetoothAdapter b=bluetoothAdapter();if(b!=null){int state=b.getState();if(bluetoothToggle!=null){bluetoothToggle.setChecked(state==BluetoothAdapter.STATE_ON||state==BluetoothAdapter.STATE_TURNING_ON);bluetoothToggle.setEnabled(state==BluetoothAdapter.STATE_ON||state==BluetoothAdapter.STATE_OFF);}if(bluetoothStatus!=null)bluetoothStatus.setText(b.isEnabled()?"Visible as "+b.getName():"Bluetooth is off");}}catch(SecurityException ignored){}
    }
    private void sound(){
        title("Sound","Find your comfortable volume.");AudioManager a=(AudioManager)getSystemService(AUDIO_SERVICE);if(a==null)return;
        volume(a,"Media",AudioManager.STREAM_MUSIC);volume(a,"Notifications",AudioManager.STREAM_NOTIFICATION);volume(a,"Alarm",AudioManager.STREAM_ALARM);
        toggleRow("sound","Touch sounds",systemInt(Settings.System.SOUND_EFFECTS_ENABLED,1)!=0,(t,on)->write(t,Settings.System.SOUND_EFFECTS_ENABLED,on?1:0));
        toggleRow("sound","Haptic feedback",systemInt(Settings.System.HAPTIC_FEEDBACK_ENABLED,1)!=0,(t,on)->write(t,Settings.System.HAPTIC_FEEDBACK_ENABLED,on?1:0));
        action("More sound settings  ›",v->StockSettings.open(this,Settings.ACTION_SOUND_SETTINGS));
    }
    private void volume(AudioManager a,String name,int stream){
        int max=a.getStreamMaxVolume(stream);slider(name,a.getStreamVolume(stream),max,value->{try{a.setStreamVolume(stream,value,0);}catch(SecurityException e){toast("Volume could not be changed.");}});
    }
    private interface IntAction {void apply(int n);}
    private void slider(String name,int current,int max,IntAction done){
        TextView label=text(name,16,true);add(label);
        SeekBar bar=new SeekBar(this);bar.setMax(max);bar.setProgress(Math.max(0,Math.min(max,current)));bar.setMinimumHeight(dp(48));bar.setContentDescription(name);
        android.graphics.drawable.GradientDrawable track=p.shape(p.control,10000,false);
        android.graphics.drawable.ClipDrawable progress=new android.graphics.drawable.ClipDrawable(p.gradient(),Gravity.LEFT,android.graphics.drawable.ClipDrawable.HORIZONTAL);
        android.graphics.drawable.LayerDrawable layers=new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{track,progress});layers.setId(0,android.R.id.background);layers.setId(1,android.R.id.progress);bar.setProgressDrawable(layers);
        android.graphics.drawable.GradientDrawable thumb=p.gradient();thumb.setSize(dp(24),dp(24));bar.setThumb(thumb);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){done.apply(b.getProgress());}public void onProgressChanged(SeekBar b,int value,boolean user){label.setText(name+"  "+Math.round(value*100f/Math.max(1,max))+"%");}});add(bar);
    }
    private void display(){
        title("Display","A little brighter. A little more you.");
        toggleRow("display","Automatic brightness",systemInt(Settings.System.SCREEN_BRIGHTNESS_MODE,0)==1,(t,on)->{write(t,Settings.System.SCREEN_BRIGHTNESS_MODE,on?1:0);build();});
        slider("Brightness",systemInt(Settings.System.SCREEN_BRIGHTNESS,128),255,value->{
            if(putSystem(Settings.System.SCREEN_BRIGHTNESS,Math.max(10,value))){putSystem(Settings.System.SCREEN_BRIGHTNESS_MODE,0);WindowManager.LayoutParams lp=getWindow().getAttributes();lp.screenBrightness=Math.max(10,value)/255f;getWindow().setAttributes(lp);}
        });
        toggleRow("display","Auto-rotate screen",systemInt(Settings.System.ACCELEROMETER_ROTATION,1)!=0,(t,on)->write(t,Settings.System.ACCELEROMETER_ROTATION,on?1:0));
        section("SLEEP");int timeout=systemInt(Settings.System.SCREEN_OFF_TIMEOUT,60000);
        int[] times={15000,30000,60000,120000,300000,600000,1800000};String[] names={"15 seconds","30 seconds","1 minute","2 minutes","5 minutes","10 minutes","30 minutes"};
        for(int i=0;i<times.length;i++){final int n=times[i];action((timeout==n?"✓  ":"")+names[i],v->{if(putSystem(Settings.System.SCREEN_OFF_TIMEOUT,n))build();});}
        action("More display settings  ›",v->StockSettings.open(this,Settings.ACTION_DISPLAY_SETTINGS));
    }
    private int systemInt(String key,int fallback){return Settings.System.getInt(getContentResolver(),key,fallback);}
    private boolean putSystem(String key,int value){
        try{if(Settings.System.putInt(getContentResolver(),key,value))return true;}catch(SecurityException e){}
        toast("Allow system settings changes in original Settings, then try again.");return false;
    }
    private void write(Toggle t,String key,int value){if(!putSystem(key,value))t.setChecked(value==0);}
    @SuppressWarnings("deprecation") private void storage(){
        title("Storage","Space for what you love.");
        storageVolume("Internal storage",Environment.getDataDirectory());
        if(Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())||Environment.MEDIA_MOUNTED_READ_ONLY.equals(Environment.getExternalStorageState()))storageVolume("Shared storage",Environment.getExternalStorageDirectory());
        action("Storage & SD card management  ›",v->StockSettings.open(this,Settings.ACTION_INTERNAL_STORAGE_SETTINGS));
    }
    @SuppressWarnings("deprecation") private void storageVolume(String name,java.io.File path){
        try{StatFs fs=new StatFs(path.getAbsolutePath());long block=fs.getBlockSize();long total=block*fs.getBlockCount();long free=block*fs.getAvailableBlocks();row("storage",name,android.text.format.Formatter.formatFileSize(this,free)+" free of "+android.text.format.Formatter.formatFileSize(this,total),null);}catch(IllegalArgumentException e){note(name+" is unavailable.");}
    }
    private void battery(){
        title("Battery","Ready for your next listen.");
        Intent b=registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if(b!=null){int level=b.getIntExtra(BatteryManager.EXTRA_LEVEL,0),scale=b.getIntExtra(BatteryManager.EXTRA_SCALE,100);int percent=scale>0?Math.round(level*100f/scale):0;
            add(text(percent+"%",56,true));int status=b.getIntExtra(BatteryManager.EXTRA_STATUS,-1);
            note(status==BatteryManager.BATTERY_STATUS_CHARGING?"Charging":status==BatteryManager.BATTERY_STATUS_FULL?"Fully charged":"On battery");
            row("battery","Temperature",String.format(Locale.getDefault(),"%.1f °C",b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,0)/10f),null);
        }action("Power usage  ›",v->StockSettings.open(this,"android.intent.action.POWER_USAGE_SUMMARY"));
    }
    private void apps(){
        title("Apps","Everything installed on your JP5S.");
        EditText search=new EditText(this);styleField(search,"Search apps",false);add(search);
        LinearLayout list=column();add(list);PackageManager pm=getPackageManager();List<ApplicationInfo> all=pm.getInstalledApplications(0);
        Collections.sort(all,(a,b)->String.valueOf(pm.getApplicationLabel(a)).compareToIgnoreCase(String.valueOf(pm.getApplicationLabel(b))));
        Runnable render=()->{list.removeAllViews();String query=search.getText().toString().toLowerCase(Locale.ROOT);for(ApplicationInfo app:all){String label=String.valueOf(pm.getApplicationLabel(app));if(!label.toLowerCase(Locale.ROOT).contains(query))continue;
            TextView item=p.chip(label,false,v->{Intent i=StockSettings.intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(android.net.Uri.parse("package:"+app.packageName));try{startActivity(i);}catch(ActivityNotFoundException|SecurityException e){StockSettings.open(this,Settings.ACTION_APPLICATION_SETTINGS);}});
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(8);list.addView(item,lp);
        }};
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){render.run();}public void afterTextChanged(Editable e){}});render.run();
    }
    private void about(){
        title("About this JP5S","Grapefruit Settings 1.0 · JP5S beta 1");
        row("about","Model",Build.MODEL,null);row("about","Android",Build.VERSION.RELEASE,null);row("about","Build",Build.DISPLAY,null);
        note("A separate Settings app for testing beside the original. Android's SettingsProvider stays in place. Original JP5S pages use Grapefruit colours and controls.");
        action("More device information  ›",v->StockSettings.open(this,Settings.ACTION_DEVICE_INFO_SETTINGS));
        action("Licences",v->message("Licences","Grapefruit Settings: GPL-3.0-or-later. Geist fonts: SIL Open Font License 1.1. Grapefruit toggle and palette adapted from Grapefruit Audio, TV and Launcher."));
    }
    private void styleField(EditText f,String hint,boolean secret){
        f.setTextColor(p.text);f.setHintTextColor(p.secondary);f.setTextSize(16);f.setTypeface(Fonts.regular());f.setSingleLine(true);f.setHint(hint);f.setContentDescription(hint);f.setMinimumHeight(dp(48));f.setPadding(dp(14),dp(10),dp(14),dp(10));f.setBackground(p.shape(p.control,22,true));
        f.setInputType(secret?android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD:android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        if(secret)f.setTransformationMethod(PasswordTransformationMethod.getInstance());
        DialogEditing.prepare(f);
    }
    private void editor(LinearLayout card,EditText field){
        LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-1,-2);fp.topMargin=dp(10);card.addView(field,fp);
        if(Build.VERSION.SDK_INT<23){
            LinearLayout actions=horizontal();String[] names={"Select all","Copy","Cut","Paste"};
            for(int i=0;i<4;i++){final int id=i;TextView b=p.chip(names[i],false,v->{if(id==0)DialogEditing.selectAll(field);else if(id==3)DialogEditing.paste(field);else DialogEditing.copy(field,id==2);});b.setTextSize(12);b.setPadding(dp(6),dp(8),dp(6),dp(8));actions.addView(b,new LinearLayout.LayoutParams(0,dp(48),1));}card.addView(actions);
        }
    }
    private Dialog dialog(String title,LinearLayout card){
        Dialog d=new Dialog(this,R.style.SettingsDialog);card.setPadding(dp(18),dp(18),dp(18),dp(18));card.setBackground(p.shape(p.surface,26,true));TextView heading=text(title,22,true);card.addView(heading,0);
        ScrollView sv=new ScrollView(this);sv.addView(card);d.setContentView(sv);d.show();Window w=d.getWindow();if(w!=null){w.setLayout(Math.min(dp(440),getResources().getDisplayMetrics().widthPixels-dp(24)),-2);w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);}
        return d;
    }
    private void dialogButton(LinearLayout card,String title,View.OnClickListener click){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(10);card.addView(p.chip(title,false,click),lp);}
    private void renameBluetooth(){
        BluetoothAdapter b=bluetoothAdapter();if(b==null)return;
        LinearLayout card=column();EditText name=new EditText(this);styleField(name,"Device name",false);try{name.setText(b.getName());}catch(SecurityException e){toast("Bluetooth permission is unavailable.");return;}editor(card,name);
        Dialog d=dialog("Rename device",card);
        dialogButton(card,"Save",v->{String n=name.getText().toString().trim();if(n.isEmpty()){name.setError("Enter a device name");return;}try{if(!b.setName(n)){toast("Turn on Bluetooth and try again.");return;}}catch(SecurityException e){toast("The device name could not be changed.");return;}d.dismiss();build();});dialogButton(card,"Cancel",v->d.dismiss());
    }
    private void networkDialog(){
        LinearLayout card=column();EditText ssid=new EditText(this),password=new EditText(this);styleField(ssid,"Network name (SSID)",false);styleField(password,"Password (blank for open)",true);editor(card,ssid);editor(card,password);
        TextView hint=text("For WPA/WPA2 personal or open networks. Other security types are available in original Wi-Fi settings.",13,false);hint.setTextColor(p.secondary);card.addView(hint);
        Dialog d=dialog("Add a network",card);
        dialogButton(card,"Save & connect",v->{
            String name=ssid.getText().toString(),pass=password.getText().toString();
            if(name.isEmpty()||name.getBytes(java.nio.charset.Charset.forName("UTF-8")).length>32){ssid.setError("Enter an SSID of 1–32 bytes");return;}
            if(!pass.isEmpty()&&!validPassword(pass)){password.setError("Use 8–63 ASCII characters or 64 hexadecimal digits");return;}
            WifiManager w=wifiManager();if(w==null||!w.isWifiEnabled()){toast("Turn on Wi-Fi first.");return;}
            WifiConfiguration config=new WifiConfiguration();config.SSID=quote(name);
            if(pass.isEmpty())config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
            else{config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);config.preSharedKey=pass.matches("[0-9a-fA-F]{64}")?pass:quote(pass);}
            try{int id=w.addNetwork(config);if(id<0){toast("This network could not be saved. Try original Wi-Fi settings.");return;}w.saveConfiguration();if(!w.enableNetwork(id,true)||!w.reconnect()){toast("Network saved. Open Wi-Fi settings to connect.");}else toast("Network saved; connecting…");d.dismiss();}catch(SecurityException e){toast("Use original Wi-Fi settings to add this network.");}
        });dialogButton(card,"Cancel",v->d.dismiss());
    }
    static String quote(String s){return "\""+s.replace("\\","\\\\").replace("\"","\\\"")+"\"";}
    static boolean validPassword(String s){return s.matches("[0-9a-fA-F]{64}")||(s.length()>=8&&s.length()<=63&&s.matches("[\\x20-\\x7e]+"));}
    private void message(String title,String body){LinearLayout card=column();TextView t=text(body,15,false);card.addView(t);Dialog d=dialog(title,card);dialogButton(card,"Close",v->d.dismiss());}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
}
