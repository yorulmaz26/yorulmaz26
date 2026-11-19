package com.kayipadalar.logic.level

import com.kayipadalar.logic.data.GameConfig
import com.kayipadalar.logic.engine.*
import com.kayipadalar.logic.level.ObjectiveType.*
import com.kayipadalar.logic.model.*
import java.util.Random

/**
 * Kontrollü prosedürel bölüm üretici.
 *
 * Yöntem: önce hedefi sağlayan "çözülmüş" bir ada ağacı kurulur, sonra izin verilen hareketlerle
 * karıştırılır. Karıştırmanın tersi ve ışın araması ile gerçek kural motoru üzerinde bir çözüm
 * bulunur; çözüm bulunamayan aday reddedilir. Hamle sınırı ve yıldız eşikleri bulunan en kısa
 * çözüme göre belirlenir ve çözüm bölüm verisine kaydedilir (testler yeniden oynatarak doğrular).
 */
class LevelGenerator(private val cfg: GameConfig) {

    enum class Mode { MAIN, ENDLESS, DAILY, DEFENSE }

    private class Node(
        val id: String,
        var type: IslandType,
        var cell: Cell,
        var ports: Int = 0,
        var owner: Owner = Owner.NEUTRAL,
        var height: Int = 0,
        var fixed: Boolean = false,
        var hidden: Boolean = false,
        var frozen: Int = 0,
        var crumble: Int = -1,
        val inTree: Boolean = true,
    )

    private data class Blueprint(
        val levelId: Int,
        val name: String,
        val theme: String,
        val width: Int,
        val height: Int,
        val islands: Int,
        val decoys: Int,
        val scramble: Int,
        val features: Set<Feature>,
        val focus: List<Feature>,
        val objectives: List<ObjectiveType>,
        val shape: MapShape,
        val combat: Boolean,
        val enemyCount: Int,
        val enemyBonus: Int,
        val ai: AiBehavior,
        val bossTier: Int,
        val bossIndex: Int,
        val slack: Int,
        val difficulty: Int,
        val tutorial: String?,
        val special: String,
        val area: Int,
        val beamWidth: Int,
        val playerUnits: List<UnitType>,
        val castleHp: Int,
        val minSolution: Int = 1,
        val maxSolution: Int = 40,
    )

    data class BossTemplate(val name: String, val abilities: List<String>)

    companion object {
        val BOSSES = listOf(
            BossTemplate("Kaya Devriyesi", listOf("LOCK", "PUSH", "SUMMON")),
            BossTemplate("Demir Kale Komutanı", listOf("LOCK", "SUMMON", "BURN")),
            BossTemplate("Sis Cadısı", listOf("FOG", "TELEPORT", "FREEZE")),
            BossTemplate("Alev Muhafızı", listOf("BURN", "PUSH", "SUMMON")),
            BossTemplate("Buz Kralı", listOf("FREEZE", "LOCK", "BURN")),
            BossTemplate("Fırtına Ejderi", listOf("PUSH", "BURN", "FREEZE")),
            BossTemplate("Zaman Bekçisi", listOf("REWIND", "LOCK", "FREEZE")),
            BossTemplate("Portal Ustası", listOf("TELEPORT", "RESHAPE", "SUMMON")),
            BossTemplate("Karanlık Mimar", listOf("RESHAPE", "LOCK", "BURN")),
            BossTemplate("Kristal Hidra", listOf("FREEZE", "BURN", "SUMMON")),
            BossTemplate("Zehir Kraliçesi", listOf("FOG", "SUMMON", "PUSH")),
            BossTemplate("Volkan Titanı", listOf("BURN", "RESHAPE", "SUMMON")),
        )

        val TEACH: Map<Feature, ObjectiveType> = mapOf(
            Feature.SLIDE to CONNECT_ALL, Feature.ROTATE to CONNECT_ALL, Feature.BRIDGES to CONNECT_TARGET,
            Feature.FOG to RESCUE, Feature.FIRE_WATER to MAKE_COMBO, Feature.ICE to CONNECT_ALL,
            Feature.WIND to DELIVER, Feature.CRYSTAL_ENERGY to COLLECT_CRYSTALS, Feature.PORTAL to PORTAL_CHAIN,
            Feature.FORT_BATTLE to CAPTURE, Feature.SWAP to CONNECT_ALL, Feature.DEFENSIVE_AI to DEFEAT_ALL,
            Feature.JUMP to CONNECT_TARGET, Feature.GRAVITY to CONNECT_ALL, Feature.CRUMBLE to ESCAPE,
            Feature.TRADE to TRADE_ROUTE, Feature.TRAPPER_AI to PROTECT_CASTLE, Feature.POISON to TRAP_ENEMY,
            Feature.TIME to CONNECT_ALL, Feature.ELECTRIC to MAKE_COMBO, Feature.VOLCANO to MAKE_COMBO,
            Feature.RESOURCER_AI to SURVIVE, Feature.LIFE to PROTECT_CASTLE, Feature.STRATEGIST_AI to DEFEAT_ALL,
            Feature.ANCIENT to CONNECT_TARGET,
        )

        val VARIANT: Map<Feature, List<ObjectiveType>> = mapOf(
            Feature.SLIDE to listOf(GATHER, RESCUE), Feature.ROTATE to listOf(GATHER, CONNECT_TARGET),
            Feature.BRIDGES to listOf(CONNECT_ALL, RESCUE), Feature.FOG to listOf(CONNECT_ALL, COLLECT_CRYSTALS),
            Feature.FIRE_WATER to listOf(GATHER, CONNECT_ALL), Feature.ICE to listOf(RESCUE, MAKE_COMBO),
            Feature.WIND to listOf(CONNECT_ALL, DELIVER), Feature.CRYSTAL_ENERGY to listOf(CONNECT_ALL, GATHER),
            Feature.PORTAL to listOf(RESCUE, CONNECT_TARGET), Feature.FORT_BATTLE to listOf(DEFEAT_ALL, PROTECT_CASTLE),
            Feature.SWAP to listOf(RESCUE, TRADE_ROUTE), Feature.DEFENSIVE_AI to listOf(CAPTURE, DEFEAT_ALL),
            Feature.JUMP to listOf(CONNECT_ALL, DELIVER), Feature.GRAVITY to listOf(RESCUE, CONNECT_TARGET),
            Feature.CRUMBLE to listOf(ESCAPE, CONNECT_ALL), Feature.TRADE to listOf(TRADE_ROUTE, GATHER),
            Feature.TRAPPER_AI to listOf(TRAP_ENEMY, SURVIVE), Feature.POISON to listOf(DEFEAT_ALL, TRAP_ENEMY),
            Feature.TIME to listOf(RESCUE, COLLECT_CRYSTALS), Feature.ELECTRIC to listOf(DEFEAT_ALL, CONNECT_ALL),
            Feature.VOLCANO to listOf(GATHER, DEFEAT_ALL), Feature.RESOURCER_AI to listOf(CAPTURE, PROTECT_CASTLE),
            Feature.LIFE to listOf(DEFEAT_ALL, SURVIVE), Feature.STRATEGIST_AI to listOf(CAPTURE, PROTECT_CASTLE),
            Feature.ANCIENT to listOf(COLLECT_CRYSTALS, RESCUE),
        )

        private val COMBAT_OBJ = setOf(CAPTURE, DEFEAT_ALL, PROTECT_CASTLE, SURVIVE, TRAP_ENEMY)
        private val AI_FEATURES = mapOf(
            Feature.DEFENSIVE_AI to AiBehavior.DEFENSIVE, Feature.TRAPPER_AI to AiBehavior.TRAPPER,
            Feature.RESOURCER_AI to AiBehavior.RESOURCER, Feature.STRATEGIST_AI to AiBehavior.STRATEGIST,
        )
    }

