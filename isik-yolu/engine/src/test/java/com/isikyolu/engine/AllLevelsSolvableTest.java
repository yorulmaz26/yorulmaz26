package com.isikyolu.engine;

import static org.junit.Assert.*;

import org.junit.Test;

/** Test 7: 100 bölümün tamamı çözülebilir ve kayıtlı optimum doğru. */
public class AllLevelsSolvableTest {

    @Test public void everyLevelIsSolvableWithStoredOptimum() throws Exception {
        LevelPack p = TestUtil.pack();
        StringBuilder report = new StringBuilder();
        for (Level l : p.levels) {
            Board b = new Board(l);
            assertFalse("başlangıçta çözülmüş: " + l.id, BeamTracer.trace(b).solved);
            Solver.Solution s = Solver.solve(b, 500_000);
            assertTrue("çözülemiyor: " + l.id, s.solvable);
            assertEquals("optimum uyuşmuyor: " + l.id, l.optimal, s.optimal);
            // çözümü tekrar oynat
            Board replay = new Board(l);
            for (Move m : s.moves) assertTrue(replay.apply(m));
            assertTrue("tekrar oynatma: " + l.id, BeamTracer.trace(replay).solved);
            int reach = Solver.countReachable(new Board(l), 100_000);
            assertTrue("durum uzayı ipucu için çok büyük: " + l.id, reach > 0);
            report.append(String.format("%d:%d/%d ", l.id, s.optimal, reach));
        }
        System.out.println("Çözüm özeti (bölüm:optimum/durum): " + report);
    }

    @Test public void dailyPuzzleIsDeterministicAndSolvable() {
        int day = 20_410;
        Level a = DailyPuzzle.generate(day), b = DailyPuzzle.generate(day);
        assertEquals(a.toJson(), b.toJson());
        assertEquals("kararlı, dilden bağımsız ad", "daily:" + DailyPuzzle.keyFor(day), a.name);
        Solver.Solution s = Solver.solve(new Board(a), 200_000);
        assertTrue(s.solvable);
        assertEquals(a.optimal, s.optimal);
        Level c = DailyPuzzle.generate(day + 1);
        assertNotEquals(a.signature(), c.signature());
        for (int d = day; d < day + 10; d++) {
            Level x = DailyPuzzle.generate(d);
            assertTrue(Solver.solve(new Board(x), 200_000).solvable);
        }
    }
}
