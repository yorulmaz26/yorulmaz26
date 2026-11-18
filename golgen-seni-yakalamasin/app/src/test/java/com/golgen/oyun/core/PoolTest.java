package com.golgen.oyun.core;

import static org.junit.Assert.*;
import org.junit.Test;

public class PoolTest {
    @Test public void reusesObjectsWithoutAllocation() {
        Pool<Diamond> p = new Pool<>(3, Diamond::new);
        Diamond a = p.obtain(), b = p.obtain(), c = p.obtain();
        assertNull("kapasite sınırı", p.obtain());
        p.free(0);
        assertEquals(2, p.size());
        Diamond again = p.obtain();
        assertTrue(again == a || again == b || again == c);
        p.clear();
        assertEquals(0, p.size());
    }
}
