package com.grapefruitfast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.util.*;

/** Only a reviewed catalog is loaded. Never import unrestricted IPTV lists or proxy feeds. */
public final class Catalog {
    public static final int MAX_BYTES = 512 * 1024;
    private Catalog() {}
    public static List<Channel> parse(String json) throws Exception {
        JSONObject root=new JSONObject(json);
        if (root.getInt("schema") != 1) throw new IOException("Unsupported channel list");
        JSONArray rows=root.getJSONArray("channels");
        if (rows.length()==0 || rows.length()>250) throw new IOException("Invalid channel count");
        List<Channel> result=new ArrayList<>(); Set<String> ids=new HashSet<>();
        for(int i=0;i<rows.length();i++) {
            JSONObject row=rows.getJSONObject(i);
            String id=row.getString("id"), country=row.getString("country");
            if (!id.matches("[a-z0-9-]{1,80}") || !ids.add(id)) throw new IOException("Invalid channel ID");
            if (!Arrays.asList(Channel.COUNTRIES).contains(country)) throw new IOException("Invalid country");
            String website=row.getString("website"); secure(website);
            JSONArray urls=row.getJSONArray("streams");
            if (urls.length()==0 || urls.length()>5) throw new IOException("Missing stream");
            String[] streams=new String[urls.length()];
            for(int j=0;j<streams.length;j++) { streams[j]=urls.getString(j); secure(streams[j]); }
            result.add(new Channel(id, row.getString("name"), country, row.getString("category"),
                row.getString("description"), website, row.getString("provenance"), streams,
                row.optString("availability", "")));
        }
        return Collections.unmodifiableList(result);
    }
    private static void secure(String value) throws Exception {
        URL url=new URL(value);
        if (!"https".equals(url.getProtocol()) || url.getHost().length()==0 || url.getUserInfo()!=null)
            throw new IOException("Channels must use public HTTPS addresses");
    }
    public static String read(InputStream input) throws IOException {
        try {
            ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] buffer=new byte[8192]; int n;
            while((n=input.read(buffer))!=-1) {
                if(out.size()+n>MAX_BYTES) throw new IOException("Channel list is too large");
                out.write(buffer,0,n);
            }
            return out.toString("UTF-8");
        } finally { input.close(); }
    }
}
