package com.besaniyelik.dunya.android

import android.app.Activity
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidGraphics
import com.besaniyelik.dunya.BuildConfig
import com.besaniyelik.dunya.ads.AdService
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.AgeRestrictedTreatment
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.OnUserEarnedRewardListener
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Google Mobile Ads (AdMob) ile [AdService] gerçeklemesi. Bütün reklam kodu bu dosyadadır; oyun çekirdeği yalnızca
 * [AdService] arayüzünü bilir (ileride başka bir reklam kitaplığına geçmek tek dosyalık iştir).
 *
 * Kurallar:
 *  - Google Mobile Ads çağrıları arayüz (UI) iş parçacığında yapılır; oyuna dönen sonuçlar `Gdx.app.postRunnable`
 *    ile oyun (GL) iş parçacığına taşınır ve her gösterim için tam bir kez bildirilir.
 *  - Sıra: onay bilgisi (UMP) -> gerekiyorsa onay formu -> `canRequestAds()` ise SDK başlatılır ve reklam yüklenir.
 *  - Reklam nesneleri tek kullanımlıktır; kapandıktan sonra yenisi yüklenir. Yükleme başarısız olursa artan
 *    aralıklarla birkaç kez yeniden denenir, sonra yalnızca oyun reklam sorduğunda (seyrek) denenir.
 *  - Hiçbir hata oyunu durdurmaz: reklam yoksa düğmeler gizlenir, geçiş reklamı atlanır.
 */
class AndroidAds(private val activity: Activity) : AdService {
    private companion object {
        const val TAG = "Reklam"
        /** Yüklenen reklam yaklaşık bir saat geçerlidir; daha eskisi gösterilmez, yenisi yüklenir. */
        const val EXPIRY_MS = 55 * 60_000L
        /** Yeniden deneme aralıkları (başarısız yüklemeden sonra); bitince yalnızca istek geldikçe denenir. */
        val BACKOFF_MS = longArrayOf(30_000L, 60_000L, 120_000L, 240_000L)
        const val LAZY_RETRY_MS = 60_000L
        /**
         * Güvenlik ağı: reklam ekrana hiç gelmezse ya da kapanış bildirimi ulaşmazsa oyun askıda kalmaz. Ödüllü
         * reklamda süre uzundur (oyuncu kendi istedi; geç açılan reklamın ödülü kaybolmasın), geçiş reklamında kısadır
         * (sonuç ekranı bekletilmesin).
         */
        const val REWARDED_SHOW_TIMEOUT_MS = 10_000L
        const val INTERSTITIAL_SHOW_TIMEOUT_MS = 4_000L
        const val RESUME_GRACE_MS = 2_500L
        /** Ödüllü reklam kapandıktan sonra ödül bildirimi için beklenen kısa süre. */
        const val REWARD_SETTLE_MS = 300L
    }

    /** Bir reklam türünün (ödüllü / geçiş) yükleme durumu. Alanlar yalnızca UI iş parçacığında değişir. */
    private class Slot<T : Any>(val name: String) {
        var ad: T? = null
        var loading = false
        var failures = 0
        var lastAttempt = 0L
        @Volatile var loadedAt = 0L
        /** Oyun iş parçacığından okunur. */
        @Volatile var available = false
        fun fresh() = available && SystemClock.elapsedRealtime() - loadedAt < EXPIRY_MS
    }

    /** Gösterilmekte olan tam ekran reklam; [complete] sonucu oyuna tam bir kez bildirir. */
    private class Pending(val complete: () -> Unit) { var displayed = false }

    private val ui = Handler(Looper.getMainLooper())
    private val consent: ConsentInformation = UserMessagingPlatform.getConsentInformation(activity)
    private val initCalled = AtomicBoolean(false)
    private val childAudience = BuildConfig.ADS_CHILD_AUDIENCE

    private var sdkReady = false
    private val rewarded = Slot<RewardedAd>("ödüllü")
    private val interstitial = Slot<InterstitialAd>("geçiş")
    private var interstitialWanted = false
    private var pending: Pending? = null

    @Volatile private var privacyRequired = false
    @Volatile private var muted = false

    // ------------------------------------------------------------------ başlatma ve onay

    private var consentAttempts = 0
    private var consentInFlight = false

    /** Uygulama açılışında (UI iş parçacığında) bir kez çağrılır. */
    fun start() = requestConsent()

