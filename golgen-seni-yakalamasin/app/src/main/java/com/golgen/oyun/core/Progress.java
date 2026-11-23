package com.golgen.oyun.core;

/**
 * Oyuncunun cihazda saklanan tüm ilerlemesi ve ayarları.
 * Kayıt/okuma KeyValueStore üzerinden yapılır.
 */
public final class Progress {
    public static final int VERSION = 1;
    public static final int TOP_N = 10;

    public int diamonds;
    public int bestScore;
    public float bestTime;
    public int totalDiamonds;
    public int totalDodges;
    public int gamesPlayed;
    public int unlockedSkins = 1;   // bit maskesi, 0. karakter ücretsiz
    public int selectedSkin;
    public int missionsCompleted;   // açık olan en yüksek görev = missionsCompleted
    public int achievements;        // bit maskesi
    public final int[] topScores = new int[TOP_N];

    public boolean sound = true;
    public boolean music = true;
    public boolean vibration = true;
    public boolean tutorialDone;

    public int dailyDay = -1;
    public final int[] dailyTypes = new int[DailyMissions.PER_DAY];
    public final int[] dailyProgress = new int[DailyMissions.PER_DAY];
    public int dailyClaimed;        // bit maskesi

    // Uzun süreli ilerleme
    public final byte[] missionStars = new byte[Missions.ALL.length];
    public int xp;
    public int level = 1;
    public int maxCombo;
    public int totalPowerups;
    public int loginStreak;
    public int lastLoginClaimDay = -1;

    /** Günlük giriş ödülleri (7 günlük döngü, 7. gün büyük ödül). */
    /** Bölüm modu canları (4 can, 15 dk'da bir yenilenir; saat hilesine dayanıklı). */
    public final Energy energy = new Energy();
    public static final int REFILL_COST = 60;
    /** Ödüllü video ile alınan günlük can sayacı. */
    public int rewardedLifeDay = -1;
    public int rewardedLivesToday;

    public static final int[] LOGIN_REWARDS = {10, 15, 20, 25, 35, 45, 100};

    // ---------- Kayıt ----------
    public void load(KeyValueStore s) {
        diamonds = s.getInt("diamonds", 0);
        bestScore = s.getInt("bestScore", 0);
        bestTime = s.getInt("bestTimeMs", 0) / 1000f;
        totalDiamonds = s.getInt("totalDiamonds", 0);
        totalDodges = s.getInt("totalDodges", 0);
        gamesPlayed = s.getInt("gamesPlayed", 0);
        unlockedSkins = s.getInt("unlockedSkins", 1) | 1;
        selectedSkin = s.getInt("selectedSkin", 0);
        if (selectedSkin < 0 || selectedSkin >= Skins.ALL.length || (unlockedSkins & (1 << selectedSkin)) == 0) selectedSkin = 0;
        missionsCompleted = Math.max(0, Math.min(Missions.ALL.length, s.getInt("levelsDone", 0)));
        achievements = s.getInt("achievements", 0);
        String top = s.getString("topScores", "");
        java.util.Arrays.fill(topScores, 0);
        if (!top.isEmpty()) {
            String[] parts = top.split(",");
            for (int i = 0; i < parts.length && i < TOP_N; i++) {
                try { topScores[i] = Integer.parseInt(parts[i].trim()); } catch (NumberFormatException ignored) { }
            }
        }
        sound = s.getInt("sound", 1) == 1;
        music = s.getInt("music", 1) == 1;
        vibration = s.getInt("vibration", 1) == 1;
        tutorialDone = s.getInt("tutorialDone", 0) == 1;
        dailyDay = s.getInt("dailyDay", -1);
        for (int i = 0; i < DailyMissions.PER_DAY; i++) {
            dailyTypes[i] = s.getInt("dailyType" + i, 0);
            dailyProgress[i] = s.getInt("dailyProg" + i, 0);
        }
        dailyClaimed = s.getInt("dailyClaimed", 0);
        String stars = s.getString("levelCrowns", "");
        java.util.Arrays.fill(missionStars, (byte) 0);
        for (int i = 0; i < stars.length() && i < missionStars.length; i++) {
            int v = stars.charAt(i) - '0';
            if (v >= 0 && v <= 3) missionStars[i] = (byte) v;
        }
        xp = Math.max(0, s.getInt("xp", 0));
        level = Math.max(1, s.getInt("level", 1));
        maxCombo = s.getInt("maxCombo", 0);
        totalPowerups = s.getInt("totalPowerups", 0);
        loginStreak = s.getInt("loginStreak", 0);
        lastLoginClaimDay = s.getInt("lastLoginClaimDay", -1);
        energy.load(s);
        rewardedLifeDay = s.getInt("rwLifeDay", -1);
        rewardedLivesToday = s.getInt("rwLives", 0);
    }

