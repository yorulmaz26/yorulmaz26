package com.golgen.oyun.core;

/**
 * Günlük görevler: her gün tarih tohumuyla 3 farklı görev seçilir (sunucu gerekmez).
 */
public final class DailyMissions {
    public static final int PLAY = 0, COLLECT = 1, SURVIVE_SINGLE = 2, DODGE = 3, SCORE_SINGLE = 4;
    private static final int TYPE_COUNT = 5;
    public static final int PER_DAY = 3;

    private DailyMissions() {}

    /** Gün için seçilen görev türleri (birbirinden farklı). */
    public static void typesFor(int epochDay, int[] out) {
        long seed = epochDay * 2654435761L + 12345L;
        int[] pool = {0, 1, 2, 3, 4};
        for (int i = TYPE_COUNT - 1; i > 0; i--) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            int j = (int) ((seed >>> 33) % (i + 1));
            int t = pool[i]; pool[i] = pool[j]; pool[j] = t;
        }
        for (int i = 0; i < PER_DAY; i++) out[i] = pool[i];
    }

    /** Gün ilerledikçe hedef hafifçe değişir (iki kademe). */
    public static int target(int type, int epochDay) {
        boolean hard = (epochDay & 1) == 1;
        switch (type) {
            case PLAY: return hard ? 5 : 3;
            case COLLECT: return hard ? 25 : 15;
            case SURVIVE_SINGLE: return hard ? 60 : 40;
            case DODGE: return hard ? 15 : 8;
            default: return hard ? 1200 : 600;
        }
    }

    public static int reward(int type, int epochDay) {
        boolean hard = (epochDay & 1) == 1;
        int base;
        switch (type) {
            case PLAY: base = 15; break;
            case COLLECT: base = 25; break;
            case SURVIVE_SINGLE: base = 30; break;
            case DODGE: base = 25; break;
            default: base = 30; break;
        }
        return hard ? base + 15 : base;
    }

    public static String text(int type, int target) {
        switch (type) {
            case PLAY: return target + " oyun oyna";
            case COLLECT: return "Toplam " + target + " elmas topla";
            case SURVIVE_SINGLE: return "Tek oyunda " + target + " sn hayatta kal";
            case DODGE: return "Toplam " + target + " kez gölgeden kaç";
            default: return "Tek oyunda " + target + " puan yap";
        }
    }

    /** Bir oyun sonucunun bu görev türüne katkısını mevcut ilerlemeye uygular. */
    public static int advance(int type, int current, RunResult r) {
        switch (type) {
            case PLAY: return current + 1;
            case COLLECT: return current + r.diamonds;
            case SURVIVE_SINGLE: return Math.max(current, (int) r.time);
            case DODGE: return current + r.dodges;
            default: return Math.max(current, r.score);
        }
    }
}
