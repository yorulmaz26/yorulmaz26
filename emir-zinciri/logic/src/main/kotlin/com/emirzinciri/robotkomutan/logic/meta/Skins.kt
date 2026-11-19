package com.emirzinciri.robotkomutan.logic.meta

import com.emirzinciri.robotkomutan.logic.progress.SaveData

/** Satın alınabilir robot görünümleri (yalnızca görsel; oynanışı etkilemez). */
data class Skin(val id: String, val title: String, val color: Int, val price: Long)

object Skins {
    val all = listOf(
        Skin("classic", "Klasik Turkuaz", 0x24B8B0, 0),
        Skin("coral", "Mercan", 0xF07A8C, 150),
        Skin("lemon", "Limon", 0xF2C53D, 200),
        Skin("sky", "Gök Mavisi", 0x4FA3E3, 250),
        Skin("mint", "Nane", 0x62C78A, 300),
        Skin("lavender", "Lavanta", 0x9277E8, 350),
        Skin("tangerine", "Mandalina", 0xFF9B42, 400),
        Skin("rose", "Gül Kurusu", 0xC9748F, 600),
        Skin("steel", "Çelik", 0x8296B6, 800),
        Skin("gold", "Altın Komutan", 0xE0B23A, 1500),
    )

    fun of(id: String) = all.firstOrNull { it.id == id } ?: all.first()

    /** Satın alır; yeterli altın yoksa veya zaten sahipse false. */
    fun buy(save: SaveData, skin: Skin): Boolean {
        val m = save.meta
        if (skin.id in m.ownedSkins || save.wallet.gold < skin.price) return false
        save.wallet.gold -= skin.price
        m.ownedSkins += skin.id
        m.skin = skin.id
        return true
    }

    fun select(save: SaveData, skin: Skin): Boolean {
        if (skin.id !in save.meta.ownedSkins) return false
        save.meta.skin = skin.id
        return true
    }
}