    /** Teşhis: hangi satırda kaç aday reddedildi. */
    val rejections = java.util.concurrent.ConcurrentHashMap<Int, Int>()
    private fun reject(line: Int): LevelDef? { rejections.merge(line, 1, Int::plus); return null }

    // ------------------------------------------------------------------ Genel giriş noktaları

    fun generateMain(levelId: Int): LevelDef {
        val area = AreaCatalog.area(AreaCatalog.areaOfLevel(levelId))
        val k = (levelId - 1) % 5 + 1
        return generateWithRetries(levelId.toLong() * 7919 + 17, Mode.MAIN) { rnd, relax -> mainBlueprint(levelId, area, k, rnd, relax) }
    }

    fun generateEndless(stage: Int, runSeed: Long): LevelDef {
        return generateWithRetries(runSeed * 31 + stage, Mode.ENDLESS) { rnd, relax -> endlessBlueprint(stage, rnd, relax) }
    }

    fun generateDaily(dayKey: Long): LevelDef {
        return generateWithRetries(dayKey * 104729 + 3, Mode.DAILY) { rnd, relax -> dailyBlueprint(dayKey, rnd, relax) }
    }

    fun generateDefense(index: Int, garrison: List<UnitType>, castleHp: Int, progressLevel: Int): LevelDef {
        return generateWithRetries(index.toLong() * 6007 + 11, Mode.DEFENSE) { rnd, relax -> defenseBlueprint(index, garrison, castleHp, progressLevel, rnd, relax) }
    }

    private fun generateWithRetries(seed: Long, mode: Mode, bp: (Random, Int) -> Blueprint): LevelDef {
        for (attempt in 0 until 40) {
            val rnd = Random(seed + attempt * 1_000_003L)
            val relax = attempt / 4
            val blueprint = bp(rnd, relax)
            val lv = attempt(blueprint, rnd, seed + attempt)
            if (lv != null) return lv
        }
        // Son çare: aynı tema ve adla sade bir bağlantı bulmacası.
        for (k in 0 until 20) {
            val rnd = Random(seed * 13 + k)
            val b = bp(rnd, 10).copy(
                combat = false, objectives = listOf(CONNECT_ALL), features = setOf(Feature.SLIDE, Feature.ROTATE), focus = listOf(Feature.SLIDE),
                islands = 4, decoys = 0, scramble = 4, bossTier = 0, shape = MapShape.OPEN, minSolution = 1, maxSolution = 40, enemyCount = 0,
            )
            attempt(b, rnd, seed + k)?.let { return it }
        }
        error("Bölüm üretilemedi: $seed")
    }

    // ------------------------------------------------------------------ Tasarım planları

    private fun mainBlueprint(levelId: Int, area: AreaSpec, k: Int, rnd: Random, relax: Int): Blueprint {
        val L = levelId
        val unlocked = area.unlocked.toSet()
        val focus = area.focus
        val primary = focus.first()
        val earlier = (area.unlocked - focus.toSet()).filter { it != Feature.SLIDE && it != Feature.ROTATE }
        val prior = if (earlier.isEmpty()) null else earlier[rnd.nextInt(earlier.size)]
        val combatUnlocked = Feature.FORT_BATTLE in unlocked

        val objectives = ArrayList<ObjectiveType>()
        val levelFocus = ArrayList<Feature>()
        when (k) {
            1 -> { objectives.add(TEACH[primary]!!); levelFocus.add(primary) }
            2 -> { objectives.add(VARIANT[primary]!![0]); levelFocus.add(primary) }
            3 -> {
                val f = focus.getOrNull(1) ?: prior ?: primary
                objectives.add(VARIANT[f]!![rnd.nextInt(2)]); levelFocus.add(primary); levelFocus.add(f)
            }
            4 -> {
                objectives.add(VARIANT[primary]!![1])
                val f = prior ?: focus.last()
                val second = TEACH[f]!!
                if (second != objectives[0] && compatible(objectives[0], second)) objectives.add(second)
                levelFocus.add(primary); levelFocus.add(f)
            }
            else -> {
                levelFocus.addAll(focus); prior?.let { levelFocus.add(it) }
                if (area.bossTier > 0) objectives.add(DEFEAT_BOSS)
                else {
                    objectives.add(if (combatUnlocked && rnd.nextBoolean()) DEFEAT_ALL else RESCUE)
                    val extra = VARIANT[primary]!![rnd.nextInt(2)]
                    if (compatible(objectives[0], extra)) objectives.add(extra)
                }
            }
        }
        val objs = objectives.distinct().let { if (relax >= 6) it.take(1) else it }
        val combat = objs.any { it in COMBAT_OBJ } || (combatUnlocked && k >= 4 && objs.none { it == DELIVER } && rnd.nextInt(3) == 0)
        val ai = levelFocus.mapNotNull { AI_FEATURES[it] }.firstOrNull()
            ?: unlocked.mapNotNull { AI_FEATURES[it] }.let { if (it.isEmpty() || rnd.nextBoolean()) AiBehavior.AGGRESSIVE else it[rnd.nextInt(it.size)] }

        val diff = (L / 25.0 + k * 0.6 - relax * 0.8).coerceAtLeast(0.0)
        val w = (4 + (L / 90) + (if (k >= 4) 1 else 0)).coerceAtMost(6)
        val h = (6 + (L / 90) + (if (k >= 4) 1 else 0)).coerceAtMost(8)
        val islands = (3 + diff.toInt() / 2 + k / 2).coerceIn(3, 10)
        val features = (levelFocus + listOf(Feature.SLIDE) + (if (Feature.ROTATE in unlocked) listOf(Feature.ROTATE) else emptyList()))
            .toMutableSet()
        if (Feature.SWAP in unlocked && rnd.nextInt(3) == 0) features.add(Feature.SWAP)
        if (Feature.JUMP in unlocked && rnd.nextInt(3) == 0) features.add(Feature.JUMP)
        if (Feature.CRYSTAL_ENERGY in unlocked && rnd.nextInt(3) == 0) features.add(Feature.CRYSTAL_ENERGY)
        val tutorial = if (k == 1 && area.newFeature != null) area.newFeature.name else null
        val enemyCount = if (!combat) 0 else (1 + L / 90 + (if (k >= 4) 1 else 0) - relax / 2).coerceIn(1, 5)
        val units = playerUnitsFor(L, enemyCount, unlocked, rnd)
        return Blueprint(
            levelId = L, name = levelName(area, k), theme = area.theme, width = w, height = h,
            islands = (islands - relax / 2).coerceAtLeast(3), decoys = ((L / 70) + (if (k >= 3) 1 else 0) - relax / 2).coerceIn(0, 3),
            scramble = (2 + L / 14 + k - relax).coerceIn(2, 16),
            features = features, focus = levelFocus, objectives = objs, shape = if (L <= 10) MapShape.OPEN else area.shape,
            combat = combat, enemyCount = enemyCount, enemyBonus = L / 70, ai = ai,
            bossTier = if (k == 5) area.bossTier else 0, bossIndex = (area.index / 5 - 1).coerceAtLeast(0),
            slack = (5 - L / 120).coerceAtLeast(2) + relax, difficulty = (1 + L / 50 + k / 3).coerceAtMost(10),
            tutorial = tutorial, special = specialText(area, k, objs), area = area.index, beamWidth = 48,
            playerUnits = units, castleHp = 10 + L / 50,
            minSolution = (2 + L / 60 + k / 2 - relax).coerceIn(1, 8),
            maxSolution = (8 + L / 12 + k + relax * 2).coerceAtMost(30),
        )
    }

