package com.yanlisrenk.logic.level

import com.yanlisrenk.logic.model.ZoneData
import com.yanlisrenk.logic.model.ZoneType

/**
 * Parçalı doğrusal konum–zaman eşlemesi. Karakter hızı yalnızca konuma bağlıdır
 * (oyuncu hızı değiştiremez), böylece her kapıya varış anı belirlenimcidir.
 * Hem doğrulayıcı hem de oyun simülasyonu aynı çizelgeyi kullanır.
 */
class Timeline {
    private val ys = ArrayList<Float>()   // parça başlangıç konumları
    private val ts = ArrayList<Float>()   // parça başlangıç zamanları
    private val vs = ArrayList<Float>()   // parça hızları

    init {
        ys.add(0f); ts.add(0f); vs.add(1f)
    }

    /** [y] konumundan itibaren hızı [speed] yapar. y artan sırada verilmeli. */
    fun setSpeedFrom(y: Float, speed: Float) {
        require(speed > 0f)
        val last = ys.size - 1
        if (y <= ys[last] + 1e-4f) {
            // aynı noktada hız değişimi → son parçanın hızını güncelle
            if (last == 0 || y <= ys[last] + 1e-4f) { vs[last] = speed; return }
        }
        val t = ts[last] + (y - ys[last]) / vs[last]
        ys.add(y); ts.add(t); vs.add(speed)
    }

    fun timeAt(y: Float): Float {
        val i = indexForY(y)
        return ts[i] + (y - ys[i]) / vs[i]
    }

    fun yAt(t: Float): Float {
        var lo = 0
        var hi = ts.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (ts[mid] <= t) lo = mid else hi = mid - 1
        }
        return ys[lo] + (t - ts[lo]) * vs[lo]
    }

    fun speedAt(y: Float): Float = vs[indexForY(y)]

    private fun indexForY(y: Float): Int {
        var lo = 0
        var hi = ys.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (ys[mid] <= y) lo = mid else hi = mid - 1
        }
        return lo
    }

    companion object {
        /** Temel hız ve hız bölgelerinden çizelge oluşturur. */
        fun build(baseSpeed: Float, zones: List<ZoneData>): Timeline {
            val tl = Timeline()
            tl.setSpeedFrom(0f, baseSpeed)
            val speedZones = zones.filter { it.type == ZoneType.SPEED }.sortedBy { it.y1 }
            for (z in speedZones) {
                tl.setSpeedFrom(z.y1, baseSpeed * z.value)
                tl.setSpeedFrom(z.y2, baseSpeed)
            }
            return tl
        }
    }
}
