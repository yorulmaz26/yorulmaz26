package com.isikyolu.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Oyun içi ekonomi: Işık Kristali (yaygın) ve Prizma Taşı (nadir). Gerçek para yoktur.
 *
 * Bilinçli olarak "zor" bir ekonomi: kazanç yavaştır, aynı gün tekrar alınan ürünün fiyatı yükselir, günlük
 * alım sınırları vardır, bazı görünümler ilerleme ister. Gün, saat hilesine karşı can sisteminin gördüğü en ileri
 * duvar saatiyle belirlenir: saati geri almak sınırları ya da teklifleri sıfırlamaz.
 */
public final class Economy {
    public static final int KR = 0, GEM = 1;
    public static final int START_CRYSTALS = 50;
    static final long DAY_MS = 86_400_000L;

    public enum Kind { HINT, HINT3, TICKET, SHIELD, LIFE, LIFE_FULL, CONVERT, MIRROR, BOARD, BEAM }

    public static final class Item {
        public final String id, name, description;
        public final int category; // 0 güçlendirici, 1 can, 2 görünüm
        public final Kind kind;
        public final int currency, basePrice, step, dailyLimit, unlockLevel;
        /** Görünüm ürünleri için anahtar (ör. "mirror_gold"). */
        public final String skin;

        Item(String id, int category, Kind kind, String name, String description, int currency, int basePrice, int step,
             int dailyLimit, int unlockLevel, String skin) {
            this.id = id; this.category = category; this.kind = kind; this.name = name; this.description = description;
            this.currency = currency; this.basePrice = basePrice; this.step = step; this.dailyLimit = dailyLimit;
            this.unlockLevel = unlockLevel; this.skin = skin;
        }

        public boolean cosmetic() { return kind == Kind.MIRROR || kind == Kind.BOARD || kind == Kind.BEAM; }
    }

    public static final int CAT_BOOST = 0, CAT_LIVES = 1, CAT_LOOK = 2;

    public static final List<Item> ITEMS = Collections.unmodifiableList(Arrays.asList(
            new Item("hint1", CAT_BOOST, Kind.HINT, "İpucu", "Bir sonraki doğru hamleyi gösterir.", KR, 60, 20, 5, 0, null),
            new Item("hint3", CAT_BOOST, Kind.HINT3, "İpucu Paketi ×3", "Üç ipucu, tek tek almaktan ucuz.", KR, 150, 0, 1, 0, null),
            new Item("ticket", CAT_BOOST, Kind.TICKET, "Ek Hamle Bileti", "Hamle hakkın bitince +5 hak ile devam et.", KR, 80, 25, 3, 0, null),
            new Item("shield", CAT_BOOST, Kind.SHIELD, "Can Kalkanı", "Kaybedilen bir sonraki denemede canın geri gelir.", GEM, 2, 0, 2, 0, null),
            new Item("convert", CAT_BOOST, Kind.CONVERT, "Prizma Taşı", "500 kristali 1 Prizma Taşı'na çevir.", KR, 500, 0, 1, 0, null),
            new Item("life1", CAT_LIVES, Kind.LIFE, "+1 Can", "Hemen bir can kazan.", KR, 45, 25, 4, 0, null),
            new Item("lifefull", CAT_LIVES, Kind.LIFE_FULL, "Canları Doldur", "Bütün canları doldur.", KR, 140, 0, 1, 0, null),
            new Item("mirror_gold", CAT_LOOK, Kind.MIRROR, "Altın Aynalar", "Altın çerçeveli, sıcak ışıltılı aynalar.", KR, 400, 0, 0, 20, "mirror_gold"),
            new Item("mirror_obsidian", CAT_LOOK, Kind.MIRROR, "Obsidyen Aynalar", "Siyah cam, mor kenarlı aynalar.", KR, 700, 0, 0, 60, "mirror_obsidian"),
            new Item("mirror_ice", CAT_LOOK, Kind.MIRROR, "Buz Kristali", "Buz mavisi, buğulu aynalar.", KR, 1000, 0, 0, 120, "mirror_ice"),
            new Item("mirror_rainbow", CAT_LOOK, Kind.MIRROR, "Gökkuşağı Aynalar", "Renk değiştiren gökkuşağı camı.", GEM, 3, 0, 0, 200, "mirror_rainbow"),
            new Item("board_ocean", CAT_LOOK, Kind.BOARD, "Okyanus Tahtası", "Derin deniz mavisi kareler.", KR, 500, 0, 0, 30, "board_ocean"),
            new Item("board_sunset", CAT_LOOK, Kind.BOARD, "Gün Batımı", "Mor ve turuncu tonlarında tahta.", KR, 800, 0, 0, 80, "board_sunset"),
            new Item("board_forest", CAT_LOOK, Kind.BOARD, "Zümrüt Orman", "Yeşil tonlarında doğa tahtası.", KR, 1100, 0, 0, 150, "board_forest"),
            new Item("board_galaxy", CAT_LOOK, Kind.BOARD, "Galaksi", "Yıldızlı mor uzay tahtası.", GEM, 5, 0, 0, 250, "board_galaxy"),
            new Item("beam_thick", CAT_LOOK, Kind.BEAM, "Kalın Işın", "Daha kalın, daha parlak ışınlar.", KR, 600, 0, 0, 40, "beam_thick"),
            new Item("beam_stardust", CAT_LOOK, Kind.BEAM, "Yıldız Tozu", "Işın boyunca akan yıldız parçacıkları.", KR, 900, 0, 0, 100, "beam_stardust"),
            new Item("beam_plasma", CAT_LOOK, Kind.BEAM, "Plazma", "Titreşen çift çekirdekli enerji ışını.", GEM, 4, 0, 0, 300, "beam_plasma")
    ));

