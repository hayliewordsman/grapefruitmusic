package com.grapefruitsettings;

import android.content.*;
import android.net.DhcpInfo;
import android.net.wifi.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.time.Duration;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.*;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=21,qualifiers="w320dp-h568dp-mdpi")
public class WifiTest {
    static final class Radio implements WifiNetworks.Backend {
        boolean on=true,failScan,failSave,failConnect,failRead;
        final List<ScanResult> scans=new ArrayList<>();final List<WifiConfiguration> saved=new ArrayList<>();
        WifiInfo info=ShadowWifiInfo.newInstance();WifiConfiguration written;int added,updated,removed=-1,selected=-1,disconnects;
        public boolean enabled(){return on;}public boolean enable(boolean enabled){on=enabled;return true;}public boolean scan(){return !failScan;}
        public List<ScanResult> scans(){if(failRead)throw new SecurityException();return scans;}
        public List<WifiConfiguration> saved(){return saved;}public WifiInfo info(){return info;}public DhcpInfo dhcp(){return new DhcpInfo();}
        public int add(WifiConfiguration c){written=c;added++;c.networkId=20;saved.add(c);return 20;}public int update(WifiConfiguration c){written=c;updated++;return c.networkId;}
        public boolean save(){return !failSave;}public boolean select(int id){selected=id;return !failConnect;}public boolean reconnect(){return !failConnect;}
        public boolean disconnect(){disconnects++;return true;}public boolean remove(int id){removed=id;saved.removeIf(c->c.networkId==id);return true;}
    }
    private Radio radio;private WifiNetworks networks;
    @Before public void setup(){radio=new Radio();networks=new WifiNetworks(radio);RuntimeEnvironment.getApplication().getSharedPreferences("settings",0).edit().clear().commit();ThemeProvider.prefs(RuntimeEnvironment.getApplication()).edit().clear().commit();}
    static ScanResult scan(String ssid,String caps,int rssi){ScanResult r=ReflectionHelpers.callConstructor(ScanResult.class);r.SSID=ssid;r.capabilities=caps;r.level=rssi;r.frequency=2412;return r;}
    static WifiConfiguration saved(String ssid,int id,WifiNetworks.Security security){WifiConfiguration c=new WifiConfiguration();c.SSID=MainActivity.quote(ssid);c.networkId=id;c.allowedKeyManagement.set(security==WifiNetworks.Security.EAP?WifiConfiguration.KeyMgmt.WPA_EAP:security==WifiNetworks.Security.PSK?WifiConfiguration.KeyMgmt.WPA_PSK:WifiConfiguration.KeyMgmt.NONE);c.preSharedKey="*";return c;}
    @Test public void scansDeduplicateBySsidAndSecurityAndPreferStrongest(){
        radio.scans.add(scan("Home","[WPA2-PSK-CCMP][ESS]",-80));radio.scans.add(scan("Home","[WPA2-PSK-CCMP][ESS]",-45));radio.scans.add(scan("Home","[ESS]",-40));radio.scans.add(scan("","[ESS]",-20));radio.saved.add(saved("Home",7,WifiNetworks.Security.PSK));networks.refresh();
        List<WifiNetworks.Entry> entries=networks.nearby();assertEquals(2,entries.size());assertEquals(WifiNetworks.Security.OPEN,entries.get(0).security);assertFalse(entries.get(0).saved());assertEquals(-45,entries.get(1).scan.level);assertEquals(7,entries.get(1).config.networkId);
    }
    @Test public void savedReconnectNeverOverwritesMaskedPassword(){
        radio.saved.add(saved("Home",7,WifiNetworks.Security.PSK));networks.refresh();assertTrue(networks.connect(7));assertEquals(7,radio.selected);assertEquals(0,radio.added);assertEquals(0,radio.updated);assertEquals("*",radio.saved.get(0).preSharedKey);
    }
    @Test public void passwordChangeCopiesConfigurationAndKeepsOtherSettings(){
        WifiConfiguration original=saved("Home",7,WifiNetworks.Security.PSK);original.hiddenSSID=true;original.BSSID="00:11:22:33:44:55";radio.saved.add(original);networks.refresh();
        assertTrue(networks.save("Home",WifiNetworks.Security.PSK,"correct password",original,true,true));assertEquals(1,radio.updated);assertEquals(0,radio.added);assertEquals(7,radio.written.networkId);assertEquals(original.BSSID,radio.written.BSSID);assertTrue(radio.written.hiddenSSID);assertEquals("*",original.preSharedKey);assertEquals("\"correct password\"",radio.written.preSharedKey);
    }
    @Test public void manualAddUpdatesMatchingProfileAndSupportsHiddenNetworks(){
        radio.saved.add(saved("Home",7,WifiNetworks.Security.PSK));networks.refresh();assertTrue(networks.save("Home",WifiNetworks.Security.PSK,"new password",null,true,false));assertEquals(1,radio.updated);assertTrue(radio.written.hiddenSSID);assertEquals(-1,radio.selected);
    }
    @Test public void openWepAndPskAreExplicitAndValidated(){
        assertNotNull(WifiNetworks.validate("Home",WifiNetworks.Security.PSK,""));assertNull(WifiNetworks.validate("Home",WifiNetworks.Security.OPEN,""));
        WifiConfiguration open=WifiNetworks.configuration("Open",WifiNetworks.Security.OPEN,"",null,false);assertTrue(open.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.NONE));assertNull(open.preSharedKey);
        WifiConfiguration wep=WifiNetworks.configuration("Old",WifiNetworks.Security.WEP,"1234567890",null,false);assertEquals("1234567890",wep.wepKeys[0]);assertNull(wep.preSharedKey);
        assertNotNull(WifiNetworks.validate("Home",WifiNetworks.Security.WEP,"bad"));assertNotNull(WifiNetworks.validate(String.join("",Collections.nCopies(17,"é")),WifiNetworks.Security.OPEN,""));
        assertEquals(WifiNetworks.Security.UNSUPPORTED,WifiNetworks.security("[WAPI-PSK][ESS]"));assertEquals(WifiNetworks.Security.UNSUPPORTED,WifiNetworks.security("[SAE][ESS]"));assertEquals(WifiNetworks.Security.PSK,WifiNetworks.security("[PSK+SAE][ESS]"));
    }
    @Test public void savedEnterpriseCanConnectWithoutChangingCertificates(){
        radio.saved.add(saved("Work",8,WifiNetworks.Security.EAP));networks.refresh();assertEquals(WifiNetworks.Security.EAP,networks.saved().get(0).security);assertTrue(networks.connect(8));assertEquals(0,radio.updated);
    }
    @Test public void connectedHiddenNetworkIsNotReportedOutOfRange(){
        WifiConfiguration hidden=saved("Hidden",7,WifiNetworks.Security.PSK);hidden.hiddenSSID=true;radio.saved.add(hidden);ShadowWifiInfo info=Shadows.shadowOf(radio.info);info.setNetworkId(7);info.setSSID("Hidden");info.setSupplicantState(SupplicantState.COMPLETED);networks.refresh();
        assertTrue(networks.saved().get(0).connected);assertFalse(networks.saved().get(0).detail().contains("out of range"));
    }
    @Test public void failedScanAuthenticationAndReadHaveRecoverableStatus(){
        radio.failScan=true;assertFalse(networks.scan(true));assertTrue(networks.feedback.contains("retry"));radio.failScan=false;assertTrue(networks.scan(true));
        Intent auth=new Intent(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION).putExtra(WifiManager.EXTRA_SUPPLICANT_ERROR,WifiManager.ERROR_AUTHENTICATING);networks.event(auth);assertTrue(networks.status().contains("Authentication failed"));networks.event(new Intent(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));assertTrue(networks.status().contains("Authentication failed"));
        radio.failRead=true;networks.refresh();assertTrue(networks.feedback.contains("could not be read"));
    }
    @Test public void connectFailureAndTimeoutDoNotPretendToBeConnected(){
        radio.failConnect=true;assertFalse(networks.connect(7));radio.failConnect=false;assertTrue(networks.connect(7));ShadowSystemClock.advanceBy(Duration.ofSeconds(31));networks.connectionTimeout();assertTrue(networks.status().contains("timed out"));
    }
    @Test public void forgetAndDisconnectUseOnlyWifiBackend(){
        radio.saved.add(saved("Home",7,WifiNetworks.Security.PSK));networks.refresh();assertTrue(networks.disconnect());assertEquals(1,radio.disconnects);assertTrue(networks.forget(7));assertEquals(7,radio.removed);assertTrue(networks.saved().isEmpty());
    }
    private ActivityController<MainActivity> launch(){ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class);c.get().wifiNetworks=networks;return c.setup().visible();}
    @Test public void networkSelectorAndPasswordDialogStayInsideAppAndKeepEditingControls(){
        radio.scans.add(scan("Home","[WPA2-PSK-CCMP][ESS]",-40));networks.refresh();
        try(ActivityController<MainActivity> c=launch()){
            MainActivity a=c.get();click(find(root(a),"Wi-Fi"));click(find(root(a),"Networks & saved connections"));click(find(root(a),"Home"));
            android.app.Dialog d=ShadowDialog.getLatestDialog();assertTrue(d.isShowing());View view=d.getWindow().getDecorView();EditText password=edit(view);assertNotNull(password);assertFalse(password.isLongClickable());assertNotNull(find(view,"Select all"));assertNotNull(find(view,"Paste"));
            password.setText("correct password");click(find(view,"Connect"));assertEquals("\"correct password\"",radio.written.preSharedKey);assertEquals(20,radio.selected);assertNull(Shadows.shadowOf(a).getNextStartedActivity());
        }
    }
    @Test public void broadcastsDoNotDestroyPasswordDraftAndBackDismissesDialogFirst(){
        radio.scans.add(scan("Home","[WPA2-PSK-CCMP][ESS]",-40));networks.refresh();
        try(ActivityController<MainActivity> c=launch()){
            MainActivity a=c.get();click(find(root(a),"Wi-Fi"));click(find(root(a),"Networks & saved connections"));click(find(root(a),"Home"));android.app.Dialog d=ShadowDialog.getLatestDialog();EditText f=edit(d.getWindow().getDecorView());f.setText("still typing");
            a.sendBroadcast(new Intent(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));Shadows.shadowOf(Looper.getMainLooper()).idle();assertTrue(d.isShowing());assertEquals("still typing",f.getText().toString());a.onBackPressed();assertFalse(d.isShowing());assertNotNull(find(root(a),"Rescan"));
        }
    }
    @Test public void wifiPasswordToolbarEditsTheSelectionAndShowPasswordKeepsCursor(){
        radio.scans.add(scan("Studio","[WPA2-PSK-CCMP][ESS]",-45));networks.refresh();
        try(ActivityController<MainActivity> c=launch()){
            MainActivity a=c.get();click(find(root(a),"Wi-Fi"));click(find(root(a),"Networks & saved connections"));click(find(root(a),"Studio"));View view=ShadowDialog.getLatestDialog().getWindow().getDecorView();EditText f=edit(view);
            f.setText("hello world");f.setSelection(6,11);click(find(view,"Cut"));assertEquals("hello ",f.getText().toString());f.setSelection(0,5);click(find(view,"Paste"));assertEquals("world ",f.getText().toString());assertEquals(5,f.getSelectionStart());
            ((Toggle)described(view,"Show password")).performClick();assertEquals(5,f.getSelectionStart());assertNull(f.getTransformationMethod());click(find(view,"Select all"));assertEquals(0,f.getSelectionStart());assertEquals(6,f.getSelectionEnd());
        }
    }
    @Test @Config(sdk=28,qualifiers="w360dp-h640dp-mdpi") @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void renderWifiAndPasswordDialogInEveryTheme() throws Exception {
        java.io.File folder=new java.io.File("build/wifi-previews");folder.mkdirs();
        radio.scans.add(scan("Home","[WPA2-PSK-CCMP][ESS]",-40));radio.scans.add(scan("Studio","[WPA2-PSK-CCMP][ESS]",-62));radio.scans.add(scan("Coffee","[ESS]",-68));radio.saved.add(saved("Home",7,WifiNetworks.Security.PSK));networks.refresh();
        for(int id=0;id<4;id++){
            RuntimeEnvironment.getApplication().getSharedPreferences("settings",0).edit().putInt("theme",id).commit();
            try(ActivityController<MainActivity> c=launch()){
                MainActivity a=c.get();click(find(root(a),"Wi-Fi"));click(find(root(a),"Networks & saved connections"));capture(root(a),new java.io.File(folder,"networks-"+id+".png"),360,640);
                click(find(root(a),"Studio"));capture(ShadowDialog.getLatestDialog().getWindow().getDecorView(),new java.io.File(folder,"password-"+id+".png"),336,510);
            }
        }
    }
    private static void capture(View view,java.io.File file,int width,int height)throws Exception{view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));view.layout(0,0,width,height);android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(width,height,android.graphics.Bitmap.Config.ARGB_8888);view.draw(new android.graphics.Canvas(bitmap));try(java.io.FileOutputStream out=new java.io.FileOutputStream(file)){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();}
    @Test public void iconAndHardwareBackReturnToParentsAndSurviveRecreation(){
        try(ActivityController<MainActivity> c=launch()){
            MainActivity a=c.get();click(find(root(a),"Wi-Fi"));click(find(root(a),"Networks & saved connections"));click(find(root(a),"Saved networks"));
            View back=described(root(a),"Back to Networks");assertTrue(back instanceof ImageButton);assertTrue(((ImageButton)back).getDrawable() instanceof BackIcon);back.performClick();assertNotNull(find(root(a),"Rescan"));
            c.recreate();a=c.get();a.onBackPressed();assertNotNull(find(root(a),"Networks & saved connections"));a.onBackPressed();assertNotNull(find(root(a),"App appearance"));
        }
    }
    private static View root(MainActivity a){return a.findViewById(android.R.id.content);}
    private static View find(View v,String text){if(v instanceof TextView&&text.contentEquals(((TextView)v).getText()))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View child=find(((ViewGroup)v).getChildAt(i),text);if(child!=null)return child;}return null;}
    private static EditText edit(View v){if(v instanceof EditText)return(EditText)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){EditText f=edit(((ViewGroup)v).getChildAt(i));if(f!=null)return f;}return null;}
    private static View described(View v,String value){if(value.contentEquals(String.valueOf(v.getContentDescription())))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View found=described(((ViewGroup)v).getChildAt(i),value);if(found!=null)return found;}return null;}
    private static void click(View v){assertNotNull(v);while(!v.hasOnClickListeners()){assertTrue(v.getParent() instanceof View);v=(View)v.getParent();}v.performClick();}
}
