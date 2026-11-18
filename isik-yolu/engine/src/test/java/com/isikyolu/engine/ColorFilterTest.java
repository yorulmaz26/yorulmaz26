package com.isikyolu.engine;

import static org.junit.Assert.*;

import org.junit.Test;

/** Test 4: renkli ışıklar, karışım ve filtreler. */
public class ColorFilterTest {

    private static Level line(int srcColor, int filter, int targetColor) {
        Level l = new Level(5, 1).add(Piece.source(0, 0, Dir.RIGHT, srcColor)).add(Piece.target(4, 0, targetColor));
        if (filter != 0) l.add(Piece.filter(2, 0, filter));
        return l;
    }

    @Test public void whiteThroughFilterBecomesFilterColor() {
        for (int f : new int[]{1, 2, 3, 4, 5, 6}) {
            assertTrue(TestUtil.trace(line(LightColor.WHITE, f, f)).solved);
            assertFalse(TestUtil.trace(line(LightColor.WHITE, f, LightColor.WHITE)).solved);
        }
    }

    @Test public void filterBlocksOtherColors() {
        BeamTracer.Result r = TestUtil.trace(line(LightColor.RED, LightColor.BLUE, LightColor.RED));
        assertFalse(r.solved);
        assertEquals(0, r.received[4]);
        // sarı filtre kırmızıyı geçirir
        assertTrue(TestUtil.trace(line(LightColor.RED, LightColor.YELLOW, LightColor.RED)).solved);
        // camgöbeği filtre beyazı yeşil+maviye indirger
        assertTrue(TestUtil.trace(line(LightColor.WHITE, LightColor.CYAN, LightColor.CYAN)).solved);
    }

    @Test public void targetNeedsExactColor() {
        assertFalse(TestUtil.trace(line(LightColor.WHITE, 0, LightColor.RED)).solved);
        assertFalse(TestUtil.trace(line(LightColor.RED, 0, LightColor.GREEN)).solved);
        assertTrue(TestUtil.trace(line(LightColor.BLUE, 0, LightColor.BLUE)).solved);
    }

    @Test public void beamsMergeAtTarget() {
        // kırmızı soldan, yeşil yukarıdan: sarı hedef
        Level l = new Level(3, 3)
                .add(Piece.source(0, 1, Dir.RIGHT, LightColor.RED))
                .add(Piece.source(1, 0, Dir.DOWN, LightColor.GREEN))
                .add(Piece.target(1, 1, LightColor.YELLOW));
        assertTrue(TestUtil.trace(l).solved);
        Level only = new Level(3, 3).add(Piece.source(0, 1, Dir.RIGHT, LightColor.RED)).add(Piece.target(1, 1, LightColor.YELLOW));
        assertFalse(TestUtil.trace(only).solved);
    }

    @Test public void crossingBeamsDoNotInterfere() {
        Level l = new Level(3, 3)
                .add(Piece.source(0, 1, Dir.RIGHT, LightColor.RED))
                .add(Piece.target(2, 1, LightColor.RED))
                .add(Piece.source(1, 0, Dir.DOWN, LightColor.BLUE))
                .add(Piece.target(1, 2, LightColor.BLUE));
        BeamTracer.Result r = TestUtil.trace(l);
        assertTrue(r.solved);
        assertEquals(LightColor.MAGENTA, r.lit[4]);
    }
}
