package com.emirzinciri.robotkomutan.logic.factory

import com.emirzinciri.robotkomutan.logic.progress.GameClock
import com.emirzinciri.robotkomutan.logic.progress.Wallet
import kotlinx.serialization.Serializable
import kotlin.math.pow
import kotlin.math.roundToLong

enum class Resource(val title: String) { GOLD("Altın"), METAL("Metal"), ENERGY("Enerji"), CHIPS("Mikroçip"), TECH("Teknoloji Puanı") }

fun Wallet.get(r: Resource): Long = when (r) {
    Resource.GOLD -> gold; Resource.METAL -> metal; Resource.ENERGY -> energy; Resource.CHIPS -> chips; Resource.TECH -> tech
}

fun Wallet.add(r: Resource, v: Long) {
    when (r) {
        Resource.GOLD -> gold += v; Resource.METAL -> metal += v; Resource.ENERGY -> energy += v
        Resource.CHIPS -> chips += v; Resource.TECH -> tech += v
    }
}

/** Maliyet: kaynak -> miktar. */
typealias Cost = Map<Resource, Long>

/**
 * Fabrika binaları. [phase] > 5 olanlar ileriki aşamaların sistemlerine bağlıdır ve şimdilik kurulamaz
 * (arayüzde dürüstçe "sonraki aşama" olarak gösterilir).
 */
enum class BuildingType(
    val title: String,
    val description: String,
    val maxLevel: Int,
    /** Kurulabilmesi için gereken Komuta Merkezi seviyesi. */
    val requiresCommand: Int,
    val phase: Int = 5,
    val baseGold: Long = 100,
    val baseMetal: Long = 20,
    val baseChips: Long = 0,
) {
    COMMAND_CENTER("Komuta Merkezi", "Diğer binaların en yüksek seviyesini ve yeni binaları açar.", 10, 0, baseGold = 150, baseMetal = 40),
    ENERGY_PLANT("Enerji Santrali", "Saatte enerji üretir. Metal işleme enerji harcar.", 10, 1, baseGold = 60, baseMetal = 10),
    METAL_WORKS("Metal İşleme Tesisi", "Enerji harcayarak metal üretir.", 10, 1, baseGold = 80, baseMetal = 0),
    STORAGE("Kaynak Deposu", "Her kaynağın depolama sınırını artırır.", 10, 1, baseGold = 90, baseMetal = 25),
    CHIP_LAB("Mikroçip Laboratuvarı", "Metal harcayarak mikroçip üretir.", 8, 2, baseGold = 180, baseMetal = 60),
    RESEARCH_CENTER("Araştırma Merkezi", "Teknoloji puanı üretir ve araştırma kademelerini açar.", 8, 2, baseGold = 200, baseMetal = 50, baseChips = 2),
    TRADE_CENTER("Ticaret Merkezi", "Fazla metal ve enerjiyi altına çevirir.", 5, 3, baseGold = 250, baseMetal = 80),
    ROBOT_PRODUCTION("Robot Üretim Tesisi", "Yeni robot sınıfları üretir.", 10, 3, phase = 6),
    GARAGE("Robot Garajı", "Robot koleksiyonunu ve geliştirmeleri barındırır.", 10, 3, phase = 6),
    ACADEMY("Komut Akademisi", "Komut ustalığı eğitimleri.", 10, 4, phase = 7),
    DEFENSE("Savunma Merkezi", "Fabrika savunması.", 10, 4, phase = 6),
    EXPLORATION("Keşif Merkezi", "Robot seferleri.", 10, 4, phase = 7),
    REPAIR("Robot Tamir Atölyesi", "Hasarlı robotları onarır.", 10, 5, phase = 6),
    MUSEUM("Koleksiyon Müzesi", "Toplanan robotları sergiler.", 10, 5, phase = 7),
    AI_LAB("Merkezi Yapay Zekâ Laboratuvarı", "Son araştırmalar.", 10, 8, phase = 8);

    val available: Boolean get() = phase <= 5

    /** Bir sonraki seviyenin maliyeti (seviye 0 → 1 kurulumdur). */
    fun costFor(nextLevel: Int): Cost {
        val k = 1.55.pow(nextLevel - 1)
        return buildMap {
            put(Resource.GOLD, (baseGold * k).roundToLong())
            if (baseMetal > 0) put(Resource.METAL, (baseMetal * k).roundToLong())
            if (baseChips > 0 || nextLevel >= 4) put(Resource.CHIPS, (baseChips + (nextLevel - 3).coerceAtLeast(0) * 2L))
        }.filterValues { it > 0 }
    }
}

