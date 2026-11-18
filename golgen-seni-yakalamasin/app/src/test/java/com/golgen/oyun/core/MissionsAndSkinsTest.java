package com.golgen.oyun.core;

import static org.junit.Assert.*;
import org.junit.Test;

public class MissionsAndSkinsTest {
    @Test public void atLeastEightSkinsWithIncreasingPrices() {
        assertTrue(Skins.ALL.length >= 8);
        assertEquals(0, Skins.ALL[0].price);
        for (int i = 1; i < Skins.ALL.length; i++) {
            assertEquals(i, Skins.ALL[i].id);
            assertTrue(Skins.ALL[i].price > Skins.ALL[i - 1].price);
        }
    }

    @Test public void achievementTablesConsistent() {
        assertEquals(Achievements.TITLES.length, Achievements.DESCS.length);
        assertTrue(Achievements.COUNT <= 31);
    }
}
