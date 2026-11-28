package com.isikyolu.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Oyuncu ilerlemesi. Platformdan bağımsızdır; JSON metni olarak kaydedilir. */
public final class Progress {
    public static final int LEVEL_COUNT = 500;
    public static final int START_HINTS = 3;
    /** Her bu kadar bölüm tamamlandığında +1 ipucu. */
    public static final int HINT_EVERY = 10;

    /** stars[1..500]; 0 = tamamlanmadı. */
    public final int[] stars = new int[LEVEL_COUNT + 1];
    /** En iyi hamle sayısı (0 = yok). */
    public final int[] bestMoves = new int[LEVEL_COUNT + 1];
    public int hints = START_HINTS;
    public int hintsUsedTotal;
    public int levelsWithoutHint;
    public int undoCount;
    public final Set<String> dailyDone = new LinkedHashSet<>();
    public final Set<String> achievements = new LinkedHashSet<>();
    public boolean sound = true, music = true, vibration = true;
    public boolean tutorialSeen;
    public int lastPlayed = 1;

    // can sistemi (bkz. Lives)
    public int lives = Lives.MAX;
    public long lifeBaseWall, lifeBaseElapsed = Long.MIN_VALUE, lifeBaseNet = -1, lifeMaxWall;
    public int lifeBaseBoot = -1;
    /** Gün hesabı için güvenilir zaman (bkz. Lives.trustedWall). */
    public long trustWall, trustElapsed;
    public int trustBoot = -1;
    /** Canı düşülmüş, henüz bitmemiş deneme (0 = yok). */
    public int attemptLevel;

    // ekonomi (bkz. Economy)
    public int crystals = Economy.START_CRYSTALS, gems, tickets, shields, purchases, crystalsEarned;
    public final Set<String> owned = new LinkedHashSet<>();
    public String mirrorSkin = Economy.DEFAULT_MIRROR, boardSkin = Economy.DEFAULT_BOARD, beamSkin = Economy.DEFAULT_BEAM;
    public int shopDay = -1;
    /** Görülen en büyük yerel gün (bkz. Economy.monotonicDay). */
    public int maxDay = -1;
    public final Map<String, Integer> shopBuys = new LinkedHashMap<>();
    public final Set<Integer> perfectChapters = new LinkedHashSet<>();

    // reklamlar (bkz. AdPolicy)
    /** Son geçiş reklamından beri biten bölüm; son geçiş reklamı ve son ödüllü video zamanı (güvenilir). */
    public int adLevelsSince;
    public long adLastWall, adLastReward;
    public int adRewardDay = -1, adVideos;
    public final Map<String, Integer> adRewards = new LinkedHashMap<>();

    public boolean isUnlocked(int level) {
        return level == 1 || (level >= 2 && level <= LEVEL_COUNT && stars[level - 1] > 0);
    }

    public int completedCount() {
        int n = 0;
        for (int i = 1; i <= LEVEL_COUNT; i++) if (stars[i] > 0) n++;
        return n;
    }

    public int totalStars() {
        int n = 0;
        for (int i = 1; i <= LEVEL_COUNT; i++) n += stars[i];
        return n;
    }

    public int threeStarCount() {
        int n = 0;
        for (int i = 1; i <= LEVEL_COUNT; i++) if (stars[i] == 3) n++;
        return n;
    }

    /** İlk tamamlanmamış açık bölüm. */
    public int nextLevel() {
        for (int i = 1; i <= LEVEL_COUNT; i++) if (stars[i] == 0) return i;
        return LEVEL_COUNT;
    }

    /**
     * Bölüm sonucunu kaydeder. Yıldız ve hamle rekoru yalnızca iyileşirse güncellenir.
     * @return ilk kez tamamlandıysa true
     */
    public boolean recordLevel(int level, int stars, int moves, boolean usedHint) {
        boolean first = this.stars[level] == 0;
        if (stars > this.stars[level]) this.stars[level] = stars;
        if (bestMoves[level] == 0 || moves < bestMoves[level]) bestMoves[level] = moves;
        if (first) {
            if (!usedHint) levelsWithoutHint++;
            if (completedCount() % HINT_EVERY == 0) hints++; // ödül: her 10 tamamlanan bölümde +1 ipucu
        }
        lastPlayed = Math.min(LEVEL_COUNT, level + 1);
        return first;
    }

