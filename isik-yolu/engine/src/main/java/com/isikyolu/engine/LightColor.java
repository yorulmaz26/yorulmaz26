package com.isikyolu.engine;

/** Işık renkleri bit maskesidir: R=1, G=2, B=4. Karışımlar bitlerin birleşimidir. */
public final class LightColor {
    public static final int RED = 1, GREEN = 2, BLUE = 4;
    public static final int YELLOW = RED | GREEN;
    public static final int MAGENTA = RED | BLUE;
    public static final int CYAN = GREEN | BLUE;
    public static final int WHITE = RED | GREEN | BLUE;

    private LightColor() {}

    public static String nameTr(int c) {
        switch (c) {
            case RED: return "Kırmızı";
            case GREEN: return "Yeşil";
            case BLUE: return "Mavi";
            case YELLOW: return "Sarı";
            case MAGENTA: return "Pembe";
            case CYAN: return "Camgöbeği";
            case WHITE: return "Beyaz";
            default: return "Karanlık";
        }
    }

    public static boolean isPrimary(int c) { return c == RED || c == GREEN || c == BLUE; }
}
