package com.grapefruitmusic.ui;

import android.content.*;
import android.net.*;
import android.os.*;
import com.grapefruitmusic.compat.Platform;
import com.grapefruitmusic.util.Prefs;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/** Only provider films/episodes are queued here. No live recording, alarms, DVR or recording state. */
final class TvDownloads {
    static final String QUEUED="queued",RUNNING="downloading",DONE="downloaded",FAILED="failed",PAUSED="paused";
    static final List<Runnable> listeners=new CopyOnWriteArrayList<Runnable>();
    private static final Handler main=new Handler(Looper.getMainLooper());
    static final class Item {
        String id="",channelId="",title="",source="",sourceId="",url="",userAgent="",file="",state=QUEUED,error="",poster="",kind=Channel.FILM,description="";
        long bytes,total;
        boolean ready(){return DONE.equals(state)&&new File(file).isFile();}
        Channel channel(){Channel c=new Channel("download-"+id,title,"","Added",description,"",source,new String[]{"file://"+file},"",source,"");c.kind=kind;c.poster=poster;return c;}
        JSONObject json() throws JSONException{return new JSONObject().put("id",id).put("channelId",channelId).put("title",title).put("source",source).put("sourceId",sourceId).put("url",url).put("ua",userAgent).put("file",file).put("state",state).put("error",error).put("poster",poster).put("kind",kind).put("description",description).put("bytes",bytes).put("total",total);}
        static Item from(JSONObject o){Item i=new Item();i.id=o.optString("id");i.channelId=o.optString("channelId");i.title=o.optString("title");i.source=o.optString("source");i.sourceId=o.optString("sourceId");i.url=o.optString("url");i.userAgent=o.optString("ua");i.file=o.optString("file");i.state=o.optString("state",QUEUED);i.error=o.optString("error");i.poster=o.optString("poster");i.kind=o.optString("kind",Channel.FILM);i.description=o.optString("description");i.bytes=o.optLong("bytes");i.total=o.optLong("total");return i;}
    }
    static synchronized List<Item> all(Context c){
        List<Item> list=new ArrayList<Item>();File f=new File(c.getFilesDir(),"tv-downloads.json");if(!f.isFile())return list;
        try{JSONArray a=new JSONArray(new String(UserSources.read(new FileInputStream(f),4*1024*1024),"UTF-8"));for(int n=0;n<a.length();n++)list.add(Item.from(a.getJSONObject(n)));}catch(Exception e){/* keep other libraries independent */}return list;
    }
    static Item get(Context c,String id){for(Item i:all(c))if(i.id.equals(id))return i;return null;}
    static Item forChannel(Context c,String id){for(Item i:all(c))if(i.channelId.equals(id))return i;return null;}
    private static void save(Context c,List<Item> all){
        try{JSONArray a=new JSONArray();for(Item i:all)a.put(i.json());File temp=new File(c.getFilesDir(),"tv-downloads.json.part");
            try(FileOutputStream out=new FileOutputStream(temp)){out.write(a.toString().getBytes("UTF-8"));out.getFD().sync();}
            if(!temp.renameTo(new File(c.getFilesDir(),"tv-downloads.json")))throw new IOException("Couldn't save downloads");
        }catch(Exception e){android.util.Log.w("BlueDownloads","Couldn't save the download list",e);}
        main.post(()->{for(Runnable r:listeners)r.run();});
    }
    static synchronized void put(Context c,Item item){List<Item> all=all(c);for(int n=0;n<all.size();n++)if(all.get(n).id.equals(item.id)){all.set(n,item);save(c,all);return;}all.add(item);save(c,all);}
    static synchronized void updateExisting(Context c,Item item){if(get(c,item.id)!=null)put(c,item);}
    static synchronized Item enqueue(Context c,Channel ch) throws IOException {
        if(!ch.onDemand()||ch.live()||ch.local()||ch.streams.length==0||ch.streams[0].isEmpty())throw new IOException("Only provider films and episodes can be downloaded.");
        Item old=forChannel(c,ch.id);if(old!=null){if(!old.ready()&&!RUNNING.equals(old.state)){old.state=QUEUED;old.error="";put(c,old);wake(c);}return old;}
        Item i=new Item();i.id=UUID.randomUUID().toString();i.channelId=ch.id;i.title=ch.name;i.source=ch.source;i.sourceId=Guide.sourceId(ch);i.url=ch.streams[0];i.userAgent=ch.userAgent;i.poster=ch.poster;i.kind=ch.kind;i.description=ch.meta==null?ch.description:ch.meta;
        File base=c.getExternalFilesDir(null);if(base==null)base=c.getFilesDir();File dir=new File(base,"TV Downloads");if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("No space for TV downloads");new File(dir,".nomedia").createNewFile();
        String path=i.url.split("\\?",2)[0];String ext=LivePlayer.hls(path)?".ts":path.substring(Math.max(path.lastIndexOf('.'),path.lastIndexOf('/')+1));if(!ext.matches("\\.[a-zA-Z0-9]{2,4}"))ext=".mp4";
        i.file=new File(dir,i.id+ext).getPath();put(c,i);wake(c);return i;
    }
    static synchronized void remove(Context c,String id){TvDownloadService.cancel(id);List<Item> all=all(c);for(Iterator<Item> it=all.iterator();it.hasNext();){Item i=it.next();if(i.id.equals(id)){new File(i.file).delete();new File(i.file+".part").delete();it.remove();}}save(c,all);}
    static synchronized void pauseSource(Context c,String id){TvDownloadService.pauseSource(id);for(Item i:all(c))if(i.sourceId.equals(id)&&(QUEUED.equals(i.state)||RUNNING.equals(i.state))){i.state=PAUSED;put(c,i);}}
    static boolean networkReady(Context c){ConnectivityManager cm=(ConnectivityManager)c.getSystemService(Context.CONNECTIVITY_SERVICE);if(cm==null)return false;NetworkInfo n=cm.getActiveNetworkInfo();return n!=null&&n.isConnected()&&(!Prefs.wifiOnlyDownloads()||!cm.isActiveNetworkMetered());}
    static void wake(Context c){for(Item i:all(c))if(QUEUED.equals(i.state)||RUNNING.equals(i.state)){Platform.service(c,new Intent(c,TvDownloadService.class));return;}}
}
