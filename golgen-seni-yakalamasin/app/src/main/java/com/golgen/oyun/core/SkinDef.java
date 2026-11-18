package com.golgen.oyun.core;

/** Karakter görünümü tanımı (veri nesnesi). */
public final class SkinDef {
    public static final int SQUARE = 0, TRIANGLE = 1, RHOMBUS = 2, HEXAGON = 3, STAR = 4, RING = 5, CROSS = 6, CRYSTAL = 7;

    public final int id;
    public final String name;
    public final int shape;
    public final int price;
    public final int glowColor; // ARGB

    public SkinDef(int id, String name, int shape, int price, int glowColor) {
        this.id = id;
        this.name = name;
        this.shape = shape;
        this.price = price;
        this.glowColor = glowColor;
    }
}
