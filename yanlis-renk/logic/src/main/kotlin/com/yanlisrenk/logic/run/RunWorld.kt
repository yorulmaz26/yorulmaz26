package com.yanlisrenk.logic.run

import com.yanlisrenk.logic.level.LevelValidator
import com.yanlisrenk.logic.level.LevelValidator.Event
import com.yanlisrenk.logic.level.LevelValidator.Kind
import com.yanlisrenk.logic.level.Timeline
import com.yanlisrenk.logic.model.ColorCycle
import com.yanlisrenk.logic.model.GameColor
import com.yanlisrenk.logic.model.GateData
import com.yanlisrenk.logic.model.LevelData
import com.yanlisrenk.logic.model.PadData
import com.yanlisrenk.logic.model.PadType
import com.yanlisrenk.logic.model.PickupData
import com.yanlisrenk.logic.model.PickupType
import com.yanlisrenk.logic.model.SignData
import com.yanlisrenk.logic.model.ZoneData
import com.yanlisrenk.logic.model.ZoneType

/** Oyun alanı: bölüm ya da sonsuz moddaki eklenebilir parçalar. */
class Track(baseSpeed: Float) {
    val gates = ArrayList<GateData>()
    val pickups = ArrayList<PickupData>()
    val zones = ArrayList<ZoneData>()
    val pads = ArrayList<PadData>()
    val signs = ArrayList<SignData>()
    val events = ArrayList<Event>()
    val timeline = Timeline().also { it.setSpeedFrom(0f, baseSpeed) }
    var length = Float.MAX_VALUE
    var fogDist = 0f

    /** Parçayı [offsetY] konumuna ekler. Parça kendi yerel koordinatında doğrulanmış olmalı. */
    fun append(chunk: LevelData, offsetY: Float) {
        val t0 = timeline.timeAt(offsetY)
        timeline.setSpeedFrom(offsetY, chunk.speed)
        chunk.zones.filter { it.type == ZoneType.SPEED }.sortedBy { it.y1 }.forEach {
            timeline.setSpeedFrom(offsetY + it.y1, chunk.speed * it.value)
            timeline.setSpeedFrom(offsetY + it.y2, chunk.speed)
        }
        val gBase = gates.size
        val pBase = pickups.size
        val dBase = pads.size
        val zBase = zones.size
        chunk.gates.forEach { g ->
            val ph = if (g.segDur > 0f) g.phase - t0 / g.segDur else g.phase
            gates.add(g.copy(y = g.y + offsetY, phase = ph))
        }
        chunk.pickups.forEach { pickups.add(it.copy(y = it.y + offsetY)) }
        chunk.pads.forEach { pads.add(it.copy(y = it.y + offsetY)) }
        chunk.zones.forEach { zones.add(it.copy(y1 = it.y1 + offsetY, y2 = it.y2 + offsetY)) }
        chunk.signs.forEach { s -> signs.add(SignData(s.y + offsetY, s.gates.map { it + gBase })) }
        val local = LevelValidator.buildEvents(chunk)
        for (e in local) {
            val ref = when (e.kind) {
                Kind.GATE -> e.ref + gBase
                Kind.PICKUP -> e.ref + pBase
                Kind.PAD -> e.ref + dBase
                Kind.ZONE_START, Kind.ZONE_END -> e.ref + zBase
            }
            events.add(Event(e.y + offsetY, e.kind, ref))
        }
    }

    companion object {
        fun fromLevel(level: LevelData): Track {
            val tr = Track(level.speed)
            tr.append(level, 0f)
            tr.length = level.length
            tr.fogDist = level.fogDist
            return tr
        }
    }
}

sealed class RunEvent {
    data class Tap(val color: Int) : RunEvent()
    data class GatePassed(val gate: Int, val ok: Boolean, val color: Int, val shielded: Boolean = false) : RunEvent()
    data class Coin(val index: Int, val collected: Boolean) : RunEvent()
    data class Diamond(val index: Int) : RunEvent()
    data class Shield(val index: Int) : RunEvent()
    data class Pad(val index: Int, val color: Int) : RunEvent()
    data class ZoneEnter(val zone: Int) : RunEvent()
    data class ZoneExit(val zone: Int) : RunEvent()
    data object Failed : RunEvent()
    data object Finished : RunEvent()
}

/**
 * Bir koşunun belirlenimci simülasyonu. Doğrulayıcıyla aynı kuralları uygular.
 * Görselleştirme katmanı yalnızca durumu okur ve [drainEvents] ile efekt üretir.
 */
