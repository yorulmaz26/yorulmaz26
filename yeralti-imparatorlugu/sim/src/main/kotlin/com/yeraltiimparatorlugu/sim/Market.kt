package com.yeraltiimparatorlugu.sim

enum class MarketSlot(val title: String) {
    ANT_SKIN("Koloni görünümü"), QUEEN("Kraliçe görünümü"), ROOM_STYLE("Oda tasarımı"), PHEROMONE_FX("Feromon efekti"),
    DECOR("Dekorasyon"), WORKER_LOOK("İşçi görünümü"), BOOST("Araştırma desteği"), COLLECTIBLE("Koleksiyon öğesi"),
}

/**
 * Oyun içi market: yalnızca oyunda kazanılan jetonlarla çalışır (hedefler, başarımlar, seferler, zaferler).
 * Gerçek para yoktur; [PurchaseBackend] arayüzü Google Play Billing'in ileride eklenebilmesi için ayrılmıştır.
 */
enum class MarketItem(val title: String, val slot: MarketSlot, val price: Int, val description: String) {
    SKIN_RED("Kızıl Koloni", MarketSlot.ANT_SKIN, 20, "Karıncalar kızıl tonlarda."),
    SKIN_GOLD("Altın Koloni", MarketSlot.ANT_SKIN, 45, "Karıncalar altın sarısı parıltıda."),
    SKIN_FOREST("Orman Kolonisi", MarketSlot.ANT_SKIN, 30, "Yeşilimsi orman karıncaları."),
    QUEEN_FLOWER("Çiçek Taç", MarketSlot.QUEEN, 25, "Kraliçeye çiçekten taç."),
    QUEEN_CRYSTAL("Kristal Taç", MarketSlot.QUEEN, 50, "Turkuaz kristal taç."),
    ROOMS_PASTEL("Pastel Odalar", MarketSlot.ROOM_STYLE, 30, "Odalar yumuşak pastel tonlarda."),
    ROOMS_HONEY("Bal Peteği", MarketSlot.ROOM_STYLE, 40, "Sıcak bal tonlu oda duvarları."),
    FX_SPARKLE("Işıltılı Feromon", MarketSlot.PHEROMONE_FX, 20, "Feromon halkaları parlak ve kalın."),
    FX_RAINBOW("Gökkuşağı Feromon", MarketSlot.PHEROMONE_FX, 35, "Feromonlar gökkuşağı renklerinde döner."),
    DECOR_GARDEN("Çiçek Bahçesi", MarketSlot.DECOR, 15, "Yuva girişine çiçekler."),
    DECOR_LAMPS("Mantar Lambaları", MarketSlot.DECOR, 25, "Tünel girişinde ışıldayan mantarlar."),
    DECOR_STATUE("Kraliçe Heykeli", MarketSlot.DECOR, 40, "Yüzeyde taş kraliçe heykeli."),
    LOOK_STRIPES("Çizgili İşçiler", MarketSlot.WORKER_LOOK, 20, "İşçilerin karnında açık çizgiler."),
    BOOST_RESEARCH("Araştırma Hızlandırıcı", MarketSlot.BOOST, 12, "10 dakika boyunca araştırma +%50 (tekrar alınabilir)."),
    COLLECT_AMBER("Kehribar Parçası", MarketSlot.COLLECTIBLE, 30, "Koleksiyon öğesi: +15 itibar."),
    COLLECT_MAP("Antik Harita", MarketSlot.COLLECTIBLE, 35, "Yeni bir keşif bölgesini hemen açar."),
    ;

    val consumable: Boolean get() = slot == MarketSlot.BOOST
    val equippable: Boolean get() = slot != MarketSlot.BOOST && slot != MarketSlot.COLLECTIBLE
}

interface PurchaseBackend {
    /** Gerçek para ile satın alma bu sürümde yoktur. */
    val realMoneyAvailable: Boolean get() = false
}

object OfflinePurchaseBackend : PurchaseBackend

class MarketSystem {
    val owned = LinkedHashSet<MarketItem>()
    val equipped = HashMap<MarketSlot, MarketItem>()
    val decorations = LinkedHashSet<MarketItem>()

    fun buy(sim: Simulation, item: MarketItem): String {
        if (!item.consumable && item in owned) return "Zaten sahipsiniz"
        if (sim.coins < item.price) return "Yetersiz jeton (${item.price} gerekli)"
        sim.coins -= item.price
        when (item) {
            MarketItem.BOOST_RESEARCH -> sim.research.boostUntil = maxOf(sim.research.boostUntil, sim.time) + 600.0
            MarketItem.COLLECT_MAP -> sim.expeditions.regionsCompleted++
            else -> {}
        }
        if (!item.consumable) owned.add(item)
        if (item.equippable) equip(item)
        sim.addLog("Market: ${item.title} satın alındı.")
        return "${item.title} alındı"
    }

    fun equip(item: MarketItem) {
        if (item !in owned) return
        if (item.slot == MarketSlot.DECOR) { if (!decorations.add(item)) decorations.remove(item); return }
        if (equipped[item.slot] == item) equipped.remove(item.slot) else equipped[item.slot] = item
    }

    fun isEquipped(item: MarketItem) = if (item.slot == MarketSlot.DECOR) item in decorations else equipped[item.slot] == item
}

enum class SoundCue { CLICK, DIG, BUILD, BIRTH, ALERT, HIT, RESEARCH, DEATH, VICTORY }
