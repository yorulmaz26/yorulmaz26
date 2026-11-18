package com.isikyolu.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Çözümden geriye bölüm üretici. Önce kaynaktan hedefe geçerli bir ışık yolu kurulur
 * (aynalar, filtreler, prizmalar, kapılar ve anahtarlar bu yol üzerinde), ardından aynalar
 * karıştırılır, yol dışına yanıltıcı nesneler eklenir ve sonuç BFS çözücüyle doğrulanır.
 * En az hamle sayısı istenen aralıkta değilse bölüm reddedilir ve yeniden üretilir.
 */
public final class LevelGenerator {

    public static final class Config {
        public int w = 6, h = 8;
        public int[] sourceColors = {LightColor.WHITE};
        public int turnsMin = 1, turnsMax = 2;
        /** İlk kaç kaynağın yolunda renk filtresi olacak. */
        public int filterSources;
        public int[] filterColors = {LightColor.RED, LightColor.GREEN, LightColor.BLUE, LightColor.YELLOW, LightColor.MAGENTA, LightColor.CYAN};
        /** İlk kaç kaynağın yolu prizmada bitecek. */
        public int prismSources;
        /** Prizmadan önce konacak filtre rengi (0 = yok). */
        public int prismFilter;
        public int branchTurnsMin = 0, branchTurnsMax = 1;
        /** İkinci kaynak birinci kaynağın hedefinde birleşir (renk karışımı). */
        public boolean merge;
        public int blocks, doors;
        public int decoyRot, decoyFixed, walls, decoyFilters;
        public double fixedFrac;
        public int minOpt = 1, maxOpt = 3;
        public int stateCap = 80000;
        public int maxRot = 16;
        /** En az ışın takip zahmeti (BeamFollower yaprak sayısı); 0 = şart yok. */
        public int minEffort;
        /** Işınlar aynı aynayı paylaşabilir (bir aynayı çevirmek iki ışını etkiler). */
        public boolean shareMirrors;
        /** Paylaşılan ayna olasılığı (her düğümde). */
        public double shareChance = 0.45;

        public Config copy() {
            Config c = new Config();
            c.w = w; c.h = h; c.sourceColors = sourceColors.clone(); c.turnsMin = turnsMin; c.turnsMax = turnsMax;
            c.filterSources = filterSources; c.filterColors = filterColors.clone(); c.prismSources = prismSources;
            c.prismFilter = prismFilter; c.branchTurnsMin = branchTurnsMin; c.branchTurnsMax = branchTurnsMax;
            c.merge = merge; c.blocks = blocks; c.doors = doors; c.decoyRot = decoyRot; c.decoyFixed = decoyFixed;
            c.walls = walls; c.decoyFilters = decoyFilters; c.fixedFrac = fixedFrac; c.minOpt = minOpt;
            c.maxOpt = maxOpt; c.stateCap = stateCap; c.maxRot = maxRot;
            c.minEffort = minEffort; c.shareMirrors = shareMirrors; c.shareChance = shareChance;
            return c;
        }
    }

    private static final int H_BIT = 1, V_BIT = 2, NODE = 4;

    private final Config cfg;
    private final Random rnd;
    private int w, h;
    private Piece[] grid;
    private int[] mark;
    /** Her kaynağın yolundaki tek işaretli düz hücreler (kapı/anahtar/blok adayları). */
    private List<List<Integer>> straight;
    private List<Piece> pathMirrors;
    private java.util.Set<Piece> sharedUsed;

    public LevelGenerator(Config cfg, Random rnd) { this.cfg = cfg; this.rnd = rnd; }

    /** En fazla {@code attempts} deneme yapar; başarısızsa null döner. */
    public Level generate(int attempts) {
        for (int a = 0; a < attempts; a++) {
            Level l = tryOnce();
            if (l != null) return l;
        }
        return null;
    }

