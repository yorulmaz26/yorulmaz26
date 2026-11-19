package com.emirzinciri.robotkomutan.logic.gen

import com.emirzinciri.robotkomutan.logic.command.CommandType
import com.emirzinciri.robotkomutan.logic.command.CommandType.*
import com.emirzinciri.robotkomutan.logic.content.Areas
import com.emirzinciri.robotkomutan.logic.grid.Dir
import com.emirzinciri.robotkomutan.logic.level.EnemyDef
import com.emirzinciri.robotkomutan.logic.level.EnemyType
import com.emirzinciri.robotkomutan.logic.level.LevelData
import com.emirzinciri.robotkomutan.logic.level.LevelDef
import com.emirzinciri.robotkomutan.logic.level.ObjectiveDef
import com.emirzinciri.robotkomutan.logic.level.ObjectiveType
import com.emirzinciri.robotkomutan.logic.level.RobotClass
import com.emirzinciri.robotkomutan.logic.level.RobotDef
import com.emirzinciri.robotkomutan.logic.level.StarRule
import com.emirzinciri.robotkomutan.logic.level.StarRuleType
import com.emirzinciri.robotkomutan.logic.sim.Outcome
import com.emirzinciri.robotkomutan.logic.sim.Simulation
import com.emirzinciri.robotkomutan.logic.sim.Solver
import java.util.Random

/** Kampanya bölümlerinde kullanılabilen mekanikler. */
enum class Feature(val hint: String) {
    BOXES("Kutuyu sarı hedefe it."),
    PITS("Çukura itilen kutu çukuru doldurur."),
    SWITCH("Anahtar paneli mavi kapıyı açar."),
    PLATE("Plaka, üstünde biri durdukça kapıyı açık tutar."),
    TIMER("Zamanlı düğme kapıyı birkaç adım açık tutar."),
    ENERGY("Enerji sınırlı: hücreleri topla."),
    CONVEYOR("Bantlar üstündekileri taşır."),
    LASER("Lazer ışınına girme; anahtar lazeri kapatır."),
    PATROL("Devriyeyi etkisiz bırak ya da atlat."),
    TURRET("Taretin hattında kalkan aç."),
    CHASER("Takipçi en yakın robota yürür."),
    LOOP("Tekrar eden hareketleri 'Tekrar et' bloğuyla kısalt."),
    FLYER("Uçan robot boşluk ve çukurların üstünden geçer ama plakaya basamaz."),
    GHOST("Görünmez robotu düşmanlar fark etmez."),
    MAGNET("Manyetik robot 'Çek' ile uzaktaki kutuyu kendine çeker."),
}

/**
 * 11–100. alanların bölümlerini üretir. Her alanın mekanik profili vardır; üretilen her bölüm çözücüyle çözülür,
 * çözüm alanın mekaniğini gerçekten kullanmalıdır. Döngü alanlarında çözüm Tekrar bloklarıyla sıkıştırılır.
 * Her 25. bölüm patron savaşıdır. Aynı alan numarası her zaman aynı bölümleri üretir.
 */
object Campaign {
    private val names = listOf("Tanışma", "Uygulama", "Birleşim", "Strateji", "Alan Finali")

