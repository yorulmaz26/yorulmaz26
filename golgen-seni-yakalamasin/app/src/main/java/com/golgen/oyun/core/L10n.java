package com.golgen.oyun.core;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Merkezi yerelleştirme yöneticisi (Localization Manager).
 *
 * <p>Metinler kaynak kodda değil, {@code assets/localization/<dil>.json} dosyalarında durur
 * (düz anahtar → metin eşlemesi). Bu sınıf saf Java'dır: Android'de varlıklardan, birim
 * testlerinde doğrudan dosyadan yüklenir.
 *
 * <ul>
 *   <li>{@link #t(String)}: düz metin</li>
 *   <li>{@link #f(String, Object...)}: {0}, {1} … yer tutuculu metin</li>
 *   <li>{@link #p(String, int)}: tekil/çoğul ({@code anahtar.one} / {@code anahtar.other}, sayı {n})</li>
 * </ul>
 * Eksik anahtar önce İngilizceye, o da yoksa anahtarın kendisine düşer ve kaydedilir
 * ({@link #missingSeen()}); {@code L10nTest} tüm dillerin anahtar kümelerini karşılaştırır.
 * Yeni dil eklemek: {@link #CODES}/{@link #NATIVE_NAMES} dizisine kod eklemek ve aynı anahtarlarla
 * bir JSON dosyası koymak yeterlidir.
 */
public final class L10n {
    private L10n() {}

    /** Desteklenen diller (sıra Ayarlar'daki sıradır). */
    public static final String[] CODES = {"tr", "en", "de", "it", "fr", "es"};
    public static final String[] NATIVE_NAMES = {"Türkçe", "English", "Deutsch", "Italiano", "Français", "Español"};
    public static final String DEFAULT = "en";

    private static final Map<String, Map<String, String>> tables = new HashMap<>();
    private static Map<String, String> cur = new HashMap<>();
    private static Map<String, String> fallback = new HashMap<>();
    private static String code = DEFAULT;
    private static Locale locale = Locale.ENGLISH;
    private static int version;
    private static final Set<String> missing = new TreeSet<>();

    // ---------------- Yükleme ----------------
    /** Bir dilin tablosunu JSON metninden yükler (aynı dil tekrar yüklenirse değiştirilir). */
    public static synchronized void load(String lang, String json) {
        tables.put(lang, parse(json));
        if (lang.equals(DEFAULT)) fallback = tables.get(lang);
        if (lang.equals(code)) { cur = tables.get(lang); version++; }
    }

    public static synchronized boolean isLoaded(String lang) { return tables.containsKey(lang); }

    /** Etkin dili değiştirir; arayüz önbellekleri {@link #version()} ile yenilenir. */
    public static synchronized void use(String lang) {
        if (!isSupported(lang)) lang = DEFAULT;
        code = lang;
        locale = Locale.forLanguageTag(lang);
        Map<String, String> m = tables.get(lang);
        cur = m != null ? m : fallback;
        version++;
    }

    public static String code() { return code; }
    public static Locale locale() { return locale; }
    /** Dil veya tablo her değiştiğinde artar (önbellekli metinler bunu izler). */
    public static int version() { return version; }

    public static boolean isSupported(String lang) {
        if (lang == null) return false;
        for (String c : CODES) if (c.equals(lang)) return true;
        return false;
    }

    /** Sistem dilinden oyun dili: destekleniyorsa o, değilse İngilizce. */
    public static String pickForSystem(String systemLanguage) {
        if (systemLanguage == null) return DEFAULT;
        String l = systemLanguage.toLowerCase(Locale.ROOT);
        int dash = l.indexOf('-');
        if (dash < 0) dash = l.indexOf('_');
        if (dash > 0) l = l.substring(0, dash);
        return isSupported(l) ? l : DEFAULT;
    }

    public static int indexOf(String lang) {
        for (int i = 0; i < CODES.length; i++) if (CODES[i].equals(lang)) return i;
        return -1;
    }

    // ---------------- Erişim ----------------
    public static String t(String key) {
        String v = cur.get(key);
        if (v != null) return v;
        synchronized (L10n.class) { missing.add(code + ":" + key); }
        v = fallback.get(key);
        return v != null ? v : key;
    }

    /** {0}, {1} … yer tutucularını doldurur. */
    public static String f(String key, Object... args) {
        return format(t(key), args);
    }

    /** Tekil/çoğul: anahtar.one / anahtar.other; {n} sayı ile, {0}… ek argümanlarla doldurulur. */
    public static String p(String key, int n, Object... args) {
        String form = pluralForm(code, n);
        String s = cur.get(key + "." + form);
        if (s == null) s = cur.get(key + ".other");
        if (s == null) s = t(key + ".other");
        return format(s.replace("{n}", Integer.toString(n)), args);
    }

    /** CLDR tam sayı kuralları (bu oyunun dilleri için): fr'de 0 ve 1 tekildir. */
    public static String pluralForm(String lang, int n) {
        if ("fr".equals(lang)) return (n == 0 || n == 1) ? "one" : "other";
        return n == 1 ? "one" : "other";
    }

    /** Dile uygun büyük harf (Türkçede i → İ). */
    public static String upper(String s) { return s.toUpperCase(locale); }

    public static String format(String s, Object... args) {
        if (args == null || args.length == 0 || s.indexOf('{') < 0) return s;
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '{' && i + 2 < s.length() && Character.isDigit(s.charAt(i + 1)) && s.charAt(i + 2) == '}') {
                int k = s.charAt(i + 1) - '0';
                sb.append(k < args.length ? String.valueOf(args[k]) : "");
                i += 2;
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    // ---------------- Denetim (testler ve debug) ----------------
    public static synchronized Set<String> keys(String lang) {
        Map<String, String> m = tables.get(lang);
        return m == null ? new TreeSet<>() : new TreeSet<>(m.keySet());
    }

    public static synchronized String raw(String lang, String key) {
        Map<String, String> m = tables.get(lang);
        return m == null ? null : m.get(key);
    }

    /** Çalışırken istenip hiçbir dilde bulunamayan veya etkin dilde eksik olan anahtarlar ("dil:anahtar"). */
    public static synchronized Set<String> missingSeen() { return new TreeSet<>(missing); }

    // ---------------- Küçük JSON okuyucu (düz nesne: "anahtar": "metin") ----------------
    static Map<String, String> parse(String json) {
        Map<String, String> out = new HashMap<>();
        int[] pos = {0};
        skipWs(json, pos);
        expect(json, pos, '{');
        skipWs(json, pos);
        if (peek(json, pos) == '}') return out;
        while (true) {
            skipWs(json, pos);
            String k = readString(json, pos);
            skipWs(json, pos);
            expect(json, pos, ':');
            skipWs(json, pos);
            String v = readString(json, pos);
            if (out.put(k, v) != null) throw new IllegalArgumentException("Yinelenen anahtar: " + k);
            skipWs(json, pos);
            char c = json.charAt(pos[0]++);
            if (c == '}') break;
            if (c != ',') throw new IllegalArgumentException("',' bekleniyordu, konum " + (pos[0] - 1));
        }
        return out;
    }

    private static char peek(String s, int[] p) { return s.charAt(p[0]); }

    private static void skipWs(String s, int[] p) {
        while (p[0] < s.length() && Character.isWhitespace(s.charAt(p[0]))) p[0]++;
    }

    private static void expect(String s, int[] p, char c) {
        if (p[0] >= s.length() || s.charAt(p[0]) != c) throw new IllegalArgumentException("'" + c + "' bekleniyordu, konum " + p[0]);
        p[0]++;
    }

    private static String readString(String s, int[] p) {
        expect(s, p, '"');
        StringBuilder sb = new StringBuilder();
        while (true) {
            char c = s.charAt(p[0]++);
            if (c == '"') return sb.toString();
            if (c != '\\') { sb.append(c); continue; }
            char e = s.charAt(p[0]++);
            switch (e) {
                case 'n': sb.append('\n'); break;
                case 't': sb.append('\t'); break;
                case '"': sb.append('"'); break;
                case '\\': sb.append('\\'); break;
                case '/': sb.append('/'); break;
                case 'u':
                    sb.append((char) Integer.parseInt(s.substring(p[0], p[0] + 4), 16));
                    p[0] += 4;
                    break;
                default: throw new IllegalArgumentException("Geçersiz kaçış \\" + e);
            }
        }
    }
}
