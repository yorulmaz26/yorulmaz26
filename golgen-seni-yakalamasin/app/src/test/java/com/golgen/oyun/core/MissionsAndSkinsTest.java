package com.golgen.oyun.core;

import static org.junit.Assert.*;
import org.junit.BeforeClass;
import org.junit.Test;

public class MissionsAndSkinsTest {
    @BeforeClass public static void setUp() { L10nTestSupport.load("tr"); }

    @Test public void atLeastEightSkinsWithIncreasingPrices() {
        assertTrue(Skins.ALL.length >= 8);
        assertEquals(0, Skins.ALL[0].price);
        for (int i = 1; i < Skins.ALL.length; i++) {
            assertEquals(i, Skins.ALL[i].id);
            assertTrue(Skins.ALL[i].price > Skins.ALL[i - 1].price);
        }
    }

    @Test public void achievementTablesConsistent() {
        assertEquals(Achievements.REWARDS.length, Achievements.COUNT);
        assertTrue(Achievements.COUNT <= 31);
        java.util.HashSet<String> titles = new java.util.HashSet<>();
        for (int i = 0; i < Achievements.COUNT; i++) assertTrue(Achievements.title(i), titles.add(Achievements.title(i)));
    }

    @Test public void skinNamesDistinct() {
        java.util.HashSet<String> names = new java.util.HashSet<>();
        for (SkinDef s : Skins.ALL) assertTrue(s.name(), names.add(s.name()));
        assertEquals("Işık Kare", Skins.ALL[0].name());
    }
}
