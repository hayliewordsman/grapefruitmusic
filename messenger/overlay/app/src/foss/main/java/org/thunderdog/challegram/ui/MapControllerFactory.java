/*
 * This file is a part of Grapefruitgram Messenger, based on Telegram X.
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * The FOSS build's maps: OpenStreetMap through osmdroid, instead of Google Maps.
 */
package org.thunderdog.challegram.ui;

import android.content.Context;

import org.thunderdog.challegram.telegram.Tdlib;

public final class MapControllerFactory {
  public static MapController<?,?> newMapController (Context context, Tdlib tdlib) {
    return new MapOsmController(context, tdlib);
  }
}
