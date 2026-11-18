package com.isikyolu.engine;

import static org.junit.Assert.*;

import org.junit.Test;

/** Test 8: yıldız hesaplama. */
public class StarRatingTest {
    @Test public void thresholds() {
        assertEquals(3, StarRating.stars(1, 1));
        assertEquals(2, StarRating.stars(2, 1));
        assertEquals(2, StarRating.stars(3, 1));
        assertEquals(1, StarRating.stars(4, 1));
        assertEquals(3, StarRating.stars(6, 6));
        assertEquals(2, StarRating.stars(9, 6));
        assertEquals(1, StarRating.stars(10, 6));
        assertEquals(3, StarRating.stars(5, 6)); // optimumdan az olamaz ama güvenli
        assertEquals(StarRating.twoStarLimit(6), 9);
    }

    @Test public void monotonic() {
        for (int opt = 1; opt <= 15; opt++) {
            int prev = 3;
            for (int m = opt; m < opt * 4; m++) {
                int s = StarRating.stars(m, opt);
                assertTrue(s <= prev && s >= 1);
                prev = s;
            }
        }
    }
}
