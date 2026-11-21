package com.isikyolu.engine.tools;

import com.isikyolu.engine.BeamFollower;
import com.isikyolu.engine.BeamTracer;
import com.isikyolu.engine.Board;
import com.isikyolu.engine.Cell;
import com.isikyolu.engine.Chapters;
import com.isikyolu.engine.DailyPuzzle;
import com.isikyolu.engine.Dir;
import com.isikyolu.engine.GameSession;
import com.isikyolu.engine.Level;
import com.isikyolu.engine.LevelPack;
import com.isikyolu.engine.Lives;
import com.isikyolu.engine.Move;
import com.isikyolu.engine.Piece;
import com.isikyolu.engine.Solver;
import com.isikyolu.engine.StarRating;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Bütün bölümleri mekanik olarak "oynar" ve denetler. Her bölümde başlangıçtan ulaşılabilen BÜTÜN tahta
 * durumları gezilir. HATA = oyunu bozan sorun, UYARI = tasarım kusuru, BİLGİ = istatistik.
 * Kullanım: Audit &lt;levels.json&gt; [rapor.txt] [günlükGün]
 */
public final class Audit {
    private Audit() {}

    public static final class Finding {
        public final int level; public final String severity, code, detail;
        Finding(int level, String severity, String code, String detail) { this.level = level; this.severity = severity; this.code = code; this.detail = detail; }
        @Override public String toString() { return severity + " bölüm " + level + " [" + code + "] " + detail; }
    }

    public static final class Report {
        public final List<Finding> findings = new ArrayList<>();
        public final StringBuilder table = new StringBuilder();
        public int levels, states;
        public long ms;
        void add(int l, String sev, String code, String d) { findings.add(new Finding(l, sev, code, d)); }
        public long count(String sev) { return findings.stream().filter(f -> f.severity.equals(sev)).count(); }
    }

    static String pos(Board b, int c) { return "(" + c % b.width + "," + c / b.width + ")"; }

    /** Işın parçalarının kurallara uyduğunu doğrular: düz, ızgara içinde, engellerin içinden geçmeyen. */
    static String segmentProblem(Board b, BeamTracer.Result r) {
        int W = 2 * b.width, H = 2 * b.height;
        for (BeamTracer.Segment s : r.segments) {
            if (s.x1 != s.x2 && s.y1 != s.y2) return "çapraz ışın parçası";
            if (s.x1 < 0 || s.y1 < 0 || s.x2 < 0 || s.y2 < 0 || s.x1 > W || s.x2 > W || s.y1 > H || s.y2 > H) return "ızgara dışına taşan ışın";
            // parçanın içinden geçtiği hücre merkezleri (başlangıç ve bitiş hariç) engel olmamalı
            int dx = Integer.signum(s.x2 - s.x1), dy = Integer.signum(s.y2 - s.y1);
            int x = s.x1, y = s.y1;
            while (x != s.x2 || y != s.y2) {
                x += dx; y += dy;
                if ((x & 1) == 1 && (y & 1) == 1 && !(x == s.x2 && y == s.y2)) {
                    int c = (y / 2) * b.width + x / 2;
                    int t = b.typeAt(c);
                    boolean blocked = t == Cell.WALL || t == Cell.BLOCK || t == Cell.SOURCE || t == Cell.TARGET
                            || t == Cell.MIRROR || t == Cell.FILTER || t == Cell.PRISM
                            || (t == Cell.DOOR && !r.doorOpen[b.linkAt(c)]);
                    if (blocked) return "ışın " + pos(b, c) + " hücresindeki " + Cell.code(t) + " nesnesinin içinden geçiyor";
                }
            }
        }
        return null;
    }

