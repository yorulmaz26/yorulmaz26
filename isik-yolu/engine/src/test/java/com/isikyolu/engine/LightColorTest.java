package com.isikyolu.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

/** Renk kodları (çeviri anahtarı {@code color.<kod>}) değişmez ve her renk için farklıdır. */
public class LightColorTest {
    @Test public void idsAreStableAndDistinct() {
        int[] all = {LightColor.RED, LightColor.GREEN, LightColor.BLUE, LightColor.YELLOW, LightColor.MAGENTA, LightColor.CYAN, LightColor.WHITE, 0};
        String[] ids = {"red", "green", "blue", "yellow", "magenta", "cyan", "white", "dark"};
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < all.length; i++) {
            assertEquals(ids[i], LightColor.id(all[i]));
            assertTrue(seen.add(LightColor.id(all[i])));
        }
    }
}
