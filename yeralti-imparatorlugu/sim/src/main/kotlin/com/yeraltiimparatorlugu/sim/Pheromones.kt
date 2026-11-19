package com.yeraltiimparatorlugu.sim

/**
 * Oyuncunun yerleştirdiği 10 feromon türü. Güç zamanla azalır; aynı yere yeniden dokunmak güçlendirir.
 * [requires] araştırma kimliğidir (null: baştan açık).
 */
enum class PheromoneType(val title: String, val description: String, val radius: Int, val duration: Float, val requires: String? = null) {
    FOOD("Yiyecek", "Bölgedeki kaynaklar öncelikli toplanır; bilinen menzil dışındaki kaynaklar ancak bu işaretle fark edilir.", 8, 240f),
    DIG("Kazı", "Bölgedeki kazı işleri öne alınır.", 6, 180f),
    CARRY("Taşıma", "Bölgedeki yığınlar ve cesetler öncelikle taşınır.", 6, 180f),
    AVOID("Kaçınma", "Karıncalar bölgeden geçmekten kaçınır, buradaki işleri almaz.", 4, 240f),
    RALLY("Toplanma", "Boştaki karıncalar ve askerler burada toplanır.", 3, 120f),
    EXPLORE("Keşif", "Keşifçiler buraya gider ve çevreyi açığa çıkarır.", 6, 240f, "dig_survey"),
    DANGER("Tehlike", "İşçiler bölgeden uzak durur, askerler buraya koşar.", 6, 150f, "ph_alarm"),
    HELP("Acil yardım", "Şifacılar ve boştaki işçiler buradaki yaralılara yardım eder; iyileşme hızlanır.", 5, 150f, "ph_alarm"),
    ATTACK("Saldırı", "Askerler bölgedeki tüm düşmanlara saldırır.", 7, 150f, "mil_soldiers"),
    DEFENSE("Savunma", "Askerler bölgeyi korur ve buradan ayrılmaz.", 4, 240f, "mil_soldiers"),
}

class PheromoneMarker(val id: Int, val type: PheromoneType, val x: Int, val y: Int) {
    var strength = 1f
    var radiusBonus = 0

    val radius: Int get() = type.radius + radiusBonus

    fun covers(cx: Int, cy: Int): Boolean {
        val dx = cx - x
        val dy = cy - y
        return dx * dx + dy * dy <= radius * radius
    }
}

class PheromoneField {
    val markers = ArrayList<PheromoneMarker>()
    var durationMultiplier = 1f
    var radiusBonus = 0

    fun update(dt: Float) {
        val it = markers.iterator()
        while (it.hasNext()) {
            val m = it.next()
            m.radiusBonus = radiusBonus
            m.strength -= dt / (m.type.duration * durationMultiplier)
            if (m.strength <= 0f) it.remove()
        }
    }

    fun place(id: Int, type: PheromoneType, x: Int, y: Int): PheromoneMarker {
        val existing = markers.firstOrNull { it.type == type && manhattan(it.x, it.y, x, y) <= 2 }
        if (existing != null) { existing.strength = 1f; return existing }
        return PheromoneMarker(id, type, x, y).also { it.radiusBonus = radiusBonus; markers.add(it) }
    }

    fun remove(markerId: Int) = markers.removeAll { it.id == markerId }

    fun strengthAt(type: PheromoneType, cx: Int, cy: Int): Float {
        var best = 0f
        for (m in markers) if (m.type == type && m.strength > best && m.covers(cx, cy)) best = m.strength
        return best
    }

    fun nearest(type: PheromoneType, x: Int, y: Int): PheromoneMarker? =
        markers.filter { it.type == type }.minByOrNull { manhattan(it.x, it.y, x, y) }

    fun hasAvoid(): Boolean = markers.any { it.type == PheromoneType.AVOID || it.type == PheromoneType.DANGER }
}