    fun features(area: Int): Set<Feature> {
        val m = Areas.of(area).mechanic.lowercase()
        val f = LinkedHashSet<Feature>()
        when (area) {
            11 -> f += setOf(Feature.MAGNET, Feature.BOXES)
            12 -> f += setOf(Feature.FLYER, Feature.PITS)
            13, 14 -> f += setOf(Feature.LOOP)
            15, 16 -> f += setOf(Feature.PLATE)
            17 -> f += setOf(Feature.TURRET)
            18 -> f += setOf(Feature.GHOST, Feature.TURRET)
            19 -> f += setOf(Feature.TIMER)
        }
        if ("bant" in m || "akıntı" in m || "rüzgâr" in m) f += Feature.CONVEYOR
        if ("lazer" in m || "ışık" in m || "güvenlik" in m) f += Feature.LASER
        if ("enerji" in m || "güneş" in m || "oksijen" in m) f += Feature.ENERGY
        if ("kazı" in m || "çukur" in m || "köprü" in m || "su basan" in m) f += Feature.PITS
        if ("kalkan" in m || "savunma" in m || "taret" in m) f += Feature.TURRET
        if ("devriye" in m || "gizlilik" in m || "takım" in m || "düşman" in m) f += Feature.PATROL
        if ("zaman" in m || "gecikme" in m || "saat" in m || "periyodik" in m) f += Feature.TIMER
        if ("kilit" in m || "kapı" in m || "sinyal" in m || "yönlendirme" in m) f += Feature.SWITCH
        if ("taşıma" in m || "kutu" in m || "veri paket" in m || "ham madde" in m || "üretim" in m) f += Feature.BOXES
        if ("döngü" in m || "tekrar" in m || "hafıza" in m || "şablon" in m) f += Feature.LOOP
        if ("uçan" in m || "yerçekimi" in m || "süzülen" in m) f += Feature.FLYER
        if ("görünmez" in m || "gölge" in m || "sahte" in m) f += Feature.GHOST
        if ("manyetik" in m || "mıknatıs" in m) f += Feature.MAGNET
        if ("takip" in m || "arena" in m || "şampiyon" in m) f += Feature.CHASER
        if (f.isEmpty() || f.size == 1 && area > 20) {
            // Karışık alanlar: önceki mekaniklerin dönüşümlü birleşimi
            val pool = listOf(Feature.BOXES, Feature.SWITCH, Feature.PLATE, Feature.CONVEYOR, Feature.LASER, Feature.ENERGY, Feature.PATROL, Feature.TIMER, Feature.PITS, Feature.TURRET)
            f += pool[area % pool.size]
            if (area > 40) f += pool[(area * 3 + 1) % pool.size]
        }
        return f
    }

    data class AreaResult(val levels: List<LevelDef>, val attempts: Int)

    fun generateArea(area: Int): AreaResult {
        var total = 0
        val out = (1..5).map { i ->
            val id = (area - 1) * 5 + i
            val r = if (id % 25 == 0) boss(area, id) else level(area, id, i)
            total += r.second
            r.first
        }
        return AreaResult(out, total)
    }

    private fun robotsFor(area: Int, i: Int, feats: Set<Feature>): List<RobotClass> {
        val max = minOf(Areas.maxRobotsFor((area - 1) * 5 + i), 3)
        val commandArea = "komuta" in Areas.of(area).name.lowercase()
        val count = when {
            feats.contains(Feature.LOOP) -> 1
            i <= 2 -> 1
            i == 5 && commandArea -> minOf(3, max)
            else -> minOf(2, max)
        }
        val first = when {
            Feature.FLYER in feats -> RobotClass.FLYER
            Feature.GHOST in feats -> RobotClass.GHOST
            Feature.MAGNET in feats -> RobotClass.MAGNET
            Feature.TURRET in feats || Feature.PATROL in feats || Feature.CHASER in feats -> RobotClass.WARRIOR
            else -> RobotClass.BASIC
        }
        return List(count) { if (it == 0) first else RobotClass.BASIC }
    }

    private fun level(area: Int, id: Int, i: Int): Pair<LevelDef, Int> {
        val feats = features(area)
        var classes = robotsFor(area, i, feats)
        var spent = 0
        while (true) {
            val r = tryLevel(area, id, i, feats, classes)
            if (r != null) return r.first to r.second + spent
            spent += 160
            if (classes.size == 1) {
                // Son çare: en az kart eşiği düşürülür (mekanik kullanımı şartı korunur, sonra o da gevşer).
                for (ease in 1..3) tryLevel(area, id, i, feats, classes, ease)?.let { return it.first to it.second + spent }
                error("Bölüm $id üretilemedi")
            }
            classes = classes.dropLast(1) // robot sayısı üst sınırdır; üretilemezse bir azalt
        }
    }

