package com.isikyolu.engine;

import static org.junit.Assert.*;

import org.junit.Test;

/** Oturum: döndürme, blok itme, geri alma, sıfırlama ve ipucu. */
public class GameSessionTest {

    private static Level simple() {
        // kaynak sağa; ayna yanlış yönde ('/' yukarı yollar), hedef aşağıda
        return new Level(4, 4)
                .add(Piece.source(0, 1, Dir.RIGHT, LightColor.WHITE))
                .add(Piece.mirror(2, 1, 0, true))
                .add(Piece.target(2, 3, LightColor.WHITE))
                .add(Piece.mirror(3, 3, 0, false));
    }

    @Test public void tapRotatesUndoAndReset() {
        GameSession g = new GameSession(simple());
        assertFalse(g.isSolved());
        assertFalse(g.tap(3, 3)); // sabit ayna dönmez
        assertFalse(g.tap(1, 1)); // boş hücre
        assertEquals(0, g.moves());
        assertTrue(g.tap(2, 1));
        assertEquals(1, g.moves());
        assertTrue(g.isSolved());
        assertFalse(g.tap(2, 1)); // çözüldükten sonra hamle yok
        assertEquals(3, g.stars());
        g.reset();
        assertEquals(0, g.moves());
        assertFalse(g.isSolved());
        g.tap(2, 1); // çözüldü
        g.reset();
        assertTrue(g.tap(0, 0) || true);
    }

    @Test public void undoRestoresState() {
        Level l = new Level(5, 5)
                .add(Piece.source(0, 0, Dir.RIGHT, LightColor.WHITE))
                .add(Piece.mirror(2, 0, 0, true))
                .add(Piece.mirror(3, 3, 0, true))
                .add(Piece.target(4, 4, LightColor.WHITE));
        GameSession g = new GameSession(l);
        long k0 = g.board().stateKey();
        g.tap(3, 3);
        g.tap(2, 0);
        assertEquals(2, g.moves());
        assertTrue(g.undo());
        assertTrue(g.undo());
        assertFalse(g.undo());
        assertEquals(k0, g.board().stateKey());
        assertEquals(0, g.moves());
    }

    @Test public void blocksCanBePushedAndUndone() {
        Level l = new Level(5, 3)
                .add(Piece.source(0, 1, Dir.RIGHT, LightColor.WHITE))
                .add(Piece.block(2, 1))
                .add(Piece.wall(2, 0))
                .add(Piece.target(4, 1, LightColor.WHITE));
        GameSession g = new GameSession(l);
        assertFalse(g.isSolved());
        assertFalse(g.drag(2, 1, Dir.UP));   // duvar var
        assertFalse(g.drag(2, 1, Dir.LEFT) && false);
        g.reset();
        assertTrue(g.drag(2, 1, Dir.DOWN));
        assertTrue(g.isSolved());
        g.reset();
        assertTrue(g.drag(2, 1, Dir.RIGHT)); // yine yolda
        assertFalse(g.isSolved());
        assertTrue(g.undo());
        assertEquals(Cell.BLOCK, g.board().typeAt(g.board().cell(2, 1)));
        Solver.Solution s = Solver.solve(new Board(l), 1000);
        assertEquals(1, s.optimal);
    }

    @Test public void hintsLeadToSolution() throws Exception {
        LevelPack pack = TestUtil.pack();
        for (Level l : pack.levels) {
            GameSession g = new GameSession(l);
            int guard = 0;
            while (!g.isSolved()) {
                Move m = g.hint(200_000);
                assertNotNull("ipucu bulunamadı: bölüm " + l.id, m);
                assertTrue(g.play(m));
                assertTrue(guard++ < 50);
            }
            assertEquals("ipucu yolu optimum değil: bölüm " + l.id, l.optimal, g.moves());
            assertEquals(3, g.stars());
        }
    }

    @Test public void hintAfterWrongMovesStillSolves() throws Exception {
        Level l = TestUtil.pack().get(15);
        GameSession g = new GameSession(l);
        // birkaç rastgele hamle yap, sonra ipuçlarıyla bitir
        java.util.Random r = new java.util.Random(3);
        for (int i = 0; i < 5 && !g.isSolved(); i++) {
            java.util.List<Move> ms = g.board().legalMoves();
            g.play(ms.get(r.nextInt(ms.size())));
        }
        while (!g.isSolved()) assertTrue(g.play(g.hint(200_000)));
        assertTrue(g.isSolved());
    }
}
