package com.golgen.oyun.core;

/**
 * Basit nesne havuzu: nesneler baştan oluşturulur, oyun sırasında yeniden kullanılır.
 * Aktif nesneler [0, activeCount) aralığında tutulur; silme O(1) takas ile yapılır.
 */
public final class Pool<T> {
    public interface Factory<T> { T create(); }

    private final Object[] items;
    private int active;

    public Pool(int capacity, Factory<T> factory) {
        items = new Object[capacity];
        for (int i = 0; i < capacity; i++) items[i] = factory.create();
    }

    /** Havuz doluysa null döner (performans sınırı). */
    @SuppressWarnings("unchecked")
    public T obtain() {
        if (active >= items.length) return null;
        return (T) items[active++];
    }

    @SuppressWarnings("unchecked")
    public T get(int i) {
        return (T) items[i];
    }

    public int size() {
        return active;
    }

    public int capacity() {
        return items.length;
    }

    /** i indeksindeki aktif nesneyi havuza iade eder. Döngü geriye doğru dönmelidir. */
    public void free(int i) {
        int last = active - 1;
        if (i != last) {
            Object tmp = items[i];
            items[i] = items[last];
            items[last] = tmp;
        }
        active--;
    }

    public void clear() {
        active = 0;
    }
}
