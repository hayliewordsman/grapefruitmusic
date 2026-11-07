package com.grapefruitmusic.ui;
import com.grapefruitmusic.R;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import org.json.*;
import java.io.*;
import java.util.*;

/**
 * The DVR's list: scheduled, running and finished recordings, and downloads for offline viewing, kept
 * on this device (recordings.json in the app's storage). A recording is a channel's stream saved between
 * two times; a download is a film's or an episode's file.
 *
 * So that a scheduled recording happens with the app in the background, swiped out of Recents or after a
 * restart: an exact alarm (AlarmManager) wakes TvRecorderService, a foreground service with a notification,
 * when the next recording is due; TvBootReceiver sets the alarm again after a restart (Android forgets
 * alarms then). A force stop from Android's settings cancels alarms until the app is next opened.
 */
public final class TvRecordings {
    public static final String SCHEDULED="scheduled", RECORDING="recording", FINISHED="finished", FAILED="failed",
        QUEUED="queued", DOWNLOADING="downloading";
    public static final String TYPE_RECORDING="recording", TYPE_DOWNLOAD="download";
    private TvRecordings() {}

    /** One recording or download. */
    public static final class Item {
        public String id="",type=TYPE_RECORDING,state=SCHEDULED,title="",channelId="",channelName="",url="",userAgent="",
            sourceId="",file="",error="",poster="",kind=Channel.RECORDING,description="";
        /** The programme's times (what the viewer chose), and the window recorded (with a little either side). */
        public long start,end,from,to;
        public long bytes,total;
        public boolean added;
        JSONObject json() throws JSONException {
            return new JSONObject().put("id",id).put("type",type).put("state",state).put("title",title).put("channelId",channelId)
                .put("channelName",channelName).put("url",url).put("userAgent",userAgent).put("sourceId",sourceId).put("file",file)
                .put("error",error).put("poster",poster).put("kind",kind).put("description",description).put("start",start).put("end",end)
                .put("from",from).put("to",to).put("bytes",bytes).put("total",total).put("added",added);
        }
        static Item from(JSONObject o){
            Item i=new Item();
            i.id=o.optString("id");i.type=o.optString("type",TYPE_RECORDING);i.state=o.optString("state",SCHEDULED);i.title=o.optString("title");
            i.channelId=o.optString("channelId");i.channelName=o.optString("channelName");i.url=o.optString("url");i.userAgent=o.optString("userAgent");
            i.sourceId=o.optString("sourceId");i.file=o.optString("file");i.error=o.optString("error");i.poster=o.optString("poster");
            i.kind=o.optString("kind",Channel.RECORDING);i.description=o.optString("description");
            i.start=o.optLong("start");i.end=o.optLong("end");i.from=o.optLong("from",i.start);i.to=o.optLong("to",i.end);
            i.bytes=o.optLong("bytes");i.total=o.optLong("total");i.added=o.optBoolean("added");
            return i;
        }
        public boolean download(){return TYPE_DOWNLOAD.equals(type);}
        /** Done and on this device: it plays with no connection. */
        public boolean ready(){return FINISHED.equals(state)&&new File(file).exists();}
        public boolean active(){return RECORDING.equals(state)||DOWNLOADING.equals(state);}
        public boolean waiting(){return SCHEDULED.equals(state)||QUEUED.equals(state);}
        /** As something to play: the saved file, on the watch page and in the floating player. */
        public Channel channel(){
            Channel c=new Channel("rec-"+id,title,"",download()?"On Demand":"Recording",description,"",channelName,
                new String[]{"file://"+file},"",added?channelName:"","");
            c.kind=download()?(Channel.EPISODE.equals(kind)?Channel.EPISODE:Channel.FILM):Channel.RECORDING;c.poster=poster;
            c.seconds=download()?0:(int)Math.max(0,(to-from)/1000);
            return c;
        }
    }

    // ---- The list, on disk. Everything goes through here, from the app and the service alike. ----

