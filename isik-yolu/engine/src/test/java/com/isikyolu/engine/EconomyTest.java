package com.isikyolu.engine;

import static org.junit.Assert.*;

import java.util.List;
import org.junit.Test;

/** Market ve ekonomi: fiyat artışı, günlük sınırlar, teklifler, görünümler, kazançlar, hile koruması. */
public class EconomyTest {
    static final long DAY = 86_400_000L;
    static final long T0 = 1_800_000_000_000L;

    private static Progress rich(int kr, int gem) {
        Progress p = new Progress();
        p.crystals = kr; p.gems = gem;
        return p;
    }

    private static Economy.Item it(String id) { return Economy.item(id); }

    @Test public void startingBalance() {
        Progress p = new Progress();
        assertEquals(50, p.crystals);
        assertEquals(0, p.gems);
        assertEquals(Economy.DEFAULT_MIRROR, p.mirrorSkin);
    }

    @Test public void priceEscalatesAndDailyLimit() {
        Progress p = rich(10_000, 0);
        int day = 100;
        Economy.Item h = it("hint1");
        int[] expect = {60, 80, 100, 120, 140};
        int hints = p.hints;
        for (int k = 0; k < 5; k++) {
            assertEquals(expect[k], Economy.price(p, h, day));
            assertTrue(Economy.buy(p, h, day, null));
        }
        assertEquals(hints + 5, p.hints);
        assertEquals(10_000 - 500, p.crystals);
        assertEquals(Economy.BLOCK_DAILY_LIMIT, Economy.blockReason(p, h, day));
        assertFalse(Economy.buy(p, h, day, null));
        // ertesi gün sıfırlanır
        assertEquals(60, Economy.price(p, h, day + 1));
        assertNull(Economy.blockReason(p, h, day + 1));
    }

    @Test public void notEnoughMoney() {
        Progress p = rich(59, 0);
        assertEquals(Economy.BLOCK_NOT_ENOUGH_CRYSTALS, Economy.blockReason(p, it("hint1"), 1));
        assertFalse(Economy.buy(p, it("hint1"), 1, null));
        assertEquals(59, p.crystals);
        assertEquals(Economy.BLOCK_NOT_ENOUGH_GEMS, Economy.blockReason(p, it("shield"), 1));
    }

    @Test public void trustedDayIgnoresClockGoingBack() {
        Progress p = new Progress();
        p.lifeMaxWall = T0 + 3 * DAY; // daha önce görülen en ileri zaman
        int d1 = Economy.trustedDay(p, T0 + 3 * DAY, 0);
        int dBack = Economy.trustedDay(p, T0, 0); // kullanıcı saati 3 gün geri aldı
        assertEquals(d1, dBack);
        assertEquals(d1 + 1, Economy.trustedDay(p, T0 + 4 * DAY, 0));
        assertTrue(Economy.msUntilNextDay(p, T0, 0) <= DAY);
    }

    @Test public void livesPurchaseNeedsMissingLife() {
        Progress p = rich(1000, 0);
        assertEquals(Economy.BLOCK_LIVES_FULL, Economy.blockReason(p, it("life1"), 5));
        p.lives = 1;
        Lives.Clock c = new Lives.Clock(T0, 100_000, 3, -1);
        Lives.update(p, c);
        assertTrue(Economy.buy(p, it("life1"), 5, c));
        assertEquals(2, p.lives);
        assertEquals(70, Economy.price(p, it("life1"), 5));
        assertTrue(Economy.buy(p, it("lifefull"), 5, c));
        assertEquals(Lives.MAX, p.lives);
        assertEquals(Economy.BLOCK_LIVES_FULL, Economy.blockReason(p, it("life1"), 5));
    }

    @Test public void cosmeticsOneTimeEquipAndUnlock() {
        Progress p = rich(5000, 10);
        Economy.Item gold = it("mirror_gold");
        assertEquals(Economy.BLOCK_LOCKED, Economy.blockReason(p, gold, 1));
        assertEquals(20, gold.unlockLevel);
        for (int i = 1; i <= 20; i++) p.stars[i] = 1;
        assertTrue(Economy.buy(p, gold, 1, null));
        assertEquals("mirror_gold", p.mirrorSkin);
        assertTrue(Economy.equipped(p, gold));
        assertEquals(Economy.BLOCK_OWNED, Economy.blockReason(p, gold, 2));
        Economy.equipDefault(p, Economy.Kind.MIRROR);
        assertEquals(Economy.DEFAULT_MIRROR, p.mirrorSkin);
        Economy.equip(p, gold);
        assertEquals("mirror_gold", p.mirrorSkin);
        // sahip olunmayan görünüm takılamaz
        Economy.equip(p, it("board_ocean"));
        assertEquals(Economy.DEFAULT_BOARD, p.boardSkin);
        // taşla satılan görünüm
        for (int i = 1; i <= 300; i++) p.stars[i] = 1;
        assertTrue(Economy.buy(p, it("beam_plasma"), 1, null));
        assertEquals(6, p.gems);
        assertEquals("beam_plasma", p.beamSkin);
    }

