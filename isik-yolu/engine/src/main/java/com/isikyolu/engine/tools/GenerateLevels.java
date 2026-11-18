package com.isikyolu.engine.tools;

import com.isikyolu.engine.BeamTracer;
import com.isikyolu.engine.Board;
import com.isikyolu.engine.Chapters;
import com.isikyolu.engine.Level;
import com.isikyolu.engine.LevelGenerator;
import com.isikyolu.engine.LevelPack;
import com.isikyolu.engine.Solver;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * Bölüm üretim aracı: java GenerateLevels <çıktı.json> [ilk] [son]
 * Her bölüm deterministik tohumla üretilir, çözücüyle doğrulanır ve kopyalar elenir.
 * Var olan dosyadaki aralık dışı bölümler korunur (önce 1–10, sonra 11–100 üretilebilir).
 */
public final class GenerateLevels {
    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);
        int from = args.length > 1 ? Integer.parseInt(args[1]) : 1;
        int to = args.length > 2 ? Integer.parseInt(args[2]) : com.isikyolu.engine.Progress.LEVEL_COUNT;
        LevelPack old = out.exists() ? LevelPack.parse(new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8)) : new LevelPack();
        LevelPack pack = new LevelPack();
        Set<String> sigs = new HashSet<>();
        for (int id = 1; id <= com.isikyolu.engine.Progress.LEVEL_COUNT; id++) {
            Level keep = old.get(id);
            if ((id < from || id > to) && keep != null) {
                pack.levels.add(keep);
                sigs.add(keep.signature());
                continue;
            }
            if (id < from || id > to) continue;
            long t0 = System.currentTimeMillis();
            Level l = null;
            int seed = 0;
            // istenen zorlukta üretilemezse aralık adım adım gevşetilir (deterministik)
            for (int relax = 0; relax <= 4 && l == null; relax++) {
                for (seed = 0; seed < (relax == 0 ? 400 : 120) && l == null; seed++) {
                    LevelGenerator.Config cfg = Chapters.configFor(id);
                    cfg.minOpt = Math.max(1, cfg.minOpt - relax);
                    cfg.maxOpt = cfg.maxOpt + relax;
                    Level cand = new LevelGenerator(cfg, new Random(id * 100_003L + seed * 7_919L + relax * 31L)).generate(400);
                    if (cand == null || sigs.contains(cand.signature())) continue;
                    l = cand;
                }
                if (l != null && relax > 0) System.out.println("  (bölüm " + id + " için zorluk aralığı " + relax + " gevşetildi)");
            }
            if (l == null) throw new IllegalStateException("Bölüm üretilemedi: " + id);
            l.id = id;
            l.chapter = Chapters.chapterOf(id);
            l.name = Chapters.NAMES[l.chapter - 1] + " " + ((id - 1) % 10 + 1);
            // son doğrulama
            Solver.Solution s = Solver.solve(new Board(l), 200_000);
            if (!s.solvable || s.optimal != l.optimal || BeamTracer.trace(new Board(l)).solved)
                throw new IllegalStateException("Doğrulama başarısız: " + id);
            sigs.add(l.signature());
            pack.levels.add(l);
            System.out.printf("Bölüm %3d  %dx%d  optimum=%d  nesne=%d  durum=%d  (%d ms, tohum %d)%n",
                    id, l.width, l.height, l.optimal, l.pieces.size(), Solver.countReachable(new Board(l), 200_000),
                    System.currentTimeMillis() - t0, seed - 1);
        }
        out.getParentFile().mkdirs();
        Files.write(out.toPath(), pack.toJson().getBytes(StandardCharsets.UTF_8));
        System.out.println("Yazıldı: " + out + " (" + pack.size() + " bölüm)");
    }
}