    private static File store(Context c){return new File(c.getFilesDir(),"tv-recordings.json");}
    public static synchronized List<Item> all(Context c) {
        List<Item> out=new ArrayList<>();
        File f=store(c);if(!f.exists()&&!new File(f+".bak").exists())return out;
        try{
            JSONArray a=new JSONArray(new String(UserSources.read(new android.util.AtomicFile(f).openRead(),8*1024*1024),"UTF-8"));
            for(int i=0;i<a.length();i++)out.add(Item.from(a.getJSONObject(i)));
        }catch(Exception e){/* unreadable: start again */}
        return out;
    }
    private static void save(Context c,List<Item> items) {
        android.util.AtomicFile store=new android.util.AtomicFile(store(c));FileOutputStream out=null;
        try{
            JSONArray a=new JSONArray();for(Item i:items)a.put(i.json());
            out=store.startWrite();out.write(a.toString().getBytes("UTF-8"));store.finishWrite(out);
        }catch(Exception e){if(out!=null)store.failWrite(out);/* preserve the last valid schedule */}
        changed();
    }
    public static synchronized Item get(Context c,String id){for(Item i:all(c))if(i.id.equals(id))return i;return null;}
    /** Adds an item, or replaces the one with its ID. */
    public static synchronized void put(Context c,Item item) {
        List<Item> items=all(c);boolean found=false;
        for(int i=0;i<items.size();i++)if(items.get(i).id.equals(item.id)){items.set(i,item);found=true;}
        if(!found)items.add(item);
        save(c,items);
    }
    /** A cancelled/deleted job must never be resurrected by a late progress callback. */
    static synchronized boolean updateExisting(Context c,Item item){
        List<Item> items=all(c);for(int n=0;n<items.size();n++)if(items.get(n).id.equals(item.id)){items.set(n,item);save(c,items);return true;}return false;
    }
    /** Removes an item and its file (stopping it first if it's running). */
    public static void remove(Context c,String id) {
        TvRecorderService.stop(id);
        synchronized(TvRecordings.class){
        List<Item> items=all(c);
        for(Iterator<Item> it=items.iterator();it.hasNext();){
            Item i=it.next();
            if(i.id.equals(id)){if(!i.file.isEmpty()){new File(i.file).delete();new File(i.file+".part").delete();}it.remove();}
        }
        save(c,items);}wake(c);
    }

    /** Told (on the main thread) when the list changes, so the TvRecordings tab and the guide can redraw. */
    public static volatile Runnable listener;
    static final java.util.concurrent.CopyOnWriteArrayList<Runnable> listeners=new java.util.concurrent.CopyOnWriteArrayList<Runnable>();
    private static final Handler main=new Handler(Looper.getMainLooper());
    static void changed(){final Runnable l=listener;main.post(()->{if(l!=null)l.run();for(Runnable callback:listeners)callback.run();});}

    // ---- Scheduling. ----

    /** Minutes recorded before a programme starts and after it ends, for clocks that disagree. */
    static int early(Context c){return c.getSharedPreferences("blue_tv",0).getInt("recordEarly",1);}
    static int late(Context c){return c.getSharedPreferences("blue_tv",0).getInt("recordLate",2);}
    /** Schedules a channel from start to end (now, if start has passed). */
    public static Item schedule(Context c,Channel ch,String title,long start,long end) {
        if(!ch.live()||ch.streams.length==0||ch.streams[0].isEmpty()||end<=Math.max(start,System.currentTimeMillis()))throw new IllegalArgumentException("Choose a live channel and a future recording end time.");
        Item i=new Item();
        i.id=UUID.randomUUID().toString().substring(0,12);i.type=TYPE_RECORDING;i.title=title;
        i.channelId=ch.id;i.channelName=ch.name;i.url=ch.streams[0];i.userAgent=ch.userAgent;i.added=ch.added();
        i.sourceId=ch.added()?Guide.sourceId(ch):"";i.description=ch.description;
        long now=System.currentTimeMillis();
        i.start=start;i.end=end;
        i.from=Math.max(now,start-early(c)*Guide.MINUTE);i.to=end+late(c)*Guide.MINUTE;
        i.file=new File(dir(c,TYPE_RECORDING),i.id+".ts").getAbsolutePath();
        put(c,i);wake(c);
        return i;
    }
    /** Queues a film or an episode to download for offline viewing. */
    public static Item download(Context c,Channel ch) {
        for(Item i:all(c))if(i.download()&&i.channelId.equals(ch.id))return i;
        Item i=new Item();
        i.id=UUID.randomUUID().toString().substring(0,12);i.type=TYPE_DOWNLOAD;i.state=QUEUED;i.title=ch.name;
        i.channelId=ch.id;i.channelName=ch.added()?ch.source:"On Demand";i.url=ch.streams[0];i.userAgent=ch.userAgent;i.added=ch.added();
        i.sourceId=ch.added()?Guide.sourceId(ch):"";i.poster=ch.poster;i.kind=ch.kind;i.description=ch.meta!=null?ch.meta:ch.description;
        i.start=i.from=System.currentTimeMillis();
        i.file=new File(dir(c,TYPE_DOWNLOAD),i.id+extension(ch.streams[0])).getAbsolutePath();
        put(c,i);wake(c);
        return i;
    }
    /** The download of this film or episode, if there is one. */
    public static Item downloadOf(Context c,String channelId){for(Item i:all(c))if(i.download()&&i.channelId.equals(channelId))return i;return null;}
    static String extension(String url){
        String path=url.toLowerCase(Locale.US);int q=path.indexOf('?');if(q>=0)path=path.substring(0,q);
        if(LivePlayer.hls(url))return ".ts"; // an HLS film is saved as its segments, joined
        int dot=path.lastIndexOf('.'),slash=path.lastIndexOf('/');
        String ext=dot>slash?path.substring(dot):".mp4";
        return ext.matches("\\.[a-z0-9]{2,4}")?ext:".mp4";
    }
    /** Where recordings and downloads go: the app's own folder on shared storage (no permission from 4.4), else internal. */
    static File dir(Context c,String type) {
        File base=null;
        if(Build.VERSION.SDK_INT>=19)try{base=c.getExternalFilesDir(null);}catch(RuntimeException e){/* no shared storage */}
        if(base==null)base=c.getFilesDir();
        File d=new File(base,TYPE_DOWNLOAD.equals(type)?"Downloads":"Recordings");d.mkdirs();return d;
    }
    static long free(Context c){return dir(c,TYPE_RECORDING).getUsableSpace();}

