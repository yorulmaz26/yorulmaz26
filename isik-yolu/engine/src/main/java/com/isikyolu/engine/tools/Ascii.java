package com.isikyolu.engine.tools;

import com.isikyolu.engine.BeamTracer;
import com.isikyolu.engine.Board;
import com.isikyolu.engine.Cell;
import com.isikyolu.engine.Level;

/** Bölümü metin olarak çizer (hata ayıklama ve raporlar için). */
public final class Ascii {
    private Ascii() {}

    public static String render(Level l) {
        Board b = new Board(l);
        BeamTracer.Result r = BeamTracer.trace(b);
        StringBuilder sb = new StringBuilder();
        for (int y = 0; y < l.height; y++) {
            for (int x = 0; x < l.width; x++) {
                int c = b.cell(x, y);
                char ch;
                switch (b.typeAt(c)) {
                    case Cell.WALL: ch = '#'; break;
                    case Cell.SOURCE: ch = "^>v<".charAt(b.dirAt(c)); break;
                    case Cell.TARGET: ch = r.targetSatisfied(b, c) ? '@' : 'O'; break;
                    case Cell.MIRROR: ch = b.orientAt(c) == 0 ? '/' : '\\'; break;
                    case Cell.FILTER: ch = (char) ('0' + b.colorAt(c)); break;
                    case Cell.PRISM: ch = 'P'; break;
                    case Cell.BLOCK: ch = 'B'; break;
                    case Cell.KEY: ch = 'k'; break;
                    case Cell.DOOR: ch = r.doorOpen[b.linkAt(c)] ? 'd' : 'D'; break;
                    default: ch = r.lit[c] != 0 ? '·' : '.';
                }
                sb.append(ch).append(b.typeAt(c) == Cell.MIRROR && !b.isRotatable(c) ? '!' : ' ');
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    public static void main(String[] args) throws Exception {
        com.isikyolu.engine.LevelPack p = com.isikyolu.engine.LevelPack.parse(new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(args[0])), "UTF-8"));
        for (int i = 1; i < args.length; i++) {
            Level l = p.get(Integer.parseInt(args[i]));
            System.out.println("== " + l.id + " " + l.name + " optimum " + l.optimal);
            System.out.println(render(l));
        }
    }
}