    /**
     * Onay bilgisi istenir. İlk açılışta ağ yoksa `canRequestAds()` false kalır ve hiçbir reklam yüklenemez; bu yüzden
     * başarısız istek 30 sn ve 2 dk sonra, ardından uygulama her öne geldiğinde yeniden denenir.
     */
    private fun requestConsent() {
        if (initCalled.get() || consentInFlight || activity.isFinishing) return
        consentInFlight = true; consentAttempts++
        try {
            // Çocukları da kapsayan kitlede onay formu gösterilmez ve reklamlar kişiselleştirilmez
            val params = ConsentRequestParameters.Builder().setTagForUnderAgeOfConsent(childAudience).build()
            consent.requestConsentInfoUpdate(
                activity, params,
                { consentInFlight = false; safely { UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { afterConsent() } } },
                { err ->
                    consentInFlight = false
                    Log.w(TAG, "onay bilgisi alınamadı: ${err.message}"); afterConsent()
                    if (!initCalled.get()) when (consentAttempts) {
                        1 -> ui.postDelayed({ requestConsent() }, 30_000L)
                        2 -> ui.postDelayed({ requestConsent() }, 120_000L)
                        else -> {} // sonraki denemeler onHostResumed() ile
                    }
                },
            )
        } catch (t: Throwable) { consentInFlight = false; Log.w(TAG, "onay akışı başlatılamadı", t) }
        // önceki oturumda alınmış onay varsa beklemeden başla
        afterConsent()
    }

    private fun afterConsent() = safely {
        privacyRequired = consent.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        if (consent.canRequestAds()) initSdk()
    }

    private fun initSdk() {
        if (initCalled.getAndSet(true)) return
        // Yaş ayarı ve içerik sınırı, ilk reklam isteğinden ÖNCE verilir.
        //  - Çocukları da kapsayan kitle: CHILD (eski adıyla "child-directed"): kişiselleştirme ve reklam kimliği kullanılmaz, içerik G.
        //  - Yalnızca 13 yaş üstü kitle: yaş ayarı belirtilmez, içerik sınırı PG.
        val cfg = RequestConfiguration.Builder()
            .setMaxAdContentRating(if (childAudience) RequestConfiguration.MAX_AD_CONTENT_RATING_G else RequestConfiguration.MAX_AD_CONTENT_RATING_PG)
        if (childAudience) cfg.setAgeRestrictedTreatment(AgeRestrictedTreatment.CHILD)
        val testDevices = BuildConfig.ADS_TEST_DEVICES.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (testDevices.isNotEmpty()) cfg.setTestDeviceIds(testDevices)
        MobileAds.setRequestConfiguration(cfg.build())
        Thread({
            try { MobileAds.initialize(activity.applicationContext) {} } catch (t: Throwable) { Log.w(TAG, "SDK başlatılamadı", t) }
            ui.post { sdkReady = true; ensureRewarded(); if (interstitialWanted) ensureInterstitial(); safely { updateBanner() } }
        }, "reklam-baslat").start()
    }

    private fun canLoad() = sdkReady && consent.canRequestAds()

    // ------------------------------------------------------------------ yükleme

    /** Yükleme gerekli mi ve şimdi denenebilir mi (UI iş parçacığı)? */
    private fun <T : Any> shouldLoad(slot: Slot<T>, forced: Boolean): Boolean {
        if (!canLoad() || slot.loading) return false
        if (slot.ad != null && slot.fresh()) return false
        if (slot.ad != null) { slot.ad = null; slot.available = false } // süresi doldu
        val now = SystemClock.elapsedRealtime()
        if (!forced && slot.failures > 0 && now - slot.lastAttempt < LAZY_RETRY_MS) return false
        slot.loading = true; slot.lastAttempt = now
        return true
    }

    private fun <T : Any> loaded(slot: Slot<T>, ad: T) {
        slot.ad = ad; slot.loadedAt = SystemClock.elapsedRealtime(); slot.loading = false; slot.failures = 0; slot.available = true
    }

    private fun <T : Any> failed(slot: Slot<T>, error: LoadAdError, retry: () -> Unit) {
        Log.w(TAG, "${slot.name} reklam yüklenemedi: ${error.code} ${error.message}")
        slot.ad = null; slot.available = false; slot.loading = false
        val i = slot.failures++
        // geri çağrının içinden hemen yeniden yüklemek yerine artan aralıklarla, sınırlı sayıda denenir
        if (i < BACKOFF_MS.size) ui.postDelayed({ safely(retry) }, BACKOFF_MS[i])
    }

