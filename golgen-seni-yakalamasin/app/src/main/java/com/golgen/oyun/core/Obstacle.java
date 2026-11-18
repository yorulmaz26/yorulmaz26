package com.golgen.oyun.core;

/** Havuzlanan engel. Tür alanına göre farklı alanlar kullanılır. */
public final class Obstacle {
    public static final int WALL = 0;     // boşluklu yatay duvar
    public static final int SWEEPER = 1;  // yatay salınan blok
    public static final int ROTOR = 2;    // dönen lazer çubuğu
    public static final int MINE = 3;     // nabız atan mayın

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

    public void reset() {
        type = WALL;
        y = x = gapX = gapW = w = h = r = phase = speed = amp = baseX = angle = 0f;
        passed = false;
    }

    /** MINE'ın o anki yarıçapı. */
    public float mineRadius() {
        return r * (1f + 0.18f * (float) Math.sin(phase * 5f));
    }

    /** Engelin dikeyde kapladığı yarı yükseklik (ekran dışı kontrolü için). */
    public float halfExtent() {
        switch (type) {
            case ROTOR: return w * 0.5f + h;
            case MINE: return r * 1.3f;
            default: return h * 0.5f;
        }
    }
}
