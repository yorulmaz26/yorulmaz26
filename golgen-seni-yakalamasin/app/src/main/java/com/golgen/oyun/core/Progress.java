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
        missionsCompleted = Math.max(0, Math.min(Missions.ALL.length, s.getInt("missionsCompleted", 0)));
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
        s.putInt("missionsCompleted", missionsCompleted);
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
    }

    public Report applyRun(RunResult r, int epochDay, Report out) {
        out.newRecord = false;
        out.missionReward = 0;
        out.newAchievements = 0;
        out.achievementReward = 0;
        out.dailyCompleted = 0;
        out.diamondsEarned = r.diamonds;

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
            diamonds += out.missionReward;
        }

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
                diamonds += Achievements.REWARD;
                if (out != null) out.achievementReward += Achievements.REWARD;
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
