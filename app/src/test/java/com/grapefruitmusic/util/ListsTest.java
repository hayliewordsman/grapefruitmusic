package com.grapefruitmusic.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ListsTest {
    private static List<String> list(String... items) {
        return new ArrayList<String>(Arrays.asList(items));
    }

    @Test
    public void movesDownAndUp() {
        List<String> l = list("a", "b", "c", "d");
        Lists.move(l, 0, 2);
        assertEquals(list("b", "c", "a", "d"), l);
        Lists.move(l, 3, 0);
        assertEquals(list("d", "b", "c", "a"), l);
    }

    @Test
    public void ignoresOutOfRange() {
        List<String> l = list("a", "b");
        Lists.move(l, 0, 5);
        Lists.move(l, -1, 0);
        Lists.move(l, 1, 1);
        assertEquals(list("a", "b"), l);
    }
}
