package com.isikyolu.game;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.isikyolu.engine.Achievements;
import com.isikyolu.engine.Chapters;
import com.isikyolu.engine.LightColor;
import com.isikyolu.game.i18n.L;
import com.isikyolu.game.i18n.WorldText;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Dinamik anahtarlar (kodda {@code L.t("chapter." + n + ".name")} gibi kurulan, {@link I18nTest#codeUsesOnlyDefinedKeys}
 * denetimine girmeyen) motorun her dünyası, grubu, konusu, mekaniği, rengi ve başarısı için tr ve en'de tanımlı;
 * Türkçe metinler motorun Türkçe kaynak metinleriyle birebir aynı (Türk oyuncu değişiklik görmez).
 */
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner.class) // org.json için gerçek Android sınıfları
@org.robolectric.annotation.Config(sdk = 34)
public class WorldKeysTest {
    static final int[] COLORS = {LightColor.RED, LightColor.GREEN, LightColor.BLUE, LightColor.YELLOW,
            LightColor.MAGENTA, LightColor.CYAN, LightColor.WHITE, 0};

    @Test public void everyEngineTextHasAKey() throws Exception {
        List<String> problems = new ArrayList<>();
        Map<String, Object> tr = I18nTest.load("tr", problems), en = I18nTest.load("en", problems);
        List<String[]> expected = new ArrayList<>(); // anahtar, Türkçe kaynak
        for (int w = 1; w <= Chapters.WORLDS; w++) {
            expected.add(new String[]{"world." + w + ".name", Chapters.WORLD_NAMES[w - 1]});
            expected.add(new String[]{"world." + w + ".level", Chapters.WORLD_LEVELS[w - 1]});
            expected.add(new String[]{"world." + w + ".intro", Chapters.WORLD_INTROS[w - 1]});
        }
        for (int ch = 1; ch <= Chapters.COUNT; ch++) expected.add(new String[]{"chapter." + ch + ".name", Chapters.NAMES[ch - 1]});
        for (int m = 1; m <= Chapters.MECHANIC_COUNT; m++) {
            expected.add(new String[]{"topic." + m, Chapters.TOPICS[m - 1]});
            expected.add(new String[]{"mechanic." + m, Chapters.MECHANICS[m - 1]});
        }
        for (int c : COLORS) expected.add(new String[]{"color." + LightColor.id(c), LightColor.nameTr(c)});
        for (Achievements.Def d : Achievements.ALL) {
            expected.add(new String[]{"ach." + d.id + ".title", d.title});
            expected.add(new String[]{"ach." + d.id + ".desc", d.description});
        }
        for (String[] e : expected) {
            if (!e[1].equals(tr.get(e[0]))) problems.add("tr: '" + e[0] + "' = " + tr.get(e[0]) + " (beklenen: " + e[1] + ")");
            if (!(en.get(e[0]) instanceof String) || ((String) en.get(e[0])).trim().isEmpty()) problems.add("en: eksik '" + e[0] + "'");
        }
        // dizilerin dışına taşan anahtar yok
        for (String k : new String[]{"world." + (Chapters.WORLDS + 1) + ".name", "chapter." + (Chapters.COUNT + 1) + ".name",
                "topic." + (Chapters.MECHANIC_COUNT + 1), "mechanic." + (Chapters.MECHANIC_COUNT + 1), "chapter.0.name", "topic.0"})
            if (tr.containsKey(k)) problems.add("tr: fazla anahtar '" + k + "'");
        if (!problems.isEmpty()) fail(problems.size() + " sorun:\n" + String.join("\n", problems));
        assertEquals(Chapters.MECHANIC_COUNT, Chapters.TOPICS.length);
        assertEquals(Chapters.MECHANIC_COUNT, Chapters.MECHANICS.length);
    }

    /** Dinamik anahtarlar çalışma anında çözülür; tarih seçili dile göre biçimlenir (Türkçe biçim değişmez). */
    @Test public void runtimeLookupAndDates() {
        android.content.Context app = org.robolectric.RuntimeEnvironment.getApplication();
        int day = 20785; // 28.11.2026, cumartesi
        try {
            L.override = "tr";
            L.init(app);
            assertEquals("28 Kasım 2026, Cumartesi", com.isikyolu.game.screens.DailyScreen.prettyDate(day));
            assertEquals("Işık Bahçesi", WorldText.worldName(1));
            assertEquals("Prizmalar · Zor", WorldText.topic(16));
            assertEquals("İlk Işık", WorldText.achievementTitle(Achievements.ALL.get(0)));
            assertEquals("Camgöbeği", WorldText.color(LightColor.CYAN));
            L.override = "en";
            L.init(app);
            assertEquals("Saturday, November 28, 2026", com.isikyolu.game.screens.DailyScreen.prettyDate(day));
            assertEquals("First Spark", WorldText.chapterName(1));
            assertEquals("Prisms · Hard", WorldText.topic(16));
            assertEquals("Level 7, 3 stars", L.n("levels.tile_desc", 3, 7));
            assertEquals("Level 7, 1 star", L.n("levels.tile_desc", 1, 7));
        } finally {
            L.override = null;
        }
    }
}
