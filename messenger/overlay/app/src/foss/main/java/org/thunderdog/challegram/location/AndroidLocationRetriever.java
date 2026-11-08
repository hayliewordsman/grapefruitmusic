/*
 * This file is a part of Grapefruitgram Messenger, based on Telegram X.
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * One location fix from Android's own LocationManager (satellites and network), with the same
 * timing as Telegram X's Play services retriever: ask for a fresh fix, and after a few seconds fall
 * back to the newest location the phone already knows.
 */
package org.thunderdog.challegram.location;

import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;

import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.grapefruit.OsmMaps;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.LocationRetriever;

import me.vkryl.core.lambda.CancellableRunnable;
import me.vkryl.core.lambda.RunnableBool;

public class AndroidLocationRetriever extends LocationRetriever {
  private static final long LOCATION_MAX_WAIT_TIME = 3000L;
  private static final String[] PROVIDERS = {LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER};

  private final LocationManager manager;
  private LocationListener activeListener;

  public AndroidLocationRetriever (BaseActivity context) {
    super(context);
    this.manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
  }

  @Override
  public void performDestroy () {
    stopUpdates();
  }

  /** Location switched off: offer the system setting (Play services showed its own dialog here). */
  @Override
  public void checkPermissions (RunnableBool callback) {
    if (OsmMaps.isLocationEnabled(activity)) {
      callback.runWithBool(true);
      return;
    }
    try {
      activity.startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
    } catch (Throwable t) {
      Log.w("Unable to open location settings", t);
    }
    callback.runWithBool(false);
  }

  @Override
  protected void retrieveLocation () {
    if (manager == null) {
      onLocationFetchFailed();
      return;
    }
    checkPermissions(enabled -> {
      if (enabled) {
        retrieveLocationImpl();
      } else {
        onLocationFetchFailed();
      }
    });
  }

  private void stopUpdates () {
    if (activeListener != null && manager != null) {
      try {
        manager.removeUpdates(activeListener);
      } catch (Throwable t) {
        Log.w("removeUpdates failed", t);
      }
    }
    activeListener = null;
  }

  @SuppressWarnings("MissingPermission")
  private Location lastKnownLocation () {
    Location best = null;
    for (String provider : PROVIDERS) {
      try {
        Location location = manager.getLastKnownLocation(provider);
        if (location != null && (best == null || location.getTime() > best.getTime())) {
          best = location;
        }
      } catch (Throwable ignored) { }
    }
    return best;
  }

  @SuppressWarnings("MissingPermission")
  private void retrieveLocationImpl () {
    stopUpdates();
    final boolean[] sent = new boolean[1];
    final CancellableRunnable[] timeout = new CancellableRunnable[1];
    final LocationListener listener = new LocationListener() {
      @Override
      public void onLocationChanged (Location location) {
        timeout[0].cancel();
        stopUpdates();
        if (!sent[0]) {
          sent[0] = true;
          onLocationRetrieved(location);
        }
      }

      @Override
      public void onStatusChanged (String provider, int status, Bundle extras) { }

      @Override
      public void onProviderEnabled (String provider) { }

      @Override
      public void onProviderDisabled (String provider) { }
    };
    timeout[0] = new CancellableRunnable() {
      @Override
      public void act () {
        if (!sent[0]) {
          sent[0] = true;
          stopUpdates();
          Location location = lastKnownLocation();
          if (location != null) {
            onLocationRetrieved(location);
          } else {
            onLocationFetchFailed();
          }
        }
      }
    };
    activeListener = listener;
    boolean requested = false;
    for (String provider : PROVIDERS) {
      try {
        if (manager.isProviderEnabled(provider)) {
          manager.requestSingleUpdate(provider, listener, Looper.getMainLooper());
          requested = true;
        }
      } catch (SecurityException e) {
        Log.w("No location permission", e);
      } catch (Throwable t) {
        Log.w("requestSingleUpdate failed for %s", t, provider);
      }
    }
    if (requested) {
      UI.post(timeout[0], LOCATION_MAX_WAIT_TIME);
    } else {
      timeout[0].run();
    }
  }
}
