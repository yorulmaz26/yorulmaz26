package com.golgen.oyun.core;

/**
 * Günlük görevler: her gün tarih tohumuyla 8 türden 3 farklı görev seçilir (sunucu gerekmez).
 */
public final class DailyMissions {
    public static final int PLAY = 0, COLLECT = 1, SURVIVE_SINGLE = 2, DODGE = 3, SCORE_SINGLE = 4,
            POWERUPS = 5, MISSION_WINS = 6, COMBO_SINGLE = 7;
    public static final int TYPE_COUNT = 8;
    public static final int PER_DAY = 3;

    private DailyMissions() {}

    /** Gün için seçilen görev türleri (birbirinden farklı). */
    public static void typesFor(int epochDay, int[] out) {
        long seed = epochDay * 2654435761L + 12345L;
        int[] pool = new int[TYPE_COUNT];
        for (int i = 0; i < TYPE_COUNT; i++) pool[i] = i;
        for (int i = TYPE_COUNT - 1; i > 0; i--) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            int j = (int) ((seed >>> 33) % (i + 1));
            int t = pool[i]; pool[i] = pool[j]; pool[j] = t;
        }
        for (int i = 0; i < PER_DAY; i++) out[i] = pool[i];
    }

    /** Gün ilerledikçe hedef değişir (üç kademe). */
    public static int target(int type, int epochDay) {
        int tier = Math.floorMod(epochDay, 3);
        switch (type) {
            case PLAY: return 3 + tier;
            case COLLECT: return 15 + tier * 10;
            case SURVIVE_SINGLE: return 40 + tier * 20;
            case DODGE: return 8 + tier * 5;
            case POWERUPS: return 3 + tier * 2;
            case MISSION_WINS: return 1 + tier;
            case COMBO_SINGLE: return 8 + tier * 4;
            default: return 600 + tier * 500;
        }
    }

    public static int reward(int type, int epochDay) {
        int tier = Math.floorMod(epochDay, 3);
        int base;
        switch (type) {
            case PLAY: base = 15; break;
            case COLLECT: case DODGE: case POWERUPS: base = 25; break;
            case MISSION_WINS: base = 35; break;
            default: base = 30; break;
        }
        return base + tier * 10;
    }

    /** Görev türlerinin çoğul yerelleştirme anahtarları (tür sırasıyla; {n} = hedef). */
    private static final String[] TEXT_KEYS = {"daily.play", "daily.collect", "daily.survive", "daily.dodge",
            "daily.score", "daily.powerups", "daily.level_wins", "daily.combo"};

    /** Görev metni o anki dilde (ör. "3 oyun oyna"). */
    public static String text(int type, int target) {
        int t = (type >= 0 && type < TEXT_KEYS.length) ? type : SCORE_SINGLE;
        return L10n.p(TEXT_KEYS[t], target);
    }

    /** Bir oyun sonucunun bu görev türüne katkısını mevcut ilerlemeye uygular. */
    public static int advance(int type, int current, RunResult r) {
        switch (type) {
            case PLAY: return current + 1;
            case COLLECT: return current + r.diamonds;
            case SURVIVE_SINGLE: return Math.max(current, (int) r.time);
            case DODGE: return current + r.dodges;
            case POWERUPS: return current + r.powerups;
            case MISSION_WINS: return current + (r.mode == RunResult.MODE_MISSION && r.won ? 1 : 0);
            case COMBO_SINGLE: return Math.max(current, r.maxCombo);
            default: return Math.max(current, r.score);
        }
    }
}
