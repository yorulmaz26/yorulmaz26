package com.zamaninefendisi.logic.meta

import com.zamaninefendisi.logic.meta.Resource.*

enum class ShopCategory(val title: String) {
    CASTLE_SKIN("Zaman Kalesi Görünümleri"),
    UNIT_COSTUME("Asker Kostümleri"),
    PORTAL_EFFECT("Portal Efektleri"),
    BUILDING_DESIGN("Özel Bina Tasarımları"),
    MAP_THEME("Harita Temaları"),
    HERO_LOOK("Karakter Görünümleri"),
    DECORATION("Dekorasyonlar"),
    PROFILE_FRAME("Profil Çerçeveleri"),
    ANIMATION("Animasyonlar"),
    RESOURCE_PACK("Kaynak Paketleri"),
}

/**
 * Market ürünü. Kozmetikler giyilir/kuşanılır ve oyunda gerçekten görünür ([colors]); kaynak paketleri anında
 * [grant] verir. [requiresResearch]: yalnızca o araştırma tamamlanınca satışa çıkar.
 */
class ShopItem(
    val id: String,
    val category: ShopCategory,
    val title: String,
    val description: String,
    val price: Cost,
    val colors: List<String> = emptyList(),
    val grant: Map<Resource, Int> = emptyMap(),
    val requiresResearch: String? = null,
) {
    val consumable: Boolean get() = category == ShopCategory.RESOURCE_PACK
}

/** Ödeme sağlayıcısı soyutlaması: ilk sürümde oyun içi para; ileride Google Play Billing bu arayüzle eklenir. */
interface PurchaseProvider {
    val name: String
    fun canPay(wallet: Map<Resource, Int>, price: Cost): Boolean
}

object InGameCurrency : PurchaseProvider {
    override val name = "Oyun içi para"
    override fun canPay(wallet: Map<Resource, Int>, price: Cost) = wallet.has(price)
}

object Shop {
    private fun gold(n: Int): Cost = mapOf(GOLD to n)
    private fun crystals(n: Int): Cost = mapOf(CRYSTAL to n)

