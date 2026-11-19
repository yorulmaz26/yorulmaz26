package com.zamaninefendisi.logic.meta

import com.zamaninefendisi.logic.meta.Resource.*
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Zaman Kalesi binaları. Diğer binaların seviyesi Zaman Çekirdeği'nin seviyesini aşamaz.
 * Etkiler bulmacaları otomatik çözmez: üretim, ödül çarpanı, önizleme hakkı gibi kalıcı avantajlar sağlar.
 */
enum class BuildingType(
    val title: String,
    val description: String,
    private val baseCost: Cost,
    val produces: Map<Resource, Double> = emptyMap(),
    val maxLevel: Int = 5,
) {
    CORE("Zaman Çekirdeği", "Kalenin kalbi. Diğer binalar en fazla çekirdeğin seviyesine kadar geliştirilebilir.", mapOf(GOLD to 120, STONE to 30, CRYSTAL to 2)),
    LAB("Araştırma Laboratuvarı", "Bilim Puanı üretir; araştırma kademelerini açar.", mapOf(GOLD to 80, WOOD to 30), mapOf(SCIENCE to 6.0)),
    STORAGE("Kaynak Deposu", "Binaların biriktirebileceği üretim sınırını artırır.", mapOf(GOLD to 60, WOOD to 40)),
    ACADEMY("Askerî Akademi", "Savaş bölümlerinde Zaman Muhafızı'na her 2 seviyede +1 can verir.", mapOf(GOLD to 100, IRON to 20)),
    TECH_CENTER("Teknoloji Merkezi", "Her bölümde fazladan Geleceği Önceden Görme hakkı verir (seviye başına +1).", mapOf(GOLD to 90, IRON to 15, ENERGY to 20)),
    PORTAL("Zaman Portalı", "Paradoks Enerjisi üretir ve zaman seferlerinin yuvalarını açar.", mapOf(GOLD to 110, ENERGY to 30, CRYSTAL to 1), mapOf(PARADOX to 0.5)),
    POWER_PLANT("Enerji Santrali", "Enerji üretir.", mapOf(GOLD to 70, STONE to 20), mapOf(ENERGY to 8.0)),
    MUSEUM("Tarih Müzesi", "Bölümlerden kazanılan deneyimi seviye başına %5 artırır.", mapOf(GOLD to 90, STONE to 30)),
    EXPLORATION("Keşif Merkezi", "Kadim Parça bulur.", mapOf(GOLD to 100, WOOD to 30, IRON to 10), mapOf(SHARD to 0.25)),
    TRADE("Ticaret Merkezi", "Altın üretir; markette kaynak takası oranlarını iyileştirir.", mapOf(GOLD to 60, WOOD to 25), mapOf(GOLD to 12.0)),
    DEFENSE_TOWER("Savunma Kulesi", "Krallık Savunması'nda kalenin kule gücünü artırır; savaş bölümlerinde seviye başına +5 ganimet altını.", mapOf(GOLD to 90, STONE to 40)),
    CRYSTAL_MINE("Kristal Madeni", "Zaman Kristali çıkarır.", mapOf(GOLD to 150, STONE to 40, IRON to 20), mapOf(CRYSTAL to 0.2)),
    ARCHIVE("Medeniyet Arşivi", "Bölümlerden kazanılan altını seviye başına %5 artırır.", mapOf(GOLD to 80, WOOD to 20, STONE to 20)),
    WORKSHOP("Zaman Atölyesi", "Odun ve taş üretir.", mapOf(GOLD to 50), mapOf(WOOD to 10.0, STONE to 6.0)),
    QUEST_CENTER("Görev Merkezi", "Demir üretir ve günlük görev ödüllerini seviye başına %10 artırır.", mapOf(GOLD to 70, WOOD to 30), mapOf(IRON to 4.0)),
    ;

    /** [level] seviyesine yükseltmenin maliyeti (1. seviye inşa). */
    fun costFor(level: Int): Cost = baseCost.mapValues { (_, v) -> (v * level.toDouble().pow(1.5)).roundToInt() }

    fun productionPerHour(level: Int): Map<Resource, Double> = produces.mapValues { it.value * level }
}

data class UpgradeCheck(val ok: Boolean, val reason: String?)

object Kingdom {
    /** Başlangıçta kurulu binalar. */
    val starting: Map<BuildingType, Int> = mapOf(BuildingType.CORE to 1, BuildingType.WORKSHOP to 1, BuildingType.TRADE to 1)

    fun storageCap(storageLevel: Int, research: Set<String>): Int {
        val base = 150 + 200 * storageLevel
        return if (ResearchTree.STORAGE_BOOST in research) (base * 1.5).toInt() else base
    }

    fun canUpgrade(b: BuildingType, levels: Map<BuildingType, Int>, wallet: Map<Resource, Int>): UpgradeCheck {
        val cur = levels[b] ?: 0
        if (cur >= b.maxLevel) return UpgradeCheck(false, "En yüksek seviyede")
        val core = levels[BuildingType.CORE] ?: 1
        if (b != BuildingType.CORE && cur + 1 > core) return UpgradeCheck(false, "Önce Zaman Çekirdeği'ni seviye ${cur + 1}'e yükselt")
        val cost = b.costFor(cur + 1)
        if (!wallet.has(cost)) return UpgradeCheck(false, "Yetersiz kaynak: ${cost.describe()}")
        return UpgradeCheck(true, null)
    }

    /** Kalenin genel gücü (kişisel rekor "en güçlü zaman kalesi" için). */
    fun power(levels: Map<BuildingType, Int>): Int = levels.entries.sumOf { (b, l) -> l * if (b == BuildingType.CORE) 20 else 10 }

    /** Geçen süreye göre üretimi biriktirir (depo sınırına kadar). */
    fun accumulate(levels: Map<BuildingType, Int>, research: Set<String>, pending: Map<Resource, Double>, deltaMs: Long): Map<Resource, Double> {
        if (deltaMs <= 0) return pending
        val hours = deltaMs / 3_600_000.0
        val cap = storageCap(levels[BuildingType.STORAGE] ?: 0, research).toDouble()
        val out = pending.toMutableMap()
        for ((b, lvl) in levels) {
            if (lvl <= 0) continue
            for ((r, perHour) in b.productionPerHour(lvl)) {
                val mult = ResearchTree.productionMultiplier(r, research)
                out[r] = minOf(cap, (out[r] ?: 0.0) + perHour * mult * hours)
            }
        }
        return out
    }
}
