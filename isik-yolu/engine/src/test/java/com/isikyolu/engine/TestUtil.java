package com.isikyolu.engine;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

final class TestUtil {
    private static LevelPack pack;

    static synchronized LevelPack pack() throws Exception {
        if (pack == null) {
            String f = System.getProperty("levels.file", "../app/src/main/assets/levels.json");
            pack = LevelPack.parse(new String(Files.readAllBytes(Paths.get(f)), StandardCharsets.UTF_8));
        }
        return pack;
    }

    static BeamTracer.Result trace(Level l) { return BeamTracer.trace(new Board(l)); }

    static int cell(Level l, int x, int y) { return y * l.width + x; }
}
