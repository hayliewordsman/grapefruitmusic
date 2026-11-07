package com.grapefruitmusic.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import java.util.*;
import java.util.concurrent.*;

/** Blue's 59-channel catalog, linked providers, guide and capability-dependent On Demand library. */
final class TvRepository {
    private static TvRepository instance;
    static synchronized TvRepository get(Context c){if(instance==null)instance=new TvRepository(c.getApplicationContext());return instance;}
    final Context context;
    final SharedPreferences prefs;
    final UserSources sources;
    final OnDemand demand;
    final Guide guide;
    final Handler main=new Handler(Looper.getMainLooper());
    final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final List<Runnable> listeners=new CopyOnWriteArrayList<Runnable>();
    private final Map<String,List<Channel>> channelsBySource=new LinkedHashMap<String,List<Channel>>();
    private final Map<String,List<Channel>> filmsBySource=new LinkedHashMap<String,List<Channel>>();
    private List<Channel> builtIn=Collections.emptyList();
    volatile List<Channel> channels=Collections.emptyList(),films=Collections.emptyList();
    volatile boolean loading=true;
    volatile String error;
    private TvRepository(Context c){
        context=c;prefs=c.getSharedPreferences("blue_tv",0);sources=new UserSources(c,prefs);demand=new OnDemand(c);
        guide=new Guide(id->sources.find(id));guide.changed=this::changed;
        worker.execute(()->{
            List<Channel> catalog=Collections.emptyList();String problem=null;
            Map<String,List<Channel>> live=new LinkedHashMap<String,List<Channel>>(),vod=new LinkedHashMap<String,List<Channel>>();
            try{catalog=Catalog.parse(Catalog.read(c.getAssets().open("tv/channels.json")));}catch(Exception e){problem="Couldn't read the TV channel catalog.";}
            for(UserSources.Source s:sources.list()){live.put(s.id,sources.cached(s));vod.put(s.id,demand.cached(s));}
            final List<Channel> got=catalog;final String failure=problem;
            main.post(()->{builtIn=got;channelsBySource.putAll(live);filmsBySource.putAll(vod);loading=false;error=failure;merge();changed();refresh(null);});
        });
    }
    void listen(Runnable r){listeners.add(r);}
    void unlisten(Runnable r){listeners.remove(r);}
    void changed(){for(Runnable r:listeners)r.run();}
    private void merge(){
        List<Channel> live=new ArrayList<Channel>(builtIn),vod=new ArrayList<Channel>();
        for(List<Channel> list:channelsBySource.values())for(Channel c:list){if(c.live())live.add(c);else vod.add(c);}
        for(List<Channel> list:filmsBySource.values())vod.addAll(list);
        channels=Collections.unmodifiableList(live);films=Collections.unmodifiableList(vod);
    }
    boolean hasOnDemand(){return !films.isEmpty();}
    List<String> sections(){
        List<String> result=new ArrayList<String>(Arrays.asList("Channels","Guide","Favourites","Search"));
        if(hasOnDemand())result.add("On Demand");
        if(hasOnDemand()||!TvDownloads.all(context).isEmpty())result.add("Downloads");
        result.add("Recordings");result.add("TV Apps");
        return result;
    }
    boolean favourite(Channel c){return prefs.getStringSet("favourites",Collections.<String>emptySet()).contains(c.id);}
    void toggleFavourite(Channel c){Set<String> saved=new HashSet<String>(prefs.getStringSet("favourites",Collections.<String>emptySet()));if(!saved.remove(c.id))saved.add(c.id);prefs.edit().putStringSet("favourites",saved).apply();changed();}
    Channel find(String id){for(Channel c:channels)if(c.id.equals(id))return c;for(Channel c:films)if(c.id.equals(id))return c;return null;}
    UserSources.Source source(Channel c){return sources.find(Guide.sourceId(c));}
    void refresh(UserSources.Source only){
        List<UserSources.Source> selected=only==null?sources.list():Collections.singletonList(only);
        if(selected.isEmpty())return;
        worker.execute(()->{
            for(UserSources.Source s:selected){
                List<Channel> live=null,vod=null;String failure=null;
                try{live=sources.refresh(s,LivePlayer.USER_AGENT);}catch(Exception e){failure="Couldn't refresh "+s.name+". Check its address, login and connection.";}
                if(UserSources.LOGIN.equals(s.kind))try{vod=demand.refresh(s);}catch(Exception e){if(failure==null)failure="Couldn't refresh On Demand from "+s.name+".";}
                final List<Channel> l=live,v=vod;final String message=failure;
                main.post(()->{if(sources.find(s.id)==null)return;if(l!=null)channelsBySource.put(s.id,l);if(v!=null)filmsBySource.put(s.id,v);error=message;merge();changed();});
            }
        });
    }
    void remove(String id){TvDownloads.pauseSource(context,id);sources.remove(id);demand.remove(id);channelsBySource.remove(id);filmsBySource.remove(id);merge();changed();}
}
