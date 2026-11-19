package com.yeraltiimparatorlugu.sim

class SideColony(val id: Int, val name: String, val x: Int) {
    var population = 6
    var food = 20f
    var shipmentTimer = 0f
    var totalSent = 0
}

/**
 * Koloni federasyonu: Kraliyet Merkezi ve kraliyet özüyle yeni bir kraliçe uzak bir noktada yan koloni kurar.
 * Yan koloniler soyut olarak büyür ve ana koloniye düzenli kaynak gönderir; ana koloni onlara destek yollayabilir.
 */
class FederationSystem {
    val colonies = ArrayList<SideColony>()
    private val names = listOf("Kuzey Yuvası", "Güney Yuvası", "Çınar Kolonisi", "Pınar Kolonisi", "Tepe Kolonisi", "Vadi Kolonisi")

    fun maxColonies(sim: Simulation) = sim.rooms.filter { it.type == RoomType.ROYAL_CENTER && it.functional }.sumOf { it.level }

    val foundCost = mapOf(ResourceType.ROYAL_ESSENCE to 2, ResourceType.FOOD to 40, ResourceType.SOIL to 20)

    fun found(sim: Simulation): String? {
        if (colonies.size >= maxColonies(sim)) return "Kraliyet Merkezi seviyesi kadar yan koloni kurulabilir"
        if (!sim.resources.hasAll(foundCost)) return "Yetersiz kaynak (2 kraliyet özü, 40 yiyecek, 20 toprak)"
        val team = sim.ants.filter { it.type.caste == Caste.WORKER }.take(6)
        if (sim.workerCount - team.size < 6) return "Kolonide en az 6 işçi kalmalı"
        sim.resources.pay(foundCost)
        team.forEach { sim.removeAntQuietly(it) }
        val side = if (colonies.size % 2 == 0) 1 else -1
        val c = SideColony(sim.newId(), names[colonies.size % names.size], side * (40 + colonies.size * 25))
        colonies.add(c)
        sim.addLog("Federasyon: ${c.name} kuruldu. Yeni kraliçe 6 işçiyle yola çıktı.")
        sim.notify("Yan koloni kuruldu: ${c.name}")
        return null
    }

    fun support(sim: Simulation, c: SideColony): String? {
        if (sim.resources.edibleTotal() < 20) return "20 yiyecek gerekli"
        var n = 20
        while (n > 0) { val t = sim.resources.bestEdible() ?: break; sim.resources.take(t, 1); n-- }
        c.food += 20f
        return null
    }

    fun update(sim: Simulation, dt: Float) {
        for (c in colonies) {
            c.food += c.population * 0.04f * dt * sim.environment.season(sim.day).sourceMult
            c.food -= c.population * 0.02f * dt
            if (c.food > c.population * 3f && c.population < 200 && sim.rng.nextFloat() < dt / 60f) { c.population++; c.food -= 5f }
            if (c.food < 0f) c.food = 0f
            c.shipmentTimer += dt
            if (c.shipmentTimer >= 180f) {
                c.shipmentTimer = 0f
                val surplus = ((c.food - c.population * 2f) * 0.5f).toInt()
                if (surplus > 0) {
                    c.food -= surplus
                    val got = sim.addCapped(ResourceType.FOOD, surplus)
                    val mats = sim.addCapped(ResourceType.LEAF, c.population / 3) + sim.addCapped(ResourceType.WOOD, c.population / 5)
                    c.totalSent += got + mats
                    if (got + mats > 0) sim.notify("${c.name} kaynak gönderdi: +$got yiyecek, +$mats malzeme")
                }
            }
        }
    }
}
