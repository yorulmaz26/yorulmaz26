package com.isikyolu.game.ui;

import android.graphics.Color;

import com.isikyolu.engine.LightColor;

/** Oyunun renk paleti. */
public final class Palette {
    public static final int NIGHT = 0xFF101827;
    public static final int NIGHT_DEEP = 0xFF1C253B;
    public static final int NIGHT_LIGHT = 0xFF304064;
    public static final int CARD = 0xFF2F3F61;
    public static final int CARD_BORDER = 0xFF455980;
    public static final int TILE = 0xFF35466D;
    public static final int TILE_ALT = 0xFF324267;
    public static final int TURQUOISE = 0xFF20B8A6;
    public static final int GOLD = 0xFFF5C542;
    public static final int PURPLE = 0xFF6D5DFB;
    public static final int WHITE = 0xFFFFFFFF;
    public static final int TEXT_DIM = 0xFFC6D1E8;
    public static final int TEXT_FAINT = 0xFF94A2C2;
    public static final int WALL = 0xFF4C5D86;
    public static final int MIRROR = 0xFFE3EAF6;
    public static final int MIRROR_FIXED = 0xFF8792A8;

    /** Anahtar/kapı eşleşme renkleri. */
    public static final int[] LINKS = {0xFFF5C542, 0xFF6D5DFB, 0xFF20B8A6, 0xFFFF6FA8};

    private Palette() {}

    /** Işık rengi (bit maskesi) için ekran rengi. */
    public static int light(int c) {
        switch (c) {
            case LightColor.RED: return 0xFFFF4D5E;
            case LightColor.GREEN: return 0xFF3DDC84;
            case LightColor.BLUE: return 0xFF4D8DFF;
            case LightColor.YELLOW: return 0xFFFFD23F;
            case LightColor.MAGENTA: return 0xFFE15CFF;
            case LightColor.CYAN: return 0xFF3FE0F0;
            case LightColor.WHITE: return 0xFFFFF4D6;
            default: return 0xFF3A4560;
        }
    }

    public static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }

    public static int mix(int a, int b, float t) {
        int r = (int) (Color.red(a) + (Color.red(b) - Color.red(a)) * t);
        int g = (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * t);
        int bl = (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t);
        int al = (int) (Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * t);
        return Color.argb(al, r, g, bl);
    }

    public static int lighten(int c, float t) { return mix(c, WHITE, t); }
}
