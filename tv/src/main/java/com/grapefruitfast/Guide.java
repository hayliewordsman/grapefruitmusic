package com.grapefruitfast;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;
import java.io.*;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.GZIPInputStream;

/**
 * The programme guide (EPG). Where a channel's service publishes one, its programmes: an IPTV login's
 * short EPG (the Xtream Codes API), or a playlist's XMLTV guide (its url-tvg address and tvg-id). Where
 * nothing is published (most free channels), and in any gap, the guide shows blocks labelled "Program
 * information currently unavailable": an hour long on news channels, half an hour elsewhere, on the clock.
 */
public final class Guide {
    public static final String UNAVAILABLE="Program information currently unavailable";
    public static final long MINUTE=60_000L, HOUR=60*MINUTE;
    /** One programme, or a placeholder block where the guide has none. */
    public static final class Program {
        public final long start,end;public final String title,description;public final boolean placeholder;
        public Program(long start,long end,String title,String description,boolean placeholder){
            this.start=start;this.end=end;this.title=title;this.description=description==null?"":description;this.placeholder=placeholder;
        }
        public boolean on(long now){return start<=now&&now<end;}
    }
    /** How long a placeholder block is: an hour on news channels, half an hour on everything else. */
    public static long block(Channel c){return c.news()?HOUR:30*MINUTE;}
    /**
     * The channel's guide from..to: its known programmes in order, with every gap filled by placeholder
     * blocks that start and end on the block's clock boundaries (so 8:00, 9:00 on news; 8:00, 8:30 elsewhere).
     */
    public static List<Program> fill(Channel c,List<Program> known,long from,long to) {
        long block=block(c);
        List<Program> sorted=new ArrayList<>();
        if(known!=null)for(Program p:known)if(p.end>from&&p.start<to&&p.end>p.start)sorted.add(p);
        Collections.sort(sorted,(a,b)->Long.compare(a.start,b.start));
        List<Program> out=new ArrayList<>();
        long at=floor(from,block);
        for(Program p:sorted){
            if(p.start<at){if(p.end>at){out.add(p);at=p.end;}continue;} // overlaps: keep the earlier one
            placeholders(out,at,p.start,block);out.add(p);at=p.end;
        }
        placeholders(out,at,Math.max(at,ceil(to,block)),block);
        return out;
    }
    private static void placeholders(List<Program> out,long from,long to,long block) {
        long at=from;
        while(at<to){long next=Math.min(to,floor(at,block)+block);out.add(new Program(at,next,UNAVAILABLE,"",true));at=next;}
    }
    /** Local clock boundaries (so blocks start on the hour where the viewer is, half-hour time zones too). */
    static long floor(long t,long step){
        long offset=TimeZone.getDefault().getOffset(t);
        return (t+offset)/step*step-offset;
    }
    static long ceil(long t,long step){long f=floor(t,step);return f==t?t:f+step;}
    /** The programme on at a time (a placeholder if the guide has none). */
    public Program now(Channel c,long time){
        for(Program p:fill(c,known(c),time,time+1))if(p.on(time))return p;
        return new Program(floor(time,block(c)),floor(time,block(c))+block(c),UNAVAILABLE,"",true);
    }

    // ---- The published guides, fetched in the background and kept for a while. ----

