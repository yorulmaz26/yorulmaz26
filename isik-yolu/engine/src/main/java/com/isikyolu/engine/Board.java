package com.isikyolu.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Bulmacanın değişebilir durumu. Hücre dizileriyle tutulur; çözücünün hızlı çalışması için
 * durum 64 bitlik bir anahtara kodlanabilir (döndürülebilir aynalar + blok konumları).
 */
public final class Board {
    public final int width, height;
    final int[] type, dir, color, orient, link;
    final boolean[] rotatable;
    /** Döndürülebilir aynaların hücre indisleri (sabit sıra). */
    final int[] rotCells;
    /** Hareketli blokların hücre indisleri (her zaman sıralı tutulur). */
    final int[] blocks;
    final int[] sources, targets;
    final int maxLink;

    public Board(Level level) {
        width = level.width;
        height = level.height;
        int n = width * height;
        type = new int[n]; dir = new int[n]; color = new int[n]; orient = new int[n]; link = new int[n];
        rotatable = new boolean[n];
        List<Integer> rot = new ArrayList<>(), blk = new ArrayList<>(), src = new ArrayList<>(), tgt = new ArrayList<>();
        int ml = 0;
        for (Piece p : level.pieces) {
            int c = p.y * width + p.x;
            if (type[c] != Cell.EMPTY) throw new IllegalArgumentException("Aynı hücrede iki nesne: " + p.x + "," + p.y);
            type[c] = p.type; dir[c] = p.dir; color[c] = p.color; orient[c] = p.orient & 1; link[c] = p.link;
            rotatable[c] = p.type == Cell.MIRROR && p.rotatable;
            if (rotatable[c]) rot.add(c);
            if (p.type == Cell.BLOCK) blk.add(c);
            if (p.type == Cell.SOURCE) src.add(c);
            if (p.type == Cell.TARGET) tgt.add(c);
            if (p.type == Cell.KEY || p.type == Cell.DOOR) ml = Math.max(ml, p.link);
        }
        rotCells = toArray(rot);
        blocks = toArray(blk);
        Arrays.sort(blocks);
        sources = toArray(src);
        targets = toArray(tgt);
        maxLink = ml;
        if (rotCells.length + 8 * blocks.length > 63) throw new IllegalArgumentException("Durum anahtarı için çok fazla nesne");
        if (n > 256 && blocks.length > 0) throw new IllegalArgumentException("Bloklu bölümler en fazla 256 hücre olabilir");
    }

    private Board(Board o) {
        width = o.width; height = o.height;
        type = o.type.clone(); dir = o.dir; color = o.color; orient = o.orient.clone(); link = o.link;
        rotatable = o.rotatable; rotCells = o.rotCells; blocks = o.blocks.clone();
        sources = o.sources; targets = o.targets; maxLink = o.maxLink;
    }

    private static int[] toArray(List<Integer> l) {
        int[] a = new int[l.size()];
        for (int i = 0; i < a.length; i++) a[i] = l.get(i);
        return a;
    }

    public Board copy() { return new Board(this); }

    public int cell(int x, int y) { return y * width + x; }
    public boolean inside(int x, int y) { return x >= 0 && y >= 0 && x < width && y < height; }
    public int typeAt(int c) { return type[c]; }
    public int dirAt(int c) { return dir[c]; }
    public int colorAt(int c) { return color[c]; }
    public int orientAt(int c) { return orient[c]; }
    public int linkAt(int c) { return link[c]; }
    public boolean isRotatable(int c) { return rotatable[c]; }
    public int[] targetCells() { return targets; }
    public int[] sourceCells() { return sources; }
    public int[] blockCells() { return blocks; }
    public int rotatableCount() { return rotCells.length; }
    public int blockCount() { return blocks.length; }

    public boolean isMovable(int c) { return type[c] == Cell.BLOCK; }

    public boolean rotate(int c) {
        if (!rotatable[c]) return false;
        orient[c] ^= 1;
        return true;
    }

    public boolean canPush(int c, int d) {
        if (type[c] != Cell.BLOCK) return false;
        int x = c % width + Dir.DX[d], y = c / width + Dir.DY[d];
        return inside(x, y) && type[cell(x, y)] == Cell.EMPTY;
    }

    /** Bloğu bir hücre iter; yeni hücreyi döndürür veya -1. */
    public int push(int c, int d) {
        if (!canPush(c, d)) return -1;
        int nc = cell(c % width + Dir.DX[d], c / width + Dir.DY[d]);
        type[c] = Cell.EMPTY;
        type[nc] = Cell.BLOCK;
        for (int i = 0; i < blocks.length; i++) if (blocks[i] == c) { blocks[i] = nc; break; }
        Arrays.sort(blocks);
        return nc;
    }

    public boolean apply(Move m) {
        if (m.kind == Move.ROTATE) return rotate(m.cell);
        return push(m.cell, m.dir) >= 0;
    }

    /** Hamlenin tersini uygular (geri alma). */
    public void revert(Move m) {
        if (m.kind == Move.ROTATE) rotate(m.cell);
        else {
            int nc = cell(m.cell % width + Dir.DX[m.dir], m.cell / width + Dir.DY[m.dir]);
            push(nc, Dir.opposite(m.dir));
        }
    }

    public long stateKey() {
        long k = 0;
        for (int i = 0; i < rotCells.length; i++) if (orient[rotCells[i]] == 1) k |= 1L << i;
        int off = rotCells.length;
        for (int j = 0; j < blocks.length; j++) k |= ((long) blocks[j]) << (off + 8 * j);
        return k;
    }

    public void applyKey(long k) {
        for (int i = 0; i < rotCells.length; i++) orient[rotCells[i]] = (int) ((k >>> i) & 1);
        if (blocks.length > 0) {
            for (int b : blocks) type[b] = Cell.EMPTY;
            int off = rotCells.length;
            for (int j = 0; j < blocks.length; j++) {
                blocks[j] = (int) ((k >>> (off + 8 * j)) & 0xFF);
                type[blocks[j]] = Cell.BLOCK;
            }
            Arrays.sort(blocks);
        }
    }

    /** Geçerli durumdan tüm geçerli hamleler. */
    public List<Move> legalMoves() {
        List<Move> out = new ArrayList<>();
        for (int c : rotCells) out.add(Move.rotate(c));
        for (int b : blocks.clone()) for (int d = 0; d < 4; d++) if (canPush(b, d)) out.add(Move.push(b, d));
        return out;
    }

    /** Mevcut durumu bölüm verisine dönüştürür (yönelim ve blok konumları dahil). */
    public Level toLevel(Level template) {
        Level l = new Level(width, height);
        l.id = template.id; l.chapter = template.chapter; l.name = template.name; l.optimal = template.optimal;
        for (int c = 0; c < type.length; c++) {
            if (type[c] == Cell.EMPTY) continue;
            Piece p = new Piece(type[c], c % width, c / width);
            p.dir = dir[c]; p.color = color[c]; p.orient = orient[c]; p.rotatable = rotatable[c]; p.link = link[c];
            l.pieces.add(p);
        }
        return l;
    }
}
