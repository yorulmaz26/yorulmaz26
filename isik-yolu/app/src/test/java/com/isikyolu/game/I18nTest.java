package com.isikyolu.game;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.json.JSONObject;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Yerelleştirme denetimi (eksik çeviri denetleyicisi):
 * <ul>
 *   <li>her dilde Türkçe (kaynak) dosyadaki her anahtar var, fazlası yok, aynı anahtar iki kez tanımlanmamış;</li>
 *   <li>yer tutucular ({0}, {1}…) ve çoğul biçimleri kaynakla uyumlu, boş metin yok;</li>
 *   <li>koddaki her {@code L.t("…")} / {@code L.n("…")} anahtarı tanımlı;</li>
 *   <li>arayüz kodunda doğrudan yazılmış Türkçe metin ve motorun Türkçe alanlarının ekrana basılması yok.</li>
 * </ul>
 * Yeni dil: {@link com.isikyolu.game.i18n.L#SUPPORTED} listesine ekle, bu test eksik anahtarları listeler.
 */
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner.class) // org.json için gerçek Android sınıfları
@org.robolectric.annotation.Config(sdk = 34)
public class I18nTest {
    static final File ASSETS = new File("src/main/assets/i18n");
    static final File SRC = new File("src/main/java");
    static final Pattern PH = Pattern.compile("\\{(\\d+)}");
    /** Çevrilmeyen marka adları. */
    static final Set<String> BRAND = new TreeSet<>(Arrays.asList("Magic Lumos Software", "MAGIC LUMOS", "SOFTWARE"));

    /** dil → anahtar → metin ya da çoğul biçimleri (Map). */
    static Map<String, Object> load(String code, List<String> problems) throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        List<File> files = new ArrayList<>();
        File single = new File(ASSETS, code + ".json");
        if (single.exists()) files.add(single);
        File dir = new File(ASSETS, code);
        if (dir.isDirectory()) {
            File[] parts = dir.listFiles((d, n) -> n.endsWith(".json"));
            Arrays.sort(parts);
            files.addAll(Arrays.asList(parts));
        }
        for (File f : files) {
            JSONObject o = new JSONObject(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
            for (Iterator<String> it = o.keys(); it.hasNext(); ) {
                String k = it.next();
                Object v = o.get(k);
                Object val;
                if (v instanceof JSONObject) {
                    Map<String, String> forms = new LinkedHashMap<>();
                    JSONObject po = (JSONObject) v;
                    for (Iterator<String> fi = po.keys(); fi.hasNext(); ) { String fk = fi.next(); forms.put(fk, po.getString(fk)); }
                    val = forms;
                } else val = String.valueOf(v);
                if (m.containsKey(k)) problems.add(code + ": '" + k + "' birden çok kez tanımlı (" + f.getName() + ")");
                m.put(k, val);
            }
        }
        return m;
    }

    static Set<String> placeholders(Object v) {
        Set<String> s = new TreeSet<>();
        if (v instanceof String) { Matcher mt = PH.matcher((String) v); while (mt.find()) s.add(mt.group(1)); }
        else for (Object f : ((Map<?, ?>) v).values()) s.addAll(placeholders(f));
        return s;
    }

    @Test public void everyLanguageHasEveryKey() throws Exception {
        List<String> problems = new ArrayList<>();
        Map<String, Object> tr = load("tr", problems);
        assertTrue("Türkçe metin yok", tr.size() > 0);
        Map<String, Integer> missing = new LinkedHashMap<>();
        for (String code : com.isikyolu.game.i18n.L.SUPPORTED) {
            Map<String, Object> m = load(code, problems);
            int miss = 0;
            for (Map.Entry<String, Object> e : tr.entrySet()) {
                Object v = m.get(e.getKey());
                if (v == null) { miss++; if (miss <= 15) problems.add(code + ": eksik '" + e.getKey() + "'"); continue; }
                if (!placeholders(v).equals(placeholders(e.getValue())))
                    problems.add(code + ": '" + e.getKey() + "' yer tutucuları farklı " + placeholders(v) + " ≠ " + placeholders(e.getValue()));
                if (e.getValue() instanceof Map) {
                    if (!(v instanceof Map) || !((Map<?, ?>) v).containsKey("other") || !((Map<?, ?>) v).containsKey("one"))
                        problems.add(code + ": '" + e.getKey() + "' çoğul biçimleri (one/other) eksik");
                } else if (v instanceof Map) problems.add(code + ": '" + e.getKey() + "' kaynakta çoğul değil");
                for (String text : (v instanceof Map ? ((Map<String, String>) v).values() : java.util.Collections.singletonList((String) v)))
                    if (text.trim().isEmpty()) problems.add(code + ": '" + e.getKey() + "' boş");
            }
            for (String k : m.keySet()) if (!tr.containsKey(k)) problems.add(code + ": kaynakta olmayan anahtar '" + k + "'");
            missing.put(code, miss);
        }
        System.out.println("Eksik çeviri sayısı: " + missing);
        if (!problems.isEmpty()) fail(problems.size() + " sorun:\n" + String.join("\n", problems.subList(0, Math.min(80, problems.size()))));
    }

