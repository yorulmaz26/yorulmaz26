package com.golgen.oyun.core;

/** Görev modu seviyeleri. */
public final class Missions {
    private Missions() {}

    public static final MissionDef[] ALL = {
            new MissionDef(0, "İlk Kaçış", MissionDef.SURVIVE, 20, 0f, 20),
            new MissionDef(1, "Parıltı Avı", MissionDef.COLLECT, 5, 0f, 25),
            new MissionDef(2, "Gölge Dansı", MissionDef.DODGE, 3, 0f, 30),
            new MissionDef(3, "Temiz Sayfa", MissionDef.NO_HIT, 25, 5f, 35),
            new MissionDef(4, "Dayanıklılık", MissionDef.SURVIVE, 45, 20f, 40),
            new MissionDef(5, "Altın Yağmuru", MissionDef.COLLECT, 12, 15f, 45),
            new MissionDef(6, "Kıl Payı", MissionDef.DODGE, 8, 20f, 50),
            new MissionDef(7, "Hayalet Adım", MissionDef.NO_HIT, 40, 30f, 60),
            new MissionDef(8, "Gece Yarısı", MissionDef.SURVIVE, 75, 40f, 70),
            new MissionDef(9, "Hazine Avcısı", MissionDef.COLLECT, 20, 50f, 80),
            new MissionDef(10, "Gölgelerin Efendisi", MissionDef.DODGE, 15, 60f, 90),
            new MissionDef(11, "Kusursuz Işık", MissionDef.NO_HIT, 60, 70f, 120),
    };
}
