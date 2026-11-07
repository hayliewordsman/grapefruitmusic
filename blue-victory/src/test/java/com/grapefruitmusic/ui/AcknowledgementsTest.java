package com.grapefruitmusic.ui;

import android.app.Application;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={23,37},application=Application.class)
public class AcknowledgementsTest {
    @Test public void bundledSnapshotHasSourcesUniquePeopleAndExplicitGaps() throws Exception {
        AcknowledgementsRoster r=AcknowledgementsRoster.load(RuntimeEnvironment.getApplication());
        assertFalse(r.complete);assertTrue(r.members.size()>3000);assertTrue(r.unresolved.size()>0);
        Set<String> people=new HashSet<String>(),states=new HashSet<String>();
        for(AcknowledgementsRoster.Member m:r.members){assertFalse(m.sources.isEmpty());assertTrue("Duplicate person: "+m.label(),people.add(m.chamber+":"+m.state+":"+m.name));states.add(m.state);for(String flag:m.flags)assertFalse(m.identitySources.get(flag).isEmpty());}
        for(org.json.JSONObject gap:r.unresolved)states.add(gap.getString("state"));assertTrue(states.containsAll(Arrays.asList(AcknowledgementsRoster.STATE_CODES)));
        AcknowledgementsRoster.Member ossoff=find(r,"Jon Ossoff","us_senate");assertEquals("Senator Jon Ossoff, D-GA",ossoff.label());assertFalse(ossoff.detail().contains("District Class"));
        assertEquals("projected",find(r,"Troy Dale Jackson","us_senate").status);assertEquals("Special election",find(r,"Sherrod Brown","us_senate").district);
        assertEquals("projected",find(r,"Darcy Castro","state_senate").status);assertEquals("projected",find(r,"Heather Bagnall","state_house").status);
        assertEquals(Collections.singletonList("rainbow"),find(r,"Robert Garcia","us_house").flags);assertTrue(find(r,"Robert Garcia","state_house").flags.isEmpty());
        assertTrue(find(r,"Joanna Whaley","state_house").flags.containsAll(Arrays.asList("bisexual","transgender")));
        assertEquals("Governor-elect Maura Healey, D-MA",find(r,"Maura Healey","governor").label());assertTrue(find(r,"Tina Kotek","governor").flags.contains("rainbow"));
        assertTrue(find(r,"Danica Roem","state_senate").flags.contains("transgender"));
    }
    @Test public void allReviewedTransgenderMembersHaveASourcedFlag()throws Exception{
        AcknowledgementsRoster r=AcknowledgementsRoster.load(RuntimeEnvironment.getApplication());assertEquals("November 6, 2026",r.date);
        for(String name:new String[]{"Allison Montgomery","Alexis Hoffkling","Bentley Hudgins","Aime Wichtendahl","Elle Abigail Boatman","Joanna Whaley","Liish Kozlowski","Leigh Finke","Wick Thomas","Zooey Zephyr","SJ Howell","Alissandra Murray","Alice Wade","Jaelynn Scott","Dina Nina Martinez-Rutherford","Kim Coco Iwamoto"}){
            AcknowledgementsRoster.Member m=find(r,name,"state_house");assertTrue(name,m.flags.contains("transgender"));assertFalse(name,m.identitySources.get("transgender").isEmpty());
        }
        assertTrue(find(r,"Sarah McBride","us_house").flags.contains("transgender"));assertTrue(find(r,"Danica Roem","state_senate").flags.contains("transgender"));
        assertEquals("State Representative Allison Montgomery, D-AL",find(r,"Allison Montgomery","state_house").label());
        for(AcknowledgementsRoster.Member m:r.members)assertNotEquals("A candidate biography alone does not establish an election win","Alleria Stanley",m.name);
    }
    @Test public void unsourcedIdentityAndUnverifiedCandidateAreRejected() throws Exception {
        JSONObject m=new JSONObject().put("id","test").put("name","Example").put("state","GA").put("chamber","us_senate").put("office","Senator").put("status","projected").put("sources",new JSONArray().put("https://example.org/result")).put("flags",new JSONArray().put("rainbow"));
        try{new AcknowledgementsRoster.Member(m);fail("Unsourced identity must fail");}catch(JSONException expected){}
        m.put("flags",new JSONArray()).put("status","candidate");try{new AcknowledgementsRoster.Member(m);fail("Candidate is not a verified winner");}catch(JSONException expected){}
    }
    private static AcknowledgementsRoster.Member find(AcknowledgementsRoster r,String name,String chamber){for(AcknowledgementsRoster.Member m:r.members)if(name.equals(m.name)&&chamber.equals(m.chamber))return m;throw new AssertionError(name);}
}