enum class ResearchBranch(val title: String, val phase: Int = 5) {
    COMMAND("Komut Teknolojisi"), ENGINEERING("Robot Mühendisliği"), ENERGY("Enerji Teknolojisi"),
    MILITARY("Askerî Teknoloji", phase = 6), AUTOMATION("Fabrika Otomasyonu"),
}

enum class Effect(val title: String) {
    GOLD_BONUS("Bölüm altın ödülü"), XP_BONUS("Bölüm deneyim ödülü"), ENERGY_RATE("Enerji üretimi"),
    METAL_RATE("Metal üretimi"), CHIP_RATE("Mikroçip üretimi"), TECH_RATE("Teknoloji puanı üretimi"),
    STORAGE("Depo kapasitesi"), OFFLINE_HOURS("Çevrimdışı üretim süresi"), TRADE_RATE("Ticaret kuru"),
    NONE("—"),
}

/** Araştırma düğümü. [tier], gereken Araştırma Merkezi seviyesidir; aynı daldaki önceki kademe şarttır. */
enum class Research(val branch: ResearchBranch, val tier: Int, val title: String, val effect: Effect, val amount: Int, val tech: Long, val chips: Long) {
    CMD_1(ResearchBranch.COMMAND, 1, "Verimli Zincirler", Effect.GOLD_BONUS, 10, 20, 1),
    CMD_2(ResearchBranch.COMMAND, 2, "Komut Analizi", Effect.XP_BONUS, 20, 60, 3),
    CMD_3(ResearchBranch.COMMAND, 3, "Usta Komutan", Effect.GOLD_BONUS, 20, 150, 8),
    ENG_1(ResearchBranch.ENGINEERING, 1, "Raf Sistemleri", Effect.STORAGE, 25, 25, 1),
    ENG_2(ResearchBranch.ENGINEERING, 2, "Uzun Vardiya", Effect.OFFLINE_HOURS, 4, 70, 3),
    ENG_3(ResearchBranch.ENGINEERING, 3, "Dev Depolar", Effect.STORAGE, 50, 160, 8),
    NRG_1(ResearchBranch.ENERGY, 1, "Verimli Türbinler", Effect.ENERGY_RATE, 20, 20, 1),
    NRG_2(ResearchBranch.ENERGY, 2, "Süper İletken Hatlar", Effect.ENERGY_RATE, 30, 60, 3),
    NRG_3(ResearchBranch.ENERGY, 3, "Füzyon Çekirdeği", Effect.ENERGY_RATE, 50, 150, 8),
    MIL_1(ResearchBranch.MILITARY, 1, "Kalkan Plakaları", Effect.NONE, 0, 40, 2),
    AUT_1(ResearchBranch.AUTOMATION, 1, "Otomatik Presler", Effect.METAL_RATE, 20, 25, 1),
    AUT_2(ResearchBranch.AUTOMATION, 2, "Çip Baskı Hattı", Effect.CHIP_RATE, 30, 70, 3),
    AUT_3(ResearchBranch.AUTOMATION, 3, "Akıllı Pazar", Effect.TRADE_RATE, 25, 140, 6),
    AUT_4(ResearchBranch.AUTOMATION, 3, "Araştırma Ağı", Effect.TECH_RATE, 40, 160, 8);

    val available: Boolean get() = branch.phase <= 5
    val previous: Research? get() = entries.filter { it.branch == branch && it.tier < tier }.maxByOrNull { it.tier }
}

@Serializable
data class FactoryState(
    val buildings: MutableMap<BuildingType, Int> = mutableMapOf(BuildingType.COMMAND_CENTER to 1, BuildingType.ENERGY_PLANT to 1, BuildingType.METAL_WORKS to 1),
    val research: MutableSet<Research> = mutableSetOf(),
    /** Ondalık üretim artıkları (kaybolmasın diye). */
    val fraction: MutableMap<Resource, Double> = mutableMapOf(),
    var lastWallMs: Long = 0,
    var lastMonoMs: Long = 0,
    var lastBootId: String = "",
    var trades: Long = 0,
)

data class ProductionReport(val minutes: Long, val gained: Map<Resource, Long>, val capped: Boolean)

sealed class FactoryResult {
    object Ok : FactoryResult()
    data class Missing(val cost: Cost) : FactoryResult()
    data class Locked(val reason: String) : FactoryResult()
}

/**
 * Fabrika kuralları. Üretim gerçek zamanlıdır; geçen süre can sistemiyle aynı yöntemle ölçülür
 * (aynı açılışta monotonik saat, yeniden başlatmada duvar saati, negatif süre 0). Ayrıca:
 * çevrimdışı üretim [offlineHours] ile ve depo kapasitesiyle sınırlıdır; saat hilesinin kazancı sınırlı kalır.
 */
