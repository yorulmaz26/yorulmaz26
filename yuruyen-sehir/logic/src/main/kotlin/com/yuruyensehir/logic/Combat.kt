package com.yuruyensehir.logic

import com.yuruyensehir.logic.Resource.*
import kotlinx.serialization.Serializable
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

@Serializable
enum class EnemyType(
    val title: String,
    val hp: Double,
    /** kare/saniye */
    val speed: Double,
    val dps: Double,
    val lootGold: Double,
    val prefersTowers: Boolean,
    val prefersStorage: Boolean,
    val steals: Boolean,
) {
    BANDIT("Haydut", 30.0, 0.9, 4.0, 6.0, false, true, true),
    BEAST("Vahşi Canlı", 45.0, 1.25, 6.0, 0.0, false, false, false),
    MECH("Mekanik Gözcü", 75.0, 0.6, 8.0, 10.0, true, false, false),
    RAIDER("Akıncı", 40.0, 1.0, 6.0, 8.0, false, true, true),
}

@Serializable
enum class TargetPriority(val title: String) {
    NEAREST("En yakın"), STRONGEST("En güçlü"), WEAKEST("En zayıf"), FASTEST("En hızlı"), ATTACKERS("Binaya saldıran")
}

@Serializable
data class Enemy(
    val id: Int,
    val type: EnemyType,
    var x: Double,
    var y: Double,
    var hp: Double,
    val maxHp: Double,
    var targetId: Int = -1,
    var retarget: Double = 0.0,
    var attacking: Boolean = false,
)

@Serializable
data class Guard(val homeId: Int, var x: Double, var y: Double, var hp: Double = Guard.MAX_HP, var respawn: Double = 0.0) {
    val alive get() = respawn <= 0.0
    companion object { const val MAX_HP = 35.0 }
}

@Serializable
data class Battle(
    val title: String,
    val enemies: MutableList<Enemy>,
    val total: Int,
    var prep: Double,
    val guards: MutableList<Guard> = mutableListOf(),
    var shield: Double = 0.0,
    var elapsed: Double = 0.0,
    var repairCooldown: Double = 0.0,
    val stolen: MutableMap<Resource, Double> = mutableMapOf(),
    val civId: Int = -1,
    var kills: Int = 0,
    var lootGold: Double = 0.0,
    /** Düşmanların geldiği yön (radyan, ızgara merkezine göre). */
    val angle: Double = 0.0,
    /** Yağma üst sınırı: savaş başındaki stokun %20'si (adil kayıp). */
    val stealCap: MutableMap<Resource, Double> = mutableMapOf(),
)

/** Arayüz için: atış izi (kalıcı değil). */
data class Shot(val x1: Double, val y1: Double, val x2: Double, val y2: Double, var ttl: Double, val energy: Boolean)

/** Gerçek zamanlı, oyun hızıyla duraklatılabilen savunma savaşı. */
object CombatSystem {
    const val TIME_LIMIT = 150.0
    const val TOWER_INTERVAL = 1.0
    const val GUARD_DPS = 4.0
    const val SHIELD_ENERGY_PER_SEC = 0.4

    fun staffed(b: PlacedBuilding) = !b.underConstruction && b.enabled && (b.def.workers == 0 || b.workers > 0)

    fun towers(state: GameState) = state.buildings.filter { it.def.towerDps > 0 && staffed(it) }

    fun towerRange(state: GameState, b: PlacedBuilding): Double {
        var r = b.def.towerRange + 0.3 * (b.level - 1)
        if (b.type != BuildingType.WATCHTOWER && state.buildings.any { it.type == BuildingType.WATCHTOWER && staffed(it) && it.gapTo(b) <= 2 }) r += 1.5
        return r
    }