    /** Bir sonraki ipucu ödülüne kalan bölüm sayısı. */
    public int levelsUntilNextHint() { return HINT_EVERY - completedCount() % HINT_EVERY; }

    /** Bir dünyadaki (1–5) tamamlanan bölüm sayısı. */
    public int completedInWorld(int world) {
        int n = 0;
        for (int i = (world - 1) * 100 + 1; i <= world * 100 && i <= LEVEL_COUNT; i++) if (stars[i] > 0) n++;
        return n;
    }

    public boolean useHint() {
        if (hints <= 0) return false;
        hints--;
        hintsUsedTotal++;
        return true;
    }

    /** Günlük bulmacada art arda gün sayısı (bugünden geriye). */
    public int dailyStreak(int todayEpochDay) {
        int s = 0;
        while (dailyDone.contains(DailyPuzzle.keyFor(todayEpochDay - s))) s++;
        return s;
    }

    /** Gösterilecek seri: bugünkü henüz çözülmediyse dünkü seri hâlâ sürüyor sayılır (gün içinde 0 görünmez). */
    public int currentDailyStreak(int todayEpochDay) {
        return dailyDone.contains(DailyPuzzle.keyFor(todayEpochDay)) ? dailyStreak(todayEpochDay) : dailyStreak(todayEpochDay - 1);
    }

    public void resetAll() {
        Progress fresh = new Progress();
        System.arraycopy(fresh.stars, 0, stars, 0, stars.length);
        System.arraycopy(fresh.bestMoves, 0, bestMoves, 0, bestMoves.length);
        hints = START_HINTS; hintsUsedTotal = 0; levelsWithoutHint = 0; undoCount = 0;
        dailyDone.clear(); achievements.clear(); tutorialSeen = false; lastPlayed = 1;
        lives = Lives.MAX; attemptLevel = 0; lifeBaseElapsed = Long.MIN_VALUE;
        crystals = Economy.START_CRYSTALS; gems = 0; tickets = 0; shields = 0; purchases = 0; crystalsEarned = 0;
        owned.clear(); perfectChapters.clear(); shopBuys.clear(); shopDay = -1; maxDay = -1;
        adLevelsSince = 0; adLastWall = 0; adLastReward = 0; adRewardDay = -1; adVideos = 0; adRewards.clear();
        mirrorSkin = Economy.DEFAULT_MIRROR; boardSkin = Economy.DEFAULT_BOARD; beamSkin = Economy.DEFAULT_BEAM;
    }

