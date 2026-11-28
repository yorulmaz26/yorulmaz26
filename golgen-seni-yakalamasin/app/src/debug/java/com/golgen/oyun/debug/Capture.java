package com.golgen.oyun.debug;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.Log;

import com.golgen.oyun.GameView;
import com.golgen.oyun.core.Bot;
import com.golgen.oyun.core.GameWorld;

import java.io.File;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * YALNIZCA DEBUG DERLEMESİ — mağaza görselleri ve tanıtım videoları için gerçek oyun görüntüsü çekimi.
 *
 * <p>Oyun döngüsü durdurulur; GameView sabit 1/60 sn adımlarla ilerletilir ve her kare ekran
 * dışı bir tuvale (cihaz çözünürlüğünde) çizilip dosyaya yazılır. Böylece yavaş bir emülatörde
 * de akıcı 60 FPS görüntü elde edilir. Oyunu otomatik pilot ({@link Bot}) oynar; dünya
 * deterministiktir (sabit tohum), aynı sahne her çalıştırmada aynı çıkar.
 *
 * <p>Kullanım: adb shell am start -n com.golgen.oyun/.MainActivity --es cekim "ad=s01;dil=tr;gorev=17;tohum=5;atla=8;kare=180"
 * <ul>
 *   <li>ad: sahne adı (klasör), dil: tr/en/de/it/fr/es</li>
 *   <li>gorev: bölüm indeksi (0 tabanlı) veya sonsuz=1; tohum: dünya tohumu</li>
 *   <li>atla: kayıttan önce oyun zamanı (sn) ileri sar; asama=2: 2. aşamaya kadar ileri sar</li>
 *   <li>bitir=1;once=2.5: koşunun bittiği andan 'once' sn önce kayda başla (oyun sonu ekranı dahil)</li>
 *   <li>ekran=menu|karakter|gorevler|gunluk|basarilar|rekorlar|ayarlar|canyok: oyun dışı ekran</li>
 *   <li>bekle: kayıttan önce ekranı (sn) ilerlet; kare: kaydedilecek kare sayısı; png=1: PNG (tek kare için)</li>
 *   <li>parmak=1: tek parmak kontrolünü gösteren dokunma işareti çizer</li>
 * </ul>
 * Çıktı: getExternalFilesDir(null)/cekim/&lt;ad&gt;/00000.jpg … ve bitince "bitti" dosyası; logcat: GolgenCekim.
 */
public final class Capture {
    private Capture() {}

    private static final float DT = 1f / 60f;
    private static volatile boolean busy;

    /** GameView.handleDebugIntent tarafından yansıma ile çağrılır (UI iş parçacığı). */
    public static void run(final GameView g, final String spec) {
        if (busy) { Log.w("GolgenCekim", "meşgul, atlandı: " + spec); return; }
        busy = true;
        g.debugSetCapturing(true);           // oyun döngüsü durur, ekran çizimi askıya alınır
        new Thread(() -> {
            try {
                doRun(g, parse(spec));
            } catch (Throwable t) {
                Log.e("GolgenCekim", "hata: " + spec, t);
            } finally {
                busy = false;
                g.post(() -> g.debugSetCapturing(false));
            }
        }, "cekim").start();
    }

    private static Map<String, String> parse(String spec) {
        Map<String, String> m = new HashMap<>();
        for (String part : spec.split(";")) {
            int i = part.indexOf('=');
            if (i > 0) m.put(part.substring(0, i).trim(), part.substring(i + 1).trim());
        }
        return m;
    }

    private static float num(Map<String, String> m, String k, float def) {
        try { return m.containsKey(k) ? Float.parseFloat(m.get(k)) : def; } catch (NumberFormatException e) { return def; }
    }

