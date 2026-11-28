package com.isikyolu.game;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.isikyolu.engine.Economy;
import com.isikyolu.game.i18n.EconomyTexts;
import com.isikyolu.game.i18n.L;

import org.junit.After;
import org.junit.Test;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Ekonomi metinleri: motor yalnızca kod ve ürün kimliği verir; her kodun ve her ürünün (dinamik anahtar,
 * {@code I18nTest.codeUsesOnlyDefinedKeys} görmez) Türkçe ve İngilizce metni olmalı. Türkçe metinler motorun
 * Türkçe kaynak alanlarıyla ve eski engel cümleleriyle aynı kalır.
 */
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 34)
public class EconomyTextsTest {
    @After public void reset() { L.override = null; }

    private static void use(String lang) {
        L.override = lang;
        L.init(RuntimeEnvironment.getApplication());
    }

    @Test public void everyItemHasNameAndDescriptionKeys() throws Exception {
        List<String> problems = new ArrayList<>();
        Map<String, Object> tr = I18nTest.load("tr", problems), en = I18nTest.load("en", problems);
        assertTrue(problems.toString(), problems.isEmpty());
        for (Economy.Item it : Economy.ITEMS) {
            String n = "item." + it.id + ".name", d = "item." + it.id + ".desc";
            assertEquals("Türkçe ad değişmedi: " + it.id, it.name, tr.get(n));
            assertEquals("Türkçe açıklama değişmedi: " + it.id, it.description, tr.get(d));
            assertTrue("İngilizce ad: " + it.id, en.get(n) instanceof String && !((String) en.get(n)).isEmpty());
            assertTrue("İngilizce açıklama: " + it.id, en.get(d) instanceof String && !((String) en.get(d)).isEmpty());
        }
        for (String code : Economy.BLOCK_CODES) {
            assertTrue("tr econ.block." + code, tr.containsKey("econ.block." + code));
            assertTrue("en econ.block." + code, en.containsKey("econ.block." + code));
        }
        for (String code : Arrays.asList(Economy.REWARD_FIRST_CLEAR, Economy.REWARD_THREE_STARS, Economy.REWARD_PERFECT_CHAPTER,
                Economy.REWARD_DAILY)) {
            assertTrue("tr econ.reward." + code, tr.containsKey("econ.reward." + code));
            assertTrue("en econ.reward." + code, en.containsKey("econ.reward." + code));
        }
    }

    /** Türkçe engel metinleri eskiden motorun döndürdüğü cümlelerle aynı. */
    @Test public void turkishBlockReasonsUnchanged() {
        use("tr");
        assertNull(EconomyTexts.blockReason(null, 0));
        assertEquals("Sahipsin", EconomyTexts.blockReason(Economy.BLOCK_OWNED, 0));
        // 1.8.0: "Bölüm 20'u bitir" ek hatası düzeltildi (20'yi, 40'ı, 100'ü … sayıya göre değişir): ek kullanılmaz
        assertEquals("Önce 20. bölümü bitir", EconomyTexts.blockReason(Economy.BLOCK_LOCKED, Economy.item("mirror_gold")));
        assertEquals("Bugünlük sınır doldu", EconomyTexts.blockReason(Economy.BLOCK_DAILY_LIMIT, 0));
        assertEquals("Canların dolu", EconomyTexts.blockReason(Economy.BLOCK_LIVES_FULL, 0));
        assertEquals("Yetersiz Prizma Taşı", EconomyTexts.blockReason(Economy.BLOCK_NOT_ENOUGH_GEMS, 0));
        assertEquals("Yetersiz kristal", EconomyTexts.blockReason(Economy.BLOCK_NOT_ENOUGH_CRYSTALS, 0));
        assertEquals("Tükendi", EconomyTexts.blockReason(Economy.BLOCK_SOLD_OUT, 0));
        Economy.Reward r = new Economy.Reward();
        r.lines.addAll(Arrays.asList(Economy.REWARD_FIRST_CLEAR, Economy.REWARD_THREE_STARS, Economy.REWARD_PERFECT_CHAPTER));
        assertEquals("İlk tamamlama · 3 yıldız · Grup 30/30 yıldız", EconomyTexts.rewardSummary(r));
        assertEquals("Günlük bulmaca", EconomyTexts.rewardLine(Economy.REWARD_DAILY));
        assertEquals("İpucu Paketi ×3", EconomyTexts.itemName(Economy.item("hint3")));
    }

    /** İngilizcede her kod çevrilir (kodun kendisi ya da Türkçe metin görünmez). */
    @Test public void englishTextsForEveryCode() {
        use("en");
        for (String code : Economy.BLOCK_CODES) {
            String s = EconomyTexts.blockReason(code, 20);
            assertNotEquals(code, s);
            assertTrue(code + ": " + s, s.matches("[\\x20-\\x7E]+"));
        }
        assertEquals("Finish Level 20 first", EconomyTexts.blockReason(Economy.BLOCK_LOCKED, 20));
        for (Economy.Item it : Economy.ITEMS) {
            assertNotEquals(it.id, it.name, EconomyTexts.itemName(it));
            assertNotEquals(it.id, it.description, EconomyTexts.itemDesc(it));
        }
        Economy.Reward r = new Economy.Reward();
        r.lines.addAll(Arrays.asList(Economy.REWARD_FIRST_CLEAR, Economy.REWARD_THREE_STARS));
        assertEquals("First clear · 3 stars", EconomyTexts.rewardSummary(r));
    }
}