    public static final String DEFAULT_MIRROR = "mirror_silver", DEFAULT_BOARD = "board_night", DEFAULT_BEAM = "beam_classic";

    private Economy() {}

    public static Item item(String id) {
        for (Item i : ITEMS) if (i.id.equals(id)) return i;
        return null;
    }

    // ---------- gün ----------

    /**
     * Güvenilir gün (yerel saat diliminde): duvar saati ile daha önce görülen en ileri zamanın büyüğü.
     * Saati geri almak günü geri götürmez.
     */
    public static int trustedDay(Progress p, long wall, long zoneOffsetMs) {
        long t = Math.max(wall, p.lifeMaxWall);
        return (int) Math.floorDiv(t + zoneOffsetMs, DAY_MS);
    }

    /** Güvenilir zamandan (Lives.trustedWall) yerel gün. */
    public static int dayOf(long trustedWall, long zoneOffsetMs) {
        return (int) Math.floorDiv(trustedWall + zoneOffsetMs, DAY_MS);
    }

    /** Güvenilir zamana göre bir sonraki gün başına kalan süre. */
    public static long msUntilNextDayAt(long trustedWall, long zoneOffsetMs) {
        return DAY_MS - Math.floorMod(trustedWall + zoneOffsetMs, DAY_MS);
    }

    /** Gün değiştiyse günlük alım sayaçları sıfırlanır. */
    static void rollDay(Progress p, int day) {
        if (p.shopDay != day) {
            p.shopDay = day;
            p.shopBuys.clear();
        }
    }

    /** Bir sonraki gün başına kalan süre. */
    public static long msUntilNextDay(Progress p, long wall, long zoneOffsetMs) {
        long t = Math.max(wall, p.lifeMaxWall) + zoneOffsetMs;
        return DAY_MS - Math.floorMod(t, DAY_MS);
    }

    public static int boughtToday(Progress p, String key, int day) {
        rollDay(p, day);
        Integer n = p.shopBuys.get(key);
        return n == null ? 0 : n;
    }

    // ---------- fiyat ve satın alma ----------

    /** Bugünkü fiyat: her alımda {@code step} kadar artar. */
    public static int price(Progress p, Item it, int day) {
        return it.basePrice + it.step * boughtToday(p, it.id, day);
    }

    public static int balance(Progress p, int currency) { return currency == GEM ? p.gems : p.crystals; }

    /** Satın alınamıyorsa nedenini, alınabiliyorsa null döndürür. */
    public static String blockReason(Progress p, Item it, int day, int price) {
        if (it.cosmetic() && p.owned.contains(it.skin)) return "Sahipsin";
        if (it.unlockLevel > 0 && p.stars[Math.min(it.unlockLevel, Progress.LEVEL_COUNT)] == 0)
            return "Bölüm " + it.unlockLevel + "'u bitir";
        if (it.dailyLimit > 0 && boughtToday(p, it.id, day) >= it.dailyLimit) return "Bugünlük sınır doldu";
        if ((it.kind == Kind.LIFE || it.kind == Kind.LIFE_FULL) && p.lives >= Lives.MAX) return "Canların dolu";
        if (balance(p, it.currency) < price) return it.currency == GEM ? "Yetersiz Prizma Taşı" : "Yetersiz kristal";
        return null;
    }

