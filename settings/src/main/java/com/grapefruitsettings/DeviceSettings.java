package com.grapefruitsettings;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.os.*;
import android.view.MenuItem;
import android.widget.Toast;
import org.json.*;
import java.io.*;
import java.util.*;

/** Owns device-page navigation. The old package name is accepted only as a compatibility input. */
public final class DeviceSettings {
    static final String DEVICE="com.grapefruitsettings.device";
    static final String OLD="com.android.settings";
    static final String PAGE="grapefruit.page";
    private static JSONObject catalog;
    private static Application application;
    private static final Set<Activity> resumed=Collections.newSetFromMap(new WeakHashMap<Activity,Boolean>());
    private static long bluetoothUntil;
    private DeviceSettings(){}

    static JSONObject catalog(Context c){
        if(catalog==null)try(InputStream in=c.getAssets().open("device-routes.json");ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);catalog=new JSONObject(out.toString("UTF-8"));
        }catch(IOException|JSONException e){throw new IllegalStateException("Device page catalog is missing",e);}
        return catalog;
    }
    public static void install(Application app){
        if(application==app)return;application=app;catalog=null;resumed.clear();bluetoothUntil=0;
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks(){
            public void onActivityResumed(Activity a){resumed.add(a);}
            public void onActivityPaused(Activity a){resumed.remove(a);}
            public void onActivityDestroyed(Activity a){resumed.remove(a);}
            public void onActivityCreated(Activity a,Bundle b){}
            public void onActivityStarted(Activity a){}
            public void onActivityStopped(Activity a){}
            public void onActivitySaveInstanceState(Activity a,Bundle b){}
        });
    }
    static String mainPage(String action){
        if("android.settings.SETTINGS".equals(action))return "home";
        if("android.settings.WIFI_SETTINGS".equals(action))return "networks";
        if("android.settings.WIFI_IP_SETTINGS".equals(action))return "wifi_advanced";
        if("android.settings.SOUND_SETTINGS".equals(action))return "sound";
        if("android.settings.DISPLAY_SETTINGS".equals(action))return "display";
        if("android.settings.DEVICE_INFO_SETTINGS".equals(action))return "about";
        if("android.settings.APPLICATION_SETTINGS".equals(action)||"android.settings.MANAGE_APPLICATIONS_SETTINGS".equals(action)||"android.settings.MANAGE_ALL_APPLICATIONS_SETTINGS".equals(action))return "apps";
        if("android.settings.INTERNAL_STORAGE_SETTINGS".equals(action)||"android.settings.MEMORY_CARD_SETTINGS".equals(action))return "storage";
        if("android.intent.action.POWER_USAGE_SUMMARY".equals(action))return "battery";
        return null;
    }
    static String initialPage(Context c,Intent i){
        String requested=i.getStringExtra(PAGE);if(requested!=null)return requested;
        String main=mainPage(i.getAction());return main==null?"home":main;
    }
    static String renamedClass(String name){return name!=null&&name.startsWith(OLD+".")?DEVICE+name.substring(OLD.length()):name;}
    private static boolean oldPackage(String name){return OLD.equals(name);}
    private static boolean extension(String value){return (OLD+".OEM_RADIO_INFO").equals(value)||(OLD+".OPERATOR_APPLICATION_SETTING").equals(value)||(OLD+".MANUFACTURER_APPLICATION_SETTING").equals(value);}
    private static String action(Context c,String value){
        if(value==null)return null;
        String prefix=c.getPackageName()+".route.";
        value=value.startsWith(prefix)?value.substring(prefix.length()):value;
        return value.startsWith(OLD+".")&&!extension(value)?DEVICE+value.substring(OLD.length()):value;
    }
    static Intent nativeIntent(Context c,String action){
        String cls=catalog(c).optJSONObject("activities").optString(action,"");
        if(cls.isEmpty())throw new ActivityNotFoundException("No bundled device page for "+action);
        return new Intent(action).setClassName(c.getPackageName(),cls);
    }
    static void open(Activity a,String action){
        if(action.contains("BLUETOOTH"))bluetoothUntil=SystemClock.elapsedRealtime()+30000;
        try{
            // Wi-Fi always uses our new UI; the OEM Wi-Fi list is no longer a fallback.
            String page=action.equals("android.settings.WIFI_SETTINGS")?"networks":action.equals("android.settings.WIFI_IP_SETTINGS")?"wifi_advanced":null;
            Intent target=page==null?nativeIntent(a,action):new Intent(a,MainActivity.class).putExtra(PAGE,page);
            a.getPackageManager().getActivityInfo(target.getComponent(),0);a.startActivity(target);
        }catch(ActivityNotFoundException|PackageManager.NameNotFoundException|SecurityException e){unavailable(a);}
    }
    static void details(Activity a,String packageName){
        try{Intent i=nativeIntent(a,"android.settings.APPLICATION_DETAILS_SETTINGS").setData(android.net.Uri.parse("package:"+packageName));a.getPackageManager().getActivityInfo(i.getComponent(),0);a.startActivity(i);}
        catch(ActivityNotFoundException|PackageManager.NameNotFoundException|SecurityException e){unavailable(a);}
    }
    static void hdmi(Activity a){
        Intent i=new Intent().setClassName(a.getPackageName(),DEVICE+".Settings")
                .putExtra(":android:show_fragment",DEVICE+".HdmiControllerActivity");
        try{a.getPackageManager().getActivityInfo(i.getComponent(),0);a.startActivity(i);}
        catch(ActivityNotFoundException|PackageManager.NameNotFoundException|SecurityException e){unavailable(a);}
    }
    private static void unavailable(Context c){Toast.makeText(c,"This device page is unavailable in this build.",Toast.LENGTH_LONG).show();}

    /** Returns a copy, preserving caller data, flags, result handling and unrelated app intents. */
    public static void incoming(Activity a){
        Intent i=route(a,a.getIntent(),false);
        // Public preference hosts may display their declared fragment only. Internal
        // SubSettings remains private and can host the other bundled device pages.
        try{
            ActivityInfo info=a.getPackageManager().getActivityInfo(i.getComponent(),PackageManager.GET_META_DATA);
            String fragment=i.getStringExtra(":android:show_fragment");
            String expected=info.metaData==null?null:info.metaData.getString(DEVICE+".FRAGMENT_CLASS");
            if(info.exported&&fragment!=null&&!fragment.equals(expected))i.removeExtra(":android:show_fragment");
        }catch(PackageManager.NameNotFoundException ignored){}
        a.setIntent(i);
    }
    public static Intent route(Context c,Intent source){return route(c,source,true);}
    private static Intent route(Context c,Intent source,boolean launching){
        if(source==null)throw new ActivityNotFoundException("Missing activity intent");
        Intent i=new Intent(source);String action=action(c,i.getAction());i.setAction(action);
        ComponentName component=i.getComponent();boolean own=false;
        if(component!=null){
            boolean old=oldPackage(component.getPackageName());
            if(old||c.getPackageName().equals(component.getPackageName())){
                String cls=component.getClassName();if(old&&cls.startsWith("."))cls=OLD+cls;cls=renamedClass(cls);
                if(old&&cls.equals(OLD))throw new ActivityNotFoundException("Invalid device component");
                i.setComponent(new ComponentName(c.getPackageName(),cls));own=true;
            }
        }
        if(oldPackage(i.getPackage())){i.setPackage(c.getPackageName());own=true;}
        // Normalize selectors and chooser payloads as well as ordinary startActivity calls.
        if(i.getSelector()!=null)i.setSelector(route(c,i.getSelector()));
        Parcelable nested=i.getParcelableExtra(Intent.EXTRA_INTENT);
        if(nested instanceof Intent)i.putExtra(Intent.EXTRA_INTENT,route(c,(Intent)nested));
        Bundle extras=i.getExtras();
        if(extras!=null){
            for(String key:new ArrayList<>(extras.keySet())){
                String renamed=key.startsWith(OLD+".")?DEVICE+key.substring(OLD.length()):key;
                Object value=extras.get(key);
                if(!renamed.equals(key)){i.removeExtra(key);Bundle entry=new Bundle();entry.putAll(extras);Object item=entry.get(key);putExtra(i,renamed,item);}
                if((key.equals(":android:show_fragment")||key.endsWith("FRAGMENT_CLASS"))&&value instanceof String)i.putExtra(renamed,renamedClass((String)value));
            }
        }
        if(launching&&component==null&&i.getSelector()==null){
            String page=mainPage(action);
            if(page!=null||own&&Intent.ACTION_MAIN.equals(action))i.setClass(c,MainActivity.class).putExtra(PAGE,page==null?"home":page);
            else {
                JSONObject routes=catalog(c).optJSONObject("activities");String cls=routes.optString(action==null?"":action,"");
                if(cls.isEmpty()&&action!=null&&action.startsWith(OLD+"."))cls=routes.optString(DEVICE+action.substring(OLD.length()),"");
                boolean contract=action!=null&&!extension(action)&&(action.startsWith("android.settings.")||action.startsWith(OLD+".")||action.startsWith(DEVICE+"."));
                if(!cls.isEmpty())i.setClassName(c.getPackageName(),cls);
                else if(own||contract)throw new ActivityNotFoundException("No bundled device handler for "+action);
            }
        }
        if(i.getComponent()!=null&&oldPackage(i.getComponent().getPackageName())&&!c.getPackageName().equals(OLD))throw new ActivityNotFoundException("External stock Settings target blocked");
        return i;
    }
    private static void putExtra(Intent i,String key,Object value){
        if(value instanceof String)i.putExtra(key,(String)value);
        else if(value instanceof Bundle)i.putExtra(key,(Bundle)value);
        else if(value instanceof Integer)i.putExtra(key,(Integer)value);
        else if(value instanceof Long)i.putExtra(key,(Long)value);
        else if(value instanceof Boolean)i.putExtra(key,(Boolean)value);
        else if(value instanceof Parcelable)i.putExtra(key,(Parcelable)value);
        else if(value instanceof java.io.Serializable)i.putExtra(key,(java.io.Serializable)value);
    }
    public static Intent[] routes(Context c,Intent[] intents){Intent[] out=new Intent[intents.length];for(int n=0;n<intents.length;n++)out[n]=route(c,intents[n]);return out;}
    public static void start(Context c,Intent i){c.startActivity(route(c,i));}
    public static void start(Context c,Intent i,Bundle b){c.startActivity(route(c,i),b);}
    public static PendingIntent pendingActivity(Context c,int request,Intent i,int flags){return PendingIntent.getActivity(c,request,route(c,i),flags);}
    public static PendingIntent pendingActivity(Context c,int request,Intent i,int flags,Bundle b){return PendingIntent.getActivity(c,request,route(c,i),flags,b);}
    public static ResolveInfo resolve(PackageManager pm,Intent i,int flags){return pm.resolveActivity(application==null?i:route(application,i),flags);}
    public static List<ResolveInfo> query(PackageManager pm,Intent i,int flags){return pm.queryIntentActivities(application==null?i:route(application,i),flags);}
    public static boolean acceptBluetooth(android.content.BroadcastReceiver receiver,Context c,Intent i){
        boolean replacement="replacement".equals(catalog(c).optString("mode"));
        boolean explicit=i.getComponent()!=null&&c.getPackageName().equals(i.getComponent().getPackageName());
        boolean active=SystemClock.elapsedRealtime()<bluetoothUntil;
        for(Activity a:resumed){String cls=a.getClass().getName();String fragment=a.getIntent().getStringExtra(":android:show_fragment");if(cls.contains("Bluetooth")||cls.contains("DevicePicker")||(fragment!=null&&fragment.contains(".bluetooth."))||(a instanceof MainActivity&&((MainActivity)a).bluetoothPage()))active=true;}
        if(!replacement&&!explicit&&!active)return false;
        if(receiver.isOrderedBroadcast())receiver.abortBroadcast();
        return true;
    }
}
