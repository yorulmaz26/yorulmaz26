package com.isikyolu.engine;

import static org.junit.Assert.*;

import java.util.Random;
import org.junit.Test;

/** Test 3: ışın yansıma, engeller ve sonsuz döngü koruması. */
public class BeamReflectionTest {

    @Test public void straightBeamHitsTarget() {
        Level l = new Level(5, 1).add(Piece.source(0, 0, Dir.RIGHT, LightColor.WHITE)).add(Piece.target(4, 0, LightColor.WHITE));
        BeamTracer.Result r = TestUtil.trace(l);
        assertTrue(r.solved);
        assertEquals(1, r.segments.size());
        BeamTracer.Segment s = r.segments.get(0);
        assertEquals(1, s.x1); assertEquals(9, s.x2); // merkezden merkeze (yarım hücre birimi)
    }

    @Test public void reflectionTableIsCorrect() {
        // '/' : sağ→yukarı, yukarı→sağ, aşağı→sol, sol→aşağı
        assertEquals(Dir.UP, Dir.reflect(Dir.RIGHT, 0));
        assertEquals(Dir.RIGHT, Dir.reflect(Dir.UP, 0));
        assertEquals(Dir.LEFT, Dir.reflect(Dir.DOWN, 0));
        assertEquals(Dir.DOWN, Dir.reflect(Dir.LEFT, 0));
        // '\' : sağ→aşağı, aşağı→sağ, yukarı→sol, sol→yukarı
        assertEquals(Dir.DOWN, Dir.reflect(Dir.RIGHT, 1));
        assertEquals(Dir.RIGHT, Dir.reflect(Dir.DOWN, 1));
        assertEquals(Dir.LEFT, Dir.reflect(Dir.UP, 1));
        assertEquals(Dir.UP, Dir.reflect(Dir.LEFT, 1));
        for (int d = 0; d < 4; d++) for (int o = 0; o < 2; o++) {
            int nd = Dir.reflect(d, o);
            assertNotEquals(d, nd);
            assertNotEquals(Dir.opposite(d), nd);
            assertEquals(o, Dir.orientFor(d, nd));
            // yansıma tersinirdir
            assertEquals(Dir.opposite(d), Dir.reflect(Dir.opposite(nd), o));
        }
    }

    @Test public void mirrorRoutesBeamInAllDirections() {
        // kaynak merkezde, ayna her yönde; hedef aynadan sonra
        for (int d = 0; d < 4; d++) for (int o = 0; o < 2; o++) {
            Level l = new Level(7, 7);
            l.add(Piece.source(3, 3, d, LightColor.WHITE));
            int mx = 3 + 2 * Dir.DX[d], my = 3 + 2 * Dir.DY[d];
            l.add(Piece.mirror(mx, my, o, true));
            int nd = Dir.reflect(d, o);
            l.add(Piece.target(mx + Dir.DX[nd], my + Dir.DY[nd], LightColor.WHITE));
            assertTrue("yön " + d + " ayna " + o, TestUtil.trace(l).solved);
            // ayna döndürülünce hedef kaçırılır
            Board b = new Board(l);
            b.rotate(b.cell(mx, my));
            assertFalse(BeamTracer.trace(b).solved);
        }
    }

    @Test public void wallsBlocksAndSourcesStopLight() {
        for (int t : new int[]{Cell.WALL, Cell.BLOCK, Cell.SOURCE}) {
            Level l = new Level(5, 1).add(Piece.source(0, 0, Dir.RIGHT, LightColor.WHITE)).add(Piece.target(4, 0, LightColor.WHITE));
            Piece p = new Piece(t, 2, 0);
            p.dir = Dir.LEFT; p.color = LightColor.RED;
            l.add(p);
            BeamTracer.Result r = TestUtil.trace(l);
            if (t == Cell.SOURCE) {
                // ikinci kaynak sola ışık verir ama sağdaki hedefe ulaşan olmaz
                assertFalse(r.solved);
            } else {
                assertFalse(r.solved);
                assertEquals(1, r.segments.size());
                assertEquals(4, r.segments.get(0).x2); // engel hücresinin kenarında durur
            }
        }
    }

    @Test public void beamLeavesGridAtEdge() {
        Level l = new Level(3, 3).add(Piece.source(1, 1, Dir.UP, LightColor.WHITE)).add(Piece.target(0, 0, LightColor.WHITE));
        BeamTracer.Result r = TestUtil.trace(l);
        assertFalse(r.solved);
        assertEquals(0, r.segments.get(0).y2);
    }

    /**
     * Aynalar, prizmalar ve filtreler her renk için yön dönüşümünde birebirdir; bu yüzden
     * kaynaktan çıkan ışın kapalı bir döngüye giremez. Ziyaret tablosu yine de güvenlik ağı
     * olarak tutulur. Bu test rastgele yoğun düzenlerde izlemenin her zaman sonlandığını ve
     * parça sayısının sınırlı kaldığını doğrular.
     */
    @Test public void tracerAlwaysTerminates() {
        Random rnd = new Random(42);
        int loops = 0;
        int[] types = {Cell.MIRROR, Cell.MIRROR, Cell.MIRROR, Cell.PRISM, Cell.EMPTY, Cell.EMPTY};
        for (int n = 0; n < 4000; n++) {
            Level l = new Level(6, 6);
            l.add(Piece.source(0, rnd.nextInt(6), Dir.RIGHT, LightColor.WHITE));
            for (int y = 0; y < 6; y++) for (int x = 1; x < 6; x++) {
                int t = types[rnd.nextInt(types.length)];
                if (t == Cell.MIRROR) l.add(Piece.mirror(x, y, rnd.nextInt(2), true));
                else if (t == Cell.PRISM) l.add(Piece.prism(x, y));
            }
            l.add(Piece.target(0, (l.pieces.get(0).y + 1) % 6, LightColor.WHITE));
            BeamTracer.Result r = TestUtil.trace(l);
            assertTrue(r.segments.size() < 6 * 6 * 4 * 8 + 10);
            if (r.loopDetected) loops++;
        }
        assertEquals("tersinir optikte döngü oluşmamalı", 0, loops);
    }
}
