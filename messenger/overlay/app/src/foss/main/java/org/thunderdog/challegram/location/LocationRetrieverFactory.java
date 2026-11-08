/*
 * This file is a part of Grapefruitgram Messenger, based on Telegram X.
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * The FOSS build's location: Android's LocationManager, instead of Play services' fused provider.
 */
package org.thunderdog.challegram.location;

import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.unsorted.LocationRetriever;

public class LocationRetrieverFactory {
  public static LocationRetriever newRetriever (BaseActivity context) {
    return new AndroidLocationRetriever(context);
  }
}
