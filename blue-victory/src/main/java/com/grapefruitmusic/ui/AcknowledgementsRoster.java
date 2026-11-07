package com.grapefruitmusic.ui;

import android.content.Context;
import android.util.JsonReader;
import android.util.JsonToken;
import org.json.*;
import java.io.*;
import java.util.*;

/** A dated, source-backed snapshot; missing results and identities are never inferred. */
final class AcknowledgementsRoster {
    static final String CLOSING_MESSAGE="A special thank you to Donald Trump, for being such a deeply unpopular president that his entire party suffered a generational defeat. May accountability and justice be swift and ripple through his administration like the Blue Tsunami.";
    static final String[] STATE_CODES="AL AK AZ AR CA CO CT DE FL GA HI ID IL IN IA KS KY LA ME MD MA MI MN MS MO MT NE NV NH NJ NM NY NC ND OH OK OR PA RI SC SD TN TX UT VT VA WA WV WI WY".split(" ");
    static final String[] STATE_NAMES="Alabama|Alaska|Arizona|Arkansas|California|Colorado|Connecticut|Delaware|Florida|Georgia|Hawaii|Idaho|Illinois|Indiana|Iowa|Kansas|Kentucky|Louisiana|Maine|Maryland|Massachusetts|Michigan|Minnesota|Mississippi|Missouri|Montana|Nebraska|Nevada|New Hampshire|New Jersey|New Mexico|New York|North Carolina|North Dakota|Ohio|Oklahoma|Oregon|Pennsylvania|Rhode Island|South Carolina|South Dakota|Tennessee|Texas|Utah|Vermont|Virginia|Washington|West Virginia|Wisconsin|Wyoming".split("\\|");
    static String stateName(String code){for(int n=0;n<STATE_CODES.length;n++)if(STATE_CODES[n].equals(code))return STATE_NAMES[n];if("DC".equals(code))return "District of Columbia";if("PR".equals(code))return "Puerto Rico";if("VI".equals(code))return "U.S. Virgin Islands";if("GU".equals(code))return "Guam";if("MP".equals(code))return "Northern Mariana Islands";if("AS".equals(code))return "American Samoa";return code;}
    static final class Member {
        final String id,name,state,chamber,district,party,office,status;
        final List<String> sources,flags;
        final Map<String,List<String>> identitySources;
        Member(JSONObject value) throws JSONException {
            id=value.getString("id");name=value.getString("name");state=value.getString("state");chamber=value.getString("chamber");district=value.optString("district");party=value.optString("partyCode","D");office=value.getString("office");status=value.getString("status");
            if(id.isEmpty()||name.isEmpty()||stateName(state).equals(state)||!Arrays.asList("us_senate","us_house","governor","state_senate","state_house").contains(chamber)||!Arrays.asList("D","DFL","D-NPL").contains(party))throw new JSONException("Invalid acknowledgements member");
            if(!Arrays.asList("elected","projected","holdover").contains(status))throw new JSONException("Unverified candidates are not officeholders");
            sources=urls(value.getJSONArray("sources"));if(sources.isEmpty())throw new JSONException("Member has no election or term source");
            List<String> labels=new ArrayList<String>();Map<String,List<String>> evidence=new LinkedHashMap<String,List<String>>();
            JSONArray marked=value.optJSONArray("flags");JSONObject known=value.optJSONObject("identitySources");
            for(int n=0;marked!=null&&n<marked.length();n++){
                String flag=marked.getString(n);if(!Arrays.asList(PrideFlagView.RAINBOW,PrideFlagView.BISEXUAL,PrideFlagView.TRANSGENDER).contains(flag)||labels.contains(flag))throw new JSONException("Invalid pride flag");
                if(known==null||!known.has(flag))throw new JSONException("A pride flag requires public self-identification evidence");List<String> refs=urls(known.getJSONArray(flag));if(refs.isEmpty())throw new JSONException("Empty pride source");labels.add(flag);evidence.put(flag,refs);
            }
            flags=Collections.unmodifiableList(labels);identitySources=Collections.unmodifiableMap(evidence);
        }
        String label(){return office+" "+name+", "+party+"-"+state;}
        String detail(){return (district.isEmpty()?stateName(state):stateName(state)+" · "+(district.startsWith("Class ")||district.contains("election")?district:"District "+district))+("holdover".equals(status)?" · Continuing term":"projected".equals(status)?" · Projected winner":"");}
        boolean matches(String query){return (label()+" "+stateName(state)+" "+district).toLowerCase(Locale.US).contains(query.trim().toLowerCase(Locale.US));}
    }
    final String date,note;
    final long updatedAt;
    final boolean complete;
    final List<Member> members;
    final List<JSONObject> unresolved;
    AcknowledgementsRoster(JSONObject root) throws JSONException {this(root,null);}
    private AcknowledgementsRoster(JSONObject root,List<Member> streamed) throws JSONException {
        if(root.getInt("schema")!=1||!"post-2026".equals(root.getString("scope")))throw new JSONException("Unsupported acknowledgements snapshot");
        date=root.getString("snapshotLabel");note=root.getString("note");complete=root.getBoolean("complete");updatedAt=root.optLong("feedUpdatedAt",0);
        List<Member> people=streamed==null?new ArrayList<Member>():streamed;Set<String> ids=new HashSet<String>();
        if(streamed==null){JSONArray list=root.getJSONArray("members");for(int n=0;n<list.length();n++)people.add(new Member(list.getJSONObject(n)));}
        for(Member m:people)if(!ids.add(m.id))throw new JSONException("Duplicate acknowledgements member");
        Collections.sort(people,(a,b)->{int c=rank(a)-rank(b);if(c!=0)return c;c=stateName(a.state).compareTo(stateName(b.state));if(c!=0)return c;c=a.chamber.compareTo(b.chamber);return c!=0?c:a.name.compareToIgnoreCase(b.name);});members=Collections.unmodifiableList(people);
        List<JSONObject> pending=new ArrayList<JSONObject>();JSONArray gaps=root.getJSONArray("unresolved");for(int n=0;n<gaps.length();n++)pending.add(gaps.getJSONObject(n));unresolved=Collections.unmodifiableList(pending);
        if(complete&&!unresolved.isEmpty())throw new JSONException("A complete snapshot cannot have unresolved contests");
    }
    private static int rank(Member m){return "us_senate".equals(m.chamber)?0:"us_house".equals(m.chamber)?1:"governor".equals(m.chamber)?2:3;}
    private static List<String> urls(JSONArray values) throws JSONException {List<String> result=new ArrayList<String>();for(int n=0;n<values.length();n++){String value=values.getString(n);if(!value.startsWith("https://")&&!value.startsWith("http://"))throw new JSONException("Invalid acknowledgement source URL");if(!result.contains(value))result.add(value);}return Collections.unmodifiableList(result);}
    static AcknowledgementsRoster load(Context c) throws IOException,JSONException {
        return read(c.getAssets().open("acknowledgements/roster.json"));
    }
    static AcknowledgementsRoster read(InputStream input) throws IOException,JSONException {
        // Stream one person at a time instead of keeping a second full JSON object graph in
        // memory. Older Android devices have small heaps; source/audit extras are not UI data.
        try(JsonReader reader=new JsonReader(new InputStreamReader(input,"UTF-8"))){
            JSONObject header=new JSONObject();List<Member> people=new ArrayList<Member>();
            reader.beginObject();while(reader.hasNext()){
                String key=reader.nextName();
                if("members".equals(key)){reader.beginArray();while(reader.hasNext())people.add(new Member(readObject(reader)));reader.endArray();}
                else if("unresolved".equals(key)){JSONArray gaps=new JSONArray();reader.beginArray();while(reader.hasNext())gaps.put(readObject(reader));reader.endArray();header.put(key,gaps);}
                else if("schema".equals(key))header.put(key,reader.nextInt());
                else if("complete".equals(key))header.put(key,reader.nextBoolean());
                else if("feedUpdatedAt".equals(key))header.put(key,reader.nextLong());
                else if(Arrays.asList("scope","snapshotLabel","note").contains(key))header.put(key,reader.nextString());
                else reader.skipValue();
            }reader.endObject();if(reader.peek()!=JsonToken.END_DOCUMENT)throw new JSONException("Unexpected roster content");return new AcknowledgementsRoster(header,people);
        }
    }
    private static JSONObject readObject(JsonReader reader)throws IOException,JSONException{
        JSONObject result=new JSONObject();reader.beginObject();while(reader.hasNext()){String key=reader.nextName();result.put(key,readValue(reader));}reader.endObject();return result;
    }
    private static Object readValue(JsonReader reader)throws IOException,JSONException{
        JsonToken token=reader.peek();if(token==JsonToken.BEGIN_OBJECT)return readObject(reader);
        if(token==JsonToken.BEGIN_ARRAY){JSONArray result=new JSONArray();reader.beginArray();while(reader.hasNext())result.put(readValue(reader));reader.endArray();return result;}
        if(token==JsonToken.NULL){reader.nextNull();return JSONObject.NULL;}if(token==JsonToken.BOOLEAN)return reader.nextBoolean();return reader.nextString();
    }
    int pending(String state){int n=0;for(JSONObject gap:unresolved)if(state.equals(gap.optString("state"))&&gap.optString("chamber").startsWith("state_"))n++;return n;}
    String pendingChamberText(String chamber){StringBuilder text=new StringBuilder();for(JSONObject gap:unresolved)if(chamber.equals(gap.optString("chamber"))){if(text.length()>0)text.append("\n\n");text.append(stateName(gap.optString("state"))).append(" · ").append(gap.optString("district")).append(": ").append(gap.optString("reason"));}return text.toString();}
    String pendingText(String state){StringBuilder text=new StringBuilder();for(JSONObject gap:unresolved)if(state.equals(gap.optString("state"))&&gap.optString("chamber").startsWith("state_")){if(text.length()>0)text.append('\n');text.append(gap.optString("office")).append(gap.optString("district").isEmpty()?"":" · "+gap.optString("district")).append(": ").append(gap.optString("reason","Result not yet verified"));}return text.toString();}
}
