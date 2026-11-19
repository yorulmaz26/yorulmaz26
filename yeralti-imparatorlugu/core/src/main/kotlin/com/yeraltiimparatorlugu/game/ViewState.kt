package com.yeraltiimparatorlugu.game

import com.yeraltiimparatorlugu.sim.OrderKind
import com.yeraltiimparatorlugu.sim.PheromoneType
import com.yeraltiimparatorlugu.sim.RoomType

enum class ToolMode(val title: String, val hint: String, val inToolbar: Boolean = true) {
    VIEW("Gez", "Dokunarak hücre, oda, karınca veya canlı seçin. Sürükleyerek kaydırın, iki parmakla yakınlaştırın."),
    DIG("Kaz", "Kazılacak hücrelere dokunun veya parmağınızı sürükleyin. Kaydırmak için iki parmak kullanın."),
    CANCEL("Sil", "Kazı ve dolgu işaretlerini kaldırmak için dokunun veya sürükleyin."),
    FILL("Doldur", "Tüneli kapatmak için hücrelere dokunun: işçiler toprakla doldurur (hücre başına 2 toprak)."),
    BUILD("İnşa", "Odayı yerleştirmek için dokunun, sonra Onayla'ya basın."),
    PHEROMONE("Feromon", "Seçili feromonu bırakmak için dokunun. Aynı yere tekrar dokunmak güçlendirir."),
    ORDER_POINT("Emir noktası", "Birliğin gideceği noktaya dokunun.", false),
    TARGET("Hedef", "Saldırılacak düşmana dokunun.", false),
}

/** Arayüz ile dünya çizici arasında paylaşılan görünüm durumu. */
class ViewState {
    var mode = ToolMode.VIEW
    var speed = 1
    var selectedCell: Pair<Int, Int>? = null
    var ghostType: RoomType? = null
    var ghostX: Int? = null
    var ghostY: Int? = null
    var pheromone = PheromoneType.FOOD
    var pendingSquad = 0
    var pendingOrder = OrderKind.GUARD

    fun clearGhost() { ghostType = null; ghostX = null; ghostY = null }
}
