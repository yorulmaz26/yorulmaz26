package com.isikyolu.engine;

/**
 * Bölüm modu can sistemi: en fazla 4 can, 15 dakikada bir yenilenir.
 *
 * Saat hilesine karşı süre şu sırayla ölçülür:
 * 1. Aynı açılış (boot) içinde: {@code elapsedRealtime} — telefon açıldığından beri işleyen, kullanıcının
 *    değiştiremediği monoton saat. Telefon saatini ileri almak hiçbir şey kazandırmaz.
 * 2. Yeniden başlatmadan sonra, ağ saati (Android 13+) varsa: ağ saatindeki fark.
 * 3. Yalnızca duvar saati varsa: fark kullanılır, ama saat daha önce görülen en ileri zamandan geriye
 *    alınmışsa hiç süre sayılmaz ve taban yeniden kurulur.
 * Aynı açılışta duvar saati monoton saatten 2 dakikadan fazla saparsa saat değişikliği algılanır.
 */
public final class Lives {
    public static final int MAX = 4;
    public static final long REGEN_MS = 15 * 60_000L;
    static final long TOLERANCE_MS = 2 * 60_000L;

    /** Platformdan okunan saatler. */
    public static final class Clock {
        public final long wall, elapsed, net;
        public final int boot;

        /** @param net ağ saati (bilinmiyorsa -1); boot açılış sayacı (bilinmiyorsa -1) */
        public Clock(long wall, long elapsed, int boot, long net) {
            this.wall = wall; this.elapsed = elapsed; this.boot = boot; this.net = net;
        }
    }

    private Lives() {}

    /** Hamle hakkı: bölümün en az hamlesinin iki katı + 5. Geri almak hakkı iade etmez. */
    public static int moveBudget(int optimal) { return optimal * 2 + 5; }

    private static boolean sameBoot(Progress p, Clock c) {
        if (p.lifeBaseBoot < 0 || c.boot < 0) {
            // açılış sayacı yoksa: açılış anı tahmini (duvar - monoton) ve monoton saatin ilerlemesi
            return c.elapsed >= p.lifeBaseElapsed && Math.abs((c.wall - c.elapsed) - (p.lifeBaseWall - p.lifeBaseElapsed)) < TOLERANCE_MS;
        }
        return p.lifeBaseBoot == c.boot && c.elapsed >= p.lifeBaseElapsed;
    }

    /** Taban anından bu yana güvenilir şekilde geçen süre. */
    static long trustedElapsed(Progress p, Clock c) {
        if (sameBoot(p, c)) return c.elapsed - p.lifeBaseElapsed;
        if (p.lifeBaseNet >= 0 && c.net >= 0) return Math.max(0, c.net - p.lifeBaseNet);
        if (c.wall < p.lifeMaxWall - TOLERANCE_MS) return -1; // saat geri alınmış: süre sayılmaz
        return Math.max(0, c.wall - p.lifeBaseWall);
    }

    private static void rebase(Progress p, Clock c, long remainder) {
        p.lifeBaseWall = c.wall - remainder;
        p.lifeBaseElapsed = c.elapsed - remainder;
        p.lifeBaseBoot = c.boot;
        p.lifeBaseNet = c.net >= 0 ? c.net - remainder : -1;
    }

    /**
     * Canları güncel saate göre yeniler.
     * @return saat değişikliği algılandıysa true
     */
    public static boolean update(Progress p, Clock c) {
        boolean tamper = false;
        if (p.lifeBaseElapsed == Long.MIN_VALUE) rebase(p, c, 0); // ilk kurulum
        if (sameBoot(p, c)) {
            long drift = (c.wall - p.lifeBaseWall) - (c.elapsed - p.lifeBaseElapsed);
            if (Math.abs(drift) > TOLERANCE_MS) {
                tamper = true;
                p.lifeBaseWall = c.wall - (c.elapsed - p.lifeBaseElapsed); // duvar tabanını gerçeğe hizala
            }
        }
        if (!tamper && c.wall >= p.lifeMaxWall - TOLERANCE_MS) p.lifeMaxWall = Math.max(p.lifeMaxWall, c.wall);
        if (p.lives >= MAX) { p.lives = MAX; rebase(p, c, 0); return tamper; }
        long el = trustedElapsed(p, c);
        if (el < 0) { tamper = true; rebase(p, c, 0); return tamper; }
        int gain = (int) Math.min(MAX, el / REGEN_MS);
        if (gain > 0) {
            p.lives = Math.min(MAX, p.lives + gain);
            rebase(p, c, p.lives >= MAX ? 0 : el % REGEN_MS);
        } else if (!sameBoot(p, c)) {
            rebase(p, c, el); // yeni açılışta tabanı monoton saate taşı, ilerlemeyi koru
        }
        return tamper;
    }

    /** Bir sonraki cana kalan süre (can doluysa 0). */
    public static long msUntilNext(Progress p, Clock c) {
        if (p.lives >= MAX) return 0;
        long el = Math.max(0, trustedElapsed(p, c));
        return Math.max(0, REGEN_MS - el % REGEN_MS);
    }

    /** Bölüm denemesini başlatır: 1 can düşer. Can yoksa false. */
    public static boolean startAttempt(Progress p, Clock c, int levelId) {
        update(p, c);
        if (p.attemptLevel == levelId) return true; // bu deneme için zaten düştü
        if (p.lives <= 0) return false;
        if (p.lives >= MAX) rebase(p, c, 0); // yenilenme sayacı can azalınca başlar
        p.lives--;
        p.attemptLevel = levelId;
        return true;
    }

    /** Bölüm kazanıldı: denemenin canı geri verilir. */
    public static void winAttempt(Progress p, Clock c, int levelId) {
        update(p, c);
        if (p.attemptLevel != levelId) return;
        p.attemptLevel = 0;
        p.lives = Math.min(MAX, p.lives + 1);
        if (p.lives >= MAX) rebase(p, c, 0);
    }

    /** Deneme kaybedildi veya bırakıldı: can geri gelmez. */
    public static void loseAttempt(Progress p) { p.attemptLevel = 0; }

    /** "12:34" biçiminde süre. */
    public static String format(long ms) {
        long s = (ms + 999) / 1000;
        return String.format(java.util.Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }
}