    public interface Sources { UserSources.Source find(String id); }
    private final Sources sources;
    private final Map<String,List<Program>> byChannel=new ConcurrentHashMap<>();
    private final Map<String,Long> asked=new ConcurrentHashMap<>();
    private final Map<String,Map<String,List<Program>>> xmltv=new ConcurrentHashMap<>();
    private final ExecutorService work=Executors.newFixedThreadPool(2);
    private final Handler main=new Handler(Looper.getMainLooper());
    /** Told (on the main thread) when more of the guide has arrived. */
    public Runnable changed;
    public Guide(Sources sources){this.sources=sources;}
    /** The programmes known for a channel (none for most free channels); asks the service for more if it's time. */
    public List<Program> known(Channel c) {
        List<Program> got=byChannel.get(c.id);
        if(c.added())request(c);
        return got==null?Collections.<Program>emptyList():got;
    }
    /** Fetches a channel's published guide, at most every 30 minutes. */
    void request(final Channel c) {
        Long last=asked.get(c.id);long now=System.currentTimeMillis();
        if(last!=null&&now-last<30*MINUTE)return;
        asked.put(c.id,now);
        final UserSources.Source s=sources.find(sourceId(c));
        if(s==null)return;
        work.execute(()->{
            try{
                List<Program> got=UserSources.LOGIN.equals(s.kind)?shortEpg(s,c):xmltvFor(s,c);
                if(got!=null&&!got.isEmpty()){byChannel.put(c.id,got);main.post(()->{if(changed!=null)changed.run();});}
            }catch(Exception e){/* no guide from the service: placeholders */}
        });
    }
    /** "src-<source>-<n>" → the source's ID. */
    static String sourceId(Channel c){String[] p=c.id.split("-");return p.length>=3?p[1]:"";}
    /** An Xtream login's channel address ends /live/<user>/<pass>/<stream id>.<ext>. */
    static String streamId(String url){
        int slash=url.lastIndexOf('/'),dot=url.lastIndexOf('.');
        return slash>=0?url.substring(slash+1,dot>slash?dot:url.length()):"";
    }
    private List<Program> shortEpg(UserSources.Source s,Channel c) throws Exception {
        String id=streamId(c.streams[0]);if(id.isEmpty())return null;
        String json=new String(UserSources.fetch(UserSources.base(s)+"/player_api.php?"+UserSources.auth(s)+"&action=get_short_epg&stream_id="+URLEncoder.encode(id,"UTF-8")+"&limit=16",LivePlayer.USER_AGENT),"UTF-8");
        return parseShortEpg(json);
    }
    /** get_short_epg: titles and descriptions in Base64, times as Unix seconds (or "yyyy-MM-dd HH:mm:ss" UTC). */
    static List<Program> parseShortEpg(String json) throws Exception {
        JSONArray rows=new JSONObject(json).optJSONArray("epg_listings");
        List<Program> out=new ArrayList<>();if(rows==null)return out;
        SimpleDateFormat utc=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US);utc.setTimeZone(TimeZone.getTimeZone("UTC"));
        for(int i=0;i<rows.length();i++){
            JSONObject r=rows.getJSONObject(i);
            long start=r.optLong("start_timestamp",0)*1000,end=r.optLong("stop_timestamp",0)*1000;
            if(start==0&&r.has("start"))start=utc.parse(r.getString("start")).getTime();
            if(end==0&&r.has("end"))end=utc.parse(r.getString("end")).getTime();
            if(end<=start)continue;
            out.add(new Program(start,end,decode(r.optString("title")),decode(r.optString("description")),false));
        }
        return out;
    }
    private static String decode(String b64){
        if(b64.isEmpty())return "";
        try{return new String(Base64.decode(b64,Base64.DEFAULT),"UTF-8").trim();}catch(Exception e){return b64;}
    }
    /** A playlist's XMLTV guide, read once (every 6 hours) for all of its channels. */
    private List<Program> xmltvFor(UserSources.Source s,Channel c) throws Exception {
        if(c.guideId.isEmpty())return null;
        String url=s.guide;if(url.isEmpty())return null;
        Map<String,List<Program>> all=xmltv.get(s.id);
        Long loaded=asked.get("xmltv:"+s.id);
        if(all==null||loaded==null||System.currentTimeMillis()-loaded>6*HOUR){
            synchronized(this){
                all=xmltv.get(s.id);
                if(all==null){
                    asked.put("xmltv:"+s.id,System.currentTimeMillis());
                    all=parseXmltv(open(url),System.currentTimeMillis()-2*HOUR,System.currentTimeMillis()+24*HOUR);
                    xmltv.put(s.id,all);
                }
            }
        }
        return all.get(c.guideId);
    }
    private static InputStream open(String url) throws IOException {
        byte[] data=UserSources.fetch(url,LivePlayer.USER_AGENT);
        InputStream in=new ByteArrayInputStream(data);
        if(data.length>2&&(data[0]&0xff)==0x1f&&(data[1]&0xff)==0x8b)in=new GZIPInputStream(in);
        return in;
    }
    /** XMLTV: <programme start="20261103200000 +0000" stop="…" channel="id"><title>…</title><desc>…</desc>. */
    static Map<String,List<Program>> parseXmltv(InputStream in,long from,long to) throws Exception {
        Map<String,List<Program>> out=new HashMap<>();
        XmlPullParser x=android.util.Xml.newPullParser();x.setInput(in,null);
        String channel=null,title=null,desc=null,field=null;long start=0,end=0;
        for(int e=x.getEventType();e!=XmlPullParser.END_DOCUMENT;e=x.next()){
            if(e==XmlPullParser.START_TAG){
                if("programme".equals(x.getName())){
                    channel=x.getAttributeValue(null,"channel");start=xmltvTime(x.getAttributeValue(null,"start"));end=xmltvTime(x.getAttributeValue(null,"stop"));title=null;desc=null;
                } else if(channel!=null&&("title".equals(x.getName())||"desc".equals(x.getName())))field=x.getName();
            } else if(e==XmlPullParser.TEXT&&field!=null){
                if("title".equals(field)&&title==null)title=x.getText().trim();else if("desc".equals(field)&&desc==null)desc=x.getText().trim();
            } else if(e==XmlPullParser.END_TAG){
                if("programme".equals(x.getName())&&channel!=null){
                    if(end>from&&start<to&&end>start&&title!=null){
                        List<Program> l=out.get(channel);if(l==null){l=new ArrayList<>();out.put(channel,l);}
                        l.add(new Program(start,end,title,desc,false));
                    }
                    channel=null;
                }
                field=null;
            }
        }
        in.close();
        return out;
    }
    static long xmltvTime(String v){
        if(v==null||v.length()<14)return 0;
        try{
            SimpleDateFormat f=new SimpleDateFormat(v.length()>=20?"yyyyMMddHHmmss Z":"yyyyMMddHHmmss",Locale.US);
            if(v.length()<20)f.setTimeZone(TimeZone.getTimeZone("UTC"));
            return f.parse(v.length()>=20?v.substring(0,20):v.substring(0,14)).getTime();
        }catch(Exception e){return 0;}
    }
}
