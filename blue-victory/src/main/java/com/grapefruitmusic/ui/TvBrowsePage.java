package com.grapefruitmusic.ui;

import android.os.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Channels, the published EPG and capability-dependent provider libraries. */
public final class TvBrowsePage extends BaseFragment implements GuideViews.Host {
    private BlueTelevision tv;
    private TvStyle b;
    private ListView list;
    private Rows adapter;
    private String section="Channels",query="",country="All Regions",category="All Categories",source="All Sources",demandKind="All";
    private final List<Channel> rows=new ArrayList<Channel>();
    private List<Channel> episodes;
    private Map<Integer,List<Channel>> seasons;
    private int selectedSeason;
    private List<TvRecordings.Item> planned=Collections.emptyList();
    private Map<String,TvDownloads.Item> downloads=new HashMap<String,TvDownloads.Item>();
    private String menuSignature="";
    private long guideStart=Guide.floor(System.currentTimeMillis(),30*Guide.MINUTE);
    private GuideViews.Ruler ruler;
    private TextView count;
    private final Runnable changed=this::refresh;
    public static TvBrowsePage create(String name){TvBrowsePage p=new TvBrowsePage();Bundle a=new Bundle();a.putString("section",name);p.setArguments(a);return p;}
    static TvBrowsePage series(Channel channel){TvBrowsePage p=create("On Demand");p.getArguments().putString("series",channel.id);return p;}
    @Override public View onCreateView(LayoutInflater inflater,ViewGroup parent,Bundle state){
        tv=(BlueTelevision)main().television();b=tv.style;section=getArguments().getString("section","Channels");
        List<String> names=tv.sections();if(!names.contains(section))section="Channels";
        menuSignature=names.toString();
        list="Guide".equals(section)?new GuideViews.List(getActivity(),this):new ListView(getActivity());
        list.setDivider(null);list.setClipToPadding(false);
        SectionMenu menu=SectionMenu.television(this,names.toArray(new String[0]),names.indexOf(section),n->main().showTvSection(names.get(n)));
        menu.addHeaders(list);
        LinearLayout tools=b.column();tools.setPadding(b.dp(16),b.dp(8),b.dp(16),b.dp(8));
        String seriesId=getArguments().getString("series");
        if(seriesId!=null){
            Channel series=tv.repo.find(seriesId);TextView back=b.button("‹ On Demand",false,v->getFragmentManager().popBackStack());tools.addView(back);
            TextView title=b.text(series==null?"Episodes":series.name,20,true);title.setPadding(0,b.dp(12),0,b.dp(8));tools.addView(title);
            if(episodes==null&&series!=null){bg(()->tv.repo.demand.episodesFor(tv.repo.source(series),series),(got,error)->{
                if(error!=null){count.setText("Couldn't load episodes. Check the provider in Settings.");return;}
                seasons=got;if(!got.isEmpty()){selectedSeason=got.keySet().iterator().next();episodes=got.get(selectedSeason);}else episodes=Collections.emptyList();refresh();
            });}
            tools.addView(b.button("Choose Season",false,v->{if(seasons==null||seasons.isEmpty()){com.grapefruitmusic.util.Notice.show(getActivity(),"Episodes are still loading.");return;}List<Integer> keys=new ArrayList<Integer>(seasons.keySet());String[] labels=new String[keys.size()];for(int n=0;n<labels.length;n++)labels[n]="Season "+keys.get(n);tv.choice("Choose Season",labels,keys.indexOf(selectedSeason),n->{selectedSeason=keys.get(n);episodes=seasons.get(selectedSeason);refresh();});}));
        }
        EditText search=new BlueEditText(getActivity());search.setSingleLine();search.setTextColor(b.text);search.setHintTextColor(b.secondary);search.setTypeface(Fonts.regular());search.setTextSize(16);search.setHint("Search "+section);search.setText(query);
        tools.addView(search,new LinearLayout.LayoutParams(-1,b.dp(48)));
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int before,int count){query=s.toString();filter();}public void afterTextChanged(Editable s){}});
        if(!"Downloads".equals(section)&&seriesId==null){
            TvButtonRow strip=new TvButtonRow(getActivity());strip.setPadding(0,b.dp(12),0,b.dp(4));
            if("On Demand".equals(section))addFilter(strip,"Films / Series",()->{String[] labels={"All","Films","Series","Episodes"};tv.choice("On Demand",labels,Arrays.asList(labels).indexOf(demandKind),n->{demandKind=labels[n];filter();});});
            if(!"On Demand".equals(section))addFilter(strip,"Region",()->{List<String> values=new ArrayList<String>();values.add("All Regions");for(Channel c:tv.repo.channels)if(!c.region().isEmpty()&&!values.contains(c.region()))values.add(c.region());tv.choice("Region",values.toArray(new String[0]),values.indexOf(country),n->{country=values.get(n);filter();});});
            addFilter(strip,"Category",()->{List<String> values=new ArrayList<String>();values.add("All Categories");for(Channel c:available()){String group=group(c);if(!group.isEmpty()&&!values.contains(group))values.add(group);}tv.choice("Category",values.toArray(new String[0]),values.indexOf(category),n->{category=values.get(n);filter();});});
            addFilter(strip,"Source",()->{List<String> values=new ArrayList<String>();values.add("All Sources");if(!"On Demand".equals(section))values.add("Included Channels");for(UserSources.Source s:tv.repo.sources.list())values.add(s.name);tv.choice("Source",values.toArray(new String[0]),values.indexOf(source),n->{source=values.get(n);filter();});});
            if("Guide".equals(section))addFilter(strip,"Now",()->{guideStart=Guide.floor(System.currentTimeMillis(),30*Guide.MINUTE);redrawGuide();});
            tools.addView(strip,new LinearLayout.LayoutParams(-1,-2));
        }
        count=b.label("",12,b.secondary);count.setPadding(b.dp(4),b.dp(10),0,b.dp(4));tools.addView(count);list.addHeaderView(tools,null,false);
        if("Guide".equals(section)){ruler=new GuideViews.Ruler(getActivity(),this);list.addHeaderView(ruler,null,false);}else ruler=null;
        adapter=new Rows();list.setAdapter(adapter);
        list.setOnItemClickListener((p,v,pos,id)->{Object item=list.getItemAtPosition(pos);if(item instanceof Channel)open((Channel)item);});
        list.setOnItemLongClickListener((p,v,pos,id)->{Object item=list.getItemAtPosition(pos);if(item instanceof Channel){details((Channel)item);return true;}return false;});
        filter();restoreScroll(list);return menu.page(list);
    }
    private void addFilter(TvButtonRow row,String name,Runnable action){row.addView(b.button(name,false,v->action.run()));}
    private static String group(Channel c){return c.added()?c.description:c.category;}
    private List<Channel> available(){if(episodes!=null)return episodes;if("On Demand".equals(section))return tv.repo.films;if("Favourites".equals(section)||"Search".equals(section)){List<Channel> all=new ArrayList<Channel>(tv.repo.channels);all.addAll(tv.repo.films);return all;}return tv.repo.channels;}
    private void filter(){
        if(adapter==null)return;rows.clear();downloads.clear();planned=TvRecordings.all(getActivity());
        if("Downloads".equals(section)){for(TvDownloads.Item item:TvDownloads.all(getActivity())){Channel c=item.channel();if(c.matches(query)){rows.add(c);downloads.put(c.id,item);}}}
        else if(getArguments().getString("series")!=null&&episodes==null){/* loading */}
        else for(Channel c:available()){
            if("Favourites".equals(section)&&!tv.repo.favourite(c))continue;
            if("Search".equals(section)&&query.trim().length()<2)continue;
            if("On Demand".equals(section)&&getArguments().getString("series")==null&&!"All".equals(demandKind)&&!("Films".equals(demandKind)?Channel.FILM:"Series".equals(demandKind)?Channel.SERIES:Channel.EPISODE).equals(c.kind))continue;
            if(!c.matches(query))continue;
            if(!"All Regions".equals(country)&&!country.equals(c.region()))continue;
            if(!"All Categories".equals(category)&&!category.equals(group(c)))continue;
            if("Included Channels".equals(source)?c.added():!"All Sources".equals(source)&&!source.equals(c.source))continue;
            rows.add(c);
        }
        count.setText(tv.repo.loading?"Loading channels…":rows.size()+" "+("Downloads".equals(section)?"downloads":"On Demand".equals(section)?"titles":"channels")+(tv.repo.error==null?"":" · "+tv.repo.error)+(rows.isEmpty()&&!tv.repo.loading?" · No matches":""));
        adapter.notifyDataSetChanged();
    }
    private void refresh(){
        if(!isAdded()||list==null)return;
        if(!menuSignature.equals(tv.sections().toString())){getFragmentManager().beginTransaction().detach(this).attach(this).commitAllowingStateLoss();return;}
        filter();if(ruler!=null)ruler.invalidate();
    }
    private void open(Channel c){
        TvDownloads.Item item=downloads.get(c.id);
        if(item!=null&&!item.ready()){downloadMenu(item);return;}
        if(Channel.SERIES.equals(c.kind)){main().push(series(c));return;}
        tv.play(c);
    }
    public void details(Channel c){
        TvDownloads.Item item=downloads.get(c.id);if(item!=null){downloadMenu(item);return;}
        List<String> actions=new ArrayList<String>();List<Runnable> callbacks=new ArrayList<Runnable>();actions.add(Channel.SERIES.equals(c.kind)?"Episodes":"Watch");callbacks.add(()->open(c));
        actions.add(tv.repo.favourite(c)?"Remove Favourite":"Add Favourite");callbacks.add(()->tv.repo.toggleFavourite(c));
        if(c.live()){actions.add("Record");callbacks.add(()->tv.recordNow(c));}
        if(c.onDemand()&&!c.local()){actions.add("Download");callbacks.add(()->tv.download(c));}
        Ui.dialog(getActivity()).setTitle(c.name).setMessage(c.description+(c.source.isEmpty()?"":"\n\n"+c.source)+(c.availability.isEmpty()?"":"\n"+c.availability)).setItems(actions.toArray(new String[0]),(d,n)->callbacks.get(n).run()).setNegativeButton("Close",null).show();
    }
    private void downloadMenu(TvDownloads.Item item){
        List<String> actions=new ArrayList<String>();if(item.ready())actions.add("Play");else actions.add(TvDownloads.RUNNING.equals(item.state)||TvDownloads.QUEUED.equals(item.state)?"Pause":"Resume Download");actions.add("Delete Download");
        Ui.dialog(getActivity()).setTitle(item.title).setItems(actions.toArray(new String[0]),(d,n)->{
            if(n==1){if(tv.session.current!=null&&tv.session.current.id.equals(item.channel().id))tv.session.stop();TvDownloads.remove(getActivity(),item.id);tv.repo.changed();return;}
            if(item.ready()){tv.play(item.channel());return;}
            if(TvDownloads.RUNNING.equals(item.state)||TvDownloads.QUEUED.equals(item.state)){TvDownloadService.cancel(item.id);item.state=TvDownloads.PAUSED;}else{item.state=TvDownloads.QUEUED;item.error="";}
            TvDownloads.put(getActivity(),item);TvDownloads.wake(getActivity());
        }).setNegativeButton("Close",null).show();
    }
    @Override public void onResume(){super.onResume();tv.repo.listen(changed);TvDownloads.listeners.add(changed);TvRecordings.listeners.add(changed);refresh();}
    @Override public void onPause(){tv.repo.unlisten(changed);TvDownloads.listeners.remove(changed);TvRecordings.listeners.remove(changed);rememberScroll(list);super.onPause();}
    @Override public void onDestroyView(){list=null;adapter=null;super.onDestroyView();}
    @Override String backTitle(){return "TV";}
    private final class Rows extends BaseAdapter {
        public int getCount(){return rows.size();}public Channel getItem(int n){return rows.get(n);}public long getItemId(int n){return n;}
        public View getView(int n,View old,ViewGroup parent){
            Channel c=getItem(n);
            if("Guide".equals(section)){GuideViews.Row row=old instanceof GuideViews.Row?(GuideViews.Row)old:new GuideViews.Row(getActivity(),TvBrowsePage.this);row.bind(c);return row;}
            LinearLayout row=b.row();row.setPadding(b.dp(16),b.dp(8),b.dp(16),b.dp(8));row.setMinimumHeight(b.dp(80));
            if(!c.live()&&!c.poster.isEmpty()){ImageView art=new ImageView(getActivity());art.setScaleType(ImageView.ScaleType.CENTER_CROP);row.addView(art,new LinearLayout.LayoutParams(b.dp(48),b.dp(68)));Posters.show(getActivity(),art,c.poster,b.dp(48));}
            else{TextView initials=b.text(c.initials(),15,true);initials.setGravity(Gravity.CENTER);initials.setTextColor(b.onAccent);initials.setBackground(b.gradientCircle());row.addView(initials,new LinearLayout.LayoutParams(b.dp(48),b.dp(48)));}
            LinearLayout words=b.column();words.setPadding(b.dp(12),0,0,0);TextView title=b.text(c.name,16,true);title.setMaxLines(2);words.addView(title);
            TvDownloads.Item item=downloads.get(c.id);
            String note=item!=null?item.state+(item.total>0?" · "+(item.bytes*100/item.total)+"%":"")+(item.error.isEmpty()?"":" · "+item.error):c.meta!=null?c.meta:c.live()?(c.added()?c.source:c.countryName())+" · "+group(c):(Channel.SERIES.equals(c.kind)?"Series":Channel.EPISODE.equals(c.kind)?"Episode":"Film")+(c.year>0?" · "+c.year:"")+" · "+c.source;
            TextView meta=b.label(note,12,b.secondary);meta.setMaxLines(2);words.addView(meta);row.addView(words,new LinearLayout.LayoutParams(0,-2,1));
            return row;
        }
    }
    public TvStyle brand(){return b;}public Guide guide(){return tv.repo.guide;}public long start(){return guideStart;}public float minute(){return b.dp(2.4f);}public int column(){return b.dp(156);}public Channel playing(){return tv.session.active()?tv.session.current:null;}
    public void scrollBy(float px){long now=System.currentTimeMillis();guideStart=Math.max(now-12*Guide.HOUR,Math.min(now+24*Guide.HOUR,guideStart+(long)(px/minute()*Guide.MINUTE)));redrawGuide();}
    private void redrawGuide(){if(ruler!=null)ruler.invalidate();if(list!=null)list.invalidateViews();}
    public void tune(Channel c){tv.play(c);}public void program(Channel c,Guide.Program p){tv.program(c,p);}
    public boolean recording(Channel c,long start,long end){return TvRecordings.scheduledAt(planned,c.id,start,end)!=null;}
}
