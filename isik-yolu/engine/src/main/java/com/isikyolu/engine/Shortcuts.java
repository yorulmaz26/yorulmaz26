package com.isikyolu.engine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Düşünmeden çözme kısayolları. İki konumlu aynalarda dönüşlerin sırası önemsizdir; karıştırma çözüm
 * yolundaki bütün döner aynaları çevirdiyse "her döner aynaya bir kez dokun" bölümü bitirir. Bunu önlemek
 * için çözümde ışık alan ama zaten doğru duran döner aynalar (canlı yem) gerekir: onlara dokunan oyuncu
 * yolu bozar.
 */
public final class Shortcuts {
    private Shortcuts() {}

    /** Bütün döner aynalar birer kez çevrilince (bloklar varsa yalnızca itmelerle) çözüme ulaşılabiliyor mu? */
    public static boolean flipAllSolves(Board start, int pushCap) {
        Board work = start.copy();
        for (int c : work.rotCells) work.rotate(c);
        BeamTracer tr = new BeamTracer();
        if (tr.isSolved(work)) return true;
        if (work.blocks.length == 0) return false;
        LongIntMap seen = new LongIntMap(256);
        ArrayDeque<Long> q = new ArrayDeque<>();
        long k0 = work.stateKey();
        seen.putIfAbsent(k0, 0);
        q.add(k0);
        while (!q.isEmpty()) {
            long k = q.poll();
            work.applyKey(k);
            if (tr.isSolved(work)) return true;
            for (int b : work.blocks.clone())
                for (int d = 0; d < 4; d++) {
                    if (!work.canPush(b, d)) continue;
                    int nc = work.push(b, d);
                    long nk = work.stateKey();
                    work.push(nc, Dir.opposite(d));
                    if (seen.putIfAbsent(nk, 0)) {
                        if (seen.size() > pushCap) return false;
                        q.add(nk);
                    }
                }
        }
        return false;
    }

    /** Döner aynalara verilen sırayla birer kez dokunmak, ara adımlardan birinde bölümü bitiriyor mu? */
    public static boolean tapOrderSolves(Board start, List<Integer> order) {
        Board work = start.copy();
        BeamTracer tr = new BeamTracer();
        for (int c : order) {
            work.rotate(c);
            if (tr.isSolved(work)) return true;
        }
        return false;
    }

    /** Oyuncunun düşünmeden izleyebileceği dokunma sıraları: satır satır, sütun sütun ve tersleri. */
    public static List<List<Integer>> naiveOrders(Board b) {
        List<Integer> rows = new ArrayList<>();
        for (int c : b.rotCells) rows.add(c);
        Collections.sort(rows);
        List<Integer> cols = new ArrayList<>(rows);
        cols.sort((p, q) -> p % b.width != q % b.width ? p % b.width - q % b.width : p / b.width - q / b.width);
        List<List<Integer>> out = new ArrayList<>();
        out.add(rows);
        out.add(cols);
        List<Integer> r2 = new ArrayList<>(rows); Collections.reverse(r2); out.add(r2);
        List<Integer> c2 = new ArrayList<>(cols); Collections.reverse(c2); out.add(c2);
        return out;
    }

    /** "Her döner aynaya birer kez dokun" türünden bir kısayol bölümü bitiriyor mu? */
    public static boolean thoughtless(Board start) {
        if (start.rotCells.length == 0) return false;
        if (flipAllSolves(start, 50_000)) return true;
        for (List<Integer> o : naiveOrders(start)) if (tapOrderSolves(start, o)) return true;
        return false;
    }

    /** Çözüm durumunda ışık alan ama çözümde (tek sayıda) çevrilmeyen döner aynaların sayısı. */
    public static int liveDecoys(Board start, List<Move> solution) {
        Board fin = start.copy();
        int[] flips = new int[fin.width * fin.height];
        for (Move m : solution) { fin.apply(m); if (m.kind == Move.ROTATE) flips[m.cell] ^= 1; }
        BeamTracer.Result r = BeamTracer.trace(fin);
        int n = 0;
        for (int c : fin.rotCells) if (flips[c] == 0 && r.lit[c] != 0) n++;
        return n;
    }

