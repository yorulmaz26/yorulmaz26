package com.isikyolu.game.ui;

import com.isikyolu.engine.Economy;

/** Marketten alınan görünümlerin renk ve çizim ayarları. */
public final class Skins {
    private Skins() {}

    /** Ayna kaplaması: cam geçişi, çerçeve ve uç kapağı. rainbow = renk değiştiren cam. */
    public static final class Mirror {
        public final int[] glass; public final int frame, cap; public final boolean rainbow;
        Mirror(int[] glass, int frame, int cap, boolean rainbow) { this.glass = glass; this.frame = frame; this.cap = cap; this.rainbow = rainbow; }
    }

    /** Tahta teması: kareler, zemin, çerçeve geçişi; stars = zeminde yıldızlar. */
    public static final class Board {
        public final int tileA, tileB, back; public final int[] frame; public final boolean stars;
        Board(int tileA, int tileB, int back, int[] frame, boolean stars) { this.tileA = tileA; this.tileB = tileB; this.back = back; this.frame = frame; this.stars = stars; }
    }

    /** Işın efekti: kalınlık çarpanı ve foton biçimi (0 nokta, 1 yıldız tozu, 2 plazma). */
    public static final class Beam {
        public final float width; public final int photon;
        Beam(float width, int photon) { this.width = width; this.photon = photon; }
    }

    public static Mirror mirror(String id) {
        if (id == null) id = Economy.DEFAULT_MIRROR;
        switch (id) {
            case "mirror_gold": return new Mirror(new int[]{0xFFFFF8E1, 0xFFFFE29A, 0xFFF5C542, 0xFFC98F1E}, 0xFF8A6420, 0xFFFFF0B0, false);
            case "mirror_obsidian": return new Mirror(new int[]{0xFF8C7BC0, 0xFF3E3463, 0xFF221B3D, 0xFF100C1E}, 0xFF6D5DFB, 0xFFB9A8FF, false);
            case "mirror_ice": return new Mirror(new int[]{0xFFFFFFFF, 0xFFE0FBFF, 0xFFA8EEFF, 0xFF6CCBEA}, 0xFF7FB8D6, 0xFFE0FBFF, false);
            case "mirror_rainbow": return new Mirror(new int[]{0xFFFF6F7D, 0xFFFFD23F, 0xFF3DDC84, 0xFF3FE0F0, 0xFF6D5DFB, 0xFFE15CFF, 0xFFFF6F7D}, 0xFF5B4A8F, 0xFFFFFFFF, true);
            default: return new Mirror(new int[]{0xFFFFFFFF, 0xFFD6E6FA, 0xFF9DBBE6, 0xFF6F8FC4}, 0xFF4F6188, Palette.TURQUOISE, false);
        }
    }

    public static Board board(String id) {
        if (id == null) id = Economy.DEFAULT_BOARD;
        switch (id) {
            case "board_ocean": return new Board(0xFF22557C, 0xFF1E4C70, 0xE6103452, new int[]{0xFF20B8A6, 0xFF3FE0F0, 0xFF4D8DFF}, false);
            case "board_sunset": return new Board(0xFF65406B, 0xFF5A3760, 0xE6301A3C, new int[]{0xFFFF9F43, 0xFFFF6F7D, 0xFF6D5DFB}, false);
            case "board_forest": return new Board(0xFF2F6149, 0xFF2A5641, 0xE6133322, new int[]{0xFF3DDC84, 0xFFF5C542, 0xFF20B8A6}, false);
            case "board_galaxy": return new Board(0xFF342A63, 0xFF2D2457, 0xE6120D2B, new int[]{0xFF6D5DFB, 0xFFE15CFF, 0xFF3FE0F0}, true);
            default: return new Board(0xFF364971, 0xFF324268, 0xE61D2843, new int[]{0xFF20B8A6, 0xFF6D5DFB, 0xFFF5C542}, false);
        }
    }

    public static Beam beam(String id) {
        if (id == null) id = Economy.DEFAULT_BEAM;
        switch (id) {
            case "beam_thick": return new Beam(1.5f, 0);
            case "beam_stardust": return new Beam(1.0f, 1);
            case "beam_plasma": return new Beam(1.15f, 2);
            default: return new Beam(1.0f, 0);
        }
    }
}
