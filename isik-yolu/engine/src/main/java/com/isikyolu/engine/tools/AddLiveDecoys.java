package com.isikyolu.engine.tools;

import com.isikyolu.engine.BeamTracer;
import com.isikyolu.engine.Board;
import com.isikyolu.engine.Chapters;
import com.isikyolu.engine.Level;
import com.isikyolu.engine.LevelPack;
import com.isikyolu.engine.Shortcuts;
import com.isikyolu.engine.Solver;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Random;

/**
 * Mantık bölümlerinde "her döner aynaya bir kez dokun" kısayolunu kapatır: çözüm yolundaki sabit
 * aynalardan bazıları (yönü zaten doğru) döner yapılır; yetmezse bir yol aynası başlangıçta doğru
 * bırakılır (optimum en çok 2 düşer). Çözülebilirlik ve optimum yeniden doğrulanır.
 * Kullanım: AddLiveDecoys &lt;levels.json&gt; [--dry]
 */
public final class AddLiveDecoys {
    /** Durum sınırı: ipucu aramasının telefonda hızlı kalması için (uygulamanın sınırı 150000). */
    public static final int CAP = 100_000, MAX_REDUCE = 2;

    public static int need(int id) { return Chapters.liveDecoys(id); }

    public static void main(String[] a) throws Exception {
        boolean dry = a.length > 1 && a[1].equals("--dry");
        LevelPack p = LevelPack.parse(new String(Files.readAllBytes(Paths.get(a[0])), StandardCharsets.UTF_8));
        int logic = 0, bad = 0, fixed = 0, failed = 0, added = 0, reducedTotal = 0, reducedLevels = 0;
        StringBuilder fails = new StringBuilder();
        for (Level l : p.levels) {
            if (!Chapters.requiresLogic(l.id)) continue;
            logic++;
            Board b = new Board(l);
            Solver.Solution s = Solver.solve(b, CAP);
            boolean ok = !Shortcuts.thoughtless(b) && Shortcuts.liveDecoys(b, s.moves) >= need(l.id);
            if (ok) continue;
            bad++;
            int before = b.rotatableCount();
            int oldOpt = l.optimal;
            // önce yalnızca sabit yol aynalarıyla (optimum değişmez); olmazsa en az 1 canlı yem için en çok 2 hamle
            // kısaltılır, sonra kalan yemler yine optimumu değiştirmeden eklenir
            boolean done = Shortcuts.ensureLiveDecoys(l, need(l.id), CAP, 40, 0, oldOpt, new Random(l.id * 7919L));
            if (!done) {
                done = Shortcuts.ensureLiveDecoys(l, 1, CAP, 40, MAX_REDUCE, Math.max(3, oldOpt - MAX_REDUCE), new Random(l.id * 7919L));
                if (done && need(l.id) > 1) Shortcuts.ensureLiveDecoys(l, need(l.id), CAP, 40, 0, l.optimal, new Random(l.id * 104729L));
            }
            reducedTotal += oldOpt - l.optimal;
            if (oldOpt != l.optimal) reducedLevels++;
            if (done) {
                Board nb = new Board(l);
                Solver.Solution ns = Solver.solve(nb, CAP);
                if (!ns.solvable || ns.optimal != l.optimal || BeamTracer.trace(nb).solved)
                    throw new IllegalStateException("bölüm " + l.id + " bozuldu");
                fixed++;
                added += nb.rotatableCount() - before;
            } else {
                failed++;
                fails.append(l.id).append(' ');
            }
        }
        // 5. dünya: zorluk eğrisi düşmesin diye sabit yol aynalarından ek canlı yem (optimum değişmez)
        int boosted = 0;
        for (int round = 0, changed = 1; round < 5 && changed > 0; round++) { // değişiklik kalmayana dek
            changed = 0;
            for (Level l : p.levels) {
                if (l.id <= 400 || !Chapters.requiresLogic(l.id)) continue;
                Board b = new Board(l);
                int live = Shortcuts.liveDecoys(b, Solver.solve(b, CAP).moves), before = b.rotatableCount();
                for (int k = live + 2; k > live; k--)
                    if (Shortcuts.ensureLiveDecoys(l, k, CAP, 40, 0, l.optimal, new Random(l.id * 31L + round))) break;
                if (new Board(l).rotatableCount() > before) { boosted++; changed++; }
            }
        }
        System.out.println("5. dünyada ek canlı yem: " + boosted + " bölüm");
        System.out.println("mantık bölümü " + logic + " · kısayollu/yemsiz " + bad + " · düzeltilen " + fixed
                + " (" + added + " sabit ayna döner yapıldı; " + reducedLevels + " bölümde optimum toplam " + reducedTotal + " azaldı) · düzeltilemeyen " + failed + ": " + fails);
        if (!dry) Files.write(Paths.get(a[0]), p.toJson().getBytes(StandardCharsets.UTF_8));
    }
}
