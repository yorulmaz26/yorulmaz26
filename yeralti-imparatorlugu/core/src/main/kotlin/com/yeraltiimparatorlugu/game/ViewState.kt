package com.yeraltiimparatorlugu.game

import com.yeraltiimparatorlugu.sim.PheromoneType
import com.yeraltiimparatorlugu.sim.RoomType

enum class ToolMode(val title: String, val hint: String) {
    VIEW("Gez", "Dokunarak hücre, oda veya karınca seçin. Sürükleyerek kaydırın, iki parmakla yakınlaştırın."),
    DIG("Kaz", "Kazılacak hücrelere dokunun veya parmağınızı sürükleyin. Kaydırmak için iki parmak kullanın."),
    CANCEL("Sil", "Kazı işaretlerini kaldırmak için dokunun veya sürükleyin."),
    BUILD("İnşa", "Odayı yerleştirmek için dokunun, sonra Onayla'ya basın."),
    PHEROMONE("Feromon", "Seçili feromonu bırakmak için dokunun. Aynı yere tekrar dokunmak güçlendirir."),
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

    fun clearGhost() { ghostType = null; ghostX = null; ghostY = null }
}
