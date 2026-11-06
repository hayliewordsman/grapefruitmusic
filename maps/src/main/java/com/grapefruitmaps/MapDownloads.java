// SPDX-License-Identifier: GPL-3.0-or-later
package com.grapefruitmaps;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import org.json.*;
import org.mapsforge.map.reader.MapFile;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.Executors;

/** One streamed transfer at a time. .part + HTTP If-Range safely resume after process death. */
final class MapDownloads {
    static final String BASE = "https://download.mapsforge.org/maps/v5/";
    private static MapDownloads instance;
    static synchronized MapDownloads get(Context c) {
        if (instance == null) instance = new MapDownloads(c.getApplicationContext());
        return instance;
    }
    static final class Entry {
        final String country, region, path, size;
        Entry(JSONObject j) throws JSONException {
            country=j.getString("country"); region=j.getString("region"); path=j.getString("path"); size=j.getString("size");
            if (!path.matches("[a-zA-Z0-9_./-]+\\.map") || path.contains("..")) throw new JSONException("Invalid map path");
        }
        String label() { return country.equals(region) ? country : country + " / " + region; }
    }
    final File directory;
    final ArrayList<Entry> entries = new ArrayList<>();
    final java.util.concurrent.ExecutorService worker=Executors.newSingleThreadExecutor();
    final Handler main = new Handler(Looper.getMainLooper());
    volatile String active, status = "", error;
    volatile boolean cancelled;
    private volatile HttpURLConnection connection;
    Runnable listener;
    interface Connector { HttpURLConnection open(String url) throws IOException; }
    private final Connector connector;
    MapDownloads(Context c) { this(c,Net::open); }
    MapDownloads(Context c, Connector connector) {
        this.connector=connector;
        File storage=c.getExternalFilesDir(null);
        directory = new File(storage != null ? storage : c.getFilesDir(), "maps");
        if (!directory.isDirectory() && !directory.mkdirs()) error="Map storage is unavailable";
        try (InputStream in=c.getAssets().open("countries.json"); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] b=new byte[8192]; int n; while((n=in.read(b))!=-1) out.write(b,0,n);
            JSONArray a=new JSONArray(out.toString("UTF-8"));
            for(int i=0;i<a.length();i++) entries.add(new Entry(a.getJSONObject(i)));
        } catch(Exception e) { error="Cannot read map catalogue: "+e.getMessage(); }
    }
    File file(Entry e) { return new File(directory, e.path.replace("/", "__")); }
    void update() { main.post(() -> { if(listener!=null) listener.run(); }); }
    synchronized void cancel() { cancelled=true; HttpURLConnection c=connection; if(c!=null) c.disconnect(); }
    synchronized boolean start(final Entry e) {
        if(active!=null) return false;
        active=e.path; error=null; cancelled=false; status="Connecting…"; update();
        worker.execute(new Runnable() {
            @Override public void run() {
                try { transfer(e); status="Downloaded "+e.label(); }
                catch(Exception ex) { error=cancelled?"Paused. Tap the map to resume.":"Download stopped: "+ex.getMessage()+". Tap to retry."; }
                finally { active=null; connection=null; update(); }
            }
        });
        return true;
    }
    void transfer(Entry e) throws Exception {
        File target=file(e), part=new File(target+".part"), validator=new File(target+".http");
        long offset=part.length(); String tag="";
        if(validator.isFile()) try(BufferedReader r=new BufferedReader(new FileReader(validator))) { tag=r.readLine(); }
        if(tag==null) tag="";
        if(offset>0 && tag.isEmpty()) { if(!part.delete()) throw new IOException("Cannot reset partial map"); offset=0; }
        HttpURLConnection c=connector.open(BASE+e.path); connection=c;
        if(offset>0) { c.setRequestProperty("Range","bytes="+offset+"-"); c.setRequestProperty("If-Range",tag); }
        try {
            int code=c.getResponseCode();
            if(code==416&&offset>0&&("bytes */"+offset).equals(c.getHeaderField("Content-Range"))) {
                MapFile complete=new MapFile(part);complete.close();
                if(!part.renameTo(target))throw new IOException("Cannot save completed map");
                validator.delete();return;
            }
            if(code!=200 && code!=206) throw new IOException("HTTP "+code);
            if(code==200) offset=0;
            String range=c.getHeaderField("Content-Range");
            if(code==206 && (range==null || !range.startsWith("bytes "+offset+"-"))) throw new IOException("Invalid resume response");
            String length=c.getHeaderField("Content-Length");
            long remaining=length==null?-1:Long.parseLong(length), total=remaining<0?-1:offset+remaining;
            if(remaining>0 && directory.getUsableSpace()<remaining+16*1024*1024) throw new IOException("Not enough free storage");
            String next=c.getHeaderField("ETag"); if(next==null) next=c.getHeaderField("Last-Modified");
            try(FileWriter w=new FileWriter(validator)) { w.write(next==null?"":next); }
            long done=offset, last=0;
            try(InputStream in=c.getInputStream(); FileOutputStream out=new FileOutputStream(part,offset>0)) {
                byte[] b=new byte[64*1024]; int n;
                while((n=in.read(b))!=-1) {
                    if(cancelled) throw new IOException("Paused");
                    out.write(b,0,n); done+=n;
                    if(System.currentTimeMillis()-last>700) {
                        status=(done/1048576)+" MB"+(total>0?" / "+(total/1048576)+" MB":"")+" · "+e.region;
                        last=System.currentTimeMillis(); update();
                    }
                }
                out.getFD().sync();
            }
            if(total>=0 && done!=total) throw new IOException("Incomplete map");
            if(cancelled) throw new IOException("Paused");
            MapFile check=new MapFile(part); check.close();
            if(!part.renameTo(target)) throw new IOException("Cannot save downloaded map");
            if(validator.exists()) validator.delete();
        } finally { c.disconnect(); }
    }
}