    private fun tryLevel(area: Int, id: Int, i: Int, feats: Set<Feature>, classes: List<RobotClass>, ease: Int = 0): Pair<LevelDef, Int>? {
        val tier = ((area - 11) / 15).coerceIn(0, 5)
        val multi = classes.size > 1
        val minCards = (5 + tier + i / 2).coerceAtMost(11) + (if (multi) 2 else 0) - (if (classes.size >= 3) 2 else 0) - ease * 2
        val maxCards = (minCards + 8).coerceAtMost(22)
        val three = classes.size >= 3
        for (attempt in 0 until 160) {
            val rnd = Random(area * 1_000_003L + i * 10_007L + attempt + ease * 977L)
            val relax = attempt >= 100 || ease >= 3
            val def = draft(rnd, area, id, i, feats, classes, tier) ?: continue
            val level = LevelData(def)
            val sol = Solver(level, maxNodes = if (three) 150_000 else if (multi) 50_000 else 60_000).solve(maxCards) ?: continue
            if (sol.cards.size < minCards - (if (relax) 2 else 0)) continue
            val programs = if (sol.programs.isEmpty()) mapOf(def.robots.single().id to sol.cards) else sol.programs
            if (!relax && !usesFeatures(programs.values.flatten(), def, feats)) continue
            var final = programs
            if (Feature.LOOP in feats) final = programs.mapValues { (_, c) -> compress(c) }
            val o = Simulation.create(level, final).getOrNull()?.runToEnd()
            if (o !is Outcome.Win) { final = programs }
            val out = Simulation.create(level, final).getOrThrow().runToEnd() as Outcome.Win
            val par = out.stats.cards
            val third = when {
                def.energy -> StarRule(StarRuleType.ENERGY_USED, out.stats.energyUsed)
                multi -> StarRule(StarRuleType.TICKS, out.stats.ticks)
                else -> null
            }
            val loopTight = Feature.LOOP in feats && par < sol.cards.size
            val result = def.copy(par = par, maxCommands = if (loopTight) par + 2 else (par + 4).coerceAtMost(26), solution = final, third = third,
                difficulty = 1 + tier + i / 2)
            return result to attempt + 1
        }
        return null
    }

    /** Alanın mekaniği çözümde gerçekten kullanılıyor mu. */
    private fun usesFeatures(cards: List<String>, def: LevelDef, feats: Set<Feature>): Boolean {
        fun has(t: CommandType) = cards.any { it.startsWith(t.name) }
        for (f in feats) {
            val ok = when (f) {
                Feature.SWITCH, Feature.TIMER -> has(PRESS)
                Feature.BOXES -> has(PUSH) || has(PULL) || has(PICK)
                Feature.MAGNET -> has(PULL)
                Feature.ENERGY -> has(COLLECT)
                Feature.PATROL, Feature.CHASER -> has(ATTACK)
                Feature.TURRET -> Feature.GHOST in feats || has(SHIELD) || has(ATTACK)
                Feature.LASER -> has(PRESS) || has(PUSH)
                else -> true
            }
            if (!ok) return false
        }
        return true
    }

