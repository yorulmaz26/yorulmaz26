package com.isikyolu.game;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import com.isikyolu.game.i18n.Brand;
import com.isikyolu.game.i18n.L;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Otomatik dil algılama, İngilizce yedek, kalıcı dil seçimi, çoğul kuralları ve iki dilli ad. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class LanguageDetectionTest {
    private Context app;

    @Before public void setUp() { L.override = null; app = RuntimeEnvironment.getApplication(); }
    @After public void tearDown() { L.override = null; }

    @Test @Config(qualifiers = "de")
    public void germanPhoneStartsInGerman() {
        L.init(app);
        assertEquals("de", L.lang());
        assertFalse(L.hasUserChoice(app));
    }

    @Test @Config(qualifiers = "fr-rCA")
    public void regionalVariantUsesLanguage() { L.init(app); assertEquals("fr", L.lang()); }

    @Test @Config(qualifiers = "tr")
    public void turkishPhoneStartsInTurkish() {
        L.init(app);
        assertEquals("tr", L.lang());
        assertEquals("Işık Yolu", Brand.primary());
        assertEquals("Prismway", Brand.secondary());
    }

    @Test @Config(qualifiers = "ja")
    public void unsupportedLanguageFallsBackToEnglish() {
        L.init(app);
        assertEquals("en", L.lang());
        assertEquals("Prismway", Brand.primary());
        assertEquals("Işık Yolu", Brand.secondary());
    }

    @Test @Config(qualifiers = "es")
    public void playerChoicePersistsAndBeatsSystemLanguage() {
        L.init(app);
        assertEquals("es", L.lang());
        L.set(app, "it");
        assertEquals("it", L.lang());
        assertTrue(L.hasUserChoice(app));
        L.init(app); // yeniden açılış
        assertEquals("it", L.lang());
        L.set(app, "xx"); // desteklenmeyen kod yok sayılır
        assertEquals("it", L.lang());
    }

    @Test public void pluralRules() {
        assertEquals("one", L.pluralCategory("en", 1));
        assertEquals("other", L.pluralCategory("en", 0));
        assertEquals("other", L.pluralCategory("de", 2));
        assertEquals("one", L.pluralCategory("fr", 0)); // Fransızcada 0 ve 1 tekil
        assertEquals("one", L.pluralCategory("fr", 1));
        assertEquals("other", L.pluralCategory("fr", 2));
        L.override = "en";
        L.init(app);
        assertEquals("1 crystal", L.n("common.crystals", 1));
        assertEquals("1,200 crystals", L.n("common.crystals", 1200));
        L.override = "tr";
        L.init(app);
        assertEquals("1.200 kristal", L.n("common.crystals", 1200));
        assertEquals("Bölüm 12", L.t("common.level_n", 12));
    }

    @Test public void everyLanguageLoadsAndHasSpecialCharacters() {
        for (String code : L.SUPPORTED) {
            L.override = code;
            L.init(app);
            assertEquals(code, L.lang());
            String ok = L.t("common.ok");
            assertFalse(code + " common.ok", ok.equals("common.ok") || ok.isEmpty());
        }
        L.override = "tr";
        L.init(app);
        assertTrue(L.t("lives.full_title").contains("ı")); // "Canların doldu!" — Türkçe karakterler bozulmaz
    }
}
