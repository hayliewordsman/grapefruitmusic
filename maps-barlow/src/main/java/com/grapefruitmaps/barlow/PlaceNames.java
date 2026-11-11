// SPDX-License-Identifier: GPL-3.0-or-later
package com.grapefruitmaps.barlow;

import android.graphics.*;
import com.grapefruitmusic.ui.Fonts;
import org.mapsforge.core.model.*;
import org.mapsforge.core.util.MercatorProjection;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.layer.Layer;

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
}
