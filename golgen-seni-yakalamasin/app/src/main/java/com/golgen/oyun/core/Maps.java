package com.golgen.oyun.core;

/**
 * 20 harita. Ağırlık sırası: WALL, MINE, SWEEPER, ROTOR, GATE, BOUNCER, METEOR, ORBITER, ZIGZAG.
 * Her harita farklı renk, arka plan efekti, imza engeli ve özellik getirir.
 */
public final class Maps {
    private Maps() {}

    public static final MapTheme[] ALL = {
            new MapTheme(0, "Neon Kapı", "Her şeyin başladığı yer", 0xFF101827, 0xFF1A1640, 0xFF6D5DFB, 0xFF6D5DFB, 0xFF20B8A6,
                    MapTheme.DECO_STARS, 0, Obstacle.MINE, 3.0f, 4.4f, 5, 2, 0, 0, 0, 0, 0, 0, 0),
            new MapTheme(1, "Kristal Mağara", "Buz mavisi kristaller arasında", 0xFF0B1A2A, 0xFF10304A, 0xFF4FC3F7, 0xFF4FC3F7, 0xFFB3E5FC,
                    MapTheme.DECO_SPARKS, 0, Obstacle.SWEEPER, 3.0f, 4.2f, 4, 2, 3, 0, 0, 0, 0, 0, 0),
            new MapTheme(2, "Yağmurlu Şehir", "Islak sokaklarda rüzgâr eser", 0xFF0E1522, 0xFF1C2638, 0xFF5C7CFA, 0xFF7C8CFF, 0xFF20B8A6,
                    MapTheme.DECO_RAIN, MapTheme.F_WIND, Obstacle.ZIGZAG, 2.9f, 4.2f, 3, 1, 2, 0, 0, 0, 0, 0, 3),
            new MapTheme(3, "Lav Çekirdeği", "Gökten ateş yağar", 0xFF1A0B0B, 0xFF3A1208, 0xFFFF7043, 0xFFFF7043, 0xFFD6AD60,
                    MapTheme.DECO_EMBERS, MapTheme.F_STORM, Obstacle.METEOR, 2.9f, 4.0f, 3, 2, 0, 0, 0, 2, 3, 0, 0),
            new MapTheme(4, "Buz Çölü", "Zemin kaygan, dikkatli ol", 0xFF0F1E2C, 0xFF20384E, 0xFFB3E5FC, 0xFF81D4FA, 0xFFFFFFFF,
                    MapTheme.DECO_SNOW, MapTheme.F_ICE, Obstacle.ZIGZAG, 2.9f, 4.0f, 4, 1, 2, 0, 0, 0, 0, 0, 3),
            new MapTheme(5, "Derin Okyanus", "Karanlık sularda dönen tehlikeler", 0xFF041420, 0xFF06283A, 0xFF26C6DA, 0xFF26C6DA, 0xFF80DEEA,
                    MapTheme.DECO_BUBBLES, MapTheme.F_DARK, Obstacle.ORBITER, 2.8f, 4.0f, 3, 3, 0, 0, 0, 0, 0, 3, 0),
            new MapTheme(6, "Sonbahar Ormanı", "Yapraklar ve dönen dallar", 0xFF1A140B, 0xFF2E2210, 0xFFE0A040, 0xFFE09B3D, 0xFFD6AD60,
                    MapTheme.DECO_LEAVES, MapTheme.F_WIND, Obstacle.ROTOR, 2.8f, 3.9f, 3, 1, 2, 3, 0, 0, 0, 0, 1),
            new MapTheme(7, "Elektrik Santrali", "Lazer kapılar açılıp kapanır", 0xFF0C0F1E, 0xFF161C38, 0xFFFFEB3B, 0xFFFFEB3B, 0xFF6D5DFB,
                    MapTheme.DECO_SPARKS, 0, Obstacle.GATE, 2.8f, 3.9f, 3, 0, 1, 2, 4, 0, 0, 0, 0),
            new MapTheme(8, "Kum Fırtınası", "Rüzgâr ve uçan kayalar", 0xFF1E160C, 0xFF3A2A14, 0xFFD6AD60, 0xFFE0B870, 0xFFFFE0A0,
                    MapTheme.DECO_DUST, MapTheme.F_WIND | MapTheme.F_STORM, Obstacle.BOUNCER, 2.7f, 3.8f, 3, 0, 1, 0, 0, 3, 2, 0, 0),
            new MapTheme(9, "Gece Pazarı", "Kalabalık ve hızlı", 0xFF180E1E, 0xFF2C1636, 0xFFFF80AB, 0xFFFF80AB, 0xFFD6AD60,
                    MapTheme.DECO_SPARKS, MapTheme.F_RUSH, Obstacle.ORBITER, 2.7f, 3.8f, 3, 1, 3, 0, 0, 1, 0, 2, 0),
            new MapTheme(10, "Yıldız Tozu", "Uzayın derinliklerinde", 0xFF05060F, 0xFF120E2A, 0xFF9488FF, 0xFF9488FF, 0xFFFFFFFF,
                    MapTheme.DECO_STARS, MapTheme.F_RUSH, Obstacle.BOUNCER, 2.7f, 3.7f, 2, 1, 1, 1, 0, 3, 0, 2, 0),
            new MapTheme(11, "Sis Bataklığı", "Göremediğin yerde gölge bekler", 0xFF0C1612, 0xFF16261E, 0xFF66BB6A, 0xFF81C784, 0xFFA5D6A7,
                    MapTheme.DECO_DUST, MapTheme.F_DARK, Obstacle.MINE, 2.6f, 3.6f, 2, 4, 1, 0, 0, 0, 0, 0, 2),
            new MapTheme(12, "Saat Kulesi", "Zaman daralıyor, gölgeler yakın", 0xFF141018, 0xFF241C2C, 0xFFD6AD60, 0xFFD6AD60, 0xFF20B8A6,
                    MapTheme.DECO_SPARKS, MapTheme.F_FAST_SHADOWS, Obstacle.ROTOR, 2.6f, 3.6f, 2, 0, 2, 3, 2, 0, 0, 0, 0),
            new MapTheme(13, "Kızıl Gezegen", "Toz ve meteorlar", 0xFF1E0C08, 0xFF3A1A10, 0xFFFF8A65, 0xFFFF8A65, 0xFFFFCCBC,
                    MapTheme.DECO_DUST, MapTheme.F_STORM, Obstacle.ROTOR, 2.6f, 3.5f, 2, 1, 0, 3, 0, 1, 3, 0, 0),
            new MapTheme(14, "Ayna Labirenti", "Daralan koridorlar", 0xFF0E1420, 0xFF1A2236, 0xFFE0E0FF, 0xFFC5CAE9, 0xFF20B8A6,
                    MapTheme.DECO_SPARKS, MapTheme.F_NARROW, Obstacle.ZIGZAG, 2.5f, 3.5f, 2, 0, 1, 0, 2, 0, 0, 0, 3),
            new MapTheme(15, "Kuzey Işıkları", "Kaygan buz, dans eden ışıklar", 0xFF061418, 0xFF0E2A2C, 0xFF20B8A6, 0xFF64FFDA, 0xFF6D5DFB,
                    MapTheme.DECO_SNOW, MapTheme.F_ICE | MapTheme.F_DARK, Obstacle.ORBITER, 2.5f, 3.4f, 2, 1, 2, 0, 0, 0, 0, 3, 1),
            new MapTheme(16, "Volkan Zirvesi", "Ateş yağmuru ve hızlı akış", 0xFF200806, 0xFF40140A, 0xFFFF5722, 0xFFFF7043, 0xFFFFC107,
                    MapTheme.DECO_EMBERS, MapTheme.F_STORM | MapTheme.F_RUSH, Obstacle.METEOR, 2.4f, 3.4f, 2, 1, 0, 1, 0, 3, 3, 0, 0),
            new MapTheme(17, "Siber Ağ", "Kod akışında hızlı gölgeler", 0xFF041008, 0xFF08200F, 0xFF00E676, 0xFF00E676, 0xFF20B8A6,
                    MapTheme.DECO_CODE, MapTheme.F_FAST_SHADOWS | MapTheme.F_RUSH, Obstacle.GATE, 2.4f, 3.3f, 2, 0, 1, 2, 3, 0, 0, 0, 2),
            new MapTheme(18, "Kara Delik", "Çekim seni yana savurur", 0xFF030308, 0xFF100A20, 0xFF9488FF, 0xFFB39DDB, 0xFFD6AD60,
                    MapTheme.DECO_STARS, MapTheme.F_DARK | MapTheme.F_WIND, Obstacle.ORBITER, 2.3f, 3.2f, 1, 1, 0, 2, 0, 2, 0, 3, 1),
            new MapTheme(19, "Gölge Tahtı", "Son durak: her şey bir arada", 0xFF08060C, 0xFF1A0E22, 0xFF6D5DFB, 0xFF6D5DFB, 0xFFD6AD60,
                    MapTheme.DECO_EMBERS, MapTheme.F_DARK | MapTheme.F_FAST_SHADOWS | MapTheme.F_STORM | MapTheme.F_RUSH,
                    Obstacle.GATE, 2.2f, 3.1f, 2, 1, 1, 2, 2, 2, 2, 2, 2),
    };

    /** Sonsuz modun varsayılan teması. */
    public static final MapTheme ENDLESS = ALL[0];
}
