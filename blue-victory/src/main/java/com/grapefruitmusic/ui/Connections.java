package com.grapefruitmusic.ui;
import android.content.Context;
import java.util.*;
/** One shared provider connection budget for playback, downloads and DVR recordings. */
final class Connections {
    private static final Map<String,Integer> jobs=new HashMap<String,Integer>();
    private static String watching;
    interface StopWatcher { void stop(String sourceId); }
    static volatile StopWatcher stopWatching;
    private static final android.os.Handler main=new android.os.Handler(android.os.Looper.getMainLooper());
    static int limit(Context c,String id){UserSources.Source s=TvRepository.get(c).sources.find(id);return s==null?1:Math.max(1,s.streams);}
    static synchronized int jobs(String id){Integer n=jobs.get(id);return n==null?0:n;}
    static synchronized int inUse(String id){return jobs(id)+(id.equals(watching)?1:0);}
    static synchronized boolean take(String id,int limit){if(inUse(id)>=limit)return false;jobs.put(id,jobs(id)+1);return true;}
    static synchronized boolean take(String id,int limit,boolean takePlayback){
        if(inUse(id)<limit)return take(id,limit);
        if(!takePlayback||!id.equals(watching)||jobs(id)>=limit)return false;
        watching=null;jobs.put(id,jobs(id)+1);final StopWatcher stop=stopWatching;
        if(stop!=null){if(android.os.Looper.myLooper()==android.os.Looper.getMainLooper())stop.stop(id);else main.post(()->stop.stop(id));}
        return true;
    }
    static synchronized void give(String id){int n=jobs(id)-1;if(n<=0)jobs.remove(id);else jobs.put(id,n);}
    static synchronized void watch(String id){watching=id;}
    static synchronized boolean canWatch(String id,int limit){return jobs(id)<limit;}
}