    fun towerDps(state: GameState, b: PlacedBuilding): Double {
        val wr = if (b.def.workers == 0) 1.0 else b.workers.toDouble() / b.def.workers
        var d = b.def.towerDps * (1.0 + 0.4 * (b.level - 1)) * wr * (0.5 + 0.5 * b.hp / 100.0)
        if (ResearchSystem.has(state, TechId.REINFORCED_ARROWS)) d *= 1.25
        return d * Bonuses.towerDps(state)
    }

    fun shieldCapacity(state: GameState): Double {
        var c = state.buildings.filter { it.def.shieldCapacity > 0 && staffed(it) }.sumOf { it.def.shieldCapacity * it.level }
        if (ResearchSystem.has(state, TechId.ENERGY_SHIELDS)) c *= 1.5
        return c
    }

    fun guardCount(state: GameState) = state.buildings.filter { it.def.guardCount > 0 && staffed(it) }.sumOf { it.def.guardCount * it.level }

    /** Arayüz ve tehdit tahmini için toplam savunma gücü. */
    fun defenseRating(state: GameState): Double =
        towers(state).sumOf { towerDps(state, it) } + guardCount(state) * GUARD_DPS + shieldCapacity(state) / 10.0

    fun prepTime(state: GameState) = 4.0 + if (state.buildings.any { it.type == BuildingType.WATCHTOWER && staffed(it) }) 8.0 else 0.0

    /** "BANDIT:3,BEAST:1" biçiminde düşman tanımı. */
    fun parseSpec(spec: String): List<Pair<EnemyType, Int>> = spec.split(',').mapNotNull {
        val t = runCatching { EnemyType.valueOf(it.substringBefore(':').trim()) }.getOrNull() ?: return@mapNotNull null
        t to (it.substringAfter(':').trim().toIntOrNull() ?: 1)
    }

    /** Tehdidin gücü günle ve tehlikeyle artar ama ölçülü kalır. */
    fun scaledCount(state: GameState, base: Int, danger: Double): Int =
        (base + state.day / 6 + (danger * 3).roundToInt()).coerceIn(1, 14)

    fun start(state: GameState, title: String, spec: List<Pair<EnemyType, Int>>, civId: Int = -1): ActionResult {
        if (state.battle != null) return ActionResult.fail("Zaten savaş var.")
        val angle = state.nextRandom() * Math.PI * 2
        val rx = CityGrid.radiusX(state); val ry = CityGrid.radiusY(state)
        val enemies = ArrayList<Enemy>()
        var id = 1
        val hpScale = 1.0 + state.day * 0.03
        for ((type, n) in spec) repeat(n) {
            val a = angle + (state.nextRandom() - 0.5) * 1.1
            val d = 1.45 + state.nextRandom() * 0.5
            enemies += Enemy(id++, type, CityGrid.CENTER_X + cos(a) * rx * d, CityGrid.CENTER_Y + sin(a) * ry * d, type.hp * hpScale, type.hp * hpScale)
        }
        val battle = Battle(title, enemies, enemies.size, prepTime(state), civId = civId, angle = angle, shield = shieldCapacity(state))
        for (r in listOf(GOLD, FOOD, WATER)) battle.stealCap[r] = state.store[r] * 0.2
        for (b in state.buildings.filter { it.def.guardCount > 0 && staffed(it) }) repeat(b.def.guardCount * b.level) { k ->
            battle.guards += Guard(b.id, b.centerX + (k % 2) * 0.4, b.centerY + (k / 2) * 0.4)
        }
        state.buildings.firstOrNull { it.type == BuildingType.CITY_CENTER }?.let { cc ->
            repeat(FleetSystem.guards(state)) { k -> battle.guards += Guard(cc.id, cc.centerX + (k % 3) * 0.4 - 0.4, cc.centerY + (k / 3) * 0.4) }
        }
        state.battle = battle
        state.log("Savaş başladı: $title (${enemies.size} düşman). Savunma gücü ${defenseRating(state).toInt()}.", LogKind.BATTLE)
        state.cityVersion++
        return ActionResult.ok("$title! Savunmaya geçin.")
    }

