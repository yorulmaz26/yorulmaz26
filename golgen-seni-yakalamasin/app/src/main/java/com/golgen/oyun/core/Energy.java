package com.golgen.oyun.core;

/**
 * Bölüm modu canları: en fazla 4, her 15 dakikada 1 yenilenir.
 *
 * <p>Hileye karşı: yenilenme süresi telefon saatinden değil, cihaz açıldığından beri geçen monoton
 * süreden ({@link TimeSource#elapsedMs()}) ölçülür. Bu süre kullanıcı tarafından değiştirilemez,
 * bu yüzden saati ileri almak can kazandırmaz, geri almak can kaybettirmez.
 * Cihaz yeniden başlatıldığında monoton süre sıfırlanır; bu aralıkta duvar saati kullanılır, ancak
 * saat geri alınmışsa yalnızca açılıştan beri geçen süre sayılır ve tek seferde verilecek süre sınırlıdır.
 * Aynı açılış içinde duvar saatinin monoton süreden saptığı (saatle oynandığı) tespit edilir ve kaydedilir.
 */
public final class Energy {
    public static final int MAX = 4;
    public static final long REGEN_MS = 15L * 60L * 1000L;
    /** Yeniden başlatma sonrası duvar saatine göre verilebilecek en fazla süre (tam dolum için yeterli). */
    public static final long MAX_REBOOT_CREDIT_MS = MAX * REGEN_MS;
    /** Bu kadar sapma "saatle oynandı" sayılır. */
    public static final long TAMPER_TOLERANCE_MS = 2L * 60L * 1000L;

    public int lives = MAX;
    public long progressMs;      // bir sonraki cana doğru biriken süre
    public long lastWall;        // son kontrol noktasındaki duvar saati (güvenilir tahmin)
    public long lastElapsed;     // son kontrol noktasındaki monoton süre
    public int lastBoot = -2;    // -2 = hiç başlatılmadı
    public int tamperCount;      // tespit edilen saat oynamaları

    /** İlk kurulum: 4 can. */
    public void init(TimeSource t) {
        lives = MAX;
        progressMs = 0;
        checkpoint(t.wallMs(), t.elapsedMs(), t.bootCount());
    }

    public boolean initialized() {
        return lastBoot != -2;
    }

    private void checkpoint(long wall, long elapsed, int boot) {
        lastWall = wall;
        lastElapsed = elapsed;
        lastBoot = boot;
    }

    /** Son kontrol noktasından beri güvenilir şekilde geçen süreyi hesaplar ve canları yeniler. */
    public void tick(TimeSource t) {
        if (!initialized()) { init(t); return; }
        long wall = t.wallMs(), elapsed = t.elapsedMs();
        int boot = t.bootCount();
        boolean sameBoot = elapsed >= lastElapsed && (boot < 0 || lastBoot < 0 || boot == lastBoot);
        long delta;
        long trustedWall;
        if (sameBoot) {
            delta = elapsed - lastElapsed;
            trustedWall = lastWall + delta;
            if (Math.abs(wall - trustedWall) > TAMPER_TOLERANCE_MS && tamperCount < 1_000_000) tamperCount++; // saat değiştirilmiş
        } else {
            // Yeniden başlatma: geçen süre en az "açılıştan beri geçen" kadardır.
            long wallDelta = wall - lastWall;
            if (wallDelta < elapsed) {
                // Saat geri alınmış (veya tutarsız): yalnızca kesin bilinen süre sayılır
                if (wallDelta < 0) tamperCount++;
                delta = elapsed;
            } else {
                delta = Math.min(wallDelta, elapsed + MAX_REBOOT_CREDIT_MS);
            }
            trustedWall = Math.max(wall, lastWall + delta);
        }
        apply(delta);
        checkpoint(trustedWall, elapsed, boot);
    }

    private void apply(long delta) {
        if (delta <= 0) return;
        if (lives >= MAX) { progressMs = 0; return; }
        progressMs += delta;
        while (progressMs >= REGEN_MS && lives < MAX) {
            progressMs -= REGEN_MS;
            lives++;
        }
        if (lives >= MAX) progressMs = 0;
    }

    public boolean hasLife(TimeSource t) {
        tick(t);
        return lives > 0;
    }

    /** Bölüme başlarken 1 can harcar. */
    public boolean consume(TimeSource t) {
        tick(t);
        if (lives <= 0) return false;
        if (lives == MAX) progressMs = 0; // dolu iken sayaç yeni başlar
        lives--;
        return true;
    }

    /** Bölüm kazanılınca harcanan can geri verilir. */
    public void refund(TimeSource t) {
        tick(t);
        if (lives < MAX) lives++;
        if (lives >= MAX) progressMs = 0;
    }

    /** Elmasla tam dolum. */
    public void refill(TimeSource t) {
        tick(t);
        lives = MAX;
        progressMs = 0;
    }

    /** Sonraki cana kalan süre (dolu ise 0). */
    public long msToNext(TimeSource t) {
        tick(t);
        if (lives >= MAX) return 0;
        return Math.max(0, REGEN_MS - progressMs);
    }

    // ---- Kayıt (uzun sayılar metin olarak saklanır) ----
    public void load(KeyValueStore s) {
        lives = Math.max(0, Math.min(MAX, s.getInt("enLives", MAX)));
        progressMs = parse(s.getString("enProg", "0"));
        lastWall = parse(s.getString("enWall", "0"));
        lastElapsed = parse(s.getString("enElapsed", "0"));
        lastBoot = s.getInt("enBoot", -2);
        tamperCount = s.getInt("enTamper", 0);
        if (progressMs < 0 || progressMs > REGEN_MS) progressMs = 0;
    }

    public void save(KeyValueStore s) {
        s.putInt("enLives", lives);
        s.putString("enProg", Long.toString(progressMs));
        s.putString("enWall", Long.toString(lastWall));
        s.putString("enElapsed", Long.toString(lastElapsed));
        s.putInt("enBoot", lastBoot);
        s.putInt("enTamper", tamperCount);
    }

    private static long parse(String v) {
        try { return Long.parseLong(v); } catch (NumberFormatException e) { return 0L; }
    }
}
