package com.yuruyensehir.game

/** Mağazadaki bir ürünün yerelleştirilmiş fiyatı (Google Play'den gelir). */
data class StoreProduct(val productId: String, val price: String)

/**
 * Gerçek parayla satın alma köprüsü. Android'de Google Play Billing ile uygulanır;
 * masaüstünde ve mağazasız ortamlarda [NoStore] kullanılır. Bütün geri çağrılar oyun iş parçacığında
 * (Gdx.app.postRunnable) gelecek şekilde uygulanmalıdır.
 */
interface PlatformStore {
    /** Mağaza bağlı ve ürün bilgileri alındı mı? */
    val ready: Boolean
    val unavailableReason: String?
    fun products(): List<StoreProduct>

    /** Bağlanır, ürünleri ve sahip olunanları sorgular. [onOwned] yalnızca sorgu başarılıysa, tam listeyle çağrılır. */
    fun connect(onOwned: (List<String>) -> Unit)

    /** Satın alma akışını başlatır. [onPurchased] onaylanmış ürün kimliğiyle; [onMessage] hata/iptal için. */
    fun purchase(productId: String, onPurchased: (String) -> Unit, onMessage: (String) -> Unit)

    fun dispose() {}
}

object NoStore : PlatformStore {
    override val ready = false
    override val unavailableReason = "Bu sürümde gerçek para mağazası yok (yalnızca Google Play sürümünde)."
    override fun products() = emptyList<StoreProduct>()
    override fun connect(onOwned: (List<String>) -> Unit) {}
    override fun purchase(productId: String, onPurchased: (String) -> Unit, onMessage: (String) -> Unit) = onMessage(unavailableReason)
}
