package com.yanlisrenk.game

/**
 * Reklam hizmeti (Android: Google AdMob). Geri çağrılar oyun iş parçacığında çalışır.
 * Ekranlar reklamın nasıl gösterildiğini bilmez; yalnızca bu arayüzü kullanır.
 */
interface AdService {
    /** Şu an gösterilen banner'ın piksel yüksekliği (yüklenmemişse/gizliyse 0). */
    val bannerHeightPx: Int
    fun setBannerVisible(visible: Boolean)
    fun isInterstitialReady(): Boolean
    /** Tam ekran resimli/geçiş reklamı. Reklam kapanınca (ya da gösterilemezse) [onClosed]. */
    fun showInterstitial(onClosed: () -> Unit)
    fun isRewardedReady(): Boolean
    /** Ödüllü video. Oyuncu ödülü hak ettiyse [onResult] true ile çağrılır. */
    fun showRewarded(onResult: (Boolean) -> Unit)
    /** Gizlilik/izin seçenekleri (AB kullanıcıları için) gösterilmeli mi? */
    val privacyOptionsRequired: Boolean get() = false
    fun showPrivacyOptions() {}
    /** Masaüstü QA: banner alanını yer tutucu olarak çiz. */
    val drawsPlaceholder: Boolean get() = false
}

/** Reklamsız platform (masaüstü vb.). */
object NoAds : AdService {
    override val bannerHeightPx = 0
    override fun setBannerVisible(visible: Boolean) {}
    override fun isInterstitialReady() = false
    override fun showInterstitial(onClosed: () -> Unit) = onClosed()
    override fun isRewardedReady() = false
    override fun showRewarded(onResult: (Boolean) -> Unit) = onResult(false)
}
