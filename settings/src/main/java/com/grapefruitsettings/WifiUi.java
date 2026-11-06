package com.grapefruitsettings;

import android.app.Dialog;
import android.net.DhcpInfo;
import android.net.wifi.WifiInfo;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.widget.*;
import java.util.*;

/** Wi-Fi screens and password dialogs stay entirely in the Grapefruit frontend. */
final class WifiUi {
    private final MainActivity a;
    private final Palette p;
    private final WifiNetworks networks;
    private LinearLayout list;
    private TextView status;
    private Toggle enabled;
    private boolean savedOnly;
    WifiUi(MainActivity host,Palette palette,WifiNetworks service){a=host;p=palette;networks=service;}
    void overview(){
        a.title("Wi-Fi","Choose a network. Keep everything in Grapefruit.");
        enabled=a.toggleRow("wifi","Wi-Fi",networks.enabled(),(t,on)->{if(!networks.enable(on)){t.setChecked(networks.enabled());a.toast(networks.feedback);}else if(on)a.requestWifiScan(false);refresh();});
        status=a.text(networks.status(),14,false);a.add(status);
        a.action("Networks & saved connections",v->a.navigate("networks"));
        a.action("Add a network",v->form(null,false));
        a.action("Connection details",v->a.navigate("wifi_advanced"));
    }
    void networks(boolean saved){
        savedOnly=saved;
        a.title(saved?"Saved networks":"Networks",saved?"Reconnect, change a password or forget a network.":"Nearby access points and saved connections.");
        status=a.text(networks.status(),14,false);a.add(status);
        if(!saved)a.action("Rescan",v->a.requestWifiScan(true));
        a.action(saved?"Nearby networks":"Saved networks",v->a.navigate(saved?"networks":"saved_networks"));
        a.action("Add a network",v->form(null,false));
        list=a.column();a.add(list);refresh();
    }
    void details(){
        a.title("Connection details","Wi-Fi information from your JP5S.");
        WifiInfo info=networks.info();DhcpInfo dhcp=networks.dhcp();
        if(!networks.enabled()||info==null||networks.currentId()<0){a.note("Connect to a Wi-Fi network to see its details.");a.action("Choose a network",v->a.navigate("networks"));return;}
        a.row("wifi","Network",WifiNetworks.unquote(info.getSSID()),null);
        a.row("wifi","IP address",info.getIpAddress()==0?"Waiting for an address…":WifiNetworks.address(info.getIpAddress()),null);
        if(dhcp!=null){a.row("wifi","Gateway",WifiNetworks.address(dhcp.gateway),null);a.row("wifi","Netmask",WifiNetworks.address(dhcp.netmask),null);a.row("wifi","DNS",WifiNetworks.address(dhcp.dns1)+"\n"+WifiNetworks.address(dhcp.dns2),null);}
        a.row("wifi","Link speed",info.getLinkSpeed()+" Mbps",null);
        a.row("wifi","Signal",WifiNetworks.signal(info.getRssi())+" ("+info.getRssi()+" dBm)",null);
        a.row("wifi","Access point",info.getBSSID()==null?"Unavailable":info.getBSSID(),null);
        a.action("Disconnect",v->{networks.disconnect();a.toast(networks.feedback);a.onBackPressed();});
    }
    void refresh(){
        if(status!=null)status.setText(networks.status());
        if(enabled!=null)enabled.setChecked(networks.enabled());
        if(list==null)return;
        list.removeAllViews();
        List<WifiNetworks.Entry> rows=savedOnly?networks.saved():networks.nearby();
        if(!networks.enabled()&&!savedOnly){label("Wi-Fi is off. Turn it on from the Wi-Fi page.");return;}
        if(rows.isEmpty()){label(savedOnly?"No saved networks yet.":networks.scanning?"Scanning…":"No nearby networks found. Rescan, or add a hidden network manually.");return;}
        for(WifiNetworks.Entry entry:rows){
            LinearLayout row=a.horizontal();row.setPadding(p.dp(12),p.dp(12),p.dp(12),p.dp(12));row.setMinimumHeight(p.dp(72));row.setBackground(p.interactive(false));
            row.addView(new SettingIcon(a,p,"wifi"),new LinearLayout.LayoutParams(p.dp(42),p.dp(42)));
            LinearLayout names=a.column();names.setPadding(p.dp(12),0,0,0);names.addView(a.text(entry.ssid,16,true));TextView detail=a.text(entry.detail(),12,false);detail.setTextColor(p.secondary);names.addView(detail);row.addView(names,new LinearLayout.LayoutParams(0,-2,1));
            row.setContentDescription(entry.ssid+", "+entry.detail());row.setFocusable(true);row.setOnClickListener(v->select(entry));
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.topMargin=p.dp(8);list.addView(row,params);
        }
    }
    private void label(String message){TextView t=a.text(message,14,false);t.setTextColor(p.secondary);list.addView(t);}
    private void select(WifiNetworks.Entry entry){
        if(!entry.saved()){
            if(entry.security==WifiNetworks.Security.OPEN){
                LinearLayout body=a.column();body.addView(a.text("Open network · no password",14,false));Dialog d=a.dialog(entry.ssid,body);
                a.dialogButton(body,"Connect",v->{if(networks.save(entry.ssid,entry.security,"",null,false,true)){a.watchWifiConnection();d.dismiss();refresh();}else a.toast(networks.feedback);});a.dialogButton(body,"Cancel",v->d.dismiss());
            }else if(entry.security==WifiNetworks.Security.PSK||entry.security==WifiNetworks.Security.WEP)form(entry,false);
            else a.message(entry.ssid,entry.security==WifiNetworks.Security.EAP?"An enterprise network must already be provisioned on this JP5S. A saved enterprise profile can be selected under Saved networks.":"This network's security is not supported by Jelly Bean. Use an access point offering WPA2 Personal.");
            return;
        }
        LinearLayout body=a.column();body.addView(a.text(entry.detail(),14,false));
        if(entry.scan!=null)body.addView(a.text(entry.scan.level+" dBm · "+entry.scan.frequency+" MHz",13,false));
        Dialog d=a.dialog(entry.ssid,body);
        if(entry.connected){a.dialogButton(body,"Connection details",v->{d.dismiss();a.navigate("wifi_advanced");});a.dialogButton(body,"Disconnect",v->{if(networks.disconnect()){d.dismiss();refresh();}else a.toast(networks.feedback);});}
        else a.dialogButton(body,"Connect",v->{if(networks.connect(entry.config.networkId)){a.watchWifiConnection();d.dismiss();refresh();}else a.toast(networks.feedback);});
        if(entry.security==WifiNetworks.Security.PSK||entry.security==WifiNetworks.Security.WEP)a.dialogButton(body,"Change password",v->{d.dismiss();form(entry,true);});
        a.dialogButton(body,"Forget network",v->{d.dismiss();forget(entry);});a.dialogButton(body,"Close",v->d.dismiss());
    }
    private void forget(WifiNetworks.Entry entry){
        LinearLayout body=a.column();body.addView(a.text("Remove the saved connection to "+entry.ssid+"?",15,false));Dialog d=a.dialog("Forget network",body);
        a.dialogButton(body,"Forget",v->{if(networks.forget(entry.config.networkId)){d.dismiss();refresh();}else a.toast(networks.feedback);});a.dialogButton(body,"Cancel",v->d.dismiss());
    }
    void form(WifiNetworks.Entry entry,boolean edit){
        final boolean manual=entry==null;
        LinearLayout body=a.column();
        EditText ssid=new EditText(a);a.styleField(ssid,"Network name (SSID)",false);ssid.setText(manual?"":entry.ssid);
        if(manual)a.editor(body,ssid);else body.addView(a.text(entry.ssid,18,true));
        WifiNetworks.Security[] choices={WifiNetworks.Security.PSK,WifiNetworks.Security.OPEN,WifiNetworks.Security.WEP};
        WifiNetworks.Security[] security={manual?WifiNetworks.Security.PSK:entry.security};
        EditText password=new EditText(a);a.styleField(password,"Network password",true);password.setSaveEnabled(false);
        LinearLayout passwordGroup=a.column();a.editor(passwordGroup,password);
        TextView securityButton=p.chip("Security: "+security[0].label,false,v->{});
        if(manual)body.addView(securityButton);else body.addView(a.text(security[0].label,14,false));
        body.addView(passwordGroup);
        Toggle show=new Toggle(a);show.setPalette(p);show.setContentDescription("Show password");
        LinearLayout showRow=a.horizontal();showRow.addView(a.text("Show password",14,false),new LinearLayout.LayoutParams(0,-2,1));showRow.addView(show,new LinearLayout.LayoutParams(p.dp(60),p.dp(48)));passwordGroup.addView(showRow);
        show.setListener((toggle,on)->{int from=password.getSelectionStart(),to=password.getSelectionEnd();password.setTransformationMethod(on?null:PasswordTransformationMethod.getInstance());if(from>=0&&to>=0)password.setSelection(Math.min(from,password.length()),Math.min(to,password.length()));});
        Toggle hidden=new Toggle(a);hidden.setPalette(p);hidden.setContentDescription("Hidden network");hidden.setChecked(entry!=null&&entry.config!=null&&entry.config.hiddenSSID);
        if(manual){LinearLayout row=a.horizontal();row.addView(a.text("Hidden network",14,false),new LinearLayout.LayoutParams(0,-2,1));row.addView(hidden,new LinearLayout.LayoutParams(p.dp(60),p.dp(48)));body.addView(row);}
        TextView error=a.text("",13,false);error.setTextColor(p.accent);body.addView(error);
        if(edit)body.addView(a.text("Enter the new password. Cancel keeps the existing saved connection.",13,false));
        Dialog d=a.dialog(edit?"Change password":manual?"Add a network":"Connect to Wi-Fi",body);
        securityButton.setOnClickListener(v->{
            LinearLayout menu=a.column();Dialog choice=a.dialog("Network security",menu);
            for(WifiNetworks.Security option:choices)a.dialogButton(menu,option.label,item->{security[0]=option;securityButton.setText("Security: "+option.label);passwordGroup.setVisibility(option==WifiNetworks.Security.OPEN?View.GONE:View.VISIBLE);error.setText("");choice.dismiss();});
            a.dialogButton(menu,"Cancel",item->choice.dismiss());
        });
        View.OnClickListener save=v->{
            String name=ssid.getText().toString(),secret=password.getText().toString();String invalid=WifiNetworks.validate(name,security[0],secret);
            if(invalid!=null){error.setText(invalid);return;}
            if(networks.save(name,security[0],secret,entry==null?null:entry.config,manual?hidden.isChecked():entry.config!=null&&entry.config.hiddenSSID,true)){a.watchWifiConnection();d.dismiss();refresh();}else error.setText(networks.feedback);
        };
        a.dialogButton(body,edit?"Save & reconnect":"Connect",save);a.dialogButton(body,"Cancel",v->d.dismiss());
    }
}
