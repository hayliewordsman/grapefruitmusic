// SPDX-License-Identifier: Apache-2.0
package com.android.settings.grapefruit;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.grapefruit.platform.GrapefruitStyle;
import org.grapefruit.platform.TileOrder;

/** Editing changes only tile presentation; all tile actions remain in AOSP QuickSettings. */
public final class QuickSettingsEditor extends Activity {
    private List<String> selected;
    @Override public void onCreate(Bundle state) {super.onCreate(state);setTitle("Edit Quick Settings");load();render();}
    private void load(){selected=TileOrder.parse(Settings.System.getString(getContentResolver(),TileOrder.KEY));}
    private boolean supported(String id) {
        PackageManager pm=getPackageManager();
        if("bluetooth".equals(id))return pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH);
        if("cellular".equals(id))return pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY);
        if("wifi".equals(id))return pm.hasSystemFeature(PackageManager.FEATURE_WIFI);
        return true;
    }
    private void save(){
        if(!Settings.System.putString(getContentResolver(),TileOrder.KEY,TileOrder.join(selected))) {
            Toast.makeText(this,"Tile order could not be saved",Toast.LENGTH_LONG).show();load();
        }
        render();
    }
    private void button(LinearLayout row,String label,View.OnClickListener action) {
        Button b=new Button(this);b.setText(label);b.setContentDescription(label);b.setMinHeight(GrapefruitStyle.dp(this,48));
        b.setOnClickListener(action);row.addView(b);
    }
    private void render() {
        ScrollView scroll=new ScrollView(this);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);
        int pad=GrapefruitStyle.dp(this,12);body.setPadding(pad,pad,pad,pad);scroll.addView(body);setContentView(scroll);
        final List<String> visible=new ArrayList<String>();for(String id:selected)if(supported(id))visible.add(id);
        for(final String id:visible) {
            LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);body.addView(row);
            TextView name=new TextView(this);name.setText(TileOrder.label(id));name.setTextSize(16);
            row.addView(name,new LinearLayout.LayoutParams(0,-2,1));
            button(row,"Up",new View.OnClickListener(){public void onClick(View v){int i=visible.indexOf(id);if(i>0){Collections.swap(selected,selected.indexOf(id),selected.indexOf(visible.get(i-1)));save();}}});
            button(row,"Down",new View.OnClickListener(){public void onClick(View v){int i=visible.indexOf(id);if(i<visible.size()-1){Collections.swap(selected,selected.indexOf(id),selected.indexOf(visible.get(i+1)));save();}}});
            button(row,"Remove",new View.OnClickListener(){public void onClick(View v){selected.remove(id);save();}});
        }
        button(body,"Add tile",new View.OnClickListener(){public void onClick(View v){
            final List<String> available=new ArrayList<String>();final List<String> labels=new ArrayList<String>();
            for(String id:TileOrder.IDS)if(supported(id)&&!selected.contains(id)){available.add(id);labels.add(TileOrder.label(id));}
            new AlertDialog.Builder(QuickSettingsEditor.this).setTitle("Add tile").setItems(labels.toArray(new String[labels.size()]),new DialogInterface.OnClickListener(){
                public void onClick(DialogInterface d,int which){selected.add(available.get(which));save();}
            }).setNegativeButton(android.R.string.cancel,null).show();
        }});
        button(body,"Reset defaults",new View.OnClickListener(){public void onClick(View v){selected=TileOrder.parse(null);save();}});
    }
}
