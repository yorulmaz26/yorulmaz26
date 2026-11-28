package com.isikyolu.game.i18n;

import com.isikyolu.engine.Achievements;
import com.isikyolu.engine.Chapters;
import com.isikyolu.engine.LightColor;

/**
 * Motordaki dünya, grup, konu, mekanik, renk ve başarı adlarının seçili dildeki karşılıkları.
 * Motorun Türkçe dizileri (ör. {@code Chapters.NAMES}) arayüzde gösterilmez; anahtarlar
 * {@code assets/i18n/<dil>/world.json} dosyasındadır:
 * {@code world.<1-5>.name|level|intro}, {@code chapter.<1-50>.name}, {@code topic.<1-10>}, {@code mechanic.<1-10>},
 * {@code color.<LightColor.id>}, {@code ach.<id>.title|desc}.
 */
public final class WorldText {
    private WorldText() {}

    /** Anahtarı parçalardan kurar ve çevirir (dinamik anahtar; {@code I18nTest} sabit anahtar sanmasın diye ayrı yöntem). */
    private static String key(Object... parts) {
        StringBuilder b = new StringBuilder();
        for (Object o : parts) b.append(o);
        return L.t(b.toString());
    }

    /** Dünya adı (1–5). */
    public static String worldName(int world) { return key("world.", world, ".name"); }

    /** Dünyanın zorluk etiketi (1–5): "Başlangıç", "Zor"… */
    public static String worldLevel(int world) { return key("world.", world, ".level"); }

    /** Dünyanın tanıtım cümlesi (1–5). */
    public static String worldIntro(int world) { return key("world.", world, ".intro"); }

    /** Grup adı (1–50). */
    public static String chapterName(int chapter) { return key("chapter.", chapter, ".name"); }

    /** Mekanik konusunun adı (1–10, bkz. {@link Chapters#mechanicOf(int)}). */
    public static String topicName(int mechanic) { return key("topic.", mechanic); }

    /** Grup konusu; ilk dünyadan sonra dünya zorluğuyla birlikte ("Prizmalar · Zor"). */
    public static String topic(int chapter) {
        int w = Chapters.worldOfChapter(chapter);
        String t = topicName(Chapters.mechanicOf(chapter));
        return w == 1 ? t : L.t("levels.topic_level", t, worldLevel(w));
    }

    /** Mekaniğin açıklaması (1–10). */
    public static String mechanic(int mechanic) { return key("mechanic.", mechanic); }

    /** Işık renginin adı. */
    public static String color(int lightColor) { return key("color.", LightColor.id(lightColor)); }

    public static String achievementTitle(Achievements.Def d) { return key("ach.", d.id, ".title"); }

    public static String achievementDesc(Achievements.Def d) { return key("ach.", d.id, ".desc"); }
}