    private Level tryOnce() {
        w = cfg.w; h = cfg.h;
        grid = new Piece[w * h];
        mark = new int[w * h];
        straight = new ArrayList<>();
        pathMirrors = new ArrayList<>();
        sharedUsed = new java.util.HashSet<>();
        int ns = cfg.sourceColors.length;
        Piece firstTarget = null;
        for (int s = 0; s < ns; s++) {
            straight.add(new ArrayList<Integer>());
            int color = cfg.sourceColors[s];
            int[] src = placeSource(color);
            if (src == null) return null;
            int turns = range(cfg.turnsMin, cfg.turnsMax);
            boolean filter = s < cfg.filterSources;
            boolean prism = s < cfg.prismSources;
            Piece mergeInto = (cfg.merge && s == 1) ? firstTarget : null;
            Piece t = walk(src[0], src[1], color, turns, s, filter, prism, mergeInto);
            if (t == null) return null;
            if (s == 0) firstTarget = t;
        }
        // kapılar ve anahtarlar
        for (int k = 0; k < cfg.doors; k++) {
            if (ns < 2) return null;
            int ds = rnd.nextInt(ns), ks = (ds + 1 + rnd.nextInt(ns - 1)) % ns;
            Integer dc = takeStraight(ds), kc = takeStraight(ks);
            if (dc == null || kc == null) return null;
            put(Piece.door(dc % w, dc / w, k));
            put(Piece.key(kc % w, kc / w, k));
        }
        // sabit aynalar
        for (Piece m : pathMirrors) m.rotatable = rnd.nextDouble() >= cfg.fixedFrac;
        // çözüm durumunu doğrula (bloklar eklenmeden önce)
        Level solved = snapshot();
        if (!BeamTracer.trace(new Board(solved)).solved) return null;
        // bloklar yolun üzerine konur: oyuncunun onları kenara itmesi gerekir
        for (int b = 0; b < cfg.blocks; b++) {
            Integer bc = takeStraightAny();
            if (bc == null) return null;
            put(Piece.block(bc % w, bc / w));
        }
        // yanıltıcı nesneler
        for (int i = 0; i < cfg.decoyRot; i++) { Integer c = freeCell(true); if (c == null) break; put(Piece.mirror(c % w, c / w, rnd.nextInt(2), true)); }
        for (int i = 0; i < cfg.decoyFixed; i++) { Integer c = freeCell(true); if (c == null) break; put(Piece.mirror(c % w, c / w, rnd.nextInt(2), false)); }
        for (int i = 0; i < cfg.decoyFilters; i++) {
            Integer c = freeCell(true); if (c == null) break;
            put(Piece.filter(c % w, c / w, cfg.filterColors[rnd.nextInt(cfg.filterColors.length)]));
        }
        for (int i = 0; i < cfg.walls; i++) { Integer c = freeCell(false); if (c == null) break; put(Piece.wall(c % w, c / w)); }
        // karıştır
        List<Piece> rot = new ArrayList<>();
        for (Piece p : pathMirrors) if (p.rotatable) rot.add(p);
        if (rot.size() < cfg.minOpt && cfg.blocks == 0 && cfg.doors == 0) return null;
        Collections.shuffle(rot, rnd);
        int flips = Math.min(rot.size(), range(cfg.minOpt, Math.max(cfg.minOpt, cfg.maxOpt)));
        for (int i = 0; i < rot.size(); i++) {
            if (i < flips || rnd.nextDouble() < 0.25) rot.get(i).orient ^= 1;
        }
        Level level = snapshot();
        Board board = new Board(level);
        if (board.rotatableCount() > cfg.maxRot) return null;
        BeamTracer.Result initial = BeamTracer.trace(board);
        if (initial.solved) return null;
        // kapı mekaniği anlamlı olsun: başlangıçta bütün kapılar kapalı olmalı
        for (boolean open : initial.doorOpen) if (open) return null;
        if (cfg.minEffort > 0 && BeamFollower.measure(board, cfg.minEffort).leaves < cfg.minEffort) return null;
        Solver.Solution sol = Solver.solve(board, cfg.stateCap);
        if (!sol.solvable || sol.capped) return null;
        if (sol.optimal < cfg.minOpt || sol.optimal > cfg.maxOpt) return null;
        if (Solver.countReachable(board, cfg.stateCap) < 0) return null;
        level.optimal = sol.optimal;
        return level;
    }

    private Level snapshot() {
        Level l = new Level(w, h);
        for (Piece p : grid) if (p != null) l.pieces.add(p.copy());
        return l;
    }

    private int range(int a, int b) { return b <= a ? a : a + rnd.nextInt(b - a + 1); }

    private void put(Piece p) {
        int c = p.y * w + p.x;
        grid[c] = p;
        mark[c] |= NODE;
    }

    private boolean free(int c) { return grid[c] == null && mark[c] == 0; }

    private boolean crossable(int c, int d) {
        int bit = Dir.horizontal(d) ? H_BIT : V_BIT;
        return grid[c] == null && (mark[c] & (NODE | bit)) == 0;
    }

    private int next(int c, int d) {
        int x = c % w + Dir.DX[d], y = c / w + Dir.DY[d];
        return (x < 0 || y < 0 || x >= w || y >= h) ? -1 : y * w + x;
    }

