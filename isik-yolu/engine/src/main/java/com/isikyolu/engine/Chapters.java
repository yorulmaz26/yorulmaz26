package com.isikyolu.engine;

/** 10 bölüm grubu: adlar, yeni mekanik açıklamaları ve üretici ayarları. */
public final class Chapters {
    public static final int COUNT = 10;

    public static final String[] NAMES = {
            "İlk Kıvılcım", "Ayna Labirenti", "Gölge Duvarlar", "Renk Cümbüşü", "Süzgeç Atölyesi",
            "Prizma Bahçesi", "Çoklu Hedef", "Hareketli Taşlar", "Kilitli Kapılar", "Işığın Ustası"
    };

    /** Bölüm aralıklarının konusu. */
    public static final String[] TOPICS = {
            "Temel aynalar", "Birden fazla ayna", "Engeller", "Renkli ışıklar", "Renk filtreleri",
            "Prizmalar", "Birden fazla hedef", "Hareketli bloklar", "Kapılar ve anahtarlar", "Karma mekanikler"
    };

    public static final String[] MECHANICS = {
            "Aynaya dokun, 90° dönsün. Işığı hedefe yönlendir!",
            "Birden fazla ayna var. Işığın izleyeceği yolu baştan planla.",
            "Duvarlar ışığı durdurur. Koyu çerçeveli aynalar sabittir, dönmez.",
            "Renkli kaynaklar! Her hedef halkasının rengindeki ışığı ister. Kırmızı + yeşil = sarı.",
            "Renk filtreleri yalnızca kendi rengini geçirir. Beyaz ışık filtreden renkli çıkar.",
            "Prizma ışığı ayrıştırır: kırmızı sola, yeşil düz, mavi sağa sapar.",
            "Birden fazla hedef var. Bölümü bitirmek için hepsi aynı anda yanmalı.",
            "Altın bloklar ışığı keser. Sürükleyerek bir kare kaydırabilirsin.",
            "Işık bir anahtar taşına değince aynı renkteki kapı açılır.",
            "Tüm mekanikler bir arada. Gerçek bir Işık Ustası olduğunu göster!"
    };

    private Chapters() {}

    public static int chapterOf(int levelId) { return (levelId - 1) / 10 + 1; }