    private fun draft(rnd: Random, area: Int, id: Int, i: Int, feats: Set<Feature>, classes: List<RobotClass>, tier: Int): LevelDef? {
        val w = if (classes.size >= 3) 5 else 5 + (tier + i) / 3 + (if (classes.size > 1) 0 else 1)
        val h = if (classes.size > 1) 5 else 5 + (tier + i) / 4
        val g = Array(h) { CharArray(w) { '.' } }
        val values = HashMap<String, Int>()
        val channels = HashMap<String, Int>()
        val wallP = 0.12 + 0.02 * tier
        val voidP = if (Feature.FLYER in feats) 0.18 else 0.04
        for (y in 0 until h) for (x in 0 until w) {
            val r = rnd.nextDouble()
            g[y][x] = when { r < wallP -> '#'; r < wallP + voidP -> ' '; else -> '.' }
        }
        val used = HashSet<Pair<Int, Int>>()
        fun free(): Pair<Int, Int>? {
            repeat(300) {
                val x = rnd.nextInt(w); val y = rnd.nextInt(h)
                if (g[y][x] == '.' && (x to y) !in used) { used += x to y; return x to y }
            }
            return null
        }
        fun put(c: Char): Pair<Int, Int>? = free()?.also { (x, y) -> g[y][x] = c }
        fun key(p: Pair<Int, Int>) = "${p.first},${p.second}"
        val robots = classes.mapIndexed { idx, cls ->
            val (x, y) = free() ?: return null
            RobotDef(('A' + idx).toString(), x, y, Dir.entries[rnd.nextInt(4)], cls)
        }
        repeat(classes.size) {
            var placed = false
            repeat(80) {
                if (placed) return@repeat
                val c = free() ?: return null
                val far = robots.all { r -> kotlin.math.abs(r.x - c.first) + kotlin.math.abs(r.y - c.second) >= 3 }
                if (far) { g[c.second][c.first] = 'E'; placed = true } else used -= c
            }
            if (!placed) return null
        }
        val cmds = if (classes.size > 1) linkedSetOf(FORWARD, TURN_LEFT, TURN_RIGHT) else linkedSetOf(FORWARD, TURN_LEFT, TURN_RIGHT, BACK)
        val objectives = mutableListOf(ObjectiveDef(ObjectiveType.REACH_EXIT))
        val enemies = ArrayList<EnemyDef>()
        var energy = false
        var startEnergy = 10
        for (f in feats) when (f) {
            Feature.BOXES -> { put('B') ?: return null; put('x') ?: return null; objectives += ObjectiveDef(ObjectiveType.BOXES_ON_TARGETS); cmds += PUSH }
            Feature.MAGNET -> { if (Feature.BOXES !in feats) { put('B') ?: return null; put('x') ?: return null }; if (objectives.none { it.type == ObjectiveType.BOXES_ON_TARGETS }) objectives += ObjectiveDef(ObjectiveType.BOXES_ON_TARGETS); cmds += PULL }
            Feature.PITS -> { put('o') ?: return null; if (Feature.FLYER !in feats) { put('B') ?: return null; cmds += PUSH } }
            Feature.SWITCH -> { put('s') ?: return null; put('D') ?: return null; cmds += PRESS }
            Feature.PLATE -> { val p = put('p') ?: return null; val d = put('D') ?: return null; channels[key(p)] = 2; channels[key(d)] = 2; cmds += WAIT }
            Feature.TIMER -> { val t = put('t') ?: return null; val d = put('D') ?: return null; channels[key(t)] = 3; channels[key(d)] = 3; values[key(t)] = 3 + rnd.nextInt(3); cmds += PRESS; cmds += WAIT }
            Feature.ENERGY -> { put('c') ?: return null; if (rnd.nextBoolean()) put('c'); energy = true; startEnergy = (w + h) / 2 + rnd.nextInt(3); cmds += COLLECT }
            Feature.CONVEYOR -> {
                val (sx, sy) = free() ?: return null
                val d = Dir.entries[rnd.nextInt(4)]
                val ch = when (d) { Dir.N -> '^'; Dir.E -> '>'; Dir.S -> 'v'; Dir.W -> '<' }
                var x = sx; var y = sy
                repeat(3 + rnd.nextInt(2)) { if (x in 0 until w && y in 0 until h && g[y][x] == '.') { g[y][x] = ch; used += x to y }; x += d.dx; y += d.dy }
                cmds += WAIT
            }
            Feature.LASER -> {
                val l = put('L') ?: return null
                values[key(l)] = rnd.nextInt(4); channels[key(l)] = 4
                val s = put('s') ?: return null; channels[key(s)] = 4
                cmds += PRESS
            }
            Feature.PATROL -> { val p = free() ?: return null; enemies += EnemyDef("P", EnemyType.PATROL, p.first, p.second, Dir.entries[rnd.nextInt(4)], hp = 1 + tier / 3); cmds += ATTACK }
            Feature.TURRET -> { val p = free() ?: return null; enemies += EnemyDef("T", EnemyType.TURRET, p.first, p.second, Dir.entries[rnd.nextInt(4)], hp = 2, range = 4); cmds += SHIELD; cmds += ATTACK }
            Feature.CHASER -> { val p = free() ?: return null; enemies += EnemyDef("C", EnemyType.CHASER, p.first, p.second, hp = 2); cmds += ATTACK }
            Feature.LOOP -> cmds += REPEAT
            Feature.FLYER, Feature.GHOST -> Unit
        }
        if (Feature.LOOP in feats) cmds += END
        if (classes.size > 1) cmds += WAIT
        val area0 = Areas.of(area)
        val feature = feats.joinToString(" ") { it.hint }
        return LevelDef(
            id = id, area = area, name = names[i - 1],
            goalText = objectives.joinToString(" · ") { it.type.title },
            hint = feature,
            story = if (i == 1) "${area0.name}: ${area0.mechanic.lowercase()}. $feature" else null,
            map = g.map { String(it) },
            robots = robots.map { it.copy(energy = startEnergy) },
            commands = cmds.filter { it != END }.sortedBy { it.ordinal },
            maxCommands = 22, par = 1, objectives = objectives, energy = energy,
            channels = channels, values = values, enemies = enemies, maxTicks = 80,
        )
    }

