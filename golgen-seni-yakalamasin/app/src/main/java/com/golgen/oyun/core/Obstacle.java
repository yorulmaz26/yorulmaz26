package com.golgen.oyun.core;

/** Havuzlanan engel. Tür alanına göre farklı alanlar kullanılır. */
public final class Obstacle {
    public static final int WALL = 0;     // boşluklu yatay duvar
    public static final int SWEEPER = 1;  // yatay salınan blok
    public static final int ROTOR = 2;    // dönen lazer çubuğu
    public static final int MINE = 3;     // nabız atan mayın
    public static final int GATE = 4;     // yanıp sönen lazer kapı
    public static final int BOUNCER = 5;  // duvarlardan seken top
    public static final int METEOR = 6;   // hızlı düşen kaya (üstte uyarı)
    public static final int ORBITER = 7;  // merkez etrafında dönen çift top
    public static final int ZIGZAG = 8;   // boşluğu sağa sola kayan duvar
    public static final int TYPE_COUNT = 9;
    /** Ağırlık dizisi sırası (Maps): WALL, MINE, SWEEPER, ROTOR, GATE, BOUNCER, METEOR, ORBITER, ZIGZAG. */
    public static final int[] WEIGHT_ORDER = {WALL, MINE, SWEEPER, ROTOR, GATE, BOUNCER, METEOR, ORBITER, ZIGZAG};

    public static final float GATE_CYCLE = 2.6f, GATE_WARN = 1.0f, GATE_ON = 1.6f; // [0,1.0) kapalı, [1.0,1.6) uyarı, [1.6,2.6) açık
    public static final float ORBIT_R = 150f, ORBIT_BALL = 30f, BOUNCER_R = 34f, METEOR_R = 24f;

    public int type;
    public float y;           // merkez/üst referans (dünya birimi)
    public float x;           // merkez x (SWEEPER, ROTOR, MINE)
    public float gapX;        // WALL: boşluğun merkezi
    public float gapW;        // WALL: boşluk genişliği
    public float w;           // SWEEPER: genişlik, ROTOR: uzunluk
    public float h;           // kalınlık
    public float r;           // MINE: taban yarıçap
    public float phase;       // animasyon/hareket fazı
    public float speed;       // SWEEPER/ROTOR açısal veya yatay hız
    public float amp;         // SWEEPER salınım genliği
    public float baseX;       // SWEEPER orijin
    public float angle;       // ROTOR açısı
    public boolean passed;    // oyuncunun altından geçtiği işaretlendi mi
    public float vx;          // BOUNCER yatay hız

    public void reset() {
        type = WALL;
        y = x = gapX = gapW = w = h = r = phase = speed = amp = baseX = angle = vx = 0f;
        passed = false;
    }

    /** MINE'ın o anki yarıçapı. */
    public float mineRadius() {
        return r * (1f + 0.18f * (float) Math.sin(phase * 5f));
    }

    /** GATE: lazer şu an zarar veriyor mu? */
    public boolean gateActive() {
        float t = phase % GATE_CYCLE;
        return t >= GATE_ON;
    }

    /** GATE: uyarı (yanıp sönme) evresinde mi? */
    public boolean gateWarning() {
        float t = phase % GATE_CYCLE;
        return t >= GATE_WARN && t < GATE_ON;
    }

    /** Engelin dikeyde kapladığı yarı yükseklik (ekran dışı kontrolü için). */
    public float halfExtent() {
        switch (type) {
            case ROTOR: return w * 0.5f + h;
            case MINE: return r * 1.3f;
            case ORBITER: return ORBIT_R + ORBIT_BALL;
            case BOUNCER: return BOUNCER_R;
            case METEOR: return METEOR_R;
            default: return h * 0.5f;
        }
    }
}
