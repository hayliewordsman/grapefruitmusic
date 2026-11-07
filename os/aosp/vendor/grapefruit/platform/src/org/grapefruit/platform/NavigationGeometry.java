// SPDX-License-Identifier: Apache-2.0
package org.grapefruit.platform;

/** Mirrored integer boundaries keep slot four's center exactly at the display center. */
public final class NavigationGeometry {
    public static final String[] SLOTS = {"screenshot","volume_down","back","home","recent_apps","volume_up","spacer"};
    private NavigationGeometry() {}
    public static int boundary(int width, int index) {
        if (index < 0 || index > 7 || width < 0) throw new IllegalArgumentException();
        return index <= 3 ? Math.round(width * index / 7f) : width - boundary(width, 7-index);
    }
}