    /**
     * Wakes the recorder now if anything's due (or downloading), and sets an exact alarm for the next
     * recording. Called whenever the list changes, when the app opens, and after a restart.
     */
    public static void wake(Context c) {
        long now=System.currentTimeMillis(),next=Long.MAX_VALUE;boolean due=false;
        for(Item i:all(c)){
            if(i.download()){if(QUEUED.equals(i.state)||DOWNLOADING.equals(i.state))due=true;continue;}
            if(SCHEDULED.equals(i.state)||RECORDING.equals(i.state)){
                if(i.from<=now+5000)due=true;else next=Math.min(next,i.from);
            }
        }
        AlarmManager alarms=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent wake=TvRecorderService.pending(c);
        if(alarms!=null){
            alarms.cancel(wake);
            if(next!=Long.MAX_VALUE)alarm(alarms,next,wake);
        }
        if(due)TvRecorderService.start(c);
    }
    /** An exact alarm that works in Doze: 6.0+ allowWhileIdle, 4.4+ exact, 4.2 and 4.3 are always exact. */
    static void alarm(AlarmManager alarms,long at,PendingIntent wake) {
        if(Build.VERSION.SDK_INT>=31&&!alarms.canScheduleExactAlarms()){
            // Without the exact-alarm permission: as close as Android allows (it may be a few minutes late).
            alarms.setWindow(AlarmManager.RTC_WAKEUP,at,60_000,wake);return;
        }
        if(Build.VERSION.SDK_INT>=23)alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,wake);
        else if(Build.VERSION.SDK_INT>=19)alarms.setExact(AlarmManager.RTC_WAKEUP,at,wake);
        else alarms.set(AlarmManager.RTC_WAKEUP,at,wake);
    }
    /** Whether Android lets the app set exact alarms (12+; granted with the app from 13). */
    public static boolean exactAlarms(Context c){
        if(Build.VERSION.SDK_INT<31)return true;
        AlarmManager a=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);return a!=null&&a.canScheduleExactAlarms();
    }
    /** The scheduled recording of this channel at this time, if there is one (the guide marks it). */
    public static Item scheduledAt(List<Item> items,String channelId,long start,long end){
        for(Item i:items)if(!i.download()&&i.channelId.equals(channelId)&&(i.waiting()||i.active())&&i.start<end&&i.end>start)return i;
        return null;
    }
    /** "Tue 3 Nov, 8:00 – 9:00 PM", in the phone's own clock style. */
    public static String when(Context c,long start,long end) {
        java.text.DateFormat time=android.text.format.DateFormat.getTimeFormat(c);
        String day=new java.text.SimpleDateFormat("EEE d MMM",Locale.getDefault()).format(new Date(start));
        return day+", "+time.format(new Date(start))+" – "+time.format(new Date(end));
    }
    /** "540 MB", "1.2 GB". */
    public static String size(long bytes){
        if(bytes>=1_000_000_000L)return String.format(Locale.getDefault(),"%.1f GB",bytes/1e9);
        return Math.max(0,Math.round(bytes/1e6))+" MB";
    }
    /** Keeps Intents to the service package-local. */
    static Intent intent(Context c,String action){return new Intent(c,TvRecorderService.class).setAction(action);}
}
