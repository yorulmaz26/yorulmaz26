package com.yanlisrenk.logic

import com.yanlisrenk.logic.i18n.L
import com.yanlisrenk.logic.level.AreaCatalog
import com.yanlisrenk.logic.model.GameColor
import com.yanlisrenk.logic.model.Mechanic
import com.yanlisrenk.logic.progress.Achievements
import com.yanlisrenk.logic.progress.DailyRewards
import com.yanlisrenk.logic.progress.Missions
import com.yanlisrenk.logic.progress.WeeklyChallenge
import com.yanlisrenk.logic.shop.Currency
import com.yanlisrenk.logic.shop.ShopCatalog
import com.yanlisrenk.logic.shop.ShopCategory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Eksik çeviri denetimi.
 * - Koddaki tüm katalog metinlerinin (dünyalar, alanlar, ürünler, başarılar, görevler...) anahtarı tr.json'da olmalı ve
 *   Türkçe değeri koddakiyle aynı olmalı.
 * - Arayüz kodunda kullanılan her L.t/L.f anahtarı tr.json'da olmalı.
 * - Her dil dosyası tr.json'daki tüm anahtarları boş olmayan metinle içermeli; {n} yer tutucuları aynı olmalı.
 */
class LocalizationTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
    private val dir = File(root, "android/assets/localization")

    companion object {
        fun catalogKeys(): LinkedHashMap<String, String> {
            val m = LinkedHashMap<String, String>()
            AreaCatalog.worlds.forEach { m["world.${it.id}.name"] = it.nameTr }
            AreaCatalog.areas.forEach {
                m["area.${it.id}.name"] = it.nameTr; m["area.${it.id}.desc"] = it.descriptionTr
                m["area.${it.id}.final"] = it.finalTitleTr; m["area.${it.id}.collectible"] = it.collectibleTr
            }
            ShopCategory.entries.forEach { m["shopcat.${it.name}"] = it.titleTr }
            Currency.entries.forEach { m["currency.${it.name}"] = it.trName }
            ShopCatalog.all.forEach { m["item.${it.id}.name"] = it.nameTr; m["item.${it.id}.desc"] = it.descriptionTr }
            Mechanic.entries.forEach { m["mech.${it.name}.title"] = it.titleTr; m["mech.${it.name}.hint"] = it.hintTr }
            GameColor.entries.forEach { m["color.${it.name}"] = it.trName; m["color.${it.name}.upper"] = it.trUpper }
            Achievements.all.filter { it.family.isNotEmpty() }.groupBy { it.family }.forEach { (fam, list) ->
                if (fam != "world") m["ach.$fam.title"] = list.first().familyTitleTr
                m["ach.$fam.desc"] = list.first().descPatternTr
            }
            Missions.templates.forEach { m["mission.${it.id}"] = it.title.replace("%d", "{0}") }
            m["reward.gold"] = "{0} Altın"; m["reward.diamonds"] = "{0} Elmas"
            WeeklyChallenge.themes.forEachIndexed { i, t -> m["weekly.theme.$i"] = t.first }
            return m
        }
        private val PH = Regex("""\{(\d+)[,}]""")
        fun placeholders(s: String): Set<String> = PH.findAll(s).map { it.groupValues[1] }.toSet()
    }

    private fun load(code: String): Map<String, String> {
        val f = File(dir, "$code.json")
        if (!f.exists()) return emptyMap()
        val o = Json.parseToJsonElement(f.readText()).jsonObjectOrEmpty()
        return o.filter { !it.key.startsWith("_") }.mapValues { (it.value as JsonPrimitive).content }
    }
    private fun kotlinx.serialization.json.JsonElement.jsonObjectOrEmpty(): JsonObject = this as? JsonObject ?: JsonObject(emptyMap())

    @Test fun katalogDisaAktar() {
        val out = File(root, "logic/build/i18n_catalog.json")
        out.parentFile.mkdirs()
        val sb = StringBuilder("{\n")
        val keys = catalogKeys()
        keys.entries.forEachIndexed { i, (k, v) ->
            sb.append("  ").append(Json.encodeToString(JsonPrimitive.serializer(), JsonPrimitive(k))).append(": ")
                .append(Json.encodeToString(JsonPrimitive.serializer(), JsonPrimitive(v))).append(if (i < keys.size - 1) ",\n" else "\n")
        }
        out.writeText(sb.append("}\n").toString())
        assertTrue(keys.size > 600)
    }

    @Test fun katalogAnahtarlariTurkceDosyadaVar() {
        val tr = load("tr")
        assertTrue("tr.json yok", tr.isNotEmpty())
        val missing = catalogKeys().filter { (k, v) -> tr[k] != v }
        assertTrue("tr.json katalogla uyumsuz (${missing.size}): ${missing.keys.take(20)}", missing.isEmpty())
    }

    @Test fun arayuzAnahtarlariTurkceDosyadaVar() {
        val tr = load("tr")
        val used = HashSet<String>()
        val re = Regex("""L\.(?:t|f)\("([a-zA-Z0-9_.]+)"""")
        val re2 = Regex(""""((?:common|game|map|menu|prog|saga|shop|duel|hud|world|intro|lives|settings|brand|store)\.[a-zA-Z0-9_.]+)"""")
        File(root, "core/src/main/kotlin").walkTopDown().filter { it.extension == "kt" }.forEach { f ->
            val src = f.readText()
            re.findAll(src).forEach { used += it.groupValues[1] }
            re2.findAll(src).forEach { used += it.groupValues[1] }
        }
        val missing = used.filter { it !in tr }
        assertTrue("arayüz anahtarı eksik (${missing.size}): $missing", missing.isEmpty())
    }

    @Test fun tumDillerEksiksiz() {
        val tr = load("tr")
        val report = StringBuilder()
        var total = 0
        for (code in L.SUPPORTED) {
            val m = load(code)
            val missing = tr.keys.filter { m[it].isNullOrBlank() }
            val badPh = tr.keys.filter { k -> m[k] != null && placeholders(m[k]!!) != placeholders(tr[k]!!) }
            val markup = m.filter { it.value.contains('[') }.keys
            val extra = m.keys - tr.keys
            total += missing.size + badPh.size + markup.size
            report.append("$code: ${m.size} anahtar, eksik=${missing.size}, yer tutucu hatası=${badPh.size}, '[' içeren=${markup.size}, fazla=${extra.size}\n")
            if (missing.isNotEmpty()) report.append("  eksik: ${missing.take(15)}\n")
            if (badPh.isNotEmpty()) report.append("  yer tutucu: ${badPh.take(15)}\n")
        }
        File(root, "logic/build/i18n_report.txt").writeText(report.toString())
        assertEquals(report.toString(), 0, total)
    }

    @Test fun dilSecimiVeBicim() {
        assertEquals("en", L.resolve("", "ja"))
        assertEquals("de", L.resolve("", "de_AT"))
        assertEquals("fr", L.resolve("fr", "tr"))
        assertEquals("tr", L.resolve("", "tr"))
        L.install { c -> File(dir, "$c.json").takeIf { it.exists() }?.readText() }
        L.setLanguage("en")
        assertEquals("1 gate", L.format("{0,plural,one{# gate} other{# gates}}", 1))
        assertEquals("3 gates", L.format("{0,plural,one{# gate} other{# gates}}", 3))
        L.setLanguage("fr")
        assertEquals("0 porte", L.format("{0,plural,one{# porte} other{# portes}}", 0))
        assertEquals("10 000", L.num(10000).replace(' ', ' ').replace(' ', ' '))
        L.setLanguage("tr")
        assertEquals("İSTANBUL", "istanbul".uppercase(L.locale))
        assertEquals("Bölüm 5", L.format("Bölüm {0}", 5))
        // katalog metinleri dile göre değişir, Türkçede kaynakla aynıdır
        assertEquals("Renk Bahçesi", AreaCatalog.worlds[0].name)
        L.setLanguage("en")
        assertNotEquals("Renk Bahçesi", AreaCatalog.worlds[0].name)
        L.setLanguage("tr")
    }
}
