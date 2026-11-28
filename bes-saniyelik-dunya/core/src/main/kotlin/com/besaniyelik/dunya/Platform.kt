package com.besaniyelik.dunya

/** Platforma özgü küçük servisler (titreşim, saat, dil vb.). */
interface Platform {
    val name: String
    fun vibrate(ms: Int)

    /** Reklam altyapısı; reklamsız platformlarda [AdService.NONE]. */
    val ads: com.besaniyelik.dunya.ads.AdService get() = com.besaniyelik.dunya.ads.AdService.NONE

    /** Can yenilenmesi için zaman kaynağı (Android: elapsedRealtime + açılış sayacı). */
    val time: com.besaniyelik.dunya.progress.TimeSource get() = com.besaniyelik.dunya.progress.SystemTimeSource

    /** Telefonun / bilgisayarın dili (ISO 639-1), ilk açılışta oyun dilini seçmek için. */
    fun systemLanguage(): String = java.util.Locale.getDefault().language

    companion object {
        val NONE = object : Platform {
            override val name = "none"
            override fun vibrate(ms: Int) {}
        }
    }
}
