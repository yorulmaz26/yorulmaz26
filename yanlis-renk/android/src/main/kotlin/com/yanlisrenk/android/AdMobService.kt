package com.yanlisrenk.android

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.View
import com.badlogic.gdx.Gdx
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.yanlisrenk.game.AdService
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Google AdMob reklamları. Reklam birim kimlikleri android/build.gradle.kts içindeki
 * gradle özelliklerinden gelir (varsayılan: Google'ın resmî test kimlikleri).
 * Akışı bozmamak için reklamların ne zaman gösterileceğine oyun (AdPolicy) karar verir;
 * bu sınıf yalnızca yükler ve gösterir.
 */
class AdMobService(private val activity: Activity, private val banner: AdView) : AdService {
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var bannerLoaded = false
    @Volatile private var bannerWanted = false
    @Volatile private var bannerPx = 0
    @Volatile private var interstitial: InterstitialAd? = null
    @Volatile private var rewarded: RewardedAd? = null
    private val started = AtomicBoolean(false)
    private lateinit var consent: ConsentInformation

    /** İzin (AB/BK için UMP) → MobileAds başlat → reklamları yükle. */
    fun start() {
        consent = UserMessagingPlatform.getConsentInformation(activity)
        val params = ConsentRequestParameters.Builder().build()
        consent.requestConsentInfoUpdate(activity, params, {
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { if (consent.canRequestAds()) initAds() }
        }, { if (consent.canRequestAds()) initAds() })
        if (consent.canRequestAds()) initAds()
    }

    private fun initAds() {
        if (!started.compareAndSet(false, true)) return
        // aile dostu içerik: yalnızca genel izleyiciye uygun reklamlar
        MobileAds.setRequestConfiguration(
            RequestConfiguration.Builder().setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_G).build()
        )
        MobileAds.initialize(activity) {
            main.post { loadBanner(); loadInterstitial(); loadRewarded() }
        }
    }

    private fun request() = AdRequest.Builder().build()

    private fun loadBanner() {
        val dm = DisplayMetrics(); @Suppress("DEPRECATION") activity.windowManager.defaultDisplay.getMetrics(dm)
        val widthDp = (dm.widthPixels / dm.density).toInt()
        val size = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, widthDp)
        banner.adUnitId = BuildConfig.ADMOB_BANNER
        banner.setAdSize(size)
        banner.adListener = object : AdListener() {
            override fun onAdLoaded() { bannerLoaded = true; bannerPx = size.getHeightInPixels(activity); applyBanner() }
            override fun onAdFailedToLoad(e: LoadAdError) { bannerLoaded = false; applyBanner(); main.postDelayed({ banner.loadAd(request()) }, 60_000) }
        }
        banner.loadAd(request())
    }

    private fun applyBanner() = main.post { banner.visibility = if (bannerWanted && bannerLoaded) View.VISIBLE else View.GONE }

    override val bannerHeightPx: Int get() = if (bannerWanted && bannerLoaded) bannerPx else 0

    override fun setBannerVisible(visible: Boolean) { bannerWanted = visible; applyBanner() }

    private fun loadInterstitial() {
        InterstitialAd.load(activity, BuildConfig.ADMOB_INTERSTITIAL, request(), object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) { interstitial = ad }
            override fun onAdFailedToLoad(e: LoadAdError) { interstitial = null; main.postDelayed({ loadInterstitial() }, 60_000) }
        })
    }

    override fun isInterstitialReady() = interstitial != null

    override fun showInterstitial(onClosed: () -> Unit) {
        val ad = interstitial ?: run { onClosed(); return }
        interstitial = null
        main.post {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() { Gdx.app.postRunnable(onClosed); loadInterstitial() }
                override fun onAdFailedToShowFullScreenContent(e: com.google.android.gms.ads.AdError) { Gdx.app.postRunnable(onClosed); loadInterstitial() }
            }
            ad.show(activity)
        }
    }

    private fun loadRewarded() {
        RewardedAd.load(activity, BuildConfig.ADMOB_REWARDED, request(), object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) { rewarded = ad }
            override fun onAdFailedToLoad(e: LoadAdError) { rewarded = null; main.postDelayed({ loadRewarded() }, 60_000) }
        })
    }

    override fun isRewardedReady() = rewarded != null

    override fun showRewarded(onResult: (Boolean) -> Unit) {
        val ad = rewarded ?: run { onResult(false); return }
        rewarded = null
        main.post {
            var earned = false
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() { val e = earned; Gdx.app.postRunnable { onResult(e) }; loadRewarded() }
                override fun onAdFailedToShowFullScreenContent(err: com.google.android.gms.ads.AdError) { Gdx.app.postRunnable { onResult(false) }; loadRewarded() }
            }
            ad.show(activity) { earned = true }
        }
    }

    override val privacyOptionsRequired: Boolean
        get() = ::consent.isInitialized && consent.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    override fun showPrivacyOptions() { main.post { UserMessagingPlatform.showPrivacyOptionsForm(activity) {} } }

    fun pause() = banner.pause()
    fun resume() = banner.resume()
    fun destroy() = banner.destroy()
}
