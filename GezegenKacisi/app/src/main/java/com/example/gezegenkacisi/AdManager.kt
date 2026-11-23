package com.example.gezegenkacisi

import android.app.Activity
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Google AdMob reklamları: alttaki banner, geçiş (resim) reklamı ve ödüllü video.
 *
 * - Reklam birimi kimlikleri app/build.gradle.kts içinde (şu an Google'ın TEST kimlikleri; yayından önce değiştirin).
 * - AB/İngiltere için Google'ın onay penceresi (UMP) reklamlardan önce gösterilir; izin yoksa reklam istenmez.
 * - Reklamlar yüklenemezse oyun hiç etkilenmez: düğmeler gizli kalır, geçiş reklamı atlanır.
 */
class AdManager(private val activity: Activity, private val container: FrameLayout) : AdsBridge {

    private val main = Handler(Looper.getMainLooper())
    private val consent: ConsentInformation = UserMessagingPlatform.getConsentInformation(activity)
    private val initStarted = AtomicBoolean(false)
    @Volatile private var ready = false

    private var banner: AdView? = null
    private var bannerLoaded = false
    private var bannerWanted = false
    private var bannerWidthDp = 0
    private var navInsetPx = 0              // altta dokunulabilir gezinme düğmeleri varsa yükseklikleri
    private var bannerRetryMs = 15_000L
    @Volatile private var destroyed = false
    private var interstitial: InterstitialAd? = null
    private var rewarded: RewardedAd? = null
    private var interstitialLoading = false
    private var rewardedLoading = false
    private var interstitialRetryMs = 15_000L
    private var rewardedRetryMs = 15_000L

    /** Robolectric gibi test ortamlarında reklam kütüphanesi hiç başlatılmaz. */
    private val disabled = Build.FINGERPRINT == "robolectric"

    fun start() {
        if (disabled) return
        // Banner, gizli de olsa açılabilen gezinme düğmelerinin (geri/ana ekran) üstünde durur:
        // kaydırınca beliren çubuğa basmak isterken reklama dokunulmasın. Hareketle gezinmede bu yükseklik 0'dır.
        container.setOnApplyWindowInsetsListener { v, insets ->
            val bottom = tappableBottom(insets)
            if (bottom != navInsetPx) {
                navInsetPx = bottom
                banner?.let { applyBannerMargin(it) }
            }
            v.onApplyWindowInsets(insets)
        }
        container.requestApplyInsets()
        val params = ConsentRequestParameters.Builder().build()
        consent.requestConsentInfoUpdate(
            activity, params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { initAds() }
            },
            { initAds() } // onay bilgisi alınamadı: daha önceki izin geçerliyse yine de başlar
        )
        // Önceki oturumdan izin varsa beklemeden başla
        if (consent.canRequestAds()) initAds()
    }

    private fun initAds() {
        if (disabled || !consent.canRequestAds()) return
        if (!initStarted.compareAndSet(false, true)) return
        thread(name = "ads-init", isDaemon = true) {
            MobileAds.initialize(activity) {}
            main.post {
                if (destroyed || activity.isFinishing || activity.isDestroyed) return@post
                ready = true
                createBanner()
                loadInterstitial()
                loadRewarded()
            }
        }
    }

    // ---------------------------------------------------------------------
    // Banner
    // ---------------------------------------------------------------------

    private fun tappableBottom(insets: WindowInsets): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            insets.getInsetsIgnoringVisibility(WindowInsets.Type.tappableElement()).bottom
        } else {
            @Suppress("DEPRECATION")
            insets.stableInsetBottom
        }

    private fun applyBannerMargin(view: AdView) {
        val lp = view.layoutParams as? FrameLayout.LayoutParams ?: return
        if (lp.bottomMargin == navInsetPx) return
        lp.bottomMargin = navInsetPx
        view.layoutParams = lp
    }

    private fun screenWidthDp(): Int {
        val dm = activity.resources.displayMetrics
        return (dm.widthPixels / dm.density).toInt()
    }

    private fun createBanner() {
        if (destroyed || banner != null) return
        val widthDp = screenWidthDp()
        bannerWidthDp = widthDp
        bannerLoaded = false
        val view = AdView(activity)
        view.adUnitId = activity.getString(R.string.ad_banner_id)
        view.setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, widthDp))
        view.visibility = View.GONE
        view.adListener = object : AdListener() {
            override fun onAdLoaded() {
                if (banner !== view) return
                bannerLoaded = true
                bannerRetryMs = 15_000L
                applyBannerVisibility()
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                if (banner !== view || destroyed) return
                // Yenileme başarısız olsa da önceki reklam ekranda geçerli kalır; banner gizlenmez.
                // Hiç yüklenememişse (çevrimdışı açılış, reklam yok) artan aralıklarla yeniden denenir.
                if (!bannerLoaded) {
                    main.postDelayed({ if (banner === view && !bannerLoaded) view.loadAd(AdRequest.Builder().build()) }, bannerRetryMs)
                    bannerRetryMs = (bannerRetryMs * 2).coerceAtMost(300_000L)
                }
            }
        }
        container.addView(
            view,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            ).apply { bottomMargin = navInsetPx }
        )
        banner = view
        view.loadAd(AdRequest.Builder().build())
    }

    private fun applyBannerVisibility() {
        banner?.visibility = if (bannerWanted && bannerLoaded) View.VISIBLE else View.GONE
    }

    override fun setBannerVisible(visible: Boolean) {
        if (bannerWanted == visible) return
        bannerWanted = visible
        applyBannerVisibility()
    }

    override val bannerHeightPx: Int
        get() = if (bannerWanted) bannerSlotPx else 0

    override val bannerSlotPx: Int
        get() {
            val b = banner ?: return 0
            if (!bannerLoaded) return 0
            // Banner'ın altındaki gezinme düğmesi şeridi de ayrılır (oyunun yazıları ikisinin de üstünde kalır)
            return (b.adSize?.getHeightInPixels(activity) ?: 0) + navInsetPx
        }

    /**
     * Ekran boyutu ya da yoğunluğu değişince (katlanabilir telefon, ekran boyutu ayarı) etkinlik yeniden
     * başlatılmaz; uyarlanabilir banner yeni genişliğe göre baştan kurulur.
     */
    fun onConfigurationChanged() {
        if (destroyed || !ready) return
        val old = banner ?: return
        if (screenWidthDp() == bannerWidthDp) return
        container.removeView(old)
        old.destroy()
        banner = null
        bannerLoaded = false
        bannerRetryMs = 15_000L
        createBanner()
    }

    // ---------------------------------------------------------------------
    // Geçiş (resim) reklamı
    // ---------------------------------------------------------------------

    private fun loadInterstitial() {
        if (destroyed || !ready || interstitial != null || interstitialLoading) return
        interstitialLoading = true
        InterstitialAd.load(
            activity, activity.getString(R.string.ad_interstitial_id), AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialLoading = false
                    if (destroyed) return
                    interstitial = ad
                    interstitialRetryMs = 15_000L
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialLoading = false
                    interstitial = null
                    if (destroyed) return
                    main.postDelayed({ loadInterstitial() }, interstitialRetryMs)
                    interstitialRetryMs = (interstitialRetryMs * 2).coerceAtMost(300_000L)
                }
            }
        )
    }

    override fun interstitialReady() = interstitial != null

    override fun showInterstitial(onDone: () -> Unit): Boolean {
        val ad = interstitial ?: return false
        interstitial = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                onDone()
                loadInterstitial()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                onDone()
                loadInterstitial()
            }
        }
        ad.show(activity)
        return true
    }

    // ---------------------------------------------------------------------
    // Ödüllü video
    // ---------------------------------------------------------------------

    private fun loadRewarded() {
        if (destroyed || !ready || rewarded != null || rewardedLoading) return
        rewardedLoading = true
        RewardedAd.load(
            activity, activity.getString(R.string.ad_rewarded_id), AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedLoading = false
                    if (destroyed) return
                    rewarded = ad
                    rewardedRetryMs = 15_000L
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedLoading = false
                    rewarded = null
                    if (destroyed) return
                    main.postDelayed({ loadRewarded() }, rewardedRetryMs)
                    rewardedRetryMs = (rewardedRetryMs * 2).coerceAtMost(300_000L)
                }
            }
        )
    }

    override fun rewardedReady() = rewarded != null

    override fun showRewarded(onReward: () -> Unit, onDone: () -> Unit): Boolean {
        val ad = rewarded ?: return false
        rewarded = null
        var earned = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                if (earned) onReward()
                onDone()
                loadRewarded()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                onDone()
                loadRewarded()
            }
        }
        ad.show(activity) { earned = true }
        return true
    }

    // ---------------------------------------------------------------------
    // Gizlilik ayarları ve yaşam döngüsü
    // ---------------------------------------------------------------------

    override fun privacyOptionsRequired() = !disabled &&
        consent.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    override fun showPrivacyOptions() {
        if (disabled) return
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { initAds() }
    }

    fun onResume() {
        banner?.resume()
    }

    fun onPause() {
        banner?.pause()
    }

    fun onDestroy() {
        destroyed = true
        ready = false
        main.removeCallbacksAndMessages(null)
        banner?.destroy()
        banner = null
        interstitial = null
        rewarded = null
    }
}
