package com.yuruyensehir.logic

import com.yuruyensehir.logic.Resource.*
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class Quote(val buy: Double, val sell: Double)

/** Arz-talep ve bölgeye göre değişen fiyatlarla ticaret, kervanlar. */
object TradeSystem {
    const val LOT = 10.0
    const val CARAVAN_LOT = 30.0

    private val basePrice = mapOf(
        FOOD to 1.0, WATER to 1.0, WOOD to 1.2, STONE to 1.3, METAL to 3.0, CLOTH to 2.5, TOOLS to 5.0,
        CRYSTAL to 8.0, RARE_ORE to 9.0, ANCIENT_PART to 14.0, TECH_PART to 16.0, LIGHT_MATERIAL to 10.0, CARE_ESSENCE to 9.0,
    )

    fun tradable(r: Resource) = r in basePrice

    /** Bölgesel kıtlık: çölde su pahalı, ormanda odun ucuz… */
    fun biomeFactor(b: Biome, r: Resource): Double = when (b) {
        Biome.DESERT, Biome.VOLCANIC -> when (r) { WATER -> 2.2; FOOD -> 1.4; STONE, RARE_ORE -> 0.75; else -> 1.0 }
        Biome.FOREST, Biome.MYSTIC_FOREST -> when (r) { WOOD -> 0.6; CARE_ESSENCE -> 0.8; METAL -> 1.3; else -> 1.0 }
        Biome.MOUNTAINS -> when (r) { STONE -> 0.7; METAL -> 0.8; FOOD -> 1.3; else -> 1.0 }
        Biome.LAKE, Biome.SWAMP -> when (r) { WATER -> 0.5; FOOD -> 0.8; WOOD -> 1.2; else -> 1.0 }
        Biome.SNOW -> when (r) { FOOD -> 1.5; WATER -> 0.7; WOOD -> 1.4; else -> 1.0 }
        Biome.CRYSTAL_VALLEY -> if (r == CRYSTAL) 0.6 else 1.0
        Biome.RUINS -> if (r == ANCIENT_PART || r == TECH_PART) 0.7 else 1.0
        Biome.STORMLANDS -> if (r == CRYSTAL) 0.8 else 1.1
        Biome.PLAINS -> if (r == FOOD) 0.85 else 1.0
    }

    fun hasMarket(state: GameState) = state.buildings.any { it.type == BuildingType.TRADE_MARKET && !it.underConstruction && it.enabled && it.workers > 0 }

    fun range(state: GameState, c: Civilization): Int {
        var r = 2
        if (hasMarket(state)) r += 2
        if (Treaty.TRADE in c.treaties) r += 2
        return r
    }

    fun quote(state: GameState, c: Civilization, r: Resource): Quote {
        val base = basePrice[r] ?: return Quote(0.0, 0.0)
        val biome = state.world.biomeAt(c.position)
        val stock = (c.stock[r] ?: 1.0).coerceAtLeast(1.0)
        val stockFactor = sqrt(CivSystem.targetStock(c, r) / stock).coerceIn(0.5, 2.5)
        val culture = when (r) { in c.culture.offers -> 0.8; in c.culture.wants -> 1.35; else -> 1.0 }
        var rel = 1.0 - (c.relation / 100.0) * 0.2
        if (Treaty.TRADE in c.treaties) rel *= 0.9
        rel *= 1.0 - (state.reputation / 1000.0)
        val boost = if (c.boostResource == r && c.boostSeconds > 0) 1.8 else 1.0
        val buy = base * biomeFactor(biome, r) * stockFactor * culture * rel * boost
        val spread = (if (hasMarket(state)) 0.78 else 0.65) * Bonuses.sellPrice(state)
        return Quote(buy, buy * spread.coerceAtMost(0.95))
    }

    fun canTrade(state: GameState, c: Civilization): String? = when {
        !c.met -> "Bu medeniyetle henüz karşılaşmadınız."
        c.atWar -> "Savaş halindeyken ticaret yapılamaz."
        c.relation <= -50 -> "${c.name} sizinle ticaret yapmayı reddediyor."
        c.position.dist(state.travel.position) > range(state, c) -> "Çok uzak: ticaret menzili ${range(state, c)} bölge (şu an ${c.position.dist(state.travel.position)})."
        else -> null
    }

