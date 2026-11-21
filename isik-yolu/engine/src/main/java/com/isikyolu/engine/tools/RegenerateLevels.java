package com.isikyolu.engine.tools;

import com.isikyolu.engine.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Belirli bölümleri zorluğu gevşetmeden yeniden üretir: en az hamle eski bölümün altına inmez, gerekirse
 * tahta büyütülür. Canlı yem şartı sağlanamayan bölümler için. Kullanım: RegenerateLevels &lt;girdi&gt; &lt;çıktı&gt; id...
 */
public final class RegenerateLevels {
    public static void main(String[] a) throws Exception {
        Path in = Paths.get(a[0]), out = Paths.get(a[1]);
        LevelPack orig = LevelPack.parse(new String(Files.readAllBytes(in), StandardCharsets.UTF_8));
        Set<String> sigs = new HashSet<>();
        for (Level l : orig.levels) sigs.add(l.signature());
        for (int ai = 2; ai < a.length; ai++) {
            int id = Integer.parseInt(a[ai]);
            Level old = orig.get(id);
            long t0 = System.currentTimeMillis();
            Level best = null;
            for (int grow = 0; grow <= 2 && best == null; grow++) {
                for (int seed = 0; seed < 1500 && best == null; seed++) {
                    LevelGenerator.Config cfg = Chapters.configFor(id);
                    cfg.w += grow; cfg.h += grow;
                    if (cfg.w > 8) cfg.w = 8; if (cfg.h > 11) cfg.h = 11;
                    cfg.minOpt = Math.max(cfg.minOpt, Math.min(old.optimal, cfg.maxOpt));
                    cfg.minEffort = (int) (cfg.minEffort * 0.8);
                    Level c = new LevelGenerator(cfg, new Random(id * 1_000_003L + seed * 7919L + grow * 13L)).generate(400);
                    if (c == null || sigs.contains(c.signature())) continue;
                    best = c;
                }
            }
            if (best == null) { System.out.println("bölüm " + id + ": üretilemedi"); continue; }
            best.id = id; best.chapter = old.chapter; best.name = old.name;
            sigs.add(best.signature());
            for (int i = 0; i < orig.levels.size(); i++) if (orig.levels.get(i).id == id) orig.levels.set(i, best);
            System.out.println("bölüm " + id + " " + best.width + "x" + best.height + " optimum " + old.optimal + " -> " + best.optimal
                + " durum " + Solver.countReachable(new Board(best), 200000) + " (" + (System.currentTimeMillis() - t0) + " ms)");
            Files.write(out, orig.toJson().getBytes(StandardCharsets.UTF_8));
        }
        Files.write(out, orig.toJson().getBytes(StandardCharsets.UTF_8));
    }
}
