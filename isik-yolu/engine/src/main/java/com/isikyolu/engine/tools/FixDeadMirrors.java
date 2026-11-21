package com.isikyolu.engine.tools;

import com.isikyolu.engine.Board;
import com.isikyolu.engine.BeamTracer;
import com.isikyolu.engine.Level;
import com.isikyolu.engine.LevelPack;
import com.isikyolu.engine.Solver;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/** levels.json'daki hiç ışık almayan döner aynaları sabitler; optimumun değişmediğini doğrular. */
public final class FixDeadMirrors {
    public static void main(String[] a) throws Exception {
        LevelPack p = LevelPack.parse(new String(Files.readAllBytes(Paths.get(a[0])), StandardCharsets.UTF_8));
        int total = 0, levels = 0;
        for (Level l : p.levels) {
            int n = Solver.fixDeadMirrors(l, 200_000);
            if (n == 0) continue;
            total += n; levels++;
            Solver.Solution s = Solver.solve(new Board(l), 200_000);
            if (!s.solvable || s.optimal != l.optimal || BeamTracer.trace(new Board(l)).solved)
                throw new IllegalStateException("bölüm " + l.id + " sabitlemeden sonra bozuldu");
        }
        Files.write(Paths.get(a[0]), p.toJson().getBytes(StandardCharsets.UTF_8));
        System.out.println(levels + " bölümde " + total + " işlevsiz döner ayna sabitlendi");
    }
}
