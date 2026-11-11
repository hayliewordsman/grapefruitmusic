// SPDX-License-Identifier: GPL-3.0-or-later
package com.grapefruitmaps.barlow;

import android.graphics.*;
import com.grapefruitmusic.ui.Fonts;
import java.io.File;
import java.util.*;
import org.mapsforge.core.model.*;
import org.mapsforge.core.util.MercatorProjection;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.datastore.*;
import org.mapsforge.map.layer.Layer;
import org.mapsforge.map.reader.MapFile;

/**
 * App-owned geographic labels stay stable independently of provider naming policies. Text baked
 * into Google's tiles cannot be rewritten, so Grapefruit draws its names above them and
 * normalizes search results.
 */
final class PlaceNames extends Layer {
    static final String GULF="Gulf of Mexico", LAKE="Lake Ontario";
    static final LatLong GULF_POINT=new LatLong(25.2834459,-89.9066163), LAKE_POINT=new LatLong(43.7340141,-77.7073563);
    private final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG), halo=new Paint(Paint.ANTI_ALIAS_FLAG);
    PlaceNames() {
        ink.setColor(0xff174d70);ink.setTextSize(14);ink.setTextAlign(Paint.Align.CENTER);ink.setTypeface(Fonts.semiBold());
        halo.set(ink);halo.setColor(0xfff5fbff);halo.setStyle(Paint.Style.STROKE);halo.setStrokeWidth(4);
    }
    static LatLong find(String query) {
        String q=query.trim();
        if(GULF.equalsIgnoreCase(q)||"Golfo de México".equalsIgnoreCase(q)||"Gulf of America".equalsIgnoreCase(q))return GULF_POINT;
        if(LAKE.equalsIgnoreCase(q)||"Lac Ontario".equalsIgnoreCase(q))return LAKE_POINT;
        return null;
    }
    /** Replaces only the renamed water body's name in a search result, leaving other text unchanged. */
    static String canonical(String result) {
        return result==null?"":result.replace("Gulf of America",GULF).replace("Golfo de América",GULF);
    }
    @Override public void draw(BoundingBox box,byte zoom,org.mapsforge.core.graphics.Canvas surface,org.mapsforge.core.model.Point origin) {
        Canvas canvas=AndroidGraphicFactory.getCanvas(surface);
        if(zoom>=3&&zoom<=10)label(canvas,GULF,GULF_POINT,zoom,origin);
        if(zoom>=5&&zoom<=13)label(canvas,LAKE,LAKE_POINT,zoom,origin);
    }
    private void label(Canvas c,String text,LatLong position,byte zoom,org.mapsforge.core.model.Point origin) {
        long size=MercatorProjection.getMapSize(zoom,displayModel.getTileSize());
        float x=(float)(MercatorProjection.longitudeToPixelX(position.longitude,size)-origin.x),y=(float)(MercatorProjection.latitudeToPixelY(position.latitude,size)-origin.y);
        c.drawText(text,x,y,halo);c.drawText(text,x,y,ink);
    }
    /** Normalize only the identified water bodies, leaving the downloaded database unchanged. */
    static final class CanonicalMapFile extends MapFile {
        CanonicalMapFile(File file){super(file,"en");}
        @Override public MapReadResult readMapData(Tile tile){return normalize(super.readMapData(tile));}
        @Override public MapReadResult readMapData(Tile a,Tile z){return normalize(super.readMapData(a,z));}
        @Override public MapReadResult readPoiData(Tile tile){return normalize(super.readPoiData(tile));}
        @Override public MapReadResult readLabels(Tile tile){return normalize(super.readLabels(tile));}
        static MapReadResult normalize(MapReadResult data) {
            if(data==null)return null;
            for(int i=0;i<data.pointOfInterests.size();i++) {
                PointOfInterest p=data.pointOfInterests.get(i);String name=null;
                if(near(p.position,GULF_POINT,.04))name=GULF;
                if(near(p.position,LAKE_POINT,.04))name=LAKE;
                if(name!=null&&water(p.tags))data.pointOfInterests.set(i,new PointOfInterest(p.layer,renamed(p.tags,name),p.position));
            }
            for(int i=0;i<data.ways.size();i++) {
                Way w=data.ways.get(i);if(!water(w.tags))continue;
                String name=null;
                for(Tag t:w.tags)if(t.key.equals("name")) {
                    if(t.value.equalsIgnoreCase(GULF)||t.value.equalsIgnoreCase("Golfo de México")||t.value.equalsIgnoreCase("Gulf of America"))name=GULF;
                    if(t.value.equalsIgnoreCase(LAKE)||t.value.equalsIgnoreCase("Lac Ontario"))name=LAKE;
                }
                // Lake Ontario's extent also identifies it if a provider changes its name tag.
                double minLat=90,maxLat=-90,minLon=180,maxLon=-180;
                for(LatLong[] line:w.latLongs)for(LatLong p:line){minLat=Math.min(minLat,p.latitude);maxLat=Math.max(maxLat,p.latitude);minLon=Math.min(minLon,p.longitude);maxLon=Math.max(maxLon,p.longitude);}
                if(Math.abs(minLat-43.175)<.15&&Math.abs(maxLat-44.293)<.15&&Math.abs(minLon+79.925)<.15&&Math.abs(maxLon+76.045)<.15)name=LAKE;
                if(name!=null)data.ways.set(i,new Way(w.layer,renamed(w.tags,name),w.latLongs,w.labelPosition));
            }
            return data;
        }
        private static boolean near(LatLong a,LatLong z,double limit){return Math.abs(a.latitude-z.latitude)<limit&&Math.abs(a.longitude-z.longitude)<limit;}
        private static boolean water(List<Tag> tags){for(Tag t:tags)if(t.key.equals("water")||t.key.equals("natural")&&Arrays.asList("water","bay","sea").contains(t.value)||t.key.equals("place")&&t.value.equals("sea"))return true;return false;}
        private static List<Tag> renamed(List<Tag> tags,String name){ArrayList<Tag> copy=new ArrayList<>();for(Tag t:tags)copy.add(t.key.equals("name")||t.key.startsWith("name:")?new Tag(t.key,name):t);return copy;}
    }
}