    static List<File> javaFiles(File dir) {
        List<File> out = new ArrayList<>();
        File[] fs = dir.listFiles();
        if (fs == null) return out;
        for (File f : fs) {
            if (f.isDirectory()) out.addAll(javaFiles(f));
            else if (f.getName().endsWith(".java")) out.add(f);
        }
        return out;
    }

    /** Yorumları siler (satır numaraları korunur); dize içindeki // korunur. */
    static String stripComments(String s) {
        StringBuilder b = new StringBuilder(s.length());
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '"' ) {
                int j = i + 1;
                while (j < s.length() && s.charAt(j) != '"') { if (s.charAt(j) == '\\') j++; j++; }
                b.append(s, i, Math.min(s.length(), j + 1));
                i = j + 1;
            } else if (c == '\'' && i + 2 < s.length()) {
                int j = s.indexOf('\'', i + (s.charAt(i + 1) == '\\' ? 3 : 2));
                if (j < 0) j = i;
                b.append(s, i, j + 1);
                i = j + 1;
            } else if (s.startsWith("//", i)) {
                while (i < s.length() && s.charAt(i) != '\n') i++;
            } else if (s.startsWith("/*", i)) {
                int j = s.indexOf("*/", i + 2);
                if (j < 0) j = s.length() - 2;
                for (int k = i; k < j + 2; k++) if (s.charAt(k) == '\n') b.append('\n');
                i = j + 2;
            } else { b.append(c); i++; }
        }
        return b.toString();
    }

    @Test public void codeUsesOnlyDefinedKeys() throws Exception {
        List<String> problems = new ArrayList<>();
        Map<String, Object> tr = load("tr", problems);
        Pattern call = Pattern.compile("\\bL\\.(?:t|n)\\(\\s*\"([^\"]+)\"");
        int used = 0;
        for (File f : javaFiles(SRC)) {
            String s = stripComments(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
            Matcher m = call.matcher(s);
            while (m.find()) {
                used++;
                if (!tr.containsKey(m.group(1))) problems.add(f.getName() + ": tanımsız anahtar '" + m.group(1) + "'");
            }
        }
        System.out.println("Koddaki sabit anahtar kullanımı: " + used);
        if (!problems.isEmpty()) fail(String.join("\n", problems));
    }

    /** Doğrudan yazılmış Türkçe arayüz metni kalmadı (yorumlar hariç). */
    @Test public void noHardCodedTurkishInUi() throws Exception {
        Pattern literal = Pattern.compile("\"((?:[^\"\\\\\\n]|\\\\.)*)\"");
        Pattern turkish = Pattern.compile("[çğıöşüÇĞİÖŞÜâÂ]");
        // motorun Türkçe kaynak alanları arayüzde gösterilmez; yerine L anahtarları kullanılır
        Pattern engineText = Pattern.compile("Chapters\\.(NAMES|TOPICS|MECHANICS|WORLD_NAMES|WORLD_LEVELS|WORLD_INTROS)\\b|LightColor\\.nameTr\\b|\\bnameTr\\(");
        Pattern uiCall = Pattern.compile("\\b(?:Ui\\.text|Ui\\.button|Ui\\.header|toast|title|body|banner|setText|setContentDescription|note|button)\\s*\\((?:[^\"();]|\\([^()]*\\))*\"([^\"]*[A-Za-z][^\"]*)\"");
        List<String> problems = new ArrayList<>();
        for (File f : javaFiles(SRC)) {
            if (f.getName().equals("L.java")) continue; // dil adları (Türkçe, Français …)
            String[] lines = stripComments(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8)).split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                Matcher m = literal.matcher(lines[i]);
                while (m.find()) if (turkish.matcher(m.group(1)).find()) problems.add(f.getName() + ":" + (i + 1) + "  \"" + m.group(1) + "\"");
                // özel harfsiz Türkçe metinler de ("Market", "Tamam"): arayüz çağrısına doğrudan verilen harfli dize
                Matcher u = uiCall.matcher(lines[i]);
                while (u.find()) if (!BRAND.contains(u.group(1))) problems.add(f.getName() + ":" + (i + 1) + "  arayüze sabit metin: \"" + u.group(1) + "\"");
                if (engineText.matcher(lines[i]).find()) problems.add(f.getName() + ":" + (i + 1) + "  motor metni: " + lines[i].trim());
            }
        }
        if (!problems.isEmpty()) fail(problems.size() + " sabit Türkçe metin:\n" + String.join("\n", problems.subList(0, Math.min(120, problems.size()))));
    }
}
