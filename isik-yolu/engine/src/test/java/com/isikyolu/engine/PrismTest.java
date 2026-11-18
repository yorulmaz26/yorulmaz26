package com.isikyolu.engine;

import static org.junit.Assert.*;

import org.junit.Test;

/** Test 5: prizma ışığı ayrıştırır (kırmızı sola, yeşil düz, mavi sağa). */
public class PrismTest {

    @Test public void whiteSplitsIntoThree() {
        for (int d = 0; d < 4; d++) {
            Level l = new Level(7, 7);
            int px = 3, py = 3;
            l.add(Piece.source(px - 2 * Dir.DX[d], py - 2 * Dir.DY[d], d, LightColor.WHITE));
            l.add(Piece.prism(px, py));
            int L = Dir.left(d), R = Dir.right(d);
            l.add(Piece.target(px + 2 * Dir.DX[L], py + 2 * Dir.DY[L], LightColor.RED));
            l.add(Piece.target(px + 2 * Dir.DX[d], py + 2 * Dir.DY[d], LightColor.GREEN));
            l.add(Piece.target(px + 2 * Dir.DX[R], py + 2 * Dir.DY[R], LightColor.BLUE));
            BeamTracer.Result r = TestUtil.trace(l);
            assertTrue("yön " + d, r.solved);
            assertEquals(3, r.satisfied);
        }
    }

    @Test public void singleColorBendsDeterministically() {
        // kırmızı ışık sağa giderken prizmada sola (yukarı) döner
        Level l = new Level(5, 5)
                .add(Piece.source(0, 2, Dir.RIGHT, LightColor.RED))
                .add(Piece.prism(2, 2))
                .add(Piece.target(2, 0, LightColor.RED));
        assertTrue(TestUtil.trace(l).solved);
        // mavi aşağı döner
        Level b = new Level(5, 5)
                .add(Piece.source(0, 2, Dir.RIGHT, LightColor.BLUE))
                .add(Piece.prism(2, 2))
                .add(Piece.target(2, 4, LightColor.BLUE));
        assertTrue(TestUtil.trace(b).solved);
    }

    @Test public void filterBeforePrismSelectsBranches() {
        Level l = new Level(7, 5)
                .add(Piece.source(0, 2, Dir.RIGHT, LightColor.WHITE))
                .add(Piece.filter(1, 2, LightColor.YELLOW))
                .add(Piece.prism(3, 2))
                .add(Piece.target(3, 0, LightColor.RED))
                .add(Piece.target(6, 2, LightColor.GREEN));
        BeamTracer.Result r = TestUtil.trace(l);
        assertTrue(r.solved);
        assertEquals(0, r.lit[4 * 7 + 3]); // mavi kol yok
    }
}
