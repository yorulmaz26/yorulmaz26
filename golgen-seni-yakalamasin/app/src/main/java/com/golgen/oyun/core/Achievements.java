package com.golgen.oyun.core;

/** Başarı sistemi (21 başarı). Her başarı tek seferlik elmas ödülü verir. */
public final class Achievements {
    private Achievements() {}

    public static final String[] TITLES = {
            "İlk Adım", "Gölge Avcısı", "Hayatta Kalan", "Gece Bekçisi", "Maraton",
            "Koleksiyoncu", "Hazine Dağı", "Gardırop", "Tam Koleksiyon", "İlk Harita",
            "Gezgin", "Gölgelerin Efendisi", "Taç Avcısı", "Mükemmeliyetçi", "Kusursuz",
            "Puan Canavarı", "Efsanevi Skor", "Kombo Kralı", "Güç Toplayıcı", "Sadık Oyuncu",
            "Usta Oyuncu"
    };
    public static final String[] DESCS = {
            "İlk oyununu oyna", "Toplam 50 kez gölgeden kaç", "Tek oyunda 60 sn dayan", "Tek oyunda 180 sn dayan",
            "Tek oyunda 300 sn dayan", "Toplam 100 elmas topla", "Toplam 1000 elmas topla", "4 karakter aç",
            "Tüm karakterleri aç", "İlk haritayı bitir (5 bölüm)", "50 bölüm bitir", "100 bölümün hepsini bitir",
            "Toplam 100 taç kazan", "300 tacın hepsini kazan", "Bir bölümü hiç çarpmadan bitir",
            "Tek oyunda 2000 puan yap", "Tek oyunda 10000 puan yap", "Tek oyunda 20 kombo yap",
            "Toplam 25 güçlendirme topla", "7 gün üst üste giriş yap", "10. seviyeye ulaş"
    };
    public static final int[] REWARDS = {20, 30, 30, 60, 100, 30, 120, 40, 300, 60, 120, 300, 80, 400, 40, 50, 200, 60, 50, 100, 80};
    public static final int REWARD = 20; // en düşük ödül (eski arayüz metinleri için)
    public static final int COUNT = TITLES.length;

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
            case 10: return p.missionsCompleted >= 50;
            case 11: return p.missionsCompleted >= Missions.ALL.length;
            case 12: return p.totalStars() >= 100;
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