    private fun compatible(a: ObjectiveType, b: ObjectiveType): Boolean {
        val combatA = a in COMBAT_OBJ; val combatB = b in COMBAT_OBJ
        if (combatA && combatB) return false
        if (a == DELIVER || b == DELIVER) return false
        if (a == ESCAPE || b == ESCAPE) return false
        return true
    }

    private fun playerUnitsFor(L: Int, enemies: Int, unlocked: Set<Feature>, rnd: Random): List<UnitType> {
        if (enemies == 0) return emptyList()
        val pool = mutableListOf(UnitType.SWORD)
        if (L > 55) pool.add(UnitType.ARCHER)
        if (L > 70) pool.add(UnitType.SHIELD)
        if (L > 90 && Feature.POISON in unlocked) pool.add(UnitType.MAGE)
        if (L > 60 && Feature.FOG in unlocked) pool.add(UnitType.SCOUT)
        if (L > 110 && Feature.LIFE in unlocked) pool.add(UnitType.HEALER)
        if (L > 140) pool.add(UnitType.FLYER)
        if (L > 170) pool.add(UnitType.SIEGE)
        if (L > 200) pool.add(UnitType.GUARDIAN)
        if (L > 80) pool.add(UnitType.ENGINEER)
        val n = (enemies + 1).coerceIn(2, 5)
        return (0 until n).map { if (it == 0) UnitType.SWORD else pool[rnd.nextInt(pool.size)] }
    }

    private fun levelName(area: AreaSpec, k: Int): String {
        val parts = listOf("Giriş", "Keşif", "Birleşim", "Sınav", "Final")
        return "${area.name} ${parts[k - 1]}"
    }

    private fun specialText(area: AreaSpec, k: Int, objs: List<ObjectiveType>): String = when {
        k == 5 && area.bossTier == 2 -> "Dünya patronu! Mühür kristallerini kaleye bağla, kombolarla patronu zayıflat."
        k == 5 && area.bossTier == 1 -> "Büyük patron mücadelesi: patron her birkaç turda haritayı bozar."
        k == 5 -> "Alan finali: birden çok hedefi aynı anda tamamla."
        k == 1 && area.newFeature != null -> "Yeni mekanik: ${AreaCatalog.FEATURE_NAMES[area.newFeature]}"
        else -> ""
    }

    private fun endlessBlueprint(stage: Int, rnd: Random, relax: Int): Blueprint {
        val all = Feature.entries
        val f = listOf(all[2 + rnd.nextInt(all.size - 2)], all[2 + rnd.nextInt(all.size - 2)]).distinct()
        val unlocked = all.toSet()
        val objPool = f.flatMap { listOf(TEACH[it]!!) + VARIANT[it]!! }.filter { it != DEFEAT_BOSS }
        val obj = objPool[rnd.nextInt(objPool.size)]
        val combat = obj in COMBAT_OBJ
        val L = (stage * 12).coerceAtMost(450)
        val enemies = if (combat) (1 + stage / 6).coerceAtMost(4) else 0
        return Blueprint(
            levelId = 10_000 + stage, name = "Sonsuz Aşama $stage", theme = AreaCatalog.THEMES[stage % AreaCatalog.THEMES.size],
            width = (4 + stage / 8).coerceAtMost(6), height = (6 + stage / 8).coerceAtMost(8),
            islands = (3 + stage / 3 - relax / 2).coerceIn(3, 10), decoys = (stage / 5).coerceAtMost(3),
            scramble = (3 + stage / 2 - relax).coerceIn(2, 14), features = (f + Feature.SLIDE + Feature.ROTATE).toSet(), focus = f,
            objectives = listOf(obj), shape = MapShape.entries[stage % MapShape.entries.size], combat = combat,
            enemyCount = enemies, enemyBonus = stage / 8, ai = AiBehavior.entries[rnd.nextInt(AiBehavior.entries.size)],
            bossTier = 0, bossIndex = 0, slack = (4 - stage / 10).coerceAtLeast(1) + relax, difficulty = (1 + stage / 4).coerceAtMost(10),
            tutorial = null, special = "", area = 0, beamWidth = 24, playerUnits = playerUnitsFor(L, enemies, unlocked, rnd), castleHp = 10,
            minSolution = (2 + stage / 4 - relax).coerceIn(1, 8), maxSolution = 25 + relax * 2,
        )
    }

