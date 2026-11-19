package com.yuruyensehir.logic

import com.yuruyensehir.logic.Resource.*
import kotlinx.serialization.Serializable

enum class ResearchBranch(val title: String) {
    CITY("Şehir Mühendisliği"), LIGHT("Hafif Yapı Teknolojisi"), PRODUCTION("Üretim"), ENERGY("Enerji"),
    CARRIER("Taşıyıcı Biyolojisi"), DEFENSE("Askerî Savunma"), EXPLORATION("Keşif ve Navigasyon")
}

@Serializable
enum class TechId {
    STRONG_FOUNDATIONS, EFFICIENT_LAYOUT,
    LIGHT_TIMBER, COMPOSITE_ROOFS, HANGING_STORAGE,
    IRRIGATION, ADVANCED_WORKSHOP, ADVANCED_WATER,
    EFFICIENT_BOILERS, ENERGY_SAVING,
    NUTRITIOUS_FODDER, MUSCLE_TRAINING, DEEP_SLEEP,
    SHOCK_ABSORBERS, WATCHMEN,
    STAR_CHARTS, ROUTE_SCIENCE, BETTER_GATHERERS,
}

data class TechDef(
    val id: TechId,
    val title: String,
    val branch: ResearchBranch,
    val description: String,
    val points: Double,
    val cost: Amounts = emptyMap(),
    val requires: List<TechId> = emptyList(),
)

object TechCatalog {
    val all: List<TechDef> = listOf(
        TechDef(TechId.STRONG_FOUNDATIONS, "Sağlam Temeller", ResearchBranch.CITY, "Binaların aldığı hasar %30 azalır.", 40.0, amounts(STONE to 40)),
        TechDef(TechId.EFFICIENT_LAYOUT, "Verimli Yerleşim", ResearchBranch.CITY, "Komşuluk bonusları %50 güçlenir. Toplu Konut açılır.", 80.0, amounts(WOOD to 60, STONE to 40), listOf(TechId.STRONG_FOUNDATIONS)),
        TechDef(TechId.LIGHT_TIMBER, "Hafif Kereste", ResearchBranch.LIGHT, "Bütün binaların ağırlığı %10 azalır.", 40.0, amounts(WOOD to 60)),
        TechDef(TechId.COMPOSITE_ROOFS, "Kompozit Çatılar", ResearchBranch.LIGHT, "Bina ağırlıkları ayrıca %15 azalır.", 110.0, amounts(CLOTH to 30, METAL to 20), listOf(TechId.LIGHT_TIMBER)),
        TechDef(TechId.HANGING_STORAGE, "Asma Depolar", ResearchBranch.LIGHT, "Depolanan kaynakların ağırlığı %25 azalır.", 70.0, amounts(CLOTH to 25, WOOD to 30), listOf(TechId.LIGHT_TIMBER)),
        TechDef(TechId.IRRIGATION, "Sulama Kanalları", ResearchBranch.PRODUCTION, "Tarla ve sera verimi +%20. Sera açılır.", 35.0, amounts(WOOD to 30, STONE to 20)),
        TechDef(TechId.ADVANCED_WORKSHOP, "Gelişmiş Atölye", ResearchBranch.PRODUCTION, "Atölye, metal ve tekstil verimi +%20. Teknoloji Laboratuvarı açılır.", 70.0, amounts(METAL to 20, TOOLS to 10)),
        TechDef(TechId.ADVANCED_WATER, "Gelişmiş Su Üretimi", ResearchBranch.PRODUCTION, "Su toplama verimi +%30.", 50.0, amounts(CLOTH to 15, METAL to 10), listOf(TechId.IRRIGATION)),
        TechDef(TechId.EFFICIENT_BOILERS, "Verimli Kazanlar", ResearchBranch.ENERGY, "Enerji üretimi +%25.", 45.0, amounts(METAL to 15, STONE to 20)),
        TechDef(TechId.ENERGY_SAVING, "Enerji Tasarrufu", ResearchBranch.ENERGY, "Binaların enerji tüketimi %20 azalır.", 90.0, amounts(METAL to 25, TOOLS to 10), listOf(TechId.EFFICIENT_BOILERS)),
        TechDef(TechId.NUTRITIOUS_FODDER, "Besleyici Yem", ResearchBranch.CARRIER, "Taşıyıcının açlık ve susuzluk artışı %15 azalır.", 40.0, amounts(FOOD to 60)),
        TechDef(TechId.MUSCLE_TRAINING, "Kas Güçlendirme", ResearchBranch.CARRIER, "Taşıma kapasitesi +%15.", 90.0, amounts(FOOD to 80, WATER to 60), listOf(TechId.NUTRITIOUS_FODDER)),
        TechDef(TechId.DEEP_SLEEP, "Derin Uyku", ResearchBranch.CARRIER, "Dinlenirken toparlanma +%40.", 60.0, amounts(CLOTH to 20, FOOD to 40), listOf(TechId.NUTRITIOUS_FODDER)),
        TechDef(TechId.SHOCK_ABSORBERS, "Darbe Emiciler", ResearchBranch.DEFENSE, "Yolculukta yük kayması hasarı %40 azalır.", 50.0, amounts(CLOTH to 20, WOOD to 30)),
        TechDef(TechId.WATCHMEN, "Gözcü Eğitimi", ResearchBranch.DEFENSE, "Fırtına ve saldırı olaylarındaki hasar %25 azalır.", 80.0, amounts(METAL to 20, FOOD to 30), listOf(TechId.SHOCK_ABSORBERS)),
        TechDef(TechId.STAR_CHARTS, "Yıldız Haritaları", ResearchBranch.EXPLORATION, "Keşif menzili +1.", 40.0, amounts(CLOTH to 15)),
        TechDef(TechId.ROUTE_SCIENCE, "Rota Bilimi", ResearchBranch.EXPLORATION, "Yolculuk hızı +%10.", 70.0, amounts(CLOTH to 20, METAL to 10), listOf(TechId.STAR_CHARTS)),
        TechDef(TechId.BETTER_GATHERERS, "Gelişmiş Toplayıcılar", ResearchBranch.EXPLORATION, "Toplayıcı rampası verimi +%30.", 60.0, amounts(TOOLS to 10, WOOD to 30)),
    )
    private val byId = all.associateBy { it.id }
    fun get(id: TechId): TechDef = byId.getValue(id)
}

