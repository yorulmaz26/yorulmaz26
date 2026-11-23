package com.yanlisrenk.logic.level

import com.yanlisrenk.logic.model.ColorCycle
import com.yanlisrenk.logic.model.GateData
import com.yanlisrenk.logic.model.LevelData
import com.yanlisrenk.logic.model.Motion
import com.yanlisrenk.logic.model.PadType
import com.yanlisrenk.logic.model.PickupType
import com.yanlisrenk.logic.model.ZoneType
import com.yanlisrenk.logic.model.ZoneData

/**
 * Bölüm doğrulayıcı. Bir bölümün insan tarafından çözülebilir olduğunu kanıtlar:
 *
 * - Karakterin bir kapıya varış anı konumdan belirlenir ([Timeline]).
 * - Oyuncu bir kapının gereksinimini ancak kapı görünür olduğunda (ekran, sis, gizli kapı,
 *   hafıza tabelası) öğrenebilir; öğrendikten sonra en az [reaction] sn tepki süresi ve
 *   her ek dokunuş için [TAP_GAP] sn gerekir.
 * - Durum (renk, son kapı rengi) üzerinde dinamik programlama yapılır; her durumda
 *   (toplanan altın, dokunuş) Pareto kümesi tutulur. Böylece hem çözülebilirlik hem de
 *   ★2 (altın) ve ★3 (dokunuş) hedeflerinin aynı anda ulaşılabilirliği kanıtlanır.
 */
object LevelValidator {
    /** Kapı ekranın üstüne girdiğinde karakterle arasındaki en küçük mesafe (dünya birimi). */
    const val VIEW_AHEAD = 11f
    const val TAP_GAP = 0.16f
    const val MARGIN = 0.06f
    /** Hareketli kapıda varış rengini tahmin etmek için ek düşünme süresi. */
    const val PREDICT_EXTRA = 0.3f
    const val FLICKER_EXTRA = 0.45f
    const val FLICKER_PERIOD = 0.9f

    enum class Kind(val order: Int) { ZONE_END(0), ZONE_START(1), PAD(2), PICKUP(3), GATE(4) }

    class Event(val y: Float, val kind: Kind, val ref: Int)

    data class Result(
        val ok: Boolean,
        val reason: String,
        /** Her kapıdan geçiş rengi (önerilen çözüm). */
        val gateColors: List<Int> = emptyList(),
        /** Çözümün dokunuş zamanları (sn). */
        val tapTimes: List<Float> = emptyList(),
        val minTaps: Int = 0,
        val maxCoins: Int = 0,
        /** Final Pareto cephesi: (altın, dokunuş). */
        val front: List<Pair<Int, Int>> = emptyList(),
        /** Seçilen çözümün altın/dokunuş değeri. */
        val chosenCoins: Int = 0,
        val chosenTaps: Int = 0,
    )

    private class Entry(
        val coins: Int,
        val taps: Int,
        val prevState: Int,
        val prevEntry: Int,
        val k: Int,
    )

    fun buildEvents(level: LevelData): List<Event> {
        val ev = ArrayList<Event>()
        level.gates.forEachIndexed { i, g -> ev.add(Event(g.y, Kind.GATE, i)) }
        level.pickups.forEachIndexed { i, p -> ev.add(Event(p.y, Kind.PICKUP, i)) }
        level.pads.forEachIndexed { i, p -> ev.add(Event(p.y, Kind.PAD, i)) }
        level.zones.forEachIndexed { i, z ->
            if (z.type == ZoneType.MIRROR || z.type == ZoneType.LOCK) {
                ev.add(Event(z.y1, Kind.ZONE_START, i))
                ev.add(Event(z.y2, Kind.ZONE_END, i))
            }
        }
        ev.sortWith(compareBy<Event>({ it.y }, { it.kind.order }))
        return ev
    }

    /** Kapının görüş mesafesi: ekran, sis ve gizlilik dikkate alınır. */
    fun visibleDistance(level: LevelData, gate: GateData): Float = visibleDistance(level.fogDist, level.zones, gate.y, gate.revealDist)