    private static void doRun(GameView g, Map<String, String> m) throws Exception {
        String name = m.containsKey("ad") ? m.get("ad") : "sahne";
        File dir = new File(g.getContext().getExternalFilesDir(null), "cekim/" + name);
        deleteRecursive(dir);
        if (!dir.mkdirs() && !dir.isDirectory()) throw new IllegalStateException("klasör yok: " + dir);
        if (m.containsKey("dil")) g.setLanguage(m.get("dil"));
        int frames = (int) num(m, "kare", 1);
        boolean png = "1".equals(m.get("png"));
        boolean finger = "1".equals(m.get("parmak"));
        long seed = (long) num(m, "tohum", 7);
        Log.i("GolgenCekim", "başla ad=" + name + " " + m);

        if (m.containsKey("ekran")) {
            g.debugShow(m.get("ekran"));
            step(g, num(m, "bekle", 0.6f));
            record(g, dir, frames, png, false);
        } else {
            int level = (int) num(m, "gorev", -1);
            boolean endless = "1".equals(m.get("sonsuz")) || level < 0;
            float startAt = num(m, "atla", 0f);
            if ("1".equals(m.get("bitir")) && m.containsKey("sonra")) {
                // Koşu bitene kadar oyna, sonra oyun sonu ekranı açılana kadar 'sonra' sn ilerlet
                startRun(g, endless, level, seed);
                runUntilEnd(g, 600f);
                step(g, num(m, "sonra", 2.5f));
            } else if ("1".equals(m.get("bitir"))) {
                // 1. geçiş: koşunun ne zaman bittiğini bul (deterministik), 2. geçiş: bitişten önce kayda başla
                startRun(g, endless, level, seed);
                float tEnd = runUntilEnd(g, 600f);
                Log.i("GolgenCekim", "koşu bitişi t=" + tEnd + " kazandı=" + g.play.debugWorld().state);
                startRun(g, endless, level, seed);
                skipTo(g, Math.max(0f, tEnd - num(m, "once", 2.5f)));
            } else {
                startRun(g, endless, level, seed);
                if ("2".equals(m.get("asama"))) {
                    float limit = 400f;
                    while (g.play.debugWorld().levelStage < 2 && g.play.debugWorld().state == GameWorld.RUNNING && g.play.debugWorld().time < limit) {
                        g.debugStep(DT);
                    }
                    skipTo(g, g.play.debugWorld().time + startAt);
                } else {
                    skipTo(g, startAt);
                }
            }
            record(g, dir, frames, png, finger);
        }
        new FileOutputStream(new File(dir, "bitti")).close();
        Log.i("GolgenCekim", "bitti ad=" + name + " kare=" + frames + " klasor=" + dir.getAbsolutePath());
    }

    private static void startRun(GameView g, boolean endless, int level, long seed) {
        g.play.debugSeed = seed;
        Bot bot = new Bot(null);
        bot.strong = true;
        g.play.debugPilot = bot::act;
        if (endless) g.debugStartEndless(); else g.debugStartMission(level);
        g.play.debugBegin();
    }

    private static void skipTo(GameView g, float gameTime) {
        int guard = 0;
        while (g.play.debugWorld().time < gameTime && g.play.debugWorld().state == GameWorld.RUNNING && guard++ < 60 * 900) {
            g.debugStep(DT);
        }
    }

    private static float runUntilEnd(GameView g, float limit) {
        while (g.play.debugWorld().state == GameWorld.RUNNING && g.play.debugWorld().time < limit) g.debugStep(DT);
        return g.play.debugWorld().time;
    }

    private static void step(GameView g, float seconds) {
        for (int i = 0; i < Math.round(seconds / DT); i++) g.debugStep(DT);
    }

    private static final Paint fingerFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint fingerRing = new Paint(Paint.ANTI_ALIAS_FLAG);

    private static void record(GameView g, File dir, int frames, boolean png, boolean finger) throws Exception {
        int w = g.getWidth(), h = g.getHeight();
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        float[] pos = new float[2];
        float fx = -1, fy = -1;
        for (int f = 0; f < frames; f++) {
            if (f > 0) g.debugStep(DT);
            c.drawColor(Color.BLACK);
            g.debugDraw(c);
            if (finger && g.debugCurrentIsPlay() && g.play.debugPlayerScreenPos(pos)) {
                // Tek parmak kontrol işareti: parmak, karakterin altında göreli hareket eder
                float tx = pos[0], ty = Math.min(h - 140f, pos[1] + 330f);
                fx = fx < 0 ? tx : fx + (tx - fx) * 0.35f;
                fy = fy < 0 ? ty : fy + (ty - fy) * 0.35f;
                fingerFill.setColor(0x55FFFFFF);
                c.drawCircle(fx, fy, 46f, fingerFill);
                fingerRing.setStyle(Paint.Style.STROKE);
                fingerRing.setStrokeWidth(5f);
                fingerRing.setColor(0xCCFFFFFF);
                c.drawCircle(fx, fy, 58f, fingerRing);
            }
            File out = new File(dir, String.format(java.util.Locale.ROOT, "%05d.%s", f, png ? "png" : "jpg"));
            try (FileOutputStream os = new FileOutputStream(out)) {
                bmp.compress(png ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG, png ? 100 : 92, os);
            }
            if (f % 60 == 0) Log.i("GolgenCekim", "kare " + f + "/" + frames);
        }
        bmp.recycle();
    }

    private static void deleteRecursive(File f) {
        if (!f.exists()) return;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteRecursive(k);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }
}
