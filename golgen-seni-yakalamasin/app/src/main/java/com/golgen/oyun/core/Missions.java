package com.golgen.oyun.core;

/** Görev modu: 20 harita × 5 bölüm = 100 bölüm. */
public final class Missions {
    private Missions() {}

    public static final int PER_MAP = 5;
    public static final int MAPS = Maps.ALL.length;
    /** Eski isimler (arayüz uyumluluğu). */
    public static final int CHAPTERS = MAPS;
    public static final int PER_CHAPTER = PER_MAP;

    public static final MissionDef[] ALL = build();

    private static MissionDef[] build() {
        MissionDef[] all = new MissionDef[MAPS * PER_MAP];
        for (int m = 0; m < MAPS; m++) {
            for (int j = 0; j < PER_MAP; j++) all[m * PER_MAP + j] = new MissionDef(m * PER_MAP + j, Maps.ALL[m], j);
        }
        return all;
    }

    public static int mapOf(int index) {
        return index / PER_MAP;
    }
}
