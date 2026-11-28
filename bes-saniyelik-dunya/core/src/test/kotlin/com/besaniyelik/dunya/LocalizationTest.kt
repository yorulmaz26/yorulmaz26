package com.besaniyelik.dunya

import com.besaniyelik.dunya.i18n.L
import com.besaniyelik.dunya.i18n.LevelText
import com.besaniyelik.dunya.i18n.PlayerNames
import com.besaniyelik.dunya.level.*
import com.besaniyelik.dunya.progress.*
import com.besaniyelik.dunya.sim.DeathCause
import com.besaniyelik.dunya.sim.Mechanic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.*

/**
 * Yerelleştirme denetimi: eksik çeviri, yer tutucu, kodda kullanılan her anahtarın varlığı, yazı tipinde olmayan
 * karakter, bölüm verisindeki Türkçe metinlerin çevrilebilmesi ve arayüz kodunda kalmış sabit Türkçe metin.
 */
class LocalizationTest {
    companion object {
        private val dir = System.getProperty("assetsDir") ?: "../assets"
        @JvmStatic @BeforeAll fun setup() {
            L.reader = { p -> File(dir, p).takeIf { it.exists() }?.readText() }
            LevelRepository.reader = { File(dir, it).readText() }
        }
        val languages: List<String> get() = File(dir, "i18n").listFiles()!!.filter { it.extension == "json" }.map { it.nameWithoutExtension }.sorted()
        private val placeholder = Regex("\\{[a-z]+\\}")
    }

    @AfterEach fun reset() { L.use("tr") }

    @DisplayName("Desteklenen 6 dilin dosyası var; her dil kaynakla (tr) aynı anahtarlara sahip")
    @Test fun noMissingKeys() {
        assertEquals(GameConfig.LANGUAGES.sorted(), languages.filter { it in GameConfig.LANGUAGES }.sorted(), "dil dosyaları eksik")
        val src = L.table("tr")
        for (lang in GameConfig.LANGUAGES) {
            val t = L.table(lang)
            val missing = src.keys - t.keys
            val extra = t.keys - src.keys
            assertTrue(missing.isEmpty(), "$lang: eksik çeviri ${missing.size}: ${missing.take(10)}")
            assertTrue(extra.isEmpty(), "$lang: fazla anahtar: ${extra.take(10)}")
            for ((k, v) in t) {
                if (src[k]!!.isNotEmpty()) assertTrue(v.isNotBlank(), "$lang: $k boş")
                if (!k.endsWith(".one")) assertEquals(placeholder.findAll(src[k]!!).map { it.value }.toSet(), placeholder.findAll(v).map { it.value }.toSet(), "$lang: $k yer tutucuları farklı")
            }
        }
    }

    @DisplayName("Kodda kullanılan bütün sabit anahtarlar kaynak dosyada var")
    @Test fun codeKeysExist() {
        val src = L.table("tr")
        val code = File("$dir/../core/src/main/kotlin").walkTopDown().filter { it.extension == "kt" }.map { it.readText() }.joinToString("\n")
        val used = Regex("L\\.(t|n|list|raw)\\(\"([^\"$]+)\"").findAll(code).map { it.groupValues[1] to it.groupValues[2] }.toSet()
        assertTrue(used.size > 150, "anahtar taraması çalışmadı")
        for ((fn, k) in used) {
            if (fn == "n") assertTrue("$k.other" in src && "$k.one" in src, "çoğul anahtar yok: $k")
            else assertTrue(k in src, "anahtar yok: $k")
        }
    }

    @DisplayName("Oyun içeriğinin (kurallar, haritalar, alanlar, karakterler, başarılar, görevler) her dilde metni var")
    @Test fun dynamicKeysExist() {
        for (lang in GameConfig.LANGUAGES) {
            L.use(lang)
            fun ok(s: String, what: String) { assertTrue(s.isNotBlank() && !s.contains('{') && !s.startsWith("mech.") && !s.startsWith("map."), "$lang: $what -> '$s'") }
            Mechanic.entries.forEach { ok(it.title, "kural"); ok(it.hint, "kural ipucu"); ok(it.short, "kısa ad"); if (it != Mechanic.NORMAL) ok(it.detail, "kural ayrıntısı") }
            PowerupType.entries.forEach { ok(it.title, "güçlendirme"); ok(it.desc, "güçlendirme açıklaması") }
            DeathCause.entries.forEach { ok(it.title, "ölüm") }
            GameContent.maps.forEach { ok(it.name, "harita"); ok(it.subtitle, "harita alt başlık") }
            GameContent.stages.forEach { ok(it.title, "alan ${it.number}"); ok(it.desc, "alan açıklaması ${it.number}") }
            Catalog.skins.forEach { ok(it.name, "karakter ${it.id}") }
            Catalog.trails.forEach { ok(it.name, "iz ${it.id}") }
            Catalog.achievements.forEach { ok(it.title, "başarı ${it.id}"); ok(it.desc, "başarı açıklaması ${it.id}") }
            MissionType.entries.forEach { ok(it.text(1), "görev"); ok(it.text(5), "görev") }
            PurchaseResult.entries.forEach { ok(it.message, "satın alma") }
            LevelKind.entries.forEach { ok(L.t("level.kind.${it.name}"), "bölüm türü") }
            Catalog.loginRewards.forEach { ok(it.describe(), "giriş ödülü") }
            (1..99).forEach { ok(ProgressionManager.levelUpText(it), "seviye") }
            PlayerNames.SOURCE.forEach { ok(PlayerNames.display(it), "oyuncu adı") }
            assertEquals(8, L.list("player.names").size)
        }
    }