    private fun distToBuilding(x: Double, y: Double, b: PlacedBuilding): Double {
        val dx = maxOf(b.x - x, 0.0, x - (b.x + b.w)); val dy = maxOf(b.y - y, 0.0, y - (b.y + b.h))
        return sqrt(dx * dx + dy * dy)
    }

    private fun dist(ax: Double, ay: Double, bx: Double, by: Double) = sqrt((ax - bx) * (ax - bx) + (ay - by) * (ay - by))

    fun shielded(state: GameState, b: PlacedBuilding) = state.buildings.any { it.def.shieldRadius > 0 && staffed(it) && it.gapTo(b) <= it.def.shieldRadius }

    // ------------------------------------------------------------------ Düşman yapay zekâsı
    /** Düşman türüne göre hedef seçimi: haydutlar depoları, mekanikler kuleleri, canlılar en yakını hedefler. */
    fun chooseTarget(state: GameState, e: Enemy): PlacedBuilding? {
        val candidates = state.buildings.filter { !it.underConstruction }
        if (candidates.isEmpty()) return null
        val alive = candidates.filter { it.hp > 20.5 }
        val pool = when {
            e.type.prefersTowers -> alive.filter { it.def.towerDps > 0 }.ifEmpty { alive }
            e.type.prefersStorage -> candidates.filter { it.def.storage.isNotEmpty() }.ifEmpty { alive }
            else -> alive
        }.ifEmpty { candidates }
        return pool.minByOrNull { distToBuilding(e.x, e.y, it) }
    }

    private fun pickTarget(state: GameState, b: PlacedBuilding, battle: Battle, range: Double): Enemy? {
        val inRange = battle.enemies.filter { it.hp > 0 && dist(b.centerX, b.centerY, it.x, it.y) <= range }
        if (inRange.isEmpty()) return null
        return when (b.targetPriority ?: state.defensePriority) {
            TargetPriority.NEAREST -> inRange.minByOrNull { dist(b.centerX, b.centerY, it.x, it.y) }
            TargetPriority.STRONGEST -> inRange.maxByOrNull { it.hp }
            TargetPriority.WEAKEST -> inRange.minByOrNull { it.hp }
            TargetPriority.FASTEST -> inRange.maxByOrNull { it.type.speed }
            TargetPriority.ATTACKERS -> inRange.filter { it.attacking }.minByOrNull { it.hp } ?: inRange.minByOrNull { dist(b.centerX, b.centerY, it.x, it.y) }
        }
    }

