package com.grapefruitfast;
import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;

/**
 * The viewer's own TV sources, added in Settings › Your Sources: an M3U playlist address, or a server
 * login (server, username, password: the Xtream Codes API many IPTV services offer). Grapefruit TV ships
 * no providers and no directory of them; it plays what the viewer adds, as any media player does. Channel
 * names only: no programme guide. Kept in the app's private storage on this device.
 */
public final class UserSources {
    public static final String M3U="m3u", LOGIN="login";
    /** One source the viewer added. */
    public static final class Source {
        public final String id, kind, name, address, username, password;
        public Source(String id,String kind,String name,String address,String username,String password){
            this.id=id;this.kind=kind;this.name=name;this.address=address;this.username=username;this.password=password;
        }
        JSONObject json() throws JSONException {
            return new JSONObject().put("id",id).put("kind",kind).put("name",name).put("address",address)
                .put("username",username).put("password",password);
        }
        static Source from(JSONObject o){
            return new Source(o.optString("id"),o.optString("kind"),o.optString("name"),o.optString("address"),o.optString("username"),o.optString("password"));
        }
    }
    /** Large playlists are common; these caps keep an old phone's memory safe. */
    static final int MAX_BYTES=40*1024*1024, MAX_CHANNELS=20000;
    private final SharedPreferences prefs;
    private final File cache;
    public UserSources(Context c,SharedPreferences prefs){this.prefs=prefs;this.cache=new File(c.getFilesDir(),"sources");}

    public List<Source> list() {
        List<Source> out=new ArrayList<>();
        try{JSONArray a=new JSONArray(prefs.getString("userSources","[]"));for(int i=0;i<a.length();i++)out.add(Source.from(a.getJSONObject(i)));}
        catch(JSONException e){/* nothing saved yet */}
        return out;
    }
    private void save(List<Source> all) {
        JSONArray a=new JSONArray();
        try{for(Source s:all)a.put(s.json());}catch(JSONException e){throw new IllegalStateException(e);}
        prefs.edit().putString("userSources",a.toString()).apply();
    }
    public Source add(String kind,String name,String address,String username,String password) {
        List<Source> all=list();
        Source s=new Source(UUID.randomUUID().toString().substring(0,8),kind,name,address.trim(),username.trim(),password);
        all.add(s);save(all);return s;
    }
    public void remove(String id) {
        List<Source> all=list();
        for(Iterator<Source> it=all.iterator();it.hasNext();) if(it.next().id.equals(id)) it.remove();
        save(all);new File(cache,id+".json").delete();
    }

