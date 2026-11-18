package com.golgen.oyun.core;

/**
 * Harita teması (veri nesnesi, Unity'deki ScriptableObject karşılığı): renkler, arka plan efekti,
 * engel karışımı ve harita özelliği. Her harita 5 bölüm boyunca kullanılır.
 */
public final class MapTheme {
    // Arka plan efektleri
    public static final int DECO_STARS = 0, DECO_SNOW = 1, DECO_RAIN = 2, DECO_EMBERS = 3, DECO_BUBBLES = 4,
            DECO_LEAVES = 5, DECO_DUST = 6, DECO_SPARKS = 7, DECO_CODE = 8;

    // Harita özellikleri (bit maskesi)
    public static final int F_WIND = 1;          // yatay rüzgâr karakteri iter
    public static final int F_ICE = 2;           // kaygan zemin: karakter ataletle hareket eder
    public static final int F_DARK = 4;          // görüş karakter çevresiyle sınırlı
    public static final int F_STORM = 8;         // meteor yağmuru
    public static final int F_NARROW = 16;       // yanlardan lazer duvarlar: dar geçit
    public static final int F_RUSH = 32;         // hızlı akış
    public static final int F_FAST_SHADOWS = 64; // gölgeler daha yakından takip eder
    public static final String[] FEATURE_NAMES = {"Rüzgâr", "Buz", "Karanlık", "Meteor Yağmuru", "Dar Geçit", "Hızlı Akış", "Hızlı Gölgeler"};

    public final int id;
    public final String name;
    public final String desc;
    public final int bgTop, bgBottom, grid, obstacle, accent;
    public final int deco;
    public final int features;
    /** Engel ağırlıkları: Obstacle türü indeksine göre (WALL..ZIGZAG). */
    public final float[] weights;
    /** Haritanın imza engeli (bölümler ilerledikçe sıklığı artar). */
    public final int signature;
    public final float shadowDelay1, shadowDelay2;

    public MapTheme(int id, String name, String desc, int bgTop, int bgBottom, int grid, int obstacle, int accent,
                    int deco, int features, int signature, float shadowDelay1, float shadowDelay2, float... weights) {
        this.id = id;
        this.name = name;
        this.desc = desc;
        this.bgTop = bgTop;
        this.bgBottom = bgBottom;
        this.grid = grid;
        this.obstacle = obstacle;
        this.accent = accent;
        this.deco = deco;
        this.features = features;
        this.signature = signature;
        this.shadowDelay1 = shadowDelay1;
        this.shadowDelay2 = shadowDelay2;
        float[] w = new float[Obstacle.TYPE_COUNT];
        System.arraycopy(weights, 0, w, 0, Math.min(weights.length, w.length));
        this.weights = w;
    }

    public boolean has(int f) {
        return (features & f) != 0;
    }

    public String featuresText() {
        if (features == 0) return "Klasik";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < FEATURE_NAMES.length; i++) {
            if ((features & (1 << i)) != 0) {
                if (sb.length() > 0) sb.append(" · ");
                sb.append(FEATURE_NAMES[i]);
            }
        }
        return sb.toString();
    }
}
