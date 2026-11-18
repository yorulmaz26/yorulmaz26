package com.golgen.oyun.core;

/** Elmaslarla açılabilen 8 karakter görünümü. */
public final class Skins {
    private Skins() {}

    public static final SkinDef[] ALL = {
            new SkinDef(0, "Işık Kare", SkinDef.SQUARE, 0, 0xFF20B8A6),
            new SkinDef(1, "Ok Ucu", SkinDef.TRIANGLE, 60, 0xFF6D5DFB),
            new SkinDef(2, "Elmas Göz", SkinDef.RHOMBUS, 120, 0xFFD6AD60),
            new SkinDef(3, "Petek", SkinDef.HEXAGON, 200, 0xFF20B8A6),
            new SkinDef(4, "Kutup Yıldızı", SkinDef.STAR, 300, 0xFFD6AD60),
            new SkinDef(5, "Halka", SkinDef.RING, 450, 0xFF6D5DFB),
            new SkinDef(6, "Pusula", SkinDef.CROSS, 650, 0xFF20B8A6),
            new SkinDef(7, "Kristal", SkinDef.CRYSTAL, 900, 0xFFD6AD60),
            new SkinDef(8, "Altın Ok", SkinDef.TRIANGLE, 1200, 0xFFD6AD60),
            new SkinDef(9, "Mor Petek", SkinDef.HEXAGON, 1500, 0xFF6D5DFB),
            new SkinDef(10, "Turkuaz Yıldız", SkinDef.STAR, 1900, 0xFF20B8A6),
            new SkinDef(11, "Efsane Kristal", SkinDef.CRYSTAL, 2500, 0xFF6D5DFB),
    };
}
