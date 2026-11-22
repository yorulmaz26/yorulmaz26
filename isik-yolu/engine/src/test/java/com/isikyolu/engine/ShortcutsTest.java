package com.isikyolu.engine;

import static org.junit.Assert.*;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Random;

/** Düşünmeden çözme kısayolları ve canlı yem şartı (bölüm denetimi 2–3). */
public class ShortcutsTest {

    /** Her mantık bölümünde kısayol çözmez ve en az bir gerçek canlı yem vardır. */
    @Test public void noLogicLevelFallsToShortcuts() throws Exception {
        for (Level l : TestUtil.pack().levels) {
            if (!Chapters.requiresLogic(l.id)) continue;
            Board b = new Board(l);
            assertFalse("kısayol bölüm " + l.id + "'i bitiriyor", Shortcuts.thoughtless(b));
            assertTrue("bölüm " + l.id + "'de canlı yem yok", Shortcuts.liveDecoys(b, Solver.solve(b, 200_000).moves) >= 1);
        }
    }

    /** Canlı yem sayımı yalnızca çevrilince çözümü bozan aynaları sayar. */
    @Test public void liveDecoysReallyBreakTheSolution() throws Exception {
        BeamTracer tr = new BeamTracer();
        for (int id : new int[]{60, 266, 392, 457}) {
            Level l = TestUtil.pack().get(id);
            Board b = new Board(l);
            java.util.List<Move> sol = Solver.solve(b, 200_000).moves;
            Board fin = b.copy();
            java.util.Set<Integer> flipped = new java.util.HashSet<>();
            for (Move m : sol) { fin.apply(m); if (!flipped.add(m.cell)) flipped.remove(m.cell); }
            BeamTracer.Result r = BeamTracer.trace(fin);
            int breaking = 0;
            for (int c = 0; c < fin.width * fin.height; c++) {
                if (!fin.isRotatable(c) || flipped.contains(c) || r.lit[c] == 0) continue;
                fin.rotate(c);
                if (!tr.isSolved(fin)) breaking++;
                fin.rotate(c);
            }
            assertEquals("bölüm " + id, breaking, Shortcuts.liveDecoys(b, sol));
        }
    }

    /** Hepsine bir kez dokunan bölüm algılanır; sabit yol aynaları yetiyorsa optimum korunur. */
    @Test public void fixedPathMirrorsAreUsedBeforeShorteningTheLevel() throws Exception {
        String json = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
                getClass().getResource("/level422_eski.json").toURI())), StandardCharsets.UTF_8);
        Level l = Level.fromJson(json);
        Board b = new Board(l);
        assertTrue("eski 422 kısayolla çözülüyordu", Shortcuts.thoughtless(b));
        int opt = l.optimal;
        assertTrue(Shortcuts.ensureLiveDecoys(l, 2, 100_000, 40, 0, opt, new Random(422 * 7919L)));
        assertEquals("optimum değişmemeli", opt, l.optimal);
        Board nb = new Board(l);
        assertFalse(Shortcuts.thoughtless(nb));
        Solver.Solution s = Solver.solve(nb, 200_000);
        assertEquals(opt, s.optimal);
        assertTrue(Shortcuts.liveDecoys(nb, s.moves) >= 2);
    }
}
