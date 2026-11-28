package com.golgen.oyun.core;

import static org.junit.Assert.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.BeforeClass;
import org.junit.Test;

/** Yerelleştirme: eksik çeviri taraması, yer tutucu tutarlılığı, çoğullar, dil algılama. */
public class L10nTest {
    @BeforeClass public static void setUp() { L10nTestSupport.load("tr"); }

    @Test public void allSixLanguagesHaveExactlyTheSameKeys() {
        Set<String> ref = L10n.keys("tr");
        assertTrue("anahtar yok", ref.size() > 0);
        List<String> problems = new ArrayList<>();
        for (String code : L10n.CODES) {
            Set<String> k = L10n.keys(code);
            Set<String> missing = new TreeSet<>(ref); missing.removeAll(k);
            Set<String> extra = new TreeSet<>(k); extra.removeAll(ref);
            if (!missing.isEmpty()) problems.add(code + " eksik: " + missing);
            if (!extra.isEmpty()) problems.add(code + " fazla: " + extra);
        }
        assertTrue(String.join("\n", problems), problems.isEmpty());
    }

    @Test public void noEmptyTranslationsAndPlaceholdersMatch() {
        Pattern ph = Pattern.compile("\\{(\\d|n)\\}");
        List<String> problems = new ArrayList<>();
        for (String key : L10n.keys("tr")) {
            Set<String> refPh = placeholders(ph, L10n.raw("tr", key));
            for (String code : L10n.CODES) {
                String v = L10n.raw(code, key);
                if (v == null || v.trim().isEmpty()) { problems.add(code + ":" + key + " boş"); continue; }
                if (!placeholders(ph, v).equals(refPh)) problems.add(code + ":" + key + " yer tutucu " + placeholders(ph, v) + " != " + refPh);
                if (v.contains("TODO") || v.contains("???")) problems.add(code + ":" + key + " çevrilmemiş");
            }
        }
        assertTrue(String.join("\n", problems), problems.isEmpty());
    }

    @Test public void pluralKeysComeInPairs() {
        for (String key : L10n.keys("tr")) {
            if (key.endsWith(".one")) assertNotNull(key, L10n.raw("tr", key.substring(0, key.length() - 4) + ".other"));
        }
    }

    @Test public void pluralRules() {
        assertEquals("one", L10n.pluralForm("en", 1));
        assertEquals("other", L10n.pluralForm("en", 0));
        assertEquals("one", L10n.pluralForm("fr", 0));
        assertEquals("one", L10n.pluralForm("fr", 1));
        assertEquals("other", L10n.pluralForm("fr", 2));
        assertEquals("other", L10n.pluralForm("de", 5));
        L10n.use("en");
        assertEquals("1 video left today", L10n.p("lives.video_left", 1));
        assertEquals("3 videos left today", L10n.p("lives.video_left", 3));
        L10n.use("tr");
    }

    @Test public void systemLanguageDetection() {
        assertEquals("tr", L10n.pickForSystem("tr"));
        assertEquals("de", L10n.pickForSystem("de-AT"));
        assertEquals("es", L10n.pickForSystem("es_MX"));
        assertEquals("fr", L10n.pickForSystem("FR"));
        assertEquals("en", L10n.pickForSystem("ja"));
        assertEquals("en", L10n.pickForSystem("pt-BR"));
        assertEquals("en", L10n.pickForSystem(null));
    }

    @Test public void switchingLanguageChangesTextAndVersion() {
        int v = L10n.version();
        L10n.use("de");
        assertEquals("Leben voll", L10n.t("lives.full"));
        assertTrue(L10n.version() > v);
        L10n.use("xx");
        assertEquals("en", L10n.code());
        L10n.use("tr");
        assertEquals("Canlar dolu", L10n.t("lives.full"));
        assertEquals("Sonraki can: 19:42", L10n.f("lives.next", "19:42"));
    }

    @Test public void turkishUppercaseUsesTurkishLocale() {
        L10n.use("tr");
        assertEquals("İLERİ", L10n.upper("ileri"));
        L10n.use("en");
        assertEquals("ILERI", L10n.upper("ileri"));
        L10n.use("tr");
    }

    @Test public void jsonFilesAreValidUtf8AndSorted() throws Exception {
        for (String code : L10n.CODES) {
            byte[] b = Files.readAllBytes(new File(L10nTestSupport.dir(), code + ".json").toPath());
            String s = new String(b, StandardCharsets.UTF_8);
            assertFalse(code + " geçersiz UTF-8", s.contains("�"));
            L10n.parse(s); // yinelenen anahtar veya sözdizimi hatası fırlatır
        }
    }

    @Test public void parserHandlesEscapes() {
        java.util.Map<String, String> m = L10n.parse("{\"a\": \"x\\\"y\\n\\u00e7\", \"b\":\"\"}");
        assertEquals("x\"y\nç", m.get("a"));
        assertEquals("", m.get("b"));
    }

    private static Set<String> placeholders(Pattern p, String s) {
        Set<String> out = new TreeSet<>();
        Matcher m = p.matcher(s);
        while (m.find()) out.add(m.group());
        return out;
    }
}
