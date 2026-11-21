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

    /**
     * Ulaşılabilen bütün durumlarda ışık alan hücrelerin renk birleşimi (sınır aşılırsa null).
     * Hiçbir durumda ışık almayan döner aynaları bulmak için kullanılır.
     */
    public static int[] litUnion(Board start, int cap) {
        Board work = start.copy();
        BeamTracer tracer = new BeamTracer();
        int[] union = new int[work.width * work.height];
        java.util.ArrayDeque<Long> q = new java.util.ArrayDeque<>();
        LongIntMap seen = new LongIntMap(1024);
        long k0 = work.stateKey();
        seen.putIfAbsent(k0, 0);
        q.add(k0);
        while (!q.isEmpty()) {
            long k = q.poll();
            work.applyKey(k);
            BeamTracer.Result r = tracer.run(work, false);
            for (int c = 0; c < union.length; c++) union[c] |= r.lit[c];
            for (Move m : work.legalMoves()) {
                work.apply(m);
                long nk = work.stateKey();
                work.applyKey(k);
                if (seen.putIfAbsent(nk, 0)) {
                    if (seen.size() > cap) return null;
                    q.add(nk);
                }
            }
        }
        return union;
    }

    /** Hiçbir durumda ışık almayan döner aynaları sabitler (yönleri sonucu etkilemez). @return sabitlenen sayısı */
    public static int fixDeadMirrors(Level level, int cap) {
        int[] u = litUnion(new Board(level), cap);
        if (u == null) return 0;
        int n = 0;
        for (Piece p : level.pieces)
            if (p.type == Cell.MIRROR && p.rotatable && u[p.y * level.width + p.x] == 0) { p.rotatable = false; n++; }
        return n;
    }

    /**
     * Başlangıçtan ulaşılabilen her durumun çözüme en kısa uzaklığı (hamleler tersinir olduğu için
     * hedef durumlarından geriye çok kaynaklı BFS). Sınır aşılırsa null.
     */
    public static java.util.Map<Long, Integer> distancesToGoal(Board start, int cap) {
        Board work = start.copy();
        BeamTracer tracer = new BeamTracer();
        java.util.ArrayDeque<Long> q = new java.util.ArrayDeque<>();
        java.util.HashSet<Long> all = new java.util.HashSet<>();
        java.util.ArrayList<Long> goals = new java.util.ArrayList<>();
        long k0 = work.stateKey();
        all.add(k0); q.add(k0);
        while (!q.isEmpty()) {
            long k = q.poll();
            work.applyKey(k);
            if (tracer.isSolved(work)) goals.add(k);
            for (Move m : work.legalMoves()) {
                work.apply(m);
                long nk = work.stateKey();
                work.applyKey(k);
                if (all.add(nk)) {
                    if (all.size() > cap) return null;
                    q.add(nk);
                }
            }
        }
        java.util.HashMap<Long, Integer> dist = new java.util.HashMap<>();
        for (long g : goals) { dist.put(g, 0); q.add(g); }
        while (!q.isEmpty()) {
            long k = q.poll();
            int d = dist.get(k);
            work.applyKey(k);
            for (Move m : work.legalMoves()) {
                work.apply(m);
                long nk = work.stateKey();
                work.applyKey(k);
                if (!dist.containsKey(nk)) { dist.put(nk, d + 1); q.add(nk); }
            }
        }
        return dist;
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
