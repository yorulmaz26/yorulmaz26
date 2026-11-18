package com.isikyolu.engine;

import static org.junit.Assert.*;

import org.junit.Test;

/** Test 6: ışıkla açılan kapı ve anahtar mekaniği. */
public class DoorKeyTest {

    private static Level base(boolean keyBeamOn) {
        // kaynak A aynadan yansıyıp anahtardan geçer; kaynak B kapıdan geçip hedefe gider
        return new Level(5, 4)
                .add(Piece.source(1, 3, Dir.UP, LightColor.WHITE))
                .add(Piece.mirror(1, 0, keyBeamOn ? 0 : 1, true))
                .add(Piece.key(2, 0, 0))
                .add(Piece.target(4, 0, LightColor.WHITE))
                .add(Piece.source(0, 2, Dir.RIGHT, LightColor.WHITE))
                .add(Piece.door(2, 2, 0))
                .add(Piece.target(4, 2, LightColor.WHITE));
    }

    @Test public void litKeyOpensDoor() {
        BeamTracer.Result r = TestUtil.trace(base(true));
        assertTrue(r.keyLit[0]);
        assertTrue(r.doorOpen[0]);
        assertTrue(r.solved);
    }

    @Test public void darkKeyKeepsDoorClosed() {
        BeamTracer.Result r = TestUtil.trace(base(false));
        assertFalse(r.keyLit[0]);
        assertFalse(r.doorOpen[0]);
        assertFalse(r.solved);
        assertEquals(0, r.received[2 * 5 + 4]);
    }

    @Test public void doorCannotOpenItself() {
        // anahtar yalnızca kapının arkasında: en küçük sabit nokta kapıyı kapalı tutar
        Level l = new Level(5, 1)
                .add(Piece.source(0, 0, Dir.RIGHT, LightColor.WHITE))
                .add(Piece.door(1, 0, 0))
                .add(Piece.key(2, 0, 0))
                .add(Piece.target(4, 0, LightColor.WHITE));
        BeamTracer.Result r = TestUtil.trace(l);
        assertFalse(r.doorOpen[0]);
        assertFalse(r.solved);
    }

    @Test public void chainedDoorsOpenInOrder() {
        // kapı 0 açılınca ışık anahtar 1'e ulaşır, o da kapı 1'i açar
        Level l = new Level(6, 3)
                .add(Piece.source(0, 0, Dir.RIGHT, LightColor.WHITE))
                .add(Piece.key(1, 0, 0))
                .add(Piece.wall(5, 0))
                .add(Piece.source(0, 1, Dir.RIGHT, LightColor.WHITE))
                .add(Piece.door(1, 1, 0))
                .add(Piece.key(3, 1, 1))
                .add(Piece.wall(5, 1))
                .add(Piece.source(0, 2, Dir.RIGHT, LightColor.WHITE))
                .add(Piece.door(2, 2, 1))
                .add(Piece.target(5, 2, LightColor.WHITE));
        BeamTracer.Result r = TestUtil.trace(l);
        assertTrue(r.doorOpen[0]);
        assertTrue(r.doorOpen[1]);
        assertTrue(r.solved);
    }

    @Test public void solverHandlesDoorLevels() {
        Level l = base(false);
        Solver.Solution s = Solver.solve(new Board(l), 1000);
        assertTrue(s.solvable);
        assertEquals(1, s.optimal);
    }
}
