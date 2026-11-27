package com.isikyolu.game.i18n;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Yerelleştirme yöneticisi. Metinler {@code assets/i18n/<dil>.json} dosyalarındadır (düz anahtar → metin).
 * <ul>
 *   <li>Yer tutucular: {@code {0}}, {@code {1}} … ({@link #t(String, Object...)}).</li>
 *   <li>Çoğul: değer {@code {"one": "...", "other": "..."}} nesnesi olabilir ({@link #n(String, long, Object...)}).</li>
 *   <li>Eksik anahtar: önce İngilizce, sonra Türkçe metin gösterilir; o da yoksa anahtarın kendisi.</li>
 *   <li>Dil seçimi: oyuncunun seçtiği dil → (Android 13+) sistemdeki uygulama dili → telefon dili → desteklenmiyorsa İngilizce.</li>
 * </ul>
 * Yeni dil eklemek: {@link #SUPPORTED} listesine kodu, {@link #NATIVE_NAMES}'e adını ekle ve {@code <kod>.json} dosyasını koy.
 * Eksik anahtarları {@code I18nTest} bulur.
 */
public final class L {
    private L() {}

    public static final String[] SUPPORTED = {"tr", "en", "de", "it", "fr", "es"};
    public static final String[] NATIVE_NAMES = {"Türkçe", "English", "Deutsch", "Italiano", "Français", "Español"};
    public static final String FALLBACK = "en";
    private static final String PREFS = "isik_yolu_dil", KEY = "lang";

    /** Testler için sabit dil (null: normal seçim). */
    public static String override;

    private static String lang = "tr";
    private static Locale locale = new Locale("tr", "TR");
    private static Map<String, Object> strings = Collections.emptyMap(), fallback = Collections.emptyMap(), source = Collections.emptyMap();
    private static Context app;
    private static final List<Runnable> listeners = new ArrayList<>();

    public static synchronized void init(Context c) {
        app = c.getApplicationContext();
        load(resolve(app));
    }

    /** Oyuncunun dil seçimi kayıtlı mı (yoksa sistem dili kullanılır). */
    public static boolean hasUserChoice(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) != null;
    }

    static String resolve(Context c) {
        if (override != null) return supported(override) ? override : FALLBACK;
        String sys = null;
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            try {
                android.app.LocaleManager lm = c.getSystemService(android.app.LocaleManager.class);
                android.os.LocaleList ll = lm == null ? null : lm.getApplicationLocales();
                if (ll != null && !ll.isEmpty()) sys = ll.get(0).getLanguage();
            } catch (RuntimeException ignored) {}
        }
        if (sys != null && supported(sys)) return sys; // sistem ayarlarından seçilen uygulama dili
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String chosen = p.getString(KEY, null);
        if (chosen != null && supported(chosen)) return chosen;
        return systemLanguage();
    }

    /** Telefonun dili; desteklenmiyorsa İngilizce. */
    public static String systemLanguage() {
        android.os.LocaleList ll = android.content.res.Resources.getSystem().getConfiguration().getLocales();
        for (int i = 0; i < ll.size(); i++) {
            String l = normalize(ll.get(i).getLanguage());
            if (supported(l)) return l;
        }
        return FALLBACK;
    }

    private static String normalize(String l) {
        if (l == null) return "";
        l = l.toLowerCase(Locale.ROOT);
        return l;
    }

    public static boolean supported(String code) {
        for (String s : SUPPORTED) if (s.equals(code)) return true;
        return false;
    }

    /** Dili değiştirir ve kaydeder; ekranlar dinleyicilerle yenilenir. İlerlemeyi etkilemez. */
    public static synchronized void set(Context c, String code) {
        if (!supported(code)) return;
        Context a = c.getApplicationContext();
        a.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, code).apply();
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            try {
                android.app.LocaleManager lm = a.getSystemService(android.app.LocaleManager.class);
                if (lm != null) lm.setApplicationLocales(android.os.LocaleList.forLanguageTags(code));
            } catch (RuntimeException ignored) {}
        }
        load(code);
        for (Runnable r : new ArrayList<>(listeners)) r.run();
    }

    /** Sistem dili/uygulama dili değiştiyse yeniden yükler; değiştiyse true. */
    public static synchronized boolean refresh(Context c) {
        String l = resolve(c.getApplicationContext());
        if (l.equals(lang)) return false;
        load(l);
        return true;
    }

    public static void addListener(Runnable r) { listeners.add(r); }
    public static void removeListener(Runnable r) { listeners.remove(r); }

    private static void load(String code) {
        lang = code;
        locale = Locale.forLanguageTag(code);
        strings = read(code);
        fallback = code.equals(FALLBACK) ? strings : read(FALLBACK);
        source = code.equals("tr") ? strings : read("tr");
    }

    /** {@code i18n/<dil>.json} ve varsa {@code i18n/<dil>/} klasöründeki tüm .json dosyaları (alfabetik sırayla) birleşir. */
    public static Map<String, Object> read(String code) {
        if (app == null) return Collections.emptyMap();
        Map<String, Object> m = new HashMap<>();
        readInto(m, "i18n/" + code + ".json");
        try {
            String[] parts = app.getAssets().list("i18n/" + code);
            if (parts != null) {
                java.util.Arrays.sort(parts);
                for (String f : parts) if (f.endsWith(".json")) readInto(m, "i18n/" + code + "/" + f);
            }
        } catch (java.io.IOException ignored) {}
        return m;
    }

    private static void readInto(Map<String, Object> m, String path) {
        try (InputStream in = app.getAssets().open(path)) {
            m.putAll(parse(readAll(in)));
        } catch (Exception ignored) {}
    }

    static String readAll(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        for (int n; (n = in.read(buf)) > 0; ) bo.write(buf, 0, n);
        return new String(bo.toByteArray(), StandardCharsets.UTF_8);
    }

    public static Map<String, Object> parse(String json) throws org.json.JSONException {
        JSONObject o = new JSONObject(json);
        Map<String, Object> m = new HashMap<>();
        for (Iterator<String> it = o.keys(); it.hasNext(); ) {
            String k = it.next();
            Object v = o.get(k);
            if (v instanceof JSONObject) {
                JSONObject po = (JSONObject) v;
                Map<String, String> forms = new HashMap<>();
                for (Iterator<String> f = po.keys(); f.hasNext(); ) { String fk = f.next(); forms.put(fk, po.getString(fk)); }
                m.put(k, forms);
            } else m.put(k, String.valueOf(v));
        }
        return m;
    }

    public static String lang() { return lang; }
    public static Locale locale() { return locale; }

    /** Seçili dilin yerel adı (ör. "Deutsch"). */
    public static String nativeName(String code) {
        for (int i = 0; i < SUPPORTED.length; i++) if (SUPPORTED[i].equals(code)) return NATIVE_NAMES[i];
        return code;
    }

    private static Object raw(String key) {
        Object v = strings.get(key);
        if (v == null) v = fallback.get(key);
        if (v == null) v = source.get(key);
        return v;
    }

    /** Anahtar var mı (herhangi bir dilde). */
    public static boolean has(String key) { return raw(key) != null; }

    /** Metin; {0}, {1}… yer tutucuları sırayla doldurulur. */
    public static String t(String key, Object... args) {
        Object v = raw(key);
        String s;
        if (v == null) s = key;
        else if (v instanceof String) s = (String) v;
        else s = form(v, 1);
        return args.length == 0 ? s : fill(s, args);
    }

    /** Çoğul metin: {0} = n, sonraki yer tutucular {1}, {2}… */
    public static String n(String key, long count, Object... args) {
        Object v = raw(key);
        String s;
        if (v == null) s = key;
        else if (v instanceof String) s = (String) v;
        else s = form(v, count);
        Object[] all = new Object[args.length + 1];
        all[0] = count;
        System.arraycopy(args, 0, all, 1, args.length);
        return fill(s, all);
    }

    @SuppressWarnings("unchecked")
    private static String form(Object v, long n) {
        Map<String, String> m = (Map<String, String>) v;
        String f = pluralCategory(lang, n);
        String s = m.get(f);
        if (s == null) s = m.get("other");
        if (s == null && !m.isEmpty()) s = m.values().iterator().next();
        return s == null ? "" : s;
    }

    /** CLDR'ın tamsayılar için çoğul kuralı (desteklenen diller). */
    public static String pluralCategory(String lang, long n) {
        switch (lang) {
            case "fr": return n == 0 || n == 1 ? "one" : "other";
            default: return n == 1 ? "one" : "other";
        }
    }

    static String fill(String s, Object[] args) {
        if (s.indexOf('{') < 0) return s;
        StringBuilder b = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '{') {
                int j = s.indexOf('}', i);
                if (j > i + 1) {
                    String idx = s.substring(i + 1, j);
                    boolean digits = true;
                    for (int k = 0; k < idx.length(); k++) if (!Character.isDigit(idx.charAt(k))) { digits = false; break; }
                    if (digits) {
                        int a = Integer.parseInt(idx);
                        if (a < args.length) { b.append(format(args[a])); i = j; continue; }
                    }
                }
            }
            b.append(ch);
        }
        return b.toString();
    }

    private static String format(Object o) {
        if (o instanceof Integer || o instanceof Long) return java.text.NumberFormat.getIntegerInstance(locale).format(((Number) o).longValue());
        return String.valueOf(o);
    }
}