    /**
     * [y] konumundaki bir nesnenin oyuncuya görünür olduğu en büyük mesafe. Oyun ekranı da
     * ([com.yanlisrenk.logic.run.RunWorld.visibleDistanceAt]) aynı kuralı kullanır:
     * ekran (VIEW_AHEAD), genel görüş sınırı, sis bölgeleri ve gizli kapının açılma mesafesi.
     */
    fun visibleDistance(fogDist: Float, zones: List<ZoneData>, y: Float, revealDist: Float = 0f): Float {
        var d = VIEW_AHEAD
        if (fogDist > 0f) d = minOf(d, fogDist)
        for (z in zones) if (z.type == ZoneType.FOG && y >= z.y1 - VIEW_AHEAD && y < z.y2) d = minOf(d, z.value)
        if (revealDist > 0f) d = minOf(d, revealDist)
        return d
    }

    /** Kapı gereksiniminin öğrenilebildiği an. */
    fun knowTime(level: LevelData, tl: Timeline, gateIndex: Int): Float {
        val g = level.gates[gateIndex]
        var know = if (g.chain) {
            val sign = level.signs.firstOrNull { gateIndex in it.gates }
            // tabela da sis içinde kalabilir → tabelanın kendi görünürlüğü kullanılır
            if (sign != null) tl.timeAt(maxOf(0f, sign.y - visibleDistance(level.fogDist, level.zones, sign.y))) + 0.6f else tl.timeAt(g.y) // tabelasız zincir → çözülemez
        } else {
            tl.timeAt(maxOf(0f, g.y - visibleDistance(level, g)))
        }
        if (g.motion != Motion.STATIC) know += PREDICT_EXTRA
        if (g.flicker) know += FLICKER_EXTRA
        return know
    }

