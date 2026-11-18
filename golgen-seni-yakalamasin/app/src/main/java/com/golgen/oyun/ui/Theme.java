package com.golgen.oyun.ui;

/** Renk paleti ve ölçü sabitleri. */
public final class Theme {
    private Theme() {}

    public static final int NAVY = 0xFF101827;     // Gece laciverti
    public static final int PURPLE = 0xFF6D5DFB;   // Neon mor
    public static final int TEAL = 0xFF20B8A6;     // Turkuaz
    public static final int GOLD = 0xFFD6AD60;     // Altın
    public static final int WHITE = 0xFFFFFFFF;

    // Paletten türetilmiş tonlar
    public static final int NAVY_DEEP = 0xFF0A101C;
    public static final int PANEL = 0xFF172238;
    public static final int CARD = 0xFF1C2943;
    public static final int LINE = 0xFF2C3A5E;
    public static final int MUTED = 0xFF8C97B5;
    public static final int PURPLE_LIGHT = 0xFF9488FF;
    public static final int PURPLE_DARK = 0xFF4B3FD0;
    public static final int SHADOW_FILL = 0xFF05070D;

    public static int alpha(int color, float a) {
        int al = (int) (((color >>> 24) & 0xff) * Math.max(0f, Math.min(1f, a)));
        return (color & 0x00ffffff) | (al << 24);
    }

    public static int lerp(int c1, int c2, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int a = (int) (((c1 >>> 24) & 0xff) + ((((c2 >>> 24) & 0xff) - ((c1 >>> 24) & 0xff)) * t));
        int r = (int) (((c1 >> 16) & 0xff) + ((((c2 >> 16) & 0xff) - ((c1 >> 16) & 0xff)) * t));
        int g = (int) (((c1 >> 8) & 0xff) + ((((c2 >> 8) & 0xff) - ((c1 >> 8) & 0xff)) * t));
        int b = (int) ((c1 & 0xff) + (((c2 & 0xff) - (c1 & 0xff)) * t));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
