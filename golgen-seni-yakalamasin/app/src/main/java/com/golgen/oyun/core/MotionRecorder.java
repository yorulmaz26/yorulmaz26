package com.golgen.oyun.core;

/**
 * Oyuncunun hareket geçmişini oyun zamanı damgalarıyla tutan sabit boyutlu halka tampon.
 * Oyun duraklatıldığında oyun zamanı ilerlemediği için kayıt ve gölge gecikmesi bozulmaz.
 * Çalışma sırasında hiç bellek ayırmaz.
 */
public final class MotionRecorder {
    private final float[] times;
    private final float[] xs;
    private final float[] ys;
    private final int capacity;
    private int head;   // bir sonraki yazılacak indeks
    private int count;

    public MotionRecorder(int capacity) {
        if (capacity < 2) throw new IllegalArgumentException("capacity");
        this.capacity = capacity;
        times = new float[capacity];
        xs = new float[capacity];
        ys = new float[capacity];
    }

    public void clear() {
        head = 0;
        count = 0;
    }

    public int size() {
        return count;
    }

    public int capacity() {
        return capacity;
    }

    /** Zaman damgaları artan sırada gelmelidir. */
    public void record(float time, float x, float y) {
        if (count > 0 && time < times[idx(count - 1)]) {
            throw new IllegalArgumentException("Zaman geriye gidemez");
        }
        times[head] = time;
        xs[head] = x;
        ys[head] = y;
        head = (head + 1) % capacity;
        if (count < capacity) count++;
    }

    public float oldestTime() {
        return count == 0 ? 0f : times[idx(0)];
    }

    public float newestTime() {
        return count == 0 ? 0f : times[idx(count - 1)];
    }

    /** Mantıksal sıra (0 = en eski) indeksini fiziksel indekse çevirir. */
    private int idx(int logical) {
        int start = (head - count + capacity) % capacity;
        return (start + logical) % capacity;
    }

    /**
     * Verilen zamandaki konumu doğrusal enterpolasyonla döndürür.
     * Zaman kaydın dışında kalırsa en yakın uç değer kullanılır.
     * @return kayıt boşsa false
     */
    public boolean sample(float time, float[] out) {
        if (count == 0) return false;
        int first = idx(0);
        if (time <= times[first]) {
            out[0] = xs[first];
            out[1] = ys[first];
            return true;
        }
        int last = idx(count - 1);
        if (time >= times[last]) {
            out[0] = xs[last];
            out[1] = ys[last];
            return true;
        }
        // İkili arama: times[lo] <= time < times[hi]
        int lo = 0, hi = count - 1;
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (times[idx(mid)] <= time) lo = mid; else hi = mid;
        }
        int a = idx(lo), b = idx(hi);
        float span = times[b] - times[a];
        float k = span <= 0f ? 0f : (time - times[a]) / span;
        out[0] = xs[a] + (xs[b] - xs[a]) * k;
        out[1] = ys[a] + (ys[b] - ys[a]) * k;
        return true;
    }
}
