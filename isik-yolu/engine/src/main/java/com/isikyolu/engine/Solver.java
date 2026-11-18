package com.isikyolu.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Genişlik öncelikli arama ile en az hamleli çözümü bulur.
 * Her hamle (bir ayna döndürme veya bir blok itme) maliyeti 1'dir.
 */
public final class Solver {

    public static final class Solution {
        public boolean solvable;
        /** En az hamle sayısı (çözülemezse -1). */
        public int optimal = -1;
        public final List<Move> moves = new ArrayList<>();
        public int explored;
        /** Durum sınırı aşıldı mı? */
        public boolean capped;
    }

    private Solver() {}

    public static Solution solve(Board start, int stateCap) {
        return search(start, stateCap, true);
    }

    /** Başlangıçtan ulaşılabilen toplam durum sayısı (sınırla). */
    public static int countReachable(Board start, int stateCap) {
        Solution s = search(start, stateCap, false);
        return s.capped ? -1 : s.explored;
    }

    private static Solution search(Board start, int cap, boolean stopAtGoal) {
        Solution sol = new Solution();
        Board work = start.copy();
        BeamTracer tracer = new BeamTracer();
        long[] queue = new long[1024];
        int[] parent = new int[1024];
        int[] via = new int[1024];
        LongIntMap seen = new LongIntMap(1024);
        int head = 0, tail = 0;
        long k0 = work.stateKey();
        queue[tail] = k0; parent[tail] = -1; via[tail] = 0; tail++;
        seen.putIfAbsent(k0, 0);
        int goal = -1;
        int[] blocksTmp;
        while (head < tail) {
            int idx = head++;
            long key = queue[idx];
            work.applyKey(key);
            if (stopAtGoal && tracer.isSolved(work)) { goal = idx; break; }
            // ayna döndürme komşuları
            for (int i = 0; i < work.rotCells.length; i++) {
                long nk = key ^ (1L << i);
                if (seen.putIfAbsent(nk, tail)) {
                    if (tail >= cap) { sol.capped = true; sol.explored = tail; return sol; }
                    if (tail == queue.length) {
                        queue = java.util.Arrays.copyOf(queue, tail * 2);
                        parent = java.util.Arrays.copyOf(parent, tail * 2);
                        via = java.util.Arrays.copyOf(via, tail * 2);
                    }
                    queue[tail] = nk; parent[tail] = idx; via[tail] = Move.rotate(work.rotCells[i]).encode(); tail++;
                }
            }
            // blok itme komşuları
            if (work.blocks.length > 0) {
                blocksTmp = work.blocks.clone();
                for (int b : blocksTmp) {
                    for (int d = 0; d < 4; d++) {
                        if (!work.canPush(b, d)) continue;
                        int nc = work.push(b, d);
                        long nk = work.stateKey();
                        work.push(nc, Dir.opposite(d));
                        if (seen.putIfAbsent(nk, tail)) {
                            if (tail >= cap) { sol.capped = true; sol.explored = tail; return sol; }
                            if (tail == queue.length) {
                                queue = java.util.Arrays.copyOf(queue, tail * 2);
                                parent = java.util.Arrays.copyOf(parent, tail * 2);
                                via = java.util.Arrays.copyOf(via, tail * 2);
                            }
                            queue[tail] = nk; parent[tail] = idx; via[tail] = Move.push(b, d).encode(); tail++;
                        }
                    }
                }
            }
        }
        sol.explored = tail;
        if (goal >= 0) {
            sol.solvable = true;
            for (int i = goal; parent[i] >= 0; i = parent[i]) sol.moves.add(Move.decode(via[i]));
            Collections.reverse(sol.moves);
            sol.optimal = sol.moves.size();
        }
        return sol;
    }
}