    private int[] placeSource(int color) {
        for (int t = 0; t < 200; t++) {
            int x, y;
            if (rnd.nextDouble() < 0.7) {
                int side = rnd.nextInt(4);
                if (side == 0) { x = rnd.nextInt(w); y = 0; }
                else if (side == 1) { x = w - 1; y = rnd.nextInt(h); }
                else if (side == 2) { x = rnd.nextInt(w); y = h - 1; }
                else { x = 0; y = rnd.nextInt(h); }
            } else { x = rnd.nextInt(w); y = rnd.nextInt(h); }
            int c = y * w + x;
            if (!free(c)) continue;
            List<Integer> dirs = new ArrayList<>();
            for (int d = 0; d < 4; d++) {
                int n = next(c, d), n2 = n >= 0 ? next(n, d) : -1;
                if (n >= 0 && n2 >= 0 && crossable(n, d)) dirs.add(d);
            }
            if (dirs.isEmpty()) continue;
            int d = dirs.get(rnd.nextInt(dirs.size()));
            put(Piece.source(x, y, d, color));
            return new int[]{c, d};
        }
        return null;
    }

    /** start hücresinden d yönünde gidildiğinde düğüm konabilecek uzaklıklar. */
    private List<Integer> nodeSteps(int start, int d) {
        List<Integer> out = new ArrayList<>();
        int c = start;
        for (int s = 1; ; s++) {
            c = next(c, d);
            if (c < 0) break;
            if (free(c) && s >= 1) out.add(s);
            if (!crossable(c, d)) break;
        }
        return out;
    }

    /** d yönünde ilerlerken ilk karşılaşılan, başka bir ışının kullandığı ayna (yoksa -1 ve adım). */
    private int[] sharedMirrorAhead(int start, int d) {
        int c = start;
        for (int s = 1; ; s++) {
            c = next(c, d);
            if (c < 0) return null;
            Piece p = grid[c];
            if (p != null) {
                if (p.type == Cell.MIRROR && pathMirrors.contains(p) && !sharedUsed.contains(p)) return new int[]{s, c};
                return null;
            }
            if (!crossable(c, d)) return null;
        }
    }

    private int stepCell(int start, int d, int s) {
        int c = start;
        for (int i = 0; i < s; i++) c = next(c, d);
        return c;
    }

    private void markStraight(int start, int d, int s, int src) {
        int bit = Dir.horizontal(d) ? H_BIT : V_BIT;
        int c = start;
        for (int i = 1; i < s; i++) {
            c = next(c, d);
            if (mark[c] == 0) straight.get(src).add(c);
            else straight.get(src).remove(Integer.valueOf(c));
            for (List<Integer> l : straight) if (l != straight.get(src)) l.remove(Integer.valueOf(c));
            mark[c] |= bit;
        }
    }

    /** Uzun adımları biraz daha olası kılan seçim (yollar daha okunaklı olur). */
    private int pickStep(List<Integer> steps) {
        if (steps.size() > 1 && rnd.nextDouble() < 0.6) {
            List<Integer> longer = new ArrayList<>();
            for (int s : steps) if (s >= 2) longer.add(s);
            if (!longer.isEmpty()) return longer.get(rnd.nextInt(longer.size()));
        }
        return steps.get(rnd.nextInt(steps.size()));
    }

    private Piece walk(int start, int d, int color, int turns, int src, boolean filter, boolean prism, Piece mergeInto) {
        int filterAt = filter ? rnd.nextInt(turns + 1) : -1; // bu indeksteki düğümden önce filtre
        int cur = start;
        for (int k = 0; k <= turns; k++) {
            if (k == filterAt) {
                int fc = pickFilter(color);
                if (fc == 0) return null;
                List<Integer> steps = nodeSteps(cur, d);
                if (steps.isEmpty()) return null;
                int s = pickStep(steps);
                int c = stepCell(cur, d, s);
                markStraight(cur, d, s, src);
                put(Piece.filter(c % w, c / w, fc));
                color &= fc;
                cur = c;
            }
            boolean last = k == turns;
            if (last && mergeInto != null) return connect(cur, d, color, mergeInto, src);
            if (last && prism && cfg.prismFilter != 0) {
                List<Integer> steps = nodeSteps(cur, d);
                if (steps.isEmpty()) return null;
                int s = pickStep(steps);
                int c = stepCell(cur, d, s);
                markStraight(cur, d, s, src);
                put(Piece.filter(c % w, c / w, cfg.prismFilter));
                color &= cfg.prismFilter;
                cur = c;
            }
            // başka bir ışının aynasını paylaş: bu ayna artık iki ışını birden yönlendirir
            if (!last && cfg.shareMirrors && rnd.nextDouble() < cfg.shareChance) {
                int[] sh = sharedMirrorAhead(cur, d);
                if (sh != null) {
                    Piece m = grid[sh[1]];
                    int nd = Dir.reflect(d, m.orient);
                    int after = next(sh[1], nd);
                    if (after >= 0 && (free(after) || crossable(after, nd))) {
                        markStraight(cur, d, sh[0], src);
                        sharedUsed.add(m);
                        cur = sh[1]; d = nd;
                        continue;
                    }
                }
            }
            List<Integer> steps = nodeSteps(cur, d);
            Collections.shuffle(steps, rnd);
            boolean placed = false;
            for (int attempt = 0; attempt < steps.size() && !placed; attempt++) {
                int s = attempt == 0 ? pickStep(steps) : steps.get(attempt);
                int c = stepCell(cur, d, s);
                if (last) {
                    markStraight(cur, d, s, src);
                    if (prism) {
                        put(Piece.prism(c % w, c / w));
                        int[] comps = {LightColor.RED, LightColor.GREEN, LightColor.BLUE};
                        int[] outs = {Dir.left(d), d, Dir.right(d)};
                        Piece lastT = null;
                        for (int i = 0; i < 3; i++) {
                            if ((color & comps[i]) == 0) continue;
                            int bt = range(cfg.branchTurnsMin, cfg.branchTurnsMax);
                            lastT = walk(c, outs[i], comps[i], bt, src, false, false, null);
                            if (lastT == null) return null;
                        }
                        return lastT;
                    }
                    Piece t = Piece.target(c % w, c / w, color);
                    put(t);
                    return t;
                }
                // ayna: sola veya sağa dönüş
                int[] turnsOpt = rnd.nextBoolean() ? new int[]{Dir.left(d), Dir.right(d)} : new int[]{Dir.right(d), Dir.left(d)};
                for (int nd : turnsOpt) {
                    int after = next(c, nd);
                    if (after < 0 || !(free(after) || crossable(after, nd))) continue;
                    markStraight(cur, d, s, src);
                    Piece m = Piece.mirror(c % w, c / w, Dir.orientFor(d, nd), true);
                    put(m);
                    pathMirrors.add(m);
                    cur = c; d = nd;
                    placed = true;
                    break;
                }
            }
            if (!placed) return null;
        }
        return null;
    }

