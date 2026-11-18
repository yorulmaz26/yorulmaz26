package com.isikyolu.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** levels.json dosyasındaki bölüm listesi. */
public final class LevelPack {
    public final List<Level> levels = new ArrayList<>();

    public Level get(int id) {
        for (Level l : levels) if (l.id == id) return l;
        return null;
    }

    public int size() { return levels.size(); }

    @SuppressWarnings("unchecked")
    public static LevelPack parse(String json) {
        Map<String, Object> root = (Map<String, Object>) Json.parse(json);
        LevelPack pack = new LevelPack();
        for (Object o : (List<Object>) root.get("levels")) pack.levels.add(Level.fromMap((Map<String, Object>) o));
        return pack;
    }

    /** Okunabilir olması için her bölüm ayrı satıra yazılır. */
    public String toJson() {
        StringBuilder b = new StringBuilder("{\"format\":1,\"game\":\"Işık Yolu\",\"levels\":[\n");
        for (int i = 0; i < levels.size(); i++) {
            b.append(levels.get(i).toJson());
            if (i < levels.size() - 1) b.append(',');
            b.append('\n');
        }
        return b.append("]}\n").toString();
    }

}