    /**
     * En az {@code need} canlı yem sağlar ve kısayolları kapatır. Önce çözüm yolundaki sabit aynalardan
     * bazıları (yönü zaten doğru) döner yapılır: çözüm, optimum ve başlangıç değişmez. Yetmezse çözümün
     * çevirdiği bir yol aynası başlangıçta doğru yöne bırakılır (optimum en çok {@code maxReduce} düşer,
     * {@code minOpt} altına inmez).
     * @return şart sağlandıysa true (sağlanamazsa bölüm değiştirilmeden bırakılır)
     */
    public static boolean ensureLiveDecoys(Level level, int need, int cap, int maxRot, int maxReduce, int minOpt, Random rnd) {
        List<Runnable> undo = new ArrayList<>();
        Set<Piece> tried = new HashSet<>();
        int reduced = 0;
        while (true) {
            Board b = new Board(level);
            Solver.Solution s = Solver.solve(b, cap);
            if (!s.solvable) { rollback(undo); return false; }
            if (!thoughtless(b) && liveDecoys(b, s.moves) >= need) return true;
            Board fin = b.copy();
            int[] flips = new int[level.width * level.height];
            for (Move m : s.moves) { fin.apply(m); if (m.kind == Move.ROTATE) flips[m.cell] ^= 1; }
            BeamTracer.Result r = BeamTracer.trace(fin);
            // 1) çözüm yolundaki sabit aynayı döner yap
            List<Piece> cands = new ArrayList<>();
            for (Piece p : level.pieces)
                if (p.type == Cell.MIRROR && !p.rotatable && !tried.contains(p) && r.lit[p.y * level.width + p.x] != 0) cands.add(p);
            Collections.shuffle(cands, rnd);
            Piece pick = null;
            boolean pickBreaks = false;
            for (Piece p : cands) {
                tried.add(p);
                p.rotatable = true;
                boolean ok = valid(level, cap, maxRot, level.optimal);
                boolean breaks = ok && !thoughtless(new Board(level));
                p.rotatable = false;
                if (!ok) continue;
                if (pick == null || (breaks && !pickBreaks)) { pick = p; pickBreaks = breaks; }
                if (pickBreaks) break;
            }
            if (pick != null) {
                Piece fp = pick;
                fp.rotatable = true;
                undo.add(() -> fp.rotatable = false);
                continue;
            }
            // 2) çözümün çevirdiği bir yol aynasını başlangıçta doğru bırak
            if (reduced >= maxReduce) { rollback(undo); return false; }
            List<Piece> rot = new ArrayList<>();
            for (Piece p : level.pieces) {
                int c = p.y * level.width + p.x;
                if (p.type == Cell.MIRROR && p.rotatable && flips[c] == 1 && r.lit[c] != 0) rot.add(p);
            }
            Collections.shuffle(rot, rnd);
            int oldOpt = level.optimal, pickOpt = -1;
            for (Piece p : rot) {
                p.orient ^= 1;
                int opt = startOk(level, cap) ? optimal(level, cap) : -1;
                boolean ok = opt >= minOpt && opt < oldOpt && opt >= oldOpt - (maxReduce - reduced);
                boolean breaks = false;
                if (ok) { level.optimal = opt; ok = valid(level, cap, maxRot, opt); breaks = ok && !thoughtless(new Board(level)); level.optimal = oldOpt; }
                p.orient ^= 1;
                if (!ok) continue;
                if (pick == null || (breaks && !pickBreaks)) { pick = p; pickBreaks = breaks; pickOpt = opt; }
                if (pickBreaks) break;
            }
            if (pick == null) { rollback(undo); return false; }
            Piece fp = pick;
            fp.orient ^= 1;
            level.optimal = pickOpt;
            reduced += oldOpt - pickOpt;
            undo.add(() -> { fp.orient ^= 1; level.optimal = oldOpt; });
        }
    }

    /** Başlangıç çözülü değil ve bütün kapılar kapalı. */
    private static boolean startOk(Level level, int cap) {
        BeamTracer.Result r0 = BeamTracer.trace(new Board(level));
        if (r0.solved) return false;
        for (boolean open : r0.doorOpen) if (open) return false;
        return true;
    }

    private static int optimal(Level level, int cap) {
        Solver.Solution s = Solver.solve(new Board(level), cap);
        return s.solvable && !s.capped ? s.optimal : -1;
    }

    private static void rollback(List<Runnable> undo) {
        for (int i = undo.size() - 1; i >= 0; i--) undo.get(i).run();
        undo.clear();
    }

    private static boolean valid(Level level, int cap, int maxRot, int opt) {
        Board b;
        try { b = new Board(level); } catch (IllegalArgumentException e) { return false; }
        if (b.rotatableCount() > maxRot) return false;
        Solver.Solution s = Solver.solve(b, cap);
        if (!s.solvable || s.capped || s.optimal != opt) return false;
        return Solver.countReachable(b, cap) >= 0;
    }
}