    public static String blockReason(Progress p, Item it, int day) { return blockReason(p, it, day, price(p, it, day)); }

    /** Ürünü satın alır. @return başarılıysa true */
    public static boolean buy(Progress p, Item it, int day, Lives.Clock clock) {
        int price = price(p, it, day);
        if (blockReason(p, it, day, price) != null) return false;
        if (it.currency == GEM) p.gems -= price; else p.crystals -= price;
        p.shopBuys.put(it.id, boughtToday(p, it.id, day) + 1);
        p.purchases++;
        grant(p, it, clock);
        return true;
    }

    private static void grant(Progress p, Item it, Lives.Clock clock) {
        switch (it.kind) {
            case HINT: p.hints += 1; break;
            case HINT3: p.hints += 3; break;
            case TICKET: p.tickets += 1; break;
            case SHIELD: p.shields += 1; break;
            case CONVERT: p.gems += 1; break;
            case LIFE: addLives(p, 1, clock); break;
            case LIFE_FULL: addLives(p, Lives.MAX, clock); break;
            default:
                p.owned.add(it.skin);
                equip(p, it);
        }
    }

    private static void addLives(Progress p, int n, Lives.Clock clock) {
        if (clock != null) Lives.update(p, clock);
        p.lives = Math.min(Lives.MAX, p.lives + n);
        if (clock != null) Lives.update(p, clock); // dolduysa sayaç sıfırlanır
    }

    public static void equip(Progress p, Item it) {
        if (!it.cosmetic() || !p.owned.contains(it.skin)) return;
        if (it.kind == Kind.MIRROR) p.mirrorSkin = it.skin;
        else if (it.kind == Kind.BOARD) p.boardSkin = it.skin;
        else p.beamSkin = it.skin;
    }

    /** Varsayılan görünüme dön. */
    public static void equipDefault(Progress p, Kind kind) {
        if (kind == Kind.MIRROR) p.mirrorSkin = DEFAULT_MIRROR;
        else if (kind == Kind.BOARD) p.boardSkin = DEFAULT_BOARD;
        else if (kind == Kind.BEAM) p.beamSkin = DEFAULT_BEAM;
    }

    public static boolean equipped(Progress p, Item it) {
        return it.skin != null && (it.skin.equals(p.mirrorSkin) || it.skin.equals(p.boardSkin) || it.skin.equals(p.beamSkin));
    }

    // ---------- günlük teklifler ----------

    public static final class Deal {
        public final int index;
        public final Item item;
        public final int discount; // yüzde
        public final int price;

        Deal(int index, Item item, int discount) {
            this.index = index; this.item = item; this.discount = discount;
            this.price = Math.max(1, item.basePrice * (100 - discount) / 100);
        }

        public String key() { return "deal" + index; }
    }

    /** Güne göre deterministik 3 indirimli teklif (her biri tek adet). */
    public static List<Deal> deals(int day) {
        List<Item> pool = new ArrayList<>();
        for (Item i : ITEMS) if (i.kind != Kind.CONVERT && i.kind != Kind.LIFE_FULL) pool.add(i);
        Random r = new Random(day * 2_654_435_761L + 17);
        Collections.shuffle(pool, r);
        List<Deal> out = new ArrayList<>();
        int[] pcts = {20, 25, 30, 35, 40};
        for (int k = 0; k < 3; k++) out.add(new Deal(k, pool.get(k), pcts[r.nextInt(pcts.length)]));
        return out;
    }

    /** Teklifte günlük ürün sınırı uygulanmaz; yalnızca teklifin tek adet stoku vardır. */
    public static String dealBlockReason(Progress p, Deal d, int day) {
        Item it = d.item;
        if (boughtToday(p, d.key(), day) >= 1) return "Tükendi";
        if (it.cosmetic() && p.owned.contains(it.skin)) return "Sahipsin";
        if (it.unlockLevel > 0 && p.stars[Math.min(it.unlockLevel, Progress.LEVEL_COUNT)] == 0) return "Bölüm " + it.unlockLevel + "'u bitir";
        if ((it.kind == Kind.LIFE || it.kind == Kind.LIFE_FULL) && p.lives >= Lives.MAX) return "Canların dolu";
        if (balance(p, it.currency) < d.price) return it.currency == GEM ? "Yetersiz Prizma Taşı" : "Yetersiz kristal";
        return null;
    }

    public static boolean buyDeal(Progress p, Deal d, int day, Lives.Clock clock) {
        if (dealBlockReason(p, d, day) != null) return false;
        Item it = d.item;
        if (it.currency == GEM) p.gems -= d.price; else p.crystals -= d.price;
        p.shopBuys.put(d.key(), 1);
        p.purchases++;
        grant(p, it, clock);
        return true;
    }

