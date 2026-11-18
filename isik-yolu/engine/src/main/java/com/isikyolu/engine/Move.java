package com.isikyolu.engine;

/** Oyuncu hamlesi: ayna döndürme veya blok itme. */
public final class Move {
    public static final int ROTATE = 0, PUSH = 1;

    public final int kind;
    public final int cell;
    public final int dir;

    public Move(int kind, int cell, int dir) { this.kind = kind; this.cell = cell; this.dir = dir; }

    public static Move rotate(int cell) { return new Move(ROTATE, cell, 0); }
    public static Move push(int cell, int dir) { return new Move(PUSH, cell, dir); }

    public int encode() { return (kind << 20) | (cell << 2) | dir; }

    public static Move decode(int code) {
        return new Move(code >>> 20, (code >>> 2) & 0x3FFFF, code & 3);
    }

    @Override public boolean equals(Object o) {
        if (!(o instanceof Move)) return false;
        Move m = (Move) o;
        return m.kind == kind && m.cell == cell && m.dir == dir;
    }

    @Override public int hashCode() { return encode(); }

    @Override public String toString() {
        return (kind == ROTATE ? "Döndür@" : "İt@") + cell + (kind == PUSH ? "/" + dir : "");
    }
}