    @DisplayName("Türkçe bölüm adları kayıtlı bölüm verisiyle birebir aynı; diğer dillerde 500 bölüm adı düzgün")
    @Test fun levelNames() {
        L.use("tr")
        val all = (1..GameContent.LEVEL_COUNT).map { LevelRepository.level(it) }
        all.forEach { assertEquals(it.name, LevelText.name(it), "bölüm ${it.id}") }
        all.forEach { assertEquals(it.idea, GameContent.stageOf(it.id).trTitle) }
        for (lang in GameConfig.LANGUAGES) {
            L.use(lang)
            val names = all.map { LevelText.name(it) }
            names.forEach { n -> assertTrue(n.isNotBlank() && '{' !in n && ':' !in n.takeLast(2) && "  " !in n, "$lang: '$n'") }
            assertTrue(names.toSet().size > 300, "$lang: bölüm adları çeşitli olmalı (${names.toSet().size})")
        }
    }

    @DisplayName("Bölüm verisindeki bütün ipuçları her dile çevrilebiliyor")
    @Test fun hints() {
        val hints = (1..GameContent.LEVEL_COUNT).flatMap { LevelRepository.level(it).hints }.distinctBy { it.text }
        assertTrue(hints.size >= 20)
        for (lang in GameConfig.LANGUAGES.filter { it != "tr" }) {
            L.use(lang)
            hints.forEach { h -> assertNotEquals(h.text, LevelText.hint(h), "$lang: çevrilmeyen ipucu '${h.text}'") }
        }
    }

    @DisplayName("Dil algılama: desteklenen dil seçilir, desteklenmeyen dilde İngilizce")
    @Test fun detect() {
        assertEquals("de", L.detect("de")); assertEquals("tr", L.detect("tr-TR")); assertEquals("es", L.detect("es_MX"))
        assertEquals("fr", L.detect("FR")); assertEquals("it", L.detect("it"))
        assertEquals("en", L.detect("pt")); assertEquals("en", L.detect("ja")); assertEquals("en", L.detect(null)); assertEquals("en", L.detect(""))
        L.use("xx"); assertEquals("en", L.lang)
    }

    @DisplayName("Tekil/çoğul ve dile göre ondalık ayırıcı")
    @Test fun plurals() {
        L.use("en")
        assertEquals("1 coin", L.n("unit.coins", 1)); assertEquals("2 coins", L.n("unit.coins", 2)); assertEquals("0 coins", L.n("unit.coins", 0))
        L.use("tr"); assertEquals("5 altın", L.n("unit.coins", 5))
        L.use("fr"); assertEquals(L.raw("unit.coins.one").replace("{n}", "0"), L.n("unit.coins", 0), "Fransızcada 0 tekildir")
        L.use("de"); assertEquals("4,5", L.dec(4.5f)); L.use("en"); assertEquals("4.5", L.dec(4.5f))
    }

    @DisplayName("Dil dosyalarındaki her karakter oyunun yazı tipinde ve yazı tipi karakter listesinde var")
    @Test fun glyphs() {
        val font = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, File(dir, "fonts/Nunito-ExtraBold.ttf"))
        val list = Assets.FONT_CHARS.toSet()
        for (lang in GameConfig.LANGUAGES) {
            val chars = L.table(lang).values.joinToString("").toSet().filter { it != '\n' }
            val bad = chars.filter { it !in list || !font.canDisplay(it) }
            assertTrue(bad.isEmpty(), "$lang: yazı tipinde olmayan karakterler: ${bad.joinToString { "'$it' U+%04X".format(it.code) }}")
        }
    }

    @DisplayName("Arayüz kodunda sabit Türkçe metin kalmadı (metinler dil dosyalarından gelir)")
    @Test fun noHardcodedUiText() {
        val turkish = Regex("[çğıöşüÇĞİÖŞÜ]")
        val offenders = File("$dir/../core/src/main/kotlin/com/besaniyelik/dunya/ui").walkTopDown().filter { it.extension == "kt" }.flatMap { f ->
            f.readLines().withIndex().filter { (_, line) ->
                val code = line.substringBefore("//").trim()
                !code.startsWith("*") && !code.startsWith("/*") && Regex("\"([^\"]*)\"").findAll(code).any { turkish.containsMatchIn(it.groupValues[1]) }
            }.map { "${f.name}:${it.index + 1}: ${it.value.trim()}" }
        }.toList()
        assertTrue(offenders.isEmpty(), "sabit metin:\n" + offenders.joinToString("\n"))
    }
}
