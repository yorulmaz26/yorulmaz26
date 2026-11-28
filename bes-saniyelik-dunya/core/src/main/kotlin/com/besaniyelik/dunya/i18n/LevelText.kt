package com.besaniyelik.dunya.i18n

import com.besaniyelik.dunya.level.GameContent
import com.besaniyelik.dunya.level.HintDef
import com.besaniyelik.dunya.level.LevelData
import com.besaniyelik.dunya.level.LevelKind
import com.besaniyelik.dunya.sim.Mechanic

/**
 * Bölüm verisinde Türkçe kaydedilmiş metinlerin (bölüm adı, alan fikri, ipuçları) oyuncunun dilindeki karşılığı.
 * Bölümler yeniden üretilmez: aynı sıfat/isim indeksleri o dilin listelerinden okunur.
 *
 * Dil dosyasında:
 *  - map.N.nouns: "Wiese:f|Hügel:m|Tal:n" (cinsiyet: m, f, n; cinsiyetsiz dillerde yazılmaz)
 *  - map.N.adjectives: "Sonniger/Sonnige/Sonniges" (eril/dişil/nötr biçimler; tek biçim de olur)
 *  - levelname.pattern: "{adj} {noun}" ya da "{noun} {adj}"
 */
object LevelText {
    fun name(lv: LevelData): String = name(lv.id)

    fun name(levelId: Int): String {
        val m = GameContent.mapOf(levelId)
        return when (GameContent.kindOf(levelId)) {
            LevelKind.GRAND_FINAL -> L.t("levelname.grand")
            LevelKind.FINAL -> L.t("levelname.final", "map" to m.name)
            LevelKind.CHALLENGE -> L.t("levelname.challenge", "name" to base(levelId))
            else -> base(levelId)
        }
    }

    /** Sıfat + isim (dilin söz dizimine ve ismin cinsiyetine göre). */
    fun base(levelId: Int): String {
        val m = GameContent.mapOf(levelId)
        val (ai, ni) = GameContent.nameParts(levelId, 0)
        val nounEntry = L.list("map.${m.id}.nouns").getOrElse(ni) { "" }
        val noun = nounEntry.substringBefore(':')
        val gender = nounEntry.substringAfter(':', "")
        val forms = L.list("map.${m.id}.adjectives").getOrElse(ai) { "" }.split('/')
        val idx = when (gender) { "f" -> 1; "n" -> 2; else -> 0 }
        val adj = forms.getOrElse(idx) { forms.last() }
        return L.t("levelname.pattern", "adj" to adj, "noun" to noun)
    }

    /** Alan fikri (bölüm verisindeki "idea"): bölümün ait olduğu alanın adı. */
    fun idea(lv: LevelData): String = GameContent.stageOf(lv.id).title

    private val warn = Regex("^Dikkat! (.+) geliyor\\.$")

    /** Bölüm verisindeki Türkçe ipucu metnini çevirir. */
    fun hint(h: HintDef): String {
        L.fromSource("hint.", h.text)?.let { return it }
        warn.find(h.text)?.let { mr ->
            val m = Mechanic.entries.firstOrNull { it.trTitle == mr.groupValues[1] }
            if (m != null) return L.t("hint.warning", "rule" to m.title)
        }
        return h.text
    }
}