    private fun dailyBlueprint(day: Long, rnd: Random, relax: Int): Blueprint {
        val f = listOf(Feature.SLIDE, Feature.ROTATE, if (day % 2 == 0L) Feature.SWAP else Feature.BRIDGES)
        val obj = listOf(CONNECT_ALL, RESCUE, CONNECT_TARGET)[(day % 3).toInt().let { if (it < 0) -it else it }]
        return Blueprint(
            levelId = 20_000, name = "Günün Bulmacası", theme = AreaCatalog.THEMES[Math.floorMod(day, AreaCatalog.THEMES.size.toLong()).toInt()],
            width = 5, height = 7, islands = 6 - relax / 2, decoys = 1, scramble = (9 - relax).coerceAtLeast(3), features = f.toSet(), focus = f,
            objectives = listOf(obj), shape = MapShape.entries[Math.floorMod(day, 6L).toInt()], combat = false, enemyCount = 0, enemyBonus = 0,
            ai = AiBehavior.AGGRESSIVE, bossTier = 0, bossIndex = 0, slack = 8, difficulty = 4, tutorial = null,
            special = "Her gün aynı bulmaca: en az hamleyle çöz!", area = 0, beamWidth = 80, playerUnits = emptyList(), castleHp = 10,
            minSolution = (5 - relax).coerceAtLeast(1), maxSolution = 22 + relax * 2,
        )
    }

    private fun defenseBlueprint(index: Int, garrison: List<UnitType>, castleHp: Int, progress: Int, rnd: Random, relax: Int): Blueprint {
        val units = garrison.ifEmpty { listOf(UnitType.SWORD, UnitType.SWORD) }.take(4)
        val ai = listOf(AiBehavior.AGGRESSIVE, AiBehavior.STRATEGIST, AiBehavior.RESOURCER, AiBehavior.TRAPPER)[index % 4]
        return Blueprint(
            levelId = 30_000 + index, name = "Krallık Savunması ${index + 1}", theme = "IRON", width = 6, height = 8,
            islands = 6, decoys = 1, scramble = 2, features = setOf(Feature.SLIDE, Feature.ROTATE, Feature.FORT_BATTLE), focus = listOf(Feature.FORT_BATTLE),
            objectives = listOf(PROTECT_CASTLE), shape = MapShape.OPEN, combat = true,
            enemyCount = (2 + progress / 100 + index / 4 - relax).coerceIn(1, 5), enemyBonus = progress / 120, ai = ai,
            bossTier = 0, bossIndex = 0, slack = 2, difficulty = 5, tutorial = null, special = "Krallığına saldırı var! Kaleyi koru.",
            area = 0, beamWidth = 24, playerUnits = units, castleHp = castleHp,
        )
    }

    // ------------------------------------------------------------------ Gerçekleştirme

    private fun blockedCells(bp: Blueprint, rnd: Random): MutableSet<Cell> {
        val s = HashSet<Cell>()
        val w = bp.width; val h = bp.height
        when (bp.shape) {
            MapShape.OPEN -> {}
            MapShape.RING -> { s.add(Cell(w / 2, h / 2 - 1)); if (w > 4) s.add(Cell(w / 2 - 1, h / 2 - 1)) }
            MapShape.CROSS -> { s.add(Cell(0, h / 2)); s.add(Cell(w - 1, h / 2)); s.add(Cell(w / 2, 0)) }
            MapShape.SCATTER -> repeat(2 + rnd.nextInt(2)) { s.add(Cell(rnd.nextInt(w), rnd.nextInt(h - 2))) }
            MapShape.CORRIDOR -> { s.add(Cell(0, h / 2 - 1)); s.add(Cell(w - 1, h / 2 + 1)) }
            MapShape.SPLIT -> { for (x in 0 until w) if (x != 1 && x != w - 2) s.add(Cell(x, h / 2 - 1)) }
        }
        return s
    }

