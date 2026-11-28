package com.golgen.oyun.core;

/** Yerel kayıt soyutlaması (Android'de SharedPreferences, testlerde bellek içi). */
public interface KeyValueStore {
    int getInt(String key, int def);
    void putInt(String key, int value);
    String getString(String key, String def);
    void putString(String key, String value);
    void apply();
    /** Eşzamanlı yazma (varsayılan: apply). */
    default void commit() { apply(); }
}
