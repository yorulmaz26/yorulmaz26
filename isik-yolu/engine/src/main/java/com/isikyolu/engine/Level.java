package com.isikyolu.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Veri odaklı bölüm tanımı. JSON'a yazılır/okunur; görsel sistemden bağımsızdır. */
public final class Level {
    public int id;
    public int chapter;
    public String name = "";
    public int width, height;
    /** Çözücünün bulduğu en az hamle sayısı. */
    public int optimal;
    public final List<Piece> pieces = new ArrayList<>();

    public Level() {}

    public Level(int width, int height) { this.width = width; this.height = height; }

    public Level add(Piece p) { pieces.add(p); return this; }

    public Level copy() {
        Level l = new Level(width, height);
        l.id = id; l.chapter = chapter; l.name = name; l.optimal = optimal;
        for (Piece p : pieces) l.pieces.add(p.copy());
        return l;
    }

    public int count(int type) {
        int n = 0;
        for (Piece p : pieces) if (p.type == type) n++;
        return n;
    }

    /** Kopya tespiti için konumdan bağımsız olmayan kanonik imza. */
    public String signature() {
        char[] g = new char[width * height];
        java.util.Arrays.fill(g, '.');
        for (Piece p : pieces) {
            char c = Cell.code(p.type).charAt(0);
            g[p.y * width + p.x] = c;
        }
        return width + "x" + height + ":" + new String(g);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("chapter", chapter);
        m.put("name", name);
        m.put("w", width);
        m.put("h", height);
        m.put("optimal", optimal);
        List<Object> ps = new ArrayList<>();
        for (Piece p : pieces) {
            Map<String, Object> pm = new LinkedHashMap<>();
            pm.put("t", Cell.code(p.type));
            pm.put("x", p.x);
            pm.put("y", p.y);
            switch (p.type) {
                case Cell.SOURCE: pm.put("d", p.dir); pm.put("c", p.color); break;
                case Cell.TARGET:
                case Cell.FILTER: pm.put("c", p.color); break;
                case Cell.MIRROR: pm.put("o", p.orient); pm.put("r", p.rotatable); break;
                case Cell.KEY:
                case Cell.DOOR: pm.put("l", p.link); break;
                default: break;
            }
            ps.add(pm);
        }
        m.put("pieces", ps);
        return m;
    }

    @SuppressWarnings("unchecked")
    public static Level fromMap(Map<String, Object> m) {
        Level l = new Level(Json.getInt(m, "w", 0), Json.getInt(m, "h", 0));
        l.id = Json.getInt(m, "id", 0);
        l.chapter = Json.getInt(m, "chapter", 0);
        l.name = Json.getString(m, "name", "");
        l.optimal = Json.getInt(m, "optimal", 0);
        if (l.width <= 0 || l.height <= 0) throw new IllegalArgumentException("Geçersiz bölüm boyutu");
        for (Object o : (List<Object>) m.get("pieces")) {
            Map<String, Object> pm = (Map<String, Object>) o;
            Piece p = new Piece(Cell.fromCode(Json.getString(pm, "t", "E")), Json.getInt(pm, "x", 0), Json.getInt(pm, "y", 0));
            p.dir = Json.getInt(pm, "d", 0);
            p.color = Json.getInt(pm, "c", 0);
            p.orient = Json.getInt(pm, "o", 0);
            p.rotatable = Json.getBool(pm, "r", false);
            p.link = Json.getInt(pm, "l", 0);
            if (p.x < 0 || p.y < 0 || p.x >= l.width || p.y >= l.height)
                throw new IllegalArgumentException("Nesne ızgara dışında: bölüm " + l.id);
            l.pieces.add(p);
        }
        return l;
    }

    public String toJson() { return Json.write(toMap()); }

    @SuppressWarnings("unchecked")
    public static Level fromJson(String s) { return fromMap((Map<String, Object>) Json.parse(s)); }
}
