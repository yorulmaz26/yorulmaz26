package com.isikyolu.game.i18n;

import com.isikyolu.engine.Economy;

import java.util.ArrayList;
import java.util.List;

/**
 * Ekonominin (market, kazanç) metinleri. Motor ({@link Economy}) metin üretmez; engel ve kazanç kodlarını ve ürün
 * kimliklerini burada dile göre metne çeviririz. Diğer ekranlar da (oyun, can penceresi) bunu kullanır.
 * <ul>
 *   <li>Ürün adı/açıklaması: {@code item.<id>.name}, {@code item.<id>.desc} (her {@link Economy#ITEMS} kimliği).</li>
 *   <li>Engel: {@code econ.block.<kod>} ({@link Economy#BLOCK_CODES}).</li>
 *   <li>Kazanç satırı: {@code econ.reward.<kod>}; başarı ödülü {@code "ach:<id>"} → {@code ach.<id>.title}.</li>
 * </ul>
 */
public final class EconomyTexts {
    private EconomyTexts() {}

    /** Dinamik anahtar: {@code önek + kimlik + sonek} (ör. "item." + "hint1" + ".name"). */
    private static String key(String prefix, String id, String suffix) { return prefix + id + suffix; }

    /** Ürünün görünen adı ({@code item.<id>.name}). */
    public static String itemName(Economy.Item it) { return L.t(key("item.", it.id, ".name")); }

    /** Ürünün görünen açıklaması ({@code item.<id>.desc}). */
    public static String itemDesc(Economy.Item it) { return L.t(key("item.", it.id, ".desc")); }

    /**
     * {@link Economy#blockReason} / {@link Economy#dealBlockReason} kodunun metni (ör. toast için).
     * @param it engellenen ürün (kilitli görünümde gereken bölüm numarası buradan alınır)
     * @return metin; kod null ise (satın alınabilir) null
     */
    public static String blockReason(String code, Economy.Item it) {
        return blockReason(code, it == null ? 0 : it.unlockLevel);
    }

    /**
     * Kodun metni; {@code unlockLevel} yalnızca {@link Economy#BLOCK_LOCKED} için kullanılır.
     * @return metin; kod null ise null
     */
    public static String blockReason(String code, int unlockLevel) {
        if (code == null) return null;
        switch (code) {
            case Economy.BLOCK_OWNED: return L.t("econ.block.owned");
            case Economy.BLOCK_LOCKED: return L.t("econ.block.locked", unlockLevel);
            case Economy.BLOCK_DAILY_LIMIT: return L.t("econ.block.daily_limit");
            case Economy.BLOCK_LIVES_FULL: return L.t("econ.block.lives_full");
            case Economy.BLOCK_NOT_ENOUGH_GEMS: return L.t("econ.block.not_enough_gems");
            case Economy.BLOCK_NOT_ENOUGH_CRYSTALS: return L.t("econ.block.not_enough_crystals");
            case Economy.BLOCK_SOLD_OUT: return L.t("econ.block.sold_out");
            default: return code; // BLOCK_CODES dışında kod yok (EconomyTest, EconomyTextsTest denetler)
        }
    }

    /** Tek kazanç satırının metni ({@link Economy.Reward#lines} öğesi). */
    public static String rewardLine(String code) {
        if (code == null) return "";
        if (code.startsWith(Economy.REWARD_ACHIEVEMENT_PREFIX))
            return L.t(key("ach.", code.substring(Economy.REWARD_ACHIEVEMENT_PREFIX.length()), ".title"));
        switch (code) {
            case Economy.REWARD_FIRST_CLEAR: return L.t("econ.reward.first_clear");
            case Economy.REWARD_THREE_STARS: return L.t("econ.reward.three_stars");
            case Economy.REWARD_PERFECT_CHAPTER: return L.t("econ.reward.perfect_chapter");
            case Economy.REWARD_DAILY: return L.t("econ.reward.daily");
            default: return code;
        }
    }

    /** Kazanç satırlarının metinleri, sırayla. */
    public static List<String> rewardLines(Economy.Reward r) {
        List<String> out = new ArrayList<>();
        if (r != null) for (String code : r.lines) out.add(rewardLine(code));
        return out;
    }

    /** Kazanç satırları tek metin olarak ("İlk tamamlama · 3 yıldız"). */
    public static String rewardSummary(Economy.Reward r) {
        return String.join(" · ", rewardLines(r));
    }
}
