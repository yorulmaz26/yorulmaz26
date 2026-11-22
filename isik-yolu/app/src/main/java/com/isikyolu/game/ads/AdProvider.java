package com.isikyolu.game.ads;

import android.app.Activity;
import android.view.View;

/**
 * Reklam sağlayıcısı. Uygulama yalnızca bu arayüzü bilir: gerçek uygulamada Google AdMob
 * ({@link AdMobProvider}), testlerde sahte sağlayıcı kullanılır.
 */
public interface AdProvider {
    /** Ödüllü video sonucu. */
    enum VideoResult { EARNED, SKIPPED, FAILED }

    /** Bir reklam yüklendiğinde (ör. ödüllü video yeniden hazır) çağrılır: ekranlar düğmelerini yenileyebilir. */
    void setOnAvailabilityChanged(Runnable r);

    /** İzin (UMP) akışını başlatır, izin varsa SDK'yı başlatıp reklamları önceden yükler. */
    void start(Activity a);

    /** Ekranın altına konacak banner (yüklenince {@code onReady}, yüklenemezse {@code onGone}). Null = banner yok. */
    View createBanner(Activity a, int widthDp, Runnable onReady, Runnable onGone);

    void destroyBanner(View banner);

    void pause(View banner);

    void resume(View banner);

    /** Geçiş (resim) reklamı yüklü mü? */
    boolean interstitialReady();

    /**
     * Geçiş reklamını gösterir. Gerçekten ekrana gelince {@code onShown} (sıklık sayacı için), kapanınca ya da
     * gösterilemezse {@code onClosed} çalışır.
     */
    void showInterstitial(Activity a, Runnable onShown, Runnable onClosed);

    /** Ödüllü video yüklü mü? */
    boolean rewardedReady();

    /**
     * Ödüllü videoyu gösterir. Video sonuna kadar izlenirse {@code onEarned} hemen, ardından (her durumda) video
     * kapanınca {@code onClosed(sonuç)} çalışır: kazanıldı, erken kapatıldı ya da hiç açılamadı.
     */
    void showRewarded(Activity a, Runnable onEarned, java.util.function.Consumer<VideoResult> onClosed);

    /** Henüz izin istenmediyse ya da istek başarısız olduysa yeniden dener (ör. ilk açılış çevrimdışıydı). */
    default void retryConsent(Activity a) {}

    /** Tam ekran reklam şu an açık mı (uygulama duraklatılmasın). */
    boolean fullscreenShowing();

    /** Gizlilik seçenekleri (izin formu) ayarlarda gösterilmeli mi? */
    boolean privacyOptionsRequired();

    void showPrivacyOptions(Activity a);
}
