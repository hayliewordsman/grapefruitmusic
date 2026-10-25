package com.grapefruitmusic.util;

import java.util.List;

/** Pure-Java list helpers (unit tested). */
public final class Lists {
    private Lists() {
    }

    /** Moves the element at from so that it ends up at index to. */
    public static <T> void move(List<T> list, int from, int to) {
        if (from == to || from < 0 || to < 0 || from >= list.size() || to >= list.size()) return;
        T item = list.remove(from);
        list.add(to, item);
    }
}
