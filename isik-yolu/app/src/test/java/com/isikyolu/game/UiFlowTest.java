package com.isikyolu.game;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;

import com.isikyolu.engine.Board;
import com.isikyolu.engine.DailyPuzzle;
import com.isikyolu.engine.Level;
import com.isikyolu.engine.Move;
import com.isikyolu.engine.Solver;
import com.isikyolu.game.screens.AchievementsScreen;
import com.isikyolu.game.screens.DailyScreen;
import com.isikyolu.game.screens.GameScreen;
import com.isikyolu.game.screens.LevelSelectScreen;
import com.isikyolu.game.screens.MenuScreen;
import com.isikyolu.game.screens.SettingsScreen;
import com.isikyolu.game.ui.Overlay;
import com.isikyolu.game.ui.TutorialOverlay;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.function.BooleanSupplier;

/**
 * Arayüz akış testi: uygulama gerçek çizimle (Robolectric NATIVE grafik) çalıştırılır,
 * tüm ekranlar açılır, bölümler dokunmatik ana bilgisayar üzerinden oynanır ve ekran görüntüleri kaydedilir.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w392dp-h850dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class UiFlowTest {
    private MainActivity act;

    private void idle(long ms) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms)); }

    private void waitFor(String what, BooleanSupplier cond) throws InterruptedException {
        for (int i = 0; i < 400 && !cond.getAsBoolean(); i++) { Thread.sleep(15); idle(20); }
        assertTrue("beklenen durum oluşmadı: " + what, cond.getAsBoolean());
    }

    private void shot(String name) throws Exception {
        String dir = System.getProperty("screens.dir");
        if (dir == null) return;
        View root = act.getWindow().getDecorView();
        Bitmap b = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
        // Robolectric kendiliğinden kare çizmez: cihazdaki gibi ~45 kare (60 FPS) çizerek animasyonları ilerlet
        Canvas frame = new Canvas(b);
        for (int i = 0; i < 45; i++) { idle(16); root.draw(frame); }
        b.eraseColor(0);
        root.draw(new Canvas(b));
        new File(dir).mkdirs();
        try (FileOutputStream o = new FileOutputStream(new File(dir, name + ".png"))) { b.compress(Bitmap.CompressFormat.PNG, 100, o); }
    }

    private Overlay topOverlay() {
        ViewGroup r = act.overlayLayer();
        for (int i = r.getChildCount() - 1; i >= 0; i--) if (r.getChildAt(i) instanceof Overlay) return (Overlay) r.getChildAt(i);
        return null;
    }

    private void closeOverlays() {
        Overlay o;
        while ((o = topOverlay()) != null) { o.dismiss(); idle(400); }
    }

    private void start() throws Exception {
        act = Robolectric.buildActivity(MainActivity.class).setup().get();
        idle(100);
        waitFor("bölümler yüklendi", () -> Game.levels() != null);
        waitFor("ana menü", () -> act.currentScreen() instanceof MenuScreen);
        idle(800);
    }

    /** Çözücünün en kısa çözümünü ekranın dokunma arayüzünden oynatır. */
    private void solveThroughUi(GameScreen gs, Level l) {
        Board b = new Board(l);
        for (Move m : Solver.solve(b, 500_000).moves) {
            int x = m.cell % l.width, y = m.cell / l.width;
            boolean ok = m.kind == Move.ROTATE ? gs.onRotate(x, y) : gs.onPush(x, y, m.dir);
            assertTrue("hamle reddedildi: bölüm " + l.id + " " + m, ok);
            b.apply(m);
            idle(30);
        }
    }

    @Test public void allScreensRenderAndFlowWorks() throws Exception {
        start();
        shot("02_ana_menu");

        // öğretici
        Game.progress().tutorialSeen = false;
        act.goLevel(1);
        idle(900);
        assertTrue(topOverlay() instanceof TutorialOverlay);
        shot("04a_ogretici");
        closeOverlays();
        Game.progress().tutorialSeen = true;

        // bölüm 1'i dokunarak çöz
        GameScreen g1 = (GameScreen) act.currentScreen();
        solveThroughUi(g1, Game.levels().get(1));
        idle(500);
        shot("06a_kazanma_efekti");
        idle(1600);
        assertNotNull("tamamlandı penceresi", topOverlay());
        shot("06_bolum_tamamlandi");
        assertEquals(3, Game.progress().stars[1]);
        assertTrue(Game.progress().isUnlocked(2));
        assertTrue(Game.progress().achievements.contains("ilk_isik"));
        closeOverlays();

        act.goLevels();
        idle(900);
        assertTrue(act.currentScreen() instanceof LevelSelectScreen);
        shot("03_bolum_secme");

        // farklı mekaniklerden örnek bölümler
        for (int id : new int[]{12, 25, 35, 45, 55, 65, 75, 85, 95}) {
            act.goLevel(id);
            idle(700);
            closeOverlays();
            idle(500);
            shot(String.format("05_oyun_bolum_%03d", id));
        }

        // oyun içi: hamle, geri alma, ipucu, duraklatma
        act.goLevel(38);
        idle(700);
        closeOverlays();
        GameScreen g = (GameScreen) act.currentScreen();
        Level l38 = Game.levels().get(38);
        Board b = new Board(l38);
        Move first = Solver.solve(b, 500_000).moves.get(0);
        assertTrue(g.onRotate(first.cell % l38.width, first.cell / l38.width));
        idle(400);
        shot("05_oyun_ekrani");
        Method undo = GameScreen.class.getDeclaredMethod("undo");
        undo.setAccessible(true);
        undo.invoke(g);
        idle(300);
        int hintsBefore = Game.progress().hints;
        Method req = GameScreen.class.getDeclaredMethod("requestHint");
        req.setAccessible(true);
        req.invoke(g);
        idle(500);
        assertNotNull(topOverlay());
        shot("07_ipucu_penceresi");
        closeOverlays();
        Method use = GameScreen.class.getDeclaredMethod("useHint");
        use.setAccessible(true);
        use.invoke(g);
        Thread.sleep(300);
        idle(600);
        assertEquals(hintsBefore - 1, Game.progress().hints);
        shot("07b_ipucu_tahtada");

        assertTrue(act.currentScreen().onBack()); // duraklatma açılır
        idle(500);
        assertNotNull(topOverlay());
        shot("08_duraklatma");
        closeOverlays();

        // günlük bulmaca
        act.goDaily();
        idle(800);
        assertTrue(act.currentScreen() instanceof DailyScreen);
        shot("09_gunluk_bulmaca");
        int today = DailyPuzzle.today();
        Level daily = DailyPuzzle.generate(today);
        act.goDailyGame(daily, today);
        idle(800);
        shot("09b_gunluk_oyun");
        solveThroughUi((GameScreen) act.currentScreen(), daily);
        idle(2200);
        assertTrue(Game.progress().dailyDone.contains(DailyPuzzle.keyFor(today)));
        shot("09c_gunluk_tamamlandi");
        closeOverlays();

        act.goAchievements();
        idle(800);
        assertTrue(act.currentScreen() instanceof AchievementsScreen);
        shot("10_basarilar");

        act.goSettings();
        idle(800);
        assertTrue(act.currentScreen() instanceof SettingsScreen);
        shot("11_ayarlar");

        TutorialOverlay.full(act).show(act.overlayLayer());
        idle(600);
        shot("04b_nasil_oynanir");
        closeOverlays();

        act.goMenu();
        idle(800);
        shot("02b_ana_menu_ilerleme");

        // kayıt: yeni etkinlik aynı ilerlemeyi okur
        Game.save();
        String saved = Game.progress().serialize();
        act.finish();
        MainActivity again = Robolectric.buildActivity(MainActivity.class).setup().get();
        assertEquals(saved, Game.progress().serialize());
        again.finish();
    }

    @Test public void splashScreenRenders() throws Exception {
        act = Robolectric.buildActivity(MainActivity.class).setup().get();
        idle(900);
        shot("01_acilis");
    }

    @Test public void allHundredLevelsPlayableThroughUi() throws Exception {
        start();
        Game.progress().tutorialSeen = true;
        for (int id = 1; id <= 100; id++) {
            assertTrue("kilitli: " + id, Game.progress().isUnlocked(id));
            act.goLevel(id);
            idle(300);
            closeOverlays();
            GameScreen gs = (GameScreen) act.currentScreen();
            solveThroughUi(gs, Game.levels().get(id));
            idle(1300);
            assertEquals("yıldız: bölüm " + id, 3, Game.progress().stars[id]);
            closeOverlays();
        }
        assertEquals(100, Game.progress().completedCount());
        assertEquals(300, Game.progress().totalStars());
        assertTrue(Game.progress().achievements.contains("efsane"));
        assertTrue(Game.progress().achievements.contains("kusursuz"));
        act.goLevels();
        idle(800);
        shot("03b_bolum_secme_tamamlandi");
    }
}
