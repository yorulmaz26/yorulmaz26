package com.golgen.oyun.core;

/**
 * Merkezi oyun yapılandırması: dengeyle ilgili ayarlanabilir değerler tek yerde durur.
 * Değerleri değiştirmek kayıtları bozmaz (kayıtta süreler milisaniye, canlar sayı olarak tutulur).
 */
public final class GameConfig {
    private GameConfig() {}

    // ---------------- Can sistemi (bölüm modu) ----------------
    /** En fazla can (mevcut oyundaki değer korunur). */
    public static final int MAX_LIVES = 4;
    /** Bir canın yenilenme süresi: 20 dakika. */
    public static final int LIFE_REGEN_SECONDS = 1200;
    /** Elmasla tam dolum fiyatı. */
    public static final int LIFE_REFILL_COST = 60;
    /** Ödüllü video ile günde kazanılabilecek en fazla can. */
    public static final int REWARDED_LIVES_PER_DAY = 3;
    /**
     * Telefon yeniden başlatıldığında, açılıştan beri geçen sürenin üstüne duvar saatine göre
     * verilebilecek en fazla ek süre (saniye). Bir can kadardır: kapat-aç + saati ileri almak
     * en fazla 1 can kazandırır.
     */
    public static final int MAX_REBOOT_CREDIT_SECONDS = LIFE_REGEN_SECONDS;
    /** Aynı açılışta duvar saatinin monoton saatten bu kadar sapması "saatle oynandı" sayılır (saniye). */
    public static final int CLOCK_TAMPER_TOLERANCE_SECONDS = 120;
}
