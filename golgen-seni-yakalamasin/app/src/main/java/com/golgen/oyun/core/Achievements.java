package com.golgen.oyun.core;

/** Basit başarı sistemi. Her başarı tek seferlik elmas ödülü verir. */
public final class Achievements {
    private Achievements() {}

    public static final String[] TITLES = {
            "İlk Adım", "Gölge Avcısı", "Hayatta Kalan", "Gece Bekçisi", "Koleksiyoncu",
            "Gardırop", "Görev Adamı", "Efsane", "Kusursuz", "Puan Canavarı"
    };
    public static final String[] DESCS = {
            "İlk oyununu oyna", "Toplam 50 kez gölgeden kaç", "Tek oyunda 60 sn dayan", "Tek oyunda 180 sn dayan",
            "Toplam 100 elmas topla", "4 karakter aç", "6 görev tamamla", "12 görevin hepsini tamamla",
            "Engelsiz bir görevi tamamla", "Tek oyunda 2000 puan yap"
    };
    public static final int REWARD = 20;
    public static final int COUNT = TITLES.length;

    public static boolean isMet(int id, Progress p, RunResult last) {
        switch (id) {
            case 0: return p.gamesPlayed >= 1;
            case 1: return p.totalDodges >= 50;
            case 2: return p.bestTime >= 60f;
            case 3: return p.bestTime >= 180f;
            case 4: return p.totalDiamonds >= 100;
            case 5: return Integer.bitCount(p.unlockedSkins) >= 4;
            case 6: return p.missionsCompleted >= 6;
            case 7: return p.missionsCompleted >= Missions.ALL.length;
            case 8: return last != null && last.mode == RunResult.MODE_MISSION && last.won
                    && Missions.ALL[last.missionIndex].type == MissionDef.NO_HIT;
            default: return p.bestScore >= 2000;
        }
    }
}
