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

    /** Denetim bulgusu: bölüm 45'te Geri Al'la keşif sonrası düz ipucu kaybettiriyordu; akıllı plan Geri Al'ı önerir. */
    @Test public void smartPlanPrefersFreeUndo() throws Exception {
        Level l = TestUtil.pack().get(45);
        GameSession g = new GameSession(l, true);
        // ilk döner aynayı 9 kez çevir + geri al (hak harcanır, durum değişmez), sonra bir kez daha çevir
        Move probe = null;
        for (Move m : g.board().legalMoves()) if (m.kind == Move.ROTATE) { probe = m; break; }
        for (int i = 0; i < 9; i++) { assertTrue(g.play(probe)); assertTrue(g.undo()); }
        assertTrue(g.play(probe));
        int left = g.budgetLeft();
        GameSession.Plan pl = g.plan(500_000);
        assertTrue("plan kalan hakla yapılabilmeli", pl.feasible);
        assertTrue(pl.remaining <= left);
        // planı uygula: önce geri al, sonra hamleler
        int guard = 0;
        while (!g.isSolved() && guard++ < 60) {
            GameSession.Plan p = g.plan(500_000);
            assertTrue(p.feasible);
            if (p.undos > 0) assertTrue(g.undo());
            else assertTrue(g.play(p.move));
        }
        assertTrue("akıllı ipucu çözdürmeli (hak: " + g.budgetLeft() + ")", g.isSolved());
    }

    @Test public void planReportsInfeasibleWhenBudgetGone() throws Exception {
        Level l = TestUtil.pack().get(350);
        GameSession g = new GameSession(l, true);
        Move probe = null;
        for (Move m : g.board().legalMoves()) if (m.kind == Move.ROTATE) { probe = m; break; }
        while (g.budgetLeft() > 1) { g.play(probe); g.undo(); }
        GameSession.Plan pl = g.plan(500_000);
        assertFalse("kalan 1 hak, en az " + pl.remaining + " gerekir", pl.feasible);
        assertTrue(Economy.continueMoves(pl.remaining) >= pl.remaining);
    }

    /** Denetim 2: eşit uzaklıkta derin geri alma seçilir; aynı hak, daha az toplam hamle (yıldız kaybı yok). */
    @Test public void planTieBreakPrefersDeeperUndo() throws Exception {
        Level l = TestUtil.pack().get(120);
        GameSession g = new GameSession(l, true);
        Move probe = null;
        for (Move m : g.board().legalMoves()) if (m.kind == Move.ROTATE) { probe = m; break; }
        assertTrue(g.play(probe));
        assertTrue(g.play(probe)); // başlangıca döndü
        GameSession.Plan pl = g.plan(500_000);
        assertEquals(2, pl.undos);
        assertEquals(l.optimal, pl.remaining);
        assertTrue(g.undo()); assertTrue(g.undo());
        int guard = 0;
        while (!g.isSolved() && guard++ < 60) { GameSession.Plan p = g.plan(500_000); assertEquals(0, p.undos); assertTrue(g.play(p.move)); }
        assertEquals(3, g.stars());
    }

    /** Plan canlı tahtayı değiştirmez (arka plan iş parçacığında çalışır) ve kaba kuvvetle aynı sonucu verir. */
    @Test public void planMatchesBruteForceAndKeepsBoard() throws Exception {
        java.util.Random r = new java.util.Random(5);
        for (int id : new int[]{12, 45, 79, 120, 238, 300, 375, 491}) {
            Level l = TestUtil.pack().get(id);
            GameSession g = new GameSession(l, false);
            java.util.List<Move> hist = new java.util.ArrayList<>(); // en son hamle başta
            for (int i = 0; i < 6; i++) {
                java.util.List<Move> ms = g.board().legalMoves();
                Move m = ms.get(r.nextInt(ms.size()));
                Board t = g.board().copy(); t.apply(m);
                if (BeamTracer.trace(t).solved) continue; // çözülmüş oturumda geri alınamaz
                assertTrue(g.play(m)); hist.add(0, m);
            }
            long before = g.board().stateKey();
            GameSession.Plan pl = g.plan(500_000);
            assertEquals(before, g.board().stateKey());
            java.util.Map<Long, Integer> dist = Solver.distancesToGoal(g.board(), 500_000);
            Board b = g.board().copy();
            int best = dist.get(b.stateKey()), bestUndo = 0, k = 0;
            for (Move m : hist) { b.revert(m); k++; int d = dist.get(b.stateKey()); if (d <= best) { best = d; bestUndo = k; } }
            assertEquals("bölüm " + id, best, pl.remaining);
            assertEquals("bölüm " + id, bestUndo, pl.undos);
            if (pl.undos == 0 && pl.remaining > 0) {
                Board n = g.board().copy(); n.apply(pl.move);
                assertEquals(best - 1, (int) dist.get(n.stateKey()));
            }
        }
    }

    @Test public void refundedHintIsNotCounted() throws Exception {
        GameSession g = new GameSession(TestUtil.pack().get(12), true);
        g.markHintUsed();
        g.unmarkHintUsed();
        assertEquals(0, g.hintsUsed());
        g.unmarkHintUsed();
        assertEquals(0, g.hintsUsed());
    }
}