    fun buy(state: GameState, civId: Int, r: Resource): ActionResult {
        val c = CivSystem.civ(state, civId) ?: return ActionResult.fail("Medeniyet bulunamadı.")
        canTrade(state, c)?.let { return ActionResult.fail(it) }
        if (!tradable(r)) return ActionResult.fail("Bu kaynak alınıp satılamaz.")
        if ((c.stock[r] ?: 0.0) < LOT) return ActionResult.fail("${c.name} elinde yeterli ${r.title.lowercase()} yok.")
        val price = (quote(state, c, r).buy * LOT).roundToInt().toDouble().coerceAtLeast(1.0)
        if (state.store[GOLD] < price) return ActionResult.fail("Yetersiz altın: ${fmt(price)} gerekli.")
        if (state.store.cap(r) - state.store[r] < LOT) return ActionResult.fail("Depoda yer yok.")
        state.store.spend(amounts(GOLD to price))
        state.store.add(r, LOT)
        c.stock[r] = (c.stock[r] ?: 0.0) - LOT
        afterTrade(state, c)
        return ActionResult.ok("${fmt(LOT)} ${r.title} ${fmt(price)} altına alındı.")
    }

    fun sell(state: GameState, civId: Int, r: Resource): ActionResult {
        val c = CivSystem.civ(state, civId) ?: return ActionResult.fail("Medeniyet bulunamadı.")
        canTrade(state, c)?.let { return ActionResult.fail(it) }
        if (!tradable(r)) return ActionResult.fail("Bu kaynak alınıp satılamaz.")
        if (state.store[r] < LOT) return ActionResult.fail("Satacak yeterli ${r.title.lowercase()} yok.")
        val price = (quote(state, c, r).sell * LOT).roundToInt().toDouble().coerceAtLeast(1.0)
        state.store.take(r, LOT)
        state.store.add(GOLD, price)
        c.stock[r] = (c.stock[r] ?: 0.0) + LOT
        afterTrade(state, c)
        return ActionResult.ok("${fmt(LOT)} ${r.title} ${fmt(price)} altına satıldı.")
    }

    private fun afterTrade(state: GameState, c: Civilization) {
        c.relation = (c.relation + 0.6).coerceAtMost(100.0)
        state.reputation = (state.reputation + 0.25).coerceAtMost(100.0)
        state.stats.trades++
        state.cityVersion++
    }

    // ------------------------------------------------------------------ Kervanlar

    fun caravanSlots(state: GameState): Int {
        if (!hasMarket(state)) return 0
        val market = state.buildings.first { it.type == BuildingType.TRADE_MARKET }
        return market.level + state.buildings.count { it.type == BuildingType.TRADE_DEPOT && !it.underConstruction }
    }

    fun caravanReason(state: GameState, c: Civilization): String? = when {
        !hasMarket(state) -> "Kervan için çalışan bir Ticaret Pazarı gerekli."
        !c.met -> "Bu medeniyetle henüz karşılaşmadınız."
        c.atWar || c.relation < 0 -> "${c.name} kervan kabul etmiyor (ilişki en az Tarafsız olmalı)."
        c.position.dist(state.travel.position) > 10 -> "Kervanlar en fazla 10 bölge uzağa gidebilir."
        state.caravans.size >= caravanSlots(state) -> "Bütün kervanlar yolda (${caravanSlots(state)} yuva)."
        else -> null
    }

    fun caravanSeconds(state: GameState, c: Civilization) = 40.0 + c.position.dist(state.travel.position) * 25.0

    /** Kervan: malları uzak bir medeniyete pazar fiyatına yakın (yayılım kaybı olmadan) satar. */
    fun caravanPayout(state: GameState, c: Civilization, r: Resource) =
        (quote(state, c, r).buy * 0.92 * CARAVAN_LOT).roundToInt().toDouble()

    fun sendCaravan(state: GameState, civId: Int, r: Resource): ActionResult {
        val c = CivSystem.civ(state, civId) ?: return ActionResult.fail("Medeniyet bulunamadı.")
        caravanReason(state, c)?.let { return ActionResult.fail(it) }
        if (!tradable(r)) return ActionResult.fail("Bu kaynak satılamaz.")
        if (state.store[r] < CARAVAN_LOT) return ActionResult.fail("Kervan için en az ${fmt(CARAVAN_LOT)} ${r.title} gerekli.")
        state.store.take(r, CARAVAN_LOT)
        val t = caravanSeconds(state, c)
        state.caravans += Caravan(c.id, r, CARAVAN_LOT, caravanPayout(state, c, r), t, t)
        state.log("${fmt(CARAVAN_LOT)} ${r.title} taşıyan bir kervan ${c.name} yolunda.", LogKind.DIPLOMACY)
        return ActionResult.ok("Kervan yola çıktı (~${(t / 60).roundToInt().coerceAtLeast(1)} dk).")
    }

