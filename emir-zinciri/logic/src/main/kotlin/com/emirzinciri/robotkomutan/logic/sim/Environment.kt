package com.emirzinciri.robotkomutan.logic.sim

import com.emirzinciri.robotkomutan.logic.grid.Pos
import com.emirzinciri.robotkomutan.logic.level.LevelData
import com.emirzinciri.robotkomutan.logic.level.Tile

/** Çevre kuralları: kanallar ve lazer ışınları. Simülasyon ve çizici aynı hesabı kullanır. */
object Environment {

    /** Kanal etkin mi: anahtar, zamanlayıcı, dolu soket veya plaka (VE-kanallarında bütün plakalar). */
    fun channelActive(level: LevelData, s: WorldState, ch: Int): Boolean {
        if (ch in s.toggled || (s.timers[ch] ?: 0) > 0) return true
        for (p in level.sockets) if (level.channel(p) == ch && (s.socketCharge[p] ?: 0) >= level.socketNeed(p)) return true
        val plates = level.plates.filter { level.channel(it) == ch }
        if (plates.isEmpty()) return false
        val pressed = plates.count { p -> p in s.boxes || s.robotAt(p) != null }
        return if (ch in level.andChannels) pressed == plates.size else pressed > 0
    }

    /** Işını geçiren hücre mi (duvar, panel, kapalı kapı ve kutu ışını keser). */
    private fun passes(level: LevelData, s: WorldState, p: Pos): Boolean {
        val t = level.tile(p)
        if (!level.inBounds(p)) return false
        if (t == Tile.WALL || t.isConsole) return false
        if (t.isDoor && p !in s.openDoors) return false
        if (p in s.boxes) return false
        return true
    }

    /** Etkin lazerlerin kapladığı hücreler. Kanalı etkin olan lazer kapalıdır. */
    fun beams(level: LevelData, s: WorldState): Map<Pos, List<Pos>> {
        if (level.lasers.isEmpty()) return emptyMap()
        val out = LinkedHashMap<Pos, List<Pos>>()
        for (l in level.lasers) {
            if (channelActive(level, s, level.channel(l))) continue
            val d = level.laserDir(l)
            val cells = ArrayList<Pos>()
            var p = l + d
            while (passes(level, s, p)) { cells += p; p += d }
            out[l] = cells
        }
        return out
    }
}
