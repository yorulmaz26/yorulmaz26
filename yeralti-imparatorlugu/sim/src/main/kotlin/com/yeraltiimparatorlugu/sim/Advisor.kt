package com.yeraltiimparatorlugu.sim

enum class Difficulty(val title: String, val description: String, val hungerMult: Float, val raidMult: Float, val graceDays: Int, val collapseMult: Float) {
    RELAXED("Rahat", "Karıncalar daha az acıkır, baskınlar küçük ve geç gelir, tüneller nadiren çöker.", 0.7f, 0.6f, 6, 0.4f),
    NORMAL("Normal", "Dengeli deneyim.", 1f, 1f, 3, 1f),
    HARD("Zor", "Kıt kaynak, sık ve büyük baskınlar.", 1.2f, 1.4f, 2, 1.3f),
}

enum class AdviceLevel { INFO, WARN, DANGER }

sealed class AdviceAction {
    data class Build(val room: RoomType) : AdviceAction()
    data class Research(val id: String) : AdviceAction()
    data class Open(val screen: String) : AdviceAction()
    data class Focus(val x: Int, val y: Int) : AdviceAction()
    object Dig : AdviceAction()
}

class Advice(val id: String, val level: AdviceLevel, val title: String, val text: String, val action: AdviceAction?, val actionLabel: String? = null)

/**
 * Danışman: koloninin durumuna bakıp en önemli 1–5 öneriyi üretir. Her öneri tek dokunuşla uygulanabilir
 * (oda kur, araştır, ekranı aç, kameraya göster). Oyunu karmaşık sistemleri bilmeden oynanabilir kılar.
 */
