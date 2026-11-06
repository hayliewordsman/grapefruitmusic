package com.grapefruitfast;
import android.content.Context;
import java.util.*;

/**
 * Streams in use on each of the viewer's own services, so the app never opens more than a service allows
 * (many IPTV plans allow one or two at once): what the app plays takes one, and so does each recording
 * and download. Free channels and Movies have no limit.
 */
final class Connections {
    private Connections() {}
    private static final Map<String,Integer> jobs=new HashMap<>();
    /** The service the app is playing from now, or null. */
    private static String watching;
    /** Stops what the app is playing (set by the app; run on its main thread) so a recording can have its stream. */
    static volatile Runnable stopWatching;
    private static final android.os.Handler main=new android.os.Handler(android.os.Looper.getMainLooper());
    /** How many streams a source allows (a login says; the viewer can change it in Settings). */
    static int limit(Context c,String sourceId) {
        UserSources.Source s=new UserSources(c,c.getSharedPreferences("fast",0)).find(sourceId);
        return s==null?1:Math.max(1,s.streams);
    }
    static synchronized int inUse(String sourceId){Integer n=jobs.get(sourceId);return (n==null?0:n)+(sourceId.equals(watching)?1:0);}
    /** Recordings and downloads using a source now. */
    static synchronized int jobs(String sourceId){Integer n=jobs.get(sourceId);return n==null?0:n;}
    /**
     * Takes a stream for a job if the source has one free. A recording (takePlayback) may have the one the
     * app is playing: the app stops playing and says why. A download waits instead.
     */
    static synchronized boolean take(String sourceId,int limit,boolean takePlayback) {
        if(inUse(sourceId)>=limit){
            if(!takePlayback||!sourceId.equals(watching)||jobs(sourceId)>=limit)return false;
            watching=null;final Runnable stop=stopWatching;if(stop!=null)main.post(stop);
        }
        jobs.put(sourceId,jobs(sourceId)+1);return true;
    }
    static synchronized void give(String sourceId){int n=jobs(sourceId)-1;if(n<=0)jobs.remove(sourceId);else jobs.put(sourceId,n);}
    /** The app plays from this source now (null: from none, or from a free channel). */
    static synchronized void watch(String sourceId){watching=sourceId;}
    static synchronized boolean canWatch(String sourceId,int limit){return jobs(sourceId)<limit;}
}