    private fun attempt(bp: Blueprint, rnd: Random, seed: Long): LevelDef? {
        val w = bp.width; val h = bp.height
        val blocked = blockedCells(bp, rnd)
        val nodes = ArrayList<Node>()
        val edges = ArrayList<Pair<Int, Int>>()
        val occupied = HashSet<Cell>()
        fun free(c: Cell) = c.x in 0 until w && c.y in 0 until h && c !in blocked && c !in occupied

        val castleCell = Cell(w / 2, h - 2)
        blocked.remove(castleCell)
        nodes.add(Node("castle", IslandType.CASTLE, castleCell, owner = Owner.PLAYER, fixed = true)); occupied.add(castleCell)

        fun grow(fromSet: List<Int>, count: Int, prefix: String): List<Int> {
            val added = ArrayList<Int>()
            var tries = 0
            while (added.size < count && tries++ < 400) {
                val pool = fromSet + added
                val base = if (rnd.nextInt(3) == 0) pool[rnd.nextInt(pool.size)] else pool[maxOf(0, pool.size - 1 - rnd.nextInt(minOf(3, pool.size)))]
                val d = Dir.entries[rnd.nextInt(4)]
                val c = nodes[base].cell + d
                if (!free(c)) continue
                // Kale en fazla 3 köprüye sahip olsun ki tek noktaya yığılma olmasın.
                if (nodes[base].type == IslandType.CASTLE && edges.count { it.first == base || it.second == base } >= 3) continue
                val n = Node("$prefix${nodes.size}", IslandType.FOREST, c)
                nodes.add(n); occupied.add(c)
                edges.add(base to nodes.size - 1)
                added.add(nodes.size - 1)
            }
            return added
        }

        val objs = bp.objectives
        val wantPortal = Feature.PORTAL in bp.features && (PORTAL_CHAIN in objs || rnd.nextInt(2) == 0)
        val mainCount = if (wantPortal) maxOf(2, bp.islands - 2) else bp.islands
        val main = grow(listOf(0), mainCount, "i")
        if (main.size < 2) return reject(358)
        var farCluster: List<Int> = emptyList()
        if (wantPortal) {
            // Kale kümesinde bir portal yaprağı ve uzakta ikinci küme.
            val leaf = main.last()
            nodes[leaf].type = IslandType.PORTAL
            val far = (0 until 40).map { Cell(rnd.nextInt(w), rnd.nextInt(maxOf(1, h / 2))) }
                .firstOrNull { c -> free(c) && nodes.all { it.cell.manhattan(c) >= 3 } } ?: return reject(365)
            nodes.add(Node("p${nodes.size}", IslandType.PORTAL, far)); occupied.add(far)
            val pIdx = nodes.size - 1
            farCluster = listOf(pIdx) + grow(listOf(pIdx), 1 + rnd.nextInt(2), "f")
        }
        val treeIdx = listOf(0) + main + farCluster

        // Bağlantı noktaları
        for ((a, b) in edges) {
            val d = Dir.entries.first { nodes[a].cell + it == nodes[b].cell }
            nodes[a].ports = nodes[a].ports or d.bit
            nodes[b].ports = nodes[b].ports or d.opposite.bit
        }
        if (Feature.ROTATE in bp.features) for (i in treeIdx.drop(1)) {
            if (rnd.nextInt(4) == 0) {
                val d = Dir.entries[rnd.nextInt(4)]
                if (free(nodes[i].cell + d)) nodes[i].ports = nodes[i].ports or d.bit
            }
        }

        // Türler
        val leaves = treeIdx.drop(1).filter { i -> edges.count { it.first == i || it.second == i } == 1 && nodes[i].type != IslandType.PORTAL }
        val inner = treeIdx.drop(1).filter { it !in leaves && nodes[it].type != IslandType.PORTAL }
        val assignable = (leaves + inner).toMutableList()
        fun takeLeaf(): Int? = (leaves.firstOrNull { it in assignable } ?: assignable.firstOrNull())?.also { assignable.remove(it) }
        fun take(): Int? = (if (assignable.isEmpty()) null else assignable[rnd.nextInt(assignable.size)])?.also { assignable.remove(it) }

        var targetId: String? = null
        var comboId: String? = null
        var deliver: Pair<String, Cell>? = null
        var gatherRes: Res? = null
        for (o in objs) when (o) {
            RESCUE -> repeat(1 + (bp.islands / 4).coerceAtMost(2)) { takeLeaf()?.let { nodes[it].type = IslandType.VILLAGE } }
            COLLECT_CRYSTALS -> repeat(2) { takeLeaf()?.let { nodes[it].type = IslandType.CRYSTAL } }
            CONNECT_TARGET -> takeLeaf()?.let { nodes[it].type = if (Feature.ANCIENT in bp.focus) IslandType.ANCIENT else IslandType.VILLAGE; targetId = nodes[it].id }
            ESCAPE -> takeLeaf()?.let { nodes[it].type = IslandType.EXIT; targetId = nodes[it].id }
            TRADE_ROUTE -> {
                take()?.let { nodes[it].type = IslandType.TRADE }
                take()?.let { nodes[it].type = IslandType.MINE }
                take()?.let { nodes[it].type = IslandType.FARM }
            }
            MAKE_COMBO -> {
                val pair = comboPair(bp)
                val e = edges.firstOrNull { (a, b) -> a != 0 && a in assignable && b in assignable } ?: return reject(408)
                nodes[e.first].type = pair.first; nodes[e.second].type = pair.second
                assignable.remove(e.first); assignable.remove(e.second)
                comboId = cfg.comboFor(pair.first, pair.second)!!.id
            }
            GATHER -> {
                gatherRes = listOf(Res.WOOD, Res.FOOD, Res.STONE)[rnd.nextInt(3)]
                val t = when (gatherRes) { Res.WOOD -> IslandType.FOREST; Res.FOOD -> IslandType.FARM; else -> IslandType.MINE }
                repeat(2) { take()?.let { nodes[it].type = t } }
            }
            else -> {}
        }
        // Özelliğe özgü türler
        fun put(t: IslandType) { take()?.let { nodes[it].type = t } }
        for (f in bp.focus) when (f) {
            Feature.FIRE_WATER -> if (comboId == null) { put(IslandType.WATER); put(IslandType.FIRE) }
            Feature.ICE -> { put(IslandType.ICE); put(IslandType.FIRE) }
            Feature.WIND -> put(IslandType.WIND)
            Feature.CRYSTAL_ENERGY -> put(IslandType.CRYSTAL)
            Feature.FORT_BATTLE -> put(IslandType.MILITARY)
            Feature.GRAVITY -> put(IslandType.GRAVITY)
            Feature.TRADE -> if (TRADE_ROUTE !in objs) put(IslandType.TRADE)
            Feature.POISON -> put(IslandType.POISON)
            Feature.TIME -> put(IslandType.TIME)
            Feature.ELECTRIC -> if (comboId == null) { put(IslandType.ELECTRIC); put(IslandType.WATER) }
            Feature.VOLCANO -> if (comboId == null) put(IslandType.VOLCANO)
            Feature.LIFE -> put(IslandType.LIFE)
            Feature.ANCIENT -> put(IslandType.ANCIENT)
            Feature.FOG -> put(IslandType.FOG)
            else -> {}
        }
        val filler = listOf(IslandType.FOREST, IslandType.FARM, IslandType.MINE, IslandType.FOREST, IslandType.WATER)
        for (i in assignable) nodes[i].type = filler[rnd.nextInt(filler.size)]
        // Yangın tuzağı istenmeyen durumlarda: ateşe bağlı ormanlar çözümde yanmasın diye su ile dengelenir.
        for ((a, b) in edges) {
            val ta = nodes[a].type; val tb = nodes[b].type
            if ((ta == IslandType.FIRE || ta == IslandType.VOLCANO) && tb == IslandType.FOREST && GATHER in objs) nodes[b].type = IslandType.MINE
            if ((tb == IslandType.FIRE || tb == IslandType.VOLCANO) && ta == IslandType.FOREST && GATHER in objs) nodes[a].type = IslandType.MINE
        }

        // Patron
        var boss: BossDef? = null
        val units = ArrayList<UnitDef>()
        var sealCount = 0
        if (bp.bossTier > 0 && DEFEAT_BOSS in objs) {
            val seals = (leaves.filter { nodes[it].type !in setOf(IslandType.VILLAGE, IslandType.EXIT) } + inner).distinct().take(2 + bp.bossTier)
            for (s in seals) nodes[s].type = IslandType.CRYSTAL
            sealCount = seals.size
            if (sealCount == 0) return reject(456)
            val fortCell = (0 until 60).map { Cell(rnd.nextInt(w), rnd.nextInt(2)) }.firstOrNull { c -> free(c) && nodes.all { it.cell.manhattan(c) >= 2 } } ?: return reject(457)
            nodes.add(Node("bossfort", IslandType.ENEMY_FORT, fortCell, ports = 15, owner = Owner.ENEMY, fixed = true, inTree = false)); occupied.add(fortCell)
            val t = BOSSES[bp.bossIndex % BOSSES.size]
            val phases = if (bp.bossTier >= 2) listOf(
                BossPhase(100, listOf(t.abilities[0])), BossPhase(66, listOf(t.abilities[0], t.abilities[1])),
                BossPhase(33, listOf(t.abilities[1], t.abilities[2])),
            ) else listOf(BossPhase(100, listOf(t.abilities[0])), BossPhase(50, listOf(t.abilities[0], t.abilities[1])))
            val sealDamage = 4
            boss = BossDef("boss", t.name, every = if (bp.bossTier >= 2) 2 else 3, phases = phases, sealDamage = sealDamage)
            val bossHp = sealDamage * sealCount
            val base = cfg.stats(UnitType.BOSS)
            units.add(UnitDef("boss", UnitType.BOSS.name, Owner.ENEMY.name, "bossfort", hpBonus = bossHp - base.hp, atkBonus = bp.bossTier - 1))
        }

        // Düşman üssü ve birlikler
        val objsCombat = objs.any { it in COMBAT_OBJ }
        var captureTarget: String? = null
        if (bp.combat && bp.enemyCount > 0) {
            val startOnPath = objs.any { it == SURVIVE || it == PROTECT_CASTLE || it == TRAP_ENEMY }
            // Üs, ağacın bir yaprağına bağlanır.
            val anchor = treeIdx.drop(1).sortedBy { -nodes[it].cell.manhattan(castleCell) }.firstOrNull { idx ->
                Dir.entries.any { free(nodes[idx].cell + it) }
            } ?: return reject(479)
            val d = Dir.entries.filter { free(nodes[anchor].cell + it) }.let { it[rnd.nextInt(it.size)] }
            val fc = nodes[anchor].cell + d
            val fortIdx = nodes.size
            nodes.add(Node("fort", IslandType.ENEMY_FORT, fc, owner = Owner.ENEMY, fixed = true, inTree = false)); occupied.add(fc)
            if (startOnPath || objsCombat) {
                nodes[anchor].ports = nodes[anchor].ports or d.bit
                nodes[fortIdx].ports = nodes[fortIdx].ports or d.opposite.bit
            }
            // Üssün çevresine ek bağlantı noktaları
            for (dd in Dir.entries) if (rnd.nextInt(2) == 0) nodes[fortIdx].ports = nodes[fortIdx].ports or dd.bit
            captureTarget = "fort"
            val enemyTypes = buildList {
                add(UnitType.SWORD)
                if (bp.levelId > 60) add(UnitType.ARCHER)
                if (bp.levelId > 100) add(UnitType.SHIELD)
                if (bp.levelId > 150) add(UnitType.MAGE)
                if (bp.levelId > 220) add(UnitType.SIEGE)
                if (bp.levelId > 260) add(UnitType.FLYER)
            }
            val enemySpots = mutableListOf(fortIdx)
            // Diğer düşmanlar üsse yakın ağaç adalarında.
            enemySpots.addAll(treeIdx.drop(1).filter { nodes[it].type != IslandType.VILLAGE }.sortedBy { nodes[it].cell.manhattan(fc) }.take(bp.enemyCount - 1))
            for ((n, spot) in enemySpots.take(bp.enemyCount).withIndex()) {
                val t = enemyTypes[rnd.nextInt(enemyTypes.size)]
                units.add(UnitDef("e$n", t.name, Owner.ENEMY.name, nodes[spot].id, hpBonus = bp.enemyBonus, atkBonus = bp.enemyBonus / 2))
            }
            // Oyuncu birlikleri kaleye yakın adalarda.
            val playerSpots = (listOf(0) + treeIdx.drop(1).filter { idx -> units.none { it.island == nodes[idx].id } }.sortedBy { nodes[it].cell.manhattan(castleCell) })
            for ((n, t) in bp.playerUnits.withIndex()) {
                val spot = playerSpots.getOrNull(n) ?: break
                units.add(UnitDef("u$n", t.name, Owner.PLAYER.name, nodes[spot].id))
            }
        }

        // Tuzak adalar (ağaç dışı)
        repeat(bp.decoys) {
            val c = (0 until 30).map { Cell(rnd.nextInt(w), rnd.nextInt(h)) }.firstOrNull { free(it) } ?: return@repeat
            val t = filler[rnd.nextInt(filler.size)]
            val n = Node("d${nodes.size}", t, c, ports = 1 shl rnd.nextInt(4) or (1 shl rnd.nextInt(4)), inTree = false)
            nodes.add(n); occupied.add(c)
        }

        // Teslim hedefi
        if (DELIVER in objs) {
            val cargo = assignableCargo(nodes, treeIdx, rnd) ?: return reject(524)
            deliver = nodes[cargo].id to nodes[cargo].cell
            if (Feature.WIND in bp.features) nodes[cargo].type = IslandType.WIND
        }

        // ------------------ Karıştırma
        val mechanics = mutableListOf(Mechanic.SLIDE)
        if (Feature.ROTATE in bp.features) mechanics.add(Mechanic.ROTATE)
        if (Feature.SWAP in bp.features) mechanics.add(Mechanic.SWAP)
        if (Feature.SWAP in bp.features || bp.levelId >= 66) mechanics.add(Mechanic.MERGE)
        if (Feature.CRYSTAL_ENERGY in bp.features) mechanics.add(Mechanic.RAISE)
        if (Feature.JUMP in bp.features) mechanics.add(Mechanic.JUMP)
        val hasUnits = units.any { it.owner == Owner.PLAYER.name }
        if (hasUnits) mechanics.add(Mechanic.UNIT_MOVE)
        if (hasUnits || GATHER in objs || bp.combat || bp.bossTier > 0) mechanics.add(Mechanic.WAIT)

        val solved = nodes.map { it.cell to it.ports }
        val movable = nodes.indices.filter { !nodes[it].fixed && nodes[it].owner != Owner.ENEMY }
        val forward = ArrayList<String>()
        var energyNeed = 0
        val lightScramble = objs.any { it == SURVIVE || it == PROTECT_CASTLE || it == TRAP_ENEMY }
        val steps = if (lightScramble) minOf(bp.scramble, 3) else bp.scramble
        var last: Pair<Int, Dir>? = null
        var guard = 0
        // Karıştırma yalnızca ağaç adalarını hareket ettirir; teslim adası mutlaka hareket eder.
        val scramblePool = movable.filter { nodes[it].inTree || deliver?.first == nodes[it].id }.ifEmpty { movable }
        val pathProtected: Set<Int> = if (lightScramble) pathToFort(nodes, edges) else emptySet()
        while (forward.size < steps && guard++ < steps * 30) {
            val i = scramblePool[rnd.nextInt(scramblePool.size)]
            if (i in pathProtected) continue
            val n = nodes[i]
            val roll = rnd.nextInt(10)
            if (roll < 2 && Mechanic.ROTATE in mechanics && n.ports != 15 && n.ports != 0) {
                n.ports = Ports.rotateCw(n.ports); forward.add("R:${n.id}"); continue
            }
            if (roll == 2 && Mechanic.RAISE in mechanics && n.height < cfg.rules.maxHeight && energyNeed < 3) {
                n.height++; energyNeed++; forward.add("H+:${n.id}"); continue
            }
            if (roll == 3 && Mechanic.JUMP in mechanics && energyNeed < 3) {
                val d = Dir.entries[rnd.nextInt(4)]
                val c = n.cell.step(d, 2)
                if (free(c)) { occupied.remove(n.cell); n.cell = c; occupied.add(c); energyNeed += 2; forward.add("J:${n.id}:${d.name}"); continue }
            }
            val dirs = Dir.entries.filter { free(n.cell + it) && !(last?.first == i && last?.second == it.opposite) }
            if (dirs.isEmpty()) continue
            val d = dirs[rnd.nextInt(dirs.size)]
            occupied.remove(n.cell); n.cell = n.cell + d; occupied.add(n.cell)
            forward.add("S:${n.id}:${d.name}")
            last = i to d
        }
        if (deliver != null && nodes.first { it.id == deliver!!.first }.cell == deliver!!.second) return reject(573)

        // Ters çözüm
        val inverse = ArrayList<String>()
        for (op in forward.asReversed()) {
            val p = op.split(':')
            when (p[0]) {
                "S" -> inverse.add("S:${p[1]}:${Dir.valueOf(p[2]).opposite.name}")
                "J" -> inverse.add("J:${p[1]}:${Dir.valueOf(p[2]).opposite.name}")
                "H+" -> inverse.add("H-:${p[1]}")
                "R" -> {}
            }
        }
        for ((idx, n) in nodes.withIndex()) {
            val target = solved[idx].second
            var cur = n.ports; var k = 0
            while (cur != target && k < 4) { cur = Ports.rotateCw(cur); k++ }
            repeat(k) { inverse.add("R:${n.id}") }
        }

        // Çevresel durumlar
        val features = ArrayList<CellFeature>()
        if (Feature.FOG in bp.features) {
            nodes.filter { it.inTree && it.type != IslandType.CASTLE && it.cell.manhattan(castleCell) > 2 }.shuffled(rnd).take(2).forEach { it.hidden = true }
        }
        if (Feature.ICE in bp.features) {
            val fire = nodes.any { it.type == IslandType.FIRE }
            if (fire) nodes.filter { it.inTree && !it.fixed && it.type != IslandType.FIRE && it.type != IslandType.CASTLE }.shuffled(rnd).take(1).forEach { it.frozen = 99 }
        }
        if (Feature.WIND in bp.features) {
            var added = 0
            for (t in 0 until 40) {
                if (added >= 2) break
                val c = Cell(rnd.nextInt(w), rnd.nextInt(h))
                if (c in blocked || c in occupied || solved.any { it.first == c } || (deliver != null && deliver!!.second == c)) continue
                features.add(CellFeature(c.x, c.y, "WIND", Dir.entries[rnd.nextInt(4)].name)); added++
            }
        }
        deliver?.let { features.add(CellFeature(it.second.x, it.second.y, "TARGET")) }

        val objectiveDefs = objs.map { o ->
            when (o) {
                GATHER -> ObjectiveDef(GATHER, amount = 0, res = gatherRes?.name ?: Res.WOOD.name)
                CAPTURE -> ObjectiveDef(CAPTURE, target = captureTarget)
                CONNECT_TARGET -> ObjectiveDef(CONNECT_TARGET, target = targetId)
                ESCAPE -> ObjectiveDef(ESCAPE, target = targetId)
                MAKE_COMBO -> ObjectiveDef(MAKE_COMBO, combo = comboId)
                DELIVER -> ObjectiveDef(DELIVER, target = deliver?.first, x = deliver?.second?.x ?: -1, y = deliver?.second?.y ?: -1)
                SURVIVE -> ObjectiveDef(SURVIVE, amount = 6 + bp.difficulty / 2)
                PROTECT_CASTLE -> ObjectiveDef(PROTECT_CASTLE, amount = 5 + bp.difficulty / 2)
                TRADE_ROUTE -> ObjectiveDef(TRADE_ROUTE, amount = 2)
                COLLECT_CRYSTALS -> ObjectiveDef(COLLECT_CRYSTALS)
                else -> ObjectiveDef(o)
            }
        }
        if (objectiveDefs.any { it.type == CAPTURE && it.target == null }) return reject(628)
        if (objectiveDefs.any { (it.type == CONNECT_TARGET || it.type == ESCAPE) && it.target == null }) return reject(629)
        if (objectiveDefs.any { it.type == MAKE_COMBO && it.combo == null }) return reject(630)

        val islandDefs = nodes.map {
            IslandDef(it.id, it.type.name, it.cell.x, it.cell.y, Ports.format(it.ports), it.owner.name, it.height, it.fixed, it.hidden, it.frozen, it.crumble)
        }
        var def = LevelDef(
            id = bp.levelId, area = bp.area, name = bp.name, width = w, height = h,
            moveLimit = 60, star3 = 1, star2 = 1, mechanics = mechanics.map { it.name },
            islands = islandDefs, units = units, blocked = blocked.filter { c -> nodes.none { it.cell == c } }.map { listOf(it.x, it.y) },
            features = features, objectives = objectiveDefs, ai = bp.ai.name, boss = boss, castleHp = bp.castleHp,
            startEnergy = energyNeed * cfg.rules.costs.raiseEnergy + (if (Mechanic.RAISE in mechanics) 1 else 0),
            tutorial = bp.tutorial, special = bp.special, difficulty = bp.difficulty, seed = seed, theme = bp.theme,
        )

        // Toplama hedefi miktarı: üretici sayısına göre.
        if (GATHER in objs) {
            val producers = nodes.count { it.inTree && it.type.producesRes?.name == gatherRes?.name }
            def = def.copy(objectives = def.objectives.map { if (it.type == GATHER) it.copy(amount = maxOf(3, producers * 3)) else it })
        }

        val start = Board.create(def, cfg)
        // Hiçbir hedef başlangıçta tamamlanmış olmamalı (aksi hâlde hedef anlamsızlaşır).
        if (def.objectives.any { Objectives.satisfied(start, it) }) return reject(0)
        if (Objectives.allSatisfied(start)) return reject(651)
        if (objs.any { it == SURVIVE || it == PROTECT_CASTLE || it == TRAP_ENEMY }) {
            // Düşmanın başlangıçta kaleye yol bulabilmesi gerekir; aksi hâlde savunma anlamsızdır.
            val g = Rules.grid(start)
            val enemy = start.units.firstOrNull { it.owner == Owner.ENEMY } ?: return reject(655)
            val from = start.islands.indexOfFirst { it.id == enemy.islandId }
            val d = Rules.hopDistances(start, g, from)
            if (Rules.castleIndices(start).all { d[it] == Int.MAX_VALUE }) return reject(658)
        }

        // Çözüm adayları
        var best: List<String>? = null
        val inv = compress(inverse)
        val rep = Solver.replay(def, cfg, inv)
        if (rep.status == Status.WON) best = inv.take(rep.movesUsed)
        val beamDepth = minOf(40, (best?.size ?: 30) + 2)
        val beam = Solver.beam(def, cfg, width = bp.beamWidth, maxDepth = beamDepth)
        if (beam != null && (best == null || beam.solution.size < best.size)) best = beam.solution
        if (best == null) return reject(669)
        if (best.size < bp.minSolution || best.size > bp.maxSolution) return reject(670)

        // Çökme sayaçları (kaçış)
        if (Feature.CRUMBLE in bp.features) {
            val len = best.size
            val crumbleTargets = nodes.filter { !it.fixed && it.type != IslandType.CASTLE && it.type != IslandType.EXIT && it.owner != Owner.ENEMY }
            val newIslands = def.islands.map { isl ->
                val n = crumbleTargets.firstOrNull { it.id == isl.id }
                if (n == null || rnd.nextInt(3) == 0) isl
                else isl.copy(crumble = if (n.inTree) len + bp.slack + 1 + rnd.nextInt(3) else (2 + rnd.nextInt(maxOf(1, len))))
            }
            val withCrumble = def.copy(islands = newIslands)
            if (Solver.replay(withCrumble, cfg, best).status == Status.WON) def = withCrumble
        }

        val final = Solver.replay(def, cfg, best)
        if (final.status != Status.WON) return reject(686)
        val hpMode = objs.any { it == SURVIVE || it == PROTECT_CASTLE }
        val len = final.movesUsed
        val limit = len + bp.slack
        val (s3, s2) = if (hpMode) {
            val pct = final.castleHp * 100 / maxOf(1, final.castleMaxHp)
            pct.coerceAtMost(100) to (pct / 2).coerceAtLeast(1)
        } else len to (len + maxOf(1, (bp.slack + 1) / 2))
        def = def.copy(moveLimit = limit, star3 = s3, star2 = s2, solution = best.take(len), rewards = rewardsFor(bp, def))
        // Sınır değişince çözüm hâlâ kazanmalı (savunma bölümlerinde tur sınırı = hedef tur sayısından az olamaz).
        if (hpMode) {
            val turns = def.objectives.filter { it.type == SURVIVE || it.type == PROTECT_CASTLE }.maxOf { it.amount }
            if (def.moveLimit < turns) def = def.copy(moveLimit = turns + 1)
        }
        if (Solver.replay(def, cfg, def.solution).status != Status.WON) return reject(700)
        return def
    }

