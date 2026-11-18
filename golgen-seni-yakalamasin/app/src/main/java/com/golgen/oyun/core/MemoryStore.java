package com.golgen.oyun.core;

import java.util.HashMap;
import java.util.Map;

/** Bellek içi kayıt (testler için). */
public final class MemoryStore implements KeyValueStore {
    public final Map<String, Object> map = new HashMap<>();
    public int applyCount;

    @Override public int getInt(String k, int d) { Object v = map.get(k); return v instanceof Integer ? (Integer) v : d; }
    @Override public void putInt(String k, int v) { map.put(k, v); }
    @Override public String getString(String k, String d) { Object v = map.get(k); return v instanceof String ? (String) v : d; }
    @Override public void putString(String k, String v) { map.put(k, v); }
    @Override public void apply() { applyCount++; }
}
