package com.yuruyensehir.logic

import com.yuruyensehir.logic.Resource.*
import kotlinx.serialization.Serializable

/** Kozmetik yuvaları: her yuvada aynı anda bir öğe takılı olur. */
@Serializable
enum class CosmeticSlot(val title: String) {
    CARRIER_SKIN("Taşıyıcı Görünümü"), ROOF_THEME("Bina Görünümü"), FLAG("Bayrak"), TRAIL("Yolculuk Efekti"),
    HOUSE_STYLE("Özel Yapı Tasarımı"), DECOR("Şehir Dekorasyonu"), COLLECTIBLE("Koleksiyon Ürünü"),
}

/** Market öğeleri. Fiyatlar yalnızca oyun içi kaynaklarla; gerçek para gerekmez. */
@Serializable
enum class Cosmetic(val title: String, val slot: CosmeticSlot, val description: String, val price: Amounts, val colorHex: String = "") {
    SKIN_SUNSET("Gün Batımı Derisi", CosmeticSlot.CARRIER_SKIN, "Taşıyıcıya turuncu-pembe tonlar.", amounts(GOLD to 150), "F4A27A"),
    SKIN_OCEAN("Okyanus Derisi", CosmeticSlot.CARRIER_SKIN, "Turkuaz ve deniz mavisi tonlar.", amounts(GOLD to 150), "5CC8D8"),
    SKIN_GOLDEN("Altın Pullar", CosmeticSlot.CARRIER_SKIN, "Parıldayan altın pullar.", amounts(GOLD to 400, ANCIENT_PART to 3), "E9C46A"),
    ROOF_TURQUOISE("Turkuaz Çatılar", CosmeticSlot.ROOF_THEME, "Bütün çatılar turkuaz.", amounts(GOLD to 120), "2EC4B6"),
    ROOF_LAVENDER("Lavanta Çatılar", CosmeticSlot.ROOF_THEME, "Yumuşak lavanta tonları.", amounts(GOLD to 120), "B39DDB"),
    ROOF_FOREST("Orman Çatıları", CosmeticSlot.ROOF_THEME, "Yosun yeşili çatılar.", amounts(GOLD to 120), "6DBE6A"),
    FLAG_SUN("Güneş Bayrağı", CosmeticSlot.FLAG, "Şehir merkezinde altın güneş bayrağı.", amounts(GOLD to 60), "F6C445"),
    FLAG_WAVE("Dalga Bayrağı", CosmeticSlot.FLAG, "Mavi dalgalı bayrak.", amounts(GOLD to 60), "4FC3F7"),
    FLAG_LEAF("Yaprak Bayrağı", CosmeticSlot.FLAG, "Yeşil yaprak bayrağı.", amounts(GOLD to 60), "7BC86C"),
    TRAIL_FLOWERS("Çiçek İzi", CosmeticSlot.TRAIL, "Taşıyıcı yürürken arkasında çiçekler açar.", amounts(GOLD to 200), "F48FB1"),
    TRAIL_SPARKS("Kristal Işıltısı", CosmeticSlot.TRAIL, "Adımlarda mor ışıltılar.", amounts(GOLD to 200, CRYSTAL to 5), "B388FF"),
    HOUSE_DOME("Kubbeli Evler", CosmeticSlot.HOUSE_STYLE, "Evler kubbeli çöl mimarisiyle görünür.", amounts(GOLD to 180), "FFF1D6"),
    HOUSE_TALL("Kule Evler", CosmeticSlot.HOUSE_STYLE, "Evler sivri kule çatılı görünür.", amounts(GOLD to 180), "E9724C"),
    DECOR_LANTERNS("Fener Dizileri", CosmeticSlot.DECOR, "Şehrin kenarına renkli fenerler.", amounts(GOLD to 90, CLOTH to 20), "FFD54F"),
    DECOR_GARDENS("Çiçek Bahçeleri", CosmeticSlot.DECOR, "Boş karelere çiçek tarhları.", amounts(GOLD to 90, WOOD to 30), "F48FB1"),
    STATUE_CARRIER("Taşıyıcı Heykelciği", CosmeticSlot.COLLECTIBLE, "Koleksiyonluk minyatür (görsel ve başarım puanı).", amounts(GOLD to 250, ANCIENT_PART to 2)),
    MAP_GLOBE("Gezgin Küresi", CosmeticSlot.COLLECTIBLE, "Koleksiyonluk minyatür küre.", amounts(GOLD to 250, TECH_PART to 2)),
}