class RunWorld(val track: Track, startColor: Int, val endless: Boolean = false) {
    var time = 0f; private set
    var y = 0f; private set
    var color = startColor; private set
    var dir = 1; private set
    var lockMask = 0; private set
    var lastGateColor = -1; private set
    var taps = 0; private set
    var coins = 0; private set
    var coinsMissed = 0; private set
    var diamonds = 0; private set
    var gatesPassed = 0; private set
    var combo = 0; private set
    var maxCombo = 0; private set
    var score = 0; private set
    var shields = 0
    var failed = false; private set
    var finished = false; private set
    var mistakes = 0; private set
    /** Sonsuz modda son geçilen kapı sayısına göre çarpan. */
    val multiplier: Int get() = minOf(5, 1 + combo / 10)

    private var nextEvent = 0
    private val pending = ArrayList<RunEvent>()
    private val collected = HashSet<Int>()

    val pickupTaken: Set<Int> get() = collected
    val activeFog: Float
        get() {
            var d = if (track.fogDist > 0f) track.fogDist else 0f
            for (z in track.zones) if (z.type == ZoneType.FOG && y >= z.y1 - 2f && y < z.y2) d = if (d == 0f) z.value else minOf(d, z.value)
            return d
        }

    fun tap() {
        if (failed || finished) return
        val c = ColorCycle.step(color, dir, lockMask)
        if (c != color) {
            color = c
            taps++
            pending.add(RunEvent.Tap(color))
        }
    }

    /** Simülasyonu [dt] kadar ilerletir. */
    fun update(dt: Float) {
        if (failed || finished) return
        time += dt
        val newY = track.timeline.yAt(time)
        while (nextEvent < track.events.size && track.events[nextEvent].y <= newY) {
            process(track.events[nextEvent])
            nextEvent++
            if (failed) { y = newY; return }
        }
        y = newY
        if (!endless && y >= track.length) {
            finished = true
            pending.add(RunEvent.Finished)
        }
    }

    private fun process(e: Event) {
        when (e.kind) {
            Kind.GATE -> {
                val g = track.gates[e.ref]
                val te = track.timeline.timeAt(g.y)
                val ok = g.accepts(color, te, lastGateColor)
                if (ok) {
                    gatesPassed++
                    combo++
                    maxCombo = maxOf(maxCombo, combo)
                    score += 10 * multiplier
                    pending.add(RunEvent.GatePassed(e.ref, true, color))
                } else if (shields > 0) {
                    shields--
                    mistakes++
                    combo = 0
                    gatesPassed++
                    pending.add(RunEvent.GatePassed(e.ref, false, color, shielded = true))
                } else {
                    mistakes++
                    failed = true
                    pending.add(RunEvent.GatePassed(e.ref, false, color))
                    pending.add(RunEvent.Failed)
                }
                lastGateColor = color
            }
            Kind.PICKUP -> {
                val p = track.pickups[e.ref]
                when (p.type) {
                    PickupType.COIN -> {
                        val ok = p.color < 0 || p.color == color
                        if (ok) { coins++; collected.add(e.ref); score += 5 } else coinsMissed++
                        pending.add(RunEvent.Coin(e.ref, ok))
                    }
                    PickupType.DIAMOND -> { diamonds++; collected.add(e.ref); pending.add(RunEvent.Diamond(e.ref)) }
                    PickupType.SHIELD -> { shields++; collected.add(e.ref); pending.add(RunEvent.Shield(e.ref)) }
                }
            }
            Kind.PAD -> {
                val p = track.pads[e.ref]
                var c = when (p.type) {
                    PadType.SHIFT -> GameColor.norm(color + p.value)
                    PadType.SET -> p.value
                }
                c = ColorCycle.forceUnlocked(c, lockMask)
                color = c
                pending.add(RunEvent.Pad(e.ref, color))
            }
            Kind.ZONE_START -> {
                val z = track.zones[e.ref]
                if (z.type == ZoneType.MIRROR) dir = -1
                if (z.type == ZoneType.LOCK) { lockMask = z.lockMask; color = ColorCycle.forceUnlocked(color, lockMask) }
                pending.add(RunEvent.ZoneEnter(e.ref))
            }
            Kind.ZONE_END -> {
                val z = track.zones[e.ref]
                if (z.type == ZoneType.MIRROR) dir = 1
                if (z.type == ZoneType.LOCK) lockMask = 0
                pending.add(RunEvent.ZoneExit(e.ref))
            }
        }
    }

    fun drainEvents(out: MutableList<RunEvent>) {
        out.addAll(pending)
        pending.clear()
    }

    /** Bölüm yıldızı: ★ bitirme, ★ altın hedefi, ★ dokunuş sınırı. */
    fun stars(level: LevelData): Int {
        if (!finished) return 0
        var s = 1
        if (coins >= level.coinTarget) s++
        if (taps <= level.tapPar) s++
        return s
    }
}