    @Test public void dailyDealsDeterministicDiscountedSingleStock() {
        List<Economy.Deal> a = Economy.deals(400), b = Economy.deals(400), c = Economy.deals(401);
        assertEquals(3, a.size());
        for (int i = 0; i < 3; i++) {
            assertEquals(a.get(i).item.id, b.get(i).item.id);
            assertTrue(a.get(i).price < a.get(i).item.basePrice);
            assertTrue(a.get(i).discount >= 20 && a.get(i).discount <= 40);
        }
        boolean differs = false;
        for (int i = 0; i < 3; i++) if (!a.get(i).item.id.equals(c.get(i).item.id)) differs = true;
        assertTrue("teklifler günden güne değişir", differs);
        Progress p = rich(100_000, 100);
        for (int i = 1; i <= 500; i++) p.stars[i] = 1;
        Economy.Deal d = a.get(0);
        if (d.item.kind == Economy.Kind.LIFE) p.lives = 1;
        assertTrue(Economy.buyDeal(p, d, 400, new Lives.Clock(T0, 100_000, 1, -1)));
        assertEquals(Economy.BLOCK_SOLD_OUT, Economy.dealBlockReason(p, d, 400));
        // ertesi günün teklifleri yeni stokla gelir
        Economy.Deal next = Economy.deals(401).get(0);
        assertNotEquals(Economy.BLOCK_SOLD_OUT, Economy.dealBlockReason(p, next, 401));
    }

    @Test public void continueCostsDoubleAndTicketsFirst() {
        assertEquals(80, Economy.continuePrice(0));
        assertEquals(160, Economy.continuePrice(1));
        assertEquals(320, Economy.continuePrice(2));
        Progress p = rich(200, 0);
        p.tickets = 1;
        assertTrue(Economy.payContinue(p, 0));
        assertEquals(0, p.tickets);
        assertEquals(200, p.crystals);
        assertTrue(Economy.payContinue(p, 0));
        assertEquals(120, p.crystals);
        assertFalse(Economy.payContinue(p, 1)); // 160 gerekir
    }

    @Test public void continueExtendsSessionBudget() throws Exception {
        Level l = TestUtil.pack().get(12);
        GameSession g = new GameSession(l, true);
        int b = g.budgetLeft();
        g.addBudget(Economy.CONTINUE_MOVES);
        assertEquals(b + 5, g.budgetLeft());
    }

    @Test public void shieldRefundsLostAttempt() {
        Progress p = rich(0, 0);
        Lives.Clock c = new Lives.Clock(T0, 100_000, 1, -1);
        Lives.update(p, c);
        p.shields = 1;
        Lives.startAttempt(p, c, 7);
        assertEquals(3, p.lives);
        assertTrue(Economy.loseAttempt(p, c));
        assertEquals(4, p.lives);
        assertEquals(0, p.shields);
        Lives.startAttempt(p, c, 7);
        assertFalse(Economy.loseAttempt(p, c));
        assertEquals(3, p.lives);
        assertFalse("deneme yokken kalkan harcanmaz", Economy.loseAttempt(p, c));
    }

    @Test public void levelRewardsOnlyFirstTime() {
        Progress p = rich(0, 0);
        Economy.Reward r = Economy.onLevelComplete(p, 5, 0, 2);
        assertEquals(4, r.crystals);
        assertEquals(java.util.Collections.singletonList(Economy.REWARD_FIRST_CLEAR), r.lines);
        p.stars[5] = 2;
        assertEquals("tekrar oynamak kazandırmaz", 0, Economy.onLevelComplete(p, 5, 2, 2).crystals);
        Economy.Reward three = Economy.onLevelComplete(p, 5, 2, 3);
        assertEquals("3 yıldıza yükselmek 3 kristal", 3, three.crystals);
        assertEquals(java.util.Collections.singletonList(Economy.REWARD_THREE_STARS), three.lines);
        assertEquals(12, Economy.onLevelComplete(p, 401, 0, 1).crystals); // 5. dünya: 4 + 2×4
        // grup 30/30: bir kez 1 taş
        for (int i = 11; i <= 20; i++) p.stars[i] = 3;
        Economy.Reward perfect = Economy.onLevelComplete(p, 20, 2, 3);
        assertEquals(1, perfect.gems);
        assertTrue(perfect.lines.contains(Economy.REWARD_PERFECT_CHAPTER));
        assertEquals(0, Economy.onLevelComplete(p, 20, 3, 3).gems);
    }

