// SPDX-License-Identifier: GPL-3.0-or-later
package com.grapefruitmaps;

import android.app.*;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.util.*;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.android.controller.ActivityController;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={23,28,35},application=Application.class,qualifiers="w360dp-h640dp-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class MapsTest {
    private Application app;
    @Before public void setup(){app=RuntimeEnvironment.getApplication();app.getSharedPreferences("maps",0).edit().clear().putBoolean("offline_only",true).commit();}
    private ActivityController<MainActivity> activity(int theme){app.getSharedPreferences("maps",0).edit().putInt("theme",theme).commit();return Robolectric.buildActivity(MainActivity.class).setup().visible();}
    static View text(View v,String label){if(v instanceof TextView&&label.contentEquals(((TextView)v).getText()))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View found=text(((ViewGroup)v).getChildAt(i),label);if(found!=null)return found;}return null;}
    private View find(MainActivity a,String label){return text(a.getWindow().getDecorView(),label);}
    @Test public void onlyVictoryBlueUnlocksOnExactlyFifthDeveloperTapAndBackClearsDrops(){
        for(int theme=0;theme<4;theme++)try(ActivityController<MainActivity> c=activity(theme)){
            MainActivity a=c.get();a.show("about");View haylie=find(a,"Haylie");assertNotNull(haylie);
            for(int i=0;i<4;i++){((View)haylie.getParent()).performClick();assertNull(a.egg);}
            ((View)haylie.getParent()).performClick();
            if(theme==3){assertNotNull(a.egg);EggView old=a.egg;old.drop();old.drop();assertEquals(2,old.count);a.onBackPressed();assertNull(a.egg);assertEquals(0,old.count);assertNotNull(find(a,"Haylie"));a.show("egg");assertEquals(0,a.egg.count);}
            else {assertNull(a.egg);a.show("egg");assertNull(a.egg);}
        }
    }
    @Test public void countryCatalogueAndAllSettingsAreUsableOffline() {
        try(ActivityController<MainActivity> c=activity(0)){
            MainActivity a=c.get();a.show("settings");assertNotNull(find(a,"Theme"));assertNotNull(find(a,"Offline mode"));assertNotNull(find(a,"About Grapefruit Maps Legacy"));
            a.show("offline");MapDownloads d=a.downloads;assertTrue(d.entries.size()>400);Set<String> countries=new HashSet<>();for(MapDownloads.Entry e:d.entries){countries.add(e.country);assertTrue(e.path.endsWith(".map"));}assertTrue(countries.contains("United States"));assertTrue(countries.contains("Canada"));assertTrue(countries.size()>=190);
            a.show("directions");assertNotNull(find(a,"Get driving directions"));a.show("map");assertNotNull(find(a,"You're offline"));
        }
    }
    @Test public void downloadedMapStreamsValidatesAndResumesWithIfRange() throws Exception {
        byte[] valid=Files.readAllBytes(new File(System.getProperty("maps.fixture")).toPath());
        MapDownloads.Entry e=new MapDownloads.Entry(new JSONObject("{country:'Andorra',region:'Andorra',path:'europe/andorra.map',size:'2.4M'}"));
        final Fake[] current={new Fake(valid,200)};
        MapDownloads d=new MapDownloads(app,url->current[0]);File target=d.file(e);target.delete();new File(target+".part").delete();
        d.transfer(e);assertArrayEquals(valid,Files.readAllBytes(target.toPath()));target.delete();
        int split=1000;
        Files.write(new File(target+".part").toPath(),Arrays.copyOf(valid,split));Files.write(new File(target+".http").toPath(),"stable".getBytes("UTF-8"));
        current[0]=new Fake(Arrays.copyOfRange(valid,split,valid.length),206);current[0].headers.put("Content-Range","bytes "+split+"-"+(valid.length-1)+"/"+valid.length);
        d.transfer(e);assertEquals("bytes=1000-",current[0].getRequestProperty("Range"));assertEquals("stable",current[0].getRequestProperty("If-Range"));assertArrayEquals(valid,Files.readAllBytes(target.toPath()));target.delete();
        Files.write(new File(target+".part").toPath(),valid);Files.write(new File(target+".http").toPath(),"stable".getBytes("UTF-8"));
        current[0]=new Fake(new byte[0],416);current[0].headers.put("Content-Range","bytes */"+valid.length);
        d.transfer(e);assertArrayEquals(valid,Files.readAllBytes(target.toPath()));target.delete();
        current[0]=new Fake("<html>error</html>".getBytes("UTF-8"),200);
        try{d.transfer(e);fail("HTML must not appear as an installed map");}catch(RuntimeException expected){}
        assertFalse(target.isFile());new File(target+".part").delete();new File(target+".http").delete();
    }
    @Test public void rejectsMalformedResumeAndTruncatedMaps() throws Exception {
        MapDownloads.Entry e=new MapDownloads.Entry(new JSONObject("{country:'Test',region:'Test',path:'test.map',size:'1M'}"));
        Fake bad=new Fake(new byte[10],206);MapDownloads d=new MapDownloads(app,url->bad);File target=d.file(e);target.delete();
        try{d.transfer(e);fail();}catch(IOException expected){assertTrue(expected.getMessage().contains("resume"));}
        Fake shortBody=new Fake(new byte[10],200);shortBody.headers.put("Content-Length","20");d=new MapDownloads(app,url->shortBody);
        try{d.transfer(e);fail();}catch(IOException expected){assertEquals("Incomplete map",expected.getMessage());}
        assertFalse(target.isFile());new File(target+".part").delete();new File(target+".http").delete();
    }
    @Test public void rejectsCoordinatesOutsideMercatorAndParsesValidInput(){assertNull(Net.coordinate("NaN, 1"));assertNull(Net.coordinate("90, 0"));assertNull(Net.coordinate("0, 181"));assertNull(Net.coordinate("Toronto"));assertEquals(-79.38,Net.coordinate("43.65, -79.38").longitude,0.00001);}
    @Test public void geographicNamesStayStableWithoutRenamingUnrelatedPlaces() throws Exception {
        assertEquals(PlaceNames.GULF_POINT,PlaceNames.find("Gulf of Mexico"));assertEquals(PlaceNames.LAKE_POINT,PlaceNames.find("Lake Ontario"));
        assertEquals("Gulf of Mexico",PlaceNames.canonicalResult(new JSONObject("{osm_type:'node',osm_id:305639190,display_name:'Changed upstream name'}")));
        assertEquals("Lake Ontario",PlaceNames.canonicalResult(new JSONObject("{osm_type:'relation',osm_id:1206310,display_name:'Changed upstream name'}")));
        assertEquals("Ontario Street",PlaceNames.canonicalResult(new JSONObject("{osm_type:'way',osm_id:42,display_name:'Ontario Street'}")));
        org.mapsforge.map.datastore.MapReadResult data=new org.mapsforge.map.datastore.MapReadResult();
        java.util.List<org.mapsforge.core.model.Tag> tags=Arrays.asList(new org.mapsforge.core.model.Tag("natural","water"),new org.mapsforge.core.model.Tag("name","Changed upstream name"));
        org.mapsforge.core.model.LatLong[][] outline={{new org.mapsforge.core.model.LatLong(43.175,-79.925),new org.mapsforge.core.model.LatLong(44.293,-76.045)}};
        data.ways.add(new org.mapsforge.map.datastore.Way((byte)0,tags,outline,PlaceNames.LAKE_POINT));
        PlaceNames.CanonicalMapFile.normalize(data);assertEquals("Lake Ontario",data.ways.get(0).tags.get(1).value);assertEquals("Changed upstream name",tags.get(1).value);
    }
    static final class Fake extends HttpURLConnection {
        final byte[] body; final int code; final Map<String,String> headers=new HashMap<>();
        Fake(byte[] b,int code)throws Exception{super(new URL("https://example.org/map"));body=b;this.code=code;headers.put("Content-Length",Integer.toString(b.length));headers.put("ETag","stable");}
        public int getResponseCode(){return code;}public String getHeaderField(String key){return headers.get(key);}public InputStream getInputStream(){return new ByteArrayInputStream(body);}public void disconnect(){}public boolean usingProxy(){return false;}public void connect(){}
    }
}
