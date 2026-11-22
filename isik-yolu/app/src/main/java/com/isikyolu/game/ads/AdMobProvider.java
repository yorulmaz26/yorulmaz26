package com.isikyolu.game.ads;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.annotation.NonNull;

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
import com.isikyolu.game.BuildConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Google AdMob: uyarlanabilir banner, geçiş (resim) reklamı ve ödüllü video.
 * Önce Google UMP ile kullanıcı izni alınır (AB/İngiltere'de form gösterilir); izin yoksa reklam istenmez.
 * Reklam içeriği "PG" ile sınırlıdır (her yaşa uygun bulmaca oyunu).
 */
public final class AdMobProvider implements AdProvider {
    private static final long RETRY_MIN_MS = 20_000, RETRY_MAX_MS = 5 * 60_000;

    private final Handler main = new Handler(Looper.getMainLooper());
    private Context app;
    private ConsentInformation consent;
    private boolean started, initStarted, ready, fullscreen;
    private final List<AdView> pending = new ArrayList<>();

    private InterstitialAd interstitial;
    private RewardedAd rewarded;
    private boolean loadingInterstitial, loadingRewarded;
    private long retryInterstitial = RETRY_MIN_MS, retryRewarded = RETRY_MIN_MS;

    @Override public void start(Activity a) {
        if (started) return;
        started = true;
        app = a.getApplicationContext();
        consent = UserMessagingPlatform.getConsentInformation(a);
        ConsentRequestParameters params = new ConsentRequestParameters.Builder().build();
        consent.requestConsentInfoUpdate(a, params,
                () -> UserMessagingPlatform.loadAndShowConsentFormIfRequired(a, err -> { if (consent.canRequestAds()) initSdk(); }),
                err -> { if (consent.canRequestAds()) initSdk(); });
        // daha önce izin verildiyse beklemeden başla
        if (consent.canRequestAds()) initSdk();
    }

    private void initSdk() {
        if (initStarted) return;
        initStarted = true;
        MobileAds.setRequestConfiguration(new RequestConfiguration.Builder()
                .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_PG)
                .build());
        // Google'ın önerisi: başlatma ana iş parçacığının dışında
        new Thread(() -> MobileAds.initialize(app, status -> main.post(this::onReady)), "reklam-baslat").start();
    }

    private void onReady() {
        ready = true;
        for (AdView v : pending) v.loadAd(new AdRequest.Builder().build());
        pending.clear();
        loadInterstitial();
        loadRewarded();
    }

    // ---------- banner ----------

    @Override public View createBanner(Activity a, int widthDp, Runnable onReady, Runnable onGone) {
        AdView v = new AdView(a);
        v.setAdUnitId(BuildConfig.AD_BANNER);
        v.setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(a, widthDp));
        v.setAdListener(new AdListener() {
            @Override public void onAdLoaded() { onReady.run(); }
            @Override public void onAdFailedToLoad(@NonNull LoadAdError e) { onGone.run(); }
        });
        if (ready) v.loadAd(new AdRequest.Builder().build());
        else pending.add(v);
        return v;
    }

    @Override public void destroyBanner(View banner) {
        if (!(banner instanceof AdView)) return;
        pending.remove(banner);
        ((AdView) banner).destroy();
    }

    @Override public void pause(View banner) { if (banner instanceof AdView) ((AdView) banner).pause(); }

    @Override public void resume(View banner) { if (banner instanceof AdView) ((AdView) banner).resume(); }

    // ---------- geçiş reklamı ----------

    private void loadInterstitial() {
        if (!ready || loadingInterstitial || interstitial != null) return;
        loadingInterstitial = true;
        InterstitialAd.load(app, BuildConfig.AD_INTERSTITIAL, new AdRequest.Builder().build(), new InterstitialAdLoadCallback() {
            @Override public void onAdLoaded(@NonNull InterstitialAd ad) {
                loadingInterstitial = false;
                interstitial = ad;
                retryInterstitial = RETRY_MIN_MS;
            }

            @Override public void onAdFailedToLoad(@NonNull LoadAdError e) {
                loadingInterstitial = false;
                main.postDelayed(AdMobProvider.this::loadInterstitial, retryInterstitial);
                retryInterstitial = Math.min(RETRY_MAX_MS, retryInterstitial * 2);
            }
        });
    }

    @Override public boolean interstitialReady() { return interstitial != null; }

    @Override public void showInterstitial(Activity a, Runnable onClosed) {
        InterstitialAd ad = interstitial;
        if (ad == null) { onClosed.run(); return; }
        interstitial = null;
        boolean[] done = {false};
        Runnable finish = () -> {
            if (done[0]) return;
            done[0] = true;
            fullscreen = false;
            loadInterstitial();
            onClosed.run();
        };
        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override public void onAdShowedFullScreenContent() { fullscreen = true; }
            @Override public void onAdDismissedFullScreenContent() { finish.run(); }
            @Override public void onAdFailedToShowFullScreenContent(@NonNull AdError e) { finish.run(); }
        });
        fullscreen = true;
        ad.show(a);
    }

    // ---------- ödüllü video ----------

    private void loadRewarded() {
        if (!ready || loadingRewarded || rewarded != null) return;
        loadingRewarded = true;
        RewardedAd.load(app, BuildConfig.AD_REWARDED, new AdRequest.Builder().build(), new RewardedAdLoadCallback() {
            @Override public void onAdLoaded(@NonNull RewardedAd ad) {
                loadingRewarded = false;
                rewarded = ad;
                retryRewarded = RETRY_MIN_MS;
            }

            @Override public void onAdFailedToLoad(@NonNull LoadAdError e) {
                loadingRewarded = false;
                main.postDelayed(AdMobProvider.this::loadRewarded, retryRewarded);
                retryRewarded = Math.min(RETRY_MAX_MS, retryRewarded * 2);
            }
        });
    }

    @Override public boolean rewardedReady() { return rewarded != null; }

    @Override public void showRewarded(Activity a, Runnable onEarned, Consumer<Boolean> onClosed) {
        RewardedAd ad = rewarded;
        if (ad == null) { onClosed.accept(false); return; }
        rewarded = null;
        boolean[] earned = {false}, done = {false};
        Runnable finish = () -> {
            if (done[0]) return;
            done[0] = true;
            fullscreen = false;
            loadRewarded();
            onClosed.accept(earned[0]);
        };
        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override public void onAdShowedFullScreenContent() { fullscreen = true; }
            @Override public void onAdDismissedFullScreenContent() { finish.run(); }
            @Override public void onAdFailedToShowFullScreenContent(@NonNull AdError e) { finish.run(); }
        });
        fullscreen = true;
        ad.show(a, item -> {
            if (earned[0]) return;
            earned[0] = true;
            onEarned.run(); // ödül hemen kaydedilir (uygulama kapansa da kaybolmaz)
        });
    }

    @Override public boolean fullscreenShowing() { return fullscreen; }

    @Override public boolean privacyOptionsRequired() {
        return consent != null && consent.getPrivacyOptionsRequirementStatus() == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED;
    }

    @Override public void showPrivacyOptions(Activity a) {
        UserMessagingPlatform.showPrivacyOptionsForm(a, err -> { if (consent.canRequestAds()) initSdk(); });
    }
}
