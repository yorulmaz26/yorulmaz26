package com.isikyolu.game;

import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;

import com.isikyolu.engine.Achievements;
import com.isikyolu.engine.Board;
import com.isikyolu.engine.DailyPuzzle;
import com.isikyolu.engine.GameSession;
import com.isikyolu.engine.Level;
import com.isikyolu.engine.Move;
import com.isikyolu.engine.Progress;
import com.isikyolu.engine.Solver;
import com.isikyolu.game.screens.GameScreen;
import com.isikyolu.game.screens.LevelSelectScreen;
import com.isikyolu.game.screens.MenuScreen;
import com.isikyolu.game.ui.BoardView;
import com.isikyolu.game.ui.Overlay;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

/**
 * Play Store tanıtım malzemesi için kare kaydı: oyun gerçek çizimle oynatılır, her kare (30 FPS) JPEG olarak, seçilen
 * anlar PNG ekran görüntüsü olarak kaydedilir. Dokunuşlar ve ses olayları events.json'a yazılır.
 * Reklam yoktur (testte reklam sağlayıcısı kapalıdır). Yalnızca -PpromoDir=... verilince çalışır:
 * ./gradlew :app:testDebugUnitTest --tests '*PromoCaptureTest' -PpromoDir=/yol
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w360dp-h640dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class PromoCaptureTest {
    private static final int FPS = 30, FRAME_MS = 1000 / FPS;
    private MainActivity act;
    private File out;
    private Bitmap bmp;
    private Canvas canvas;
    // geçerli klip
    private File clipDir;
    private int frame;
    private StringBuilder events, uptimes;

    private void idle(long ms) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms)); }

    private static Object field(Object o, String name) throws Exception {
        java.lang.reflect.Field x = o.getClass().getDeclaredField(name); x.setAccessible(true); return x.get(o);
    }

    private static float f(Object o, String name) throws Exception {
        java.lang.reflect.Field x = o.getClass().getDeclaredField(name); x.setAccessible(true); return x.getFloat(o);
    }

    private void draw() {
        View decor = act.getWindow().getDecorView();
        if (bmp == null) { bmp = Bitmap.createBitmap(decor.getWidth(), decor.getHeight(), Bitmap.Config.ARGB_8888); canvas = new Canvas(bmp); }
        bmp.eraseColor(0xFF000000);
        decor.draw(canvas);
    }

    /** Animasyonları ilerletmeden (kayıtsız) bekler: kare çizmek BoardView animasyonunu ilerletir. */
    private void warm(int frames) { for (int i = 0; i < frames; i++) { idle(FRAME_MS); draw(); } }

    private void begin(String name) {
        clipDir = new File(out, "clips/" + name);
        clipDir.mkdirs();
        for (File old : clipDir.listFiles()) old.delete();
        frame = 0;
        events = new StringBuilder();
        uptimes = new StringBuilder();
    }

    private void rec(double seconds) throws Exception {
        int n = (int) Math.round(seconds * FPS);
        for (int i = 0; i < n; i++) {
            idle(FRAME_MS);
            draw();
            uptimes.append(',').append(android.os.SystemClock.uptimeMillis());
            try (FileOutputStream o = new FileOutputStream(new File(clipDir, String.format(Locale.ROOT, "%05d.jpg", frame)))) {
                bmp.compress(Bitmap.CompressFormat.JPEG, 93, o);
            }
            frame++;
        }
    }

    private void end() throws Exception {
        try (FileWriter w = new FileWriter(new File(clipDir, "events.json"), StandardCharsets.UTF_8)) {
            w.write("{\"fps\":" + FPS + ",\"frames\":" + frame + ",\"w\":" + bmp.getWidth() + ",\"h\":" + bmp.getHeight()
                    + ",\"events\":[" + (events.length() > 0 ? events.substring(1) : "") + "]"
                    + ",\"uptime\":[" + (uptimes.length() > 0 ? uptimes.substring(1) : "") + "]}");
        }
    }

    private void event(String kind, float x, float y, float x2, float y2) {
        events.append(String.format(Locale.ROOT, ",{\"f\":%d,\"k\":\"%s\",\"x\":%.1f,\"y\":%.1f,\"x2\":%.1f,\"y2\":%.1f}", frame, kind, x, y, x2, y2));
    }

    private void shot(String name) throws Exception {
        draw();
        File d = new File(out, "shots");
        d.mkdirs();
        try (FileOutputStream o = new FileOutputStream(new File(d, name + ".png"))) { bmp.compress(Bitmap.CompressFormat.PNG, 100, o); }
    }

    private void closeOverlays() {
        ViewGroup r = act.overlayLayer();
        for (int i = r.getChildCount() - 1; i >= 0; i--)
            if (r.getChildAt(i) instanceof Overlay) ((Overlay) r.getChildAt(i)).dismiss();
        idle(400);
    }

    private GameScreen open(int id) {
        act.goLevel(id);
        idle(400);
        closeOverlays();
        assertTrue(act.currentScreen() instanceof GameScreen);
        return (GameScreen) act.currentScreen();
    }

    /** Hamleyi oynar ve dokunuş/sürükleme konumunu (pencere pikseli) kaydeder. */
    private void play(GameScreen gs, Level l, Move m) throws Exception {
        BoardView bv = (BoardView) field(gs, "board");
        int[] loc = new int[2];
        bv.getLocationInWindow(loc);
        float ox = f(bv, "ox"), oy = f(bv, "oy"), cell = f(bv, "cell");
        int x = m.cell % l.width, y = m.cell / l.width;
        float cx = loc[0] + ox + (x + 0.5f) * cell, cy = loc[1] + oy + (y + 0.5f) * cell;
        if (m.kind == Move.ROTATE) {
            event("tap", cx, cy, cx, cy);
            assertTrue(gs.onRotate(x, y));
            event("rotate", cx, cy, cx, cy);
        } else {
            int[] DX = {0, 1, 0, -1}, DY = {-1, 0, 1, 0};
            event("drag", cx, cy, cx + DX[m.dir] * cell, cy + DY[m.dir] * cell);
            assertTrue(gs.onPush(x, y, m.dir));
            event("push", cx, cy, cx, cy);
        }
        GameSession ses = (GameSession) field(gs, "session");
        if (ses.isSolved()) event("win", 0, 0, 0, 0);
    }

    /** Bölümü baştan sona oynayan klip; shotAfter hamleden sonra ekran görüntüsü alınır (-1: yok). */
    private void levelClip(String name, int id, double perMove, int shotAfter, String shotName, String doneShot) throws Exception {
        Level l = Game.levels().get(id);
        GameScreen gs = open(id);
        warm(2);
        begin(name);
        rec(0.9);
        java.util.List<Move> sol = Solver.solve(new Board(l), 500_000).moves;
        for (int i = 0; i < sol.size(); i++) {
            play(gs, l, sol.get(i));
            boolean lastMove = i == sol.size() - 1;
            // bütün hedefler yanarken (Robolectric kazanmadan hemen sonra test saatini ileri sarabildiği için kısa bekleme)
            if (lastMove && i + 1 == shotAfter) { rec(0.10); shot(shotName); rec(1.15); }
            else {
                rec(lastMove ? 1.25 : perMove);
                if (i + 1 == shotAfter) shot(shotName);
            }
        }
        rec(1.6); // tamamlandı penceresi
        if (doneShot != null) shot(doneShot);
        end();
        closeOverlays();
        act.goLevels();
        idle(500);
    }

    private void prepareProgress() {
        Progress p = Game.progress();
        p.tutorialSeen = true;
        // gerçekçi tarih: 22 Kasım 2026 (Robolectric saati 1970'te başlar)
        long now = java.time.ZonedDateTime.of(2026, 11, 22, 11, 0, 0, 0, java.time.ZoneOffset.UTC).toInstant().toEpochMilli();
        com.isikyolu.engine.Lives.Clock clk = Game.clock();
        p.trustWall = now; p.trustElapsed = clk.elapsed; p.trustBoot = clk.boot;
        p.maxDay = -1; p.shopDay = -1;
        for (int i = 1; i <= 268; i++) {
            p.stars[i] = (i % 7 == 3 || i % 11 == 5) ? 2 : 3;
            p.bestMoves[i] = Game.levels().get(i).optimal + (p.stars[i] == 3 ? 0 : 2);
        }
        p.lastPlayed = 269;
        p.hints = 7;
        p.crystals = 1240;
        p.gems = 6;
        p.lives = 4;
        int today = Game.today();
        for (int d = 0; d < 6; d++) p.dailyDone.add(DailyPuzzle.keyFor(today - 1 - d));
        for (String s : new String[]{"mirror_gold", "board_galaxy", "beam_stardust", "mirror_rainbow", "beam_plasma", "board_sunset"}) p.owned.add(s);
        Achievements.update(p); // kazanılmış başarılar önceden işaretlenir: kayıtta afiş yağmuru olmasın
        Game.save();
    }

    private void skins(String mirror, String board, String beam) {
        Progress p = Game.progress();
        p.mirrorSkin = mirror; p.boardSkin = board; p.beamSkin = beam;
    }

    @Test public void capture() throws Exception {
        String dir = System.getProperty("promo.dir");
        Assume.assumeTrue("yalnızca -PpromoDir ile", dir != null && !dir.isEmpty());
        out = new File(dir);
        com.isikyolu.game.i18n.L.override = System.getProperty("promo.lang", "tr"); // kayıt dili: -PpromoLang=en …
        act = Robolectric.buildActivity(MainActivity.class).setup().get();
        idle(100);
        for (int i = 0; i < 600 && !(act.currentScreen() instanceof MenuScreen); i++) { Thread.sleep(10); idle(30); }
        assertTrue(act.currentScreen() instanceof MenuScreen);
        idle(800);
        prepareProgress();
        act.goMenu();
        idle(700);
        warm(40);
        shot("09_menu");
        begin("menu");
        rec(3.2);
        end();

        levelClip("a_temel", 12, 0.7, -1, null, null);
        levelClip("b_prizma", 52, 0.7, 3, "02_prizma", null); // son hamleden hemen sonra: üç renk yanarken
        levelClip("c_renk", 40, 0.6, -1, null, "03_yildizlar");
        levelClip("d_blok", 74, 0.65, 2, "06_bloklar", null);
        levelClip("e_kapi", 86, 0.65, 3, "05_kapilar", null);
        levelClip("f_mantik", 169, 0.55, 8, "01_mantik", null); // son hamleden hemen sonra: beş hedef birden yanarken
        levelClip("x_usta", 457, 0.55, -1, null, null);
        levelClip("z_blok2", 76, 0.6, -1, null, null);
        levelClip("y_karma", 95, 0.6, -1, null, null);
        skins("mirror_rainbow", "board_galaxy", "beam_plasma");
        levelClip("g_gorunum", 60, 0.55, 5, "08_gorunumler", null);
        skins("mirror_gold", "board_sunset", "beam_stardust");
        levelClip("g2_gorunum", 57, 0.6, 3, "08b_gorunumler", null);
        skins(com.isikyolu.engine.Economy.DEFAULT_MIRROR, com.isikyolu.engine.Economy.DEFAULT_BOARD, com.isikyolu.engine.Economy.DEFAULT_BEAM);

        // dünyalar ve bölüm seçimi: yavaşça kaydır
        act.goLevels();
        idle(700);
        android.widget.ScrollView sv = (android.widget.ScrollView) field(act.currentScreen(), "sv");
        int current = sv.getScrollY();
        int to = Math.max(0, current - (int) (sv.getHeight() * 0.62)); // tamamlanmış (yıldızlı) gruplar üstte, güncel grup altta
        int from = Math.max(0, to - 1500);
        sv.scrollTo(0, to);
        warm(30);
        shot("04_dunyalar");
        sv.scrollTo(0, from);
        warm(4);
        begin("h_bolumler");
        rec(0.6);
        for (int i = 0; i < 84; i++) {
            float t = i / 83f;
            sv.scrollTo(0, from + (int) ((to - from) * (0.5f - 0.5f * (float) Math.cos(Math.PI * t))));
            rec(1.0 / FPS);
        }
        rec(0.8);
        end();

        // günlük bulmaca
        act.goDaily();
        idle(700);
        warm(30);
        shot("07_gunluk");
        begin("i_gunluk");
        rec(3.2);
        end();
        assertTrue(new File(out, "clips/f_mantik/events.json").exists());
        assertTrue(act.currentScreen() instanceof com.isikyolu.game.screens.DailyScreen || act.currentScreen() instanceof LevelSelectScreen);
    }

    /** Play Store simgesi (512×512): uyarlanabilir simgenin arka planı + ön yüzü, tam kare (Play kendi maskesini uygular). */
    @Test public void icon() throws Exception {
        String dir = System.getProperty("promo.dir");
        Assume.assumeTrue("yalnızca -PpromoDir ile", dir != null && !dir.isEmpty());
        android.content.Context c = org.robolectric.RuntimeEnvironment.getApplication();
        Bitmap b = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        cv.drawColor(c.getColor(R.color.ic_launcher_background));
        android.graphics.drawable.Drawable fg = c.getDrawable(R.drawable.ic_launcher_foreground);
        fg.setBounds(-64, -64, 576, 576); // ön yüz güvenli alanda küçüktür: mağaza simgesinde biraz büyütülür
        fg.draw(cv);
        File d = new File(dir, "icon");
        d.mkdirs();
        try (FileOutputStream o = new FileOutputStream(new File(d, "icon_512.png"))) { b.compress(Bitmap.CompressFormat.PNG, 100, o); }
    }
}
