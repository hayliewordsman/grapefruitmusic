/*
 * This file is a part of Grapefruitgram Messenger, based on Telegram X.
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Interactive maps without Google Play services: osmdroid (Apache-2.0) drawing OpenStreetMap tiles.
 * Map data © OpenStreetMap contributors (ODbL); the attribution is drawn over every map.
 */
package org.thunderdog.challegram.grapefruit;

import android.content.Context;
import android.graphics.Color;
import android.location.LocationManager;

import androidx.annotation.NonNull;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.ITileSource;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.CopyrightOverlay;
import org.osmdroid.views.overlay.TilesOverlay;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.unsorted.Settings;

public final class OsmMaps {
  private static boolean configured;

  public static final double DEFAULT_ZOOM = 16.0;
  public static final double CLICK_ZOOM = 17.0;

  private OsmMaps () { }

  /** osmdroid's settings, once: its own cache folder, and the user agent OSM's tile policy asks for. */
  public static synchronized void configure (@NonNull Context context) {
    if (configured)
      return;
    Context app = context.getApplicationContext();
    Configuration config = Configuration.getInstance();
    config.load(app, app.getSharedPreferences("grapefruit_osmdroid", Context.MODE_PRIVATE));
    config.setUserAgentValue(BuildConfig.APPLICATION_ID + "/" + BuildConfig.VERSION_NAME);
    config.setOsmdroidBasePath(new java.io.File(app.getCacheDir(), "osmdroid"));
    config.setOsmdroidTileCache(new java.io.File(app.getCacheDir(), "osmdroid/tiles"));
    configured = true;
  }

  /** A MapView set up as the app's maps expect: no zoom buttons, pinch to zoom, attribution shown. */
  public static MapView newMapView (@NonNull Context context) {
    configure(context);
    return setUp(new MapView(context));
  }

  public static MapView setUp (@NonNull MapView mapView) {
    mapView.setMultiTouchControls(true);
    mapView.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);
    mapView.setTilesScaledToDpi(true);
    mapView.setMinZoomLevel(2.0);
    mapView.setMaxZoomLevel(19.0);
    mapView.setHorizontalMapRepetitionEnabled(true);
    mapView.setVerticalMapRepetitionEnabled(false);
    mapView.getOverlays().add(new CopyrightOverlay(mapView.getContext()));
    applyMapType(mapView, Settings.MAP_TYPE_DEFAULT);
    return mapView;
  }

  /** Tiles for one of Settings.MAP_TYPE_*; dark is OpenStreetMap with its colours inverted. */
  public static void applyMapType (@NonNull MapView mapView, int mapType) {
    ITileSource source;
    boolean invert;
    switch (mapType) {
      case Settings.MAP_TYPE_TERRAIN:
        source = TileSourceFactory.OpenTopo;
        invert = false;
        break;
      case Settings.MAP_TYPE_DARK:
        source = TileSourceFactory.MAPNIK;
        invert = true;
        break;
      case Settings.MAP_TYPE_DEFAULT:
      default:
        source = TileSourceFactory.MAPNIK;
        invert = false;
        break;
    }
    if (mapView.getTileProvider().getTileSource() != source) {
      mapView.setTileSource(source);
    }
    mapView.getOverlayManager().getTilesOverlay().setColorFilter(invert ? TilesOverlay.INVERT_COLORS : null);
    mapView.getOverlayManager().getTilesOverlay().setLoadingBackgroundColor(invert ? Color.BLACK : Color.rgb(0xF2, 0xEF, 0xE9));
    mapView.getOverlayManager().getTilesOverlay().setLoadingLineColor(Color.TRANSPARENT);
  }

  /** The device's own location providers: satellites, and the network where the phone has it. */
  public static GpsMyLocationProvider newLocationProvider (@NonNull Context context) {
    GpsMyLocationProvider provider = new GpsMyLocationProvider(context);
    provider.addLocationSource(LocationManager.NETWORK_PROVIDER);
    provider.setLocationUpdateMinTime(2000);
    provider.setLocationUpdateMinDistance(5f);
    return provider;
  }

  /** Whether any location provider is switched on (the system setting, not the permission). */
  public static boolean isLocationEnabled (@NonNull Context context) {
    LocationManager manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    if (manager == null)
      return false;
    try {
      return manager.isProviderEnabled(LocationManager.GPS_PROVIDER) || manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    } catch (Throwable t) {
      return false;
    }
  }
}
