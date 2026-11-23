package com.golgen.oyun.platform;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;


import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.RequestConfiguration;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Google AdMob ile banner, geçiş (görsel/video) ve ödüllü video reklamları.
 * Gizlilik onayı (AB/İngiltere) Google UMP ile alınır; onay yoksa reklam istenmez.
 * Reklam yüklenemezse (internet yok vb.) oyun hiçbir şey olmamış gibi devam eder; yüklemeler artan
 * aralıklarla yeniden denenir. Reklam birimi kimlikleri derleme ayarından gelir (varsayılan: Google test kimlikleri).
 */
public final class AdMobAds implements Ads {
    private static final String TAG = "GolgenAds";

    private final Activity activity;
    private final FrameLayout root;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final String bannerId, interstitialId, rewardedId;
    private final AtomicBoolean started = new AtomicBoolean(false);

    private AdView banner;
    private boolean bannerLoaded, bannerWanted;
    private int bannerHeightPx, bottomMargin;
    private InterstitialAd interstitial;
    private RewardedAd rewarded;
    private boolean interstitialLoading, rewardedLoading, showingFullScreen;
    private int bannerRetries, interstitialRetries, rewardedRetries;
    private boolean destroyed;

    public AdMobAds(Activity activity, FrameLayout root, Listener listener, String bannerId, String interstitialId, String rewardedId) {
        this.activity = activity;
        this.root = root;
        this.listener = listener;
        this.bannerId = bannerId;
        this.interstitialId = interstitialId;
        this.rewardedId = rewardedId;
        requestConsentThenStart();
    }

