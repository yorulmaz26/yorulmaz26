package com.yanlisrenk.game

import com.yanlisrenk.logic.i18n.L

/**
 * Marka kimliği. Türkçe seçiliyken Türkçe ad ana isim, İngilizce ad alt başlıktır;
 * diğer dillerde İngilizce ad ana isim, Türkçe ad alt başlıktır.
 */
object Brand {
    const val NAME_TR = "Yanlış Renk"
    const val NAME_EN = "Miscolor"
    const val DEVELOPER = "Magic Lumos Software"

    val turkishMain: Boolean get() = L.lang == "tr"
    val mainName: String get() = if (turkishMain) NAME_TR else NAME_EN
    val subName: String get() = if (turkishMain) NAME_EN else NAME_TR

    /** Ana logo görseli (Türkçe: kullanıcı logosu, diğer diller: Miscolor logosu). */
    fun logo(assets: Assets): String = when {
        !turkishMain && assets.has("ui2_logo_en") -> "ui2_logo_en"
        assets.has("ui2_logo_tr") -> "ui2_logo_tr"
        else -> "ui2_logo"
    }
}