    val items: List<ShopItem> = listOf(
        ShopItem("castle_classic", ShopCategory.CASTLE_SKIN, "Lavanta Kalesi", "Varsayılan kale görünümü.", gold(0), listOf("F3EAFF", "DCCCF6", "BFA8EA")),
        ShopItem("castle_sun", ShopCategory.CASTLE_SKIN, "Güneş Kalesi", "Altın kuleler ve sıcak taşlar.", gold(150), listOf("FFF1C2", "F7D98B", "E8B957")),
        ShopItem("castle_sea", ShopCategory.CASTLE_SKIN, "Deniz Kalesi", "Turkuaz kuleler.", gold(220), listOf("D9FBF6", "A6EEE3", "6DD3C5")),
        ShopItem("castle_paradox", ShopCategory.CASTLE_SKIN, "Kırık Zaman Kalesi", "Pembe-mor paradoks taşları.", mapOf(PARADOX to 25), listOf("FFE3F1", "F6B5D6", "D98AC0"), requiresResearch = ResearchTree.PARADOX_COSMETICS),
        ShopItem("unit_blue", ShopCategory.UNIT_COSTUME, "Krallık Mavisi", "Varsayılan asker kostümü.", gold(0), listOf("4F8BE8")),
        ShopItem("unit_green", ShopCategory.UNIT_COSTUME, "Orman Muhafızları", "Yeşil pelerinli birlikler.", gold(120), listOf("3FAE6A")),
        ShopItem("unit_gold", ShopCategory.UNIT_COSTUME, "Altın Lejyon", "Parlak altın zırhlar.", crystals(4), listOf("E0A82E")),
        ShopItem("portal_white", ShopCategory.PORTAL_EFFECT, "Işık Dalgası", "Varsayılan çağ geçiş efekti.", gold(0), listOf("FFFFFF")),
        ShopItem("portal_rainbow", ShopCategory.PORTAL_EFFECT, "Gökkuşağı Geçidi", "Çağ geçişinde renkli halkalar.", gold(180), listOf("FF8A3D", "F5B83D", "5CC46A", "4FB3E8", "8E6CE0")),
        ShopItem("portal_stars", ShopCategory.PORTAL_EFFECT, "Yıldız Tozu", "Altın yıldız parıltıları.", crystals(3), listOf("FFE08A", "FFF6D6")),
        ShopItem("roof_red", ShopCategory.BUILDING_DESIGN, "Kiremit Çatılar", "Varsayılan bina çatıları.", gold(0), listOf("E2654F")),
        ShopItem("roof_blue", ShopCategory.BUILDING_DESIGN, "Gök Çatılar", "Mavi çatılı kale binaları.", gold(100), listOf("5B9BE0")),
        ShopItem("roof_green", ShopCategory.BUILDING_DESIGN, "Bahçe Çatıları", "Yosun yeşili çatılar.", gold(100), listOf("5CB85C")),
        ShopItem("theme_default", ShopCategory.MAP_THEME, "Klasik Vadi", "Varsayılan harita renkleri.", gold(0), listOf("FFFFFF")),
        ShopItem("theme_spring", ShopCategory.MAP_THEME, "Bahar", "Daha canlı ve çiçekli çayırlar.", gold(160), listOf("A8F07A")),
        ShopItem("theme_autumn", ShopCategory.MAP_THEME, "Sonbahar", "Altın sarısı çayırlar.", gold(160), listOf("E9C46A")),
        ShopItem("theme_winter", ShopCategory.MAP_THEME, "Kış", "Karla serpilmiş açık çayırlar.", crystals(3), listOf("E6F4FF")),
        ShopItem("hero_era", ShopCategory.HERO_LOOK, "Çağ Pelerini", "Pelerin bulunduğun çağın rengini alır (varsayılan).", gold(0)),
        ShopItem("hero_royal", ShopCategory.HERO_LOOK, "Kraliyet Pelerini", "Mor-altın pelerin.", gold(140), listOf("8E6CE0")),
        ShopItem("hero_ember", ShopCategory.HERO_LOOK, "Kor Pelerini", "Turuncu ateş pelerini.", gold(140), listOf("FF6B3D")),
        ShopItem("hero_glacier", ShopCategory.HERO_LOOK, "Buzul Pelerini", "Buz mavisi pelerin.", crystals(3), listOf("7FD8FF")),
        ShopItem("deco_none", ShopCategory.DECORATION, "Sade Avlu", "Süslemesiz kale avlusu.", gold(0)),
        ShopItem("deco_flags", ShopCategory.DECORATION, "Bayrak Dizisi", "Kale avlusunda renkli bayraklar.", gold(80), listOf("FF8A3D", "4FB3E8", "5CC46A")),
        ShopItem("deco_fountain", ShopCategory.DECORATION, "Zaman Çeşmesi", "Avluda kristal çeşme.", gold(200), listOf("7FE3F0")),
        ShopItem("deco_garden", ShopCategory.DECORATION, "Çiçek Bahçesi", "Avluda çiçek tarhları.", gold(120), listOf("F27BA8", "F5B83D")),
        ShopItem("frame_basic", ShopCategory.PROFILE_FRAME, "Sade Çerçeve", "Varsayılan profil çerçevesi.", gold(0), listOf("FFFFFF")),
        ShopItem("frame_gold", ShopCategory.PROFILE_FRAME, "Altın Çerçeve", "Parlak altın profil çerçevesi.", gold(150), listOf("F5B83D")),
        ShopItem("frame_crystal", ShopCategory.PROFILE_FRAME, "Kristal Çerçeve", "Buz kristali çerçeve.", crystals(2), listOf("7FE3F0")),
        ShopItem("anim_confetti", ShopCategory.ANIMATION, "Konfeti", "Zaferde renkli konfeti (varsayılan).", gold(0), listOf("FF8A3D", "4FB3E8", "5CC46A", "F5B83D")),
        ShopItem("anim_crystals", ShopCategory.ANIMATION, "Kristal Yağmuru", "Zaferde kristaller yağar.", gold(130), listOf("7FE3F0", "4FB8D9", "FFFFFF")),
        ShopItem("anim_petals", ShopCategory.ANIMATION, "Çiçek Yaprakları", "Zaferde pembe yapraklar uçuşur.", gold(130), listOf("F27BA8", "FFD1E3")),
        ShopItem("pack_builder", ShopCategory.RESOURCE_PACK, "İnşaatçı Paketi", "60 odun + 40 taş", gold(80), grant = mapOf(WOOD to 60, STONE to 40)),
        ShopItem("pack_smith", ShopCategory.RESOURCE_PACK, "Demirci Paketi", "30 demir", gold(90), grant = mapOf(IRON to 30)),
        ShopItem("pack_scholar", ShopCategory.RESOURCE_PACK, "Bilgin Paketi", "50 bilim puanı", gold(120), grant = mapOf(SCIENCE to 50)),
        ShopItem("pack_energy", ShopCategory.RESOURCE_PACK, "Enerji Hücresi", "60 enerji", gold(70), grant = mapOf(ENERGY to 60)),
        ShopItem("pack_crystal", ShopCategory.RESOURCE_PACK, "Kristal Dönüşümü", "15 Paradoks Enerjisi → 1 Zaman Kristali", mapOf(PARADOX to 15), grant = mapOf(CRYSTAL to 1), requiresResearch = ResearchTree.PARADOX_TRADE),
    )

    fun item(id: String): ShopItem? = items.firstOrNull { it.id == id }
    fun defaults(): Map<ShopCategory, String> = ShopCategory.entries.filter { it != ShopCategory.RESOURCE_PACK }
        .associateWith { c -> items.first { it.category == c && it.price.values.all { v -> v == 0 } }.id }

    fun available(research: Set<String>): List<ShopItem> = items.filter { it.requiresResearch == null || it.requiresResearch in research }

    /** Ticaret Merkezi seviyesine göre kaynak paketlerinde indirim (en fazla %25). */
    fun priceFor(item: ShopItem, tradeLevel: Int): Cost =
        if (item.consumable && item.price.containsKey(GOLD)) item.price.mapValues { (r, v) -> if (r == GOLD) (v * (1.0 - 0.05 * tradeLevel.coerceAtMost(5))).toInt() else v } else item.price
}
