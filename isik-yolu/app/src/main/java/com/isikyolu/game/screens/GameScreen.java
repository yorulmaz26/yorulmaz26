package com.isikyolu.game.screens;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.isikyolu.engine.Achievements;
import com.isikyolu.engine.Chapters;
import com.isikyolu.engine.DailyPuzzle;
import com.isikyolu.engine.GameSession;
import com.isikyolu.engine.Level;
import com.isikyolu.engine.Move;
import com.isikyolu.engine.Progress;
import com.isikyolu.engine.StarRating;
import com.isikyolu.game.Audio;
import com.isikyolu.game.Game;
import com.isikyolu.game.GameLoader;
import com.isikyolu.game.MainActivity;
import com.isikyolu.game.ui.BoardView;
import com.isikyolu.game.ui.Icon;
import com.isikyolu.game.ui.MiniStars;
import com.isikyolu.game.ui.Overlay;
import com.isikyolu.game.ui.Palette;
import com.isikyolu.game.ui.StarsView;
import com.isikyolu.game.ui.TutorialOverlay;
import com.isikyolu.game.ui.Ui;

import java.util.List;

/** 4. Oyun ekranı (+ 5. duraklatma, 6. bölüm tamamlandı, 7. ipucu pencereleri). */
public final class GameScreen extends Screen implements BoardView.Host {
    private static final int HINT_CAP = 150_000;

    private final Level level;
    private final boolean daily;
    private final int epochDay;
    private final GameSession session;
    private final BoardView board;
    private final TextView movesText, goalText, hintBadge;
    private final MiniStars live;
    private boolean finished, thinking;
    private int lastLiveStars = 3;

    public GameScreen(MainActivity a, int levelId) { this(a, Game.levels().get(levelId), -1); }

