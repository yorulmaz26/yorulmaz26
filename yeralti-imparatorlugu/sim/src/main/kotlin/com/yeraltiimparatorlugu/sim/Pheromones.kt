package com.yeraltiimparatorlugu.sim

/**
 * Oyuncunun yerleştirdiği feromon işaretleri. Güç zamanla azalır; aynı yere yeniden dokunmak güçlendirir.
 */
enum class PheromoneType(val title: String, val description: String, val radius: Int, val duration: Float) {
    FOOD("Yiyecek", "Bölgedeki kaynaklar öncelikli toplanır; uzak kaynaklar ancak bu işaretle fark edilir.", 8, 240f),
    DIG("Kazı", "Bölgedeki kazı işleri öne alınır.", 6, 180f),
    AVOID("Kaçınma", "Karıncalar bölgeden geçmekten kaçınır, buradaki işleri almaz.", 4, 240f),
    RALLY("Toplanma", "Boştaki karıncalar burada toplanır.", 3, 120f),
}

class PheromoneMarker(val id: Int, val type: PheromoneType, val x: Int, val y: Int) {
    var strength = 1f

    fun covers(cx: Int, cy: Int): Boolean {
        val dx = cx - x
        val dy = cy - y
        return dx * dx + dy * dy <= type.radius * type.radius
    }
}

class PheromoneField {
    val markers = ArrayList<PheromoneMarker>()

    /** Feromon süresi çarpanı (araştırmalarla artırılabilir). */
    var durationMultiplier = 1f

    fun update(dt: Float) {
        val it = markers.iterator()
        while (it.hasNext()) {
            val m = it.next()
            m.strength -= dt / (m.type.duration * durationMultiplier)
            if (m.strength <= 0f) it.remove()
        }
    }

    /** Yakında aynı türde işaret varsa güçlendirir, yoksa yenisini ekler. */
    fun place(id: Int, type: PheromoneType, x: Int, y: Int): PheromoneMarker {
        val existing = markers.firstOrNull { it.type == type && manhattan(it.x, it.y, x, y) <= 2 }
        if (existing != null) {
            existing.strength = 1f
            return existing
        }
        return PheromoneMarker(id, type, x, y).also { markers.add(it) }
    }

    fun remove(markerId: Int) = markers.removeAll { it.id == markerId }

    /** Hücredeki en güçlü etki (0..1). */
    fun strengthAt(type: PheromoneType, cx: Int, cy: Int): Float {
        var best = 0f
        for (m in markers) if (m.type == type && m.strength > best && m.covers(cx, cy)) best = m.strength
        return best
    }

    fun hasAvoid(): Boolean = markers.any { it.type == PheromoneType.AVOID }
}