    public void save(KeyValueStore s) {
        s.putInt("version", VERSION);
        s.putInt("diamonds", diamonds);
        s.putInt("bestScore", bestScore);
        s.putInt("bestTimeMs", (int) (bestTime * 1000f));
        s.putInt("totalDiamonds", totalDiamonds);
        s.putInt("totalDodges", totalDodges);
        s.putInt("gamesPlayed", gamesPlayed);
        s.putInt("unlockedSkins", unlockedSkins);
        s.putInt("selectedSkin", selectedSkin);
        s.putInt("levelsDone", missionsCompleted);
        s.putInt("achievements", achievements);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < TOP_N; i++) {
            if (i > 0) sb.append(',');
            sb.append(topScores[i]);
        }
        s.putString("topScores", sb.toString());
        s.putInt("sound", sound ? 1 : 0);
        s.putInt("music", music ? 1 : 0);
        s.putInt("vibration", vibration ? 1 : 0);
        s.putInt("tutorialDone", tutorialDone ? 1 : 0);
        s.putInt("dailyDay", dailyDay);
        for (int i = 0; i < DailyMissions.PER_DAY; i++) {
            s.putInt("dailyType" + i, dailyTypes[i]);
            s.putInt("dailyProg" + i, dailyProgress[i]);
        }
        s.putInt("dailyClaimed", dailyClaimed);
        StringBuilder st = new StringBuilder(missionStars.length);
        for (byte b : missionStars) st.append((char) ('0' + b));
        s.putString("levelCrowns", st.toString());
        s.putInt("xp", xp);
        s.putInt("level", level);
        s.putInt("maxCombo", maxCombo);
        s.putInt("totalPowerups", totalPowerups);
        s.putInt("loginStreak", loginStreak);
        s.putInt("lastLoginClaimDay", lastLoginClaimDay);
        energy.save(s);
        s.putInt("rwLifeDay", rewardedLifeDay);
        s.putInt("rwLives", rewardedLivesToday);
        s.apply();
    }

    public void resetAll() {
        Progress fresh = new Progress();
        diamonds = 0; bestScore = 0; bestTime = 0; totalDiamonds = 0; totalDodges = 0; gamesPlayed = 0;
        unlockedSkins = 1; selectedSkin = 0; missionsCompleted = 0; achievements = 0;
        java.util.Arrays.fill(topScores, 0);
        sound = fresh.sound; music = fresh.music; vibration = fresh.vibration;
        tutorialDone = false;
        dailyDay = -1; dailyClaimed = 0;
        java.util.Arrays.fill(dailyProgress, 0);
        java.util.Arrays.fill(missionStars, (byte) 0);
        xp = 0; level = 1; maxCombo = 0; totalPowerups = 0; loginStreak = 0; lastLoginClaimDay = -1;
        energy.lives = Energy.MAX;
        energy.progressMs = 0;
    }

    // ---------- Günlük ----------
    /** Gün değiştiyse günlük görevleri yeniler. */
    public boolean ensureDaily(int epochDay) {
        if (dailyDay == epochDay) return false;
        dailyDay = epochDay;
        DailyMissions.typesFor(epochDay, dailyTypes);
        java.util.Arrays.fill(dailyProgress, 0);
        dailyClaimed = 0;
        return true;
    }

    public boolean dailyDone(int i) {
        return dailyProgress[i] >= DailyMissions.target(dailyTypes[i], dailyDay);
    }

    public boolean dailyIsClaimed(int i) {
        return (dailyClaimed & (1 << i)) != 0;
    }

    /** Ödül alınabiliyorsa elmasları ekler. */
    public int claimDaily(int i) {
        if (!dailyDone(i) || dailyIsClaimed(i)) return 0;
        int r = DailyMissions.reward(dailyTypes[i], dailyDay);
        dailyClaimed |= 1 << i;
        diamonds += r;
        return r;
    }

    public int claimableDailyCount() {
        int n = 0;
        for (int i = 0; i < DailyMissions.PER_DAY; i++) if (dailyDone(i) && !dailyIsClaimed(i)) n++;
        return n;
    }

    // ---------- Seviye / XP ----------
    public static int xpForLevel(int level) {
        return 80 + 40 * level;
    }

    public static int levelReward(int newLevel) {
        return 10 + 3 * newLevel;
    }

    /** XP ekler, atlanan seviye sayısını döndürür ve seviye ödüllerini verir. */
    public int addXp(int amount, Report out) {
        xp += Math.max(0, amount);
        int ups = 0;
        while (xp >= xpForLevel(level)) {
            xp -= xpForLevel(level);
            level++;
            ups++;
            int r = levelReward(level);
            diamonds += r;
            if (out != null) out.levelReward += r;
        }
        return ups;
    }

    // ---------- Günlük giriş ödülü ----------
    public boolean loginAvailable(int epochDay) {
        return lastLoginClaimDay != epochDay;
    }

    /** Bugün alınacak ödülün seri günü (1..7). */
    public int nextLoginDay(int epochDay) {
        int streak = lastLoginClaimDay == epochDay - 1 ? loginStreak + 1 : (lastLoginClaimDay == epochDay ? loginStreak : 1);
        return (streak - 1) % LOGIN_REWARDS.length + 1;
    }

    public int claimLogin(int epochDay) {
        if (!loginAvailable(epochDay)) return 0;
        loginStreak = lastLoginClaimDay == epochDay - 1 ? loginStreak + 1 : 1;
        lastLoginClaimDay = epochDay;
        int r = LOGIN_REWARDS[(loginStreak - 1) % LOGIN_REWARDS.length];
        diamonds += r;
        return r;
    }

    /** Bugün video izleyerek can alınabilir mi? */
    public boolean canWatchForLife(int epochDay, TimeSource t) {
        energy.tick(t);
        int used = rewardedLifeDay == epochDay ? rewardedLivesToday : 0;
        return energy.lives < Energy.MAX && used < AdPolicy.REWARDED_LIVES_PER_DAY;
    }

    public int rewardedLivesLeft(int epochDay) {
        int used = rewardedLifeDay == epochDay ? rewardedLivesToday : 0;
        return Math.max(0, AdPolicy.REWARDED_LIVES_PER_DAY - used);
    }

    /** Video izlendi: +1 can ve günlük sayaç. */
    public boolean onWatchedForLife(int epochDay, TimeSource t) {
        if (!canWatchForLife(epochDay, t)) return false;
        if (rewardedLifeDay != epochDay) { rewardedLifeDay = epochDay; rewardedLivesToday = 0; }
        rewardedLivesToday++;
        return energy.addLife(t);
    }

    /** Elmasla canları doldurur. */
    public boolean buyRefill(TimeSource t) {
        energy.tick(t);
        if (energy.lives >= Energy.MAX || diamonds < REFILL_COST) return false;
        diamonds -= REFILL_COST;
        energy.refill(t);
        return true;
    }

    // ---------- Yıldızlar ----------
    public int totalStars() {
        int t = 0;
        for (byte b : missionStars) t += b;
        return t;
    }

    public int chapterStars(int chapter) {
        int t = 0;
        for (int i = chapter * Missions.PER_CHAPTER; i < (chapter + 1) * Missions.PER_CHAPTER; i++) t += missionStars[i];
        return t;
    }

    // ---------- Karakterler ----------
    public boolean isUnlocked(int skin) {
        return (unlockedSkins & (1 << skin)) != 0;
    }

    public boolean buySkin(int skin) {
        if (isUnlocked(skin)) return false;
        int price = Skins.ALL[skin].price;
        if (diamonds < price) return false;
        diamonds -= price;
        unlockedSkins |= 1 << skin;
        return true;
    }

    public boolean select(int skin) {
        if (!isUnlocked(skin)) return false;
        selectedSkin = skin;
        return true;
    }

    public boolean hasAchievement(int id) {
        return (achievements & (1 << id)) != 0;
    }

    /** Görev açık mı? */
    public boolean missionUnlocked(int index) {
        return index <= missionsCompleted;
    }

    // ---------- Oyun sonu ----------
    public static final class Report {
        public boolean newRecord;
        public int diamondsEarned;
        public int missionReward;
        public int newAchievements;  // bit maskesi
        public int achievementReward;
        public int dailyCompleted;   // bu oyunla tamamlanan günlükler (bit maskesi)
        public int xpGained;
        public int levelUps;
        public int levelReward;
        public int stars;            // bu görevde kazanılan yıldız
        public int newStars;         // önceki rekorun üzerindeki yeni yıldız
    }

    public Report applyRun(RunResult r, int epochDay, Report out) {
        out.newRecord = false;
        out.missionReward = 0;
        out.newAchievements = 0;
        out.achievementReward = 0;
        out.dailyCompleted = 0;
        out.xpGained = 0;
        out.levelUps = 0;
        out.levelReward = 0;
        out.stars = 0;
        out.newStars = 0;
        out.diamondsEarned = r.diamonds;
        totalPowerups += r.powerups;
        if (r.maxCombo > maxCombo) maxCombo = r.maxCombo;

        gamesPlayed++;
        diamonds += r.diamonds;
        totalDiamonds += r.diamonds;
        totalDodges += r.dodges;
        if (r.time > bestTime) bestTime = r.time;

        if (r.mode == RunResult.MODE_ENDLESS) {
            if (r.score > bestScore) {
                out.newRecord = bestScore > 0 || r.score > 0;
                bestScore = r.score;
            }
            insertTop(r.score);
        } else if (r.won) {
            MissionDef m = Missions.ALL[r.missionIndex];
            boolean first = r.missionIndex >= missionsCompleted;
            if (first) missionsCompleted = Math.min(Missions.ALL.length, r.missionIndex + 1);
            out.missionReward = first ? m.reward : Math.max(5, m.reward / 4);
            out.stars = r.stars;
            int old = missionStars[r.missionIndex];
            if (r.stars > old) {
                out.newStars = r.stars - old;
                missionStars[r.missionIndex] = (byte) r.stars;
                out.missionReward += out.newStars * 10; // yeni yıldız başına ödül
            }
            diamonds += out.missionReward;
        }
        out.xpGained = r.score / 10 + (r.won ? 25 : 0) + (int) r.time;
        out.levelUps = addXp(out.xpGained, out);

        ensureDaily(epochDay);
        for (int i = 0; i < DailyMissions.PER_DAY; i++) {
            boolean before = dailyDone(i);
            dailyProgress[i] = DailyMissions.advance(dailyTypes[i], dailyProgress[i], r);
            if (!before && dailyDone(i)) out.dailyCompleted |= 1 << i;
        }
        checkAchievements(r, out);
        return out;
    }

    /** Yeni kazanılan başarıları işler (karakter satın alımından sonra da çağrılır). */
    public int checkAchievements(RunResult last, Report out) {
        int gained = 0;
        for (int id = 0; id < Achievements.COUNT; id++) {
            if (!hasAchievement(id) && Achievements.isMet(id, this, last)) {
                achievements |= 1 << id;
                gained |= 1 << id;
                diamonds += Achievements.REWARDS[id];
                if (out != null) out.achievementReward += Achievements.REWARDS[id];
            }
        }
        if (out != null) out.newAchievements |= gained;
        return gained;
    }

    private void insertTop(int score) {
        if (score <= 0) return;
        for (int i = 0; i < TOP_N; i++) {
            if (score > topScores[i]) {
                System.arraycopy(topScores, i, topScores, i + 1, TOP_N - i - 1);
                topScores[i] = score;
                return;
            }
        }
    }
}