    // ---------- oyun içi devam ----------

    /** Hamle hakkı bitince +5 hak ile devam fiyatı: aynı denemede her devamda ikiye katlanır. */
    public static int continuePrice(int continuesThisAttempt) { return 80 << Math.min(continuesThisAttempt, 6); }

    public static final int CONTINUE_MOVES = 5;

    /** Devamda verilecek hak: en az 5, ama bölümü bitirmeye yetmeyecekse kalan en az hamle kadar. */
    public static int continueMoves(int remainingOptimal) { return Math.max(CONTINUE_MOVES, remainingOptimal); }

    /** Bilet varsa bilet, yoksa kristal harcar. */
    public static boolean payContinue(Progress p, int continuesThisAttempt) {
        if (p.tickets > 0) { p.tickets--; return true; }
        int price = continuePrice(continuesThisAttempt);
        if (p.crystals < price) return false;
        p.crystals -= price;
        p.purchases++;
        return true;
    }

    /** Deneme kaybedildi: kalkan varsa can geri gelir. @return kalkan kullanıldıysa true */
    public static boolean loseAttempt(Progress p, Lives.Clock clock) {
        boolean had = p.attemptLevel != 0;
        Lives.loseAttempt(p);
        if (had && p.shields > 0) {
            p.shields--;
            addLives(p, 1, clock);
            return true;
        }
        return false;
    }

    // ---------- kazanç ----------

    public static final class Reward {
        public int crystals, gems;
        public final List<String> lines = new ArrayList<>();

        void add(int kr, int gm, String why) {
            crystals += kr; gems += gm;
            lines.add(why);
        }

        public boolean any() { return crystals > 0 || gems > 0; }
    }

    /** Bölüm tamamlandı. oldStars kayıttan önceki yıldızdır. */
    public static Reward onLevelComplete(Progress p, int levelId, int oldStars, int newStars) {
        Reward r = new Reward();
        int world = Chapters.worldOf(levelId);
        if (oldStars == 0) r.add(4 + 2 * (world - 1), 0, "İlk tamamlama");
        if (newStars == 3 && oldStars < 3) r.add(3, 0, "3 yıldız");
        int ch = Chapters.chapterOf(levelId);
        if (!p.perfectChapters.contains(ch)) {
            boolean perfect = true;
            for (int i = (ch - 1) * 10 + 1; i <= ch * 10; i++) if (p.stars[i] < 3) { perfect = false; break; }
            if (perfect) { p.perfectChapters.add(ch); r.add(0, 1, "Grup 30/30 yıldız"); }
        }
        apply(p, r);
        return r;
    }

    /** Günlük bulmaca o gün ilk kez çözüldü. */
    public static Reward onDailyComplete(Progress p, int streak) {
        Reward r = new Reward();
        r.add(20 + 3 * Math.min(Math.max(0, streak - 1), 10), 0, "Günlük bulmaca");
        apply(p, r);
        return r;
    }

    /** Başarı ödülü: 30 kristal; büyük başarılar ayrıca Prizma Taşı verir. */
    public static Reward onAchievement(Progress p, Achievements.Def d) {
        Reward r = new Reward();
        int gems = 0;
        switch (d.id) {
            case "kristal": case "gece": case "yildiz_ustasi": case "tapinak": case "yildiz_krali": case "mukemmel100": case "aylik": gems = 1; break;
            case "efsane": case "kusursuz": gems = 3; break;
            default: break;
        }
        r.add(30, gems, d.title);
        apply(p, r);
        return r;
    }

    private static void apply(Progress p, Reward r) {
        p.crystals += r.crystals;
        p.gems += r.gems;
        p.crystalsEarned += r.crystals;
    }

    /** Bütün oyun boyunca bölümlerden kazanılabilecek en fazla kristal (denge testi için). */
    public static int maxLevelIncome() {
        int s = 0;
        for (int id = 1; id <= Progress.LEVEL_COUNT; id++) s += 4 + 2 * (Chapters.worldOf(id) - 1) + 3;
        return s;
    }

    public static int cosmeticCost(int currency) {
        int s = 0;
        for (Item i : ITEMS) if (i.cosmetic() && i.currency == currency) s += i.basePrice;
        return s;
    }

    public static int cosmeticCount() {
        int n = 0;
        for (Item i : ITEMS) if (i.cosmetic()) n++;
        return n;
    }
}
