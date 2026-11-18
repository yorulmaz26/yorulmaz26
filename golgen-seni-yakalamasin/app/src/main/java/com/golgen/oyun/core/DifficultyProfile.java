package com.golgen.oyun.core;

/**
 * Zorluk eğrisini tanımlayan veri nesnesi (Unity'deki ScriptableObject karşılığı).
 * Oyun modları ve görevler farklı profiller kullanır; kod değiştirmeden ayar yapılabilir.
 */
public final class DifficultyProfile {
    public float rampSeconds = 150f;     // zorluğun maksimuma ulaşma süresi
    public float timeOffset = 0f;        // görevlerde zorluğa ön başlangıç
    public float scrollStart = 260f, scrollMax = 560f;          // birim/sn
    public float intervalStart = 1.85f, intervalMin = 0.95f;     // engel aralığı (sn)
    public float gapStart = 340f, gapMin = 215f;                 // duvar boşluğu (birim)
    public float mineFrom = 12f, sweeperFrom = 28f, rotorFrom = 48f; // engel türlerinin açılma zamanı (zorluk zamanı)
    public float diamondIntervalMin = 2.4f, diamondIntervalMax = 4.2f;
    public float[] shadowSpawnTimes = {3.0f, 20f, 40f, 62f, 88f};
    public float[] shadowDelays = {3.0f, 4.6f, 2.3f, 6.0f, 3.7f};
    public int maxShadows = 5;
    public int lives = 3;
    public float powerupIntervalMin = 12f, powerupIntervalMax = 19f;
    /** Zorluk tavana ulaştıktan sonra kayma hızına saniye başına eklenen değer (sonsuz modun hiç bitmeyen artışı). */
    public float overdrivePerSecond = 1.0f, overdriveMax = 260f;
    public float scrollMul = 1f;

    public static DifficultyProfile endless() {
        return new DifficultyProfile();
    }

    public float maxDelay() {
        float m = 0f;
        for (float d : shadowDelays) m = Math.max(m, d);
        return m;
    }

    /** 0..1 arası yumuşatılmış zorluk. */
    public float level(float time) {
        float t = (time + timeOffset) / rampSeconds;
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        return t * (2f - t); // ease-out
    }

    public float difficultyTime(float time) {
        return time + timeOffset;
    }

    public float scroll(float d) { return (scrollStart + (scrollMax - scrollStart) * d) * scrollMul; }

    /** Tavandan sonraki ek hız. */
    public float overdrive(float time) {
        float over = time + timeOffset - rampSeconds;
        if (over <= 0f) return 0f;
        return Math.min(overdriveMax, over * overdrivePerSecond);
    }
    public float interval(float d) { return intervalStart + (intervalMin - intervalStart) * d; }
    public float gap(float d) { return gapStart + (gapMin - gapStart) * d; }
}
