// SPDX-License-Identifier: Apache-2.0
package org.grapefruit.platform;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Null means factory defaults; the empty string deliberately means no permanent tiles. */
public final class TileOrder {
    public static final String KEY = "grapefruit_qs_tiles_v1";
    public static final String[] IDS = {"user","brightness","settings","wifi","cellular","rotation","battery","airplane","bluetooth"};
    public static final String[] LABELS = {"User","Brightness","Settings","Wi-Fi","Mobile network","Rotation lock","Battery","Airplane mode","Bluetooth"};
    private TileOrder() {}
    public static List<String> parse(String saved) {
        List<String> result = new ArrayList<String>();
        for (String id : (saved == null ? join(Arrays.asList(IDS)) : saved).split(",")) {
            if (Arrays.asList(IDS).contains(id) && !result.contains(id)) result.add(id);
        }
        return result;
    }
    public static String join(List<String> ids) {
        StringBuilder result = new StringBuilder();
        for (String id : ids) { if (result.length() > 0) result.append(','); result.append(id); }
        return result.toString();
    }
    public static String label(String id) { return LABELS[Arrays.asList(IDS).indexOf(id)]; }
}
