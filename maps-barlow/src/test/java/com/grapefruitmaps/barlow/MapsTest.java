// SPDX-License-Identifier: GPL-3.0-or-later
package com.grapefruitmaps.barlow;

import android.app.*;
import android.view.*;
import java.io.*;
import java.net.*;
import java.nio.file.Files;
import android.widget.*;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.mapsforge.core.model.LatLong;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.android.controller.ActivityController;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={23,28,35},application=Application.class,qualifiers="w360dp-h640dp-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class MapsTest {
    private Application app;
    @Before public void setup(){app=RuntimeEnvironment.getApplication();app.getSharedPreferences("maps",0).edit().clear().commit();}
    private ActivityController<MainActivity> activity(int theme){app.getSharedPreferences("maps",0).edit().putInt("theme",theme).commit();return Robolectric.buildActivity(MainActivity.class).setup().visible();}
    static View text(View v,String label){if(v instanceof TextView&&label.contentEquals(((TextView)v).getText()))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View found=text(((ViewGroup)v).getChildAt(i),label);if(found!=null)return found;}return null;}
    static View described(View v,String label){if(label.contentEquals(String.valueOf(v.getContentDescription())))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View found=described(((ViewGroup)v).getChildAt(i),label);if(found!=null)return found;}return null;}
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
    @Test public void everyPageWorksWithoutAnApiKeyAndExplainsIt() {
        try(ActivityController<MainActivity> c=activity(0)){
            MainActivity a=c.get();assertFalse(a.landscape);
            assertNotNull(find(a,"Google Maps isn't set up"));assertNull(a.map);
            a.show("street");assertNotNull(find(a,"Google Maps isn't set up"));
            a.show("settings");assertNotNull(find(a,"Theme"));assertNotNull(find(a,"Map view"));assertNotNull(find(a,"About Grapefruit Maps: Barlow Edition"));
            a.show("directions");assertNotNull(find(a,"Get driving directions"));assertNotNull(find(a,"Navigate in Google Maps"));
            a.show("search");assertNotNull(find(a,"Find a place"));
            // Portrait keeps Legacy's bottom text tabs.
            assertEquals(LinearLayout.HORIZONTAL,a.tabs.getOrientation());assertNotNull(text(a.tabs,"Street View"));
        }
    }
    @Test @Config(qualifiers="w640dp-h360dp-land-mdpi") public void landscapeUsesIconRailCompactHeaderAndSideControls() {
        try(ActivityController<MainActivity> c=activity(0)){
            MainActivity a=c.get();assertTrue(a.landscape);
            assertEquals(LinearLayout.VERTICAL,a.tabs.getOrientation());assertEquals(LinearLayout.HORIZONTAL,a.root.getOrientation());
            for(String tab:new String[]{"Map","Street View","Directions","Settings"})assertNotNull(described(a.tabs,tab));
            TextView title=(TextView)find(a,"Maps");assertNotNull(title);
            assertTrue("Landscape header is compact",title.getTextSize()<=20*a.getResources().getDisplayMetrics().scaledDensity+.5f);
            a.show("settings");TextView heading=(TextView)find(a,"Settings");
            boolean small=false;View v=a.content;for(TextView t:texts(v,"Settings",new ArrayList<>()))if(t.getTextSize()<=22*a.getResources().getDisplayMetrics().scaledDensity+.5f&&t.getTextSize()>15)small=true;
            assertNotNull(heading);assertTrue(small);
        }
    }
    static List<TextView> texts(View v,String label,List<TextView> out){if(v instanceof TextView&&label.contentEquals(((TextView)v).getText()))out.add((TextView)v);if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)texts(((ViewGroup)v).getChildAt(i),label,out);return out;}
    @Test public void countryCatalogueIsAvailableAsTheOfflineFallback() {
        try(ActivityController<MainActivity> c=activity(0)){
            MainActivity a=c.get();a.show("settings");assertNotNull(find(a,"Offline mode"));assertNotNull(find(a,"Offline maps"));
            a.show("offline");MapDownloads d=a.downloads;assertTrue(d.entries.size()>400);Set<String> countries=new HashSet<>();for(MapDownloads.Entry e:d.entries){countries.add(e.country);assertTrue(e.path.endsWith(".map"));}
            assertTrue(countries.contains("United States"));assertTrue(countries.contains("Canada"));assertTrue(countries.size()>=190);
            a.onBackPressed();assertNotNull(find(a,"Offline mode"));
        }
    }
    @Test public void offlineModeWithoutDownloadsExplainsAndLinksToCountryMaps() {
        app.getSharedPreferences("maps",0).edit().putBoolean("offline_only",true).commit();
        try(ActivityController<MainActivity> c=activity(0)){
            MainActivity a=c.get();assertNotNull(find(a,"You're offline"));assertNull(a.map);
            ((View)find(a,"Offline maps")).performClick();assertNotNull(a.downloads);assertNotNull(find(a,"Country maps from OpenStreetMap · Mapsforge"));
            a.show("street");assertNotNull(find(a,"You're offline"));
        }
    }
    @Test public void downloadedMapIsUsedWhenGoogleIsNotAvailable() throws Exception {
        MapDownloads d=MapDownloads.get(app);File map=new File(d.directory,"fallback-andorra.map");map.getParentFile().mkdirs();
        Files.copy(new File(System.getProperty("maps.fixture")).toPath(),map.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        app.getSharedPreferences("maps",0).edit().putString("offline_file",map.getName()).commit();
        org.mapsforge.map.android.graphics.AndroidGraphicFactory.createInstance(app);
        try(ActivityController<MainActivity> c=activity(0)){
            MainActivity a=c.get();assertNotNull(a.map);assertEquals(map,a.map.offline);assertNull(a.map.session);
            assertNotNull(find(a,"Google Maps isn't set up in this build. Showing your downloaded map."));
            assertNotNull(find(a,"© OpenStreetMap contributors · ODbL · offline"));
        } finally {map.delete();}
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
    @Test public void decodesGooglePolylinesAndRoutesResponses() throws Exception {
        // Google's documented example: (38.5,-120.2), (40.7,-120.95), (43.252,-126.453).
        List<LatLong> points=Net.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@");
        assertEquals(3,points.size());assertEquals(38.5,points.get(0).latitude,1e-6);assertEquals(-120.95,points.get(1).longitude,1e-6);assertEquals(-126.453,points.get(2).longitude,1e-6);
        JSONObject route=Net.parseRoute(new JSONObject("{routes:[{distanceMeters:1234,duration:'567s',polyline:{encodedPolyline:'_p~iF~ps|U_ulLnnqC'},legs:[{steps:[{distanceMeters:100,navigationInstruction:{instructions:'Head north on Yonge St'}},{distanceMeters:50}]}]}]}"));
        assertEquals(1234,route.getDouble("distance"),0);assertEquals(567,route.getDouble("duration"),0);assertEquals(2,route.getJSONArray("points").length());
        assertEquals("Head north on Yonge St",route.getJSONArray("steps").getJSONObject(0).getString("text"));assertEquals("Continue",route.getJSONArray("steps").getJSONObject(1).getString("text"));
        try{Net.parseRoute(new JSONObject("{}"));fail();}catch(java.io.IOException expected){}
    }
    @Test public void waypointsUseCoordinatesOwnNamesOrAddresses() throws Exception {
        assertEquals(43.65,Net.waypoint("43.65, -79.38").getJSONObject("location").getJSONObject("latLng").getDouble("latitude"),1e-9);
        assertEquals(PlaceNames.GULF_POINT.latitude,Net.waypoint("Gulf of Mexico").getJSONObject("location").getJSONObject("latLng").getDouble("latitude"),1e-9);
        assertEquals("CN Tower, Toronto",Net.waypoint(" CN Tower, Toronto ").getString("address"));
        assertTrue(Net.navigationUrl("A b","C&d").contains("origin=A+b&destination=C%26d"));
    }
    @Test public void streetViewImagesStayWithinTheStaticApiLimits() throws Exception {
        String url=Net.streetViewImage(new LatLong(43.6426,-79.3871),-45,10,90,1280,700);
        assertTrue(url.contains("size=640x640"));assertTrue(url.contains("heading=315"));assertTrue(url.contains("location=43.642600,-79.387100"));
        assertEquals("North",MainActivity.compass(0));assertEquals("Northwest",MainActivity.compass(-45));assertEquals("East",MainActivity.compass(450));
    }
    @Test public void geographicNamesStayStableWithoutRenamingUnrelatedPlaces() {
        assertEquals(PlaceNames.GULF_POINT,PlaceNames.find("Gulf of Mexico"));assertEquals(PlaceNames.GULF_POINT,PlaceNames.find("gulf of america"));assertEquals(PlaceNames.LAKE_POINT,PlaceNames.find("Lake Ontario"));
        assertEquals("Gulf of Mexico, United States",PlaceNames.canonical("Gulf of America, United States"));
        assertEquals("Ontario Street, Toronto",PlaceNames.canonical("Ontario Street, Toronto"));
    }
    static final class Fake extends HttpURLConnection {
        final byte[] body; final int code; final Map<String,String> headers=new HashMap<>();
        Fake(byte[] b,int code)throws Exception{super(new URL("https://example.org/map"));body=b;this.code=code;headers.put("Content-Length",Integer.toString(b.length));headers.put("ETag","stable");}
        public int getResponseCode(){return code;}public String getHeaderField(String key){return headers.get(key);}public InputStream getInputStream(){return new ByteArrayInputStream(body);}public void disconnect(){}public boolean usingProxy(){return false;}public void connect(){}
    }
}
