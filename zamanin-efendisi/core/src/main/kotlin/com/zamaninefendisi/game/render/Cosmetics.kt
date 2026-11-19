package com.zamaninefendisi.game.render

import com.badlogic.gdx.graphics.Color
import com.zamaninefendisi.logic.meta.Shop
import com.zamaninefendisi.logic.meta.ShopCategory

/** Kuşanılmış market ürünlerinin çizime yansıyan renkleri. */
class Cosmetics(private val equippedIds: Map<ShopCategory, String>) {
    private fun colors(c: ShopCategory): List<Color> {
        val item = equippedIds[c]?.let { Shop.item(it) } ?: return emptyList()
        return item.colors.map { Color.valueOf(it) }
    }

    val castle: List<Color> = colors(ShopCategory.CASTLE_SKIN).ifEmpty { listOf(Color.valueOf("F3EAFF"), Color.valueOf("DCCCF6"), Color.valueOf("BFA8EA")) }
    val ally: Color = colors(ShopCategory.UNIT_COSTUME).firstOrNull() ?: Color.valueOf("4F8BE8")
    val portal: List<Color> = colors(ShopCategory.PORTAL_EFFECT).ifEmpty { listOf(Color.WHITE) }
    val roof: Color = colors(ShopCategory.BUILDING_DESIGN).firstOrNull() ?: Color.valueOf("E2654F")
    /** Harita teması: çayır rengine karışan ton (null = klasik). */
    val themeTint: Color? = colors(ShopCategory.MAP_THEME).firstOrNull()?.takeIf { equippedIds[ShopCategory.MAP_THEME] != "theme_default" }
    /** Pelerin rengi (null = bulunulan çağın rengi). */
    val heroCloak: Color? = colors(ShopCategory.HERO_LOOK).firstOrNull()
    val decoration: String = equippedIds[ShopCategory.DECORATION] ?: "deco_none"
    val decorationColors: List<Color> = colors(ShopCategory.DECORATION)
    val frame: Color = colors(ShopCategory.PROFILE_FRAME).firstOrNull() ?: Color.WHITE
    val victory: List<Color> = colors(ShopCategory.ANIMATION).ifEmpty { listOf(Color.ORANGE, Color.SKY, Color.LIME, Color.GOLD) }
    val victoryStyle: String = equippedIds[ShopCategory.ANIMATION] ?: "anim_confetti"
}
