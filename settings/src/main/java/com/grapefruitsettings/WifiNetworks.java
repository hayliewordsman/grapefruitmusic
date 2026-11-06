package com.grapefruitsettings;

import android.content.Intent;
import android.net.DhcpInfo;
import android.net.NetworkInfo;
import android.net.wifi.*;
import android.os.SystemClock;
import java.nio.charset.Charset;
import java.util.*;

/** API 17 Wi-Fi operations. No Settings Activity, private OEM UI, or password persistence. */
final class WifiNetworks {
    enum Security {
        OPEN("Open"),PSK("WPA/WPA2 Personal"),WEP("WEP"),EAP("802.1X Enterprise"),UNSUPPORTED("Unsupported security");
        final String label;Security(String value){label=value;}
    }
    interface Backend {
        boolean enabled();boolean enable(boolean on);boolean scan();
        List<ScanResult> scans();List<WifiConfiguration> saved();WifiInfo info();DhcpInfo dhcp();
        int add(WifiConfiguration c);int update(WifiConfiguration c);boolean save();
        boolean select(int id);boolean reconnect();boolean disconnect();boolean remove(int id);
    }
    static final class AndroidBackend implements Backend {
        private final WifiManager w;
        AndroidBackend(WifiManager manager){w=manager;}
        public boolean enabled(){return w!=null&&w.isWifiEnabled();}
        public boolean enable(boolean on){return w!=null&&w.setWifiEnabled(on);}
        public boolean scan(){return w!=null&&w.startScan();}
        public List<ScanResult> scans(){return w==null?Collections.emptyList():w.getScanResults();}
        public List<WifiConfiguration> saved(){return w==null?Collections.emptyList():w.getConfiguredNetworks();}
        public WifiInfo info(){return w==null?null:w.getConnectionInfo();}
        public DhcpInfo dhcp(){return w==null?null:w.getDhcpInfo();}
        public int add(WifiConfiguration c){return w.addNetwork(c);}
        public int update(WifiConfiguration c){return w.updateNetwork(c);}
        public boolean save(){return w.saveConfiguration();}
        public boolean select(int id){return w.enableNetwork(id,true);}
        public boolean reconnect(){return w.reconnect();}
        public boolean disconnect(){return w.disconnect();}
        public boolean remove(int id){return w.removeNetwork(id);}
    }
    static final class Entry {
        final String ssid;final Security security;final WifiConfiguration config;final ScanResult scan;
        final boolean connected;
        Entry(String s,Security type,WifiConfiguration c,ScanResult r,int current){ssid=s;security=type;config=c;scan=r;connected=c!=null&&c.networkId==current;}
        boolean saved(){return config!=null;}
        String detail(){return (connected?"Connected · ":saved()?"Saved · ":"")+security.label+(scan==null?(connected?"":" · out of range"):" · "+signal(scan.level));}
    }
    final Backend backend;
    private List<WifiConfiguration> configurations=Collections.emptyList();
    private List<ScanResult> results=Collections.emptyList();
    String feedback="";
    boolean scanning;
    private int pendingId=-1;
    private long pendingSince;
    private long lastScan=-10000;
    WifiNetworks(Backend adapter){backend=adapter;}
    boolean enabled(){try{return backend.enabled();}catch(RuntimeException e){feedback="Wi-Fi status is unavailable.";return false;}}
    boolean enable(boolean on){try{if(backend.enable(on))return true;}catch(RuntimeException e){}feedback="Wi-Fi could not be changed. Try again in a moment.";return false;}
    void refresh(){
        try{List<WifiConfiguration> list=backend.saved();if(list!=null)configurations=new ArrayList<>(list);}catch(RuntimeException e){feedback="Saved networks could not be read.";}
        if(enabled())try{List<ScanResult> list=backend.scans();if(list!=null)results=new ArrayList<>(list);}catch(RuntimeException e){feedback="Nearby networks could not be read.";}
        else {results=Collections.emptyList();scanning=false;}
    }
    boolean scan(boolean force){
        if(!enabled()){feedback="Turn on Wi-Fi to scan.";return false;}
        long now=SystemClock.elapsedRealtime();if(!force&&now-lastScan<10000)return scanning;
        try{if(backend.scan()){lastScan=now;scanning=true;feedback="";return true;}}catch(RuntimeException e){}
        scanning=false;feedback="Scan could not start. Cached networks are shown; tap Rescan to retry.";return false;
    }
    void scanTimeout(){if(scanning){scanning=false;feedback="Waiting for fresh scan results. Tap Rescan to try again.";}}
    void event(Intent intent){
        String action=intent.getAction();
        if(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(action)){scanning=false;if(feedback.startsWith("Scan")||feedback.startsWith("Waiting"))feedback="";}
        if(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION.equals(action)&&intent.getIntExtra(WifiManager.EXTRA_SUPPLICANT_ERROR,-1)==WifiManager.ERROR_AUTHENTICATING){feedback="Authentication failed. Check the network password and try again.";pendingId=-1;}
        if(WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(action)){
            NetworkInfo info=intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
            if(info!=null&&info.isConnected()){feedback="Connected.";pendingId=-1;}
        }
        refresh();
    }
    WifiInfo info(){try{return backend.info();}catch(RuntimeException e){return null;}}
    DhcpInfo dhcp(){try{return backend.dhcp();}catch(RuntimeException e){return null;}}
    int currentId(){WifiInfo info=info();return enabled()&&info!=null&&info.getSupplicantState()==SupplicantState.COMPLETED?info.getNetworkId():-1;}
    long pendingMillis(){return pendingId<0?-1:Math.max(0,30000-(SystemClock.elapsedRealtime()-pendingSince));}
    void connectionTimeout(){if(pendingId>=0&&pendingMillis()==0){int connected=currentId();feedback=connected==pendingId?"Connected.":"Connection timed out. Check the password and signal, then try again.";pendingId=-1;}}
    String status(){
        if(!enabled())return "Wi-Fi is off";
        if(!feedback.isEmpty())return feedback;
        WifiInfo info=info();
        if(pendingId>=0)return "Connecting…";
        if(info!=null&&info.getNetworkId()>=0){
            SupplicantState state=info.getSupplicantState();
            if(state==SupplicantState.COMPLETED)return "Connected to "+unquote(info.getSSID());
            if(state==SupplicantState.ASSOCIATING||state==SupplicantState.ASSOCIATED||state==SupplicantState.FOUR_WAY_HANDSHAKE||state==SupplicantState.GROUP_HANDSHAKE)return "Connecting to "+unquote(info.getSSID())+"…";
        }
        return scanning?"Scanning for networks…":"Wi-Fi is on";
    }
    List<Entry> nearby(){
        int current=currentId();Map<String,ScanResult> strongest=new LinkedHashMap<>();
        for(ScanResult r:results){if(r==null||r.SSID==null||r.SSID.isEmpty())continue;String key=r.SSID+'\0'+security(r.capabilities);ScanResult previous=strongest.get(key);if(previous==null||r.level>previous.level)strongest.put(key,r);}
        List<Entry> rows=new ArrayList<>();
        for(ScanResult r:strongest.values()){Security security=security(r.capabilities);WifiConfiguration match=null;for(WifiConfiguration c:configurations)if(r.SSID.equals(unquote(c.SSID))&&security(c)==security&&(match==null||c.networkId==current))match=c;rows.add(new Entry(r.SSID,security,match,r,current));}
        Collections.sort(rows,(a,b)->a.connected!=b.connected?(a.connected?-1:1):a.scan.level!=b.scan.level?Integer.compare(b.scan.level,a.scan.level):a.ssid.compareToIgnoreCase(b.ssid));return rows;
    }
    List<Entry> saved(){
        int current=currentId();List<Entry> rows=new ArrayList<>();
        for(WifiConfiguration c:configurations){String name=unquote(c.SSID);if(name.isEmpty())continue;ScanResult strongest=null;for(ScanResult r:results)if(name.equals(r.SSID)&&security(c)==security(r.capabilities)&&(strongest==null||r.level>strongest.level))strongest=r;rows.add(new Entry(name,security(c),c,strongest,current));}
        Collections.sort(rows,(a,b)->a.connected!=b.connected?(a.connected?-1:1):a.ssid.compareToIgnoreCase(b.ssid));return rows;
    }
    boolean connect(int id){
        if(!enabled()){feedback="Turn on Wi-Fi first.";return false;}
        try{if(backend.select(id)&&backend.reconnect()){pendingId=id;pendingSince=SystemClock.elapsedRealtime();feedback="Connecting…";return true;}}catch(RuntimeException e){}
        feedback="Connection could not start. The saved network was kept; try again.";return false;
    }
    boolean save(String ssid,Security security,String password,WifiConfiguration existing,boolean hidden,boolean connect){
        String error=validate(ssid,security,password);if(error!=null){feedback=error;return false;}
        if(!enabled()){feedback="Turn on Wi-Fi first.";return false;}
        try{
            WifiConfiguration config=configuration(ssid,security,password,existing,hidden);
            // Manual Add must update a matching profile rather than create duplicates.
            if(existing==null)for(WifiConfiguration saved:configurations)if(unquote(saved.SSID).equals(ssid)&&security(saved)==security){config=configuration(ssid,security,password,saved,hidden);break;}
            int id=config.networkId<0?backend.add(config):backend.update(config);
            if(id<0){feedback="The network could not be saved. Your existing connection was kept.";return false;}
            if(!backend.save()){feedback="The device could not persist this network. Try Save again.";return false;}
            refresh();if(connect)return connect(id);feedback="Network saved.";return true;
        }catch(RuntimeException e){feedback="The network could not be saved. Try again.";return false;}
    }
    boolean forget(int id){try{if(backend.remove(id)){boolean saved=backend.save();if(pendingId==id)pendingId=-1;refresh();feedback=saved?"Network forgotten.":"Removed for now, but the device could not persist the change.";return true;}}catch(RuntimeException e){}feedback="The network could not be forgotten.";return false;}
    boolean disconnect(){try{if(backend.disconnect()){pendingId=-1;feedback="Disconnected.";return true;}}catch(RuntimeException e){}feedback="The network could not be disconnected.";return false;}
    static Security security(String capabilities){
        String c=capabilities==null?"":capabilities.toUpperCase(Locale.ROOT);
        if(c.contains("EAP")||c.contains("IEEE8021X"))return Security.EAP;
        if(c.contains("WAPI"))return Security.UNSUPPORTED;
        if(c.contains("WEP"))return Security.WEP;
        if(c.contains("PSK"))return Security.PSK;
        if(c.contains("SAE")||c.contains("OWE")||c.contains("WAPI")||c.contains("WPA")||c.contains("RSN"))return Security.UNSUPPORTED;
        return Security.OPEN;
    }
    static Security security(WifiConfiguration c){
        if(c.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.WPA_PSK))return Security.PSK;
        if(c.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.WPA_EAP)||c.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.IEEE8021X))return Security.EAP;
        if(c.wepKeys!=null)for(String key:c.wepKeys)if(key!=null)return Security.WEP;
        return c.allowedKeyManagement.isEmpty()||c.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.NONE)?Security.OPEN:Security.UNSUPPORTED;
    }
    static String validate(String ssid,Security security,String password){
        if(ssid==null||ssid.isEmpty()||ssid.getBytes(Charset.forName("UTF-8")).length>32||ssid.indexOf('\0')>=0)return "Enter a network name of 1–32 UTF-8 bytes.";
        String p=password==null?"":password;
        if(security==Security.PSK&&!MainActivity.validPassword(p))return "Use 8–63 ASCII characters or 64 hexadecimal digits.";
        if(security==Security.WEP&&!((p.matches("[0-9a-fA-F]+")&&(p.length()==10||p.length()==26||p.length()==58))||(p.matches("[\\x20-\\x7e]+")&&(p.length()==5||p.length()==13||p.length()==29))))return "Use a 5/13/29-character WEP key or 10/26/58 hexadecimal digits.";
        if(security==Security.EAP||security==Security.UNSUPPORTED)return "This security type needs an existing provisioned network on this JP5S.";
        return null;
    }
    static WifiConfiguration configuration(String ssid,Security security,String password,WifiConfiguration existing,boolean hidden){
        String error=validate(ssid,security,password);if(error!=null)throw new IllegalArgumentException(error);
        WifiConfiguration c=new WifiConfiguration();
        if(existing!=null){android.os.Parcel parcel=android.os.Parcel.obtain();try{parcel.writeParcelable(existing,0);parcel.setDataPosition(0);c=parcel.readParcelable(WifiConfiguration.class.getClassLoader());}finally{parcel.recycle();}}
        c.SSID=MainActivity.quote(ssid);c.hiddenSSID=hidden;c.allowedKeyManagement.clear();c.allowedAuthAlgorithms.clear();c.preSharedKey=null;c.wepKeys=new String[4];
        if(security==Security.PSK){c.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);c.preSharedKey=password.matches("[0-9a-fA-F]{64}")?password:MainActivity.quote(password);}
        else{c.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);if(security==Security.WEP){c.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);c.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.SHARED);c.wepTxKeyIndex=0;c.wepKeys[0]=password.matches("[0-9a-fA-F]+")&&(password.length()==10||password.length()==26||password.length()==58)?password:MainActivity.quote(password);}}
        return c;
    }
    static String unquote(String value){
        if(value==null||"<unknown ssid>".equalsIgnoreCase(value))return "";
        if(value.length()<2||value.charAt(0)!='"'||value.charAt(value.length()-1)!='"')return value;
        StringBuilder out=new StringBuilder();for(int i=1;i<value.length()-1;i++){char c=value.charAt(i);if(c=='\\'&&i+1<value.length()-1)c=value.charAt(++i);out.append(c);}return out.toString();
    }
    static String signal(int level){return level>=-55?"Excellent signal":level>=-67?"Good signal":level>=-75?"Fair signal":"Weak signal";}
    static String address(int ip){return (ip&255)+"."+(ip>>8&255)+"."+(ip>>16&255)+"."+(ip>>24&255);}
}