    private fun assignableCargo(nodes: List<Node>, tree: List<Int>, rnd: Random): Int? {
        val c = tree.drop(1).filter { !nodes[it].fixed && nodes[it].type !in setOf(IslandType.VILLAGE, IslandType.EXIT, IslandType.PORTAL) }
        return if (c.isEmpty()) null else c[rnd.nextInt(c.size)]
    }

    private fun pathToFort(nodes: List<Node>, edges: List<Pair<Int, Int>>): Set<Int> {
        // Kale ile üs arasındaki ağaç yolu korunur (düşman saldırısının anlamlı olması için).
        val fortNeighbor = nodes.indexOfFirst { it.id == "fort" }.let { f ->
            if (f < 0) return emptySet()
            nodes.indices.firstOrNull { i -> nodes[i].inTree && nodes[i].cell.manhattan(nodes[f].cell) == 1 } ?: return emptySet()
        }
        val parent = HashMap<Int, Int>()
        val q = ArrayDeque<Int>(); q.add(0); parent[0] = -1
        while (q.isNotEmpty()) {
            val i = q.removeFirst()
            for ((a, b) in edges) {
                val j = if (a == i) b else if (b == i) a else continue
                if (j !in parent) { parent[j] = i; q.add(j) }
            }
        }
        val path = HashSet<Int>()
        var cur: Int? = fortNeighbor
        while (cur != null && cur >= 0) { path.add(cur); cur = parent[cur] }
        return path
    }