object Advisor {
    fun advice(sim: Simulation): List<Advice> {
        val out = ArrayList<Advice>()
        val food = sim.resources.edibleTotal()
        val foodCap = sim.capacityOf(StorageKind.FOOD)
        val mat = sim.resources.totalOf(StorageKind.MATERIAL)
        val matCap = sim.capacityOf(StorageKind.MATERIAL)
        fun has(t: RoomType) = sim.rooms.any { it.type == t }
        fun canBuild(t: RoomType) = sim.roomUnlocked(t) && !sim.rooms.any { it.type == t && !it.functional }

        val threats = sim.creatures.filter { it.kind.hostile && it.cellY >= 0 }
        if (threats.isNotEmpty()) {
            if (sim.militaryCount == 0) out.add(Advice("threat", AdviceLevel.DANGER, "Kolonide düşman var!", "İşçiler kaçıyor. Asker yoksa tüneli Doldur aracıyla kapatın veya Savunma önceliğini yükseltin.", AdviceAction.Focus(threats[0].cellX, threats[0].cellY), "Göster"))
            else out.add(Advice("threat", AdviceLevel.DANGER, "Kolonide ${threats.size} düşman!", "Askerlere saldırı emri verin.", AdviceAction.Open("military"), "Ordu"))
        }
        if (food < maxOf(6f, sim.foodConsumptionPerMinute() * 2f)) {
            out.add(if (!has(RoomType.FUNGUS_FARM) && canBuild(RoomType.FUNGUS_FARM))
                Advice("food", AdviceLevel.DANGER, "Yiyecek azalıyor", "Mantar Çiftliği yaprakları sürekli yiyeceğe çevirir.", AdviceAction.Build(RoomType.FUNGUS_FARM), "Çiftlik kur")
            else Advice("food", AdviceLevel.DANGER, "Yiyecek azalıyor", "Toplama görevini en yüksek önceliğe alın ve yüzeye Yiyecek işareti bırakın.", AdviceAction.Open("tasks"), "Öncelikler"))
        }
        if (sim.layBlocker()?.startsWith("Barınak") == true && canBuild(RoomType.REST_ROOM))
            out.add(Advice("housing", AdviceLevel.WARN, "Barınak dolu", "Kraliçe yeni yumurta bırakamıyor. Dinlenme Odası +10 yer açar.", AdviceAction.Build(RoomType.REST_ROOM), "Oda kur"))
        if (sim.layBlocker()?.startsWith("Yumurta") == true && canBuild(RoomType.EGG_ROOM))
            out.add(Advice("eggs", AdviceLevel.WARN, "Yumurta yeri yok", "Yumurta Odası kraliçenin yanına kurulursa yumurtalar daha hızlı gelişir.", AdviceAction.Build(RoomType.EGG_ROOM), "Oda kur"))
        if (mat >= matCap * 0.85f && canBuild(RoomType.MATERIAL_STORAGE))
            out.add(Advice("mat", AdviceLevel.WARN, "Malzeme deposu dolu", "Yeni Malzeme Deposu yaprak ve odun için yer açar.", AdviceAction.Build(RoomType.MATERIAL_STORAGE), "Depo kur"))
        if (food >= foodCap * 0.9f && canBuild(RoomType.FOOD_STORAGE))
            out.add(Advice("foodcap", AdviceLevel.INFO, "Besin deposu dolu", "Toplanan yiyecek boşa gidiyor; yeni Besin Deposu kurun.", AdviceAction.Build(RoomType.FOOD_STORAGE), "Depo kur"))
        if (sim.environment.water.size > 12)
            out.add(Advice("flood", AdviceLevel.WARN, "Tüneller su altında", "Odaların altına derin bir kuyu kazın; su oraya akar ve zemin emer.", AdviceAction.Dig, "Kaz"))
        if (sim.day >= sim.difficulty.graceDays - 1 && sim.militaryCount == 0 && sim.isUnlocked("cb_castes") && !sim.isUnlocked("mil_soldiers") && sim.research.current == null)
            out.add(Advice("soldiers", AdviceLevel.WARN, "Savunmaya hazırlanın", "Rakipler yakında baskın yapabilir. Asker Yetiştirme araştırmasını başlatın.", AdviceAction.Research("mil_soldiers"), "Araştır"))
        if (!has(RoomType.RESEARCH_LAB) && sim.day >= 2 && canBuild(RoomType.RESEARCH_LAB))
            out.add(Advice("lab", AdviceLevel.INFO, "Araştırmaya başlayın", "Araştırma Laboratuvarı yeni odalar, karınca sınıfları ve yetenekler açar.", AdviceAction.Build(RoomType.RESEARCH_LAB), "Lab kur"))
        if (sim.rooms.any { it.type == RoomType.RESEARCH_LAB && it.functional } && sim.research.current == null) {
            val next = ResearchTree.all.firstOrNull { sim.research.available(it) && sim.resources.hasAll(it.cost) }
            if (next != null) out.add(Advice("research", AdviceLevel.INFO, "Laboratuvar boş", "Önerilen: ${next.title} — ${next.description}", AdviceAction.Research(next.id), "Başlat"))
        }
        if (sim.ants.count { it.sick } >= 2 && !has(RoomType.INFIRMARY))
            out.add(if (sim.isUnlocked("cb_medicine")) Advice("sick", AdviceLevel.WARN, "Hasta karıncalar", "Sağlık Odası hastaları iyileştirir.", AdviceAction.Build(RoomType.INFIRMARY), "Oda kur")
            else Advice("sick", AdviceLevel.WARN, "Hasta karıncalar", "Şifa Bilgisi araştırması Sağlık Odasını açar.", AdviceAction.Research("cb_medicine"), "Araştır"))
        if (sim.rooms.any { !it.maintained })
            out.add(Advice("maint", AdviceLevel.WARN, "Odalar bakımsız", "Bakım için toprak malzemesi gerekiyor; birkaç tünel kazın.", AdviceAction.Dig, "Kaz"))
        val idle = sim.workers.count { it.activity == AntActivity.IDLE }
        if (sim.workerCount > 0 && idle * 2 > sim.workerCount && sim.digOrders.isEmpty() && out.size < 3)
            out.add(Advice("idle", AdviceLevel.INFO, "İşçiler boşta", "Yeni tüneller kazdırın: toprak, mineral ve yeni bölgeler bulursunuz.", AdviceAction.Dig, "Kaz"))
        if (sim.evolution.points(sim.reputation) > 0) out.add(Advice("evo", AdviceLevel.INFO, "Evrim puanı var", "Koloninizi bir yönde geliştirin.", AdviceAction.Open("evolution"), "Evrim"))
        if (sim.resources[ResourceType.FOSSIL] > 0 && sim.museum.capacity(sim) > 0) out.add(Advice("fossil", AdviceLevel.INFO, "İncelenecek fosil var", "Fosil Müzesinde kataloğa ekleyin.", AdviceAction.Open("museum"), "Müze"))
        return out.sortedBy { it.level.ordinal * -1 }.take(5)
    }
}