    /** Bölüm numarası için üretici ayarı (1–100). */
    public static LevelGenerator.Config configFor(int levelId) {
        int ch = chapterOf(levelId), i = (levelId - 1) % 10;
        LevelGenerator.Config c = new LevelGenerator.Config();
        final int W = LightColor.WHITE, R = LightColor.RED, G = LightColor.GREEN, B = LightColor.BLUE;
        switch (ch) {
            case 1:
                c.w = i < 4 ? 5 : 6; c.h = i < 4 ? 7 : 8;
                c.turnsMin = i < 2 ? 1 : (i < 7 ? 2 : 3); c.turnsMax = i < 2 ? 1 : (i < 4 ? 2 : 3);
                c.decoyRot = i < 4 ? 0 : (i < 7 ? 1 : 2);
                c.walls = i / 3;
                c.minOpt = i < 2 ? 1 : (i < 7 ? 2 : 3); c.maxOpt = i < 4 ? c.minOpt : c.minOpt + 1;
                break;
            case 2:
                c.w = 6; c.h = 8;
                c.turnsMin = 3; c.turnsMax = i < 5 ? 4 : 5;
                c.decoyRot = 1 + i / 4; c.walls = 1 + i / 4;
                c.minOpt = 3 + i / 3; c.maxOpt = c.minOpt + 1;
                break;
            case 3:
                c.w = i < 5 ? 6 : 7; c.h = 9;
                c.turnsMin = 3; c.turnsMax = 5;
                c.walls = 6 + i; c.fixedFrac = 0.35; c.decoyFixed = 1 + i / 5; c.decoyRot = 1 + i / 4;
                c.minOpt = 3 + i / 3; c.maxOpt = c.minOpt + 2;
                break;
            case 4:
                c.w = 7; c.h = 9;
                int[][] pal = {{R, B}, {G, R}, {B, G}, {R, G}, {B, R}, {R, G}, {G, B}, {R, B, G}, {B, R, G}, {G, R, B}};
                c.sourceColors = pal[i];
                c.merge = i >= 5;
                c.turnsMin = 2; c.turnsMax = i < 5 ? 2 : 3;
                c.decoyRot = 1 + i / 4; c.walls = 2 + i / 2;
                c.minOpt = 3 + i / 3; c.maxOpt = c.minOpt + 2;
                break;
            case 5:
                c.w = 7; c.h = 9;
                c.sourceColors = i < 4 ? new int[]{W} : new int[]{W, W};
                c.filterSources = c.sourceColors.length;
                c.turnsMin = 2; c.turnsMax = 3;
                c.decoyFilters = 1 + i / 4; c.decoyRot = 1 + i / 5; c.walls = 2 + i / 2;
                c.minOpt = 3 + i / 3; c.maxOpt = c.minOpt + 2;
                break;
            case 6:
                c.w = 7; c.h = 10;
                c.sourceColors = new int[]{W};
                c.prismSources = 1;
                int[] pf = {0, 0, 0, LightColor.YELLOW, 0, 0, LightColor.CYAN, 0, LightColor.MAGENTA, 0};
                c.prismFilter = pf[i];
                c.turnsMin = 1; c.turnsMax = 2;
                c.branchTurnsMin = i < 5 ? 0 : 1; c.branchTurnsMax = i < 3 ? 1 : 2;
                c.decoyRot = i / 4; c.walls = 2 + i / 2;
                c.minOpt = 3 + i / 3; c.maxOpt = c.minOpt + 2;
                break;
            case 7:
                c.w = 7; c.h = 10;
                c.sourceColors = i < 4 ? new int[]{W, W} : (i < 8 ? new int[]{W, W, W} : new int[]{W, W, R});
                c.prismSources = i >= 6 ? 1 : 0;
                c.branchTurnsMin = 0; c.branchTurnsMax = 1;
                c.turnsMin = 2; c.turnsMax = 3;
                c.decoyRot = 1 + i / 5; c.walls = 2 + i / 2;
                c.minOpt = 4 + i / 4; c.maxOpt = c.minOpt + 2; c.maxRot = 15;
                break;
            case 8:
                c.w = 7; c.h = 9;
                c.sourceColors = i < 5 ? new int[]{W} : new int[]{W, W};
                c.blocks = i < 4 ? 1 : 2;
                c.turnsMin = 2; c.turnsMax = 3;
                c.walls = 8 + i / 2; c.decoyRot = i / 5;
                c.minOpt = 3 + i / 3; c.maxOpt = c.minOpt + 2; c.maxRot = 9;
                break;
            case 9:
                c.w = 7; c.h = 9;
                int[][] pal9 = {{W, W}, {W, W}, {R, W}, {W, B}, {W, W}, {G, W}, {W, W, W}, {W, R, W}, {W, W, W}, {B, W, W}};
                c.sourceColors = pal9[i];
                c.doors = i < 6 ? 1 : 2;
                c.turnsMin = 2; c.turnsMax = 3;
                c.walls = 3 + i / 2; c.decoyRot = 1 + i / 5;
                c.minOpt = 3 + i / 3; c.maxOpt = c.minOpt + 2; c.maxRot = 15;
                break;
            default:
                c.w = 7; c.h = 10;
                mixed(c, i);
                c.minOpt = 5 + i / 3; c.maxOpt = c.minOpt + 3; c.maxRot = 14;
                break;
        }
        // Tek kaynaklı ayna bölümlerinde optimum, yoldaki ayna sayısını aşamaz.
        if (c.sourceColors.length == 1 && c.prismSources == 0 && c.blocks == 0 && c.doors == 0) {
            c.turnsMax = Math.max(c.turnsMax, c.minOpt + 1);
            c.turnsMin = Math.max(c.turnsMin, Math.min(c.minOpt, c.turnsMax));
        }
        return c;
    }

    /** Karma mekanikler: her bölüm farklı bir birleşim. */
    static void mixed(LevelGenerator.Config c, int i) {
        final int W = LightColor.WHITE, R = LightColor.RED, G = LightColor.GREEN, B = LightColor.BLUE;
        c.turnsMin = 2; c.turnsMax = 3; c.walls = 4 + i / 2; c.decoyRot = 1;
        switch (i % 10) {
            case 0: c.sourceColors = new int[]{W, W}; c.filterSources = 1; c.blocks = 1; break;
            case 1: c.sourceColors = new int[]{W, W}; c.prismSources = 1; c.doors = 1; c.branchTurnsMax = 1; break;
            case 2: c.sourceColors = new int[]{R, G}; c.merge = true; c.blocks = 1; break;
            case 3: c.sourceColors = new int[]{W, W}; c.filterSources = 2; c.doors = 1; break;
            case 4: c.sourceColors = new int[]{W}; c.prismSources = 1; c.blocks = 1; c.branchTurnsMax = 1; c.decoyFilters = 1; break;
            case 5: c.sourceColors = new int[]{B, R, W}; c.merge = true; c.doors = 1; break;
            case 6: c.sourceColors = new int[]{W, W}; c.filterSources = 1; c.doors = 1; c.blocks = 1; break;
            case 7: c.sourceColors = new int[]{W, W}; c.prismSources = 1; c.prismFilter = LightColor.YELLOW; c.doors = 1; c.branchTurnsMax = 1; break;
            case 8: c.sourceColors = new int[]{G, B, W}; c.merge = true; c.filterSources = 0; c.blocks = 1; c.doors = 1; break;
            default: c.sourceColors = new int[]{W, W, W}; c.prismSources = 1; c.filterSources = 0; c.doors = 1; c.blocks = 1; c.branchTurnsMax = 1; break;
        }
        c.fixedFrac = 0.25;
        if (c.blocks > 0) c.maxRot = 9;
    }
}
