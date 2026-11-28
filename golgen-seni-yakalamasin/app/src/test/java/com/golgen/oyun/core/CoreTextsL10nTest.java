package com.golgen.oyun.core;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Çekirdek oyun metinleri (haritalar, kademeler, özellikler, bölümler, başarılar, karakterler,
 * günlük görevler, güçlendirmeler) altı dilin hepsinde gerçek metne çözülmeli: anahtar adı,
 * doldurulmamış yer tutucu veya İngilizceye düşme (eksik çeviri) olmamalı.
 */
public class CoreTextsL10nTest {
    private static final Pattern KEY_LIKE = Pattern.compile("(^|[^\\p{L}])(map|tier|feature|level|ach|skin|daily|powerup|run)\\.[a-z0-9_]");

    @BeforeClass public static void setUp() { L10nTestSupport.load("tr"); }

    @AfterClass public static void restore() { L10n.use("tr"); }

    private static List<String> collect() {
        List<String> out = new ArrayList<>();
        for (MapTheme m : Maps.ALL) {
            out.add(m.name());
            out.add(m.desc());
            out.add(m.featuresText());
        }
        for (int k = 0; k < Maps.TIERS; k++) out.add(Maps.tierName(k));
        for (int f = 0; f < MapTheme.FEATURE_COUNT; f++) out.add(MapTheme.featureName(f));
        for (MissionDef d : Missions.ALL) {
            out.add(d.title());
            out.add(d.description());
            out.add(d.goal());
            out.add(d.modsText());
            out.add(d.crownText());
        }
        for (int i = 0; i < Achievements.COUNT; i++) {
            out.add(Achievements.title(i));
            out.add(Achievements.desc(i));
        }
        for (SkinDef s : Skins.ALL) out.add(s.name());
        for (int t = 0; t < DailyMissions.TYPE_COUNT; t++) {
            out.add(DailyMissions.text(t, 0));
            out.add(DailyMissions.text(t, 1));
            out.add(DailyMissions.text(t, 7));
        }
        for (int p = 0; p < PowerUp.COUNT; p++) out.add(PowerUp.name(p));
        return out;
    }

    @Test public void everyCoreTextResolvesInAllLanguages() {
        List<String> problems = new ArrayList<>();
        try {
            for (String code : L10n.CODES) {
                L10n.use(code);
                Set<String> before = L10n.missingSeen();
                for (String s : collect()) {
                    if (s == null || s.trim().isEmpty()) { problems.add(code + ": boş metin"); continue; }
                    if (KEY_LIKE.matcher(s).find()) problems.add(code + ": anahtar görünüyor: " + s);
                    if (s.indexOf('{') >= 0 || s.indexOf('}') >= 0) problems.add(code + ": doldurulmamış yer tutucu: " + s);
                }
                Set<String> missing = new TreeSet<>(L10n.missingSeen());
                missing.removeAll(before);
                for (String m : missing) if (m.startsWith(code + ":")) problems.add("eksik çeviri " + m);
            }
        } finally {
            L10n.use("tr");
        }
        assertTrue(String.join("\n", problems), problems.isEmpty());
    }

    @Test public void namesAreDistinctInEveryLanguage() {
        try {
            for (String code : L10n.CODES) {
                L10n.use(code);
                Set<String> maps = new HashSet<>();
                for (MapTheme m : Maps.ALL) assertTrue(code + ": " + m.name(), maps.add(m.name()));
                for (int k = 1; k < Maps.TIERS; k++) {
                    for (int b = 0; b < Maps.BASE.length; b++) {
                        MapTheme t = Maps.ALL[k * Maps.BASE.length + b];
                        assertTrue(code + ": " + t.name(), t.name().startsWith(Maps.ALL[b].name()));
                    }
                }
                Set<String> skins = new HashSet<>();
                for (SkinDef s : Skins.ALL) assertTrue(code + ": " + s.name(), skins.add(s.name()));
                Set<String> ach = new HashSet<>();
                for (int i = 0; i < Achievements.COUNT; i++) assertTrue(code + ": " + Achievements.title(i), ach.add(Achievements.title(i)));
                Set<String> pw = new HashSet<>();
                for (int p = 0; p < PowerUp.COUNT; p++) assertTrue(code + ": " + PowerUp.name(p), pw.add(PowerUp.name(p)));
            }
        } finally {
            L10n.use("tr");
        }
    }