    public GameScreen(MainActivity a, Level lvl, int epochDay) {
        super(a);
        this.level = lvl;
        this.daily = epochDay >= 0;
        this.epochDay = epochDay;
        this.session = new GameSession(lvl);
        Context c = a;
        LinearLayout root = Ui.vertical(c);

        // üst çubuk
        LinearLayout top = Ui.horizontal(c);
        top.setPadding(Ui.dp(c, 16), Ui.dp(c, 14), Ui.dp(c, 16), Ui.dp(c, 4));
        top.addView(Ui.iconButton(c, Icon.PAUSE, Ui.SECONDARY, 46, v -> showPause()));
        LinearLayout tc = Ui.vertical(c);
        tc.setPadding(Ui.dp(c, 14), 0, Ui.dp(c, 8), 0);
        String t = daily ? "Günlük Bulmaca" : "Bölüm " + lvl.id;
        String sub = daily ? DailyScreen.prettyDate(epochDay) : Chapters.NAMES[lvl.chapter - 1] + " · " + Chapters.TOPICS[lvl.chapter - 1];
        tc.addView(Ui.text(c, t, 21, Palette.WHITE, Ui.BOLD));
        TextView st = Ui.text(c, sub, 13, Palette.TEXT_DIM, Ui.REGULAR);
        st.setSingleLine(true);
        st.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tc.addView(st);
        top.addView(tc, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout movesBox = Ui.vertical(c);
        movesBox.setGravity(Gravity.CENTER);
        movesBox.setBackground(Ui.round(0xFF1B2741, Ui.dp(c, 14)));
        movesBox.setPadding(Ui.dp(c, 12), Ui.dp(c, 6), Ui.dp(c, 12), Ui.dp(c, 6));
        movesText = Ui.text(c, "0", 20, Palette.WHITE, Ui.BOLD);
        movesText.setGravity(Gravity.CENTER);
        movesBox.addView(movesText);
        TextView ml = Ui.text(c, "HAMLE", 10, Palette.TEXT_DIM, Ui.BOLD);
        ml.setLetterSpacing(0.1f);
        movesBox.addView(ml);
        top.addView(movesBox);
        root.addView(top);

        // hedef ve canlı yıldız göstergesi
        LinearLayout goal = Ui.horizontal(c);
        goal.setGravity(Gravity.CENTER);
        goal.setPadding(0, Ui.dp(c, 6), 0, 0);
        live = new MiniStars(c, 3);
        goal.addView(live, new LinearLayout.LayoutParams(Ui.dp(c, 64), Ui.dp(c, 18)));
        goalText = Ui.text(c, "", 13, Palette.TEXT_DIM, Ui.MEDIUM);
        goalText.setPadding(Ui.dp(c, 8), 0, 0, 0);
        goal.addView(goalText);
        root.addView(goal);

        board = new BoardView(c);
        board.setHost(this);
        board.bind(session);
        root.addView(board, new LinearLayout.LayoutParams(-1, 0, 1f));

        // alt çubuk
        LinearLayout bottom = Ui.horizontal(c);
        bottom.setGravity(Gravity.CENTER);
        bottom.setPadding(Ui.dp(c, 16), Ui.dp(c, 4), Ui.dp(c, 16), Ui.dp(c, 18));
        bottom.addView(action(c, Icon.UNDO, "Geri Al", Ui.SECONDARY, v -> undo()), weightLp(c));
        bottom.addView(action(c, Icon.RESET, "Sıfırla", Ui.SECONDARY, v -> reset()), weightLp(c));
        FrameLayout hint = action(c, Icon.BULB, "İpucu", Ui.GOLD, v -> requestHint());
        hintBadge = Ui.text(c, "", 12, Palette.WHITE, Ui.BOLD);
        hintBadge.setGravity(Gravity.CENTER);
        hintBadge.setBackground(Ui.round(Palette.PURPLE, Ui.dp(c, 11)));
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(Ui.dp(c, 22), Ui.dp(c, 22), Gravity.TOP | Gravity.END);
        bl.setMargins(0, Ui.dp(c, 4), Ui.dp(c, 10), 0);
        hint.addView(hintBadge, bl);
        bottom.addView(hint, weightLp(c));
        root.addView(bottom);
        addView(root, new LayoutParams(-1, -1));
        updateHud();
    }

    private static LinearLayout.LayoutParams weightLp(Context c) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(c, 72), 1f);
        lp.setMargins(Ui.dp(c, 5), 0, Ui.dp(c, 5), 0);
        return lp;
    }

    private FrameLayout action(Context c, int icon, String label, int style, View.OnClickListener l) {
        FrameLayout f = new FrameLayout(c);
        LinearLayout b = Ui.button(c, label, style, -1, l);
        b.setOrientation(LinearLayout.VERTICAL);
        b.removeAllViews();
        b.setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 8));
        b.setGravity(Gravity.CENTER);
        b.addView(Ui.icon(c, icon, Ui.textColorFor(style), 24));
        TextView t = Ui.text(c, label, 13, Ui.textColorFor(style), Ui.BOLD);
        t.setPadding(0, Ui.dp(c, 4), 0, 0);
        t.setGravity(Gravity.CENTER);
        b.addView(t, new LinearLayout.LayoutParams(-2, -2));
        f.addView(b, new FrameLayout.LayoutParams(-1, -1));
        return f;
    }

    // ---------- yaşam döngüsü ----------

    @Override public void onShow() {
        if (!daily) Game.progress().lastPlayed = level.id;
        Progress p = Game.progress();
        if (daily) return;
        if (level.id == 1 && !p.tutorialSeen) {
            board.setInputEnabled(false);
            TutorialOverlay.first(act).onDone(() -> {
                p.tutorialSeen = true;
                Game.save();
                board.setInputEnabled(true);
                pointFirstMove();
            }).show(act.overlayLayer());
        } else if (level.id == 1 && p.stars[1] == 0) {
            pointFirstMove();
        } else if (level.id % 10 == 1 && p.stars[level.id] == 0) {
            TutorialOverlay.chapter(act, level.chapter).show(act.overlayLayer());
        }
    }

    /** İlk bölümde ücretsiz yönlendirme. */
    private void pointFirstMove() {
        Move m = session.hint(HINT_CAP);
        if (m != null) {
            board.showHint(m);
            act.toast("Parlayan aynaya dokun!");
        }
    }

    @Override public boolean onBack() {
        if (!finished) showPause();
        else act.goLevels();
        return true;
    }

    @Override public void onAppPause() {
        if (!finished && !act.hasOverlay()) showPause();
    }

    // ---------- hamleler ----------

    @Override public boolean onRotate(int x, int y) {
        if (finished || thinking) return false;
        if (!session.tap(x, y)) return false;
        Game.audio().play(Audio.ROTATE, 0.9f + (float) Math.random() * 0.25f);
        Game.haptics().move();
        board.onMove(session.lastMove(), false);
        afterMove();
        return true;
    }

    @Override public boolean onPush(int x, int y, int dir) {
        if (finished || thinking) return false;
        if (!session.drag(x, y, dir)) return false;
        Game.audio().play(Audio.PUSH);
        Game.haptics().move();
        board.onMove(session.lastMove(), false);
        afterMove();
        return true;
    }

    @Override public void onBlockTapped() { act.toast("Altın bloğu sürükleyerek kaydır"); }

    private void undo() {
        if (finished || thinking) return;
        Move m = session.lastMove();
        if (m == null || !session.undo()) { act.toast("Geri alınacak hamle yok"); return; }
        Game.audio().play(Audio.UNDO);
        Game.progress().undoCount++;
        board.onMove(m, true);
        updateHud();
        checkAchievements();
    }

    private void reset() {
        if (finished || thinking) return;
        if (session.moves() == 0) { act.toast("Bölüm zaten başlangıç durumunda"); return; }
        session.reset();
        Game.audio().play(Audio.UNDO, 0.8f);
        board.onMove(null, false);
        updateHud();
    }

    private void afterMove() {
        updateHud();
        if (session.isSolved()) win();
    }

    private void updateHud() {
        int m = session.moves();
        movesText.setText(String.valueOf(m));
        int stars = StarRating.stars(Math.max(m, 0), level.optimal);
        live.setStars(stars);
        if (stars < lastLiveStars) {
            live.animate().scaleX(1.25f).scaleY(1.25f).setDuration(120).withEndAction(() -> live.animate().scaleX(1f).scaleY(1f).setDuration(160).start()).start();
        }
        lastLiveStars = stars;
        if (m <= StarRating.threeStarLimit(level.optimal))
            goalText.setText("3 yıldız: en fazla " + level.optimal + " hamle");
        else if (m <= StarRating.twoStarLimit(level.optimal))
            goalText.setText("2 yıldız: en fazla " + StarRating.twoStarLimit(level.optimal) + " hamle");
        else goalText.setText("En az hamle: " + level.optimal + " · Sıfırlayıp yeniden dene");
        hintBadge.setText(String.valueOf(Game.progress().hints));
    }

    // ---------- ipucu ----------

    private void requestHint() {
        if (finished || thinking) return;
        Progress p = Game.progress();
        if (board.hasHint()) { act.toast("İpucu tahtada parlıyor"); return; }
        Overlay o = new Overlay(act);
        Context c = act;
        FrameLayout ic = new FrameLayout(c);
        ic.setBackground(Ui.round(0x33F5C542, Ui.dp(c, 34)));
        ic.addView(Ui.icon(c, Icon.BULB, Palette.GOLD, 38), new FrameLayout.LayoutParams(Ui.dp(c, 38), Ui.dp(c, 38), Gravity.CENTER));
        o.cardView().addView(ic, new LinearLayout.LayoutParams(Ui.dp(c, 68), Ui.dp(c, 68)));
        o.title("İpucu");
        if (p.hints > 0) {
            o.body("Bir sonraki doğru hamle tahtada altın halkayla gösterilir.\nKalan ipucu: " + p.hints);
            o.button("İpucu Göster", Ui.GOLD, Icon.BULB, v -> { o.dismiss(); useHint(); });
            o.button("Vazgeç", Ui.SECONDARY, -1, v -> o.dismiss());
        } else {
            o.body("İpucun kalmadı.\nBir bölümü ilk denemede 3 yıldızla bitirdiğinde +1 ipucu kazanırsın.");
            o.button("Tamam", Ui.PRIMARY, -1, v -> o.dismiss());
        }
        o.show(act.overlayLayer());
    }

    private void useHint() {
        Progress p = Game.progress();
        if (!p.useHint()) return;
        session.markHintUsed();
        Game.save();
        updateHud();
        thinking = true;
        board.setInputEnabled(false);
        act.toast("Işık yolu hesaplanıyor…");
        final GameSession s = session;
        GameLoader.background(() -> s.hint(HINT_CAP), m -> {
            thinking = false;
            board.setInputEnabled(!finished);
            if (m == null) { act.toast("İpucu bulunamadı. Sıfırlayıp tekrar dene."); p.hints++; updateHud(); return; }
            Game.audio().play(Audio.HINT);
            board.showHint(m);
            checkAchievements();
        });
    }

    // ---------- tamamlanma ----------

    private void win() {
        finished = true;
        board.setInputEnabled(false);
        board.celebrate();
        Game.audio().play(Audio.WIN);
        Game.haptics().success();
        Progress p = Game.progress();
        int stars = session.stars();
        boolean first = false;
        int hintsBefore = p.hints;
        if (daily) {
            p.dailyDone.add(DailyPuzzle.keyFor(epochDay));
        } else {
            first = p.recordLevel(level.id, stars, session.moves(), session.hintsUsed() > 0);
        }
        boolean rewarded = p.hints > hintsBefore;
        Game.save();
        final boolean firstF = first;
        postDelayed(() -> {
            showComplete(stars, firstF, rewarded);
            checkAchievements();
        }, 1100);
    }

    private void checkAchievements() {
        List<Achievements.Def> fresh = Achievements.update(Game.progress());
        if (fresh.isEmpty()) return;
        Game.save();
        long delay = 300;
        for (Achievements.Def d : fresh) {
            postDelayed(() -> {
                Game.audio().play(Audio.ACHIEVE);
                act.banner(Icon.TROPHY, "Başarı: " + d.title, d.description);
            }, delay);
            delay += 3200;
        }
    }

    private void showComplete(int stars, boolean first, boolean rewarded) {
        Overlay o = new Overlay(act).setCancelable(false);
        Context c = act;
        String[] titles = {"", "Tamamlandı!", "Harika!", "Kusursuz!"};
        TextView tag = Ui.text(c, daily ? "GÜNLÜK BULMACA ÇÖZÜLDÜ" : "BÖLÜM " + level.id + " TAMAMLANDI", 13, Palette.TURQUOISE, Ui.BOLD);
        tag.setLetterSpacing(0.08f);
        o.cardView().addView(tag);
        o.title(titles[stars]);
        StarsView sv = new StarsView(c);
        o.cardView().addView(sv, Ui.lpMargins(c, -1, Ui.dp(c, 78), 0, 6, 0, 8));
        sv.setStars(stars, true);
        StringBuilder b = new StringBuilder();
        b.append("Hamle: ").append(session.moves()).append("   ·   En az: ").append(level.optimal);
        if (!daily && Game.progress().bestMoves[level.id] > 0)
            b.append("\nEn iyi sonucun: ").append(Game.progress().bestMoves[level.id]).append(" hamle");
        if (stars < 3) b.append("\n3 yıldız için ").append(level.optimal).append(" hamlede bitir.");
        if (daily) b.append("\nSeri: ").append(Game.progress().dailyStreak(DailyPuzzle.today())).append(" gün");
        o.body(b.toString());
        if (rewarded) {
            TextView r = Ui.text(c, "+1 ipucu kazandın!", 15, Palette.GOLD, Ui.BOLD);
            o.cardView().addView(r, Ui.lpMargins(c, -2, -2, 0, 4, 0, 4));
        }
        if (daily) {
            o.button("Ana Menü", Ui.PRIMARY, Icon.HOME, v -> { o.dismiss(); act.goMenu(); });
            o.button("Tekrar Oyna", Ui.SECONDARY, Icon.RESET, v -> { o.dismiss(); act.goDailyGame(level, epochDay); });
        } else if (level.id < 100) {
            o.button("Sonraki Bölüm", Ui.PRIMARY, Icon.NEXT, v -> { o.dismiss(); act.goLevel(level.id + 1); });
            o.button("Tekrar Oyna", Ui.SECONDARY, Icon.RESET, v -> { o.dismiss(); act.goLevel(level.id); });
            o.button("Bölümler", Ui.SECONDARY, Icon.GRID, v -> { o.dismiss(); act.goLevels(); });
        } else {
            o.body("Tebrikler! 100 bölümün hepsini bitirdin. Sen gerçek bir Işık Ustasısın!");
            o.button("Bölümler", Ui.PRIMARY, Icon.GRID, v -> { o.dismiss(); act.goLevels(); });
            o.button("Tekrar Oyna", Ui.SECONDARY, Icon.RESET, v -> { o.dismiss(); act.goLevel(level.id); });
        }
        o.show(act.overlayLayer());
    }

    // ---------- duraklatma ----------

    private void showPause() {
        if (act.hasOverlay()) return;
        Overlay o = new Overlay(act);
        o.title("Duraklatıldı");
        o.body((daily ? "Günlük Bulmaca" : "Bölüm " + level.id + " · " + level.name) + "\nHamle: " + session.moves());
        o.button("Devam Et", Ui.PRIMARY, Icon.PLAY, v -> o.dismiss());
        o.button("Yeniden Başla", Ui.SECONDARY, Icon.RESET, v -> { o.dismiss(); reset(); });
        if (daily) o.button("Günlük Bulmaca", Ui.SECONDARY, Icon.CALENDAR, v -> { o.dismiss(); act.goDaily(); });
        else o.button("Bölüm Seç", Ui.SECONDARY, Icon.GRID, v -> { o.dismiss(); act.goLevels(); });
        o.button("Ana Menü", Ui.SECONDARY, Icon.HOME, v -> { o.dismiss(); act.goMenu(); });
        LinearLayout row = Ui.horizontal(act);
        row.setGravity(Gravity.CENTER);
        row.addView(toggle(Icon.SOUND, "Ses", () -> Game.progress().sound, () -> { Game.progress().sound = !Game.progress().sound; }));
        row.addView(toggle(Icon.MUSIC, "Müzik", () -> Game.progress().music, () -> { Game.progress().music = !Game.progress().music; Game.audio().updateMusic(); }));
        row.addView(toggle(Icon.VIBRATE, "Titreşim", () -> Game.progress().vibration, () -> { Game.progress().vibration = !Game.progress().vibration; }));
        o.cardView().addView(row, Ui.lpMargins(act, -1, -2, 0, 14, 0, 0));
        o.show(act.overlayLayer());
    }

    private interface Getter { boolean get(); }

    private View toggle(int icon, String label, Getter g, Runnable flip) {
        Context c = act;
        LinearLayout col = Ui.vertical(c);
        col.setGravity(Gravity.CENTER);
        FrameLayout btn = Ui.iconButton(c, icon, g.get() ? Ui.PRIMARY : Ui.SECONDARY, 52, null);
        col.addView(btn);
        TextView t = Ui.text(c, label + (g.get() ? " açık" : " kapalı"), 12, Palette.TEXT_DIM, Ui.MEDIUM);
        t.setPadding(0, Ui.dp(c, 4), 0, 0);
        col.addView(t);
        Ui.clickable(btn, v -> {
            flip.run();
            Game.save();
            boolean on = g.get();
            android.widget.ImageView iv = (android.widget.ImageView) btn.getChildAt(0);
            ((Icon) iv.getDrawable()).setColor(Palette.WHITE);
            btn.setAlpha(on ? 1f : 0.55f);
            t.setText(label + (on ? " açık" : " kapalı"));
        });
        btn.setAlpha(g.get() ? 1f : 0.55f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        col.setLayoutParams(lp);
        return col;
    }
}