    private String serializeJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("v", 1);
        List<Object> s = new ArrayList<>(), b = new ArrayList<>();
        for (int i = 1; i <= LEVEL_COUNT; i++) { s.add(stars[i]); b.add(bestMoves[i]); }
        m.put("stars", s);
        m.put("best", b);
        m.put("hints", hints);
        m.put("hintsUsed", hintsUsedTotal);
        m.put("noHint", levelsWithoutHint);
        m.put("undo", undoCount);
        m.put("daily", new ArrayList<Object>(dailyDone));
        m.put("ach", new ArrayList<Object>(achievements));
        m.put("sound", sound);
        m.put("music", music);
        m.put("vib", vibration);
        m.put("tut", tutorialSeen);
        m.put("last", lastPlayed);
        m.put("lives", lives);
        m.put("lbw", lifeBaseWall);
        m.put("lbe", lifeBaseElapsed);
        m.put("lbb", lifeBaseBoot);
        m.put("lbn", lifeBaseNet);
        m.put("lmw", lifeMaxWall);
        m.put("att", attemptLevel);
        m.put("tw", trustWall);
        m.put("te", trustElapsed);
        m.put("tb", trustBoot);
        m.put("kr", crystals);
        m.put("gem", gems);
        m.put("tik", tickets);
        m.put("shd", shields);
        m.put("buyN", purchases);
        m.put("krE", crystalsEarned);
        m.put("own", new ArrayList<Object>(owned));
        m.put("skM", mirrorSkin);
        m.put("skB", boardSkin);
        m.put("skL", beamSkin);
        m.put("shopDay", shopDay);
        m.put("md", maxDay);
        m.put("shopBuys", new LinkedHashMap<String, Object>(shopBuys));
        m.put("perf", new ArrayList<Object>(perfectChapters));
        m.put("adN", adLevelsSince);
        m.put("adT", adLastWall);
        m.put("adV", adLastReward);
        m.put("adD", adRewardDay);
        m.put("adC", adVideos);
        m.put("adR", new LinkedHashMap<String, Object>(adRewards));
        m.put("sv", SAVE_VERSION);
        return Json.write(m);
    }

    /**
     * Kayıt imzası: kayıt elle değiştirilirse fark edilir (çevrimdışı oyunda caydırıcı koruma).
     * Kaydın kendisi düz JSON kalır (eski sürümler de okuyabilir; bilinmeyen "sv" alanını yok sayar); imza ayrı bir
     * anahtarda saklanır (bkz. Game). Eski (1.7.x) imzasız kayıtlar cezasız kabul edilir.
     */
    public static final int SAVE_VERSION = 2;
    private transient int loadedVersion;
    private static final String SIG_SALT = "IsikYolu|MagicLumos|kayit|v1";
    /** Yalnızca 1.8.0 geliştirme sürümünde kullanılan, imzanın JSON'a eklendiği biçim (geriye dönük okunur). */
    private static final String SIG_MARK = "\n#sig:";

    /** JSON metninin imzası (SHA-256'nın ilk 128 biti, onaltılık). */
    public static String signature(String json) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest((SIG_SALT + (json == null ? "" : json)).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int k = 0; k < 16; k++) sb.append(String.format(java.util.Locale.ROOT, "%02x", h[k]));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            return "";
        }
    }

    /** Düz JSON kayıt (imza ayrıca {@link #signature(String)} ile yazılır). */
    public String serialize() { return serializeJson(); }

    /** Son yüklemede kayıt imzası tutmadı mı (kayıt elle değiştirilmiş). Bir kez bildirilir. */
    public transient boolean saveTampered;

    /** Zamanla yenilenen (ödül/satın alma dışı) can sayısı; arayüz "canların doldu" bildirimi için okur ve sıfırlar. */
    public transient int regenGained;

    /** İmza denetimi olmadan yükler (testler ve araçlar için). Bozuk veya boş kayıtta varsayılan ilerleme döner. */
    public static Progress deserialize(String text) {
        if (text != null) {
            int at = text.lastIndexOf(SIG_MARK);
            if (at >= 0) return load(text.substring(0, at), text.substring(at + SIG_MARK.length()).trim());
        }
        return deserializeJson(text); // imza denetimi yok
    }

    /**
     * Kaydı imzasıyla yükler.
     * <ul>
     *   <li>İmza tutuyor: olduğu gibi.</li>
     *   <li>İmza yok ve kayıt eski biçimde (sv &lt; 2): 1.7.x'ten güncelleme, cezasız kabul edilir.</li>
     *   <li>İmza tutmuyor ya da yeni biçimde imza silinmiş: kayıt değiştirilmiş sayılır. Bölüm ilerlemesi, yıldızlar,
     *       başarılar ve görünümler korunur; elle değiştirilebilecek can, süre ve oyun içi para güvenli başlangıç
     *       değerlerine döner (normal oyuncu bu duruma düşmez).</li>
     * </ul>
     */
    public static Progress load(String json, String sig) {
        Progress p = deserializeJson(json);
        if (json == null || json.isEmpty()) return p;
        boolean tampered = sig != null ? !sig.equals(signature(json)) : p.loadedVersion >= SAVE_VERSION;
        if (tampered) {
            p.lives = 0;
            p.lifeBaseElapsed = Long.MIN_VALUE;
            p.lifeMaxWall = 0;
            p.attemptLevel = 0;
            p.crystals = Economy.START_CRYSTALS;
            p.gems = 0;
            p.hints = Math.min(p.hints, START_HINTS);
            p.tickets = 0;
            p.shields = 0;
            p.saveTampered = true;
        }
        return p;
    }

    @SuppressWarnings("unchecked")
    private static Progress deserializeJson(String text) {
        Progress p = new Progress();
        if (text == null || text.isEmpty()) return p;
        try {
            Map<String, Object> m = (Map<String, Object>) Json.parse(text);
            List<Object> s = (List<Object>) m.get("stars"), b = (List<Object>) m.get("best");
            for (int i = 1; i <= LEVEL_COUNT; i++) {
                if (s != null && i - 1 < s.size()) p.stars[i] = Math.max(0, Math.min(3, ((Number) s.get(i - 1)).intValue()));
                if (b != null && i - 1 < b.size()) p.bestMoves[i] = ((Number) b.get(i - 1)).intValue();
            }
            p.hints = Json.getInt(m, "hints", START_HINTS);
            p.hintsUsedTotal = Json.getInt(m, "hintsUsed", 0);
            p.levelsWithoutHint = Json.getInt(m, "noHint", 0);
            p.undoCount = Json.getInt(m, "undo", 0);
            Object d = m.get("daily");
            if (d instanceof List) for (Object o : (List<Object>) d) p.dailyDone.add(String.valueOf(o));
            Object a = m.get("ach");
            if (a instanceof List) for (Object o : (List<Object>) a) p.achievements.add(String.valueOf(o));
            p.sound = Json.getBool(m, "sound", true);
            p.music = Json.getBool(m, "music", true);
            p.vibration = Json.getBool(m, "vib", true);
            p.tutorialSeen = Json.getBool(m, "tut", false);
            p.lastPlayed = Json.getInt(m, "last", 1);
            p.lives = Math.max(0, Math.min(Lives.MAX, Json.getInt(m, "lives", Lives.MAX)));
            p.lifeBaseWall = Json.getLong(m, "lbw", 0);
            p.lifeBaseElapsed = Json.getLong(m, "lbe", Long.MIN_VALUE);
            p.lifeBaseBoot = Json.getInt(m, "lbb", -1);
            p.lifeBaseNet = Json.getLong(m, "lbn", -1);
            p.lifeMaxWall = Json.getLong(m, "lmw", 0);
            p.attemptLevel = Json.getInt(m, "att", 0);
            p.loadedVersion = Json.getInt(m, "sv", 1);
            p.trustWall = Json.getLong(m, "tw", 0);
            p.trustElapsed = Json.getLong(m, "te", 0);
            p.trustBoot = Json.getInt(m, "tb", -1);
            p.crystals = Math.max(0, Json.getInt(m, "kr", Economy.START_CRYSTALS));
            p.gems = Math.max(0, Json.getInt(m, "gem", 0));
            p.tickets = Math.max(0, Json.getInt(m, "tik", 0));
            p.shields = Math.max(0, Json.getInt(m, "shd", 0));
            p.purchases = Json.getInt(m, "buyN", 0);
            p.crystalsEarned = Json.getInt(m, "krE", 0);
            Object own = m.get("own");
            if (own instanceof List) for (Object o : (List<Object>) own) p.owned.add(String.valueOf(o));
            p.mirrorSkin = Json.getString(m, "skM", Economy.DEFAULT_MIRROR);
            p.boardSkin = Json.getString(m, "skB", Economy.DEFAULT_BOARD);
            p.beamSkin = Json.getString(m, "skL", Economy.DEFAULT_BEAM);
            p.shopDay = Json.getInt(m, "shopDay", -1);
            p.maxDay = Json.getInt(m, "md", p.shopDay);
            Object sb = m.get("shopBuys");
            if (sb instanceof Map) for (Map.Entry<String, Object> e : ((Map<String, Object>) sb).entrySet())
                if (e.getValue() instanceof Number) p.shopBuys.put(e.getKey(), ((Number) e.getValue()).intValue());
            Object pf = m.get("perf");
            if (pf instanceof List) for (Object o : (List<Object>) pf) if (o instanceof Number) p.perfectChapters.add(((Number) o).intValue());
            p.adLevelsSince = Json.getInt(m, "adN", 0);
            p.adLastWall = Json.getLong(m, "adT", 0);
            p.adLastReward = Json.getLong(m, "adV", 0);
            p.adRewardDay = Json.getInt(m, "adD", -1);
            p.adVideos = Json.getInt(m, "adC", 0);
            Object ar = m.get("adR");
            if (ar instanceof Map) for (Map.Entry<String, Object> e : ((Map<String, Object>) ar).entrySet())
                if (e.getValue() instanceof Number) p.adRewards.put(e.getKey(), ((Number) e.getValue()).intValue());
            return p;
        } catch (RuntimeException e) {
            return new Progress();
        }
    }
}
