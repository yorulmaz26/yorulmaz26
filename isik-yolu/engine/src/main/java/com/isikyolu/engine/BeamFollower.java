package com.isikyolu.engine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * İnsan benzeri çözüm modeli: oyuncu ışını kaynaktan takip eder ve ışının değdiği ilk karar verilmemiş
 * döner aynada iki yönelimi dener. Bu karar ağacının yaprak sayısı "takip zahmeti"dir.
 * Kolay bölümlerde yanlış seçim ışını hemen duvara/dışarı yollar (zahmet ≈ ayna sayısı);
 * mantık bölümlerinde yanlış seçimler uzun, inandırıcı çıkmazlara gider ve zahmet katlanarak artar.
 */
public final class BeamFollower {
    private final Board b;
    private final int cap;
    private int leaves;
    private int deadEnds;
    private int deepTraps;

    private BeamFollower(Board b, int cap) { this.b = b; this.cap = cap; }

    public static final class Effort {
        /** Karar ağacının yaprak sayısı. */
        public int leaves;
        /** Çözüme ulaşmayan yapraklar. */
        public int deadEnds;
        /** En az 3 aynalık karar zincirinden sonra çıkmaza giren yapraklar (tuzaklar). */
        public int deepTraps;
        /** Ağacın içinde çözüm var mı (yalnızca ayna kararlarıyla). */
        public boolean solvable;
    }

    public static Effort measure(Board start, int cap) {
        BeamFollower f = new BeamFollower(start.copy(), cap);
        Effort e = new Effort();
        int solved = f.walk(new HashSet<Integer>(), 0);
        e.leaves = f.leaves;
        e.deadEnds = f.deadEnds;
        e.deepTraps = f.deepTraps;
        e.solvable = solved > 0;
        return e;
    }

    /** Işının değdiği döner aynalar, ışın sırasıyla. */
    private List<Integer> litMirrorsInOrder() {
        BeamTracer.Result r = BeamTracer.trace(b);
        List<Integer> out = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        for (BeamTracer.Segment s : r.segments) {
            if ((s.x2 & 1) == 0 || (s.y2 & 1) == 0) continue; // kenarda biten parça
            int c = (s.y2 / 2) * b.width + s.x2 / 2;
            if (b.isRotatable(c) && seen.add(c)) out.add(c);
        }
        return out;
    }

    /** Çözüm bulunan yaprak sayısını döndürür. */
    private int walk(Set<Integer> decided, int depth) {
        if (leaves >= cap) return 0;
        int next = -1;
        for (int c : litMirrorsInOrder()) if (!decided.contains(c)) { next = c; break; }
        if (next < 0) {
            leaves++;
            if (BeamTracer.trace(b).solved) return 1;
            deadEnds++;
            if (depth >= 3) deepTraps++;
            return 0;
        }
        int found = 0;
        decided.add(next);
        for (int o = 0; o < 2; o++) {
            found += walk(decided, depth + 1);
            b.rotate(next);
        }
        decided.remove(next);
        return found;
    }
}