    fun step(state: GameState, dt: Double) {
        val battle = state.battle ?: return
        battle.elapsed += dt
        battle.repairCooldown = (battle.repairCooldown - dt).coerceAtLeast(0.0)
        state.shots.forEach { it.ttl -= dt }
        state.shots.removeAll { it.ttl <= 0 }
        if (battle.prep > 0) { battle.prep -= dt; return }

        // Kalkan enerjiyle dolar
        val cap = shieldCapacity(state)
        if (battle.shield < cap) {
            val need = SHIELD_ENERGY_PER_SEC * dt
            val got = state.store.take(ENERGY, need)
            battle.shield = (battle.shield + 5.0 * dt * (got / need)).coerceAtMost(cap)
        }

        // Kuleler
        for (t in towers(state)) {
            val cd = (state.towerCooldown[t.id] ?: 0.0) - dt
            if (cd > 0) { state.towerCooldown[t.id] = cd; continue }
            val target = pickTarget(state, t, battle, towerRange(state, t))
            if (target == null) { state.towerCooldown[t.id] = 0.0; continue }
            if (t.def.towerEnergyPerShot > 0 && state.store.take(ENERGY, t.def.towerEnergyPerShot) + 1e-9 < t.def.towerEnergyPerShot) {
                state.towerCooldown[t.id] = TOWER_INTERVAL
                continue
            }
            target.hp -= towerDps(state, t) * TOWER_INTERVAL
            state.towerCooldown[t.id] = TOWER_INTERVAL
            if (state.shots.size < 60) state.shots += Shot(t.centerX, t.centerY, target.x, target.y, 0.25, t.def.towerEnergyPerShot > 0)
        }

        // Muhafızlar
        for (g in battle.guards) {
            if (!g.alive) {
                g.respawn -= dt
                if (g.respawn <= 0) { state.building(g.homeId)?.let { g.x = it.centerX; g.y = it.centerY }; g.hp = Guard.MAX_HP }
                continue
            }
            val home = state.building(g.homeId)
            val enemy = battle.enemies.filter { it.hp > 0 && (home == null || dist(home.centerX, home.centerY, it.x, it.y) <= 5.0) }
                .minByOrNull { dist(g.x, g.y, it.x, it.y) } ?: continue
            val d = dist(g.x, g.y, enemy.x, enemy.y)
            if (d > 0.45) {
                val s = minOf(d, 1.3 * dt)
                g.x += (enemy.x - g.x) / d * s; g.y += (enemy.y - g.y) / d * s
            } else enemy.hp -= GUARD_DPS * dt
        }

        // Düşmanlar
        for (e in battle.enemies) {
            if (e.hp <= 0) continue
            val blocker = battle.guards.firstOrNull { it.alive && dist(it.x, it.y, e.x, e.y) <= 0.5 }
            if (blocker != null) {
                e.attacking = false
                blocker.hp -= e.type.dps * dt
                if (blocker.hp <= 0) blocker.respawn = 20.0
                continue
            }
            e.retarget -= dt
            var target = state.building(e.targetId)
            if (target == null || e.retarget <= 0 || (target.hp <= 20.5 && !e.type.steals)) {
                target = chooseTarget(state, e)
                e.targetId = target?.id ?: -1
                e.retarget = 1.0
            }
            if (target == null) continue
            val d = distToBuilding(e.x, e.y, target)
            if (d > 0.5) {
                e.attacking = false
                val tx = target.centerX; val ty = target.centerY
                val dd = dist(e.x, e.y, tx, ty).coerceAtLeast(1e-6)
                val s = minOf(dd, e.type.speed * dt)
                e.x += (tx - e.x) / dd * s; e.y += (ty - e.y) / dd * s
            } else {
                e.attacking = true
                var dmg = e.type.dps * dt * EventSystem.damageMultiplier(state)
                if (battle.shield > 0 && shielded(state, target)) {
                    val absorbed = minOf(battle.shield, dmg)
                    battle.shield -= absorbed; dmg -= absorbed
                }
                target.hp = (target.hp - dmg).coerceAtLeast(20.0)
                if (target.hp <= 20.0 && e.type.steals) {
                    for (r in listOf(GOLD, FOOD)) {
                        val room = (battle.stealCap[r] ?: 0.0) - (battle.stolen[r] ?: 0.0)
                        if (room <= 0) continue
                        val got = state.store.take(r, minOf(room, 1.0 * dt))
                        if (got > 0) battle.stolen[r] = (battle.stolen[r] ?: 0.0) + got
                    }
                }
            }
        }

        // Ölen düşmanlar
        val dead = battle.enemies.filter { it.hp <= 0 }
        if (dead.isNotEmpty()) {
            battle.enemies.removeAll(dead)
            battle.kills += dead.size
            for (e in dead) battle.lootGold += e.type.lootGold
        }

        when {
            battle.enemies.isEmpty() -> finish(state, victory = true)
            battle.elapsed >= TIME_LIMIT + battle.prep -> finish(state, victory = false)
        }
    }

