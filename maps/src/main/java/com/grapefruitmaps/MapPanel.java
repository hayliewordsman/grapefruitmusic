// SPDX-License-Identifier: GPL-3.0-or-later
package com.grapefruitmaps;

import android.content.Context;
import java.io.File;
import java.net.*;
import java.util.*;
import org.json.*;
import org.mapsforge.core.model.*;
import org.mapsforge.core.graphics.Style;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.android.util.AndroidUtil;
import org.mapsforge.map.android.view.MapView;
import org.mapsforge.map.layer.*;
import org.mapsforge.map.layer.cache.*;
import org.mapsforge.map.layer.download.*;
import org.mapsforge.map.layer.download.tilesource.AbstractTileSource;
import org.mapsforge.map.layer.overlay.Polyline;
import org.mapsforge.map.reader.MapFile;
import org.mapsforge.map.rendertheme.InternalRenderTheme;

final class MapPanel {
    interface PinListener { void pin(LatLong position); }
    final MapView view;
    private TileCache cache;
    private TileDownloadLayer download;
    private Polyline route;
    private Layer pin;
    static final class Source extends AbstractTileSource {
        final boolean satellite;
        Source(boolean satellite) {
            super(new String[]{satellite?"gibs.earthdata.nasa.gov":"tile.openstreetmap.org"},443);
            this.satellite=satellite; setUserAgent(Net.AGENT); setTimeoutConnect(15000); setTimeoutRead(20000);
        }
        public byte getZoomLevelMin() { return 0; }
        public byte getZoomLevelMax() { return (byte)(satellite?8:19); }
        public int getParallelRequestsLimit() { return 2; }
        public boolean hasAlpha() { return false; }
        public URL getTileUrl(Tile tile) throws MalformedURLException {
            String path=satellite?"/wmts/epsg3857/best/BlueMarble_ShadedRelief_Bathymetry/default/GoogleMapsCompatible_Level8/"
                    +tile.zoomLevel+"/"+tile.tileY+"/"+tile.tileX+".jpeg"
                    :"/"+tile.zoomLevel+"/"+tile.tileX+"/"+tile.tileY+".png";
            return new URL("https",getHostName(),path);
        }
        @Override public long getDefaultTimeToLive() { return 7L*24*60*60*1000; }
    }
    MapPanel(Context c, boolean satellite, File offline, LatLong center, byte zoom, int accent, PinListener listener) {
        view=new MapView(c);
        view.getModel().displayModel.setFixedTileSize(256);
        view.setBuiltInZoomControls(false);
        view.setZoomLevelMin((byte)1); view.setZoomLevelMax((byte)(satellite?8:19));
        // Bounded memory (24 tiles, about 6 MB) and disk (256 tiles), separate for each source.
        String key=offline!=null?"offline-"+offline.getName():satellite?"satellite":"streets";
        cache=new TwoLevelTileCache(new InMemoryTileCache(24),new FileSystemTileCache(256,new File(c.getCacheDir(),key),AndroidGraphicFactory.INSTANCE,true));
        if(offline!=null) {
            MapFile data=new PlaceNames.CanonicalMapFile(offline);
            view.getLayerManager().getLayers().add(AndroidUtil.createTileRendererLayer(cache,view.getModel().mapViewPosition,data,InternalRenderTheme.DEFAULT));
        } else {
            download=new TileDownloadLayer(cache,view.getModel().mapViewPosition,new Source(satellite),AndroidGraphicFactory.INSTANCE);
            view.getLayerManager().getLayers().add(download); download.start();
        }
        view.setCenter(center); view.setZoomLevel((byte)Math.min(satellite?8:19,zoom));
        Layer touches=new Layer() {
            @Override public void draw(BoundingBox b,byte z,org.mapsforge.core.graphics.Canvas canvas,Point p) {}
            @Override public boolean onLongPress(LatLong point,Point layer,Point tap) { view.post(() -> listener.pin(point)); return true; }
        };
        view.getLayerManager().getLayers().add(touches);
        view.getLayerManager().getLayers().add(new PlaceNames());
    }
    void mark(LatLong point, int color) {
        if(pin!=null) view.getLayerManager().getLayers().remove(pin);
        org.mapsforge.core.graphics.Paint fill=AndroidGraphicFactory.INSTANCE.createPaint(); fill.setColor(color); fill.setStyle(Style.FILL);
        pin=new org.mapsforge.map.layer.overlay.FixedPixelCircle(point,9,fill,null);
        view.getLayerManager().getLayers().add(pin); view.repaint();
    }
    void route(JSONObject data, int color) throws JSONException {
        if(route!=null) view.getLayerManager().getLayers().remove(route);
        org.mapsforge.core.graphics.Paint paint=AndroidGraphicFactory.INSTANCE.createPaint();
        paint.setColor(color); paint.setStrokeWidth(6); paint.setStyle(Style.STROKE);
        route=new Polyline(paint,AndroidGraphicFactory.INSTANCE);
        JSONArray coords=data.getJSONObject("geometry").getJSONArray("coordinates");
        ArrayList<LatLong> points=new ArrayList<>();
        for(int i=0;i<coords.length();i++) { JSONArray p=coords.getJSONArray(i); points.add(new LatLong(p.getDouble(1),p.getDouble(0))); }
        route.setPoints(points); view.getLayerManager().getLayers().add(route); view.repaint();
    }
    void pause() { if(download!=null) download.onPause(); }
    void resume() { if(download!=null) download.onResume(); }
    void destroy() { view.destroyAll(); }
}
