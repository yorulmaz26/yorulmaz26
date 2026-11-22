package com.isikyolu.engine;

/**
 * Reklam kuralları (oyunu bozmayacak biçimde):
 * <ul>
 *   <li>Geçiş (resim) reklamı yalnızca bölüm bittikten sonra, oyuncu sonraki ekrana geçerken gösterilir; öğretici
 *       bölümlerde (ilk 10) hiç gösterilmez, en az 3 tamamlanan bölüm ve 4 dakika arayla, ödüllü videodan sonraki
 *       3 dakikada gösterilmez.</li>
 *   <li>Ödüllü video isteğe bağlıdır; günlük sınırları vardır ve ödül yalnızca video sonuna kadar izlenince verilir.</li>
 * </ul>
 * Zaman, saat hilesine dayanıklı güvenilir zamandır (Lives.trustedWall); gün Economy.monotonicDay'dir.
 */
public final class AdPolicy {
    private AdPolicy() {}

    /** Bu bölümden önce geçiş reklamı yok (öğretici grup). */
    public static final int FIRST_AD_LEVEL = 11;
    public static final int LEVELS_BETWEEN = 3;
    public static final long MIN_GAP_MS = 4 * 60_000L;
    public static final long AFTER_REWARD_MS = 3 * 60_000L;

    /** Ödüllü video türleri. */
    public enum Reward {
        /** +1 can (canlar doluysa sunulmaz). */
        LIFE(3),
        /** +1 ipucu (ipucu kalmayınca). */
        HINT(3),
        /** Hamle hakkı bitince ek hak (kristalli devamla aynı miktar). */
        MOVES(5),
        /** Markette +VIDEO_CRYSTALS kristal. */
        CRYSTALS(3);

        public final int dailyLimit;

        Reward(int dailyLimit) { this.dailyLimit = dailyLimit; }
    }

    /** Video başına kristal ödülü. */
    public static final int VIDEO_CRYSTALS = 25;

    /** İlk dakikalar reklamsız: banner ilk 3 bölüm bitince başlar. */
    public static final int BANNER_AFTER_LEVELS = 3;

    public static boolean bannersAllowed(Progress p) { return p.completedCount() >= BANNER_AFTER_LEVELS; }

    /** Bir bölüm (levelId) ya da günlük bulmaca (0) bitti. Öğretici bölümler sayılmaz. */
    public static void onLevelCompleted(Progress p, int levelId) {
        if (levelId == 0 || levelId >= FIRST_AD_LEVEL) p.adLevelsSince++;
    }

    /** Bölüm bitince sonraki ekrana geçerken geçiş reklamı zamanı geldi mi? */
    public static boolean interstitialDue(Progress p, int levelId, long now) {
        if (levelId > 0 && levelId < FIRST_AD_LEVEL) return false;
        if (p.completedCount() < FIRST_AD_LEVEL - 1) return false;
        if (p.adLevelsSince < LEVELS_BETWEEN) return false;
        if (p.adLastWall > 0 && now - p.adLastWall < MIN_GAP_MS) return false;
        if (p.adLastReward > 0 && now - p.adLastReward < AFTER_REWARD_MS) return false;
        return true;
    }

    public static void onInterstitialShown(Progress p, long now) {
        p.adLevelsSince = 0;
        p.adLastWall = now;
    }

    private static void roll(Progress p, int day) {
        if (day > p.adRewardDay) { p.adRewardDay = day; p.adRewards.clear(); }
    }

    public static int rewardsLeft(Progress p, Reward r, int day) {
        roll(p, day);
        Integer n = p.adRewards.get(r.name());
        return Math.max(0, r.dailyLimit - (n == null ? 0 : n));
    }

    /** Bu ödül şu an anlamlı ve sınır içinde mi? */
    public static boolean canOffer(Progress p, Reward r, int day) {
        if (r == Reward.LIFE && p.lives >= Lives.MAX) return false;
        return rewardsLeft(p, r, day) > 0;
    }

    /**
     * Video sonuna kadar izlendi: ödülü verir (hamle hakkı oturumda verilir, burada yalnızca sayılır).
     * @return verildiyse true (sınır dolmuşsa false)
     */
    public static boolean grant(Progress p, Reward r, int day, long now, Lives.Clock clock) {
        if (!canOffer(p, r, day)) return false;
        p.adRewards.put(r.name(), p.adRewards.getOrDefault(r.name(), 0) + 1);
        p.adLastReward = now;
        p.adVideos++;
        switch (r) {
            case LIFE: Economy.addLives(p, 1, clock); break;
            case HINT: p.hints++; break;
            case CRYSTALS: p.crystals += VIDEO_CRYSTALS; p.crystalsEarned += VIDEO_CRYSTALS; break;
            default: break;
        }
        return true;
    }
}