class Factory(val state: FactoryState, val wallet: Wallet, private val clock: GameClock) {

    fun level(b: BuildingType) = state.buildings[b] ?: 0
    fun has(r: Research) = r in state.research
    fun bonus(e: Effect): Int = state.research.filter { it.effect == e }.sumOf { it.amount }

    val offlineHours: Int get() = 8 + bonus(Effect.OFFLINE_HOURS)

    /** Kaynak başına depo sınırı (altın sınırsızdır). */
    fun capacity(r: Resource): Long? {
        if (r == Resource.GOLD) return null
        val base = when (r) { Resource.CHIPS -> 30L; Resource.TECH -> 200L; else -> 300L }
        val lvl = level(BuildingType.STORAGE)
        return (base * (1 + lvl) * (100 + bonus(Effect.STORAGE)) / 100)
    }

    /** Saatlik brüt üretim/tüketim. */
    fun ratePerHour(r: Resource): Double = when (r) {
        Resource.ENERGY -> 30.0 * level(BuildingType.ENERGY_PLANT) * (100 + bonus(Effect.ENERGY_RATE)) / 100
        Resource.METAL -> 12.0 * level(BuildingType.METAL_WORKS) * (100 + bonus(Effect.METAL_RATE)) / 100
        Resource.CHIPS -> 1.0 * level(BuildingType.CHIP_LAB) * (100 + bonus(Effect.CHIP_RATE)) / 100
        Resource.TECH -> 3.0 * level(BuildingType.RESEARCH_CENTER) * (100 + bonus(Effect.TECH_RATE)) / 100
        Resource.GOLD -> 0.0
    }

    fun consumptionPerHour(r: Resource): Double = when (r) {
        Resource.ENERGY -> 8.0 * level(BuildingType.METAL_WORKS)
        Resource.METAL -> 5.0 * level(BuildingType.CHIP_LAB)
        else -> 0.0
    }

    /** Güvenilir geçen süreyi hesaplayıp üretimi uygular. */
    fun update(): ProductionReport {
        val wall = clock.wallMs(); val mono = clock.monotonicMs(); val boot = clock.bootId()
        val s = state
        val first = s.lastWallMs == 0L && s.lastMonoMs == 0L && s.lastBootId.isEmpty()
        val elapsed = when {
            first -> 0L
            boot == s.lastBootId && mono >= s.lastMonoMs -> mono - s.lastMonoMs
            else -> (wall - s.lastWallMs).coerceAtLeast(0)
        }
        s.lastWallMs = wall; s.lastMonoMs = mono; s.lastBootId = boot
        return produce(elapsed)
    }

    /** Verilen süre kadar üretim (dakikalık adımlarla; zincir tüketimi sırayla uygulanır). */
    fun produce(elapsedMs: Long): ProductionReport {
        val maxMs = offlineHours * 3_600_000L
        val ms = elapsedMs.coerceAtMost(maxMs)
        val minutes = ms / 60_000
        val gained = HashMap<Resource, Long>()
        var capped = elapsedMs > maxMs
        val stepH = 1.0 / 60.0
        val remainderMs = ms % 60_000
        fun tick(h: Double) {
            // 1) enerji
            capped = gain(Resource.ENERGY, ratePerHour(Resource.ENERGY) * h, gained) || capped
            // 2) metal: enerji harcar
            val needE = consumptionPerHour(Resource.ENERGY) * h
            val k = if (needE <= 0) 1.0 else (available(Resource.ENERGY) / needE).coerceIn(0.0, 1.0)
            if (needE > 0) spendFrac(Resource.ENERGY, needE * k, gained)
            capped = gain(Resource.METAL, ratePerHour(Resource.METAL) * h * k, gained) || capped
            // 3) mikroçip: metal harcar
            val needM = consumptionPerHour(Resource.METAL) * h
            val km = if (needM <= 0) 1.0 else (available(Resource.METAL) / needM).coerceIn(0.0, 1.0)
            if (needM > 0) spendFrac(Resource.METAL, needM * km, gained)
            capped = gain(Resource.CHIPS, ratePerHour(Resource.CHIPS) * h * km, gained) || capped
            // 4) teknoloji
            capped = gain(Resource.TECH, ratePerHour(Resource.TECH) * h, gained) || capped
        }
        repeat(minutes.toInt()) { tick(stepH) }
        if (remainderMs > 0) tick(remainderMs / 3_600_000.0)
        return ProductionReport(minutes, gained.filterValues { it != 0L }, capped)
    }

