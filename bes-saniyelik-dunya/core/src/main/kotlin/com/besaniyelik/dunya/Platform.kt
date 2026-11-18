package com.besaniyelik.dunya

/** Platforma özgü küçük servisler (titreşim vb.). */
interface Platform {
    val name: String
    fun vibrate(ms: Int)

    companion object {
        val NONE = object : Platform {
            override val name = "none"
            override fun vibrate(ms: Int) {}
        }
    }
}
