package com.example.gezegenkacisi

/**
 * Oyunun reklamlarla konuştuğu arayüz. GameView yalnızca bunu bilir; AdMob'a doğrudan bağımlı değildir
 * (gerçek uygulama [AdManager]'ı verir, testlerde reklamsız çalışır).
 */
interface AdsBridge {
    /** Alttaki banner'ı göster/gizle (yalnızca menü ekranlarında açılır). */
    fun setBannerVisible(visible: Boolean)

    /** Banner şu an ekranda kaplıyorsa yüksekliği (piksel), değilse 0. */
    val bannerHeightPx: Int

    /** Yüklü bir banner gösterilse kaplayacağı yükseklik (piksel); banner yoksa 0. */
    val bannerSlotPx: Int

    /** Geçiş (resim) reklamı yüklü ve gösterilmeye hazır mı. */
    fun interstitialReady(): Boolean

    /** Geçiş reklamını gösterir; kapanınca [onDone] çağrılır. Gösterilemezse false döner (onDone çağrılmaz). */
    fun showInterstitial(onDone: () -> Unit): Boolean

    /** Ödüllü video reklamı hazır mı. */
    fun rewardedReady(): Boolean

    /**
     * Ödüllü videoyu gösterir. Kullanıcı ödülü hak ettiyse kapanınca önce [onReward], her durumda [onDone] çağrılır.
     * Gösterilemezse false döner.
     */
    fun showRewarded(onReward: () -> Unit, onDone: () -> Unit): Boolean

    /** AB/İngiltere gibi yerlerde reklam izni ayarlarına erişim düğmesi gösterilmeli mi. */
    fun privacyOptionsRequired(): Boolean

    /** Reklam izni (gizlilik) ayarları penceresini açar. */
    fun showPrivacyOptions()
}
