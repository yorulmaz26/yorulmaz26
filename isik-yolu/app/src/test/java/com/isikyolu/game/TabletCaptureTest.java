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
import com.isikyolu.engine.Level;
import com.isikyolu.engine.Move;
import com.isikyolu.engine.Progress;
import com.isikyolu.engine.Solver;
import com.isikyolu.game.screens.GameScreen;
import com.isikyolu.game.screens.MenuScreen;
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
import java.time.Duration;

/**
 * Tablet mağaza ekran görüntüleri: uygulamanın gerçek arayüzü 7 inç (1200×1920, 600×960 dp) ve 10 inç
 * (1600×2560, 800×1280 dp) dikey tablet ekranında çizilir. Oyun tabletlerde dikey çalışır.
 * Yalnızca -PpromoDir=... ile çalışır; dil -PpromoLang=… ile seçilir.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class TabletCaptureTest {
    private MainActivity act;
    private Bitmap bmp;
    private Canvas canvas;

    private void idle(long ms) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms)); }

    private void draw() {
        View decor = act.getWindow().getDecorView();
        if (bmp == null) { bmp = Bitmap.createBitmap(decor.getWidth(), decor.getHeight(), Bitmap.Config.ARGB_8888); canvas = new Canvas(bmp); }
        bmp.eraseColor(0xFF000000);
        decor.draw(canvas);
    }

    private void warm(int frames) { for (int i = 0; i < frames; i++) { idle(33); draw(); } }

    private void shot(File dir, String name) throws Exception {
        draw();
        dir.mkdirs();
        try (FileOutputStream o = new FileOutputStream(new File(dir, name + ".png"))) { bmp.compress(Bitmap.CompressFormat.PNG, 100, o); }
    }

    /** Önce gecikmeli açılan pencerelerin ("Tamamlandı" 1,1 sn sonra açılır) gelmesini bekler, sonra hepsini kapatır. */
    private void closeOverlays() {
        idle(1500);
        ViewGroup r = act.overlayLayer();
        for (int i = r.getChildCount() - 1; i >= 0; i--)
            if (r.getChildAt(i) instanceof Overlay) ((Overlay) r.getChildAt(i)).dismiss();
        idle(600);
    }

    private void assertNoOverlay() {
        ViewGroup r = act.overlayLayer();
        for (int i = 0; i < r.getChildCount(); i++)
            assertTrue("ekran görüntüsünde pencere kalmamalı", !(r.getChildAt(i) instanceof Overlay));
    }

    private void prepare() {
        Progress p = Game.progress();
        p.tutorialSeen = true;
        long now = java.time.ZonedDateTime.of(2026, 11, 22, 11, 0, 0, 0, java.time.ZoneOffset.UTC).toInstant().toEpochMilli();
        com.isikyolu.engine.Lives.Clock clk = Game.clock();
        p.trustWall = now; p.trustElapsed = clk.elapsed; p.trustBoot = clk.boot;
        p.maxDay = -1; p.shopDay = -1;
        for (int i = 1; i <= 268; i++) {
            p.stars[i] = (i % 7 == 3 || i % 11 == 5) ? 2 : 3;
            p.bestMoves[i] = Game.levels().get(i).optimal + (p.stars[i] == 3 ? 0 : 2);
        }
        p.lastPlayed = 269;
        p.hints = 7; p.crystals = 1240; p.gems = 6; p.lives = 4;
        int today = Game.today();
        for (int d = 0; d < 6; d++) p.dailyDone.add(DailyPuzzle.keyFor(today - 1 - d));
        Achievements.update(p);
        Game.save();
    }

    /** Bölümü açar ve çözümün ilk 'moves' hamlesini oynar. */
    private void playLevel(int id, int moves) throws Exception {
        act.goLevel(id);
        idle(400);
        closeOverlays();
        GameScreen gs = (GameScreen) act.currentScreen();
        Level l = Game.levels().get(id);
        java.util.List<Move> sol = Solver.solve(new Board(l), 500_000).moves;
        int n = Math.min(moves, sol.size());
        for (int i = 0; i < n; i++) {
            Move m = sol.get(i);
            int x = m.cell % l.width, y = m.cell / l.width;
            assertTrue(m.kind == Move.ROTATE ? gs.onRotate(x, y) : gs.onPush(x, y, m.dir));
            // son hamlede kısa bekleme: hedefler yanarken çekilir, "Tamamlandı" penceresi açılmadan
            // (Robolectric kazanmadan hemen sonra test saatini ileri sarabiliyor)
            warm(i == n - 1 ? 3 : 12);
        }
    }

    private void capture(String sub) throws Exception {
        String dir = System.getProperty("promo.dir");
        Assume.assumeTrue("yalnızca -PpromoDir ile", dir != null && !dir.isEmpty());
        File out = new File(dir, sub);
        com.isikyolu.game.i18n.L.override = System.getProperty("promo.lang", "tr");
        act = Robolectric.buildActivity(MainActivity.class).setup().get();
        idle(100);
        for (int i = 0; i < 600 && !(act.currentScreen() instanceof MenuScreen); i++) { Thread.sleep(10); idle(30); }
        assertTrue(act.currentScreen() instanceof MenuScreen);
        prepare();
        act.goMenu();
        idle(700);
        warm(40);
        shot(out, "01_menu");
        playLevel(169, 8); // mantık bölümü: beş hedef birden yanar
        shot(out, "02_oyun_mantik");
        closeOverlays();
        playLevel(52, 3); // prizma: üç renk
        shot(out, "03_oyun_prizma");
        closeOverlays();
        act.goLevels();
        idle(700);
        closeOverlays();
        warm(30);
        assertNoOverlay();
        shot(out, "04_bolumler");
        act.goDaily();
        idle(700);
        closeOverlays();
        warm(30);
        assertNoOverlay();
        shot(out, "05_gunluk");
        com.isikyolu.game.i18n.L.override = null;
    }

    @Test @Config(sdk = 34, qualifiers = "w600dp-h960dp-xhdpi")
    public void tablet7() throws Exception { capture("tablet7"); }

    @Test @Config(sdk = 34, qualifiers = "w800dp-h1280dp-xhdpi")
    public void tablet10() throws Exception { capture("tablet10"); }
}
