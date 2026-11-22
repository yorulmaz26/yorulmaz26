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
        for (int i = 0; i < 700 && !cond.getAsBoolean(); i++) { Thread.sleep(15); idle(20); }
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
        for (int i = r.getChildCount() - 1; i >= 0; i--)
            if (r.getChildAt(i) instanceof Overlay && !((Overlay) r.getChildAt(i)).isClosing()) return (Overlay) r.getChildAt(i);
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
        for (int id : new int[]{12, 25, 35, 45, 55, 65, 75, 85, 95, 150, 250, 350, 450, 500}) {
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
        int today = Game.today(); // uygulamanın güvenilir günü
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
        com.isikyolu.engine.Progress before = Game.progress();
        int[] stars = before.stars.clone();
        int hints = before.hints, lives = before.lives;
        java.util.Set<String> ach = new java.util.HashSet<>(before.achievements);
        act.finish();
        MainActivity again = Robolectric.buildActivity(MainActivity.class).setup().get();
        assertArrayEquals(stars, Game.progress().stars);
        assertEquals(hints, Game.progress().hints);
        assertEquals(lives, Game.progress().lives);
        assertEquals(ach, Game.progress().achievements);
        again.finish();
    }

    /** Hamle hakkı biter, can gider; can kalmayınca bölüm açılmaz; 15 dk sonra can gelir. */
    @Test public void livesFlow() throws Exception {
        start();
        Game.progress().tutorialSeen = true;
        com.isikyolu.engine.Progress p = Game.progress();
        assertEquals(4, p.lives);
        shot("12_menu_canlar");
        final int lvl = 12;
        Level l = Game.levels().get(lvl);
        java.util.Set<Integer> sol = new java.util.HashSet<>();
        for (Move mv : Solver.solve(new Board(l), 100000).moves) sol.add(mv.cell);
        int mx = -1, my = -1;
        for (com.isikyolu.engine.Piece pc : l.pieces)
            if (pc.type == com.isikyolu.engine.Cell.MIRROR && pc.rotatable && !sol.contains(pc.y * l.width + pc.x)) { mx = pc.x; my = pc.y; }
        if (mx < 0) for (com.isikyolu.engine.Piece pc : l.pieces) if (pc.type == com.isikyolu.engine.Cell.MIRROR && pc.rotatable) { mx = pc.x; my = pc.y; }
        int budget = com.isikyolu.engine.Lives.moveBudget(l.optimal);
        for (int round = 0; round < 4; round++) {
            act.goLevel(lvl);
            idle(500);
            closeOverlays();
            GameScreen gs = (GameScreen) act.currentScreen();
            // çözüme ait olmayan bir aynayı çevirerek hakkı çözmeden bitir
            for (int i = 0; i < budget; i++) { assertTrue(gs.onRotate(mx, my)); idle(20); }
            waitFor("hak bitti penceresi", () -> topOverlay() != null);
            idle(300);
            assertEquals("tur " + round, 3 - round, p.lives);
            if (round == 0) shot("13_hak_bitti");
            View again = findByDescription(topOverlay(), "Bölümler");
            assertNotNull(again);
            again.performClick(); // devam etmeden bırak: can gider
            idle(600);
            closeOverlays();
        }
        assertEquals(0, p.lives);
        act.goLevels();
        idle(600);
        act.goLevel(2);
        idle(600);
        assertTrue("can yokken bölüm açılmamalı", act.currentScreen() instanceof LevelSelectScreen);
        assertNotNull("can kalmadı penceresi", topOverlay());
        shot("14_can_kalmadi");
        closeOverlays();
        // 15 dakika geçer (monoton saat)
        org.robolectric.shadows.ShadowSystemClock.advanceBy(Duration.ofMinutes(15));
        Game.refreshLives();
        assertEquals(1, p.lives);
        // bölümden yarıda çıkmak canı geri getirmez
        act.goLevel(lvl);
        idle(500);
        closeOverlays();
        GameScreen gs = (GameScreen) act.currentScreen();
        assertTrue(gs.onRotate(mx, my));
        idle(100);
        assertEquals(0, p.lives);
        act.goMenu();
        idle(500);
        assertEquals(0, p.lives);
        assertEquals(0, p.attemptLevel);
    }

    private static View findByDescription(View v, String prefix) {
        if (v.getContentDescription() != null && v.getContentDescription().toString().startsWith(prefix)) return v;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) { View f = findByDescription(g.getChildAt(i), prefix); if (f != null) return f; }
        }
        return null;
    }

    /** Market sekmeleri, önizleme, görünüm satın alma ve hak bitince kristalle devam. */
    @Test public void marketFlow() throws Exception {
        start();
        com.isikyolu.engine.Progress p = Game.progress();
        p.tutorialSeen = true;
        p.crystals = 3000; p.gems = 10;
        for (int i = 1; i <= 300; i++) p.stars[i] = 1;
        for (int t = 0; t < 5; t++) {
            act.goMarket(t);
            idle(700);
            shot("15_market_" + t);
        }
        // önizleme
        act.goMarket(3);
        idle(500);
        Method prev = com.isikyolu.game.screens.MarketScreen.class.getDeclaredMethod("preview", com.isikyolu.engine.Economy.Item.class, int.class, String.class);
        prev.setAccessible(true);
        com.isikyolu.engine.Economy.Item gal = com.isikyolu.engine.Economy.item("board_galaxy");
        prev.invoke(act.currentScreen(), gal, gal.basePrice, null);
        idle(600);
        shot("16_onizleme");
        closeOverlays();
        int day = Game.shopDay();
        for (String id : new String[]{"mirror_gold", "board_galaxy", "beam_stardust"})
            assertTrue(id, com.isikyolu.engine.Economy.buy(p, com.isikyolu.engine.Economy.item(id), day, Game.clock()));
        assertEquals("mirror_gold", p.mirrorSkin);
        assertEquals("board_galaxy", p.boardSkin);
        assertEquals(3000 - 400 - 900, p.crystals);
        assertEquals(5, p.gems);
        act.goLevel(38);
        idle(600);
        closeOverlays();
        shot("17_gorunum_oyunda");
        // hak bitince 80 kristalle +5 hak
        final int lvl = 12;
        Level l = Game.levels().get(lvl);
        int[] pm = probeMirror(lvl);
        int mx = pm[0], my = pm[1];
        act.checkAchievements(); // testte verilen yıldızların başarı ödüllerini önceden işle
        act.goLevel(lvl);
        idle(500);
        closeOverlays();
        GameScreen gs = (GameScreen) act.currentScreen();
        int budget = com.isikyolu.engine.Lives.moveBudget(l.optimal);
        for (int i = 0; i < budget; i++) { assertTrue(gs.onRotate(mx, my)); idle(20); }
        waitFor("hak bitti penceresi", () -> topOverlay() != null);
        idle(300);
        shot("18_devam_teklifi");
        int kr = p.crystals, lives = p.lives;
        View cont = findByDescription(topOverlay(), "+5 hak");
        assertNotNull("devam butonu", cont);
        cont.performClick();
        idle(500);
        assertEquals(kr - 80, p.crystals);
        assertEquals("devam etmek can harcamaz", lives, p.lives);
        assertTrue("devamdan sonra hamle yapılabilir", gs.onRotate(mx, my));
        assertTrue(p.achievements.contains("alisveris"));
    }

    // ---------- denetim bulgularının geri dönüş testleri ----------

    private GameScreen openLevel(int id) throws Exception {
        act.goLevel(id);
        idle(500);
        closeOverlays();
        assertTrue(act.currentScreen() instanceof GameScreen);
        return (GameScreen) act.currentScreen();
    }

    /** Çözüme ait olmayan bir döner ayna (tek başına çevrilince bölümü bitirmez). */
    private int[] probeMirror(int id) {
        Level l = Game.levels().get(id);
        java.util.Set<Integer> sol = new java.util.HashSet<>();
        for (Move mv : Solver.solve(new Board(l), 100000).moves) sol.add(mv.cell);
        for (com.isikyolu.engine.Piece pc : l.pieces)
            if (pc.type == com.isikyolu.engine.Cell.MIRROR && pc.rotatable && !sol.contains(pc.y * l.width + pc.x)) return new int[]{pc.x, pc.y};
        for (com.isikyolu.engine.Piece pc : l.pieces) // optimumu 1'den büyük bölümde tek ayna tek başına çözmez
            if (pc.type == com.isikyolu.engine.Cell.MIRROR && pc.rotatable && l.optimal > 1) return new int[]{pc.x, pc.y};
        throw new AssertionError("yoklama aynası yok: " + id);
    }

    private void burnBudget(GameScreen gs, int id) {
        int[] pm = probeMirror(id);
        int budget = com.isikyolu.engine.Lives.moveBudget(Game.levels().get(id).optimal);
        for (int i = 0; i < budget; i++) { assertTrue(gs.onRotate(pm[0], pm[1])); idle(10); }
    }

    /** Bulgu: hak bitince hemen geri basılırsa can gitmiyor, eski pencere başka ekranda açılıyordu. */
    @Test public void backRightAfterOutOfMovesLosesLife() throws Exception {
        start();
        com.isikyolu.engine.Progress p = Game.progress();
        p.tutorialSeen = true;
        GameScreen gs = openLevel(12);
        burnBudget(gs, 12);
        assertEquals(3, p.lives);
        assertTrue(act.currentScreen().onBack()); // pencere gelmeden geri
        idle(200);
        for (int i = 0; i < 40; i++) { Thread.sleep(10); idle(50); }
        assertTrue(act.currentScreen() instanceof LevelSelectScreen);
        assertNull("eski pencere yeni ekranda açılmamalı", topOverlay());
        assertEquals("deneme kapanmalı", 0, p.attemptLevel);
        assertEquals("can geri gelmemeli", 3, p.lives);
    }

    /** Bulgu: pencere kapanırken çift dokunuş ipucunu iki kez harcatıyordu. */
    @Test public void doubleTapSpendsHintOnce() throws Exception {
        start();
        com.isikyolu.engine.Progress p = Game.progress();
        p.tutorialSeen = true;
        GameScreen gs = openLevel(38);
        Method req = GameScreen.class.getDeclaredMethod("requestHint");
        req.setAccessible(true);
        req.invoke(gs);
        idle(400);
        int before = p.hints;
        View b = findByDescription(topOverlay(), "İpucu Göster");
        assertNotNull(b);
        // gerçek parmak gibi: dokunuşlar pencerenin kökünden gelir; ikinci dokunuş 60 ms sonra
        com.isikyolu.game.ui.Overlay ov = topOverlay();
        int[] lo = new int[2], lb = new int[2];
        ov.getLocationInWindow(lo); b.getLocationInWindow(lb);
        float bx = lb[0] - lo[0] + b.getWidth() / 2f, by = lb[1] - lo[1] + b.getHeight() / 2f;
        long t = android.os.SystemClock.uptimeMillis();
        for (int k = 0; k < 2; k++) {
            long t0 = t + k * 60;
            ov.dispatchTouchEvent(android.view.MotionEvent.obtain(t0, t0, android.view.MotionEvent.ACTION_DOWN, bx, by, 0));
            ov.dispatchTouchEvent(android.view.MotionEvent.obtain(t0, t0 + 20, android.view.MotionEvent.ACTION_UP, bx, by, 0));
            idle(60);
        }
        b.performClick(); // doğrudan ikinci tıklama da (çift tıklama koruması) yok sayılmalı
        for (int i = 0; i < 30; i++) { Thread.sleep(10); idle(30); }
        assertEquals("tek ipucu harcanmalı", before - 1, p.hints);
    }

    /** Bulgu: can 0 iken "Tekrar Dene" oyuncuyu donmuş tahtada bırakıyordu. */
    @Test public void retryWithNoLivesGoesToLevels() throws Exception {
        start();
        com.isikyolu.engine.Progress p = Game.progress();
        p.tutorialSeen = true;
        p.lives = 1;
        GameScreen gs = openLevel(12);
        burnBudget(gs, 12);
        waitFor("hak bitti penceresi", () -> topOverlay() != null);
        idle(300);
        View retry = findByDescription(topOverlay(), "Tekrar Dene");
        retry.performClick();
        idle(600);
        assertEquals(0, p.lives);
        assertTrue("bölümler ekranına dönülmeli", act.currentScreen() instanceof LevelSelectScreen);
        assertNotNull("can kalmadı penceresi", topOverlay());
        // can satın alınınca bölüm açılır
        p.crystals = 500;
        View buy = findByDescription(topOverlay(), "+1 can al");
        assertNotNull(buy);
        buy.performClick();
        idle(800);
        closeOverlays();
        assertTrue("can alınınca bölüm açılmalı", act.currentScreen() instanceof GameScreen);
    }

    /** Bulgu: blok sürüklemede titreme/uzun sürükleme birden çok itme ve hak harcatıyordu. */
    @Test public void oneDragPushesBlockOnce() throws Exception {
        start();
        com.isikyolu.engine.Progress p = Game.progress();
        p.tutorialSeen = true;
        GameScreen gs = openLevel(71);
        java.lang.reflect.Field fb = GameScreen.class.getDeclaredField("board"); fb.setAccessible(true);
        com.isikyolu.game.ui.BoardView bv = (com.isikyolu.game.ui.BoardView) fb.get(gs);
        java.lang.reflect.Field fs = GameScreen.class.getDeclaredField("session"); fs.setAccessible(true);
        com.isikyolu.engine.GameSession ses = (com.isikyolu.engine.GameSession) fs.get(gs);
        float ox = f(bv, "ox"), oy = f(bv, "oy"), cell = f(bv, "cell");
        int blk = ses.board().blockCells()[0];
        int bx = blk % ses.board().width, by = blk / ses.board().width;
        int dir = -1;
        for (int d = 0; d < 4; d++) if (ses.board().canPush(blk, d)) { dir = d; break; }
        assertTrue(dir >= 0);
        float sx = ox + (bx + 0.5f) * cell, sy = oy + (by + 0.5f) * cell;
        int[] DX = {0, 1, 0, -1}, DY = {-1, 0, 1, 0};
        long t = android.os.SystemClock.uptimeMillis();
        bv.dispatchTouchEvent(android.view.MotionEvent.obtain(t, t, android.view.MotionEvent.ACTION_DOWN, sx, sy, 0));
        // titreme: ±0.5 hücre civarında gidip gelme, sonra 1.8 hücre uzun sürükleme
        float[] amounts = {0.3f, 0.51f, 0.49f, 0.51f, 0.49f, 0.9f, 1.4f, 1.8f};
        for (int i = 0; i < amounts.length; i++)
            bv.dispatchTouchEvent(android.view.MotionEvent.obtain(t, t + 10 * (i + 1), android.view.MotionEvent.ACTION_MOVE,
                    sx + DX[dir] * amounts[i] * cell, sy + DY[dir] * amounts[i] * cell, 0));
        bv.dispatchTouchEvent(android.view.MotionEvent.obtain(t, t + 200, android.view.MotionEvent.ACTION_UP, sx + DX[dir] * 1.8f * cell, sy + DY[dir] * 1.8f * cell, 0));
        idle(100);
        assertEquals("bir sürüklemede tek itme", 1, ses.actions());
    }

    private static float f(Object o, String name) throws Exception {
        java.lang.reflect.Field x = o.getClass().getDeclaredField(name); x.setAccessible(true); return x.getFloat(o);
    }

    /** Bulgu: günlük bulmaca cihaz tarihine göre ödül veriyordu; yalnızca güvenilir "bugün" ödüllendirilir. */
    @Test public void dailyRewardOnlyForTrustedToday() throws Exception {
        start();
        com.isikyolu.engine.Progress p = Game.progress();
        int tomorrow = Game.today() + 1;
        Level dl = DailyPuzzle.generate(tomorrow);
        act.goDailyGame(dl, tomorrow);
        idle(600);
        int before = p.crystals;
        solveThroughUi((GameScreen) act.currentScreen(), dl);
        idle(1600);
        assertEquals("yarının bulmacası ödül vermemeli", before, p.crystals);
        closeOverlays();
        int today = Game.today();
        Level tl = DailyPuzzle.generate(today);
        act.goDailyGame(tl, today);
        idle(600);
        solveThroughUi((GameScreen) act.currentScreen(), tl);
        idle(1600);
        assertTrue("bugünün bulmacası ödül vermeli", p.crystals > before);
    }

    // ---------- denetim 2 bulgularının geri dönüş testleri ----------

    private static Object field(Object o, String name) throws Exception {
        java.lang.reflect.Field x = o.getClass().getDeclaredField(name); x.setAccessible(true); return x.get(o);
    }

    private static void setField(Object o, String name, Object v) throws Exception {
        java.lang.reflect.Field x = o.getClass().getDeclaredField(name); x.setAccessible(true); x.set(o, v);
    }

    private void waitPlan(GameScreen gs) throws Exception {
        waitFor("plan bitti", () -> { try { return !(Boolean) field(gs, "thinking"); } catch (Exception e) { return false; } });
    }

    /** Bulgu: 400 ms çift tıklama koruması art arda Geri Al'ı yutuyordu. */
    @Test public void rapidUndoTapsAllCount() throws Exception {
        start();
        Game.progress().tutorialSeen = true;
        GameScreen gs = openLevel(12);
        com.isikyolu.engine.GameSession ses = (com.isikyolu.engine.GameSession) field(gs, "session");
        for (int i = 0; i < 3; i++) assertTrue(gs.onRotate(1, 1));
        View undo = ((ViewGroup) field(gs, "undoButton")).getChildAt(0);
        for (int i = 0; i < 3; i++) { undo.performClick(); idle(150); }
        assertEquals("üç hızlı Geri Al üç hamleyi geri almalı", 0, ses.moves());
    }

    /** Bulgu: gece yarısından önce açılan günlük bulmaca sonra çözülünce ödül/seri kayboluyordu. */
    @Test public void dailyStartedBeforeMidnightStillRewards() throws Exception {
        start();
        com.isikyolu.engine.Progress p = Game.progress();
        int today = Game.today();
        Level dl = DailyPuzzle.generate(today);
        act.goDailyGame(dl, today);
        idle(600);
        GameScreen gs = (GameScreen) act.currentScreen();
        p.trustWall += Game.msUntilNextDay() + 60_000; // güvenilir saat gece yarısını geçti
        assertEquals(today + 1, Game.today());
        int before = p.crystals;
        solveThroughUi(gs, dl);
        idle(1600);
        assertTrue("başlandığı günün ödülü verilmeli", p.crystals > before);
        assertTrue(p.dailyDone.contains(DailyPuzzle.keyFor(today)));
        assertEquals(1, p.dailyStreak(today));
    }

    /** Bulgu: kazandıktan hemen sonra geri basılınca başarı ve ödülü atlanıyor, Başarılar ekranı ödülsüz işaretliyordu. */
    @Test public void achievementsGrantedEvenOnQuickBack() throws Exception {
        start();
        com.isikyolu.engine.Progress p = Game.progress();
        p.tutorialSeen = true;
        assertFalse(p.achievements.contains("ilk_isik"));
        GameScreen gs = openLevel(1);
        int before = p.crystals;
        solveThroughUi(gs, Game.levels().get(1));
        idle(200);
        assertTrue(act.currentScreen().onBack());
        idle(400);
        assertTrue(act.currentScreen() instanceof LevelSelectScreen);
        assertTrue(p.achievements.contains("ilk_isik"));
        assertTrue("başarı ödülü verilmeli", p.crystals >= before + 30);
        // Başarılar ekranı yeni başarıyı ödülüyle verir
        p.recordLevel(2, 3, Game.levels().get(2).optimal, false);
        p.recordLevel(3, 3, Game.levels().get(3).optimal, false);
        p.undoCount = 1000;
        int c0 = p.crystals, a0 = p.achievements.size();
        act.goAchievements();
        idle(500);
        if (p.achievements.size() > a0) assertTrue("Başarılar ekranı ödülsüz işaretlememeli", p.crystals > c0);
    }

    /** Bulgu: ipucunun "önce Geri Al" talimatından sapınca bekleyen sayaç sıfırlanmıyordu. */
    @Test public void deviationClearsPendingUndoHint() throws Exception {
        start();
        com.isikyolu.engine.Progress p = Game.progress();
        p.tutorialSeen = true;
        p.hints = 5;
        GameScreen gs = openLevel(12);
        assertTrue(gs.onRotate(1, 1));
        assertTrue(gs.onRotate(1, 1));
        Method use = GameScreen.class.getDeclaredMethod("useHint");
        use.setAccessible(true);
        use.invoke(gs);
        waitPlan(gs);
        idle(50);
        assertEquals("(1,1) çözüm aynası: bir Geri Al çözüme bir hamle yaklaştırır", 1, (int) (Integer) field(gs, "pendingUndoHint"));
        assertTrue(gs.onRotate(5, 1));
        assertEquals(0, (int) (Integer) field(gs, "pendingUndoHint"));
    }

    /** Bulgu: iade edilen ipucu bölümü ipucuyla çözülmüş sayıyordu. */
    @Test public void refundedHintKeepsNoHintCredit() throws Exception {
        start();
        com.isikyolu.engine.Progress p = Game.progress();
        p.tutorialSeen = true;
        p.hints = 5;
        GameScreen gs = openLevel(12);
        com.isikyolu.engine.GameSession ses = (com.isikyolu.engine.GameSession) field(gs, "session");
        while (ses.budgetLeft() > 1) { assertTrue(gs.onRotate(1, 1)); }
        Method use = GameScreen.class.getDeclaredMethod("useHint");
        use.setAccessible(true);
        use.invoke(gs);
        waitPlan(gs);
        assertEquals("ipucu iade edilmeli", 5, p.hints);
        assertEquals(0, ses.hintsUsed());
    }

    private com.isikyolu.game.ui.BoardView boardOf(GameScreen gs) throws Exception { return (com.isikyolu.game.ui.BoardView) field(gs, "board"); }

    /** Bulgu: Duraklat penceresi açıkken süren dokunma pencerenin altında hamle yapıp can yakıyordu. */
    @Test public void touchUnderPauseDoesNotMove() throws Exception {
        start();
        com.isikyolu.engine.Progress p = Game.progress();
        p.tutorialSeen = true;
        p.shields = 0;
        GameScreen gs = openLevel(12);
        com.isikyolu.game.ui.BoardView bv = boardOf(gs);
        com.isikyolu.engine.GameSession ses = (com.isikyolu.engine.GameSession) field(gs, "session");
        float ox = f(bv, "ox"), oy = f(bv, "oy"), cell = f(bv, "cell");
        float sx = ox + 1.5f * cell, sy = oy + 1.5f * cell;
        long t = android.os.SystemClock.uptimeMillis();
        bv.dispatchTouchEvent(android.view.MotionEvent.obtain(t, t, android.view.MotionEvent.ACTION_DOWN, sx, sy, 0));
        act.onBackPressed(); // duraklat
        idle(300);
        assertNotNull(topOverlay());
        bv.dispatchTouchEvent(android.view.MotionEvent.obtain(t, t + 400, android.view.MotionEvent.ACTION_UP, sx, sy, 0));
        idle(100);
        assertEquals("pencere altında hamle yapılmamalı", 0, ses.moves());
        assertEquals(4, p.lives);
        assertEquals(0, p.attemptLevel);
    }

    /** Bulgu: başarılı itmede blok yarım kare sıçrıyordu; giriş kapanınca sürükleme donuk kalıyordu. */
    @Test public void blockSlidesSmoothlyAndDragEndsWhenInputOff() throws Exception {
        start();
        Game.progress().tutorialSeen = true;
        GameScreen gs = openLevel(71);
        com.isikyolu.game.ui.BoardView bv = boardOf(gs);
        com.isikyolu.engine.GameSession ses = (com.isikyolu.engine.GameSession) field(gs, "session");
        float ox = f(bv, "ox"), oy = f(bv, "oy"), cell = f(bv, "cell");
        int w = ses.board().width;
        int blk = ses.board().blockCells()[0];
        int dir = -1;
        for (int d = 0; d < 4; d++) if (ses.board().canPush(blk, d)) { dir = d; break; }
        int[] DX = {0, 1, 0, -1}, DY = {-1, 0, 1, 0};
        float sx = ox + (blk % w + 0.5f) * cell, sy = oy + (blk / w + 0.5f) * cell;
        long t = android.os.SystemClock.uptimeMillis();
        bv.dispatchTouchEvent(android.view.MotionEvent.obtain(t, t, android.view.MotionEvent.ACTION_DOWN, sx, sy, 0));
        bv.dispatchTouchEvent(android.view.MotionEvent.obtain(t, t + 10, android.view.MotionEvent.ACTION_MOVE, sx + DX[dir] * 0.3f * cell, sy + DY[dir] * 0.3f * cell, 0));
        bv.dispatchTouchEvent(android.view.MotionEvent.obtain(t, t + 20, android.view.MotionEvent.ACTION_MOVE, sx + DX[dir] * 0.52f * cell, sy + DY[dir] * 0.52f * cell, 0));
        assertEquals(1, ses.actions());
        int to = blk + DX[dir] + DY[dir] * w;
        float[] bx = (float[]) field(bv, "blockOffX"), by = (float[]) field(bv, "blockOffY");
        float off = DX[dir] != 0 ? bx[to] * DX[dir] : by[to] * DY[dir];
        assertEquals("blok parmağın bıraktığı yerden kaymalı (sıçramamalı)", -0.48f * cell, off, 0.05f * cell);
        bv.dispatchTouchEvent(android.view.MotionEvent.obtain(t, t + 30, android.view.MotionEvent.ACTION_UP, sx, sy, 0));
        idle(600);
        // ikinci sürükleme sırasında giriş kapanır
        int b2 = ses.board().blockCells()[0];
        int d2 = -1;
        for (int d = 0; d < 4; d++) if (ses.board().canPush(b2, d)) { d2 = d; break; }
        float s2x = ox + (b2 % w + 0.5f) * cell, s2y = oy + (b2 / w + 0.5f) * cell;
        t = android.os.SystemClock.uptimeMillis();
        bv.dispatchTouchEvent(android.view.MotionEvent.obtain(t, t, android.view.MotionEvent.ACTION_DOWN, s2x, s2y, 0));
        bv.dispatchTouchEvent(android.view.MotionEvent.obtain(t, t + 10, android.view.MotionEvent.ACTION_MOVE, s2x + DX[d2] * 0.3f * cell, s2y + DY[d2] * 0.3f * cell, 0));
        bv.setInputEnabled(false);
        bv.dispatchTouchEvent(android.view.MotionEvent.obtain(t, t + 20, android.view.MotionEvent.ACTION_UP, s2x, s2y, 0));
        bv.setInputEnabled(true);
        assertFalse("sürükleme bitmeli", (Boolean) field(bv, "dragging"));
        assertEquals(0f, f(bv, "dragOffX"), 0.001f);
        assertEquals(1, ses.actions());
    }

    // ---------- denetim 3 ----------

    private static View findText(View v, String prefix) {
        if (v instanceof android.widget.TextView && ((android.widget.TextView) v).getText().toString().startsWith(prefix)) return v;
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) {
            View r = findText(((ViewGroup) v).getChildAt(i), prefix);
            if (r != null) return r;
        }
        return null;
    }

    /** Bulgu: kazanınca hemen gelen başarı afişi sonradan açılan Tamamlandı penceresinin altında kalıyordu. */
    @Test public void achievementBannerStaysAboveCompleteOverlay() throws Exception {
        start();
        com.isikyolu.engine.Progress p = Game.progress();
        p.tutorialSeen = true;
        GameScreen gs = openLevel(1);
        solveThroughUi(gs, Game.levels().get(1));
        idle(1500);
        Overlay done = topOverlay();
        assertNotNull("tamamlandı penceresi", done);
        View title = findText(act.overlayLayer(), "Başarı: İlk Işık");
        assertNotNull("başarı afişi", title);
        View banner = (View) title.getParent().getParent();
        assertTrue("afiş pencerenin üstünde çizilmeli", banner.getZ() > done.getZ());
    }

    /** Bulgu: can yokken reddedilen itmede blok kayarak dönmüyor, anında geri sıçrıyordu. */
    @Test public void rejectedPushSlidesBack() throws Exception {
        start();
        com.isikyolu.engine.Progress p = Game.progress();
        p.tutorialSeen = true;
        GameScreen gs = openLevel(71);
        p.lives = 0;
        com.isikyolu.game.ui.BoardView bv = boardOf(gs);
        com.isikyolu.engine.GameSession ses = (com.isikyolu.engine.GameSession) field(gs, "session");
        float ox = f(bv, "ox"), oy = f(bv, "oy"), cell = f(bv, "cell");
        int w = ses.board().width, blk = ses.board().blockCells()[0], dir = -1;
        for (int d = 0; d < 4; d++) if (ses.board().canPush(blk, d)) { dir = d; break; }
        int[] DX = {0, 1, 0, -1}, DY = {-1, 0, 1, 0};
        float sx = ox + (blk % w + 0.5f) * cell, sy = oy + (blk / w + 0.5f) * cell;
        long t = android.os.SystemClock.uptimeMillis();
        bv.dispatchTouchEvent(android.view.MotionEvent.obtain(t, t, android.view.MotionEvent.ACTION_DOWN, sx, sy, 0));
        bv.dispatchTouchEvent(android.view.MotionEvent.obtain(t, t + 10, android.view.MotionEvent.ACTION_MOVE, sx + DX[dir] * 0.3f * cell, sy + DY[dir] * 0.3f * cell, 0));
        bv.dispatchTouchEvent(android.view.MotionEvent.obtain(t, t + 20, android.view.MotionEvent.ACTION_MOVE, sx + DX[dir] * 0.52f * cell, sy + DY[dir] * 0.52f * cell, 0));
        assertEquals("can yok: itme yapılmamalı", 0, ses.actions());
        float[] bx = (float[]) field(bv, "blockOffX"), by = (float[]) field(bv, "blockOffY");
        float off = DX[dir] != 0 ? bx[blk] * DX[dir] : by[blk] * DY[dir];
        assertTrue("blok bırakıldığı yerden geri kaymalı (" + off / cell + " hücre)", off > 0.4f * cell);
    }

    // ---------- reklamlar (1.7.0) ----------

    @org.junit.After public void resetAds() { com.isikyolu.game.ads.Ads.resetForTests(); }

    private FakeAds fakeAds() {
        FakeAds f = new FakeAds();
        com.isikyolu.game.ads.Ads.setForTests(f);
        return f;
    }

    private static void complete(com.isikyolu.engine.Progress p, int upTo) {
        for (int i = 1; i <= upTo; i++) { p.stars[i] = 3; p.bestMoves[i] = Game.levels().get(i).optimal; }
    }

    private View topButton(String prefix) {
        Overlay o = topOverlay();
        return o == null ? null : findText(o, prefix) == null ? null : (View) findText(o, prefix).getParent();
    }

    /** Banner: ilk 3 bölüm bitmeden yok; menü, bölümler ve oyun ekranında var; market ve ayarlarda yok; ekranla çakışmaz. */
    @Test public void bannerPlacement() throws Exception {
        FakeAds ads = fakeAds();
        start();
        assertEquals(1, ads.starts);
        assertEquals("ilk 3 bölüm reklamsız", 0, act.bannerHeight());
        com.isikyolu.engine.Progress p = Game.progress();
        p.tutorialSeen = true;
        complete(p, 3);
        act.goLevels();
        idle(600);
        assertTrue("bölümler ekranında banner", act.bannerHeight() > 0);
        View stage = (View) act.currentScreen().getParent();
        int[] ls = new int[2]; stage.getLocationInWindow(ls);
        View host = (View) findByDescription(act.overlayLayer(), "test-banner").getParent();
        int[] lb = new int[2]; host.getLocationInWindow(lb);
        assertTrue("ekran banner'ın üstünde biter", ls[1] + stage.getHeight() <= lb[1]);
        float dens = act.getResources().getDisplayMetrics().density;
        assertTrue("banner ince bir şerit (" + act.bannerHeight() / dens + " dp)", act.bannerHeight() < 80 * dens);
        assertTrue("ekranın büyük kısmı oyuna kalır", stage.getHeight() > act.overlayLayer().getHeight() * 0.85f);
        shot("20_reklam_banner_bolumler");
        // pencere açılınca banner gizlenir (yer tutar, karartmanın altında reklam kalmaz)
        p.lives = 0;
        act.showNoLives(0);
        idle(400);
        assertTrue("pencere açıkken banner gizli", act.bannerCovered());
        closeOverlays();
        idle(300);
        assertTrue("pencere kapanınca banner geri gelir", act.bannerHeight() > 0);
        p.lives = 4;
        GameScreen gs = openLevel(4);
        idle(400);
        assertEquals("oyun ekranında banner yok (düğmelere yakın, yanlışlıkla tıklama)", 0, act.bannerHeight());
        shot("20b_oyun_ekrani_reklamsiz");
        act.goMarket(0);
        idle(600);
        assertEquals("markette banner yok", 0, act.bannerHeight());
        act.goSettings();
        idle(600);
        assertEquals("ayarlarda banner yok", 0, act.bannerHeight());
        assertNotNull("gizlilik seçenekleri düğmesi", findText(act.currentScreen(), "Reklam Gizlilik"));
    }

    /** Geçiş (resim) reklamı: öğreticide yok, bölüm bitince ve en az 3 bölüm / 4 dk arayla, sonraki ekrana geçerken. */
    @Test public void interstitialBetweenLevelsWithFrequencyCap() throws Exception {
        FakeAds ads = fakeAds();
        start();
        com.isikyolu.engine.Progress p = Game.progress();
        p.tutorialSeen = true;
        // öğretici bölümleri arayüzden oyna: reklam yok
        for (int id = 1; id <= 10; id++) {
            GameScreen gs = openLevel(id);
            solveThroughUi(gs, Game.levels().get(id));
            waitFor("tamamlandı " + id, () -> findText(act.overlayLayer(), "Sonraki Bölüm") != null);
            topButton("Sonraki Bölüm").performClick();
            idle(600);
        }
        assertEquals("öğreticide geçiş reklamı yok", 0, ads.interstitials);
        int[] counts = new int[6];
        for (int k = 0; k < 6; k++) {
            int id = 11 + k;
            closeOverlays();
            GameScreen gs = (GameScreen) act.currentScreen();
            solveThroughUi(gs, Game.levels().get(id));
            waitFor("tamamlandı " + id, () -> findText(act.overlayLayer(), "Sonraki Bölüm") != null);
            topButton("Sonraki Bölüm").performClick();
            idle(600);
            counts[k] = ads.interstitials;
            assertTrue("reklamdan sonra sonraki bölüm açılır", act.currentScreen() instanceof GameScreen);
        }
        // 11, 12: yok; 13 (3. bölüm): var; 14–16: 4 dakika dolmadı
        assertArrayEquals(new int[]{0, 0, 1, 1, 1, 1}, counts);
        p.trustWall += 5 * 60_000L; // güvenilir saat 5 dk ilerledi
        closeOverlays();
        GameScreen gs = (GameScreen) act.currentScreen();
        solveThroughUi(gs, Game.levels().get(17));
        waitFor("tamamlandı 17", () -> findText(act.overlayLayer(), "Bölümler") != null);
        topButton("Bölümler").performClick();
        idle(600);
        assertEquals("3 bölüm ve 4 dk sonra yeniden", 2, ads.interstitials);
        assertTrue(act.currentScreen() instanceof LevelSelectScreen);
    }

    /** Ödüllü video: can bitince +1 can ve bölüm açılır; günlük sınır dolunca düğme kalkar. */
    @Test public void rewardedVideoGivesLife() throws Exception {
        FakeAds ads = fakeAds();
        start();
        com.isikyolu.engine.Progress p = Game.progress();
        p.tutorialSeen = true;
        complete(p, 11);
        for (int k = 0; k < 3; k++) {
            p.lives = 0;
            act.goLevel(12);
            idle(500);
            View v = topButton("Video izle · +1 can");
            assertNotNull("can videosu " + k, v);
            v.performClick();
            idle(600);
            assertEquals(k + 1, ads.rewardedShown);
            assertTrue(act.currentScreen() instanceof GameScreen);
            closeOverlays();
            act.goLevels();
            idle(400);
        }
        p.lives = 0;
        act.goLevel(12);
        idle(500);
        assertNull("günde 3 can videosu", topButton("Video izle · +1 can"));
        assertNotNull(topButton("+1 can al"));
    }

    /** Ödüllü video: hak bitince ek hak (kristalli devam fiyatını artırmaz); video yarıda kalırsa ödül yok. */
    @Test public void rewardedVideoGivesMoves() throws Exception {
        FakeAds ads = fakeAds();
        start();
        com.isikyolu.engine.Progress p = Game.progress();
        p.tutorialSeen = true;
        complete(p, 11);
        GameScreen gs = openLevel(12);
        com.isikyolu.engine.GameSession ses = (com.isikyolu.engine.GameSession) field(gs, "session");
        burnBudget(gs, 12);
        waitFor("hak bitti penceresi", () -> topButton("Video izle · +") != null);
        ads.completeVideo = false;
        topButton("Video izle · +").performClick();
        idle(1500); // çift tıklama koruması ve yeniden yükleme
        assertEquals("yarım video ödül vermez", 0, ses.budgetLeft());
        ads.completeVideo = true;
        topButton("Video izle · +").performClick();
        idle(400);
        assertTrue("ek hak verildi", ses.budgetLeft() >= 5);
        assertEquals("devam fiyatı artmadı", 0, ses.continues());
        int[] pm = probeMirror(12);
        assertTrue("hamle yapılabilir", gs.onRotate(pm[0], pm[1]));
    }

    /** Ödüllü video: ipucu bitince +1 ipucu ve ipucu hemen kullanılır; markette +25 kristal (günde 3). */
    @Test public void rewardedVideoGivesHintAndCrystals() throws Exception {
        fakeAds();
        start();
        com.isikyolu.engine.Progress p = Game.progress();
        p.tutorialSeen = true;
        complete(p, 11);
        p.hints = 0;
        GameScreen gs = openLevel(12);
        Method req = GameScreen.class.getDeclaredMethod("requestHint");
        req.setAccessible(true);
        req.invoke(gs);
        idle(400);
        topButton("Video izle · +1 ipucu").performClick();
        waitPlan(gs);
        idle(100);
        assertEquals("kazanılan ipucu kullanıldı", 0, p.hints);
        assertTrue("ipucu tahtada", boardOf(gs).hasHint());
        act.goMarket(0);
        idle(600);
        assertNull("ipucu videosu az önce izlendi: yenisi yüklenene kadar düğme yok", findText(act.currentScreen(), "Video izle · +25 kristal"));
        idle(1200); // yeni video yüklenince market kendini yeniler
        int kr = p.crystals;
        for (int k = 0; k < 3; k++) {
            View v = findText(act.currentScreen(), "Video izle · +25 kristal");
            assertNotNull("video düğmesi (yeniden yüklenince geri gelir) " + k, v);
            ((View) v.getParent()).performClick();
            idle(300);
            if (k < 2) assertNull("video tükendi: düğme yeniden yüklenene kadar yok", findText(act.currentScreen(), "Video izle · +25 kristal"));
            idle(1200);
        }
        assertEquals(kr + 75, p.crystals);
        assertNull("günde 3", findText(act.currentScreen(), "Video izle · +25 kristal"));
    }

    /** Tam ekran reklam açılınca oyun "Duraklatıldı" penceresi açmaz. */
    @Test public void fullscreenAdDoesNotPauseGame() throws Exception {
        FakeAds ads = fakeAds();
        org.robolectric.android.controller.ActivityController<MainActivity> ctl = Robolectric.buildActivity(MainActivity.class).setup();
        act = ctl.get();
        idle(100);
        waitFor("ana menü", () -> act.currentScreen() instanceof MenuScreen);
        Game.progress().tutorialSeen = true;
        openLevel(4);
        ads.fullscreen = true;
        ctl.pause();
        idle(200);
        assertNull("reklam sırasında duraklatma penceresi yok", topOverlay());
        ads.fullscreen = false;
        ctl.resume();
        ctl.pause();
        idle(200);
        assertNotNull("normal arka plana geçişte duraklatılır", topOverlay());
    }

    /** Video açılamazsa doğru mesaj ve ödül yok; gizlilik politikası ayarlardan okunur. */
    @Test public void failedVideoAndPrivacyPolicy() throws Exception {
        FakeAds ads = fakeAds();
        start();
        com.isikyolu.engine.Progress p = Game.progress();
        complete(p, 11);
        act.goMarket(0);
        idle(600);
        int kr = p.crystals;
        ads.failToShow = true;
        ((View) findText(act.currentScreen(), "Video izle · +25 kristal").getParent()).performClick();
        idle(300);
        assertEquals("açılamayan video ödül vermez", kr, p.crystals);
        assertNotNull("doğru uyarı", findText(act.overlayLayer(), "Video açılamadı"));
        act.goSettings();
        idle(600);
        ((View) findText(act.currentScreen(), "Gizlilik Politikası").getParent()).performClick();
        idle(400);
        assertNotNull(topOverlay());
        assertNotNull("AdMob açıklaması", findText(topOverlay(), "Işık Yolu bir bulmaca oyunudur"));
        shot("21_gizlilik");
    }

    /** Kenardan kenara çizim: içerik durum ve gezinme çubuğunun altına girmez. */
    @Test public void contentStaysInsideSystemBars() throws Exception {
        start();
        View decor = act.getWindow().getDecorView();
        android.view.WindowInsets in = new android.view.WindowInsets.Builder()
                .setInsets(android.view.WindowInsets.Type.systemBars(), android.graphics.Insets.of(0, 96, 0, 144)).build();
        decor.dispatchApplyWindowInsets(in);
        idle(100);
        assertEquals(96, act.overlayLayer().getPaddingTop());
        assertEquals(144, act.overlayLayer().getPaddingBottom());
        int[] ls = new int[2];
        View stage = (View) act.currentScreen().getParent();
        stage.getLocationInWindow(ls);
        assertTrue("ekran durum çubuğunun altında başlar", ls[1] >= 96);
        assertTrue("ekran gezinme çubuğunun üstünde biter", ls[1] + stage.getHeight() <= decor.getHeight() - 144);
    }

    /** Android 16: büyük ekranda dikey kilit yok sayılır; yatay tablette oyun açılır ve oynanır. */
    @Test @Config(qualifiers = "w1280dp-h800dp-land-xhdpi")
    public void tabletLandscapeWorks() throws Exception {
        start();
        Game.progress().tutorialSeen = true;
        GameScreen gs = openLevel(12);
        com.isikyolu.game.ui.BoardView bv = boardOf(gs);
        assertTrue("tahta ekrana sığar", bv.getHeight() > 0 && bv.getHeight() <= act.overlayLayer().getHeight());
        solveThroughUi(gs, Game.levels().get(12));
        idle(1500);
        assertNotNull("tamamlandı penceresi", findText(act.overlayLayer(), "Sonraki Bölüm"));
        shot("22_tablet_yatay");
    }

    @Test public void splashScreenRenders() throws Exception {
        act = Robolectric.buildActivity(MainActivity.class).setup().get();
        assertTrue("önce stüdyo açılışı", act.currentScreen() instanceof com.isikyolu.game.screens.StudioIntroScreen);
        idle(10);
        shot("00a_studyo_isik");      // ≈0,7 sn: ışık noktası yanıyor
        shot("00b_studyo_acilis");    // ≈1,5 sn: logo ışıkla açılıyor, kıvılcımlar
        idle(400);
        shot("00c_studyo_logo");      // ≈2,6 sn: logo tamamen açıldı, ışık süzülüyor
        shot("00d_studyo_isik_gecisi"); // ≈3,3 sn: ışık bandı ekranı geçiyor
        idle(600);
        shot("00e_studyo_parilti");   // ≈4,6 sn: logo parlıyor, kararmaya hazır
        waitFor("ana menü", () -> act.currentScreen() instanceof MenuScreen);
    }

    @Test public void studioIntroCanBeSkipped() throws Exception {
        act = Robolectric.buildActivity(MainActivity.class).setup().get();
        idle(200);
        View root = act.currentScreen();
        long t = android.os.SystemClock.uptimeMillis();
        root.dispatchTouchEvent(android.view.MotionEvent.obtain(t, t, android.view.MotionEvent.ACTION_DOWN, 100, 100, 0));
        root.dispatchTouchEvent(android.view.MotionEvent.obtain(t, t + 50, android.view.MotionEvent.ACTION_UP, 100, 100, 0));
        assertTrue("dokununca atlanır", act.currentScreen() instanceof com.isikyolu.game.screens.SplashScreen);
        waitFor("ana menü", () -> act.currentScreen() instanceof MenuScreen);
    }

    @Test public void allLevelsPlayableThroughUi() throws Exception {
        start();
        Game.progress().tutorialSeen = true;
        for (int id = 1; id <= com.isikyolu.engine.Progress.LEVEL_COUNT; id++) {
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
        assertEquals(500, Game.progress().completedCount());
        assertEquals(1500, Game.progress().totalStars());
        assertEquals("başlangıç 3 + her 10 bölümde 1", 3 + 50, Game.progress().hints);
        assertTrue(Game.progress().achievements.contains("efsane"));
        assertEquals("kazanılan denemelerde can harcanmaz", com.isikyolu.engine.Lives.MAX, Game.progress().lives);
        assertTrue("500 bölümün kazancı verilmeli", Game.progress().crystalsEarned >= com.isikyolu.engine.Economy.maxLevelIncome());
        assertTrue(Game.progress().achievements.contains("kusursuz"));
        act.goLevels();
        idle(800);
        shot("03b_bolum_secme_tamamlandi");
    }
}
