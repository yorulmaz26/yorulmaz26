package com.isikyolu.engine;

import static org.junit.Assert.*;

import org.junit.Test;

/** Mantık ölçüsü: ışını takip eden oyuncunun karar ağacı. */
public class BeamFollowerTest {

    @Test public void straightPuzzleHasTinyTree() {
        // tek ayna, yanlış yönelim ışını hemen dışarı yollar
        Level l = new Level(4, 4).add(Piece.source(0, 3, Dir.RIGHT, LightColor.WHITE))
                .add(Piece.mirror(2, 3, 1, true)).add(Piece.target(2, 0, LightColor.WHITE));
        BeamFollower.Effort e = BeamFollower.measure(new Board(l), 1000);
        assertTrue(e.solvable);
        assertEquals(2, e.leaves);
        assertEquals(1, e.deadEnds);
        assertEquals(0, e.deepTraps);
    }

    @Test public void decoyChainsMakeTreeBigger() {
        // yanlış dönüş, ışını başka döner aynalardan oluşan bir tuzağa sokar
        Level easy = new Level(6, 6).add(Piece.source(0, 5, Dir.RIGHT, LightColor.WHITE))
                .add(Piece.mirror(3, 5, 0, true)).add(Piece.mirror(3, 1, 0, true)).add(Piece.target(5, 1, LightColor.WHITE));
        Level trap = easy.copy().add(Piece.mirror(3, 3, 0, true)).add(Piece.mirror(1, 3, 0, true)).add(Piece.mirror(1, 1, 0, true));
        int a = BeamFollower.measure(new Board(easy), 1000).leaves;
        int b = BeamFollower.measure(new Board(trap), 1000).leaves;
        assertTrue("tuzaklı " + b + " > kolay " + a, b > a);
    }

    @Test public void logicLevelsHaveLargeDecisionTrees() throws Exception {
        LevelPack p = TestUtil.pack();
        double[] sum = new double[6], sumEasy = new double[6];
        int[] n = new int[6], nEasy = new int[6];
        java.util.List<java.util.List<Integer>> per = new java.util.ArrayList<>();
        for (int w = 0; w < 6; w++) per.add(new java.util.ArrayList<Integer>());
        for (Level l : p.levels) {
            int w = Chapters.worldOf(l.id);
            int e = BeamFollower.measure(new Board(l), 5000).leaves;
            if (Chapters.requiresLogic(l.id)) {
                assertTrue("mantık bölümü " + l.id + " çok kolay: " + e, e >= 5);
                sum[w] += e; n[w]++;
                per.get(w).add(e);
            } else { sumEasy[w] += e; nEasy[w]++; }
        }
        double prev = 0;
        for (int w = 1; w <= 5; w++) {
            double avg = sum[w] / n[w];
            System.out.printf("Dünya %d: mantık bölümü %d, ortalama karar ağacı %.1f yaprak%n", w, n[w], avg);
            if (nEasy[w] > 0) System.out.printf("         öteki bölümler %d, ortalama %.1f%n", nEasy[w], sumEasy[w] / nEasy[w]);
            java.util.List<Integer> v = per.get(w);
            java.util.Collections.sort(v);
            int median = v.get(v.size() / 2);
            System.out.printf("         ortanca %d%n", median);
            assertTrue("dünya " + w + " ortanca", median >= 14);
            assertTrue("dünya " + w + " ortancası öncekinden düşük", median >= prev);
            prev = median;
        }
        // 1. dünyada grup 2–10'un son 6 bölümü (54), sonraki dünyaların hepsi (400) mantık bölümüdür
        assertEquals(54 + 400, n[1] + n[2] + n[3] + n[4] + n[5]);
    }
}
