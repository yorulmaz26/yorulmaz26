package com.isikyolu.engine;

import static org.junit.Assert.*;

import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

/** Test 2: bölüm yükleme, veri bütünlüğü ve mekanik dağılımı. */
public class LevelPackTest {

    @Test public void allLevelsLoad() throws Exception {
        LevelPack p = TestUtil.pack();
        assertEquals(Progress.LEVEL_COUNT, p.size());
        Set<String> sigs = new HashSet<>();
        for (int i = 1; i <= Progress.LEVEL_COUNT; i++) {
            Level l = p.get(i);
            assertNotNull("eksik bölüm " + i, l);
            assertEquals(Chapters.chapterOf(i), l.chapter);
            assertFalse(l.name.isEmpty());
            assertTrue(l.optimal >= 1);
            assertTrue(l.count(Cell.SOURCE) >= 1);
            assertTrue(l.count(Cell.TARGET) >= 1);
            assertTrue("kopya düzen: " + i, sigs.add(l.signature()));
            new Board(l); // çakışma yoksa kurulur
            // JSON gidiş-dönüş
            assertEquals(l.toJson(), Level.fromJson(l.toJson()).toJson());
        }
    }

    @Test public void chaptersIntroduceTheirMechanics() throws Exception {
        LevelPack p = TestUtil.pack();
        for (int i = 1; i <= Progress.LEVEL_COUNT; i++) {
            Level l = p.get(i);
            int ch = Chapters.mechanicOf(l.chapter);
            int world = Chapters.worldOf(i);
            boolean colored = false;
            for (Piece x : l.pieces) if (x.type == Cell.SOURCE && x.color != LightColor.WHITE) colored = true;
            if (ch <= 2 && world <= 2) {
                assertEquals(0, l.count(Cell.FILTER) + l.count(Cell.PRISM) + l.count(Cell.BLOCK) + l.count(Cell.DOOR));
                assertFalse(colored);
            }
            if (ch == 3) assertTrue("duvar: " + i, l.count(Cell.WALL) >= 6);
            if (ch == 4) assertTrue("renkli kaynak: " + i, colored);
            if (ch == 5) assertTrue("filtre: " + i, l.count(Cell.FILTER) >= 1);
            if (ch == 6) assertTrue("prizma: " + i, l.count(Cell.PRISM) >= 1);
            if (ch == 7) assertTrue("çoklu hedef: " + i, l.count(Cell.TARGET) >= 2);
            if (ch == 8) assertTrue("blok: " + i, l.count(Cell.BLOCK) >= 1);
            if (ch == 9) assertTrue("kapı: " + i, l.count(Cell.DOOR) >= 1 && l.count(Cell.KEY) == l.count(Cell.DOOR));
            if (l.count(Cell.DOOR) > 0) {
                BeamTracer.Result r = TestUtil.trace(l);
                for (boolean open : r.doorOpen) assertFalse("kapı başlangıçta açık: " + i, open);
            }
            if (ch == 10) {
                int kinds = 0;
                for (int t : new int[]{Cell.FILTER, Cell.PRISM, Cell.BLOCK, Cell.DOOR}) if (l.count(t) > 0) kinds++;
                if (colored) kinds++;
                assertTrue("karma: " + i, kinds >= 2);
            }
        }
    }

    @Test public void difficultyProgresses() throws Exception {
        LevelPack p = TestUtil.pack();
        double prev = 0;
        double[] avg = new double[11];
        for (int ch = 1; ch <= 10; ch++) {
            double s = 0;
            for (int i = 1; i <= 10; i++) s += p.get((ch - 1) * 10 + i).optimal;
            avg[ch] = s / 10;
        }
        assertEquals(1, p.get(1).optimal);
        assertTrue(avg[10] > avg[1]);
        assertTrue(avg[2] > avg[1]);
        // dünyalar giderek zorlaşır
        double prevWorld = 0;
        for (int w = 1; w <= Chapters.WORLDS; w++) {
            double sum = 0;
            for (int i = 1; i <= 100; i++) sum += p.get((w - 1) * 100 + i).optimal;
            double avgW = sum / 100;
            System.out.printf("Dünya %d ortalama optimum: %.2f%n", w, avgW);
            assertTrue("dünya " + w, avgW > prevWorld);
            prevWorld = avgW;
        }
        for (int ch = 1; ch <= Chapters.COUNT; ch++) {
            // bölüm grubunun ikinci yarısı ilk yarısından kolay olmamalı
            double a = 0, b = 0;
            for (int i = 1; i <= 5; i++) a += p.get((ch - 1) * 10 + i).optimal;
            for (int i = 6; i <= 10; i++) b += p.get((ch - 1) * 10 + i).optimal;
            assertTrue("grup " + ch, b >= a);
        }
    }
}