    public static Report run(LevelPack pack, int dailyDays) {
        long t0 = System.currentTimeMillis();
        Report rep = new Report();
        Set<String> sigs = new HashSet<>();
        int prevWorldMax = 0;
        for (Level l : pack.levels) {
            rep.levels++;
            int id = l.id;
            Board start;
            try { start = new Board(l); } catch (RuntimeException e) { rep.add(id, "HATA", "kurulum", e.getMessage()); continue; }
            if (!sigs.add(l.signature())) rep.add(id, "HATA", "kopya", "aynı düzen başka bir bölümde var");
            BeamTracer.Result r0 = BeamTracer.trace(start);
            if (r0.solved) rep.add(id, "HATA", "hazır-çözüm", "bölüm başlangıçta çözülmüş");
            for (Piece p : l.pieces) if (p.x < 0 || p.y < 0 || p.x >= l.width || p.y >= l.height) rep.add(id, "HATA", "sınır", "ızgara dışı nesne");
            if (l.width > 8 || l.height > 11) rep.add(id, "UYARI", "boyut", "tahta telefona göre büyük: " + l.width + "x" + l.height);

            // ---- tam durum uzayı ----
            Board work = start.copy();
            BeamTracer tr = new BeamTracer();
            Map<Long, Integer> dist = new HashMap<>();
            ArrayDeque<Long> q = new ArrayDeque<>();
            long k0 = work.stateKey();
            dist.put(k0, 0); q.add(k0);
            List<Long> goals = new ArrayList<>();
            int irreversible = 0, segBad = 0;
            String segMsg = null;
            int[] litUnion = new int[l.width * l.height];
            boolean[] keyEverLit = new boolean[16];
            Random rnd = new Random(id);
            while (!q.isEmpty()) {
                long k = q.poll();
                work.applyKey(k);
                BeamTracer.Result rr = tr.run(work, false);
                for (int c = 0; c < litUnion.length; c++) litUnion[c] |= rr.lit[c];
                for (int i = 0; i < rr.keyLit.length && i < 16; i++) keyEverLit[i] |= rr.keyLit[i];
                if (rr.solved) goals.add(k);
                // çizim parçaları: örneklem (her durumu çizmek gereksiz pahalı)
                if (dist.size() < 4000 || rnd.nextInt(20) == 0) {
                    String sp = segmentProblem(work, BeamTracer.trace(work));
                    if (sp != null) { segBad++; segMsg = sp; }
                }
                int d = dist.get(k);
                for (Move m : work.legalMoves()) {
                    work.apply(m);
                    long nk = work.stateKey();
                    // tersinirlik: hamlenin tersi aynı duruma dönmeli
                    Move inv = m.kind == Move.ROTATE ? m
                            : Move.push(work.cell(m.cell % work.width + Dir.DX[m.dir], m.cell / work.width + Dir.DY[m.dir]), Dir.opposite(m.dir));
                    work.apply(inv);
                    if (work.stateKey() != k) irreversible++;
                    work.applyKey(k);
                    if (!dist.containsKey(nk)) { dist.put(nk, d + 1); q.add(nk); }
                }
            }
            rep.states += dist.size();
            if (irreversible > 0) rep.add(id, "HATA", "tersinmez", irreversible + " hamle geri döndürülemiyor (çıkmaz durum riski)");
            if (segBad > 0) rep.add(id, "HATA", "ışın-kuralı", segBad + " durumda: " + segMsg);
            if (goals.isEmpty()) { rep.add(id, "HATA", "çözümsüz", "hiçbir durumda bütün hedefler yanmıyor"); continue; }
            int opt = dist.get(goals.stream().min((a, b) -> dist.get(a) - dist.get(b)).get());
            if (opt != l.optimal) rep.add(id, "HATA", "optimum", "kayıtlı " + l.optimal + ", gerçek " + opt);
            int budget = Lives.moveBudget(l.optimal);
            if (opt > budget) rep.add(id, "HATA", "hak-yetmez", "en kısa çözüm " + opt + " > hak " + budget);
            if (dist.size() > 150_000) rep.add(id, "HATA", "ipucu-sınırı", "durum sayısı " + dist.size() + " ipucu sınırını (150000) aşıyor");

            // hedeflerden geriye mesafe: oyuncu en uzak duruma gitse bile çözüm uzaklığı
            Map<Long, Integer> back = new HashMap<>();
            ArrayDeque<Long> bq = new ArrayDeque<>();
            for (long g : goals) { back.put(g, 0); bq.add(g); }
            while (!bq.isEmpty()) {
                long k = bq.poll();
                work.applyKey(k);
                int d = back.get(k);
                for (Move m : work.legalMoves()) {
                    work.apply(m);
                    long nk = work.stateKey();
                    work.applyKey(k);
                    if (!back.containsKey(nk)) { back.put(nk, d + 1); bq.add(nk); }
                }
            }
            if (back.size() != dist.size()) rep.add(id, "HATA", "çıkmaz", (dist.size() - back.size()) + " durumdan çözüme dönülemiyor");
            int worst = back.values().stream().mapToInt(Integer::intValue).max().orElse(0);

            // ---- çözümü oynatma: limitli oturum, ipuçları, yıldız ----
            GameSession g = new GameSession(l, true);
            Solver.Solution sol = Solver.solve(new Board(l), 500_000);
            for (Move m : sol.moves) if (!g.play(m)) { rep.add(id, "HATA", "oynatma", "çözüm hamlesi reddedildi: " + m); break; }
            if (!g.isSolved()) rep.add(id, "HATA", "oynatma", "çözüm oynatılınca bölüm bitmedi");
            else if (g.stars() != 3) rep.add(id, "HATA", "yıldız", "optimum çözüm 3 yıldız vermedi");
            // rastgele 3 farklı yanlış başlangıçtan ipuçlarıyla çözme
            for (int trial = 0; trial < 3; trial++) {
                GameSession h = new GameSession(l, false);
                for (int i = 0; i < 2 + trial * 3; i++) { List<Move> ms = h.board().legalMoves(); if (ms.isEmpty() || h.isSolved()) break; h.play(ms.get(rnd.nextInt(ms.size()))); }
                int guard = 0;
                while (!h.isSolved() && guard++ < 200) { Move hm = h.hint(200_000); if (hm == null || !h.play(hm)) { rep.add(id, "HATA", "ipucu", "ipucu çözüme götürmedi"); break; } }
            }

            // ---- tasarım denetimi (çözüm durumunda) ----
            Board solved = new Board(l);
            for (Move m : sol.moves) solved.apply(m);
            for (int s : solved.sourceCells()) {
                Level without = solved.toLevel(l);
                without.pieces.removeIf(pc -> pc.type == Cell.SOURCE && pc.y * l.width + pc.x == s);
                without.pieces.add(Piece.wall(s % l.width, s / l.width));
                if (BeamTracer.trace(new Board(without)).solved) rep.add(id, "UYARI", "gereksiz-kaynak", "kaynak " + pos(solved, s) + " olmadan da çözülüyor");
            }
            for (int c = 0; c < l.width * l.height; c++) {
                if (start.typeAt(c) == Cell.DOOR) {
                    if (!keyEverLit[start.linkAt(c)]) rep.add(id, "HATA", "kapı", "kapı " + pos(start, c) + " hiçbir durumda açılamıyor");
                }
            }
            int deadDecoy = 0;
            for (int c = 0; c < litUnion.length; c++) if (start.isRotatable(c) && litUnion[c] == 0) deadDecoy++;
            if (deadDecoy > 0) rep.add(id, "UYARI", "ölü-yem", deadDecoy + " döner ayna hiçbir durumda ışık almıyor (oyuncu boşa dokunup hak harcar)");
            // başlangıçta zaten yanan hedefler
            int pre = 0;
            for (int t : start.targetCells()) if (r0.received[t] == start.colorAt(t)) pre++;
            if (pre > 0) rep.add(id, "BİLGİ", "hazır-hedef", pre + "/" + start.targetCells().length + " hedef başlangıçta yanıyor");
            // kaynak doğrudan dışarı bakıyor mu (yönü tahta dışına)
            for (int s : start.sourceCells()) {
                int nx = s % l.width + Dir.DX[start.dirAt(s)], ny = s / l.width + Dir.DY[start.dirAt(s)];
                if (!start.inside(nx, ny)) rep.add(id, "HATA", "kaynak-yönü", "kaynak " + pos(start, s) + " tahtanın dışına bakıyor");
            }
            int effort = BeamFollower.measure(new Board(l), 5000).leaves;
            if (Chapters.requiresLogic(id) && effort < 5) rep.add(id, "UYARI", "mantık-zayıf", "mantık bölümü ama karar ağacı " + effort);
            int w = Chapters.worldOf(id);
            rep.table.append(String.format("%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d%n", id, w, l.optimal, budget, dist.size(), goals.size(), worst, effort));
        }
        // ---- günlük bulmacalar ----
        int base = DailyPuzzle.today();
        for (int d = 0; d < dailyDays; d++) {
            Level dl = DailyPuzzle.generate(base + d);
            Solver.Solution s = Solver.solve(new Board(dl), 200_000);
            if (!s.solvable) rep.add(-(base + d), "HATA", "günlük", DailyPuzzle.keyFor(base + d) + " günlük bulmacası çözülemiyor");
            else if (s.optimal != dl.optimal) rep.add(-(base + d), "HATA", "günlük", DailyPuzzle.keyFor(base + d) + " optimum uyuşmuyor");
            if (BeamTracer.trace(new Board(dl)).solved) rep.add(-(base + d), "HATA", "günlük", DailyPuzzle.keyFor(base + d) + " başlangıçta çözülü");
        }
        rep.ms = System.currentTimeMillis() - t0;
        return rep;
    }

    public static void main(String[] a) throws Exception {
        LevelPack p = LevelPack.parse(new String(Files.readAllBytes(Paths.get(a[0])), StandardCharsets.UTF_8));
        int days = a.length > 2 ? Integer.parseInt(a[2]) : 60;
        Report r = run(p, days);
        StringBuilder sb = new StringBuilder();
        sb.append("Bölüm: ").append(r.levels).append(" · gezilen durum: ").append(r.states).append(" · süre: ").append(r.ms / 1000).append(" sn · günlük: ").append(days).append(" gün\n");
        sb.append("HATA: ").append(r.count("HATA")).append(" · UYARI: ").append(r.count("UYARI")).append(" · BİLGİ: ").append(r.count("BİLGİ")).append('\n');
        for (Finding f : r.findings) sb.append(f).append('\n');
        sb.append("\nid\tdünya\toptimum\thak\tdurum\tçözümDurumu\ten-uzak\tkararAğacı\n").append(r.table);
        String out = sb.toString();
        if (a.length > 1) Files.write(Paths.get(a[1]), out.getBytes(StandardCharsets.UTF_8));
        System.out.println(out.substring(0, Math.min(out.length(), 6000)));
    }
}