    private int pickFilter(int color) {
        List<Integer> ok = new ArrayList<>();
        for (int f : cfg.filterColors) { int r = color & f; if (r != 0 && r != color) ok.add(f); }
        return ok.isEmpty() ? 0 : ok.get(rnd.nextInt(ok.size()));
    }

    /** Işını mevcut bir hedefe bağlar (en fazla bir ek ayna ile); hedefin rengine katkı ekler. */
    private Piece connect(int cur, int d, int color, Piece target, int src) {
        int tc = target.y * w + target.x;
        // doğrudan isabet
        int c = cur;
        for (int s = 1; ; s++) {
            c = next(c, d);
            if (c < 0) break;
            if (c == tc) { markStraight(cur, d, s, src); target.color |= color; return target; }
            if (!crossable(c, d)) break;
        }
        List<int[]> options = new ArrayList<>();
        for (int s : nodeSteps(cur, d)) {
            int m = stepCell(cur, d, s);
            for (int nd : new int[]{Dir.left(d), Dir.right(d)}) {
                int x = m;
                for (int k = 1; ; k++) {
                    x = next(x, nd);
                    if (x < 0) break;
                    if (x == tc) { options.add(new int[]{s, nd, k}); break; }
                    if (!crossable(x, nd)) break;
                }
            }
        }
        if (options.isEmpty()) return null;
        int[] o = options.get(rnd.nextInt(options.size()));
        int m = stepCell(cur, d, o[0]);
        markStraight(cur, d, o[0], src);
        Piece mir = Piece.mirror(m % w, m / w, Dir.orientFor(d, o[1]), true);
        put(mir);
        pathMirrors.add(mir);
        markStraight(m, o[1], o[2], src);
        target.color |= color;
        return target;
    }

    private Integer takeStraight(int src) {
        List<Integer> l = straight.get(src);
        if (l.isEmpty()) return null;
        Integer c = l.remove(rnd.nextInt(l.size()));
        return c;
    }

    private Integer takeStraightAny() {
        List<Integer> all = new ArrayList<>();
        for (int s = 0; s < straight.size(); s++) if (!straight.get(s).isEmpty()) all.add(s);
        if (all.isEmpty()) return null;
        return takeStraight(all.get(rnd.nextInt(all.size())));
    }

    /** Yol dışında boş hücre; nearPath ise yola komşu hücreler tercih edilir. */
    private Integer freeCell(boolean nearPath) {
        List<Integer> near = new ArrayList<>(), any = new ArrayList<>();
        for (int c = 0; c < w * h; c++) {
            if (!free(c)) continue;
            any.add(c);
            for (int d = 0; d < 4; d++) {
                int n = next(c, d);
                if (n >= 0 && (mark[n] & (H_BIT | V_BIT)) != 0) { near.add(c); break; }
            }
        }
        if (any.isEmpty()) return null;
        List<Integer> pool = nearPath && !near.isEmpty() && rnd.nextDouble() < 0.8 ? near : any;
        return pool.get(rnd.nextInt(pool.size()));
    }
}
