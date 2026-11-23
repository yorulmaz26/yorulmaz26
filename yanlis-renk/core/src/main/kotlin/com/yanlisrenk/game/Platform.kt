package com.yanlisrenk.game

import com.yanlisrenk.logic.save.SaveStorage
import com.yanlisrenk.logic.time.PlatformClock

/** Platforma özgü hizmetler (Android / masaüstü). */
interface PlatformServices {
    val clock: PlatformClock
    val storage: SaveStorage
    /** Yerel saat dilimi farkı (ms). */
    fun tzOffsetMillis(): Long
    /** Güvenilir sunucu zamanını arka planda alır; sonuç ana iş parçacığında [onResult] ile döner (null = alınamadı). */
    fun fetchServerTime(onResult: (Long?) -> Unit)
    fun vibrate(ms: Int)
    fun newInstallId(): String
    val platformName: String
    val versionName: String
    /** Reklam hizmeti (varsayılan: reklamsız). */
    val ads: AdService get() = NoAds
}
