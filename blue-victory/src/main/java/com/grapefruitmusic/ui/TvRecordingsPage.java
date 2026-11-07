package com.grapefruitmusic.ui;

import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.util.*;

/** DVR recordings and scheduled programmes; On Demand downloads retain their existing page. */
public final class TvRecordingsPage extends BaseFragment {
    private BlueTelevision tv;private ListView list;private BaseAdapter adapter;private TextView count;
    private TvButtonRow filters;
    private String filter="All";private final List<TvRecordings.Item> items=new ArrayList<TvRecordings.Item>();
    private final Runnable changed=this::refresh;
    @Override public View onCreateView(LayoutInflater inflater,ViewGroup container,Bundle saved){
        tv=(BlueTelevision)main().television();TvStyle b=tv.style;list=new ListView(getActivity());list.setDivider(null);
        List<String> names=tv.sections();SectionMenu menu=SectionMenu.television(this,names.toArray(new String[0]),names.indexOf("Recordings"),n->main().showTvSection(names.get(n)));menu.addHeaders(list);
        LinearLayout tools=b.column();tools.setPadding(b.dp(16),b.dp(8),b.dp(16),b.dp(8));filters=new TvButtonRow(getActivity());filterChips();tools.addView(filters);
        count=b.label("",13,b.secondary);count.setPadding(0,b.dp(12),0,b.dp(8));tools.addView(count);list.addHeaderView(tools,null,false);
        adapter=new BaseAdapter(){public int getCount(){return items.size();}public Object getItem(int n){return items.get(n);}public long getItemId(int n){return n;}public View getView(int n,View recycled,ViewGroup parent){
            TvRecordings.Item item=items.get(n);LinearLayout row=b.column();row.setPadding(b.dp(18),b.dp(12),b.dp(18),b.dp(12));TextView title=b.text(item.title,16,true);title.setMaxLines(2);row.addView(title);TextView detail=b.label(item.channelName+" · "+item.state+"\n"+TvRecordings.when(getActivity(),item.start,item.end)+(item.bytes>0?" · "+TvRecordings.size(item.bytes):"")+(item.error.isEmpty()?"":"\n"+item.error),12,b.secondary);row.addView(detail);return row;}};
        list.setAdapter(adapter);list.setOnItemClickListener((parent,row,position,id)->{Object value=list.getItemAtPosition(position);if(value instanceof TvRecordings.Item){TvRecordings.Item item=(TvRecordings.Item)value;if(item.ready())tv.play(item.channel());else details(item);}});list.setOnItemLongClickListener((parent,row,position,id)->{Object value=list.getItemAtPosition(position);if(value instanceof TvRecordings.Item){details((TvRecordings.Item)value);return true;}return false;});refresh();restoreScroll(list);return menu.page(list);
    }
    private void filterChips(){filters.removeAllViews();for(String value:new String[]{"All","Scheduled","Finished"})filters.addView(tv.style.button(value,value.equals(filter),v->{filter=value;filterChips();refresh();}));}
    private void refresh(){if(adapter==null||getActivity()==null)return;items.clear();for(TvRecordings.Item item:TvRecordings.all(getActivity())){
        if(item.download())continue;boolean done=TvRecordings.FINISHED.equals(item.state)||TvRecordings.FAILED.equals(item.state);
        if("Scheduled".equals(filter)&&done||"Finished".equals(filter)&&!done)continue;items.add(item);
    }Collections.sort(items,(a,b)->{int x=a.active()?0:a.waiting()?1:2,y=b.active()?0:b.waiting()?1:2;if(x!=y)return x-y;return x==1?(a.start<b.start?-1:a.start==b.start?0:1):(b.start<a.start?-1:b.start==a.start?0:1);});count.setText(items.size()+" recordings · "+TvRecordings.size(TvRecordings.free(getActivity()))+" free on this device"+(items.isEmpty()?"\nTap Record on a live channel or a programme in Guide.":""));adapter.notifyDataSetChanged();}
    private void details(TvRecordings.Item item){
        List<String> labels=new ArrayList<String>();List<Runnable> actions=new ArrayList<Runnable>();
        if(item.ready()){labels.add("Play Recording");actions.add(()->tv.play(item.channel()));}
        if(item.active()){labels.add("Stop Recording");actions.add(()->{TvRecorderService.stop(item.id);refresh();});}
        if(item.waiting()){labels.add("Cancel Recording");actions.add(()->{TvRecordings.remove(getActivity(),item.id);refresh();});}
        labels.add("Delete Recording");actions.add(()->Ui.dialog(getActivity()).setTitle("Delete recording?").setMessage("Remove “"+item.title+"” and its saved file from this device?").setPositiveButton("Delete",(d,n)->{if(tv.session.current!=null&&tv.session.current.id.equals(item.channel().id))tv.session.stop();TvRecordings.remove(getActivity(),item.id);refresh();}).setNegativeButton("Cancel",null).show());
        Ui.dialog(getActivity()).setTitle(item.title).setMessage(item.channelName+" · "+TvRecordings.when(getActivity(),item.start,item.end)+(item.error.isEmpty()?"":"\n"+item.error)).setItems(labels.toArray(new String[0]),(d,n)->actions.get(n).run()).setNegativeButton("Close",null).show();
    }
    @Override public void onResume(){super.onResume();TvRecordings.listeners.add(changed);refresh();}
    @Override public void onPause(){TvRecordings.listeners.remove(changed);rememberScroll(list);super.onPause();}
    @Override public void onDestroyView(){list=null;adapter=null;super.onDestroyView();}
}