    private fun finish(state: GameState, victory: Boolean, tribute: Boolean = false) {
        val b = state.battle ?: return
        state.battle = null
        state.shots.clear()
        val civ = CivSystem.civ(state, b.civId)
        if (victory) {
            // Çalınanların yarısı geri alınır
            b.stolen.forEach { (r, v) -> state.store.add(r, v * 0.5) }
            state.store.add(GOLD, b.lootGold)
            CarrierSystem.addXp(state, 10.0 + b.kills * 3)
            state.stats.battlesWon++
            civ?.let { it.power = (it.power - 2).coerceAtLeast(3.0); it.relation = (it.relation - 3).coerceAtLeast(-100.0) }
            state.log("Zafer! ${b.title} püskürtüldü: ${b.kills} düşman, ${fmt(b.lootGold)} altın ganimet.", LogKind.BATTLE)
        } else {
            if (!tribute) {
                // Geri çekilirken kalan düşmanlar küçük bir yağma yapar; şehir yok olmaz.
                val n = b.enemies.size
                for (r in listOf(GOLD, FOOD, WATER)) {
                    val room = ((b.stealCap[r] ?: 0.0) - (b.stolen[r] ?: 0.0)).coerceAtLeast(0.0)
                    val got = state.store.take(r, minOf(room, n * 6.0))
                    b.stolen[r] = (b.stolen[r] ?: 0.0) + got
                }
                state.stats.battlesLost++
            }
            val lost = b.stolen.entries.filter { it.value >= 1 }.joinToString { "${fmt(it.value)} ${it.key.title}" }
            state.log(
                (if (tribute) "Haraç ödendi; ${b.title} sona erdi." else "${b.title}: düşman geri çekildi.") + if (lost.isNotEmpty()) " Kayıp: $lost." else "",
                if (tribute) LogKind.BATTLE else LogKind.WARNING,
            )
        }
        state.cityVersion++
    }

    fun tributeCost(state: GameState): Amounts = amounts(GOLD to ((state.battle?.enemies?.size ?: 0) * 12))

    fun payTribute(state: GameState): ActionResult {
        val b = state.battle ?: return ActionResult.fail("Savaş yok.")
        val cost = tributeCost(state)
        if (!state.store.canAfford(cost)) return ActionResult.fail("Haraç için yetersiz altın: ${cost.describe()}")
        state.store.spend(cost)
        CivSystem.civ(state, b.civId)?.let { it.relation = (it.relation + 5).coerceAtMost(100.0) }
        finish(state, victory = false, tribute = true)
        return ActionResult.ok("Haraç ödendi, düşman çekildi.")
    }

    val repairCost: Amounts = amounts(WOOD to 20, STONE to 10)

    /** Onarım ekipleri: en hasarlı üç binayı +20 onarır. */
    fun emergencyRepair(state: GameState): ActionResult {
        val b = state.battle ?: return ActionResult.fail("Savaş yok.")
        if (b.repairCooldown > 0) return ActionResult.fail("Onarım ekipleri yorgun (${b.repairCooldown.toInt()} sn).")
        if (!state.store.canAfford(repairCost)) return ActionResult.fail("Yetersiz kaynak: ${repairCost.describe()}")
        val targets = state.buildings.filter { it.hp < 99 }.sortedBy { it.hp }.take(3)
        if (targets.isEmpty()) return ActionResult.fail("Onarılacak bina yok.")
        state.store.spend(repairCost)
        targets.forEach { it.hp = (it.hp + 20).coerceAtMost(100.0) }
        b.repairCooldown = 25.0
        return ActionResult.ok("Onarım ekipleri ${targets.size} binayı onardı.")
    }

    fun setPriority(state: GameState, p: TargetPriority): ActionResult {
        state.defensePriority = p
        return ActionResult.ok("Hedef önceliği: ${p.title}")
    }

    fun setTowerPriority(state: GameState, id: Int, p: TargetPriority?): ActionResult {
        val b = state.building(id) ?: return ActionResult.fail("Bina bulunamadı.")
        if (b.def.towerDps <= 0) return ActionResult.fail("Bu bir savunma kulesi değil.")
        b.targetPriority = p
        return ActionResult.ok("${b.def.title}: hedef ${p?.title ?: "genel önceliği izler"}")
    }
}