    @Test public void dailyAndAchievementRewards() {
        Progress p = rich(0, 0);
        Economy.Reward daily = Economy.onDailyComplete(p, 1);
        assertEquals(20, daily.crystals);
        assertEquals(java.util.Collections.singletonList(Economy.REWARD_DAILY), daily.lines);
        assertEquals(29, Economy.onDailyComplete(p, 4).crystals);
        assertEquals(50, Economy.onDailyComplete(p, 40).crystals); // seri bonusu en fazla +30
        Achievements.Def efsane = null, ilk = null;
        for (Achievements.Def d : Achievements.ALL) { if (d.id.equals("efsane")) efsane = d; if (d.id.equals("ilk_isik")) ilk = d; }
        assertEquals(0, Economy.onAchievement(p, ilk).gems);
        Economy.Reward legend = Economy.onAchievement(p, efsane);
        assertEquals(3, legend.gems);
        assertEquals(java.util.Collections.singletonList(Economy.REWARD_ACHIEVEMENT_PREFIX + "efsane"), legend.lines);
    }

    /** Motor metin değil kod döndürür: engel ve kazanç kodları sabit, ASCII ve tanımlı listede. */
    @Test public void blockReasonsAreStableCodes() {
        assertEquals(java.util.Arrays.asList("owned", "locked", "daily_limit", "lives_full", "not_enough_gems",
                "not_enough_crystals", "sold_out"), Economy.BLOCK_CODES);
        Progress p = rich(0, 0);
        for (int day = 0; day < 30; day++) {
            for (Economy.Item i : Economy.ITEMS) {
                String r = Economy.blockReason(p, i, day);
                assertTrue(i.id + ": " + r, r == null || Economy.BLOCK_CODES.contains(r));
            }
            for (Economy.Deal d : Economy.deals(day)) {
                String r = Economy.dealBlockReason(p, d, day);
                assertTrue(d.item.id + ": " + r, r == null || Economy.BLOCK_CODES.contains(r));
            }
        }
        for (Economy.Item i : Economy.ITEMS) assertTrue("kimlik anahtar olarak kullanılır: " + i.id, i.id.matches("[a-z0-9_]+"));
    }

    /** "Zor" ekonomi: bütün oyunun bölüm kazancı bile görünümlerin hepsine yetmez. */
    @Test public void economyIsTight() {
        int income = Economy.maxLevelIncome();
        int cosmetics = Economy.cosmeticCost(Economy.KR);
        System.out.println("500 bölümden en fazla kristal: " + income + ", kristalle satılan görünümler: " + cosmetics
                + ", taşla satılan görünümler: " + Economy.cosmeticCost(Economy.GEM) + " taş");
        assertTrue(cosmetics > income * 0.8);
        assertEquals(11, Economy.cosmeticCount());
        // bir bölüm ortalama 6–14 kristal: bir ipucu ≈ 5–10 bölüm
        assertTrue(Economy.item("hint1").basePrice >= 5 * 8);
    }

    @Test public void saveLoadKeepsEconomy() {
        Progress p = rich(321, 4);
        p.tickets = 2; p.shields = 1;
        p.owned.add("board_ocean"); p.boardSkin = "board_ocean";
        p.shopDay = 77; p.shopBuys.put("hint1", 2); p.perfectChapters.add(3);
        Progress q = Progress.deserialize(p.serialize());
        assertEquals(321, q.crystals);
        assertEquals(4, q.gems);
        assertEquals(2, q.tickets);
        assertEquals(1, q.shields);
        assertEquals("board_ocean", q.boardSkin);
        assertTrue(q.owned.contains("board_ocean"));
        assertEquals(Integer.valueOf(2), q.shopBuys.get("hint1"));
        assertTrue(q.perfectChapters.contains(3));
        assertEquals(p.serialize(), q.serialize());
        p.resetAll();
        assertEquals(50, p.crystals);
        assertTrue(p.owned.isEmpty());
    }

    /** Denetim 2: saat dilimini ileri-geri değiştirmek günlük alım sınırını sıfırlamaz. */
    @Test public void zoneHoppingDoesNotResetDailyLimits() {
        Progress p = new Progress();
        p.crystals = 100_000;
        long now = 1_800_000_000_000L, H = 3_600_000L;
        Economy.Item hint = Economy.item("hint1");
        int bought = 0;
        for (int hop = 0; hop < 8; hop++) {
            long zone = (hop % 2 == 0 ? 14 : -11) * H;
            int day = Economy.monotonicDay(p, now + hop * 60_000L, zone);
            while (Economy.blockReason(p, hint, day) == null) { assertTrue(Economy.buy(p, hint, day, new Lives.Clock(now, 0, -1, -1))); bought++; }
        }
        // +14 dilimi en fazla bir günü öne çeker: en çok iki günlük sınır
        assertTrue("alınan " + bought, bought >= hint.dailyLimit && bought <= 2 * hint.dailyLimit);
        int d0 = Economy.monotonicDay(p, now, -11 * H);
        assertEquals(p.maxDay, d0);
        assertTrue(Economy.msUntilNextMonotonicDay(p, now, -11 * H) > 24 * H);
    }
}
