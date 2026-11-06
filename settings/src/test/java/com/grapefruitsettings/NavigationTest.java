package com.grapefruitsettings;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.Bundle;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowToast;
import java.util.Iterator;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=21)
public class NavigationTest {
    private Context context;
    @Before public void setup(){context=RuntimeEnvironment.getApplication();DeviceSettings.install((Application)context);}
    private ComponentName declare(String cls){ComponentName component=new ComponentName(context.getPackageName(),cls);Shadows.shadowOf(context.getPackageManager()).addActivityIfNotPresent(component);return component;}
    @Test public void everyCatalogRouteIsOwnedWithoutStockInstalled() throws Exception {
        try{context.getPackageManager().getPackageInfo("com.android.settings",0);fail("Fixture must not contain stock Settings");}catch(PackageManager.NameNotFoundException expected){}
        JSONObject routes=DeviceSettings.catalog(context).getJSONObject("activities");assertTrue(routes.length()>50);
        for(Iterator<String> it=routes.keys();it.hasNext();){String action=it.next();Intent intent=DeviceSettings.nativeIntent(context,action);assertEquals(context.getPackageName(),intent.getComponent().getPackageName());assertTrue(intent.getComponent().getClassName().startsWith("com.grapefruitsettings.device."));}
    }
    @Test public void securityPageLaunchesOwnedComponentWithNoFallback(){
        ComponentName expected=declare("com.grapefruitsettings.device.Settings$SecuritySettingsActivity");
        try(ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity a=c.get();DeviceSettings.open(a,"android.settings.SECURITY_SETTINGS");assertEquals(expected,Shadows.shadowOf(a).getNextStartedActivity().getComponent());assertNull(Shadows.shadowOf(a).getNextStartedActivity());
        }
    }
    @Test public void unavailablePreviewPageDoesNotLaunchStock(){
        try(ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup()){
            DeviceSettings.open(c.get(),"android.settings.SECURITY_SETTINGS");assertNull(Shadows.shadowOf(c.get()).getNextStartedActivity());assertTrue(ShadowToast.getTextOfLatestToast().contains("unavailable"));
        }
    }
    @Test public void appDetailsPreservesTargetPackageWhileOpeningOurUi(){
        ComponentName expected=declare("com.grapefruitsettings.device.applications.InstalledAppDetailsTop");
        try(ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup()){
            DeviceSettings.details(c.get(),"com.android.settings");Intent started=Shadows.shadowOf(c.get()).getNextStartedActivity();assertEquals(expected,started.getComponent());assertEquals("package:com.android.settings",started.getDataString());
        }
    }
    @Test public void legacyComponentsAndFragmentArgumentsAreRetargeted(){
        Intent original=new Intent().setClassName("com.android.settings","com.android.settings.ConfirmLockPattern").putExtra(":android:show_fragment","com.android.settings.SecuritySettings").putExtra("com.android.settings.ConfirmLockPattern.header","Header").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Intent moved=DeviceSettings.route(context,original);assertEquals(context.getPackageName(),moved.getComponent().getPackageName());assertEquals("com.grapefruitsettings.device.ConfirmLockPattern",moved.getComponent().getClassName());assertEquals("com.grapefruitsettings.device.SecuritySettings",moved.getStringExtra(":android:show_fragment"));assertEquals("Header",moved.getStringExtra("com.grapefruitsettings.device.ConfirmLockPattern.header"));assertEquals(original.getFlags(),moved.getFlags());assertEquals("com.android.settings",original.getComponent().getPackageName());
    }
    @Test public void settingsActionsUseGrapefruitPagesAndOwnedDevicePages(){
        Intent wifi=DeviceSettings.route(context,new Intent("android.settings.WIFI_SETTINGS"));assertEquals(MainActivity.class.getName(),wifi.getComponent().getClassName());assertEquals("networks",wifi.getStringExtra(DeviceSettings.PAGE));
        Intent nativePage=DeviceSettings.route(context,new Intent("android.settings.ACCESSIBILITY_SETTINGS"));assertEquals(context.getPackageName(),nativePage.getComponent().getPackageName());assertTrue(nativePage.getComponent().getClassName().endsWith("Settings$AccessibilitySettingsActivity"));
    }
    @Test public void selectorsChooserPayloadsAndActivityArraysCannotEscapeToStock(){
        Intent request=new Intent().setClassName("com.android.settings","com.android.settings.ChooseLockGeneric");
        Intent chooser=DeviceSettings.route(context,Intent.createChooser(request,"Choose"));Intent payload=chooser.getParcelableExtra(Intent.EXTRA_INTENT);assertEquals(context.getPackageName(),payload.getComponent().getPackageName());
        Intent selector=new Intent(Intent.ACTION_MAIN);selector.setSelector(request);assertEquals(context.getPackageName(),DeviceSettings.route(context,selector).getSelector().getComponent().getPackageName());
        Intent[] batch=DeviceSettings.routes(context,new Intent[]{request,new Intent("android.settings.SETTINGS")});for(Intent i:batch)assertEquals(context.getPackageName(),i.getComponent().getPackageName());
    }
    @Test public void unrelatedFrameworkAndAuthenticatorAppsKeepTheirOriginalIntents(){
        Intent auth=new Intent().setClassName("account.authenticator","account.authenticator.SignIn").putExtra("return",17).setData(Uri.parse("https://example.org/"));Intent result=DeviceSettings.route(context,auth);assertEquals(auth.getComponent(),result.getComponent());assertEquals(auth.getData(),result.getData());assertEquals(17,result.getIntExtra("return",0));
    }
    @Test public void legacyCustomActionsAreRenamedWithTheirBundledImplementations(){
        Intent moved=DeviceSettings.route(context,new Intent("com.android.settings.USER_DICTIONARY_EDIT"));assertEquals("com.grapefruitsettings.device.USER_DICTIONARY_EDIT",moved.getAction());assertTrue(moved.getComponent().getClassName().endsWith("UserDictionaryAddWordActivity"));
    }
    @Test public void bluetoothBackgroundEventsAreNotClaimedByInactiveTestApp(){
        BroadcastReceiver receiver=new BroadcastReceiver(){public void onReceive(Context c,Intent i){}};
        assertFalse(DeviceSettings.acceptBluetooth(receiver,context,new Intent("android.bluetooth.device.action.PAIRING_REQUEST")));
        assertTrue(DeviceSettings.acceptBluetooth(receiver,context,new Intent("android.bluetooth.device.action.PAIRING_REQUEST").setComponent(new ComponentName(context.getPackageName(),"receiver"))));
    }
    @Test public void vendorExtensionContractsArePreservedWithoutTargetingStock(){
        Intent extension=DeviceSettings.route(context,new Intent("com.android.settings.OEM_RADIO_INFO"));assertEquals("com.android.settings.OEM_RADIO_INFO",extension.getAction());assertNull(extension.getPackage());assertNull(extension.getComponent());
    }
    @Test public void exportedNativeHostsRejectCallerChosenFragments(){
        ComponentName component=declare("com.grapefruitsettings.device.Settings$SecuritySettingsActivity");ActivityInfo info=Shadows.shadowOf(context.getPackageManager()).addActivityIfNotPresent(component);info.exported=true;info.metaData=new Bundle();info.metaData.putString(DeviceSettings.DEVICE+".FRAGMENT_CLASS",DeviceSettings.DEVICE+".SecuritySettings");Shadows.shadowOf(context.getPackageManager()).addOrUpdateActivity(info);
        try(ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup()){
            c.get().setIntent(new Intent().setComponent(component).putExtra(":android:show_fragment",DeviceSettings.DEVICE+".MasterClearConfirm"));DeviceSettings.incoming(c.get());assertFalse(c.get().getIntent().hasExtra(":android:show_fragment"));
            c.get().setIntent(new Intent().setComponent(component).putExtra(":android:show_fragment",DeviceSettings.DEVICE+".SecuritySettings"));DeviceSettings.incoming(c.get());assertEquals(DeviceSettings.DEVICE+".SecuritySettings",c.get().getIntent().getStringExtra(":android:show_fragment"));
        }
    }
}
