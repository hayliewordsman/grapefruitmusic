package com.grapefruitmusic.ui;

import android.app.Application;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.io.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={23,37},application=Application.class)
public class RollCallRepositoryTest {
    private Application app;
    @Before public void setup(){app=RuntimeEnvironment.getApplication();}
    static String fixture(String name)throws Exception{
        JSONObject member=new JSONObject().put("id","ga-senate").put("name",name).put("state","GA").put("chamber","us_senate").put("office","Senator").put("district","Class 2").put("status","projected").put("sources",new JSONArray().put("https://example.org/result"));
        JSONArray gaps=new JSONArray();for(String state:AcknowledgementsRoster.STATE_CODES)gaps.put(new JSONObject().put("state",state).put("chamber","state_house").put("district","1").put("reason","Not verified"));
        return new JSONObject().put("schema",1).put("scope","post-2026").put("snapshotLabel","November 6, 2026").put("feedUpdatedAt",Long.MAX_VALUE-100).put("complete",false).put("note","Provisional").put("members",new JSONArray().put(member)).put("unresolved",gaps).toString();
    }
    private RollCallRepository source(String text){return new RollCallRepository(app,()->new RollCallRepository.Response(new ByteArrayInputStream(text.getBytes("UTF-8"))));}
    @Test public void freshDataChangesWithoutAnApkUpdateAndSurvivesOfflineReopening()throws Exception{
        assertEquals("Included offline snapshot",source("").offline().source);
        source(fixture("First Winner")).refresh();source(fixture("Updated Winner")).refresh();
        RollCallRepository offline=new RollCallRepository(app,()->{throw new IOException("Offline");});
        assertEquals("Updated Winner",offline.offline().roster.members.get(0).name);
        try{offline.refresh();fail();}catch(IOException expected){}
        assertEquals("Updated Winner",offline.offline().roster.members.get(0).name);
    }
    @Test public void invalidOrEmptyResponsesNeverReplaceTheGoodOfflineCopy()throws Exception{
        source(fixture("Saved Winner")).refresh();
        for(String text:new String[]{"not JSON",fixture("Candidate").replace("projected","candidate"),fixture("Missing states").replace("\"GA\"","\"ZZ\""),fixture("Empty").replaceFirst("\\\"members\\\":\\[.*?\\],","\"members\":[],")}){
            try{source(text).refresh();fail("Invalid response accepted");}catch(Exception expected){}
            assertEquals("Saved Winner",source("").offline().roster.members.get(0).name);
        }
    }
    @Test public void interruptedDownloadsKeepThePreviousCache()throws Exception{
        source(fixture("Saved Winner")).refresh();
        RollCallRepository broken=new RollCallRepository(app,()->new RollCallRepository.Response(new InputStream(){int reads;public int read()throws IOException{if(reads++>20)throw new IOException("Connection lost");return '{';}}));
        try{broken.refresh();fail();}catch(IOException expected){}
        assertEquals("Saved Winner",broken.offline().roster.members.get(0).name);
    }
    @Test public void anOlderSlowResponseCannotOverwriteNewerCachedResults()throws Exception{
        source(fixture("Newer Winner")).refresh();String older=new JSONObject(fixture("Older Winner")).put("feedUpdatedAt",1).toString();assertEquals("Newer Winner",source(older).refresh().roster.members.get(0).name);assertEquals("Newer Winner",source("").offline().roster.members.get(0).name);
    }
    @Test public void oversizedResponsesKeepThePreviousCache()throws Exception{
        source(fixture("Saved Winner")).refresh();
        RollCallRepository oversized=new RollCallRepository(app,()->new RollCallRepository.Response(new InputStream(){int remaining=RollCallRepository.MAX_BYTES+1;public int read(){return remaining-- >0?'x':-1;}public int read(byte[] out,int offset,int length){if(remaining<=0)return -1;int size=Math.min(length,remaining);java.util.Arrays.fill(out,offset,offset+size,(byte)'x');remaining-=size;return size;}}));
        try{oversized.refresh();fail();}catch(IOException expected){}
        assertEquals("Saved Winner",oversized.offline().roster.members.get(0).name);
    }
}
