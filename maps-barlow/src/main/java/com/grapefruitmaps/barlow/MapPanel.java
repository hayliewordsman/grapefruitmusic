// SPDX-License-Identifier: GPL-3.0-or-later
package com.grapefruitmaps.barlow;

import android.content.Context;
import java.io.File;
import java.net.*;
import java.util.*;
import org.json.*;
import org.mapsforge.core.model.*;
import org.mapsforge.core.graphics.Style;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.android.view.MapView;
import org.mapsforge.map.layer.*;
import org.mapsforge.map.layer.cache.*;
import org.mapsforge.map.layer.download.*;
import org.mapsforge.map.layer.download.tilesource.AbstractTileSource;
import org.mapsforge.map.layer.overlay.Polyline;
import org.mapsforge.map.layer.renderer.TileRendererLayer;
import org.mapsforge.map.android.util.AndroidUtil;
import org.mapsforge.map.rendertheme.InternalRenderTheme;

/**
 * Google Map Tiles API imagery (Map, Satellite with labels, Terrain) drawn by the lightweight Mapsforge
 * view, or a downloaded OpenStreetMap country map rendered on the device as the offline fallback.
 */
final class MapPanel {
    interface PinListener { void pin(LatLong position); }
    static final String[] TYPES={"Map","Satellite","Terrain"};
    final MapView view;
    /** The Map Tiles session, or null for a downloaded offline map. */
    final String session;
    final File offline;
    private final TileCache cache;
    private TileDownloadLayer download;
    private Polyline route;
    private Layer pin;
    static final class Source extends AbstractTileSource {
        final String session; final int type;
        Source(String session,int type) {
            super(new String[]{"tile.googleapis.com"},443);
            this.session=session; this.type=type; setUserAgent(Net.AGENT); setTimeoutConnect(15000); setTimeoutRead(20000);
        }
        public byte getZoomLevelMin() { return 0; }
        public byte getZoomLevelMax() { return (byte)maxZoom(type); }
        public int getParallelRequestsLimit() { return 4; }
        public boolean hasAlpha() { return false; }
        public URL getTileUrl(Tile tile) throws MalformedURLException { return new URL(Net.tileUrl(session,tile.zoomLevel,tile.tileX,tile.tileY)); }
        // Short-lived local cache only, as Google's terms allow; tiles are fetched again after a day.
        @Override public long getDefaultTimeToLive() { return 24L*60*60*1000; }
        @Override public boolean equals(Object o) { return o instanceof Source&&((Source)o).session.equals(session); }
        @Override public int hashCode() { return session.hashCode(); }
    }
    static int maxZoom(int type) { return type==2?15:type==1?20:21; }
    /** Online Google imagery. */
    MapPanel(Context c, int type, String session, LatLong center, byte zoom, PinListener listener) { this(c,type,session,null,center,zoom,listener); }
    /** A downloaded OpenStreetMap country map, rendered locally with no network. */
    MapPanel(Context c, File offline, LatLong center, byte zoom, PinListener listener) { this(c,0,null,offline,center,zoom,listener); }
    private MapPanel(Context c, int type, String session, File offline, LatLong center, byte zoom, PinListener listener) {
        this.session=session; this.offline=offline;
        int max=offline!=null?19:maxZoom(type);
        view=new MapView(c);
        view.getModel().displayModel.setFixedTileSize(256);
        view.setBuiltInZoomControls(false);
        view.setZoomLevelMin((byte)1); view.setZoomLevelMax((byte)max);
        // Bounded memory (24 tiles, about 6 MB) and disk (256 tiles), separate for each view and map.
        String key=offline!=null?"offline-"+offline.getName():"google-"+Net.SESSION_TYPES[type];
        cache=new TwoLevelTileCache(new InMemoryTileCache(24),new FileSystemTileCache(256,new File(c.getCacheDir(),key),AndroidGraphicFactory.INSTANCE,true));
        if(offline!=null) {
            TileRendererLayer layer=AndroidUtil.createTileRendererLayer(cache,view.getModel().mapViewPosition,new PlaceNames.CanonicalMapFile(offline),InternalRenderTheme.DEFAULT);
            view.getLayerManager().getLayers().add(layer);
        } else {
            download=new TileDownloadLayer(cache,view.getModel().mapViewPosition,new Source(session,type),AndroidGraphicFactory.INSTANCE);
            view.getLayerManager().getLayers().add(download); download.start();
        }
        view.setCenter(center); view.setZoomLevel((byte)Math.min(max,zoom));
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
        JSONArray coords=data.getJSONArray("points");
        ArrayList<LatLong> points=new ArrayList<>();
        for(int i=0;i<coords.length();i++) { JSONArray p=coords.getJSONArray(i); points.add(new LatLong(p.getDouble(0),p.getDouble(1))); }
        route.setPoints(points); view.getLayerManager().getLayers().add(route); view.repaint();
    }
    void pause() { if(download!=null) download.onPause(); }
    void resume() { if(download!=null) download.onResume(); }
    void destroy() { view.destroyAll(); }
}
