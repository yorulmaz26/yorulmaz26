package com.isikyolu.engine.tools;

import com.isikyolu.engine.BeamTracer;
import com.isikyolu.engine.Board;
import com.isikyolu.engine.Cell;
import com.isikyolu.engine.Chapters;
import com.isikyolu.engine.Dir;
import com.isikyolu.engine.GameSession;
import com.isikyolu.engine.Level;
import com.isikyolu.engine.LevelPack;
import com.isikyolu.engine.Lives;
import com.isikyolu.engine.Move;
import com.isikyolu.engine.Solver;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Bir bölümü komut satırından oynatır (denetim ve hata ayıklama için).
 * Kullanım: Play &lt;levels.json&gt; &lt;id&gt; [hamle ...] [--solution]
 * Hamle: r:x,y (aynayı döndür) | p:x,y,Y (bloğu it; Y = U/R/D/L yukarı/sağ/aşağı/sol)
 */
public final class Play {
    private Play() {}

    static String col(int c) {
        switch (c) { case 1: return "R"; case 2: return "G"; case 4: return "B"; case 3: return "Y"; case 5: return "M"; case 6: return "C"; case 7: return "W"; default: return "-"; }
    }

    static String token(Board b, BeamTracer.Result r, int c) {
        switch (b.typeAt(c)) {
            case Cell.WALL: return "###";
            case Cell.SOURCE: return "S" + "^>v<".charAt(b.dirAt(c)) + col(b.colorAt(c));
            case Cell.TARGET: return (r.received[c] == b.colorAt(c) ? "@" : "T") + ":" + col(b.colorAt(c));
            case Cell.MIRROR: return " " + (b.orientAt(c) == 0 ? "/" : "\\") + (b.isRotatable(c) ? " " : "!");
            case Cell.FILTER: return "F:" + col(b.colorAt(c));
            case Cell.PRISM: return " P ";
            case Cell.BLOCK: return "[B]";
            case Cell.KEY: return "K" + b.linkAt(c) + (r.keyLit[b.linkAt(c)] ? "+" : "-");
            case Cell.DOOR: return "D" + b.linkAt(c) + (r.doorOpen[b.linkAt(c)] ? "_" : "#");
            default: return r.lit[c] != 0 ? " " + col(r.lit[c]).toLowerCase() + " " : " . ";
        }
    }

    public static String render(Board b) {
        BeamTracer.Result r = BeamTracer.trace(b);
        StringBuilder sb = new StringBuilder("     ");
        for (int x = 0; x < b.width; x++) sb.append(String.format(" x%-2d", x));
        sb.append('\n');
        for (int y = 0; y < b.height; y++) {
            sb.append(String.format("y%-3d ", y));
            for (int x = 0; x < b.width; x++) sb.append(token(b, r, b.cell(x, y))).append(' ');
            sb.append('\n');
        }
        sb.append("Hedefler: ");
        for (int t : b.targetCells())
            sb.append(String.format("(%d,%d) ister %s alıyor %s%s; ", t % b.width, t / b.width, col(b.colorAt(t)), col(r.received[t]), r.received[t] == b.colorAt(t) ? " ✓" : ""));
        sb.append("\nYanan hedef: ").append(r.satisfied).append('/').append(r.totalTargets).append(r.solved ? "  → ÇÖZÜLDÜ" : "");
        return sb.toString();
    }

    public static void main(String[] a) throws Exception {
        LevelPack p = LevelPack.parse(new String(Files.readAllBytes(Paths.get(a[0])), StandardCharsets.UTF_8));
        int id = Integer.parseInt(a[1]);
        Level l = p.get(id);
        boolean sol = false;
        GameSession g = new GameSession(l, true);
        System.out.printf("Bölüm %d · %s · %s · %dx%d · en az hamle %d · hamle hakkı %d · mantık bölümü: %s%n",
                l.id, l.name, Chapters.topic(l.chapter), l.width, l.height, l.optimal, Lives.moveBudget(l.optimal), Chapters.requiresLogic(l.id) ? "evet" : "hayır");
        System.out.println("Açıklama: S=kaynak(yön,renk) T=hedef(istenen renk) @=yanan hedef /,\\=döner ayna /!,\\!=sabit ayna F=filtre P=prizma(kırmızı sola,yeşil düz,mavi sağa; gidiş yönüne göre)");
        System.out.println("          [B]=itilebilir blok Kn±=anahtar(ışık alıyor +) Dn#=kapalı kapı Dn_=açık kapı ###=duvar; küçük harf=o hücreden geçen ışık rengi");
        System.out.println("Renkler: R kırmızı G yeşil B mavi Y sarı(R+G) M pembe(R+B) C camgöbeği(G+B) W beyaz");
        System.out.println(render(g.board()));
        for (int i = 2; i < a.length; i++) {
            String m = a[i];
            if (m.equals("--solution")) { sol = true; continue; }
            boolean ok;
            String[] q = m.substring(2).split(",");
            int x = Integer.parseInt(q[0].trim()), y = Integer.parseInt(q[1].trim());
            if (m.startsWith("r:")) ok = g.tap(x, y);
            else {
                int d = "URDL".indexOf(q[2].trim().toUpperCase());
                ok = d >= 0 && g.drag(x, y, d);
            }
            System.out.printf("%nHamle %s → %s (hamle %d, kalan hak %d)%n", m, ok ? "uygulandı" : "GEÇERSİZ", g.moves(), g.budgetLeft());
            System.out.println(render(g.board()));
            if (g.isSolved()) { System.out.println("ÇÖZÜLDÜ · yıldız " + g.stars()); break; }
            if (g.isOutOfMoves()) { System.out.println("HAMLE HAKKI BİTTİ"); break; }
        }
        if (sol) {
            Solver.Solution s = Solver.solve(new Board(l), 500_000);
            StringBuilder sb = new StringBuilder("En kısa çözüm (" + s.optimal + "): ");
            for (Move mv : s.moves) {
                int x = mv.cell % l.width, y = mv.cell / l.width;
                sb.append(mv.kind == Move.ROTATE ? "r:" + x + "," + y : "p:" + x + "," + y + "," + "URDL".charAt(mv.dir)).append(' ');
            }
            System.out.println(sb);
        }
    }
}
