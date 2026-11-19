package com.yuruyensehir.logic

import com.yuruyensehir.logic.Resource.*
import kotlinx.serialization.Serializable

/** Kültür: medeniyetin neyi bol ürettiği, neye ihtiyaç duyduğu ve mizacı. */
@Serializable
enum class Culture(
    val title: String,
    val description: String,
    val offers: List<Resource>,
    val wants: List<Resource>,
    val nomadic: Boolean,
    /** -1 saldırgan … +1 barışçıl */
    val temper: Double,
) {
    MERCHANT_GUILD("Gezgin Tüccar Loncası", "Kervanlarla dolaşan, alışverişi seven barışçıl tüccarlar.", listOf(TOOLS, CLOTH, LIGHT_MATERIAL), listOf(FOOD, WATER, CRYSTAL), true, 0.8),
    DESERT_NOMADS("Kum Göçebeleri", "Çölde yaşayan, suya her şeyden çok değer veren göçebeler.", listOf(RARE_ORE, STONE, METAL), listOf(WATER, FOOD, CLOTH), true, 0.1),
    FOREST_CLAN("Yaprak Klanı", "Ormanın derinliklerinde yerleşik, doğayla uyumlu bir klan.", listOf(WOOD, FOOD, CARE_ESSENCE), listOf(METAL, TOOLS), false, 0.4),
    IRON_UNION("Demir Birliği", "Dağ ocaklarında çalışan, gururlu ve sert madenciler.", listOf(METAL, TOOLS, STONE), listOf(FOOD, WOOD, CLOTH), false, -0.4),
    CRYSTAL_ORDER("Kristal Tarikatı", "Kristal vadilerinin sırlarını koruyan bilginler.", listOf(CRYSTAL, TECH_PART, ANCIENT_PART), listOf(CLOTH, FOOD, METAL), false, 0.2),
    LAKE_FOLK("Göl Halkı", "Büyük göllerin kıyısında balıkçılıkla geçinen bir halk.", listOf(WATER, FOOD), listOf(WOOD, STONE, TOOLS), false, 0.6),
    STORM_RIDERS("Fırtına Binicileri", "Fırtınaların peşinden koşan, yağmacılığa meyilli akıncılar.", listOf(CRYSTAL, METAL), listOf(FOOD, WATER, TOOLS), true, -0.7),
    RIVAL_CITY("Rakip Yürüyen Şehir", "Kendi dev taşıyıcısının sırtında dolaşan rakip bir göçebe şehir.", listOf(TOOLS, CLOTH, STONE), listOf(FOOD, CARE_ESSENCE, METAL), true, -0.2);

    /** Bu kültürün geleneksel rakibi: onlarla ittifak, rakibi kızdırır. */
    val rival: Culture
        get() = when (this) {
            MERCHANT_GUILD -> STORM_RIDERS; STORM_RIDERS -> MERCHANT_GUILD
            FOREST_CLAN -> IRON_UNION; IRON_UNION -> FOREST_CLAN
            DESERT_NOMADS -> LAKE_FOLK; LAKE_FOLK -> DESERT_NOMADS
            CRYSTAL_ORDER -> RIVAL_CITY; RIVAL_CITY -> CRYSTAL_ORDER
        }
}

@Serializable
enum class Treaty(val title: String) { TRADE("Ticaret Anlaşması"), PASSAGE("Geçiş İzni"), ALLIANCE("İttifak") }

enum class Relation(val title: String) {
    ENEMY("Düşman"), RIVAL("Rakip"), NEUTRAL("Tarafsız"), FRIEND("Dost"), PARTNER("Ticaret Ortağı"), ALLY("Müttefik")
}

@Serializable
data class Civilization(
    val id: Int,
    val name: String,
    val culture: Culture,
    val home: Cell,
    var position: Cell,
    var relation: Double,
    var met: Boolean = false,
    val treaties: MutableSet<Treaty> = mutableSetOf(),
    var atWar: Boolean = false,
    var warSeconds: Double = 0.0,
    var power: Double = 8.0,
    val stock: MutableMap<Resource, Double> = mutableMapOf(),
    var moveTimer: Double = 0.0,
    var lastGiftDay: Int = -1,
    var raidTimer: Double = 0.0,
    var aidTimer: Double = 0.0,
    /** Rastgele ticaret fırsatı: bu kaynağa geçici olarak yüksek fiyat öderler. */
    var boostResource: Resource? = null,
    var boostSeconds: Double = 0.0,
) {
    val status: Relation
        get() = when {
            atWar || relation <= -50 -> Relation.ENEMY
            Treaty.ALLIANCE in treaties -> Relation.ALLY
            Treaty.TRADE in treaties -> Relation.PARTNER
            relation >= 25 -> Relation.FRIEND
            relation <= -15 -> Relation.RIVAL
            else -> Relation.NEUTRAL
        }
    val hostile: Boolean get() = status == Relation.ENEMY
}

