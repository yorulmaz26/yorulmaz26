package com.besaniyelik.dunya.i18n

/**
 * Oyuncu adı kayıtta Türkçe takma ad olarak durur ("Koşucu"); ekranda seçili dildeki karşılığı gösterilir.
 * Böylece dil değişince ad da çevrilir, kayıt değişmez.
 */
object PlayerNames {
    val SOURCE = listOf("Koşucu", "Zıpzıp", "Hızlı", "Yıldız", "Bulut", "Kaşif", "Şimşek", "Minik")

    fun display(stored: String): String {
        val i = SOURCE.indexOf(stored)
        return if (i < 0) stored else L.list("player.names").getOrElse(i) { stored }
    }

    fun next(stored: String): String = SOURCE[(SOURCE.indexOf(stored) + 1).mod(SOURCE.size)]
}
