package com.isikyolu.engine;

import java.time.LocalDate;
import java.util.Random;

/** Tarihten deterministik olarak üretilen günlük bulmaca (çevrimdışı çalışır). */
public final class DailyPuzzle {
    private DailyPuzzle() {}

    public static String keyFor(int epochDay) { return LocalDate.ofEpochDay(epochDay).toString(); }

    public static int today() { return (int) LocalDate.now().toEpochDay(); }

    public static Level generate(int epochDay) {
        long seed = 0x15C1L * 1_000_003L + epochDay * 7919L;
        Random r = new Random(seed);
        for (int round = 0; round < 6; round++) {
            LevelGenerator.Config c = new LevelGenerator.Config();
            c.w = 7; c.h = 9;
            Chapters.mixed(c, r.nextInt(10));
            c.minOpt = 4; c.maxOpt = 8;
            c.stateCap = 40000;
            c.liveDecoys = 1;
            if (c.blocks > 0) c.maxRot = 8;
            Level l = new LevelGenerator(c, new Random(r.nextLong())).generate(300);
            if (l != null) {
                l.id = 0; l.chapter = 0; l.name = "Günlük Bulmaca · " + keyFor(epochDay);
                return l;
            }
        }
        // güvenli yedek: basit iki aynalı bulmaca
        LevelGenerator.Config c = Chapters.configFor(15);
        Level l = new LevelGenerator(c, new Random(seed)).generate(2000);
        l.id = 0; l.chapter = 0; l.name = "Günlük Bulmaca · " + keyFor(epochDay);
        return l;
    }
}