    fun validate(
        level: LevelData,
        reaction: Float = level.reaction,
        startColors: List<Int> = listOf(level.startColor),
        startLastGate: List<Int> = listOf(-1),
        coinTargetRequired: Int = -1,
    ): Result {
        if (level.gates.isEmpty()) return Result(false, "kapı yok")
        val tl = Timeline.build(level.speed, level.zones)
        val events = buildEvents(level)
        val n = events.size
        val times = FloatArray(n) { tl.timeAt(events[it].y) }
        val gateKnow = FloatArray(level.gates.size) { knowTime(level, tl, it) }

        // her olay için sıradaki kapı indeksi
        val nextGate = IntArray(n)
        var ng = -1
        for (j in n - 1 downTo 0) {
            if (events[j].kind == Kind.GATE) ng = events[j].ref
            nextGate[j] = ng
        }
        // pencere j (olay j-1 → olay j) için yön ve kilit maskesi
        val dirAt = IntArray(n)
        val maskAt = IntArray(n)
        var dir = 1
        var mask = 0
        for (j in 0 until n) {
            dirAt[j] = dir; maskAt[j] = mask
            val e = events[j]
            if (e.kind == Kind.ZONE_START || e.kind == Kind.ZONE_END) {
                val z = level.zones[e.ref]
                val start = e.kind == Kind.ZONE_START
                if (z.type == ZoneType.MIRROR) dir = if (start) -1 else 1
                if (z.type == ZoneType.LOCK) mask = if (start) z.lockMask else 0
            }
        }

        val states = 4 * 5 // renk × (sonKapı+1)
        fun sid(color: Int, last: Int) = color * 5 + (last + 1)

        var layer = Array(states) { ArrayList<Entry>() }
        for (c in startColors) for (l in startLastGate) layer[sid(c, l)].add(Entry(0, 0, -1, -1, 0))
        val history = ArrayList<Array<ArrayList<Entry>>>(n + 1)
        history.add(layer)

        // pencere j'de ilk dokunuşun yapılabileceği en erken an
        val winStart = FloatArray(n)
        run {
            var tp = 0f
            var after = 0.1f
            for (j in 0 until n) {
                val g = nextGate[j]
                winStart[j] = maxOf(tp + after, (if (g >= 0) gateKnow[g] else 0f) + reaction)
                val e = events[j]
                // ped ya da kilit renk değiştirir → oyuncunun yeniden tepki vermesi gerekir
                after = if (e.kind == Kind.PAD || (e.kind == Kind.ZONE_START && level.zones[e.ref].type != ZoneType.FOG)) reaction else 0.1f
                tp = times[j]
            }
        }
        var tPrev = 0f
        for (j in 0 until n) {
            val e = events[j]
            val te = times[j]
            val avail = te - MARGIN - winStart[j]
            val maxK = if (avail < 0f) 0 else minOf(3, (avail / TAP_GAP).toInt() + 1)
            val wDir = dirAt[j]
            val wMask = maskAt[j]
            val next = Array(states) { ArrayList<Entry>() }
            for (s in 0 until states) {
                val list = layer[s]
                if (list.isEmpty()) continue
                val color = s / 5
                val last = s % 5 - 1
                for (k in 0..maxK) {
                    var c = ColorCycle.stepN(color, k, wDir, wMask)
                    if (k > 0 && c == color) continue
                    var nl = last
                    var coinGain = 0
                    var alive = true
                    when (e.kind) {
                        Kind.GATE -> {
                            val gate = level.gates[e.ref]
                            if (!gate.accepts(c, te, last)) alive = false
                            nl = c
                        }
                        Kind.PICKUP -> {
                            val p = level.pickups[e.ref]
                            if (p.type == PickupType.COIN && (p.color < 0 || p.color == c)) coinGain = 1
                        }
                        Kind.PAD -> {
                            val p = level.pads[e.ref]
                            c = when (p.type) {
                                PadType.SHIFT -> com.yanlisrenk.logic.model.GameColor.norm(c + p.value)
                                PadType.SET -> p.value
                            }
                            c = ColorCycle.forceUnlocked(c, wMask)
                        }
                        Kind.ZONE_START -> {
                            val z = level.zones[e.ref]
                            if (z.type == ZoneType.LOCK) c = ColorCycle.forceUnlocked(c, z.lockMask)
                        }
                        Kind.ZONE_END -> {}
                    }
                    if (!alive) continue
                    val ns = sid(c, nl)
                    list.forEachIndexed { idx, en ->
                        insertPareto(next[ns], Entry(en.coins + coinGain, en.taps + k, s, idx, k))
                    }
                }
            }
            layer = next
            history.add(layer)
            tPrev = te
            if (layer.all { it.isEmpty() }) {
                val what = if (e.kind == Kind.GATE) "kapı ${e.ref}" else "olay $j"
                return Result(false, "$what geçilemiyor (y=${e.y})")
            }
        }

        // en iyi çözümü seç
        val front = ArrayList<Pair<Int, Int>>()
        var best: Pair<Int, Int>? = null // (state, entry)
        var maxCoins = 0
        var minTaps = Int.MAX_VALUE
        for (s in 0 until states) layer[s].forEach { en ->
            front.add(en.coins to en.taps)
            maxCoins = maxOf(maxCoins, en.coins)
            minTaps = minOf(minTaps, en.taps)
        }
        val target = if (coinTargetRequired >= 0) coinTargetRequired else maxCoins
        var bestTaps = Int.MAX_VALUE
        for (s in 0 until states) layer[s].forEachIndexed { idx, en ->
            if (en.coins >= target && en.taps < bestTaps) { bestTaps = en.taps; best = s to idx }
        }
        if (best == null) return Result(false, "altın hedefi ulaşılamaz")

        // geri izleme
        val gateColors = IntArray(level.gates.size)
        val kPerEvent = IntArray(n)
        var (s, idx) = best!!
        for (j in n downTo 1) {
            val en = history[j][s][idx]
            kPerEvent[j - 1] = en.k
            val ev = events[j - 1]
            if (ev.kind == Kind.GATE) gateColors[ev.ref] = s % 5 - 1
            s = en.prevState; idx = en.prevEntry
        }
        val taps = ArrayList<Float>()
        for (j in 0 until n) {
            val k = kPerEvent[j]
            for (i in 0 until k) taps.add(winStart[j] + i * TAP_GAP)
        }
        val chosen = history[n][best!!.first][best!!.second]
        return Result(
            ok = true, reason = "ok", gateColors = gateColors.toList(), tapTimes = taps,
            minTaps = minTaps, maxCoins = maxCoins, front = front,
            chosenCoins = chosen.coins, chosenTaps = chosen.taps,
        )
    }

    private fun insertPareto(list: ArrayList<Entry>, e: Entry) {
        for (o in list) if (o.coins >= e.coins && o.taps <= e.taps) return
        list.removeAll { e.coins >= it.coins && e.taps <= it.taps }
        list.add(e)
        if (list.size > 48) {
            list.sortWith(compareByDescending<Entry> { it.coins }.thenBy { it.taps })
            while (list.size > 48) list.removeAt(list.size - 1)
        }
    }
}