@Serializable
data class Caravan(
    val civId: Int,
    val resource: Resource,
    val amount: Double,
    val payout: Double,
    var secondsLeft: Double,
    val totalSeconds: Double,
)

/** Medeniyetlerin üretimi, hareketi, karşılaşma ve bölgesel olayları. */
object CivSystem {
    const val REGION = 12
    private val prefixes = mapOf(
        Culture.MERCHANT_GUILD to listOf("Altın Yol", "Tuzlu Rüzgâr", "Yedi Çan", "Uzun Kervan"),
        Culture.DESERT_NOMADS to listOf("Serap", "Kızıl Kum", "Sıcak Rüzgâr", "Vaha Bekçisi"),
        Culture.FOREST_CLAN to listOf("Eski Meşe", "Yosunlu Taş", "Yeşil Gölge", "Kök"),
        Culture.IRON_UNION to listOf("Örs", "Kara Ocak", "Granit", "Demir Dağ"),
        Culture.CRYSTAL_ORDER to listOf("Mor Işık", "Prizma", "Sessiz Kristal", "Yıldız Taşı"),
        Culture.LAKE_FOLK to listOf("Mavi Kıyı", "Sazlık", "Durgun Su", "İnci"),
        Culture.STORM_RIDERS to listOf("Şimşek", "Kara Bulut", "Gök Gürültüsü", "Uluyan Rüzgâr"),
        Culture.RIVAL_CITY to listOf("Taşsırt", "Uzunbacak", "Yosunkabuk", "Dikenli Sırt"),
    )
    private val suffix = mapOf(
        Culture.MERCHANT_GUILD to "Loncası", Culture.DESERT_NOMADS to "Obası", Culture.FOREST_CLAN to "Klanı",
        Culture.IRON_UNION to "Birliği", Culture.CRYSTAL_ORDER to "Tarikatı", Culture.LAKE_FOLK to "Halkı",
        Culture.STORM_RIDERS to "Akıncıları", Culture.RIVAL_CITY to "Şehri",
    )

    fun civ(state: GameState, id: Int) = state.civs.firstOrNull { it.id == id }
    fun met(state: GameState) = state.civs.filter { it.met }
    fun at(state: GameState, c: Cell) = state.civs.firstOrNull { it.met && it.position == c }

    fun targetStock(c: Civilization, r: Resource): Double = when (r) {
        in c.culture.offers -> 320.0
        in c.culture.wants -> 40.0
        else -> 140.0
    }

    private fun cultureFor(b: Biome, h: Double): Culture = when (b) {
        Biome.DESERT, Biome.VOLCANIC -> if (h < 0.7) Culture.DESERT_NOMADS else Culture.IRON_UNION
        Biome.FOREST, Biome.MYSTIC_FOREST, Biome.SWAMP -> Culture.FOREST_CLAN
        Biome.MOUNTAINS, Biome.SNOW -> Culture.IRON_UNION
        Biome.CRYSTAL_VALLEY, Biome.RUINS -> Culture.CRYSTAL_ORDER
        Biome.LAKE -> Culture.LAKE_FOLK
        Biome.STORMLANDS -> Culture.STORM_RIDERS
        Biome.PLAINS -> when {
            h < 0.45 -> Culture.MERCHANT_GUILD
            h < 0.75 -> Culture.RIVAL_CITY
            else -> Culture.STORM_RIDERS
        }
    }

    /** Oyuncunun çevresindeki bölgelerde (12x12) medeniyetleri tembel ve deterministik biçimde üretir. */
    fun ensureNearby(state: GameState) = ensureAround(state, state.travel.position)