    private fun comboPair(bp: Blueprint): Pair<IslandType, IslandType> = when {
        Feature.VOLCANO in bp.focus -> IslandType.VOLCANO to IslandType.WATER
        Feature.ELECTRIC in bp.focus -> IslandType.ELECTRIC to IslandType.WATER
        Feature.ICE in bp.focus -> IslandType.WATER to IslandType.ICE
        Feature.WIND in bp.focus -> IslandType.WIND to IslandType.FIRE
        Feature.LIFE in bp.focus -> IslandType.LIFE to IslandType.WATER
        Feature.PORTAL in bp.focus -> IslandType.CRYSTAL to IslandType.PORTAL
        Feature.POISON in bp.focus -> IslandType.POISON to IslandType.WIND
        Feature.TIME in bp.focus -> IslandType.TIME to IslandType.MILITARY
        Feature.ANCIENT in bp.focus -> IslandType.ANCIENT to IslandType.CRYSTAL
        Feature.TRADE in bp.focus -> IslandType.TRADE to IslandType.FARM
        Feature.FOG in bp.focus -> IslandType.WIND to IslandType.FOG
        else -> IslandType.WATER to IslandType.FIRE
    }

    /** Ters çözümde arka arkaya gelen zıt kaydırmaları temizler. */
    private fun compress(ops: List<String>): List<String> {
        val out = ArrayList<String>()
        for (op in ops) {
            val last = out.lastOrNull()
            if (last != null && op.startsWith("S:") && last.startsWith("S:")) {
                val a = last.split(':'); val b = op.split(':')
                if (a[1] == b[1] && Dir.valueOf(a[2]).opposite == Dir.valueOf(b[2])) { out.removeAt(out.size - 1); continue }
            }
            out.add(op)
        }
        return out
    }

    private fun rewardsFor(bp: Blueprint, def: LevelDef): RewardDef {
        val types = def.islands.map { it.type }
        val base = 6 + bp.levelId / 10
        return RewardDef(
            wood = if ("FOREST" in types) base else 0,
            stone = if ("MINE" in types) base else base / 2,
            iron = if (bp.combat || "MINE" in types) base / 2 else 0,
            food = if ("FARM" in types) base else 0,
            crystal = if ("CRYSTAL" in types) 2 + bp.levelId / 40 else 0,
            ancientShard = if (bp.bossTier >= 1) bp.bossTier else if (bp.levelId % 5 == 0) 1 else 0,
            stardust = if (bp.bossTier >= 2) 3 else if (bp.bossTier == 1) 1 else 0,
            royalSeal = if (bp.bossTier >= 2) 1 else 0,
            collectionChancePct = when { bp.bossTier > 0 -> 100; bp.levelId % 5 == 0 -> 60; else -> 12 },
        )
    }
}
