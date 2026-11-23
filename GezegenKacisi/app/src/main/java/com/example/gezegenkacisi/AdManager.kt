package com.example.gezegenkacisi

import android.app.Activity
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
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
                if (activity.isFinishing || activity.isDestroyed) return@post
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

    private fun createBanner() {
        val dm = activity.resources.displayMetrics
        val widthDp = (dm.widthPixels / dm.density).toInt()
        val view = AdView(activity).apply {
            adUnitId = activity.getString(R.string.ad_banner_id)
            setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, widthDp))
            visibility = View.GONE
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    bannerLoaded = true
                    applyBannerVisibility()
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    bannerLoaded = false
                    applyBannerVisibility()
                }
            }
        }
        container.addView(
            view,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            )
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
        get() {
            val b = banner ?: return 0
            if (!bannerWanted || !bannerLoaded) return 0
            return b.adSize?.getHeightInPixels(activity) ?: 0
        }

    // ---------------------------------------------------------------------
    // Geçiş (resim) reklamı
    // ---------------------------------------------------------------------

    private fun loadInterstitial() {
        if (!ready || interstitial != null || interstitialLoading) return
        interstitialLoading = true
        InterstitialAd.load(
            activity, activity.getString(R.string.ad_interstitial_id), AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialLoading = false
                    interstitial = ad
                    interstitialRetryMs = 15_000L
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialLoading = false
                    interstitial = null
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
        if (!ready || rewarded != null || rewardedLoading) return
        rewardedLoading = true
        RewardedAd.load(
            activity, activity.getString(R.string.ad_rewarded_id), AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedLoading = false
                    rewarded = ad
                    rewardedRetryMs = 15_000L
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedLoading = false
                    rewarded = null
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
        main.removeCallbacksAndMessages(null)
        banner?.destroy()
        banner = null
    }
}