    /** Bir hücre çevresindeki bölgeleri üretir (keşif ekipleri de kullanır). */
    fun ensureAround(state: GameState, p: Cell, radius: Int = 1) {
        val rx = Math.floorDiv(p.x, REGION); val ry = Math.floorDiv(p.y, REGION)
        for (dx in -radius..radius) for (dy in -radius..radius) {
            val reg = Cell(rx + dx, ry + dy)
            if (!state.generatedRegions.add(reg.key())) continue
            val w = state.world
            val first = reg.x == 0 && reg.y == 0
            if (!first && w.hash01(reg.x, reg.y, 301) > 0.6) continue
            // Başlangıç bölgesinde, ilk dakikalarda karşılaşılacak barışçıl bir tüccar her zaman olsun.
            val pos = if (first) Cell(4, 1) else Cell(
                reg.x * REGION + 1 + (w.hash01(reg.x, reg.y, 302) * (REGION - 2)).toInt(),
                reg.y * REGION + 1 + (w.hash01(reg.x, reg.y, 303) * (REGION - 2)).toInt(),
            )
            val h = w.hash01(reg.x, reg.y, 304)
            val culture = if (first) Culture.MERCHANT_GUILD else cultureFor(w.biomeAt(pos), h)
            val names = prefixes.getValue(culture)
            val name = names[(w.hash01(reg.x, reg.y, 305) * names.size).toInt().coerceAtMost(names.size - 1)] + " " + suffix.getValue(culture)
            val civ = Civilization(
                id = state.nextCivId++, name = name, culture = culture, home = pos, position = pos,
                relation = (culture.temper * 20 + (state.reputation - 20) / 4).coerceIn(-40.0, 40.0),
                power = 6.0 + w.hash01(reg.x, reg.y, 306) * 8 + state.day * 0.3,
            )
            Resource.entries.filter { TradeSystem.tradable(it) }.forEach { civ.stock[it] = targetStock(civ, it) }
            state.civs += civ
        }
    }

    /** [c] çevresindeki [r] bölge içinde tanınmayan medeniyetlerle tanışır; tanışılan sayıyı döndürür. */
    fun meetNear(state: GameState, c: Cell, r: Int, offline: Boolean): Int {
        var n = 0
        for (civ in state.civs) if (!civ.met && civ.position.dist(c) <= r) {
            civ.met = true; n++
            state.travel.explored += civ.position.key()
            if (!offline) state.log("Keşif ekibi ${civ.name} (${civ.culture.title}) ile tanıştı.", LogKind.DIPLOMACY)
        }
        if (n > 0) state.cityVersion++
        return n
    }

    fun meetCheck(state: GameState, offline: Boolean) {
        val r = TravelSystem.revealRadius(state) + 1
        for (c in state.civs) {
            if (!c.met && c.position.dist(state.travel.position) <= r) {
                c.met = true
                state.travel.explored += c.position.key()
                if (!offline) state.log("Yeni bir medeniyetle karşılaştınız: ${c.name} (${c.culture.title}). İlişki: ${c.status.title}.", LogKind.DIPLOMACY)
                state.cityVersion++
            }
        }
    }

