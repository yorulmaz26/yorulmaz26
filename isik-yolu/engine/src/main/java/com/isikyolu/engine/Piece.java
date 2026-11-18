package com.isikyolu.engine;

/** Bölüm verisindeki tek bir nesne. */
public final class Piece {
    public int type;
    public int x, y;
    /** Kaynak yönü. */
    public int dir;
    /** Kaynak rengi, hedefin istediği renk veya filtre rengi. */
    public int color;
    /** Ayna yönelimi: 0 = '/', 1 = '\'. */
    public int orient;
    /** Oyuncu döndürebilir mi (ayna). */
    public boolean rotatable;
    /** Anahtar/kapı eşleşme numarası. */
    public int link;

    public Piece() {}

    public Piece(int type, int x, int y) { this.type = type; this.x = x; this.y = y; }

    public Piece copy() {
        Piece p = new Piece(type, x, y);
        p.dir = dir; p.color = color; p.orient = orient; p.rotatable = rotatable; p.link = link;
        return p;
    }

    public static Piece wall(int x, int y) { return new Piece(Cell.WALL, x, y); }

    public static Piece source(int x, int y, int dir, int color) {
        Piece p = new Piece(Cell.SOURCE, x, y); p.dir = dir; p.color = color; return p;
    }

    public static Piece target(int x, int y, int color) {
        Piece p = new Piece(Cell.TARGET, x, y); p.color = color; return p;
    }

    public static Piece mirror(int x, int y, int orient, boolean rotatable) {
        Piece p = new Piece(Cell.MIRROR, x, y); p.orient = orient; p.rotatable = rotatable; return p;
    }

    public static Piece filter(int x, int y, int color) {
        Piece p = new Piece(Cell.FILTER, x, y); p.color = color; return p;
    }

    public static Piece prism(int x, int y) { return new Piece(Cell.PRISM, x, y); }

    public static Piece block(int x, int y) { return new Piece(Cell.BLOCK, x, y); }

    public static Piece key(int x, int y, int link) {
        Piece p = new Piece(Cell.KEY, x, y); p.link = link; return p;
    }

    public static Piece door(int x, int y, int link) {
        Piece p = new Piece(Cell.DOOR, x, y); p.link = link; return p;
    }
}
