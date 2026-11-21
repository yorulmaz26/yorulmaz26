package com.besaniyelik.dunya

/** Platforma özgü küçük servisler (titreşim vb.). */
interface Platform {
    val name: String
    fun vibrate(ms: Int)

    /** Reklam altyapısı; reklamsız platformlarda [AdService.NONE]. */
    val ads: com.besaniyelik.dunya.ads.AdService get() = com.besaniyelik.dunya.ads.AdService.NONE

    companion object {
        val NONE = object : Platform {
            override val name = "none"
            override fun vibrate(ms: Int) {}
        }
    }
}
