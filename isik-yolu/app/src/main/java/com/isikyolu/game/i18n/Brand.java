package com.isikyolu.game.i18n;

/**
 * Oyunun iki dilli adı. Adlar çevrilmez: Türkçe seçiliyken "Işık Yolu" ana ad, "Prismway" ikincil ad;
 * diğer dillerde "Prismway" ana ad, "Işık Yolu" ikincil ad. Paket adı ve teknik kimlikler değişmez.
 */
public final class Brand {
    private Brand() {}

    public static final String NAME_TR = "Işık Yolu";
    public static final String NAME_EN = "Prismway";
    public static final String STUDIO = "Magic Lumos Software";

    public static boolean turkishFirst() { return "tr".equals(L.lang()); }

    /** Seçili dile göre büyük yazılan ad. */
    public static String primary() { return turkishFirst() ? NAME_TR : NAME_EN; }

    /** Seçili dile göre küçük yazılan ikinci ad. */
    public static String secondary() { return turkishFirst() ? NAME_EN : NAME_TR; }

    /** "Işık Yolu · Prismway" / "Prismway · Işık Yolu" (erişilebilirlik ve tek satırlık yerler için). */
    public static String full() { return primary() + " · " + secondary(); }
}
