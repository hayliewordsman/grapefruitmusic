package com.grapefruitfast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.*;

/**
 * The Movies tab's films: a reviewed list (assets/movies.json, made by tools/tv/movies.py) of films that
 * are public domain in the United States or under Creative Commons licences, streamed and downloaded from
 * the Internet Archive. Hand-picked: nothing discriminatory.
 */
public final class Movies {
    private Movies() {}
    /** The genres, in the order the chips show them (only those with films appear). */
    static final String[] GENRES={"Comedy","Drama","Film Noir","Thriller","Horror","Sci-Fi","Western","Adventure","Musical","Family","Silent","Animation"};
    public static List<Channel> parse(String json) throws Exception {
        JSONArray rows=new JSONObject(json).getJSONArray("movies");
        if(rows.length()==0||rows.length()>500)throw new IOException("Invalid film count");
        List<Channel> out=new ArrayList<>();Set<String> ids=new HashSet<>();
        for(int i=0;i<rows.length();i++){
            JSONObject m=rows.getJSONObject(i);
            String id=m.getString("id"),url=m.getString("url");
            if(!ids.add(id))throw new IOException("Duplicate film");
            if(!url.startsWith("https://archive.org/"))throw new IOException("Films stream from the Internet Archive only");
            Channel c=new Channel(id,m.getString("title"),"",m.getString("genre"),m.getString("description"),
                m.optString("page",""),m.optString("licence",""),new String[]{url},"");
            c.kind=Channel.FILM;c.poster=m.optString("poster","");c.year=m.optInt("year");c.seconds=m.optInt("seconds",m.optInt("minutes")*60);
            out.add(c);
        }
        return out;
    }
    /** "1h 32m" or "14m". */
    static String length(int seconds) {
        int minutes=Math.round(seconds/60f);
        return minutes>=60?(minutes/60)+"h "+(minutes%60)+"m":minutes+"m";
    }
    /** A film's line under its title: "1940 · Comedy · 1h 32m". */
    static String meta(Channel c) {
        StringBuilder s=new StringBuilder();
        if(c.year>0)s.append(c.year);
        if(!c.category.isEmpty()&&!"Added".equals(c.category)){if(s.length()>0)s.append("  ·  ");s.append(c.category);}
        if(c.seconds>0){if(s.length()>0)s.append("  ·  ");s.append(length(c.seconds));}
        return s.toString();
    }
}
