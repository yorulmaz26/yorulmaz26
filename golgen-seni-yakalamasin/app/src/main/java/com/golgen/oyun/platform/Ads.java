package com.golgen.oyun.platform;

/** Reklam katmanı soyutlaması. Oyun kodu yalnızca bunu bilir; reklam yoksa {@link NoAds} kullanılır. */
public interface Ads {
    /** Banner yalnızca menü ekranlarında istenir; oyun sırasında asla. */
    void setBannerWanted(boolean wanted);

    /** Banner'ın kapladığı yükseklik (piksel); görünmüyorsa 0. */
    int bannerHeight();

    /** Banner'ın alt kenar boşluğu (sistem çubuğu). */
    void setBottomMargin(int px);

    boolean isInterstitialReady();

    /** Geçiş reklamını gösterir; kapanınca (veya gösterilemezse) {@code after} çalışır. */
    void showInterstitial(Runnable after);

    boolean isRewardedReady();

    /** Ödüllü videoyu gösterir; izlenip ödül kazanılırsa {@code onReward}, her durumda kapanınca {@code onClosed}. */
    void showRewarded(Runnable onReward, Runnable onClosed);

    void onPause();

    void onResume();

    void destroy();

    /** Reklam yüksekliği değiştiğinde oyuna haber verir. */
    interface Listener {
        void onBannerHeightChanged(int px);
    }
}
