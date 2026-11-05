// SPDX-License-Identifier: GPL-3.0-or-later
package com.grapefruitmaps;

/** Kept separate so theme gating and exact unlock count can be verified without graphics. */
final class EggState {
    private int taps;
    boolean tap(int theme) {
        if (theme != 3) { taps = 0; return false; }
        if (++taps == 5) { taps = 0; return true; }
        return false;
    }
    void reset() { taps = 0; }
}
