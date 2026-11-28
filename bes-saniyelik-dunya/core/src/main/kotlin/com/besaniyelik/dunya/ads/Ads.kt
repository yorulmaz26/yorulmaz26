package com.besaniyelik.dunya.ads

import com.besaniyelik.dunya.progress.LoginReward
import com.besaniyelik.dunya.progress.Catalog
import com.besaniyelik.dunya.progress.ProfileService

/**
 * Platformun reklam altyapısı. Çekirdek oyun yalnızca bu arayüzü bilir: Android'de Google Mobile Ads ile
 * gerçeklenir, masaüstünde yoktur (ya da deneme için sahtesi kullanılır).
 *
 * Bütün geri çağrılar oyun (GL) iş parçacığında ve her gösterim için TAM BİR KEZ çağrılır; reklam
 * gösterilemezse de çağrılır (oyun hiçbir zaman reklam yüzünden askıda kalmaz).
 */
interface AdService {
    /** Bu platformda reklam altyapısı var mı (masaüstünde yok)? Yoksa reklamla ilgili hiçbir arayüz gösterilmez. */
    fun supported(): Boolean = true

    /** Ödüllü reklam şu anda gösterilebilir mi (yüklenmiş ve onay koşulları sağlanmış). */
    fun rewardedReady(): Boolean

    /** Ödüllü reklamı gösterir. [onResult]: oyuncu ödülü hak ettiyse true. */
    fun showRewarded(onResult: (earned: Boolean) -> Unit)

    fun interstitialReady(): Boolean

    /** Geçiş reklamını gösterir; kapanınca (ya da gösterilemeyince) [onClosed] çağrılır. */
    fun showInterstitial(onClosed: () -> Unit)

    /** Geçiş reklamı yakında gerekebilir: platform şimdiden yüklemeye başlasın (boşa istek atmamak için tembel yükleme). */
    fun prepareInterstitial() {}

    /** Oyuncu oyunun sesini tamamen kapattıysa reklamlar da sessiz başlar. */
    fun setAppMuted(muted: Boolean) {}

    /**
     * Afiş (banner) reklamı gösterir ya da gizler. Yalnızca içeriği kaydırılabilen menü ekranlarında açılır; oyun
     * sırasında asla (tek dokunuşla oynanan oyunda afişe yanlışlıkla basılır).
     */
    fun setBannerVisible(visible: Boolean) {}

    /** Görünen afişin ekranın altında kapladığı yükseklik (piksel); afiş yoksa ya da henüz yüklenmediyse 0. */
    fun bannerHeightPx(): Int = 0

    /** Reklamlar çocuklara uygun modda mı isteniyor (kişiselleştirme ve reklam kimliği yok)? Gizlilik metni buna göre yazılır. */
    fun childDirected(): Boolean = true

    /** Reklam gizlilik tercihleri formu sunulmalı mı (ör. AB/Birleşik Krallık kullanıcıları). */
    fun privacyOptionsRequired(): Boolean

    fun showPrivacyOptions()

    companion object {
        /** Reklamsız platform: hiçbir reklam hazır değildir, bütün reklam düğmeleri gizli kalır. */
        val NONE = object : AdService {
            override fun supported() = false
            override fun rewardedReady() = false
            override fun showRewarded(onResult: (Boolean) -> Unit) = onResult(false)
            override fun interstitialReady() = false
            override fun showInterstitial(onClosed: () -> Unit) = onClosed()
            override fun privacyOptionsRequired() = false
            override fun showPrivacyOptions() {}
        }
    }
}

/**
 * Reklamların NE ZAMAN gösterileceğine karar veren, oyuncuyu yormamak için sınırları uygulayan katman.
 *
 * İlkeler:
 *  - Ödüllü reklamlar her zaman isteğe bağlıdır: oyuncu açıkça yazılı bir düğmeye basmadıkça gösterilmez.
 *  - Geçiş reklamı yalnızca doğal arada (bölüm TAMAMLANINCA, kutlama bittikten sonra ve sonuç ekranı açılmadan önce)
 *    ve seyrek gösterilir. Bir düğmeye basıldıktan sonra, oyun sırasında, açılışta, çıkışta, başarısız denemeden
 *    sonra ya da Sonsuz Dünya'da asla gösterilmez (Google Play "beklenmedik reklam" kuralı ve AdMob yerleşim önerisi).
 *  - Reklam yoksa (çevrimdışı, yüklenemedi) oyun aynen çalışır; reklam düğmeleri görünmez.
 */
