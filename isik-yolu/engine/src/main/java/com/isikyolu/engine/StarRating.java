package com.isikyolu.engine;

/** Yıldız hesaplama: 3 = optimum, 2 = optimumun biraz üzeri, 1 = tamamlama. */
public final class StarRating {
    private StarRating() {}

    /** 2 yıldız için izin verilen fazladan hamle. */
    public static int slack(int optimal) { return Math.max(2, (optimal + 1) / 2); }

    public static int stars(int moves, int optimal) {
        if (optimal <= 0) return 3;
        if (moves <= optimal) return 3;
        if (moves <= optimal + slack(optimal)) return 2;
        return 1;
    }

    /** Bir sonraki yıldız seviyesine kalan hamle sınırı (gösterim için). */
    public static int threeStarLimit(int optimal) { return optimal; }

    public static int twoStarLimit(int optimal) { return optimal + slack(optimal); }
}
