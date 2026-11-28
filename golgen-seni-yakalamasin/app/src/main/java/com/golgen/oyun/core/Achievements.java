package com.golgen.oyun.core;

/** Başarı sistemi (21 başarı). Her başarı tek seferlik elmas ödülü verir. */
public final class Achievements {
    private Achievements() {}

    // Başlık/açıklama metinleri: ach.<id>.title, ach.<id>.desc (sayılı olanlar ach.<id>.desc.one/.other)
    // 0 İlk Adım, 1 Gölge Avcısı, 2 Hayatta Kalan, 3 Gece Bekçisi, 4 Maraton, 5 Koleksiyoncu, 6 Hazine Dağı,
    // 7 Gardırop, 8 Tam Koleksiyon, 9 İlk Harita, 10 Gezgin, 11 Gölgelerin Efendisi, 12 Taç Avcısı,
    // 13 Mükemmeliyetçi, 14 Kusursuz, 15 Puan Canavarı, 16 Efsanevi Skor, 17 Kombo Kralı, 18 Güç Toplayıcı,
    // 19 Sadık Oyuncu, 20 Usta Oyuncu
    public static final int[] REWARDS = {20, 30, 30, 60, 100, 30, 120, 40, 300, 60, 120, 300, 80, 400, 40, 50, 200, 60, 50, 100, 80};
    public static final int REWARD = 20; // en düşük ödül (eski arayüz metinleri için)
    public static final int COUNT = REWARDS.length;

    private static final String[] TITLE_KEYS = new String[COUNT];
    private static final String[] DESC_KEYS = new String[COUNT];
    static {
        for (int i = 0; i < COUNT; i++) {
            TITLE_KEYS[i] = "ach." + i + ".title";
            DESC_KEYS[i] = "ach." + i + ".desc";
        }
    }

    /** Başarının o anki dildeki adı. */
    public static String title(int id) {
        return L10n.t(TITLE_KEYS[id]);
    }

    /** Başarının o anki dildeki açıklaması (hedef sayısı {@link #goal(int)} ile doldurulur). */
    public static String desc(int id) {
        int n = goal(id);
        if (n < 0) return L10n.t(DESC_KEYS[id]);
        if (id == 20) return L10n.f(DESC_KEYS[id], n); // sıra sayısı ("10. seviye"): çoğul değil
        return L10n.p(DESC_KEYS[id], n);
    }

    /** Açıklamada gösterilen hedef sayısı; sayı içermeyen açıklamalar için -1. {@link #isMet} ile aynı eşikler. */
    public static int goal(int id) {
        switch (id) {
            case 1: return 50;
            case 2: return 60;
            case 3: return 180;
            case 4: return 300;
            case 5: return 100;
            case 6: return 1000;
            case 7: return 4;
            case 9: return 5;
            case 10: return 100;
            case 11: return Missions.ALL.length;
            case 12: return 300;
            case 13: return Missions.ALL.length * 3;
            case 15: return 2000;
            case 16: return 10000;
            case 17: return 20;
            case 18: return 25;
            case 19: return 7;
            case 20: return 10;
            default: return -1; // 0, 8, 14: sayısız açıklama
        }
    }

    public static boolean isMet(int id, Progress p, RunResult last) {
        switch (id) {
            case 0: return p.gamesPlayed >= 1;
            case 1: return p.totalDodges >= 50;
            case 2: return p.bestTime >= 60f;
            case 3: return p.bestTime >= 180f;
            case 4: return p.bestTime >= 300f;
            case 5: return p.totalDiamonds >= 100;
            case 6: return p.totalDiamonds >= 1000;
            case 7: return Integer.bitCount(p.unlockedSkins) >= 4;
            case 8: return Integer.bitCount(p.unlockedSkins) >= Skins.ALL.length;
            case 9: return p.missionsCompleted >= 5;
            case 10: return p.missionsCompleted >= 100;
            case 11: return p.missionsCompleted >= Missions.ALL.length;
            case 12: return p.totalStars() >= 300;
            case 13: return p.totalStars() >= Missions.ALL.length * 3;
            case 14: return last != null && last.mode == RunResult.MODE_MISSION && last.won && last.hits == 0;
            case 15: return p.bestScore >= 2000;
            case 16: return p.bestScore >= 10000;
            case 17: return p.maxCombo >= 20;
            case 18: return p.totalPowerups >= 25;
            case 19: return p.loginStreak >= 7;
            default: return p.level >= 10;
        }
    }
}
