package com.isikyolu.engine;

import java.util.Arrays;

/** Kutulama yapmayan açık adresli long→int tablosu (çözücü için). */
final class LongIntMap {
    private long[] keys;
    private int[] vals;
    private boolean[] used;
    private int size, mask;

    LongIntMap(int cap) {
        int c = 16;
        while (c < cap * 2) c <<= 1;
        keys = new long[c]; vals = new int[c]; used = new boolean[c]; mask = c - 1;
    }

    private static int hash(long k) {
        k ^= k >>> 33; k *= 0xff51afd7ed558ccdL; k ^= k >>> 33;
        return (int) k;
    }

    int get(long k, int def) {
        int i = hash(k) & mask;
        while (used[i]) {
            if (keys[i] == k) return vals[i];
            i = (i + 1) & mask;
        }
        return def;
    }

    /** Anahtar yoksa ekler ve true döndürür. */
    boolean putIfAbsent(long k, int v) {
        if (size * 2 >= keys.length) grow();
        int i = hash(k) & mask;
        while (used[i]) {
            if (keys[i] == k) return false;
            i = (i + 1) & mask;
        }
        used[i] = true; keys[i] = k; vals[i] = v; size++;
        return true;
    }

    int size() { return size; }

    private void grow() {
        long[] ok = keys; int[] ov = vals; boolean[] ou = used;
        int c = ok.length * 2;
        keys = new long[c]; vals = new int[c]; used = new boolean[c]; mask = c - 1; size = 0;
        for (int i = 0; i < ok.length; i++) if (ou[i]) putIfAbsent(ok[i], ov[i]);
    }

    void clear() { Arrays.fill(used, false); size = 0; }
}
