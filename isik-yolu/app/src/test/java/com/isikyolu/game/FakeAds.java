package com.isikyolu.game;

import android.app.Activity;
import android.view.View;

import com.isikyolu.game.ads.AdProvider;

import java.util.function.Consumer;

/** Testler için sahte reklam sağlayıcısı: her şey anında "yüklü", gösterimler sayılır. */
public class FakeAds implements AdProvider {
    public int interstitials, rewardedShown, bannersCreated, starts;
    public boolean interstitialLoaded = true, rewardedLoaded = true, completeVideo = true, fullscreen;

    @Override public void start(Activity a) { starts++; }

    @Override public View createBanner(Activity a, int widthDp, Runnable onReady, Runnable onGone) {
        bannersCreated++;
        float d = a.getResources().getDisplayMetrics().density;
        // gerçek AdView gibi kendi reklam boyutunu ölçer (320×50 dp)
        View v = new View(a) {
            @Override protected void onMeasure(int w, int h) { setMeasuredDimension((int) (320 * d), (int) (50 * d)); }
            @Override protected void onDraw(android.graphics.Canvas c) {
                super.onDraw(c);
                android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                p.setColor(0xFFFFFFFF);
                p.setTextSize(14 * d);
                p.setTextAlign(android.graphics.Paint.Align.CENTER);
                c.drawText("Test reklamı (banner 320×50)", getWidth() / 2f, getHeight() / 2f + 5 * d, p);
            }
        };
        v.setBackgroundColor(0xFF2E7D32);
        v.setContentDescription("test-banner");
        v.post(onReady);
        return v;
    }

    @Override public void destroyBanner(View banner) {}
    @Override public void pause(View banner) {}
    @Override public void resume(View banner) {}
    @Override public boolean interstitialReady() { return interstitialLoaded; }

    @Override public void showInterstitial(Activity a, Runnable onClosed) {
        interstitials++;
        onClosed.run();
    }

    @Override public boolean rewardedReady() { return rewardedLoaded; }

    @Override public void showRewarded(Activity a, Runnable onEarned, Consumer<Boolean> onClosed) {
        rewardedShown++;
        if (completeVideo) onEarned.run();
        onClosed.accept(completeVideo);
    }

    @Override public boolean fullscreenShowing() { return fullscreen; }
    @Override public boolean privacyOptionsRequired() { return true; }
    @Override public void showPrivacyOptions(Activity a) {}
}