    /** The channels last fetched for a source (from this device's cache), or an empty list. */
    public List<Channel> cached(Source s) {
        File f=new File(cache,s.id+".json");
        if(!f.exists()) return Collections.emptyList();
        try{return fromJson(s,new String(read(new FileInputStream(f),MAX_BYTES),"UTF-8"));}
        catch(Exception e){return Collections.emptyList();}
    }
    /** Fetches a source's channels now (network, off the main thread) and caches them. */
    public List<Channel> refresh(Source s,String userAgent) throws IOException {
        List<Channel> channels=LOGIN.equals(s.kind)?login(s,userAgent):playlist(s,userAgent);
        try{
            cache.mkdirs();
            JSONArray a=new JSONArray();
            for(Channel c:channels)a.put(new JSONObject().put("name",c.name).put("group",c.description).put("url",c.streams[0]).put("ua",c.userAgent));
            FileOutputStream out=new FileOutputStream(new File(cache,s.id+".json"));out.write(a.toString().getBytes("UTF-8"));out.close();
        }catch(JSONException e){throw new IOException(e.getMessage());}
        return channels;
    }
    private static List<Channel> fromJson(Source s,String json) throws JSONException {
        JSONArray a=new JSONArray(json);List<Channel> out=new ArrayList<>();
        for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);out.add(channel(s,i,o.getString("name"),o.optString("group"),o.getString("url"),o.optString("ua")));}
        return out;
    }
    static Channel channel(Source s,int n,String name,String group,String url,String userAgent) {
        return new Channel("src-"+s.id+"-"+n,name,"","Added",group,"",s.name,new String[]{url},"",s.name,userAgent);
    }

    /** An M3U/M3U8 playlist: #EXTINF lines (name after the comma, group-title), then each address. */
    List<Channel> playlist(Source s,String userAgent) throws IOException {
        return parseM3u(s,new String(fetch(s.address,userAgent),"UTF-8"));
    }
    static List<Channel> parseM3u(Source s,String text) throws IOException {
        if(!text.trim().startsWith("#EXTM3U")&&!text.contains("#EXTINF")) throw new IOException("That address isn't an M3U playlist.");
        List<Channel> out=new ArrayList<>();
        String name=null,group="",ua="";
        for(String raw:text.split("\r?\n")) {
            String line=raw.trim();
            if(line.startsWith("#EXTINF")) {
                int comma=line.lastIndexOf(',');
                name=comma>=0?line.substring(comma+1).trim():"";
                group=attribute(line,"group-title");
                if(name.isEmpty())name=attribute(line,"tvg-name");
            } else if(line.startsWith("#EXTGRP:")) {
                group=line.substring(8).trim();
            } else if(line.startsWith("#EXTVLCOPT:http-user-agent=")) {
                ua=line.substring("#EXTVLCOPT:http-user-agent=".length()).trim();
            } else if(!line.isEmpty()&&!line.startsWith("#")) {
                if(!line.startsWith("http://")&&!line.startsWith("https://")) {name=null;continue;}
                out.add(channel(s,out.size(),name==null||name.isEmpty()?"Channel "+(out.size()+1):name,group,line,ua));
                name=null;group="";ua="";
                if(out.size()>=MAX_CHANNELS) break;
            }
        }
        if(out.isEmpty()) throw new IOException("The playlist has no channels.");
        return out;
    }
    static String attribute(String line,String key) {
        int at=line.indexOf(key+"=\"");if(at<0)return "";
        int start=at+key.length()+2,end=line.indexOf('"',start);
        return end>start?line.substring(start,end).trim():"";
    }

    /** A server login (the Xtream Codes API): the live categories and streams, names only. */
    List<Channel> login(Source s,String userAgent) throws IOException {
        String base=s.address.replaceAll("/+$","");
        if(!base.startsWith("http://")&&!base.startsWith("https://")) base="http://"+base;
        String auth="username="+URLEncoder.encode(s.username,"UTF-8")+"&password="+URLEncoder.encode(s.password,"UTF-8");
        try {
            JSONObject info=new JSONObject(new String(fetch(base+"/player_api.php?"+auth,userAgent),"UTF-8"));
            JSONObject user=info.optJSONObject("user_info");
            if(user==null||user.optInt("auth",0)!=1) throw new IOException("The server didn't accept that username and password.");
            String format="ts";
            JSONArray formats=user.optJSONArray("allowed_output_formats");
            if(formats!=null) for(int i=0;i<formats.length();i++) if("m3u8".equals(formats.optString(i))) format="m3u8";
            Map<String,String> groups=new HashMap<>();
            JSONArray cats=new JSONArray(new String(fetch(base+"/player_api.php?"+auth+"&action=get_live_categories",userAgent),"UTF-8"));
            for(int i=0;i<cats.length();i++){JSONObject c=cats.getJSONObject(i);groups.put(c.optString("category_id"),c.optString("category_name"));}
            JSONArray streams=new JSONArray(new String(fetch(base+"/player_api.php?"+auth+"&action=get_live_streams",userAgent),"UTF-8"));
            List<Channel> out=new ArrayList<>();
            String userPart=URLEncoder.encode(s.username,"UTF-8"),passPart=URLEncoder.encode(s.password,"UTF-8");
            for(int i=0;i<streams.length()&&out.size()<MAX_CHANNELS;i++){
                JSONObject st=streams.getJSONObject(i);
                String id=st.optString("stream_id");if(id.isEmpty())continue;
                out.add(channel(s,out.size(),st.optString("name","Channel "+(out.size()+1)).trim(),groups.containsKey(st.optString("category_id"))?groups.get(st.optString("category_id")):"",
                    base+"/live/"+userPart+"/"+passPart+"/"+id+"."+format,""));
            }
            if(out.isEmpty()) throw new IOException("The server has no live channels for this login.");
            return out;
        } catch(JSONException e) {
            throw new IOException("That server didn't answer like a TV login. Check the address.");
        }
    }

    /** GET with the app's (or the source's) user agent; HTTP and HTTPS, as IPTV services use both. */
    static byte[] fetch(String url,String userAgent) throws IOException {
        String at=url;
        for(int hop=0;hop<5;hop++){
            URL u=new URL(at);
            if(!"https".equals(u.getProtocol())&&!"http".equals(u.getProtocol())) throw new IOException("Use an http:// or https:// address.");
            HttpURLConnection c=(HttpURLConnection)u.openConnection();
            c.setInstanceFollowRedirects(false);c.setConnectTimeout(15000);c.setReadTimeout(30000);
            c.setRequestProperty("User-Agent",userAgent);
            try{
                int code=c.getResponseCode();
                if(code>=300&&code<400&&c.getHeaderField("Location")!=null){at=new URL(u,c.getHeaderField("Location")).toString();continue;}
                if(code==401||code==403) throw new IOException("The service refused (HTTP "+code+"). Check your login, or whether it's offered where you are.");
                if(code>=400) throw new IOException("The service answered HTTP "+code+".");
                return read(c.getInputStream(),MAX_BYTES);
            } finally {c.disconnect();}
        }
        throw new IOException("Too many redirects.");
    }
    static byte[] read(InputStream in,int limit) throws IOException {
        try{
            ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[16384];int n;
            while((n=in.read(buf))!=-1){if(out.size()+n>limit)throw new IOException("That playlist is too large.");out.write(buf,0,n);}
            return out.toByteArray();
        } finally {in.close();}
    }
}
