package com.golgen.oyun.core;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Birim testlerinde dil dosyalarını (app/src/main/assets/localization) yükler. */
public final class L10nTestSupport {
    private L10nTestSupport() {}

    public static File dir() {
        String[] candidates = {"src/main/assets/localization", "app/src/main/assets/localization",
                "golgen-seni-yakalamasin/app/src/main/assets/localization"};
        for (String c : candidates) {
            File f = new File(c);
            if (f.isDirectory()) return f;
        }
        throw new IllegalStateException("localization klasörü bulunamadı (çalışma dizini: " + new File(".").getAbsolutePath() + ")");
    }

    /** Tüm dilleri yükler ve etkin dili seçer. */
    public static synchronized void load(String active) {
        try {
            for (String code : L10n.CODES) {
                if (!L10n.isLoaded(code)) {
                    L10n.load(code, new String(Files.readAllBytes(new File(dir(), code + ".json").toPath()), StandardCharsets.UTF_8));
                }
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        L10n.use(active);
    }
}