    /** Türkçe metinler yerelleştirme öncesi kaynak koddaki metinlerle birebir aynı kalmalı. */
    @Test public void turkishTextsUnchanged() {
        L10n.use("tr");
        assertEquals(MapTheme.BASE_COUNT, Maps.BASE.length);
        assertEquals("Neon Kapı", Maps.ALL[0].name());
        assertEquals("Her şeyin başladığı yer", Maps.ALL[0].desc());
        assertEquals("Klasik", Maps.ALL[0].featuresText());
        assertEquals("Rüzgâr", Maps.ALL[2].featuresText());
        assertEquals("Rüzgâr · Meteor Yağmuru", Maps.ALL[8].featuresText());
        assertEquals("Neon Kapı II · Gece", Maps.ALL[20].name());
        assertEquals("Her şeyin başladığı yer — gece hâli", Maps.ALL[20].desc());
        assertEquals("Kademenin son durağı: tehlikeler bir arada — gece hâli", Maps.ALL[39].desc());
        assertEquals("Gölge Tahtı V · Efsane", Maps.ALL[99].name());
        assertEquals("Kademenin son durağı: tehlikeler bir arada — efsane hâli", Maps.ALL[99].desc());
        assertEquals("Gölge Tahtı", Maps.ALL[19].name());
        assertEquals("Neon Kapı", Maps.ENDLESS.name());
        assertEquals("Gece", Maps.tierName(1));
        MissionDef d = Missions.ALL[0];
        assertEquals("Bölüm 1", d.title());
        assertEquals("Bölüm 500", Missions.ALL[499].title());
        assertEquals("2 aşama · her aşamada 20 yıldız topla", d.goal());
        assertEquals("Neon Kapı — 2 aşama · her aşamada 20 yıldız topla", d.description());
        assertEquals("Tamamla · Çarpmadan bitir · " + d.starCombo + " kombo", d.crownText());
        assertEquals("İlk Adım", Achievements.title(0));
        assertEquals("İlk oyununu oyna", Achievements.desc(0));
        assertEquals("Toplam 50 kez gölgeden kaç", Achievements.desc(1));
        assertEquals("Tek oyunda 60 sn dayan", Achievements.desc(2));
        assertEquals("İlk haritayı bitir (5 bölüm)", Achievements.desc(9));
        assertEquals("500 bölümün hepsini bitir", Achievements.desc(11));
        assertEquals("1500 tacın hepsini kazan", Achievements.desc(13));
        assertEquals("Tek oyunda 10000 puan yap", Achievements.desc(16));
        assertEquals("10. seviyeye ulaş", Achievements.desc(20));
        assertEquals("Usta Oyuncu", Achievements.title(20));
        assertEquals("Efsane Kristal", Skins.ALL[11].name());
        assertEquals("3 oyun oyna", DailyMissions.text(DailyMissions.PLAY, 3));
        assertEquals("Toplam 15 elmas topla", DailyMissions.text(DailyMissions.COLLECT, 15));
        assertEquals("Tek oyunda 40 sn hayatta kal", DailyMissions.text(DailyMissions.SURVIVE_SINGLE, 40));
        assertEquals("Toplam 8 kez gölgeden kaç", DailyMissions.text(DailyMissions.DODGE, 8));
        assertEquals("Tek oyunda 600 puan yap", DailyMissions.text(DailyMissions.SCORE_SINGLE, 600));
        assertEquals("5 güçlendirme topla", DailyMissions.text(DailyMissions.POWERUPS, 5));
        assertEquals("2 bölüm tamamla", DailyMissions.text(DailyMissions.MISSION_WINS, 2));
        assertEquals("Tek oyunda 12 kombo yap", DailyMissions.text(DailyMissions.COMBO_SINGLE, 12));
        assertEquals("Kalkan", PowerUp.name(PowerUp.SHIELD));
        assertEquals("Yavaş Zaman", PowerUp.name(PowerUp.SLOW));
        assertEquals("Mıknatıs", PowerUp.name(PowerUp.MAGNET));
    }

    @SuppressWarnings("deprecation")
    @Test public void deprecatedTitleBridgeFollowsLanguage() {
        try {
            L10n.use("tr");
            assertEquals("Bölüm 3", "" + Missions.ALL[2].title);
            L10n.use("es");
            assertEquals("Nivel 3", "" + Missions.ALL[2].title);
        } finally {
            L10n.use("tr");
        }
    }

    @Test public void languageSwitchRefreshesCachedTextsAndPlurals() {
        try {
            L10n.use("tr");
            assertEquals("Neon Kapı", Maps.ALL[0].name());
            assertEquals("Bölüm 1", Missions.ALL[0].title());
            L10n.use("en");
            assertEquals("Neon Gate", Maps.ALL[0].name());
            assertEquals("Neon Gate II · Night", Maps.ALL[20].name());
            assertEquals("Level 1", Missions.ALL[0].title());
            assertEquals("Neon Gate — 2 stages · collect 20 stars per stage", Missions.ALL[0].description());
            assertEquals("Play 1 game", DailyMissions.text(DailyMissions.PLAY, 1));
            assertEquals("Play 4 games", DailyMissions.text(DailyMissions.PLAY, 4));
            assertEquals("Unlock 4 characters", Achievements.desc(7));
            assertEquals("Reach player level 10", Achievements.desc(20));
            assertEquals("Slow Time", PowerUp.name(PowerUp.SLOW));
            L10n.use("fr");
            assertEquals("Joue 1 partie", DailyMissions.text(DailyMissions.PLAY, 1));
            assertEquals("Joue 0 partie", DailyMissions.text(DailyMissions.PLAY, 0));
            assertEquals("Joue 3 parties", DailyMissions.text(DailyMissions.PLAY, 3));
            L10n.use("de");
            assertEquals("Neontor", Maps.ALL[0].name());
            assertEquals("Spiele 1 Runde", DailyMissions.text(DailyMissions.PLAY, 1));
            L10n.use("tr");
            assertEquals("Neon Kapı", Maps.ALL[0].name());
            assertEquals("Bölüm 1", Missions.ALL[0].title());
        } finally {
            L10n.use("tr");
        }
    }
}