    private fun ensureRewarded(forced: Boolean = false) {
        if (!shouldLoad(rewarded, forced)) return
        try {
            RewardedAd.load(activity, BuildConfig.AD_REWARDED_UNIT, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) = loaded(rewarded, ad)
                override fun onAdFailedToLoad(error: LoadAdError) = failed(rewarded, error) { ensureRewarded(forced = true) }
            })
        } catch (t: Throwable) { rewarded.loading = false; Log.w(TAG, "ödüllü reklam istenemedi", t) }
    }

    private fun ensureInterstitial(forced: Boolean = false) {
        if (!shouldLoad(interstitial, forced)) return
        try {
            InterstitialAd.load(activity, BuildConfig.AD_INTERSTITIAL_UNIT, AdRequest.Builder().build(), object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) = loaded(interstitial, ad)
                override fun onAdFailedToLoad(error: LoadAdError) = failed(interstitial, error) { ensureInterstitial(forced = true) }
            })
        } catch (t: Throwable) { interstitial.loading = false; Log.w(TAG, "geçiş reklamı istenemedi", t) }
    }

    // ------------------------------------------------------------------ AdService (oyun iş parçacığından çağrılır)

    override fun rewardedReady(): Boolean {
        val ok = rewarded.fresh()
        if (!ok) ui.post { safely { ensureRewarded() } } // hazır değilse arka planda (seyrek) yeniden dene
        return ok
    }

    override fun showRewarded(onResult: (Boolean) -> Unit) {
        val reported = AtomicBoolean(false)
        var earned = false
        val report = { if (!reported.getAndSet(true)) { val e = earned; Gdx.app.postRunnable { onResult(e) } } }
        ui.post {
            val ad = rewarded.ad
            if (ad == null || !rewarded.fresh() || pending != null) { report(); safely { ensureRewarded() }; return@post }
            val p = Pending(report); pending = p
            rewarded.ad = null; rewarded.available = false // reklam nesnesi tek kullanımlıktır
            try {
                ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdShowedFullScreenContent() { p.displayed = true }
                    override fun onAdDismissedFullScreenContent() {
                        // ödül bildirimi kapanıştan hemen sonra gelebilir: sonucu kısa bir gecikmeyle bildir
                        ui.postDelayed({ finish(p) }, REWARD_SETTLE_MS)
                        safely { ensureRewarded(forced = true) }
                    }
                    override fun onAdFailedToShowFullScreenContent(error: AdError) {
                        Log.w(TAG, "ödüllü reklam gösterilemedi: ${error.message}")
                        finish(p); safely { ensureRewarded(forced = true) }
                    }
                }
                ad.setImmersiveMode(true)
                MobileAds.setAppMuted(muted)
                // ödül yalnızca Google "hak edildi" bildirdiğinde verilir; reklam erken kapatılırsa verilmez
                ad.show(activity, OnUserEarnedRewardListener { earned = true })
                watchdog(p, REWARDED_SHOW_TIMEOUT_MS)
            } catch (t: Throwable) { Log.w(TAG, "ödüllü reklam açılamadı", t); finish(p) }
        }
    }

    /** Geçiş reklamları ads.properties içindeki `interstitials=off` ile tümüyle kapatılabilir (ödüllü reklamlar etkilenmez). */
    private val interstitialsEnabled = BuildConfig.ADS_INTERSTITIALS

    override fun interstitialReady(): Boolean = interstitialsEnabled && interstitial.fresh()

    override fun prepareInterstitial() {
        if (!interstitialsEnabled) return
        ui.post { interstitialWanted = true; safely { ensureInterstitial() } }
    }

    override fun showInterstitial(onClosed: () -> Unit) {
        val reported = AtomicBoolean(false)
        val report = { if (!reported.getAndSet(true)) Gdx.app.postRunnable { onClosed() } }
        ui.post {
            val ad = interstitial.ad
            if (!interstitialsEnabled || ad == null || !interstitial.fresh() || pending != null) { report(); return@post }
            val p = Pending(report); pending = p
            interstitial.ad = null; interstitial.available = false
            try {
                ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdShowedFullScreenContent() { p.displayed = true }
                    override fun onAdDismissedFullScreenContent() { finish(p) }
                    override fun onAdFailedToShowFullScreenContent(error: AdError) {
                        Log.w(TAG, "geçiş reklamı gösterilemedi: ${error.message}")
                        finish(p)
                    }
                }
                ad.setImmersiveMode(true)
                MobileAds.setAppMuted(muted)
                ad.show(activity)
                watchdog(p, INTERSTITIAL_SHOW_TIMEOUT_MS)
            } catch (t: Throwable) { Log.w(TAG, "geçiş reklamı açılamadı", t); finish(p) }
        }
    }

    override fun setAppMuted(muted: Boolean) { this.muted = muted }

    override fun childDirected(): Boolean = childAudience

    override fun privacyOptionsRequired(): Boolean = privacyRequired

    override fun showPrivacyOptions() {
        ui.post { safely { UserMessagingPlatform.showPrivacyOptionsForm(activity) { afterConsent() } } }
    }

    // ------------------------------------------------------------------ afiş (banner)

    /** Afişler ads.properties içindeki `banners=off` ile tümüyle kapatılabilir. */
    private val bannersEnabled = BuildConfig.ADS_BANNERS
    private var bannerView: AdView? = null
    private var bannerContainer: FrameLayout? = null
    private var bannerBuiltWidth = 0
    private var bannerWanted = false
    private var bannerLoaded = false
    /** Afiş yalnızca ekran geçişinde açılır: oyuncu bir listeye dokunurken altında birden belirmez. */
    private var bannerRevealed = false
    private var bannerFailures = 0
    private var bannerLastAttempt = 0L
    private var hostPaused = false
    /** Oyun iş parçacığından okunur: görünen afişin (boşluk payıyla) yüksekliği, piksel. */
    @Volatile private var bannerHeight = 0

    override fun setBannerVisible(visible: Boolean) {
        if (!bannersEnabled) return
        ui.post {
            bannerWanted = visible
            // ekran açılırken afiş zaten yüklüyse gösterilir; sonradan yüklenirse bir sonraki ekrana kadar gizli kalır
            bannerRevealed = visible && bannerLoaded
            safely { updateBanner() }
        }
    }

    override fun bannerHeightPx(): Int = bannerHeight

    /**
     * Afiş görünümünü (ilk istendiğinde) oluşturur ve görünürlüğünü günceller. Görünüm, libGDX'in oyun yüzeyinin
     * üstüne, ekranın alt kenarına eklenir; libGDX'in kendi başlatma yolu değişmez.
     */
    private fun updateBanner() {
        if (bannerWanted && bannerView == null && canLoad()) createBanner()
        // ilk yükleme başarısız olduysa ve otomatik denemeler bittiyse, afiş istendikçe seyrek olarak yeniden denenir
        val view = bannerView
        if (bannerWanted && view != null && !bannerLoaded && !view.isLoading && bannerFailures >= BACKOFF_MS.size &&
            SystemClock.elapsedRealtime() - bannerLastAttempt >= LAZY_RETRY_MS) loadBanner(view)
        applyBannerVisibility()
    }

    private fun createBanner() {
        val dm = activity.resources.displayMetrics
        val decor = activity.window.decorView
        var left = 0; var right = 0
        if (Build.VERSION.SDK_INT >= 28) decor.rootWindowInsets?.displayCutout?.let { left = it.safeInsetLeft; right = it.safeInsetRight }
        // çentik payı iki yana eşit verilir: telefon ters çevrilse de afiş çentiğin altına girmez
        val side = maxOf(left, right)
        val fullWidth = if (decor.width > 0) decor.width else dm.widthPixels
        val widthDp = ((fullWidth - 2 * side) / dm.density).toInt().coerceAtLeast(320)
        val view = AdView(activity)
        view.adUnitId = BuildConfig.AD_BANNER_UNIT
        view.setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, widthDp))
        view.adListener = object : AdListener() {
            override fun onAdLoaded() {
                bannerLoaded = true; bannerFailures = 0
                // zaten görünüyorsa (yenileme) dokunulmaz; görünmüyorsa bir sonraki ekran geçişinde açılır
                applyBannerVisibility()
            }
            override fun onAdFailedToLoad(error: LoadAdError) {
                Log.w(TAG, "afiş yüklenemedi: ${error.code} ${error.message}")
                if (bannerLoaded) return // yüklü afişin yenilemesi başarısız oldu: AdMob kendi aralığıyla yeniden dener
                val i = bannerFailures++
                if (i < BACKOFF_MS.size) ui.postDelayed({ safely { if (bannerView === view && !bannerLoaded && !view.isLoading) loadBanner(view) } }, BACKOFF_MS[i])
            }
        }
        val container = FrameLayout(activity)
        container.setPadding(side, 0, side, 0)
        container.visibility = View.GONE
        // Afiş odak almaz: aksi halde afişe dokunulduktan sonra geri tuşu oyuna ulaşmaz
        container.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        container.isFocusable = false; view.isFocusable = false
        container.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
        activity.addContentView(container, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        bannerView = view; bannerContainer = container; bannerBuiltWidth = fullWidth
        bannerLoaded = false; bannerRevealed = false; bannerFailures = 0
        // Pencere genişliği değişirse (katlanır cihaz, çoklu pencere) afiş yeni genişliğe göre yeniden kurulur
        container.addOnLayoutChangeListener { _, l, _, r, _, _, _, _, _ ->
            val w = r - l
            if (w > 0 && bannerContainer === container && kotlin.math.abs(w - bannerBuiltWidth) > 8) {
                // pencere sürüklenerek boyutlandırılırken her adımda yeniden kurmamak için istekler birleştirilir
                ui.removeCallbacks(bannerRebuildTask); ui.postDelayed(bannerRebuildTask, 300L)
            }
        }
        loadBanner(view)
    }

    private fun loadBanner(view: AdView) {
        bannerLastAttempt = SystemClock.elapsedRealtime()
        view.loadAd(AdRequest.Builder().build())
    }

    private val bannerRebuildTask = Runnable {
        safely {
            val c = bannerContainer
            if (c != null && c.width > 0 && kotlin.math.abs(c.width - bannerBuiltWidth) > 8) rebuildBanner()
        }
    }

    private fun rebuildBanner() {
        val old = bannerView ?: return
        val container = bannerContainer
        bannerView = null; bannerContainer = null; bannerHeight = 0
        (container?.parent as? ViewGroup)?.removeView(container)
        old.destroy()
        updateBanner()
    }

    private fun applyBannerVisibility() {
        val view = bannerView ?: run { bannerHeight = 0; return }
        val show = bannerWanted && bannerLoaded && bannerRevealed
        val wasVisible = bannerContainer?.visibility == View.VISIBLE
        bannerContainer?.visibility = if (show) View.VISIBLE else View.GONE
        // Yüklenmiş afiş gizliyken ya da uygulama arka plandayken yenilenmez; henüz hiç yüklenmemiş görünüm duraklatılmaz
        if (bannerLoaded) { if (show && !hostPaused) view.resume() else view.pause() }
        // oyun, afişin üstünde küçük bir boşluk bırakacak kadar yer ayırır (düğmeler afişe değmesin)
        val gap = (8 * activity.resources.displayMetrics.density).toInt()
        bannerHeight = if (show) (view.adSize?.getHeightInPixels(activity) ?: 0) + gap else 0
        if (wasVisible && !show) refocusGame()
    }

    /** Geri tuşu libGDX'e yalnızca oyun yüzeyi odaktayken ulaşır; afiş kapandığında odak geri verilir. */
    private fun refocusGame() {
        safely { ((activity as? AndroidApplication)?.graphics as? AndroidGraphics)?.view?.requestFocus() }
    }

    /** Etkinlik yaşam döngüsü: afiş arka planda yenilenmez, etkinlik kapanınca serbest bırakılır. */
    fun onHostPaused() { hostPaused = true; safely { if (bannerLoaded) bannerView?.pause() } }

    fun onHostDestroyed() {
        safely { bannerView?.destroy() }
        bannerView = null; bannerContainer = null; bannerHeight = 0
        ui.removeCallbacksAndMessages(null)
    }

    // ------------------------------------------------------------------ güvenlik ağı

    private fun finish(p: Pending) {
        if (pending === p) pending = null
        p.complete()
    }

    /** Reklam birkaç saniye içinde ekrana gelmediyse oyun beklemeden sürer. */
    private fun watchdog(p: Pending, timeoutMs: Long) {
        // Oyun etkinliği duraklamışsa reklam etkinliği öne geçmiştir: sonuç kapanış bildirimiyle ya da dönüşteki bekleme ile gelir
        ui.postDelayed({ if (pending === p && !p.displayed && !hostPaused) { Log.w(TAG, "reklam ekrana gelmedi; oyun sürdürülüyor"); finish(p) } }, timeoutMs)
    }

    /**
     * Oyun etkinliği yeniden öne geldi. Tam ekran reklam ayrı bir etkinlikte gösterildiği için bu, reklamın kapandığı
     * anlamına gelir; kapanış bildirimi kısa sürede gelmezse sonuç yine de oyuna iletilir.
     */
    fun onHostResumed() {
        hostPaused = false
        safely { applyBannerVisibility() }
        // ilk açılışta ağ yoktuysa onay bilgisi yeniden istenir (yoksa oturum boyunca hiç reklam yüklenmez)
        if (!initCalled.get() && consentAttempts >= 3) requestConsent()
        val p = pending ?: return
        ui.postDelayed({ if (pending === p) { Log.w(TAG, "kapanış bildirimi gelmedi; oyun sürdürülüyor"); finish(p) } }, RESUME_GRACE_MS)
    }

    private inline fun safely(block: () -> Unit) {
        try { block() } catch (t: Throwable) { Log.w(TAG, "reklam işlemi başarısız", t) }
    }
}
