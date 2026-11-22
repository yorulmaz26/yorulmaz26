package com.isikyolu.game.ads;

import android.app.Activity;
import android.view.View;

/**
 * Reklam sağlayıcısı. Uygulama yalnızca bu arayüzü bilir: gerçek uygulamada Google AdMob
 * ({@link AdMobProvider}), testlerde sahte sağlayıcı kullanılır.
 */
public interface AdProvider {
    /** İzin (UMP) akışını başlatır, izin varsa SDK'yı başlatıp reklamları önceden yükler. */
    void start(Activity a);

    /** Ekranın altına konacak banner (yüklenince {@code onReady}, yüklenemezse {@code onGone}). Null = banner yok. */
    View createBanner(Activity a, int widthDp, Runnable onReady, Runnable onGone);

    void destroyBanner(View banner);

    void pause(View banner);

    void resume(View banner);

    /** Geçiş (resim) reklamı yüklü mü? */
    boolean interstitialReady();

    /** Geçiş reklamını gösterir; kapanınca (ya da gösterilemezse) {@code onClosed} çalışır. */
    void showInterstitial(Activity a, Runnable onClosed);

    /** Ödüllü video yüklü mü? */
    boolean rewardedReady();

    /**
     * Ödüllü videoyu gösterir. Video sonuna kadar izlenirse {@code onEarned}, ardından (her durumda)
     * {@code onClosed(earned)} çalışır.
     */
    void showRewarded(Activity a, Runnable onEarned, java.util.function.Consumer<Boolean> onClosed);

    /** Tam ekran reklam şu an açık mı (uygulama duraklatılmasın). */
    boolean fullscreenShowing();

    /** Gizlilik seçenekleri (izin formu) ayarlarda gösterilmeli mi? */
    boolean privacyOptionsRequired();

    void showPrivacyOptions(Activity a);
}