    // ---------------- Onay + başlatma ----------------
    private void requestConsentThenStart() {
        try {
            ConsentInformation ci = UserMessagingPlatform.getConsentInformation(activity);
            ConsentRequestParameters params = new ConsentRequestParameters.Builder()
                    .setTagForUnderAgeOfConsent(false).build();
            ci.requestConsentInfoUpdate(activity, params,
                    () -> UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity, formError -> {
                        if (formError != null) Log.w(TAG, "Onay formu: " + formError.getMessage());
                        if (ci.canRequestAds()) startSdk();
                    }),
                    requestError -> {
                        Log.w(TAG, "Onay bilgisi alınamadı: " + requestError.getMessage());
                        if (ci.canRequestAds()) startSdk();
                    });
            // Önceki oturumda onay alındıysa hemen başla
            if (ci.canRequestAds()) startSdk();
        } catch (RuntimeException e) {
            Log.w(TAG, "UMP kullanılamadı, doğrudan başlatılıyor", e);
            startSdk();
        }
    }

    private void startSdk() {
        if (!started.compareAndSet(false, true)) return;
        RequestConfiguration rc = MobileAds.getRequestConfiguration().toBuilder()
                .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_PG)
                .build();
        MobileAds.setRequestConfiguration(rc);
        new Thread(() -> MobileAds.initialize(activity, status -> main.post(() -> {
            if (destroyed) return;
            Log.i(TAG, "SDK hazır");
            createBanner();
            loadInterstitial();
            loadRewarded();
        })), "reklam-baslat").start();
    }

    private static long backoff(int retries) {
        return Math.min(5 * 60_000L, 15_000L * (1L << Math.min(4, retries)));
    }

    // ---------------- Banner ----------------
    private void createBanner() {
        banner = new AdView(activity);
        banner.setAdUnitId(bannerId);
        DisplayMetrics dm = activity.getResources().getDisplayMetrics();
        int widthDp = (int) (dm.widthPixels / dm.density);
        AdSize size = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, widthDp);
        banner.setAdSize(size);
        bannerHeightPx = size.getHeightInPixels(activity);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lp.bottomMargin = bottomMargin;
        banner.setLayoutParams(lp);
        banner.setVisibility(View.GONE);
        banner.setAdListener(new AdListener() {
            @Override public void onAdLoaded() {
                bannerLoaded = true;
                bannerRetries = 0;
                Log.i(TAG, "banner yüklendi");
                applyBanner();
            }

            @Override public void onAdFailedToLoad(LoadAdError e) {
                Log.w(TAG, "banner yüklenemedi: " + e.getCode() + " " + e.getMessage());
                long wait = backoff(bannerRetries++);
                main.postDelayed(() -> { if (!destroyed && banner != null) banner.loadAd(new AdRequest.Builder().build()); }, wait);
            }
        });
        root.addView(banner);
        banner.loadAd(new AdRequest.Builder().build());
    }

    private void applyBanner() {
        if (banner == null) return;
        boolean show = bannerWanted && bannerLoaded && !showingFullScreen;
        banner.setVisibility(show ? View.VISIBLE : View.GONE);
        if (listener != null) listener.onBannerHeightChanged(show ? bannerHeightPx : 0);
    }

    @Override public void setBannerWanted(boolean wanted) {
        if (bannerWanted == wanted) return;
        bannerWanted = wanted;
        applyBanner();
    }

    @Override public int bannerHeight() {
        return banner != null && banner.getVisibility() == View.VISIBLE ? bannerHeightPx : 0;
    }

    @Override public void setBottomMargin(int px) {
        bottomMargin = px;
        if (banner != null) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) banner.getLayoutParams();
            if (lp != null && lp.bottomMargin != px) {
                lp.bottomMargin = px;
                banner.setLayoutParams(lp);
            }
        }
    }

    // ---------------- Geçiş reklamı ----------------
    private void loadInterstitial() {
        if (destroyed || interstitial != null || interstitialLoading) return;
        interstitialLoading = true;
        InterstitialAd.load(activity, interstitialId, new AdRequest.Builder().build(), new InterstitialAdLoadCallback() {
            @Override public void onAdLoaded(InterstitialAd ad) {
                interstitialLoading = false;
                interstitialRetries = 0;
                interstitial = ad;
                Log.i(TAG, "geçiş reklamı hazır");
            }

            @Override public void onAdFailedToLoad(LoadAdError e) {
                interstitialLoading = false;
                Log.w(TAG, "geçiş reklamı yüklenemedi: " + e.getCode() + " " + e.getMessage());
                main.postDelayed(AdMobAds.this::loadInterstitial, backoff(interstitialRetries++));
            }
        });
    }

    @Override public boolean isInterstitialReady() {
        return interstitial != null && !showingFullScreen;
    }

    @Override public void showInterstitial(Runnable after) {
        InterstitialAd ad = interstitial;
        if (ad == null || showingFullScreen) { if (after != null) after.run(); return; }
        interstitial = null;
        showingFullScreen = true;
        applyBanner();
        final AtomicBoolean done = new AtomicBoolean(false);
        Runnable finish = () -> {
            if (!done.compareAndSet(false, true)) return;
            showingFullScreen = false;
            applyBanner();
            loadInterstitial();
            if (after != null) main.post(after);
        };
        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override public void onAdShowedFullScreenContent() { Log.i(TAG, "geçiş reklamı gösterildi"); }
            @Override public void onAdDismissedFullScreenContent() { finish.run(); }
            @Override public void onAdFailedToShowFullScreenContent(AdError e) {
                Log.w(TAG, "geçiş reklamı gösterilemedi: " + e.getMessage());
                finish.run();
            }
        });
        ad.show(activity);
    }

    // ---------------- Ödüllü video ----------------
    private void loadRewarded() {
        if (destroyed || rewarded != null || rewardedLoading) return;
        rewardedLoading = true;
        RewardedAd.load(activity, rewardedId, new AdRequest.Builder().build(), new RewardedAdLoadCallback() {
            @Override public void onAdLoaded(RewardedAd ad) {
                rewardedLoading = false;
                rewardedRetries = 0;
                rewarded = ad;
                Log.i(TAG, "ödüllü video hazır");
            }

            @Override public void onAdFailedToLoad(LoadAdError e) {
                rewardedLoading = false;
                Log.w(TAG, "ödüllü video yüklenemedi: " + e.getCode() + " " + e.getMessage());
                main.postDelayed(AdMobAds.this::loadRewarded, backoff(rewardedRetries++));
            }
        });
    }

    @Override public boolean isRewardedReady() {
        return rewarded != null && !showingFullScreen;
    }

    @Override public void showRewarded(Runnable onReward, Runnable onClosed) {
        RewardedAd ad = rewarded;
        if (ad == null || showingFullScreen) { if (onClosed != null) onClosed.run(); return; }
        rewarded = null;
        showingFullScreen = true;
        applyBanner();
        final AtomicBoolean earned = new AtomicBoolean(false), done = new AtomicBoolean(false);
        Runnable finish = () -> {
            if (!done.compareAndSet(false, true)) return;
            showingFullScreen = false;
            applyBanner();
            loadRewarded();
            main.post(() -> {
                if (earned.get() && onReward != null) onReward.run(); // ödül, video kapandıktan sonra verilir
                if (onClosed != null) onClosed.run();
            });
        };
        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override public void onAdShowedFullScreenContent() { Log.i(TAG, "ödüllü video gösterildi"); }
            @Override public void onAdDismissedFullScreenContent() { finish.run(); }
            @Override public void onAdFailedToShowFullScreenContent(AdError e) {
                Log.w(TAG, "ödüllü video gösterilemedi: " + e.getMessage());
                finish.run();
            }
        });
        ad.show(activity, rewardItem -> {
            earned.set(true);
            Log.i(TAG, "ödül kazanıldı");
        });
    }

    // ---------------- Yaşam döngüsü ----------------
    @Override public void onPause() { if (banner != null) banner.pause(); }

    @Override public void onResume() { if (banner != null) banner.resume(); }

    @Override public void destroy() {
        destroyed = true;
        main.removeCallbacksAndMessages(null);
        if (banner != null) { banner.destroy(); banner = null; }
        interstitial = null;
        rewarded = null;
    }
}
