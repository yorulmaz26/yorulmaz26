package com.yuruyensehir.android

import android.app.Activity
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.badlogic.gdx.Gdx
import com.yuruyensehir.game.PlatformStore
import com.yuruyensehir.game.StoreProduct
import com.yuruyensehir.logic.PremiumProduct

/**
 * Google Play Billing (tek seferlik, kozmetik ürünler). Satın alma ancak Play'in PURCHASED durumunu bildirmesi
 * ve onaylanmasından (acknowledge) sonra oyuna işlenir; onaylanmayan satın alımları Play 3 gün içinde iade eder.
 */
class PlayBillingStore(private val activity: Activity) : PlatformStore, PurchasesUpdatedListener {
    private val details = HashMap<String, ProductDetails>()
    private var pendingPurchased: ((String) -> Unit)? = null
    private var pendingMessage: ((String) -> Unit)? = null
    private var onOwned: ((List<String>) -> Unit)? = null
    @Volatile private var connected = false
    @Volatile private var reason: String? = "Google Play'e bağlanılıyor…"

    private val client: BillingClient = BillingClient.newBuilder(activity)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    override val ready: Boolean get() = connected && details.isNotEmpty()
    override val unavailableReason: String? get() = if (ready) null else reason

    override fun products(): List<StoreProduct> = details.values.mapNotNull { d ->
        d.oneTimePurchaseOfferDetails?.formattedPrice?.let { StoreProduct(d.productId, it) }
    }

    private fun onGame(block: () -> Unit) = Gdx.app.postRunnable(block)

    override fun connect(onOwned: (List<String>) -> Unit) {
        this.onOwned = onOwned
        if (connected) { queryAll(); return }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    connected = true
                    reason = "Ürünler yükleniyor…"
                    queryAll()
                } else {
                    reason = "Google Play mağazası kullanılamıyor (${result.debugMessage.ifEmpty { "kod ${result.responseCode}" }})."
                }
            }

            override fun onBillingServiceDisconnected() {
                connected = false
                reason = "Google Play bağlantısı koptu; Market'i yeniden açın."
            }
        })
    }

    private fun queryAll() {
        val products = PremiumProduct.entries.map {
            QueryProductDetailsParams.Product.newBuilder().setProductId(it.productId).setProductType(BillingClient.ProductType.INAPP).build()
        }
        client.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(products).build()) { result, list ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                list.forEach { details[it.productId] = it }
                reason = if (list.isEmpty()) "Ürünler Play Console'da henüz tanımlanmamış." else null
            } else reason = "Ürün bilgileri alınamadı (${result.responseCode})."
        }
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()) { result, purchases ->
            // Yalnızca başarılı sorgu "tam liste" sayılır; çevrimdışıyken iade yanlışlıkla uygulanmaz.
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryPurchasesAsync
            val owned = purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
            owned.filter { !it.isAcknowledged }.forEach { acknowledge(it) {} }
            val ids = owned.flatMap { it.products }
            onGame { onOwned?.invoke(ids) }
        }
    }

    override fun purchase(productId: String, onPurchased: (String) -> Unit, onMessage: (String) -> Unit) {
        val d = details[productId]
        if (!connected || d == null) { onMessage(unavailableReason ?: "Ürün bulunamadı."); return }
        pendingPurchased = onPurchased
        pendingMessage = onMessage
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).build()))
            .build()
        activity.runOnUiThread {
            val r = client.launchBillingFlow(activity, params)
            if (r.responseCode != BillingClient.BillingResponseCode.OK) onGame { onMessage("Satın alma başlatılamadı (${r.responseCode}).") }
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases?.forEach { handle(it) }
            BillingClient.BillingResponseCode.USER_CANCELED -> onGame { pendingMessage?.invoke("Satın alma iptal edildi.") }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> queryAll()
            else -> onGame { pendingMessage?.invoke("Satın alma tamamlanamadı (${result.responseCode}).") }
        }
    }

    private fun handle(p: Purchase) {
        when (p.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> {
                val grant = { p.products.forEach { id -> onGame { pendingPurchased?.invoke(id) ?: onOwned?.invoke(listOf(id)) } } }
                if (p.isAcknowledged) grant() else acknowledge(p, grant)
            }
            Purchase.PurchaseState.PENDING -> onGame { pendingMessage?.invoke("Ödeme bekleniyor; onaylanınca ürün açılacak.") }
            else -> Unit
        }
    }

    private fun acknowledge(p: Purchase, then: () -> Unit) {
        client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()) { r ->
            if (r.responseCode == BillingClient.BillingResponseCode.OK) then()
            else onGame { pendingMessage?.invoke("Satın alma onaylanamadı; daha sonra 'Geri yükle' ile tekrar deneyin.") }
        }
    }

    override fun dispose() {
        if (client.isReady) client.endConnection()
    }
}
