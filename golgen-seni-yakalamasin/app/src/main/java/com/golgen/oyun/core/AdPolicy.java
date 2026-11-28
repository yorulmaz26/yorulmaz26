package com.golgen.oyun.core;

/**
 * Reklam sıklığı kuralları (akışı bozmamak için):
 * - Oyun sırasında HİÇ reklam yok; geçiş reklamı yalnızca oyun sonu ekranından çıkarken (doğal mola).
 * - Yeni oyuncunun ilk {@link #GRACE_RUNS} oyununda geçiş reklamı yok.
 * - En fazla {@link #EVERY_RUNS} oyunda bir ve iki geçiş reklamı arasında en az {@link #MIN_GAP_MS}.
 * - Video (ödüllü) reklam tamamen isteğe bağlıdır; günlük +1 can hakkı {@link #REWARDED_LIVES_PER_DAY} ile sınırlı.
 * Süreler monoton saatle ölçülür (saat değiştirilerek atlatılamaz).
 */
public final class AdPolicy {
    public static final int GRACE_RUNS = 3;
    public static final int EVERY_RUNS = 3;
    public static final long MIN_GAP_MS = 150_000L;
    public static final float MIN_RUN_SECONDS = 12f;
    public static final int REWARDED_LIVES_PER_DAY = GameConfig.REWARDED_LIVES_PER_DAY;

    private int runsSince;
    private long lastShownElapsed = Long.MIN_VALUE / 4;

    /** Bir oyun bittiğinde çağrılır (çok kısa oyunlar sayılmaz: hemen ölen oyuncuya reklam yağdırılmaz). */
    public void onRunFinished(float runSeconds) {
        if (runSeconds >= MIN_RUN_SECONDS) runsSince++;
    }

    public boolean shouldShowInterstitial(int gamesPlayed, long elapsedNow) {
        if (gamesPlayed < GRACE_RUNS) return false;
        if (runsSince < EVERY_RUNS) return false;
        return elapsedNow - lastShownElapsed >= MIN_GAP_MS;
    }

    public void onInterstitialShown(long elapsedNow) {
        runsSince = 0;
        lastShownElapsed = elapsedNow;
    }

    public int runsSinceInterstitial() {
        return runsSince;
    }
}