@Serializable
data class ResearchState(
    val completed: MutableSet<TechId> = mutableSetOf(),
    var current: TechId? = null,
    var progress: Double = 0.0,
    /** Henüz harcanmamış bilgi puanı (aktif araştırma yokken birikir). */
    var bankedPoints: Double = 0.0,
)

object ResearchSystem {
    fun has(state: GameState, id: TechId) = id in state.research.completed

    fun available(state: GameState, id: TechId): Boolean {
        val def = TechCatalog.get(id)
        return !has(state, id) && def.requires.all { has(state, it) }
    }

    /** Araştırmayı başlatır; kaynak maliyeti başlangıçta ödenir. */
    fun start(state: GameState, id: TechId): ActionResult {
        if (has(state, id)) return ActionResult.fail("Bu araştırma zaten tamamlandı.")
        if (!available(state, id)) return ActionResult.fail("Önce gerekli araştırmaları tamamlayın.")
        if (state.research.current != null) return ActionResult.fail("Şu anda başka bir araştırma yürütülüyor.")
        val def = TechCatalog.get(id)
        if (!state.store.canAfford(def.cost)) return ActionResult.fail("Yetersiz kaynak: ${def.cost.describe()}")
        state.store.spend(def.cost)
        state.research.current = id
        state.research.progress = 0.0
        return ActionResult.ok("${def.title} araştırması başladı.")
    }

    fun cancel(state: GameState): ActionResult {
        val cur = state.research.current ?: return ActionResult.fail("Aktif araştırma yok.")
        state.research.bankedPoints += state.research.progress
        state.research.current = null
        state.research.progress = 0.0
        return ActionResult.ok("${TechCatalog.get(cur).title} durduruldu; puanlar saklandı (kaynaklar iade edilmez).")
    }

    fun addPoints(state: GameState, points: Double) {
        val r = state.research
        val cur = r.current
        if (cur == null) {
            r.bankedPoints += points
            return
        }
        r.progress += points + r.bankedPoints
        r.bankedPoints = 0.0
        val def = TechCatalog.get(cur)
        if (r.progress >= def.points) {
            r.bankedPoints = r.progress - def.points
            r.completed += cur
            r.current = null
            r.progress = 0.0
            state.log("Araştırma tamamlandı: ${def.title}. ${def.description}", LogKind.RESEARCH)
            state.cityVersion++
        }
    }
}