    fun stepCaravans(state: GameState, dt: Double, offline: Boolean) {
        val it = state.caravans.iterator()
        while (it.hasNext()) {
            val k = it.next()
            k.secondsLeft -= dt
            if (k.secondsLeft > 0) continue
            it.remove()
            val c = CivSystem.civ(state, k.civId)
            var payout = k.payout
            // Haydut riski (müttefik koruması ve çevrimdışıyken yok)
            val risk = if (offline || c?.status == Relation.ALLY) 0.0 else 0.08
            val robbed = state.nextRandom() < risk
            if (robbed) payout *= 0.6
            state.store.add(GOLD, payout)
            c?.let { it.stock[k.resource] = (it.stock[k.resource] ?: 0.0) + k.amount; it.relation = (it.relation + 1.5).coerceAtMost(100.0) }
            state.reputation = (state.reputation + 0.5).coerceAtMost(100.0)
            state.stats.trades++
            state.log(
                if (robbed) "Kervan haydutlara yakalandı ama ${fmt(payout)} altınla döndü." else "Kervan ${c?.name ?: "uzak pazar"} dönüşü ${fmt(payout)} altın getirdi.",
                LogKind.DIPLOMACY,
            )
        }
    }
}

/** Anlaşmalar, hediyeler, ittifak, savaş ve barış. Kararlar ilişkilere kalıcı işlenir. */
object DiplomacySystem {
    const val GIFT_GOLD = 40.0
    const val AID_AMOUNT = 40.0
    const val PEACE_WAIT = 120.0

    fun inContact(state: GameState, c: Civilization) = c.met && c.position.dist(state.travel.position) <= 8

    private fun guard(state: GameState, id: Int): Pair<Civilization?, ActionResult?> {
        val c = CivSystem.civ(state, id) ?: return null to ActionResult.fail("Medeniyet bulunamadı.")
        if (!c.met) return null to ActionResult.fail("Bu medeniyetle henüz karşılaşmadınız.")
        if (!inContact(state, c)) return null to ActionResult.fail("Elçi göndermek için çok uzak (en fazla 8 bölge).")
        return c to null
    }

    private fun changeRelation(c: Civilization, delta: Double) { c.relation = (c.relation + delta).coerceIn(-100.0, 100.0) }

    fun giftGold(state: GameState, id: Int): ActionResult {
        val (c, err) = guard(state, id); if (err != null) return err; c!!
        if (c.atWar) return ActionResult.fail("Savaştayken hediye kabul etmiyorlar; önce barış.")
        if (state.store[GOLD] < GIFT_GOLD) return ActionResult.fail("Yetersiz altın.")
        state.store.take(GOLD, GIFT_GOLD)
        val gain = if (c.lastGiftDay == state.day) 3.0 else 8.0
        c.lastGiftDay = state.day
        changeRelation(c, gain)
        state.reputation = (state.reputation + 0.5).coerceAtMost(100.0)
        state.log("${c.name} kuruluna ${fmt(GIFT_GOLD)} altın hediye edildi.", LogKind.DIPLOMACY)
        return ActionResult.ok("${c.name} hediyeden memnun (+${fmt(gain)} ilişki).")
    }

    /** Yardım: istedikleri kaynaklardan gönderilirse daha etkili. */
    fun sendAid(state: GameState, id: Int, r: Resource): ActionResult {
        val (c, err) = guard(state, id); if (err != null) return err; c!!
        if (c.atWar) return ActionResult.fail("Savaştayken yardım kabul etmiyorlar.")
        if (state.store[r] < AID_AMOUNT) return ActionResult.fail("Yardım için ${fmt(AID_AMOUNT)} ${r.title} gerekli.")
        state.store.take(r, AID_AMOUNT)
        c.stock[r] = (c.stock[r] ?: 0.0) + AID_AMOUNT
        val gain = (if (r in c.culture.wants) 12.0 else 5.0) * if (c.lastGiftDay == state.day) 0.4 else 1.0
        c.lastGiftDay = state.day
        changeRelation(c, gain)
        state.reputation = (state.reputation + 1.0).coerceAtMost(100.0)
        state.log("${c.name} halkına ${fmt(AID_AMOUNT)} ${r.title} yardım gönderildi.", LogKind.DIPLOMACY)
        return ActionResult.ok("Yardım ulaştı (+${fmt(gain)} ilişki).")
    }

    fun treatyCost(t: Treaty): Amounts = when (t) {
        Treaty.TRADE -> amounts(GOLD to 60)
        Treaty.PASSAGE -> amounts(GOLD to 30)
        Treaty.ALLIANCE -> amounts(GOLD to 120, FOOD to 50)
    }

