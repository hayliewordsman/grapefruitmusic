package com.grapefruitmusic.ui;
import android.content.Context;
import java.util.*;
/** Counts only active playback and On Demand downloads; Blue has no recorder or DVR. */
final class Connections {
    private static final Map<String,Integer> jobs=new HashMap<String,Integer>();
    private static String watching;
    static int limit(Context c,String id){UserSources.Source s=TvRepository.get(c).sources.find(id);return s==null?1:Math.max(1,s.streams);}
    static synchronized int jobs(String id){Integer n=jobs.get(id);return n==null?0:n;}
    static synchronized int inUse(String id){return jobs(id)+(id.equals(watching)?1:0);}
    static synchronized boolean take(String id,int limit){if(inUse(id)>=limit)return false;jobs.put(id,jobs(id)+1);return true;}
    static synchronized void give(String id){int n=jobs(id)-1;if(n<=0)jobs.remove(id);else jobs.put(id,n);}
    static synchronized void watch(String id){watching=id;}
    static synchronized boolean canWatch(String id,int limit){return jobs(id)<limit;}
}
