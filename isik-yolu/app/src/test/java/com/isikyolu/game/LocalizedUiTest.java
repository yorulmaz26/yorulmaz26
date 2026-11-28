package com.isikyolu.game;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.robolectric.Shadows.shadowOf;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.text.Layout;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.isikyolu.engine.Board;
import com.isikyolu.engine.Level;
import com.isikyolu.engine.Move;
import com.isikyolu.engine.Progress;
import com.isikyolu.engine.Solver;
import com.isikyolu.game.i18n.L;
import com.isikyolu.game.screens.GameScreen;
import com.isikyolu.game.screens.MenuScreen;
import com.isikyolu.game.ui.Overlay;

import org.junit.After;
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
import java.util.ArrayList;
import java.util.List;

/**
 * Her dilde bütün ana ekranlar ve pencereler küçük bir telefonda (360×640 dp) açılır; kesilen ("…") ya da kutusuna
 * sığmayan metin aranır. -Pi18nDir=… verilirse ekranların görüntüsü dil klasörlerine kaydedilir (gözle denetim için).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w360dp-h640dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class LocalizedUiTest {
    private MainActivity act;
    private final List<String> problems = new ArrayList<>();
    private File shots;
    private String lang;

    @After public void reset() { L.override = null; }

    private void idle(long ms) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms)); }

    private void settle() { for (int i = 0; i < 30; i++) idle(33); }

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

    /** Görünen her metin kutusunu dolaşır: kesik satır, tek satıra sığmama ya da kutudan taşan yükseklik. */
    private void scan(View v, String where) {
        if (v.getVisibility() != View.VISIBLE || v.getAlpha() == 0f) return;
        if (v instanceof TextView) {
            TextView t = (TextView) v;
            Layout l = t.getLayout();
            CharSequence s = t.getText();
            if (l != null && s.length() > 0 && t.getWidth() > 0) {
                for (int i = 0; i < l.getLineCount(); i++)
                    if (l.getEllipsisCount(i) > 0) { problems.add(lang + " · " + where + ": kesildi → \"" + s + "\""); break; }
                int avail = t.getWidth() - t.getTotalPaddingLeft() - t.getTotalPaddingRight();
                for (int i = 0; i < l.getLineCount(); i++)
                    if (l.getLineMax(i) > avail + 2) { // getLineMax: sondaki boşluk sayılmaz
                        problems.add(lang + " · " + where + ": satır sığmıyor (" + (int) l.getLineMax(i) + " > " + avail + " px, satır " + (i + 1) + "/"
                                + l.getLineCount() + ", katman genişliği " + l.getWidth() + ") → \"" + s + "\"");
                        break;
                    }
                int availH = t.getHeight() - t.getTotalPaddingTop() - t.getTotalPaddingBottom();
                if (l.getHeight() > availH + 4 && t.getMaxLines() > 0 && l.getLineCount() <= t.getMaxLines())
                    problems.add(lang + " · " + where + ": yükseklik taşıyor → \"" + s + "\"");
            }
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) scan(g.getChildAt(i), where);
        }
    }

    private void check(String where) throws Exception {
        settle();
        View root = act.getWindow().getDecorView();
        scan(root, where);
        if (shots != null) {
            Bitmap b = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
            root.draw(new Canvas(b));
            try (FileOutputStream o = new FileOutputStream(new File(shots, where + ".png"))) { b.compress(Bitmap.CompressFormat.PNG, 100, o); }
        }
    }

    private GameScreen open(int id) {
        act.goLevel(id);
        idle(500);
        closeOverlays();
        return (GameScreen) act.currentScreen();
    }

    private void visitAll(String code) throws Exception {
        lang = code;
        L.override = code;
        String dir = System.getProperty("i18n.dir");
        shots = dir == null || dir.isEmpty() ? null : new File(dir, code);
        if (shots != null) shots.mkdirs();
        act = Robolectric.buildActivity(MainActivity.class).setup().get();
        idle(100);
        for (int i = 0; i < 700 && !(act.currentScreen() instanceof MenuScreen); i++) { Thread.sleep(10); idle(30); }
        assertTrue(act.currentScreen() instanceof MenuScreen);
        Progress p = Game.progress();
        // ilk oyun: öğretici
        act.goLevel(1);
        idle(600);
        check("01_ogretici");
        closeOverlays();
        p.tutorialSeen = true;
        for (int i = 1; i <= 120; i++) { p.stars[i] = i % 5 == 0 ? 2 : 3; p.bestMoves[i] = Game.levels().get(i).optimal; }
        p.lastPlayed = 121; p.crystals = 12480; p.gems = 14; p.hints = 9; p.lives = 3;
        com.isikyolu.engine.Achievements.update(p);
        Game.save();
        act.goMenu(); idle(600); check("02_menu");
        act.goLevels(); idle(600); check("03_bolumler");
        GameScreen gs = open(52); check("04_oyun");
        // duraklatma
        act.handleBack(); idle(500); check("05_duraklat"); closeOverlays();
        // kazanma penceresi
        Level l = Game.levels().get(52);
        for (Move m : Solver.solve(new Board(l), 500_000).moves) {
            int x = m.cell % l.width, y = m.cell / l.width;
            assertTrue(m.kind == Move.ROTATE ? gs.onRotate(x, y) : gs.onPush(x, y, m.dir));
            idle(40);
        }
        idle(1600); check("06_tamamlandi"); closeOverlays();
        // hamle hakkı bitti
        gs = open(12);
        Level l12 = Game.levels().get(12);
        java.util.Set<Integer> sol = new java.util.HashSet<>();
        for (Move mv : Solver.solve(new Board(l12), 100000).moves) sol.add(mv.cell);
        int mx = -1, my = -1;
        for (com.isikyolu.engine.Piece pc : l12.pieces)
            if (pc.type == com.isikyolu.engine.Cell.MIRROR && pc.rotatable && !sol.contains(pc.y * l12.width + pc.x)) { mx = pc.x; my = pc.y; }
        for (int i = 0; i < com.isikyolu.engine.Lives.moveBudget(l12.optimal) && mx >= 0; i++) { gs.onRotate(mx, my); idle(20); }
        idle(800); check("07_hak_bitti"); closeOverlays();
        act.goLevels(); idle(500);
        // can kalmadı
        p.lives = 0; p.attemptLevel = 0;
        act.goLevel(30); idle(600); check("08_can_kalmadi"); closeOverlays();
        p.lives = 3;
        act.goDaily(); idle(600); check("09_gunluk");
        act.goAchievements(); idle(600); check("10_basarilar");
        for (int tab = 0; tab < 4; tab++) { act.goMarket(tab); idle(700); check("11_market_" + tab); }
        act.goSettings(); idle(600); check("12_ayarlar");
        // dil listesi
        View langRow = findText(act.currentScreen(), L.t("settings.language"));
        if (langRow != null) { ((View) langRow.getParent()).performClick(); idle(500); check("13_dil_secimi"); closeOverlays(); }
        act.finish();
        idle(300);
    }

    private static View findText(View v, String s) {
        if (v instanceof TextView && ((TextView) v).getText().toString().equals(s)) return v;
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) {
            View r = findText(((ViewGroup) v).getChildAt(i), s);
            if (r != null) return r;
        }
        return null;
    }

    private void run(String code) throws Exception {
        visitAll(code);
        if (!problems.isEmpty()) fail(problems.size() + " taşma:\n" + String.join("\n", problems));
    }

    @Test public void turkish() throws Exception { run("tr"); }
    @Test public void english() throws Exception { run("en"); }
    @Test public void german() throws Exception { run("de"); }
    @Test public void italian() throws Exception { run("it"); }
    @Test public void french() throws Exception { run("fr"); }
    @Test public void spanish() throws Exception { run("es"); }
}
