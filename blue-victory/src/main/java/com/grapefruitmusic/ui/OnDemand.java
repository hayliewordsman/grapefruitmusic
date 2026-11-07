package com.grapefruitmusic.ui;
import android.content.Context;
import org.json.*;
import java.io.*;
import java.net.URLEncoder;
import java.util.*;

/**
 * The On Demand tab: films and series from the viewer's own IPTV services. A server login lists them
 * through the Xtream Codes API (get_vod_streams, get_series, get_series_info); a playlist's films and
 * episodes are the entries that are files rather than live channels (UserSources). The tab shows only
 * when a source has some. Blue Grapefruit provides none of this: it's what the viewer's service offers.
 */
public final class OnDemand {
    private final File cache;
    interface Fetcher { byte[] get(String url) throws IOException; }
    private final Fetcher fetcher;
    public OnDemand(Context c){this(c,url->UserSources.fetch(url,LivePlayer.USER_AGENT));}
    OnDemand(Context c,Fetcher fetcher){cache=new File(c.getFilesDir(),"ondemand");this.fetcher=fetcher;}
    private JSONObject cachedJson(UserSources.Source source){
        try{return new JSONObject(new String(UserSources.read(new android.util.AtomicFile(new File(cache,source.id+".json")).openRead(),UserSources.MAX_BYTES),"UTF-8"));}catch(Exception e){return new JSONObject();}
    }
    public List<Channel> cached(UserSources.Source source){try{return parse(source,cachedJson(source).toString());}catch(Exception e){return Collections.emptyList();}}
    /** Films-only and series-only providers work independently; transient failures retain good data. */
    public List<Channel> refresh(UserSources.Source source) throws IOException {
        if(!UserSources.LOGIN.equals(source.kind))return Collections.emptyList();
        String api=UserSources.base(source)+"/player_api.php?"+UserSources.auth(source);JSONObject old=cachedJson(source),all=new JSONObject();
        String[] keys={"vodCategories","vod","seriesCategories","series"};String[] actions={"get_vod_categories","get_vod_streams","get_series_categories","get_series"};int catalogs=0;IOException failure=null;
        try{
            for(int n=0;n<keys.length;n++){
                try{JSONArray data=array(api+"&action="+actions[n]);all.put(keys[n],data);if(n==1||n==3)catalogs++;}
                catch(IOException error){
                    String message=error.getMessage()==null?"":error.getMessage();boolean unsupported=message.contains("HTTP 404")||message.contains("HTTP 405")||message.contains("HTTP 501");
                    boolean denied=message.contains("HTTP 401")||message.contains("HTTP 403");
                    all.put(keys[n],unsupported||denied||old.optJSONArray(keys[n])==null?new JSONArray():old.optJSONArray(keys[n]));
                    if(!unsupported)failure=error;
                }
            }
            if(catalogs==0&&failure!=null)throw failure;
            List<Channel> parsed=parse(source,all.toString());write(new File(cache,source.id+".json"),all.toString());return parsed;
        }catch(IOException error){throw error;}catch(Exception error){throw new IOException("The provider’s films and series could not be read.");}
    }
    public void remove(String sourceId){new File(cache,sourceId+".json").delete();File[] files=cache.listFiles();if(files!=null)for(File file:files)if(file.getName().startsWith(sourceId+"-episodes-"))file.delete();}
    private JSONArray array(String url)throws IOException{
        try{String text=new String(fetcher.get(url),"UTF-8").trim();if(text.isEmpty())return new JSONArray();if(text.startsWith("["))return new JSONArray(text);JSONObject reply=new JSONObject(text);if(reply.has("error")||reply.has("message")||reply.has("user_info"))throw new IOException("The provider did not return an On Demand catalog.");return new JSONArray();}
        catch(JSONException error){throw new IOException("The provider returned an invalid On Demand catalog.");}
    }
    private static void write(File destination,String text)throws IOException{
        destination.getParentFile().mkdirs();android.util.AtomicFile file=new android.util.AtomicFile(destination);FileOutputStream output=null;
        try{output=file.startWrite();output.write(text.getBytes("UTF-8"));file.finishWrite(output);}catch(IOException error){if(output!=null)file.failWrite(output);throw error;}
    }
    Map<Integer,List<Channel>> episodesFor(UserSources.Source source,Channel series)throws IOException{
        if(source==null)throw new IOException("This provider is no longer linked.");String id=series.id.substring(series.id.lastIndexOf('-')+1);File file=new File(cache,source.id+"-episodes-"+id+".json");
        try{String text=new String(fetcher.get(UserSources.base(source)+"/player_api.php?"+UserSources.auth(source)+"&action=get_series_info&series_id="+URLEncoder.encode(id,"UTF-8")),"UTF-8");Map<Integer,List<Channel>> parsed=parseEpisodes(source,series,new JSONObject(text));write(file,text);return parsed;}
        catch(Exception error){if(file.isFile())try{return parseEpisodes(source,series,new JSONObject(new String(UserSources.read(new android.util.AtomicFile(file).openRead(),UserSources.MAX_BYTES),"UTF-8")));}catch(Exception ignored){}if(error instanceof IOException)throw (IOException)error;throw new IOException("The series’ episodes could not be read.");}
    }
    static List<Channel> parse(UserSources.Source s,String json) throws Exception {
        JSONObject all=new JSONObject(json);List<Channel> out=new ArrayList<>();
        Map<String,String> vodGroups=groups(all.optJSONArray("vodCategories")),seriesGroups=groups(all.optJSONArray("seriesCategories"));
        String user=URLEncoder.encode(s.username,"UTF-8"),pass=URLEncoder.encode(s.password,"UTF-8"),base=UserSources.base(s);
        JSONArray vod=all.optJSONArray("vod");
        for(int i=0;vod!=null&&i<vod.length()&&out.size()<UserSources.MAX_CHANNELS;i++){
            JSONObject v=vod.getJSONObject(i);String id=v.optString("stream_id");if(id.isEmpty())continue;
            String ext=v.optString("container_extension","mp4");
            Channel c=new Channel("vod-"+s.id+"-"+id,v.optString("name","Film").trim(),"","Added",group(vodGroups,v),"",s.name,
                new String[]{base+"/movie/"+user+"/"+pass+"/"+id+"."+ext},"",s.name,"");
            c.kind=Channel.FILM;c.poster=v.optString("stream_icon","");c.year=year(v.optString("year",v.optString("releaseDate","")));
            out.add(c);
        }
        JSONArray series=all.optJSONArray("series");
        for(int i=0;series!=null&&i<series.length()&&out.size()<UserSources.MAX_CHANNELS;i++){
            JSONObject v=series.getJSONObject(i);String id=v.optString("series_id");if(id.isEmpty())continue;
            Channel c=new Channel("series-"+s.id+"-"+id,v.optString("name","Series").trim(),"","Added",group(seriesGroups,v),"",s.name,new String[]{""},"",s.name,"");
            c.kind=Channel.SERIES;c.poster=v.optString("cover","");c.year=year(v.optString("releaseDate",v.optString("year","")));
            out.add(c);
        }
        return out;
    }
    private static Map<String,String> groups(JSONArray a) throws JSONException {
        Map<String,String> m=new HashMap<>();
        for(int i=0;a!=null&&i<a.length();i++){JSONObject c=a.getJSONObject(i);m.put(c.optString("category_id"),c.optString("category_name"));}
        return m;
    }
    private static String group(Map<String,String> groups,JSONObject v){String g=groups.get(v.optString("category_id"));return g==null?"":g;}
    private static int year(String s){
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("(19|20)\\d\\d").matcher(s==null?"":s);
        return m.find()?Integer.parseInt(m.group()):0;
    }
    /** A series' seasons and episodes (get_series_info), fetched when it's opened. Keys are season numbers. */
    public static Map<Integer,List<Channel>> episodes(UserSources.Source s,Channel series) throws IOException {
        String id=series.id.substring(series.id.lastIndexOf('-')+1);
        try{
            JSONObject info=new JSONObject(new String(UserSources.fetch(UserSources.base(s)+"/player_api.php?"+UserSources.auth(s)
                +"&action=get_series_info&series_id="+URLEncoder.encode(id,"UTF-8"),LivePlayer.USER_AGENT),"UTF-8"));
            return parseEpisodes(s,series,info);
        }catch(IOException e){throw e;}
        catch(Exception e){throw new IOException("The series' episodes couldn't be read.");}
    }
    static Map<Integer,List<Channel>> parseEpisodes(UserSources.Source s,Channel series,JSONObject info) throws Exception {
        Map<Integer,List<Channel>> out=new TreeMap<>();
        JSONObject seasons=info.optJSONObject("episodes");if(seasons==null)return out;
        String user=URLEncoder.encode(s.username,"UTF-8"),pass=URLEncoder.encode(s.password,"UTF-8"),base=UserSources.base(s);
        for(Iterator<String> it=seasons.keys();it.hasNext();){
            String key=it.next();JSONArray eps=seasons.optJSONArray(key);if(eps==null)continue;
            int season;try{season=Integer.parseInt(key);}catch(NumberFormatException e){season=0;}
            List<Channel> list=new ArrayList<>();
            for(int i=0;i<eps.length();i++){
                JSONObject e=eps.getJSONObject(i);String id=e.optString("id");if(id.isEmpty())continue;
                JSONObject more=e.optJSONObject("info");
                int number=e.optInt("episode_num",i+1);
                String title=e.optString("title","Episode "+number).trim();
                Channel c=new Channel("ep-"+s.id+"-"+id,title,"","Added",more==null?"":more.optString("plot",""),"",s.name,
                    new String[]{base+"/series/"+user+"/"+pass+"/"+id+"."+e.optString("container_extension","mp4")},"",s.name,"");
                c.kind=Channel.EPISODE;c.poster=more==null||more.optString("movie_image").isEmpty()?series.poster:more.optString("movie_image");
                c.seconds=more==null?0:more.optInt("duration_secs",0);
                c.meta=series.name+"  ·  Season "+season+", episode "+number+(c.seconds>0?"  ·  "+TvTimes.length(c.seconds):"");
                list.add(c);
            }
            out.put(season,list);
        }
        return out;
    }
}
