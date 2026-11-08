/*
 * This file is a part of Grapefruitgram Messenger, based on Telegram X.
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Telegram X's map screen (locations, live locations, dropped pins) on OpenStreetMap through
 * osmdroid, in place of MapGoogleController. Markers are drawn exactly as Telegram X draws them.
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.view.MotionEvent;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.mylocation.IMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.dialogs.ChatView;
import org.thunderdog.challegram.grapefruit.OsmMaps;
import org.thunderdog.challegram.loader.ImageCache;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageLoader;
import org.thunderdog.challegram.loader.Watcher;
import org.thunderdog.challegram.loader.WatcherReference;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibAccentColor;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.text.Letters;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.core.lambda.Destroyable;
import tgx.td.MessageId;

final class MapOsmController extends MapController<MapView, MapOsmController.MarkerData> {
  private static final float FINISHED_BROADCAST_ALPHA = .6f;

  private boolean mapReady;
  private @Nullable MyLocationNewOverlay myLocationOverlay;

  public MapOsmController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  protected MapView createMapView (Context context, int marginBottom) {
    OsmMaps.configure(context);
    MapView mapView = new MapView(context) {
      @Override
      public boolean dispatchTouchEvent (MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
          onUserMovedCamera();
        }
        return super.dispatchTouchEvent(event);
      }
    };
    OsmMaps.setUp(mapView);
    mapView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    // Leave the bottom sheet's height out of camera fitting.
    mapView.setPadding(0, 0, 0, marginBottom);
    return mapView;
  }

  public static class MarkerData implements Watcher, Destroyable {
    public final Tdlib tdlib;
    public final MapView mapView;
    public final Marker marker;

    private final WatcherReference reference = new WatcherReference(this);

    public Canvas canvas;
    public Bitmap bitmap;

    public MarkerData (Tdlib tdlib, MapView mapView, LocationPoint<MarkerData> point, Marker.OnMarkerClickListener clickListener) {
      this.tdlib = tdlib;
      this.mapView = mapView;
      this.marker = new Marker(mapView);
      marker.setRelatedObject(point);
      marker.setPosition(new GeoPoint(point.latitude, point.longitude));
      marker.setInfoWindow(null);
      marker.setOnMarkerClickListener(clickListener);
      Bitmap bitmap = null;
      if (point.isSelfLocation) {
        TdApi.User user = tdlib.myUser();
        TdlibAccentColor accentColor = tdlib.cache().userAccentColor(user);
        Letters letters = tdlib.cache().userLetters(user);
        TdApi.File avatar = user != null && user.profilePhoto != null ? user.profilePhoto.small : null;
        bitmap = newBitmap(this, accentColor, letters, avatar);
      } else if (point.isLiveLocation && point.message != null) {
        this.isActive = ((TdApi.MessageLiveLocation) point.message.content).expiresIn > 0;
        marker.setAlpha(isActive ? 1f : FINISHED_BROADCAST_ALPHA);
        bitmap = newBitmap(this, point.message);
      }
      if (bitmap != null) {
        marker.setIcon(new BitmapDrawable(mapView.getResources(), bitmap));
        marker.setAnchor(0.5f, 0.907f);
      } else {
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
      }
      mapView.getOverlays().add(marker);
      mapView.invalidate();
    }

    private @Nullable Bitmap newBitmap (MarkerData data, TdApi.Message message) {
      TdlibAccentColor accentColor;
      Letters letters;
      TdApi.File avatar;
      switch (message.senderId.getConstructor()) {
        case TdApi.MessageSenderChat.CONSTRUCTOR: {
          TdApi.Chat chat = tdlib.chat(((TdApi.MessageSenderChat) message.senderId).chatId);
          accentColor = tdlib.chatAccentColor(chat);
          letters = tdlib.chatLetters(chat);
          avatar = chat != null && chat.photo != null ? chat.photo.small : null;
          break;
        }
        case TdApi.MessageSenderUser.CONSTRUCTOR: {
          TdApi.User user = tdlib.cache().user(((TdApi.MessageSenderUser) message.senderId).userId);
          accentColor = tdlib.cache().userAccentColor(user);
          letters = tdlib.cache().userLetters(user);
          avatar = user != null && user.profilePhoto != null ? user.profilePhoto.small : null;
          break;
        }
        default:
          throw new IllegalArgumentException(message.senderId.toString());
      }
      return newBitmap(data, accentColor, letters, avatar);
    }

    private Drawable liveBackground;

    private static void drawAvatar (Canvas c, Bitmap bitmap) {
      BitmapShader shader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
      Matrix matrix = new Matrix();
      float scale = Screen.dp(52) / (float) bitmap.getWidth();
      matrix.postTranslate(Screen.dp(5), Screen.dp(5));
      matrix.postScale(scale, scale);
      Paint roundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
      roundPaint.setShader(shader);
      shader.setLocalMatrix(matrix);
      RectF rect = Paints.getRectF();
      rect.set(Screen.dp(5), Screen.dp(5), Screen.dp(52 + 5), Screen.dp(52 + 5));
      c.drawRoundRect(rect, Screen.dp(26), Screen.dp(26), roundPaint);
    }

    private void drawAvatar (final MarkerData data, Canvas c, TdlibAccentColor accentColor, Letters letters, TdApi.File avatar) {
      int cx = Screen.dp(62) / 2;
      int cy = Screen.dp(62f) / 2;
      int radius = Screen.dp(26f);

      final ImageFile imageFile;
      if (avatar != null) {
        imageFile = new ImageFile(tdlib, avatar);
        imageFile.setSwOnly(true);
        imageFile.setSize(ChatView.getDefaultAvatarCacheSize());
        synchronized (ImageCache.getReferenceCounters()) {
          Bitmap avatarBitmap = ImageCache.instance().getBitmap(imageFile);
          if (U.isValidBitmap(avatarBitmap)) {
            drawAvatar(c, avatarBitmap);
            return;
          }
        }
      } else {
        imageFile = null;
      }

      c.drawCircle(cx, cy, radius, Paints.fillingPaint(accentColor.getPrimaryColor()));
      Paint paint = Paints.getMediumTextPaint(19f, accentColor.getPrimaryContentColor(), letters.needFakeBold);
      float textWidth = Paints.measureLetters(letters, 19f);
      c.drawText(letters.text, cx - textWidth / 2, cy + Screen.dp(6.5f), paint);

      data.requestFile(imageFile);
    }

    private @Nullable Bitmap newBitmap (MarkerData data, TdlibAccentColor accentColor, Letters letters, TdApi.File avatar) {
      Bitmap result = null;
      boolean success = false;
      try {
        if (liveBackground == null) {
          liveBackground = UI.getResources().getDrawable(R.drawable.bg_livepin);
          liveBackground.setBounds(0, 0, Screen.dp(62f), Screen.dp(76f));
        }
        result = Bitmap.createBitmap(Screen.dp(62), Screen.dp(76), Bitmap.Config.ARGB_8888);
        result.eraseColor(0);
        Canvas c = new Canvas(result);
        liveBackground.draw(c);

        data.canvas = c;
        data.bitmap = result;

        drawAvatar(data, c, accentColor, letters, avatar);
        success = true;
      } catch (Throwable t) {
        Log.w(t);
      }
      if (!success && result != null) {
        try {
          result.recycle();
        } catch (Throwable ignored) { }
        result = null;
      }
      return result;
    }

    public void setPosition (LocationPoint<MarkerData> point) {
      marker.setPosition(new GeoPoint(point.latitude, point.longitude));
      setActive(point.message == null || ((TdApi.MessageLiveLocation) point.message.content).expiresIn > 0);
      mapView.invalidate();
    }

    private boolean isActive = true;

    public void setActive (boolean isActive) {
      if (this.isActive != isActive) {
        this.isActive = isActive;
        marker.setAlpha(isActive ? 1f : FINISHED_BROADCAST_ALPHA);
        mapView.invalidate();
      }
    }

    /** Focused markers are drawn last, so they sit on top. */
    public void bringToFront () {
      mapView.getOverlays().remove(marker);
      mapView.getOverlays().add(marker);
      mapView.invalidate();
    }

    public void remove () {
      mapView.getOverlays().remove(marker);
      mapView.invalidate();
    }

    @Override
    public void performDestroy () {
      requestFile(null);
    }

    private ImageFile requestedFile;

    public void requestFile (ImageFile imageFile) {
      if (this.requestedFile == null && imageFile == null) {
        return;
      }
      if (this.requestedFile != null && imageFile != null && this.requestedFile.accountId() == imageFile.accountId() && this.requestedFile.getId() == imageFile.getId()) {
        return;
      }
      if (this.requestedFile != null) {
        ImageLoader.instance().removeWatcher(reference);
      }
      this.requestedFile = imageFile;
      if (imageFile != null) {
        ImageLoader.instance().requestFile(imageFile, reference);
      }
    }

    private boolean isRequested (ImageFile file) {
      return this.requestedFile != null && this.requestedFile.getId() == file.getId() && this.requestedFile.accountId() == file.accountId();
    }

    @Override
    public void imageLoaded (final ImageFile file, boolean successful, Bitmap bitmap) {
      if (successful && isRequested(file) && canvas != null && U.isValidBitmap(bitmap)) {
        drawAvatar(canvas, bitmap);
        UI.post(() -> {
          if (isRequested(file)) {
            marker.setIcon(new BitmapDrawable(mapView.getResources(), this.bitmap));
            mapView.invalidate();
          }
        });
      }
    }

    @Override
    public void imageProgress (ImageFile file, float progress) { }
  }

  private final Marker.OnMarkerClickListener markerClickListener = (marker, mapView) -> {
    @SuppressWarnings("unchecked")
    LocationPoint<MarkerData> point = (LocationPoint<MarkerData>) marker.getRelatedObject();
    onMarkerClick(point);
    return true;
  };

  private MarkerData newMarker (LocationPoint<MarkerData> point) {
    return new MarkerData(tdlib, mapView(), point, markerClickListener);
  }

  @Override
  protected boolean needBackgroundMapInitialization (@NonNull MapView mapView) {
    return false;
  }

  @Override
  protected void initializeMap (@NonNull MapView mapView, boolean inBackground) {
    if (inBackground || mapReady || isDestroyed()) {
      return;
    }
    mapReady = true;
    OsmMaps.applyMapType(mapView, mapType());
    if (context.permissions().canAccessLocation()) {
      displayMyLocation(mapView);
    }

    List<LocationPoint<MarkerData>> points = pointsOfInterest();
    for (LocationPoint<MarkerData> point : points) {
      if (point.data != null) {
        point.data.setPosition(point);
      } else {
        point.data = newMarker(point);
      }
    }
    if (isSharingLiveLocation()) {
      LocationPoint<MarkerData> point = myLocation(false);
      if (point != null) {
        if (point.data != null) {
          point.data.setPosition(point);
        } else {
          point.data = newMarker(point);
        }
      }
    }
    // The first frame needs the view's size: fit the camera once it's laid out.
    final boolean onlyFocus = getArgumentsStrict().mode == MODE_DROPPED_PIN;
    mapView.addOnFirstLayoutListener((v, left, top, right, bottom) -> {
      moveCamera(mapView, null, false, onlyFocus, false);
      executeScheduledAnimation();
    });
    if (mapView.getWidth() > 0 && mapView.getHeight() > 0) {
      moveCamera(mapView, null, false, onlyFocus, false);
      executeScheduledAnimation();
    }
  }

  @Override
  protected void resumeMap (@NonNull MapView mapView) {
    try { mapView.onResume(); } catch (Throwable ignored) { }
    if (myLocationOverlay != null && context.permissions().canAccessLocation()) {
      try { myLocationOverlay.enableMyLocation(); } catch (Throwable ignored) { }
    }
  }

  @Override
  protected void pauseMap (@NonNull MapView mapView) {
    try { mapView.onPause(); } catch (Throwable ignored) { }
    if (myLocationOverlay != null) {
      myLocationOverlay.disableMyLocation();
    }
  }

  @Override
  protected void destroyMap (@NonNull MapView mapView) {
    pauseMap(mapView);
    try { mapView.onDetach(); } catch (Throwable ignored) { }
  }

  @Override
  protected boolean onBuildDirectionTo (@NonNull MapView mapView, double latitude, double longitude) {
    return false;
  }

  @Override
  protected boolean displayMyLocation (@NonNull MapView mapView) {
    if (!mapReady) {
      return false;
    }
    if (myLocationOverlay == null) {
      myLocationOverlay = new MyLocationNewOverlay(OsmMaps.newLocationProvider(context), mapView) {
        @Override
        public void onLocationChanged (Location location, IMyLocationProvider source) {
          super.onLocationChanged(location, source);
          if (location != null) {
            final Location copy = new Location(location);
            UI.post(() -> onMyLocationChange(copy));
          }
        }
      };
      myLocationOverlay.setDrawAccuracyEnabled(true);
      mapView.getOverlays().add(0, myLocationOverlay);
    }
    try {
      return myLocationOverlay.enableMyLocation();
    } catch (Throwable t) {
      Log.w("Unable to start location updates", t);
      return false;
    }
  }

  private void onMyLocationChange (Location location) {
    if (isDestroyed()) {
      return;
    }
    Settings.instance().saveLastKnownLocation(location.getLatitude(), location.getLongitude(), location.getAccuracy());
    setMyLocation(location);
  }

  @Override
  protected void onApplyMapType (int oldType, int newType) {
    if (mapReady) {
      OsmMaps.applyMapType(mapView(), newType);
    }
  }

  @Override
  protected void onPointOfInterestFocusStateChanged (LocationPoint<MarkerData> point, boolean isFocused) {
    if (point.data != null && isFocused) {
      point.data.bringToFront();
    }
  }

  @Override
  protected void onPointOfInterestAdded (LocationPoint<MarkerData> point, int toIndex) {
    if (mapReady) {
      if (point.data != null) {
        point.data.setPosition(point);
      } else {
        point.data = newMarker(point);
      }
    }
  }

  @Override
  protected void onPointOfInterestRemoved (LocationPoint<MarkerData> point, int fromIndex) {
    if (point.data != null) {
      point.data.remove();
      point.data = null;
    }
  }

  @Override
  protected void onPointOfInterestCoordinatesChanged (LocationPoint<MarkerData> point, int index) {
    if (point.data != null) {
      point.data.setPosition(point);
    }
  }

  @Override
  protected void onPointOfInterestActiveStateMightChanged (LocationPoint<MarkerData> point, boolean isActive) {
    if (point.data != null) {
      point.data.setActive(isActive);
    }
  }

  /** Telegram X's camera: one point centred, several fitted with a margin of at least ~111 m around them. */
  private void moveCamera (MapView mapView, @Nullable LocationPoint<MarkerData> specificPoint, boolean needBearing, boolean onlyFocus, boolean animated) {
    LocationPoint<MarkerData> singlePoint = specificPoint;
    List<LocationPoint<MarkerData>> pointOfInterests = pointsOfInterest();
    LocationPoint<MarkerData> myLocation = myLocation(true);

    if (singlePoint == null) {
      int totalCount = pointOfInterests.size();
      if (myLocation != null) {
        totalCount++;
      }
      if (totalCount == 1) {
        singlePoint = myLocation != null ? myLocation : pointOfInterests.get(0);
      }
    }

    if (singlePoint != null) {
      GeoPoint target = new GeoPoint(singlePoint.latitude, singlePoint.longitude);
      double zoom = OsmMaps.DEFAULT_ZOOM;
      if (specificPoint != null) {
        zoom = Math.max(mapView.getZoomLevelDouble(), OsmMaps.CLICK_ZOOM);
      }
      mapView.setMapOrientation(needBearing ? -singlePoint.bearing : 0f);
      if (animated) {
        mapView.getController().animateTo(target, zoom, 400L);
      } else {
        mapView.getController().setZoom(zoom);
        mapView.getController().setCenter(target);
      }
      return;
    }

    List<GeoPoint> points = new ArrayList<>();
    if (myLocation != null) {
      points.add(new GeoPoint(myLocation.latitude, myLocation.longitude));
    }
    if (onlyFocus) {
      if (hasFocusPoint()) {
        LocationPoint<MarkerData> point = pointOfInterests.get(0);
        points.add(new GeoPoint(point.latitude, point.longitude));
      }
    } else {
      for (LocationPoint<MarkerData> point : pointOfInterests) {
        points.add(new GeoPoint(point.latitude, point.longitude));
      }
    }
    if (points.isEmpty()) {
      return;
    }
    BoundingBox box = BoundingBox.fromGeoPointsSafe(points);
    GeoPoint center = box.getCenterWithDateLine();
    int bound = 111;
    points.add(move(center, bound, bound));
    points.add(move(center, -bound, -bound));
    box = BoundingBox.fromGeoPointsSafe(points);
    mapView.setMapOrientation(0f);
    mapView.zoomToBoundingBox(box, animated, Screen.dp(82f));
  }

  private static final double EARTH_RADIUS = 6366198;

  /** A point toNorth metres north and toEast metres east of start. */
  private static GeoPoint move (GeoPoint start, double toNorth, double toEast) {
    double lonDiff = Math.toDegrees(toEast / (Math.cos(Math.toRadians(start.getLatitude())) * EARTH_RADIUS));
    double latDiff = Math.toDegrees(toNorth / EARTH_RADIUS);
    return new GeoPoint(start.getLatitude() + latDiff, start.getLongitude() + lonDiff);
  }

  @Override
  protected boolean onPositionRequested (@NonNull MapView mapView, @Nullable LocationPoint<MarkerData> point, boolean animated, boolean needBearing, boolean onlyFocus) {
    if (mapReady) {
      moveCamera(mapView, point, needBearing, onlyFocus, animated);
      return animated;
    }
    return false;
  }

  private void onMarkerClick (@Nullable LocationPoint<MarkerData> point) {
    if (point == null) {
      return;
    }
    long chatId = 0;
    long messageId = 0;
    if (point.message != null) {
      chatId = point.message.chatId;
      messageId = point.message.id;
    } else if (point.isSelfLocation) {
      chatId = getArgumentsStrict().chatId;
      TdApi.Message outputMessage = tdlib.cache().findOutputLiveLocationMessage(chatId);
      if (outputMessage != null) {
        messageId = outputMessage.id;
      }
    }
    if (chatId != 0 && messageId != 0) {
      tdlib.ui().openChat(this, chatId, new TdlibUi.ChatOpenParameters().highlightMessage(new MessageId(chatId, messageId)).ensureHighlightAvailable());
    }
  }

  @Override
  protected boolean wouldRememberMapType (int newMapType) {
    return true;
  }

  /** OpenStreetMap, the same inverted for night, and OpenTopoMap terrain. No free worldwide satellite layer. */
  @Override
  protected int[] getAvailableMapTypes () {
    return new int[] {
      Settings.MAP_TYPE_DEFAULT,
      Settings.MAP_TYPE_DARK,
      Settings.MAP_TYPE_TERRAIN
    };
  }

  @Override
  protected boolean onStartPeriodicBearingUpdates (@NonNull MapView mapView) {
    return false;
  }

  @Override
  protected void onFinishPeriodicBearingUpdates (@NonNull MapView mapView) { }
}