    fun treatyRequirement(t: Treaty): Double = when (t) { Treaty.TRADE -> 20.0; Treaty.PASSAGE -> 0.0; Treaty.ALLIANCE -> 60.0 }

    fun treatyReason(state: GameState, c: Civilization, t: Treaty): String? = when {
        t in c.treaties -> "Zaten ${t.title} var."
        c.atWar -> "Savaş halindesiniz."
        c.relation < treatyRequirement(t) -> "İlişki en az ${treatyRequirement(t).toInt()} olmalı (şu an ${c.relation.toInt()})."
        t == Treaty.ALLIANCE && Treaty.TRADE !in c.treaties -> "Önce ticaret anlaşması gerekli."
        !inContact(state, c) -> "Elçi göndermek için çok uzak."
        !state.store.canAfford(treatyCost(t)) -> "Yetersiz kaynak: ${treatyCost(t).describe()}"
        else -> null
    }

    fun propose(state: GameState, id: Int, t: Treaty): ActionResult {
        val c = CivSystem.civ(state, id) ?: return ActionResult.fail("Medeniyet bulunamadı.")
        treatyReason(state, c, t)?.let { return ActionResult.fail(it) }
        state.store.spend(treatyCost(t))
        c.treaties += t
        changeRelation(c, 5.0)
        state.reputation = (state.reputation + 2.0).coerceAtMost(100.0)
        state.log("${c.name} ile ${t.title} imzalandı.", LogKind.DIPLOMACY)
        if (t == Treaty.ALLIANCE) {
            // Kalıcı sonuç: müttefikin geleneksel rakipleri size soğur.
            for (o in state.civs) if (o.id != c.id && o.culture == c.culture.rival) {
                changeRelation(o, -15.0)
                if (o.met) state.log("${o.name}, ${c.name} ile kurduğunuz ittifaktan rahatsız oldu.", LogKind.WARNING)
            }
        }
        state.cityVersion++
        return ActionResult.ok("${t.title} imzalandı!")
    }

    fun declareWar(state: GameState, id: Int): ActionResult {
        val c = CivSystem.civ(state, id) ?: return ActionResult.fail("Medeniyet bulunamadı.")
        if (!c.met) return ActionResult.fail("Bu medeniyetle henüz karşılaşmadınız.")
        if (c.atWar) return ActionResult.fail("Zaten savaştasınız.")
        c.atWar = true
        c.warSeconds = 0.0
        c.treaties.clear()
        c.relation = -100.0
        state.reputation = (state.reputation - 10).coerceAtLeast(0.0)
        for (o in state.civs) if (o.id != c.id && o.met) {
            when {
                o.culture == c.culture.rival -> changeRelation(o, 10.0)
                Treaty.ALLIANCE in o.treaties -> Unit // müttefikler yanınızda
                else -> changeRelation(o, -5.0)
            }
        }
        state.log("${c.name} medeniyetine savaş ilan edildi! Yakınlarındayken baskınlar bekleyin.", LogKind.WARNING)
        state.cityVersion++
        return ActionResult.ok("Savaş ilan edildi.")
    }

    fun peaceCost(c: Civilization): Amounts = amounts(GOLD to (40 + c.power * 6).roundToInt())

    fun peaceReason(state: GameState, c: Civilization): String? = when {
        !c.atWar -> "Savaş yok."
        c.warSeconds < PEACE_WAIT -> "${c.name} henüz görüşmeye hazır değil (${(PEACE_WAIT - c.warSeconds).toInt()} sn)."
        !inContact(state, c) -> "Elçi göndermek için çok uzak."
        !state.store.canAfford(peaceCost(c)) -> "Barış bedeli: ${peaceCost(c).describe()}"
        else -> null
    }

    fun makePeace(state: GameState, id: Int): ActionResult {
        val c = CivSystem.civ(state, id) ?: return ActionResult.fail("Medeniyet bulunamadı.")
        peaceReason(state, c)?.let { return ActionResult.fail(it) }
        state.store.spend(peaceCost(c))
        c.atWar = false
        c.relation = -35.0
        state.reputation = (state.reputation + 3).coerceAtMost(100.0)
        state.log("${c.name} ile barış imzalandı. İlişkiler hâlâ gergin.", LogKind.DIPLOMACY)
        state.cityVersion++
        return ActionResult.ok("Barış sağlandı.")
    }

    fun powerLabel(c: Civilization): String {
        val label = when { c.power < 10 -> "Zayıf"; c.power < 18 -> "Orta"; c.power < 30 -> "Güçlü"; else -> "Çok güçlü" }
        return "$label (${c.power.toInt()})"
    }
}
