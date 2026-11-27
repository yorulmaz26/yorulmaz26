package com.isikyolu.engine;

/**
 * Oyunun merkezi ayarları. Denge değerleri tek yerden değiştirilir; kod içinde bu değerlerin kopyası tutulmaz.
 */
public final class GameConfig {
    private GameConfig() {}

    // ---------- can sistemi ----------
    /** En fazla can (ödül ve satın almalar dahil bu sayı aşılmaz). */
    public static final int LIVES_MAX = 4;
    /** Bir canın yenilenme süresi: 20 dakika. */
    public static final int LIFE_REGEN_SECONDS = 1200;
    /** Aynı açılışta duvar saati ile monoton saat arasında kabul edilen en büyük sapma (saat değişikliği algılama). */
    public static final int CLOCK_TOLERANCE_SECONDS = 120;

    // ---------- bölüm ----------
    /** Hamle hakkı = en az hamle × çarpan + ek. */
    public static final int MOVE_BUDGET_FACTOR = 2, MOVE_BUDGET_EXTRA = 5;
}