/** Birden fazla öğeyi indirimli satan paketler. */
@Serializable
enum class CosmeticPack(val title: String, val items: List<Cosmetic>, val price: Amounts) {
    SEA_PACK("Deniz Paketi", listOf(Cosmetic.SKIN_OCEAN, Cosmetic.ROOF_TURQUOISE, Cosmetic.FLAG_WAVE), amounts(GOLD to 260)),
    GARDEN_PACK("Bahçe Paketi", listOf(Cosmetic.ROOF_FOREST, Cosmetic.FLAG_LEAF, Cosmetic.DECOR_GARDENS, Cosmetic.TRAIL_FLOWERS), amounts(GOLD to 380, WOOD to 30)),
}

@Serializable
data class CosmeticState(
    val owned: MutableSet<Cosmetic> = mutableSetOf(),
    val equipped: MutableMap<CosmeticSlot, Cosmetic> = mutableMapOf(),
)

/**
 * Satın alma sağlayıcısı. Bugün yalnızca oyun içi kaynaklarla çalışan [InGameCurrencyProvider] var;
 * ileride Google Play Billing bu arayüzü uygulayarak eklenebilir (oyun mantığı değişmez).
 */
interface PurchaseProvider {
    val id: String
    fun canPay(state: GameState, price: Amounts): Boolean
    fun pay(state: GameState, price: Amounts): Boolean
}

object InGameCurrencyProvider : PurchaseProvider {
    override val id = "oyun-ici"
    override fun canPay(state: GameState, price: Amounts) = state.store.canAfford(price)
    override fun pay(state: GameState, price: Amounts): Boolean {
        if (!canPay(state, price)) return false
        state.store.spend(price)
        return true
    }
}

object ShopSystem {
    var provider: PurchaseProvider = InGameCurrencyProvider

    fun owned(state: GameState, c: Cosmetic) = c in state.cosmetics.owned
    fun equipped(state: GameState, slot: CosmeticSlot) = state.cosmetics.equipped[slot]

    fun buy(state: GameState, c: Cosmetic): ActionResult {
        if (owned(state, c)) return ActionResult.fail("Zaten sizde.")
        if (!provider.canPay(state, c.price)) return ActionResult.fail("Yetersiz kaynak: ${c.price.describe()}")
        if (!provider.pay(state, c.price)) return ActionResult.fail("Satın alma tamamlanamadı.")
        state.cosmetics.owned += c
        if (c.slot != CosmeticSlot.COLLECTIBLE) state.cosmetics.equipped[c.slot] = c
        state.stats.purchases++
        state.log("Marketten alındı: ${c.title}.", LogKind.INFO)
        state.cityVersion++
        return ActionResult.ok("${c.title} alındı ve takıldı.")
    }

    fun buyPack(state: GameState, p: CosmeticPack): ActionResult {
        if (p.items.all { owned(state, it) }) return ActionResult.fail("Paketteki her şey sizde.")
        if (!provider.canPay(state, p.price)) return ActionResult.fail("Yetersiz kaynak: ${p.price.describe()}")
        provider.pay(state, p.price)
        p.items.forEach { state.cosmetics.owned += it; if (it.slot != CosmeticSlot.COLLECTIBLE) state.cosmetics.equipped[it.slot] = it }
        state.stats.purchases++
        state.log("Marketten paket alındı: ${p.title}.", LogKind.INFO)
        state.cityVersion++
        return ActionResult.ok("${p.title} alındı.")
    }

    fun equip(state: GameState, c: Cosmetic): ActionResult {
        if (!owned(state, c)) return ActionResult.fail("Önce satın alın.")
        if (c.slot == CosmeticSlot.COLLECTIBLE) return ActionResult.fail("Koleksiyon ürünleri sergilenir, takılmaz.")
        state.cosmetics.equipped[c.slot] = c
        state.cityVersion++
        return ActionResult.ok("${c.title} takıldı.")
    }

    fun unequip(state: GameState, slot: CosmeticSlot): ActionResult {
        state.cosmetics.equipped.remove(slot) ?: return ActionResult.fail("Bu yuvada takılı öğe yok.")
        state.cityVersion++
        return ActionResult.ok("${slot.title}: varsayılan görünüm.")
    }

    /** Şehir ve taşıyıcı adlandırma (özelleştirme). */
    fun rename(state: GameState, city: String?, carrier: String?): ActionResult {
        fun clean(s: String) = s.trim().replace(Regex("[\\[\\]\\p{Cntrl}]"), "").take(24)
        city?.let { val c = clean(it); if (c.isEmpty()) return ActionResult.fail("İsim boş olamaz."); state.cityName = c }
        carrier?.let { val c = clean(it); if (c.isEmpty()) return ActionResult.fail("İsim boş olamaz."); state.carrier.name = c }
        state.cityVersion++
        return ActionResult.ok("İsim güncellendi.")
    }
}