    fun step(state: GameState, dt: Double, offline: Boolean) {
        val minutes = dt / 60.0
        ensureNearby(state)
        meetCheck(state, offline)
        for (c in state.civs) {
            // Güç zamanla artar (rakipler gelişir)
            c.power += 0.12 * minutes
            // Stoklar hedefe doğru yenilenir
            for ((r, v) in c.stock) {
                val t = targetStock(c, r)
                c.stock[r] = v + (t - v) * (0.03 * minutes).coerceAtMost(1.0)
            }
            // İlişkiler: anlaşmalar ilişkiyi yavaşça güçlendirir; diğerleri mizaca doğru kayar
            if (!c.atWar) {
                if (Treaty.TRADE in c.treaties) c.relation = (c.relation + 0.15 * minutes).coerceAtMost(80.0)
                else {
                    val base = c.culture.temper * 20
                    c.relation += (base - c.relation) * 0.01 * minutes
                }
            } else c.warSeconds += dt
            c.relation = c.relation.coerceIn(-100.0, 100.0)
            // Göçebeler yurtlarının çevresinde dolaşır
            if (c.culture.nomadic) {
                c.moveTimer += dt
                if (c.moveTimer >= 150) {
                    c.moveTimer = 0.0
                    val nx = (c.position.x + (state.nextRandom() * 3).toInt() - 1).coerceIn(c.home.x - 5, c.home.x + 5)
                    val ny = (c.position.y + (state.nextRandom() * 3).toInt() - 1).coerceIn(c.home.y - 5, c.home.y + 5)
                    c.position = Cell(nx, ny)
                    if (c.met && TravelSystem.isExplored(state, c.position).not()) state.travel.explored += c.position.key()
                }
            }
            if (c.boostSeconds > 0) { c.boostSeconds -= dt; if (c.boostSeconds <= 0) c.boostResource = null }
            else if (!offline && c.met && !c.hostile && c.position.dist(state.travel.position) <= 8 && state.nextRandom() < 0.0015 * dt) {
                val pool = Resource.entries.filter { TradeSystem.tradable(it) && it !in c.culture.offers }
                val r = pool[(state.nextRandom() * pool.size).toInt().coerceAtMost(pool.size - 1)]
                c.boostResource = r; c.boostSeconds = 300.0
                state.log("Ticaret fırsatı: ${c.name} 5 dakika boyunca ${r.title} için neredeyse iki kat fiyat ödüyor!", LogKind.DIPLOMACY)
            }
            if (!offline) regionalEvents(state, c, dt)
        }
        if (offline || state.pendingEvent == null) TradeSystem.stepCaravans(state, dt, offline)
    }

    /** Yakındaki medeniyetlere bağlı olaylar: baskın, haraç, müttefik yardımı. */
    private fun regionalEvents(state: GameState, c: Civilization, dt: Double) {
        if (!c.met) return
        val dist = c.position.dist(state.travel.position)
        when {
            c.hostile && dist <= 3 -> {
                c.raidTimer += dt
                if (c.raidTimer >= 75 && state.pendingEvent == null) {
                    c.raidTimer = 0.0
                    if (state.nextRandom() < 0.5) state.pendingEvent = raidEvent(state, c)
                }
            }
            c.status == Relation.ALLY && dist <= 5 -> {
                c.aidTimer += dt
                if (c.aidTimer >= 240) {
                    c.aidTimer = 0.0
                    val r = c.culture.offers.first()
                    val got = state.store.add(r, 25.0)
                    if (got > 0) state.log("Müttefikiniz ${c.name} ${fmt(got)} ${r.title} gönderdi.", LogKind.DIPLOMACY)
                }
            }
        }
    }

    fun raidEvent(state: GameState, c: Civilization): PendingEvent {
        val tribute = (30 + c.power * 3).toInt()
        val n = (2 + c.power / 5).toInt().coerceIn(2, 12)
        val type = if (c.culture == Culture.RIVAL_CITY || c.culture == Culture.IRON_UNION) "MECH:${n / 3},RAIDER:${n - n / 3}" else "RAIDER:$n"
        return PendingEvent("raid", "${c.name} Baskını!", "$n düşman akıncı şehre yaklaşıyor (güç ${c.power.toInt()}). Savunma gücünüz ${CombatSystem.defenseRating(state).toInt()}.", listOf(
            EventOption("Savun", effect = "battle=$type@${c.id}"),
            EventOption("Haraç öde", cost = amounts(GOLD to tribute), effect = "rel=${c.id}:3"),
        ))
    }

    /** Geçiş izni olmadan rakip bölgesine girildiğinde (hücreye 2 kare yakınlık). */
    fun onEnterCell(state: GameState, cell: Cell) {
        if (state.pendingEvent != null) return
        for (c in state.civs) {
            if (!c.met || c.position.dist(cell) > 2 || Treaty.PASSAGE in c.treaties || c.status == Relation.ALLY) continue
            if (c.status == Relation.RIVAL || (c.status == Relation.NEUTRAL && c.culture.temper < 0)) {
                val toll = (15 + c.power * 2).toInt()
                state.pendingEvent = PendingEvent("toll", "${c.name} Sınırı", "${c.name} topraklarından geçiyorsunuz. Geçiş ücreti istiyorlar.", listOf(
                    EventOption("Ücreti öde", cost = amounts(GOLD to toll), effect = "rel=${c.id}:4"),
                    EventOption("Reddet", effect = "rel=${c.id}:-12"),
                ))
                return
            }
        }
    }
}
