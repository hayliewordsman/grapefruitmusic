package com.grapefruitmusic.ui;

import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.util.*;

/** The standalone TV app's installed-provider shortcuts, kept inside Blue's TV navigation. */
public final class TvAppsPage extends BaseFragment {
    private BlueTelevision tv;private ListView list;private BaseAdapter adapter;
    private List<TvApps.App> apps=Collections.emptyList();
    @Override public View onCreateView(LayoutInflater inflater,ViewGroup parent,Bundle saved){
        tv=(BlueTelevision)main().television();TvStyle b=tv.style;list=new ListView(getActivity());list.setDivider(null);List<String> names=tv.sections();SectionMenu menu=SectionMenu.television(this,names.toArray(new String[0]),names.indexOf("TV Apps"),n->main().showTvSection(names.get(n)));menu.addHeaders(list);
        LinearLayout header=b.column();header.setPadding(b.dp(16),b.dp(12),b.dp(16),b.dp(12));header.addView(b.label("Open your installed TV and streaming apps. Their subscriptions and playback stay in those apps.",13,b.secondary));header.addView(b.button("Add TV App",false,v->pick()));list.addHeaderView(header,null,false);
        adapter=new BaseAdapter(){public int getCount(){return apps.size();}public Object getItem(int n){return apps.get(n);}public long getItemId(int n){return n;}public View getView(int n,View old,ViewGroup parent){TvApps.App app=apps.get(n);LinearLayout row=b.row();row.setPadding(b.dp(18),b.dp(10),b.dp(18),b.dp(10));ImageView icon=new ImageView(getActivity());icon.setImageDrawable(app.icon);row.addView(icon,new LinearLayout.LayoutParams(b.dp(44),b.dp(44)));TextView title=b.text(app.label,16,true);title.setPadding(b.dp(12),0,0,0);row.addView(title);return row;}};
        list.setAdapter(adapter);list.setOnItemClickListener((p,v,n,id)->{Object value=list.getItemAtPosition(n);if(value instanceof TvApps.App){if(tv.session.active())tv.session.pause(false);if(!TvApps.open(getActivity(),((TvApps.App)value).pkg))com.grapefruitmusic.util.Notice.show(getActivity(),"This app is no longer available.");}});list.setOnItemLongClickListener((p,v,n,id)->{Object value=list.getItemAtPosition(n);if(!(value instanceof TvApps.App))return false;TvApps.App app=(TvApps.App)value;Ui.dialog(getActivity()).setTitle(app.label).setItems(new String[]{"Remove Shortcut"},(d,i)->{TvApps.remove(tv.repo.prefs,app.pkg);load();}).setNegativeButton("Close",null).show();return true;});load();return menu.page(list);
    }
    private void load(){bg(()->TvApps.row(getActivity(),tv.repo.prefs),(data,error)->{if(error==null&&adapter!=null){apps=data;adapter.notifyDataSetChanged();}});}
    private void pick(){bg(()->TvApps.installed(getActivity()),(data,error)->{if(error!=null)return;String[] names=new String[data.size()];for(int n=0;n<names.length;n++)names[n]=data.get(n).label;Ui.dialog(getActivity()).setTitle("Add TV App").setItems(names,(d,n)->{TvApps.pin(tv.repo.prefs,data.get(n).pkg);load();}).setNegativeButton("Cancel",null).show();});}
    @Override public void onDestroyView(){list=null;adapter=null;super.onDestroyView();}
}
