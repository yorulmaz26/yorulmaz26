package com.golgen.oyun.core;

/**
 * Görev modu: 5 bölüm × 12 seviye = 60 görev. Her bölüm yeni bir zorlaştırıcı getirir,
 * hedefler ve başlangıç zorluğu kademeli artar; her bölüm uzun bir "bölüm sonu" göreviyle biter.
 */
public final class Missions {
    private Missions() {}

    public static final int CHAPTERS = 5;
    public static final int PER_CHAPTER = 12;
    public static final String[] CHAPTER_NAMES = {"Alacakaranlık", "Neon Sokaklar", "Karanlık Labirent", "Gölge Fırtınası", "Sonsuz Gece"};
    public static final String[] CHAPTER_DESCS = {
            "Temel görevler", "Hızlı akış", "Karanlıkta görüş sınırlı", "Hızlı ve çok sayıda gölge", "Tüm zorlaştırıcılar"
    };
    /** Bölüm başına zorlaştırıcılar; seviyeler bunlardan dönüşümlü alır. */
    private static final int[] CHAPTER_MODS = {
            0,
            MissionDef.MOD_RUSH,
            MissionDef.MOD_DARK,
            MissionDef.MOD_FAST_SHADOWS | MissionDef.MOD_SWARM,
            MissionDef.MOD_DARK | MissionDef.MOD_FAST_SHADOWS | MissionDef.MOD_SWARM | MissionDef.MOD_RUSH
    };
    private static final int[] TYPE_CYCLE = {
            MissionDef.SURVIVE, MissionDef.COLLECT, MissionDef.DODGE, MissionDef.NO_HIT, MissionDef.SCORE, MissionDef.SURVIVE,
            MissionDef.COLLECT, MissionDef.DODGE, MissionDef.NO_HIT, MissionDef.SCORE, MissionDef.COLLECT, MissionDef.SURVIVE
    };

    public static final MissionDef[] ALL = build();

    private static MissionDef[] build() {
        MissionDef[] all = new MissionDef[CHAPTERS * PER_CHAPTER];
        for (int c = 0; c < CHAPTERS; c++) {
            for (int j = 0; j < PER_CHAPTER; j++) {
                int idx = c * PER_CHAPTER + j;
                int type = TYPE_CYCLE[j];
                boolean boss = j == PER_CHAPTER - 1;
                int target;
                switch (type) {
                    case MissionDef.SURVIVE: target = boss ? 60 + c * 20 : 20 + c * 8 + j * 2; break;
                    case MissionDef.COLLECT: target = 5 + c * 2 + j / 2; break;
                    case MissionDef.DODGE: target = 3 + c * 2 + j / 3; break;
                    case MissionDef.NO_HIT: target = 20 + c * 6 + j; break;
                    default: target = 400 + c * 250 + j * 40; break;
                }
                int mods = CHAPTER_MODS[c];
                if (c == 4 && !boss) {
                    // Son bölüm: seviyeye göre iki zorlaştırıcı, bölüm sonunda hepsi
                    int[] pairs = {MissionDef.MOD_DARK | MissionDef.MOD_RUSH, MissionDef.MOD_FAST_SHADOWS | MissionDef.MOD_DARK,
                            MissionDef.MOD_SWARM | MissionDef.MOD_RUSH, MissionDef.MOD_SWARM | MissionDef.MOD_FAST_SHADOWS};
                    mods = pairs[j % pairs.length];
                } else if (c > 0 && j < 2) {
                    mods = c == 3 ? MissionDef.MOD_FAST_SHADOWS : CHAPTER_MODS[c]; // yumuşak giriş
                }
                float offset = c * 30f + j * 4f;
                int reward = 20 + c * 15 + j * 2 + (boss ? 40 : 0);
                int starCombo = 4 + c * 2 + (j >= 6 ? 2 : 0);
                String title = boss ? CHAPTER_NAMES[c] + " · Final" : CHAPTER_NAMES[c] + " " + (j + 1);
                all[idx] = new MissionDef(idx, c, title, type, target, offset, reward, mods, starCombo, boss);
            }
        }
        return all;
    }

    public static int chapterOf(int index) {
        return index / PER_CHAPTER;
    }
}
