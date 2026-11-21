package com.isikyolu.engine;

/**
 * 500 bölüm = 5 dünya × 10 grup × 10 bölüm. İlk dünya mekanikleri sırayla öğretir; sonraki dünyalar
 * aynı on konuyu daha büyük tahtalarda, daha çok yanıltıcı nesne ve ek mekaniklerle tekrar işler.
 */
public final class Chapters {
    /** Toplam grup sayısı. */
    public static final int COUNT = 50;
    /** Mekanik konusu sayısı (her dünyada tekrar eder). */
    public static final int MECHANIC_COUNT = 10;
    public static final int WORLDS = 5;

    public static final String[] WORLD_NAMES = {"Işık Bahçesi", "Kristal Mağaralar", "Gece Bahçesi", "Yıldız Atölyesi", "Işık Tapınağı"};
    public static final String[] WORLD_LEVELS = {"Başlangıç", "Zor", "Uzman", "Usta", "Efsane"};
    public static final String[] WORLD_INTROS = {
            "Işığın temel kurallarını öğren.",
            "Bildiğin mekanikler, daha çok ayna ve daha uzun yollarla geri dönüyor.",
            "Mekanikler birbirine karışıyor: her grupta beklenmedik bir ek düzenek var.",
            "Daha büyük tahtalar ve daha az hata payı. Planlamadan dokunma!",
            "Son sınav: en uzun yollar, en çok yanıltıcı nesne. Gerçek Işık Ustaları için."
    };

    public static final String[] NAMES = {
            "İlk Kıvılcım", "Ayna Labirenti", "Gölge Duvarlar", "Renk Cümbüşü", "Süzgeç Atölyesi",
            "Prizma Bahçesi", "Çoklu Hedef", "Hareketli Taşlar", "Kilitli Kapılar", "Işığın Ustası",
            "Kristal Kapı", "Yankı Odası", "Taş Koridor", "Renkli Damlalar", "Cam Süzgeçler",
            "Gökkuşağı Kuyusu", "Çifte Parıltı", "Kayan Kayalar", "Mühürlü Geçit", "Mağaranın Kalbi",
            "Ay Işığı", "Sarmaşık Yolu", "Gölge Çitler", "Çiçek Renkleri", "Sisli Cam",
            "Çiy Prizması", "Ateşböcekleri", "Bahçe Taşları", "Gizli Kapılar", "Gece Yarısı",
            "Yıldız Tozu", "Kuyruklu Yıldız", "Kara Delik", "Nebula", "Yıldız Camı",
            "Takımyıldızı", "Çifte Güneş", "Gezegen Taşları", "Kozmik Kilit", "Süpernova",
            "Tapınak Kapısı", "Sonsuz Aynalar", "Kadim Duvarlar", "Altın Renkler", "Mabet Camları",
            "Işık Sunağı", "Yedi Hedef", "Kayan Sütunlar", "Son Mühür", "Işığın Kalbi"
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
            "Prizma ışığı ayrıştırır. Işığın GİDİŞ YÖNÜNE göre kırmızı sola, yeşil düz, mavi sağa sapar: aşağı giden ışıkta kırmızı ekranın sağına gider.",
            "Birden fazla hedef var. Bölümü bitirmek için hepsi aynı anda yanmalı.",
            "Altın bloklar ışığı keser. Sürükleyerek bir kare kaydırabilirsin.",
            "Işık bir anahtar taşına değince aynı renkteki kapı açılır.",
            "Tüm mekanikler bir arada. Gerçek bir Işık Ustası olduğunu göster!"
    };

    private Chapters() {}

    public static int chapterOf(int levelId) { return (levelId - 1) / 10 + 1; }

    public static int worldOf(int levelId) { return (levelId - 1) / 100 + 1; }

    public static int worldOfChapter(int chapter) { return (chapter - 1) / 10 + 1; }

    /** Grubun mekanik konusu (1–10). */
    public static int mechanicOf(int chapter) { return (chapter - 1) % 10 + 1; }

    /** Grup konusu, dünya zorluğuyla birlikte. */
    public static String topic(int chapter) {
        int w = worldOfChapter(chapter);
        String t = TOPICS[mechanicOf(chapter) - 1];
        return w == 1 ? t : t + " · " + WORLD_LEVELS[w - 1];
    }

