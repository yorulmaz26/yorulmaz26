package com.yuruyensehir.logic

import kotlinx.serialization.Serializable

/**
 * Gerçek parayla (Google Play) satılan ürünler. Hepsi yalnızca KOZMETİKTİR: oynanışa avantaj vermez,
 * oyunun tamamı parasız oynanabilir. Ürün kimlikleri Play Console'da aynı adla tek seferlik ürün olarak tanımlanmalıdır.
 */
@Serializable
enum class PremiumProduct(val productId: String, val title: String, val description: String) {
    SUPPORTER_PACK("destekci_paketi", "Destekçi Paketi", "Geliştiriciyi destekleyin: yalnızca bu pakette bulunan Kutup Işığı Derisi ve Yıldız Sancağı."),
    ALL_COSMETICS("tum_kozmetikler", "Tüm Kozmetikler", "Marketteki bütün kozmetik ve paketleri kalıcı olarak açar (oyun içi altınla da tek tek alınabilir)."),
    ;

    companion object {
        fun byId(id: String) = entries.firstOrNull { it.productId == id }
    }
}

object PremiumSystem {
    fun premiumOnly(c: Cosmetic) = c == Cosmetic.SKIN_AURORA || c == Cosmetic.FLAG_STAR

    fun grants(p: PremiumProduct): List<Cosmetic> = when (p) {
        PremiumProduct.SUPPORTER_PACK -> listOf(Cosmetic.SKIN_AURORA, Cosmetic.FLAG_STAR)
        PremiumProduct.ALL_COSMETICS -> Cosmetic.entries.filter { !premiumOnly(it) }
    }

    fun owned(state: GameState, p: PremiumProduct) = p in state.premium

    /** Mağazadan doğrulanmış (onaylanmış) satın alma geldiğinde çağrılır. Tekrarlı çağrı güvenlidir. */
    fun grant(state: GameState, p: PremiumProduct): ActionResult {
        val first = state.premium.add(p)
        grants(p).forEach { state.cosmetics.owned += it }
        if (p == PremiumProduct.SUPPORTER_PACK && first) {
            state.cosmetics.equipped[CosmeticSlot.CARRIER_SKIN] = Cosmetic.SKIN_AURORA
            state.cosmetics.equipped[CosmeticSlot.FLAG] = Cosmetic.FLAG_STAR
        }
        if (first) state.log("${p.title} etkinleştirildi. Desteğiniz için teşekkürler!", LogKind.STAGE)
        state.cityVersion++
        return ActionResult.ok("${p.title} etkin.")
    }

    /**
     * Mağaza sorgusu başarılı olduğunda sahip olunan ürünlerin tam listesiyle çağrılır (geri yükleme ve iade).
     * İade edilen ürünün yalnızca o ürüne özel öğeleri geri alınır; altınla satın alınmış öğelere dokunulmaz.
     */
    fun sync(state: GameState, ownedIds: Collection<String>) {
        val owned = ownedIds.mapNotNull { PremiumProduct.byId(it) }.toSet()
        owned.forEach { grant(state, it) }
        for (p in state.premium.toList()) if (p !in owned) {
            state.premium.remove(p)
            if (p == PremiumProduct.SUPPORTER_PACK) {
                grants(p).forEach { c -> state.cosmetics.owned.remove(c); if (state.cosmetics.equipped[c.slot] == c) state.cosmetics.equipped.remove(c.slot) }
            } else {
                // Tüm Kozmetikler iade edilince: oyun içi altınla ayrıca alınmamış öğeler kapanır.
                grants(p).filter { it !in state.cosmetics.boughtWithGold }.forEach { c ->
                    state.cosmetics.owned.remove(c); if (state.cosmetics.equipped[c.slot] == c) state.cosmetics.equipped.remove(c.slot)
                }
            }
            state.log("${p.title} artık etkin değil (mağaza kaydına göre).", LogKind.WARNING)
            state.cityVersion++
        }
    }
}
