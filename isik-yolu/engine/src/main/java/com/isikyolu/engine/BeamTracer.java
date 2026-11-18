package com.isikyolu.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Işın simülasyonu. Koordinatlar yarım hücre biriminde: hücre merkezi (2x+1, 2y+1),
 * hücre kenarı çift sayıdır. Sonsuz döngüler (hücre, yön, renk) ziyaret tablosuyla engellenir.
 * Kapılar en küçük sabit nokta ile çözülür: tüm kapılar kapalı başlar, anahtarı ışık alan
 * kapılar açılır ve durum değişmeyene kadar yeniden izlenir (monoton olduğu için sonlanır).
 */
public final class BeamTracer {

    /** Çizim için tek bir düz ışın parçası. */
    public static final class Segment {
        public final int x1, y1, x2, y2, color;
        /** Kaynağa olan yol uzaklığı (yarım hücre) — büyüme animasyonu için. */
        public final int startDist;

        Segment(int x1, int y1, int x2, int y2, int color, int startDist) {
            this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2; this.color = color; this.startDist = startDist;
        }

        public int length() { return Math.abs(x2 - x1) + Math.abs(y2 - y1); }
    }

    public static final class Result {
        public final List<Segment> segments = new ArrayList<>();
        /** Her hücreye ulaşan renklerin birleşimi. */
        public int[] lit;
        /** Hedeflere ulaşan renkler (hücre indisine göre). */
        public int[] received;
        public boolean[] keyLit;
        public boolean[] doorOpen;
        public int satisfied, totalTargets;
        public boolean solved;
        public int maxDist;
        public boolean loopDetected;

        public boolean targetSatisfied(Board b, int cell) { return received[cell] == b.color[cell]; }
    }

    // yeniden kullanılan çalışma alanı
    private int[] stack = new int[256];
    private int sp;
    private boolean[] visited;
    private final Result scratch = new Result();

    /** Tam izleme (çizim parçalarıyla). */
    public static Result trace(Board b) { return new BeamTracer().run(b, true); }

    /** Yalnızca çözüldü mü? (çözücü için, ayırma yapmadan). */
    public boolean isSolved(Board b) { return run(b, false).solved; }

    public Result run(Board b, boolean withSegments) {
        int n = b.width * b.height;
        Result r = withSegments ? new Result() : scratch;
        if (r.lit == null || r.lit.length != n) { r.lit = new int[n]; r.received = new int[n]; }
        int links = b.maxLink + 1;
        if (r.keyLit == null || r.keyLit.length != links) { r.keyLit = new boolean[links]; r.doorOpen = new boolean[links]; }
        Arrays.fill(r.doorOpen, false);
        if (visited == null || visited.length != n * 32) visited = new boolean[n * 32];
        for (int iter = 0; iter < links + 1; iter++) {
            pass(b, r, withSegments);
            boolean changed = false;
            for (int k = 0; k < links; k++) if (r.keyLit[k] && !r.doorOpen[k]) { r.doorOpen[k] = true; changed = true; }
            if (!changed) break;
        }
        r.totalTargets = b.targets.length;
        r.satisfied = 0;
        for (int t : b.targets) if (r.received[t] == b.color[t]) r.satisfied++;
        r.solved = r.totalTargets > 0 && r.satisfied == r.totalTargets;
        return r;
    }

    private void push(int cell, int d, int col, int dist) {
        if (sp + 4 > stack.length) stack = Arrays.copyOf(stack, stack.length * 2);
        stack[sp++] = cell; stack[sp++] = d; stack[sp++] = col; stack[sp++] = dist;
    }

    private void pass(Board b, Result r, boolean seg) {
        Arrays.fill(r.lit, 0);
        Arrays.fill(r.received, 0);
        Arrays.fill(r.keyLit, false);
        Arrays.fill(visited, false);
        r.segments.clear();
        r.maxDist = 0;
        r.loopDetected = false;
        final int w = b.width, h = b.height;
        sp = 0;
        for (int s : b.sources) {
            r.lit[s] |= b.color[s];
            push(s, b.dir[s], b.color[s], 0);
        }
        while (sp > 0) {
            int dist = stack[--sp], col = stack[--sp], d = stack[--sp], start = stack[--sp];
            int sx = 2 * (start % w) + 1, sy = 2 * (start / w) + 1;
            int cx = start % w, cy = start / w;
            while (true) {
                int nx = cx + Dir.DX[d], ny = cy + Dir.DY[d];
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) {
                    addSeg(r, seg, sx, sy, 2 * cx + 1 + Dir.DX[d], 2 * cy + 1 + Dir.DY[d], col, dist);
                    break;
                }
                int nc = ny * w + nx;
                int vk = ((nc << 2) | d) * 8 + col;
                int ex = 2 * nx + 1, ey = 2 * ny + 1;
                if (visited[vk]) {
                    r.loopDetected = true;
                    addSeg(r, seg, sx, sy, ex, ey, col, dist);
                    break;
                }
                visited[vk] = true;
                int t = b.type[nc];
                if (t == Cell.DOOR && r.doorOpen[b.link[nc]]) t = Cell.EMPTY;
                if (t == Cell.KEY) { r.keyLit[b.link[nc]] = true; t = Cell.EMPTY; }
                if (t == Cell.EMPTY) {
                    r.lit[nc] |= col;
                    cx = nx; cy = ny;
                    continue;
                }
                if (t == Cell.WALL || t == Cell.BLOCK || t == Cell.SOURCE || t == Cell.DOOR) {
                    addSeg(r, seg, sx, sy, 2 * cx + 1 + Dir.DX[d], 2 * cy + 1 + Dir.DY[d], col, dist);
                    r.lit[nc] |= col;
                    break;
                }
                int nd = dist + Math.abs(ex - sx) + Math.abs(ey - sy);
                addSeg(r, seg, sx, sy, ex, ey, col, dist);
                r.lit[nc] |= col;
                if (t == Cell.TARGET) {
                    r.received[nc] |= col;
                } else if (t == Cell.MIRROR) {
                    push(nc, Dir.reflect(d, b.orient[nc]), col, nd);
                } else if (t == Cell.FILTER) {
                    int fc = col & b.color[nc];
                    if (fc != 0) push(nc, d, fc, nd);
                } else if (t == Cell.PRISM) {
                    // Prizma rengi ayrıştırır: kırmızı sola, yeşil düz, mavi sağa sapar.
                    if ((col & LightColor.RED) != 0) push(nc, Dir.left(d), LightColor.RED, nd);
                    if ((col & LightColor.GREEN) != 0) push(nc, d, LightColor.GREEN, nd);
                    if ((col & LightColor.BLUE) != 0) push(nc, Dir.right(d), LightColor.BLUE, nd);
                }
                break;
            }
        }
    }

    private static void addSeg(Result r, boolean seg, int x1, int y1, int x2, int y2, int col, int dist) {
        int end = dist + Math.abs(x2 - x1) + Math.abs(y2 - y1);
        if (end > r.maxDist) r.maxDist = end;
        if (seg && (x1 != x2 || y1 != y2)) r.segments.add(new Segment(x1, y1, x2, y2, col, dist));
    }
}
