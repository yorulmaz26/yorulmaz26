package com.isikyolu.game.ads;

import android.app.Activity;
import android.view.View;

import java.util.function.Consumer;

/** Reklamsız sağlayıcı (ör. Robolectric testleri): hiçbir şey göstermez, geçişler hemen devam eder. */
public class NoAds implements AdProvider {
    @Override public void start(Activity a) {}
    @Override public View createBanner(Activity a, int widthDp, Runnable onReady, Runnable onGone) { return null; }
    @Override public void destroyBanner(View banner) {}
    @Override public void pause(View banner) {}
    @Override public void resume(View banner) {}
    @Override public boolean interstitialReady() { return false; }
    @Override public void showInterstitial(Activity a, Runnable onClosed) { onClosed.run(); }
    @Override public boolean rewardedReady() { return false; }
    @Override public void showRewarded(Activity a, Runnable onEarned, Consumer<Boolean> onClosed) { onClosed.accept(false); }
    @Override public boolean fullscreenShowing() { return false; }
    @Override public boolean privacyOptionsRequired() { return false; }
    @Override public void showPrivacyOptions(Activity a) {}
}
