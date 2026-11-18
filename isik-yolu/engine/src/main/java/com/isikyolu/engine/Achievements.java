package com.isikyolu.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Başarı tanımları ve kontrolü. */
public final class Achievements {

    public static final class Def {
        public final String id, title, description;
        final int kind, goal;

        Def(String id, String title, String description, int kind, int goal) {
            this.id = id; this.title = title; this.description = description; this.kind = kind; this.goal = goal;
        }

        public int current(Progress p) {
            switch (kind) {
                case K_COMPLETED: return p.completedCount();
                case K_STARS: return p.totalStars();
                case K_THREE: return p.threeStarCount();
                case K_DAILY: return p.dailyDone.size();
                case K_NOHINT: return p.levelsWithoutHint;
                case K_HINT: return p.hintsUsedTotal;
                case K_UNDO: return p.undoCount;
                case K_WORLD: return p.completedInWorld(goal);
                case K_CHAPTER: {
                    int n = 0;
                    for (int i = (goal - 1) * 10 + 1; i <= goal * 10; i++) if (p.stars[i] > 0) n++;
                    return n;
                }
                default: return 0;
            }
        }

        public int target() { return kind == K_CHAPTER ? 10 : (kind == K_WORLD ? 100 : goal); }

        public boolean reached(Progress p) { return current(p) >= target(); }
    }

    static final int K_COMPLETED = 0, K_STARS = 1, K_THREE = 2, K_DAILY = 3, K_NOHINT = 4, K_HINT = 5, K_UNDO = 6, K_CHAPTER = 7, K_WORLD = 8;

    public static final List<Def> ALL = Arrays.asList(
            new Def("ilk_isik", "İlk Işık", "İlk bölümü tamamla.", K_COMPLETED, 1),
            new Def("cirak", "Işık Çırağı", "10 bölüm tamamla.", K_COMPLETED, 10),
            new Def("yansima", "Yansıma Ustası", "50 bölüm tamamla.", K_COMPLETED, 50),
            new Def("ilk_dunya", "Işık Bahçesi", "İlk dünyanın 100 bölümünü bitir.", K_WORLD, 1),
            new Def("kristal", "Kristal Kâşif", "Kristal Mağaralar dünyasını (101–200) bitir.", K_WORLD, 2),
            new Def("gece", "Gece Bekçisi", "Gece Bahçesi dünyasını (201–300) bitir.", K_WORLD, 3),
            new Def("yildiz_ustasi", "Yıldız Ustası", "Yıldız Atölyesi dünyasını (301–400) bitir.", K_WORLD, 4),
            new Def("tapinak", "Tapınak Muhafızı", "Işık Tapınağı dünyasını (401–500) bitir.", K_WORLD, 5),
            new Def("yari_yol", "Yolun Yarısı", "250 bölüm tamamla.", K_COMPLETED, 250),
            new Def("efsane", "Işık Yolu Efsanesi", "500 bölümün hepsini tamamla.", K_COMPLETED, 500),
            new Def("renk", "Renk Büyücüsü", "Renkli ışıklar bölümlerini (31–40) bitir.", K_CHAPTER, 4),
            new Def("prizma", "Prizma Kâşifi", "Prizma bölümlerini (51–60) bitir.", K_CHAPTER, 6),
            new Def("anahtar", "Kilit Ustası", "Kapı ve anahtar bölümlerini (81–90) bitir.", K_CHAPTER, 9),
            new Def("parilti", "Parıltı", "Toplam 30 yıldız topla.", K_STARS, 30),
            new Def("yildiz_avcisi", "Yıldız Avcısı", "Toplam 300 yıldız topla.", K_STARS, 300),
            new Def("yildiz_krali", "Yıldızların Efendisi", "Toplam 900 yıldız topla.", K_STARS, 900),
            new Def("kusursuz", "Kusursuz Işık", "1500 yıldızın hepsini topla.", K_STARS, 1500),
            new Def("mukemmel", "Mükemmeliyetçi", "10 bölümü 3 yıldızla bitir.", K_THREE, 10),
            new Def("mukemmel100", "Hatasız Yol", "100 bölümü 3 yıldızla bitir.", K_THREE, 100),
            new Def("gunun_isigi", "Günün Işığı", "İlk günlük bulmacayı çöz.", K_DAILY, 1),
            new Def("haftalik", "Haftalık Parıltı", "7 günlük bulmaca çöz.", K_DAILY, 7),
            new Def("aylik", "Ayın Işığı", "30 günlük bulmaca çöz.", K_DAILY, 30),
            new Def("kendi_yolum", "Kendi Yolum", "20 bölümü ipucu kullanmadan bitir.", K_NOHINT, 20),
            new Def("merakli", "Meraklı Zihin", "İlk ipucunu kullan.", K_HINT, 1),
            new Def("zaman_yolcusu", "Zaman Yolcusu", "10 kez hamle geri al.", K_UNDO, 10)
    );

    private Achievements() {}

    /** Yeni kazanılan başarıları işaretler ve listesini döndürür. */
    public static List<Def> update(Progress p) {
        List<Def> fresh = new ArrayList<>();
        for (Def d : ALL) if (!p.achievements.contains(d.id) && d.reached(p)) { p.achievements.add(d.id); fresh.add(d); }
        return fresh;
    }
}
