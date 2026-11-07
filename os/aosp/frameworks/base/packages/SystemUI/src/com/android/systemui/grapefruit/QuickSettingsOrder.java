// SPDX-License-Identifier: Apache-2.0
package com.android.systemui.grapefruit;

import android.database.ContentObserver;
import android.os.Handler;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import org.grapefruit.platform.TileOrder;

/** Keeps AOSP tile instances and callbacks intact, including temporary IME/alarm/location tiles. */
public final class QuickSettingsOrder extends ContentObserver implements View.OnAttachStateChangeListener {
    private final ViewGroup parent;
    private final Map<String,View> permanent=new LinkedHashMap<String,View>();
    private final ArrayList<View> temporary=new ArrayList<View>();
    private boolean observing;
    private QuickSettingsOrder(ViewGroup group){
        super(new Handler());parent=group;
        for(int i=0;i<parent.getChildCount();i++){
            View tile=parent.getChildAt(i);Object tag=tile.getTag();
            if(tag instanceof String && java.util.Arrays.asList(TileOrder.IDS).contains(tag))permanent.put((String)tag,tile);else temporary.add(tile);
        }
        parent.addOnAttachStateChangeListener(this);apply();
    }
    public static void install(ViewGroup parent){new QuickSettingsOrder(parent);}
    private void apply(){
        parent.removeAllViews();
        for(String id:TileOrder.parse(Settings.System.getString(parent.getContext().getContentResolver(),TileOrder.KEY))){View tile=permanent.get(id);if(tile!=null)parent.addView(tile);}
        for(View tile:temporary)parent.addView(tile);
    }
    @Override public void onChange(boolean self){apply();}
    public void onViewAttachedToWindow(View v){if(!observing){parent.getContext().getContentResolver().registerContentObserver(Settings.System.getUriFor(TileOrder.KEY),false,this);observing=true;}apply();}
    public void onViewDetachedFromWindow(View v){if(observing){parent.getContext().getContentResolver().unregisterContentObserver(this);observing=false;}}
}