    private fun available(r: Resource): Double = wallet.get(r) + (state.fraction[r] ?: 0.0)

    /** Kesirli kazanç ekler; depo doluysa true. */
    private fun gain(r: Resource, amount: Double, out: MutableMap<Resource, Long>): Boolean {
        if (amount <= 0) return false
        val cap = capacity(r)
        val total = (state.fraction[r] ?: 0.0) + amount
        val whole = total.toLong()
        state.fraction[r] = total - whole
        var add = whole
        var full = false
        if (cap != null && wallet.get(r) + add >= cap) {
            add = (cap - wallet.get(r)).coerceAtLeast(0); full = true; state.fraction[r] = 0.0
        }
        if (add > 0) { wallet.add(r, add); out.merge(r, add, Long::plus) }
        return full
    }

    private fun spendFrac(r: Resource, amount: Double, out: MutableMap<Resource, Long>) {
        val total = (state.fraction[r] ?: 0.0) - amount
        val whole = kotlin.math.floor(total).toLong()
        state.fraction[r] = total - whole
        if (whole != 0L) { wallet.add(r, whole); out.merge(r, whole, Long::plus) }
    }

    // ------------------------------------------------------------------ binalar

    fun canAfford(c: Cost) = c.all { (r, v) -> wallet.get(r) >= v }
    private fun pay(c: Cost) = c.forEach { (r, v) -> wallet.add(r, -v) }

    fun upgradeCost(b: BuildingType): Cost? = if (level(b) >= b.maxLevel) null else b.costFor(level(b) + 1)

    fun lockReason(b: BuildingType): String? = when {
        !b.available -> "Sonraki geliştirme aşamasında açılacak."
        level(b) >= b.maxLevel -> "En yüksek seviye."
        b != BuildingType.COMMAND_CENTER && level(BuildingType.COMMAND_CENTER) < b.requiresCommand ->
            "Komuta Merkezi seviye ${b.requiresCommand} gerekli."
        b != BuildingType.COMMAND_CENTER && level(b) >= level(BuildingType.COMMAND_CENTER) + 2 ->
            "Önce Komuta Merkezi'ni yükselt (en fazla Komuta Merkezi seviyesi + 2)."
        else -> null
    }

    fun upgrade(b: BuildingType): FactoryResult {
        lockReason(b)?.let { return FactoryResult.Locked(it) }
        val c = upgradeCost(b)!!
        if (!canAfford(c)) return FactoryResult.Missing(c)
        update()
        pay(c)
        state.buildings[b] = level(b) + 1
        return FactoryResult.Ok
    }

    // ------------------------------------------------------------------ araştırma

    fun researchLock(r: Research): String? = when {
        !r.available -> "Savaş sistemiyle birlikte açılacak."
        has(r) -> "Tamamlandı."
        level(BuildingType.RESEARCH_CENTER) < r.tier -> "Araştırma Merkezi seviye ${r.tier} gerekli."
        r.previous?.let { !has(it) } == true -> "Önce '${r.previous!!.title}' araştırılmalı."
        else -> null
    }

    fun researchCost(r: Research): Cost = mapOf(Resource.TECH to r.tech, Resource.CHIPS to r.chips)

    fun research(r: Research): FactoryResult {
        researchLock(r)?.let { return FactoryResult.Locked(it) }
        val c = researchCost(r)
        if (!canAfford(c)) return FactoryResult.Missing(c)
        pay(c)
        state.research += r
        return FactoryResult.Ok
    }

    // ------------------------------------------------------------------ ticaret

    /** 50 metal → altın. Kur Ticaret Merkezi seviyesi ve araştırmayla artar. */
    fun tradeGoldFor(r: Resource): Long {
        val lvl = level(BuildingType.TRADE_CENTER)
        if (lvl == 0) return 0
        val base = when (r) { Resource.METAL -> 20L; Resource.ENERGY -> 8L; else -> 0L }
        return base * (100 + 10 * (lvl - 1) + bonus(Effect.TRADE_RATE)) / 100
    }

    fun trade(r: Resource, lots: Int = 1): FactoryResult {
        val gold = tradeGoldFor(r)
        if (gold <= 0) return FactoryResult.Locked("Ticaret Merkezi gerekli.")
        val c = mapOf(r to TRADE_LOT * lots)
        if (!canAfford(c)) return FactoryResult.Missing(c)
        pay(c)
        wallet.gold += gold * lots
        state.trades += lots
        return FactoryResult.Ok
    }

    companion object { const val TRADE_LOT = 50L }
}
