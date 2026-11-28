package com.golgen.oyun.core;

/** Karakter görünümü tanımı (veri nesnesi). */
public final class SkinDef {
    public static final int SQUARE = 0, TRIANGLE = 1, RHOMBUS = 2, HEXAGON = 3, STAR = 4, RING = 5, CROSS = 6, CRYSTAL = 7;

    public final int id;
    public final int shape;
    public final int price;
    public final int glowColor; // ARGB

    private final String nameKey;

    public SkinDef(int id, int shape, int price, int glowColor) {
        this.id = id;
        this.shape = shape;
        this.price = price;
        this.glowColor = glowColor;
        this.nameKey = "skin." + id + ".name";
    }

    /** Karakterin o anki dildeki adı ({@code skin.<id>.name}). */
    public String name() {
        return L10n.t(nameKey);
    }
}