    /** Patron bölümü: arena, kapıyı tutan kalkan robotu ve savaş robotu (L50 tasarımının büyüyen sürümü). */
    private fun boss(area: Int, id: Int): Pair<LevelDef, Int> {
        val tier = id / 25
        for (attempt in 0 until 60) {
            val rnd = Random(id * 7_777L + attempt)
            val hp = 8 + minOf(tier / 4, 3) + rnd.nextInt(2)
            val arenaW = 5 + rnd.nextInt(2)
            val map = ArrayList<String>()
            map += "#".repeat(arenaW + 2)
            for (y in 0 until 3) {
                val row = StringBuilder("#")
                repeat(arenaW) { x -> row.append(if (rnd.nextInt(9) == 0 && x in 1 until arenaW - 1 && y != 1) '#' else '.') }
                row.append('#')
                map += row.toString()
            }
            map[2] = "." + "D" + map[2].substring(2)
            val plateX = 2 + rnd.nextInt(arenaW - 2)
            map += "#".repeat(plateX) + "p" + "#".repeat(arenaW + 1 - plateX)
            map += "#".repeat(plateX) + "." + "#".repeat(arenaW + 1 - plateX)
            val turrets = if (tier >= 6 && rnd.nextBoolean()) listOf(EnemyDef("T", EnemyType.TURRET, arenaW, 1, Dir.W, hp = 1, range = 3)) else emptyList()
            val def = LevelDef(
                id = id, area = area, name = "Patron: " + bossName(id),
                goalText = "Patronu yen!", hint = "Kalkan robotu plakada durdukça arena kapısı açık kalır. Patron önündekine 2 hasar verir; canı yarıya inince saldırganlaşır.",
                story = "${Areas.of(area).name}: bölgenin hâkimi seni bekliyor.",
                map = map, robots = listOf(RobotDef("A", 0, 2, Dir.E, RobotClass.WARRIOR), RobotDef("B", plateX, 5, Dir.N, RobotClass.SHIELD)),
                enemies = listOf(EnemyDef("Z", EnemyType.BOSS, arenaW - 1 + rnd.nextInt(2), 2, Dir.entries[rnd.nextInt(4)], hp = hp)) + turrets,
                commands = listOf(FORWARD, TURN_LEFT, TURN_RIGHT, WAIT, ATTACK, SHIELD), maxCommands = 22, par = 1,
                objectives = listOf(ObjectiveDef(ObjectiveType.DEFEAT_ENEMIES)), maxTicks = 60, difficulty = 3 + tier,
            )
            val level = LevelData(def)
            val sol = Solver(level, maxNodes = 400_000).solve(24) ?: continue
            val out = Simulation.create(level, sol.programs).getOrThrow().runToEnd() as Outcome.Win
            return def.copy(par = out.stats.cards, maxCommands = (out.stats.cards + 4).coerceAtMost(24), solution = sol.programs,
                third = StarRule(StarRuleType.TICKS, out.stats.ticks)) to attempt + 1
        }
        error("Patron bölümü $id üretilemedi")
    }

    private fun bossName(id: Int) = when (id) {
        75 -> "Ağ Bekçisi"; 100 -> "Merkezi Çekirdek Muhafızı"; 125 -> "Demir Muhafız"; 150 -> "Manyetik Dev"
        175 -> "Kopya Lordu"; 200 -> "Enerji Yutucu"; 225 -> "Pres Ustası"; 250 -> "Komut Korsanı"; 275 -> "Yörünge Kaptanı"
        300 -> "Manyetik Dev II"; 325 -> "Akıntı Lordu"; 350 -> "Zaman Makinesi"; 375 -> "Gölge Usta"; 400 -> "Arena Şampiyonu"
        425 -> "Şifre Muhafızı"; 450 -> "Sistem Bekçisi"; 475 -> "Kaos Prensi"; 500 -> "Merkezi Yapay Zekâ"; else -> "İsyan Komutanı"
    }

    /**
     * Döngüsüz kart dizisini Tekrar bloklarıyla sıkıştırır (açgözlü: en çok kart kazandıran ardışık tekrar).
     * Çalışma biçimi aynıdır; kart sayısı azalır.
     */
    fun compress(cards: List<String>): List<String> {
        val out = ArrayList<String>()
        var i = 0
        while (i < cards.size) {
            var best: Triple<Int, Int, Int>? = null // (uzunluk, tekrar, kazanç)
            for (len in 1..6) {
                if (i + len * 2 > cards.size) break
                var k = 1
                while (i + (k + 1) * len <= cards.size && (0 until len).all { cards[i + it] == cards[i + k * len + it] }) k++
                k = k.coerceAtMost(9)
                if (k >= 2) {
                    val gain = len * k - (len + 1)
                    if (gain > 0 && (best == null || gain > best.third)) best = Triple(len, k, gain)
                }
            }
            if (best == null) { out += cards[i]; i++ } else {
                val (len, k, _) = best
                out += "REPEAT:$k"; out += cards.subList(i, i + len); out += "END"
                i += len * k
            }
        }
        return out
    }
}
