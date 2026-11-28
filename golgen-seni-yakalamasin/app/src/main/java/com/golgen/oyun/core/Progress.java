package com.golgen.oyun.core;

/**
 * Oyuncunun cihazda saklanan tüm ilerlemesi ve ayarları.
 * Kayıt/okuma KeyValueStore üzerinden yapılır.
 */
public final class Progress {
    /** 3: kayıt imzası + dil tercihi eklendi. Anahtar kümesi değişirse artırılmalıdır (imza doğrulaması sürüme bağlıdır). */
    public static final int VERSION = 3;
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
    /** Oyuncunun seçtiği dil kodu; boşsa ilk açılışta sistem dilinden belirlenir. */
    public String language = "";
    /** Son yüklemede kayıt imzası tutmadı (dosya elle değiştirilmiş): ekonomi değerleri sıfırlandı. */
    public boolean saveTampered;

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

    /** Bölüm modu canları (GameConfig: en fazla 4 can, 20 dk'da bir yenilenir; saat hilesine dayanıklı). */
    public final Energy energy = new Energy();
    public static final int REFILL_COST = GameConfig.LIFE_REFILL_COST;
    /** Ödüllü video ile alınan günlük can sayacı. */
    public int rewardedLifeDay = -1;
    public int rewardedLivesToday;
    /** Görülen en büyük (güvenilir) gün: tarih geri alınarak günlük ödüller tekrar alınamaz. */
    public int maxSeenDay = -1;

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
        language = s.getString("lang", "");
        if (language == null || !L10n.isSupported(language)) language = "";
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
        maxSeenDay = s.getInt("maxSeenDay", -1);
        verifySignature(s);
        // Sürüm 1 (100 bölüm) -> 2 (500 bölüm): kimliği aynı kalıp eşiği büyüyen başarılar yeni koşulu sağlamıyorsa geri alınır
        if (s.getInt("version", 1) < 2) {
            for (int id = 10; id <= 13; id++) {
                if (hasAchievement(id) && !Achievements.isMet(id, this, null)) achievements &= ~(1 << id);
            }
        }
    }

    public void save(KeyValueStore s) {
        Tee tee = new Tee(s);
        write(tee);
        s.putString(SIG_KEY, signature(tee.values));
        s.apply();
    }

    /** Can harcama gibi kritik anlarda: zorla kapatmaya karşı diske eşzamanlı yazar. */
    public void saveNow(KeyValueStore s) {
        Tee tee = new Tee(s);
        write(tee);
        s.putString(SIG_KEY, signature(tee.values));
        s.commit();
    }

    private void write(KeyValueStore s) {
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
        s.putString("lang", language == null ? "" : language);
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
        s.putInt("maxSeenDay", maxSeenDay);
    }

    // ---------- Kayıt imzası (elle değiştirilmiş kayıt tespiti) ----------
    // Kayıttaki tüm anahtar/değerlerin sıralı listesinin SHA-256 özeti saklanır. Kayıt dosyası elle
    // değiştirilirse (ör. elmas veya can artırılırsa) imza tutmaz ve ekonomi değerleri (elmas, can)
    // sıfırlanır; ilerleme, karakterler ve rekorlar korunur. Normal oyuncu hiçbir zaman etkilenmez:
    // eski sürümlerden gelen imzasız kayıtlar kabul edilip imzalanır. Not: uygulamayı tersine mühendislikle
    // inceleyen birini durduramaz; amacı sıradan kayıt düzenlemeyi caydırmaktır.
    static final String SIG_KEY = "sig";
    private static final String SIG_SALT = "MagicLumos|golgen|kayit|v3|5c1e7a92";

    static String signature(java.util.SortedMap<String, String> entries) {
        StringBuilder sb = new StringBuilder(4096).append(SIG_SALT);
        for (java.util.Map.Entry<String, String> e : entries.entrySet()) {
            sb.append('\n').append(e.getKey()).append('=').append(e.getValue());
        }
        try {
            byte[] d = java.security.MessageDigest.getInstance("SHA-256").digest(sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(40);
            for (int i = 0; i < 20; i++) hex.append(String.format(java.util.Locale.ROOT, "%02x", d[i]));
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            return "";
        }
    }

    private void verifySignature(KeyValueStore s) {
        saveTampered = false;
        int version = s.getInt("version", 1);
        String stored = s.getString(SIG_KEY, null);
        if (version < 3 && stored == null) return;   // eski (imzasız) kayıt: kabul edilir, bir sonraki kayıtta imzalanır
        if (version > VERSION) return;               // daha yeni sürümün kaydı (uygulama eski sürüme indirildi)
        // Anahtar listesi ve türleri bu sürümün yazdığı kümeden alınır; değerler depodan ham okunur.
        Tee keys = new Tee(null);
        write(keys);
        java.util.TreeMap<String, String> raw = new java.util.TreeMap<>();
        try {
            for (String k : keys.values.keySet()) {
                if (keys.ints.contains(k)) {
                    int v = s.getInt(k, Integer.MIN_VALUE);
                    if (v != Integer.MIN_VALUE) raw.put(k, "i" + v);
                } else {
                    String v = s.getString(k, null);
                    if (v != null) raw.put(k, "s" + v);
                }
            }
        } catch (RuntimeException typeChanged) {
            raw.clear();
        }
        if (stored != null && stored.equals(signature(raw))) return;
        // İmza yok (sürüm 3+) veya tutmuyor: elle değiştirilmiş kayıt
        saveTampered = true;
        diamonds = 0;
        energy.lives = 0;
        energy.progressMs = 0;
        energy.tamperCount++;
    }

    /** Yazılan her değeri hem hedef depoya iletir hem de imza için kaydeder. */
    private static final class Tee implements KeyValueStore {
        final KeyValueStore target;
        final java.util.TreeMap<String, String> values = new java.util.TreeMap<>();
        final java.util.HashSet<String> ints = new java.util.HashSet<>();
        Tee(KeyValueStore target) { this.target = target; }
        @Override public int getInt(String key, int def) { return def; }
        @Override public String getString(String key, String def) { return def; }
        @Override public void putInt(String key, int value) {
            values.put(key, "i" + value);
            ints.add(key);
            if (target != null) target.putInt(key, value);
        }
        @Override public void putString(String key, String value) {
            values.put(key, "s" + value);
            if (target != null) target.putString(key, value);
        }
        @Override public void apply() { }
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

    /**
     * Günlük ödüller için güvenilir gün: telefon saatinden değil, can sistemindeki monoton saatle ilerleyen
     * güvenilir zamandan hesaplanır (aynı açılışta tarih değiştirmek etkisizdir) ve asla geriye gitmez.
     */
    public int trustedDay(TimeSource t, java.time.ZoneId zone) {
        energy.tick(t);
        int day = (int) java.time.Instant.ofEpochMilli(energy.lastWall).atZone(zone).toLocalDate().toEpochDay();
        if (day < maxSeenDay) return maxSeenDay;
        maxSeenDay = day;
        return day;
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