class AdDirector(
    private val service: AdService,
    private val profile: ProfileService,
    /** Tekdüze (geri gitmeyen) saat: cihaz saatinin değişmesi reklam aralıklarını etkilemez. */
    private val now: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    companion object {
        /** Mağazadaki "reklam izle, elmas kazan" ödülü ve günlük sınırı. */
        const val GEMS_PER_AD = 3
        const val GEM_ADS_PER_DAY = 5
        /** Geçiş reklamı en erken kaç bölüm tamamlandıktan sonra başlar (ilk bölümler reklamsızdır). */
        const val INTERSTITIAL_AFTER_LEVELS = 10
        /** İki geçiş reklamı arasında en az kaç doğal ara (tamamlanan bölüm) ve kaç milisaniye olmalı. */
        const val INTERSTITIAL_EVERY_BREAKS = 3
        const val INTERSTITIAL_MIN_GAP_MS = 180_000L
        /** Oturumun ilk dakikalarında geçiş reklamı gösterilmez; izlenen her ödüllü reklam bekleme süresini yeniden başlatır. */
        const val SESSION_GRACE_MS = 120_000L
        const val AFTER_REWARDED_GAP_MS = 180_000L
        /** Uygulama bu kadar süre arka planda kaldıysa dönüş yeni oturum sayılır. */
        const val NEW_SESSION_AFTER_BACKGROUND_MS = 60_000L
    }

    private var sessionStart = now()
    private var pausedAt = -1L
    private var lastInterstitialAt = Long.MIN_VALUE / 2
    private var lastRewardedAt = Long.MIN_VALUE / 2
    private var breaksSinceInterstitial = 0

    private val data get() = profile.data

    /** Reklam altyapısı olan bir platformda mıyız (reklamla ilgili arayüz yalnızca o zaman gösterilir)? */
    val supported: Boolean get() = service.supported()

    fun onAppPaused() { pausedAt = now() }

    /** Uzun süre arka planda kalıp dönülürse yeni oturum başlar: ilk dakikalarda geçiş reklamı gösterilmez. */
    fun onAppResumed() {
        val p = pausedAt; pausedAt = -1L
        if (p >= 0 && now() - p >= NEW_SESSION_AFTER_BACKGROUND_MS) sessionStart = now()
    }

    // ------------------------------------------------------------------ ödüllü reklam

    /** Bu oturumda en az bir ödüllü reklam yüklendi mi (hiç yüklenemiyorsa, ör. çevrimdışı, reklam kartları gösterilmez). */
    private var rewardedSeen = false

    val rewardedReady: Boolean get() = service.rewardedReady().also { if (it) rewardedSeen = true }

    /** Ödüllü reklamı gösterir; ödül hak edildiyse [onEarned], edilmediyse (kapatıldı / gösterilemedi) [onSkipped]. */
    fun showRewarded(onEarned: () -> Unit, onSkipped: () -> Unit = {}) {
        if (!service.rewardedReady()) { onSkipped(); return }
        service.setAppMuted(muted())
        service.showRewarded { earned ->
            if (earned) {
                lastRewardedAt = now()
                data.ads.rewardedTotal++
                onEarned()
            } else onSkipped()
        }
    }

    // ------------------------------------------------------------------ geçiş reklamı

    private fun muted() = !data.settings.sound && !data.settings.music

    /** Bir bölüm tamamlandı: doğal ara sayacı ilerler; sıradaki arada reklam gösterilebilecekse yükleme başlatılır. */
    fun onBreakEarned() {
        breaksSinceInterstitial++
        if (data.stats.levelsCompleted + 1 >= INTERSTITIAL_AFTER_LEVELS && breaksSinceInterstitial + 1 >= INTERSTITIAL_EVERY_BREAKS) service.prepareInterstitial()
    }

    /** Şu anda (doğal bir arada) geçiş reklamı göstermek kurallara uygun mu? */
    fun interstitialDue(): Boolean {
        val t = now()
        return service.interstitialReady() &&
            data.stats.levelsCompleted >= INTERSTITIAL_AFTER_LEVELS &&
            breaksSinceInterstitial >= INTERSTITIAL_EVERY_BREAKS &&
            t - lastInterstitialAt >= INTERSTITIAL_MIN_GAP_MS &&
            t - sessionStart >= SESSION_GRACE_MS &&
            t - lastRewardedAt >= AFTER_REWARDED_GAP_MS
    }

    /**
     * Doğal arada çağrılır (bölüm tamamlandı, sonuç ekranı açılmak üzere). Koşullar uygunsa önce geçiş reklamı
     * gösterilir, kapanınca [then] çalışır; değilse [then] hemen çalışır.
     */
    fun atBreak(then: () -> Unit) {
        if (!interstitialDue()) { then(); return }
        lastInterstitialAt = now()
        breaksSinceInterstitial = 0
        data.ads.interstitialTotal++
        service.setAppMuted(muted())
        service.showInterstitial(then)
    }

    // ------------------------------------------------------------------ günlük sınırlar

    /** Günlük sayaç yalnızca YENİ bir güne geçilince sıfırlanır; cihaz tarihini geri almak sınırı sıfırlamaz. */
    private fun rollDay() {
        val today = profile.clock.epochDay()
        if (today > data.ads.day) { data.ads.day = today; data.ads.gemAdsToday = 0; data.ads.lifeAdsToday = 0 }
    }

    /** Bugün "reklam izle, elmas kazan" kaç kez daha kullanılabilir. */
    fun gemAdsLeft(): Int { rollDay(); return (GEM_ADS_PER_DAY - data.ads.gemAdsToday).coerceAtLeast(0) }

    fun gemAdAvailable(): Boolean = gemAdsLeft() > 0 && service.rewardedReady()

    /** Mağazadaki ücretsiz elmas kartı gösterilsin mi (reklam o an hazır olmasa da; düğme hazır olunca açılır)? */
    fun gemCardVisible(): Boolean = service.supported() && gemAdsLeft() > 0 && (rewardedReady || rewardedSeen)

    /** Mağazadaki reklam ödülünü verir (reklam tamamlandıktan sonra çağrılır). Verilen elmas; sınır dolduysa 0. */
    fun grantGemAd(): Int {
        if (gemAdsLeft() <= 0) return 0
        data.ads.gemAdsToday++
        data.gems += GEMS_PER_AD
        profile.save()
        return GEMS_PER_AD
    }

    /** Bugün "reklam izle: +1 can" kaç kez daha kullanılabilir. */
    fun lifeAdsLeft(): Int { rollDay(); return (com.besaniyelik.dunya.GameConfig.LIFE_ADS_PER_DAY - data.ads.lifeAdsToday).coerceAtLeast(0) }

    /** Canlar penceresinde "reklam izle: +1 can" düğmesi gösterilsin mi (canlar dolu değilken, hak varken, reklam hazırken)? */
    fun lifeAdOffer(livesFull: Boolean): Boolean = service.supported() && !livesFull && lifeAdsLeft() > 0 && rewardedReady

    /** Reklam tamamlandıktan sonra can ödülünü verir (en fazla can sınırına kadar). Verildiyse true. */
    fun grantLifeAd(lives: com.besaniyelik.dunya.progress.LifeService): Boolean {
        if (lifeAdsLeft() <= 0 || lives.full) return false
        data.ads.lifeAdsToday++
        lives.add(com.besaniyelik.dunya.GameConfig.LIFE_AD_REWARD)
        return true
    }

    /** Bugün alınmış olan giriş ödülü (reklam düğmesinde ödülü açıkça yazmak için). */
    fun todaysLoginReward(): LoginReward = Catalog.loginRewards[(data.login.cycleDay + 6) % 7]

    /** Bugün alınan günlük giriş ödülü reklamla ikiye katlanabilir mi (günde bir kez)? */
    fun loginDoubleAvailable(): Boolean {
        val today = profile.clock.epochDay()
        return data.login.lastClaimDay == today && data.ads.loginDoubledDay != today && service.rewardedReady()
    }

    /** Bugünkü giriş ödülünü bir kez daha verir (reklam tamamlandıktan sonra çağrılır). Verilen ödül ya da null. */
    fun grantLoginDouble(): LoginReward? {
        val today = profile.clock.epochDay()
        if (data.login.lastClaimDay != today || data.ads.loginDoubledDay == today) return null
        val r = Catalog.loginRewards[(data.login.cycleDay + 6) % 7] // az önce alınan ödül
        data.coins += r.coins; data.gems += r.gems
        r.powerups.forEach { (k, v) -> data.inventory[k] = (data.inventory[k] ?: 0) + v }
        data.ads.loginDoubledDay = today
        profile.save()
        return r
    }

    /** Bölüm sonunda toplanan altınları ikiye katlama ödülü (reklam tamamlandıktan sonra çağrılır). */
    fun grantBonusCoins(coins: Int) {
        if (coins <= 0) return
        data.coins += coins
        profile.save()
    }

    // ------------------------------------------------------------------ gizlilik

    // ------------------------------------------------------------------ afiş

    /** Afiş yalnızca ekran izin veriyorsa görünür (bkz. BaseScreen.bannerAllowed). */
    fun setBanner(visible: Boolean) = service.setBannerVisible(visible)
    val bannerHeightPx: Int get() = service.bannerHeightPx()

    val childDirected: Boolean get() = service.childDirected()
    val privacyOptionsRequired: Boolean get() = service.privacyOptionsRequired()
    fun showPrivacyOptions() = service.showPrivacyOptions()
}