    /** Bölüm numarası için üretici ayarı (1–500). */
    public static LevelGenerator.Config configFor(int levelId) {
        int local = (levelId - 1) % 100 + 1;
        int k = worldOf(levelId) - 1;
        LevelGenerator.Config c = baseConfig(local);
        if (k > 0) harden(c, k, (local - 1) / 10 + 1, (local - 1) % 10);
        c.minEffort = minEffort(levelId, c);
        if (c.minEffort > 0) { c.decoyRot += 1; c.decoyFixed += 1; }
        c.shareMirrors = levelId > 100 || (levelId > 10 && (levelId - 1) % 10 >= 3);
        // Tek kaynaklı ayna bölümlerinde optimum, yoldaki ayna sayısını aşamaz.
        if (c.sourceColors.length == 1 && c.prismSources == 0 && c.blocks == 0 && c.doors == 0) {
            c.turnsMax = Math.max(c.turnsMax, c.minOpt + 1);
            c.turnsMin = Math.max(c.turnsMin, Math.min(c.minOpt, c.turnsMax));
        }
        return c;
    }

    /**
     * Bu bölüm "mantık bölümü" mü? Bu bölümlerde ışını takip eden oyuncunun karar ağacı büyüktür:
     * yanlış ayna seçimleri uzun, inandırıcı çıkmazlara götürür; önceden plan yapmak gerekir.
     * İlk grup öğretici olduğu için hariçtir.
     */
    public static boolean requiresLogic(int levelId) {
        if (levelId <= 10) return false;
        if (levelId > 100) return true;
        return (levelId - 1) % 10 >= 4;
    }

    /** Mantık bölümleri için en az ışın takip zahmeti (karar ağacı yaprak sayısı). */
    public static int minEffort(int levelId, LevelGenerator.Config c) {
        if (!requiresLogic(levelId)) return 0;
        int w = worldOf(levelId), i = (levelId - 1) % 10;
        int[] base = {14, 20, 26, 30, 34};
        int[] step = {2, 2, 2, 2, 2};
        int e = base[w - 1] + step[w - 1] * Math.max(0, i - (w == 1 ? 4 : 0));
        // bloklar ve kapılar ayrıca düşünme gerektirir; ayna ağacı şartı yarıya iner
        if (c.blocks > 0 || c.doors > 0 || c.prismSources > 0) e /= 2;
        return e;
    }

    /** Sonraki dünyalar için zorlaştırma: k = 1..4. */
    static void harden(LevelGenerator.Config c, int k, int mech, int i) {
        c.w = Math.min(8, c.w + (k >= 2 ? 1 : 0));
        c.h = Math.min(11, c.h + (k >= 1 ? 1 : 0) + (k >= 3 ? 1 : 0));
        c.turnsMin = Math.min(4, c.turnsMin + (k >= 2 ? 1 : 0));
        c.turnsMax = Math.min(6, c.turnsMax + 1 + (k >= 3 ? 1 : 0));
        c.decoyRot += k;
        c.decoyFixed += k / 2;
        c.walls += 2 * k;
        c.fixedFrac = Math.max(c.fixedFrac, 0.2);
        if (k >= 2) {
            // her grupta farklı ek düzenek
            int extra = (mech + i) % 4;
            boolean white = c.sourceColors[0] == LightColor.WHITE;
            if (extra == 0 && c.sourceColors.length < 3) {
                int[] s = java.util.Arrays.copyOf(c.sourceColors, c.sourceColors.length + 1);
                s[s.length - 1] = LightColor.WHITE;
                c.sourceColors = s;
            } else if (extra == 1) {
                c.decoyFilters += 1;
                if (white && c.filterSources == 0 && c.prismSources == 0) c.filterSources = 1;
            } else if (extra == 2) {
                c.blocks = Math.max(c.blocks, 1);
            } else {
                if (c.sourceColors.length < 2) c.sourceColors = new int[]{c.sourceColors[0], LightColor.WHITE};
                c.doors = Math.max(c.doors, 1);
            }
        }
        int base = c.minOpt;
        c.minOpt = base + k + (k >= 3 ? 1 : 0);
        c.maxOpt = c.minOpt + 3;
        c.maxRot = c.blocks > 0 ? Math.min(11, c.maxRot + 2) : 16;
        if (c.blocks > 1 && k >= 4) c.blocks = 1; // 8×11 tahtada iki blok durum uzayını ipucu sınırının üstüne çıkarır
        if (c.blocks > 0) c.maxRot = c.blocks > 1 ? 8 : Math.min(11, c.maxRot);
        c.stateCap = 80000;
    }

    private static LevelGenerator.Config baseConfig(int levelId) {
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
