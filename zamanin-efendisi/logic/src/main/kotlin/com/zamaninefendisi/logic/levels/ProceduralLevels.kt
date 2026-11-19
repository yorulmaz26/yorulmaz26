package com.zamaninefendisi.logic.levels

import com.zamaninefendisi.logic.LevelDef
import com.zamaninefendisi.logic.continuity.GenRules
import com.zamaninefendisi.logic.continuity.LevelGenerator
import com.zamaninefendisi.logic.continuity.LevelGenerator.Template

/**
 * Alan 8–100: her alanın teması (su, çöl, orman, savaş, teknoloji, zaman) şablon karışımını ve kurallarını belirler.
 * Zorluk alan numarasıyla artar (daha dar tur sınırı, kalabalık düşman, enerji kısıtı, Gelecek hedefleri). Her 5. bölüm alan
 * patronu, her 25. bölüm özel patron, her 50. bölüm büyük dünya finalidir. Bütün bölümler bölüm numarasından tohumlanır:
 * herkes aynı 500 bölümü oynar ve her birinin çözümü üretim sırasında doğrulanır.
 */
object ProceduralLevels {
    private enum class Theme(val keys: List<String>, val templates: List<Template>) {
        WATER(listOf("Gelgit", "Liman", "Fener", "Mercan", "Nehir", "Su ", "Buz", "Buzul", "Köprü", "Batık", "Kemer"), listOf(Template.BRIDGE, Template.VILLAGE, Template.FOREST)),
        DESERT(listOf("Çöl", "Kum", "Vaha", "Piramit", "Serap", "Kervan", "Firavun", "Bakır", "Kızıl", "Güneş"), listOf(Template.DESERT, Template.VILLAGE, Template.BRIDGE)),
        FOREST(listOf("Orman", "Bahçe", "Kelebek", "Kül", "Volkan", "Lav"), listOf(Template.FOREST, Template.DESERT, Template.VILLAGE)),
        WAR(listOf("Ordu", "Savaş", "Kale", "Muhafız", "Kral", "Taht", "Korsan", "Ejder", "Kule", "İmparator", "Mahkeme", "Tanrı"), listOf(Template.DEFENSE, Template.BRIDGE, Template.DEFENSE)),
        TECH(listOf("Teknoloji", "Robot", "Enerji", "Işık", "Makine", "Kuantum", "Mekanik", "Saat", "Kronos", "Demir", "Laboratuvar", "Gökyüzü", "Uçan", "Bulut", "Yıldız", "Fırtına"), listOf(Template.BRIDGE, Template.FOREST, Template.VILLAGE)),
        TIME(listOf("Paradoks", "Zaman", "Döngü", "Yankı", "Ayna", "Paralel", "Sonsuz", "Kilit", "Çatal", "Hafıza", "Çekirdek", "Kopya", "Kaçak"), listOf(Template.FOREST, Template.BRIDGE, Template.DESERT, Template.VILLAGE)),
        MIXED(emptyList(), Template.entries.toList()),
    }

    private fun theme(name: String, feature: String): Theme {
        val text = "$name $feature"
        return Theme.entries.firstOrNull { t -> t.keys.any { text.contains(it, ignoreCase = true) } } ?: Theme.MIXED
    }

    fun description(area: Int, feature: String): String {
        val world = (area - 1) / 20 + 1
        return "Dünya $world · $feature. Bu alanın bölümleri alanın kurallarıyla üretilir ve her birinin en az bir çözümü doğrulanmıştır."
    }

    fun name(area: Int, areaName: String, idx: Int): String = when {
        idx == 5 && area % 10 == 0 -> "$areaName · Dünya Finali"
        idx == 5 && area % 5 == 0 -> "$areaName · Özel Patron"
        idx == 5 -> "$areaName · Patron"
        else -> "$areaName ${listOf("I", "II", "III", "IV")[idx - 1]}"
    }

    private fun rules(area: Int, idx: Int, t: Theme): Pair<Template, GenRules> {
        val boss = idx == 5
        val special = boss && area % 5 == 0
        val final = boss && area % 10 == 0
        val slack = (9 - area / 12 - idx / 2).coerceIn(2, 8) - (if (special) 1 else 0)
        val baseEnemies = area / 25
        val template = when {
            final || special -> Template.DEFENSE
            boss -> if (t == Theme.TECH || t == Theme.TIME) Template.BRIDGE else Template.DEFENSE
            else -> t.templates[(idx - 1 + area) % t.templates.size]
        }
        val r = GenRules(
            energyLimited = (t == Theme.TECH || t == Theme.TIME) && idx >= 3 || (boss && !final && template != Template.DEFENSE),
            desertSpeed = if (t == Theme.DESERT && area >= 41) 2 else 1,
            extraEnemies = baseEnemies + (if (boss) 1 else 0) + (if (special) 1 else 0) + (if (final) 1 else 0),
            slack = slack.coerceAtLeast(2),
            futureGoal = t == Theme.TECH || (idx >= 3 && area >= 30),
        )
        return template to r
    }

    fun area(area: Int, areaName: String, feature: String): List<LevelDef> {
        val t = theme(areaName, feature)
        return (1..5).map { idx ->
            val id = (area - 1) * 5 + idx
            val (template, r) = rules(area, idx, t)
            val g = LevelGenerator.generate(id, id * 7919L + area * 31L, r, only = template)
            val title = name(area, areaName, idx)
            val intro = "$feature. " + g.level.intro + if (idx == 5) " Bu bir patron bölümü: kurallar daha sıkı." else ""
            g.level.rebrand(id = id, area = area, name = title, intro = intro)
        }
    }
}
