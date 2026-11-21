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
import com.isikyolu.engine.Economy;
import com.isikyolu.engine.GameSession;
import com.isikyolu.engine.Level;
import com.isikyolu.engine.Lives;
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
    private final TextView movesText, movesLabel, goalText, hintBadge;
    private FrameLayout undoButton;
    private final MiniStars live;
    private boolean finished, thinking, won;
    /** Ertelenmiş pencere (tamamlandı / hak bitti): ekrandan çıkılınca iptal edilir. */
    private Runnable pendingOverlay;
    /** Akıllı ipucu "önce N kez Geri Al" dedi: kalan geri alma sayısı. */
    private int pendingUndoHint;
    private int lastLiveStars = 3;

    public GameScreen(MainActivity a, int levelId) { this(a, Game.levels().get(levelId), -1); }

    public GameScreen(MainActivity a, Level lvl, int epochDay) {
        super(a);
        this.level = lvl;
        this.daily = epochDay >= 0;
        this.epochDay = epochDay;
        this.session = new GameSession(lvl, !daily);
        Context c = a;
        LinearLayout root = Ui.vertical(c);

        // üst çubuk
        LinearLayout top = Ui.horizontal(c);
        top.setPadding(Ui.dp(c, 16), Ui.dp(c, 14), Ui.dp(c, 16), Ui.dp(c, 4));
        top.addView(Ui.iconButton(c, Icon.PAUSE, Ui.SECONDARY, 46, v -> showPause()));
        LinearLayout tc = Ui.vertical(c);
        tc.setPadding(Ui.dp(c, 14), 0, Ui.dp(c, 8), 0);
        String t = daily ? "Günlük Bulmaca" : "Bölüm " + lvl.id;
        String sub = daily ? DailyScreen.prettyDate(epochDay) : Chapters.NAMES[lvl.chapter - 1] + " · " + Chapters.topic(lvl.chapter);
        LinearLayout titleRow = Ui.horizontal(c);
        titleRow.addView(Ui.text(c, t, 21, Palette.WHITE, Ui.BOLD));
        if (!daily && Chapters.requiresLogic(lvl.id)) {
            TextView tag = Ui.text(c, "MANTIK", 10, Palette.WHITE, Ui.BOLD);
            tag.setLetterSpacing(0.08f);
            tag.setBackground(Ui.round(Palette.PURPLE, Ui.dp(c, 8)));
            tag.setPadding(Ui.dp(c, 7), Ui.dp(c, 2), Ui.dp(c, 7), Ui.dp(c, 2));
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
            tl.leftMargin = Ui.dp(c, 8);
            titleRow.addView(tag, tl);
        }
        tc.addView(titleRow);
        TextView st = Ui.text(c, sub, 13, Palette.TEXT_DIM, Ui.REGULAR);
        st.setSingleLine(true);
        st.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tc.addView(st);
        top.addView(tc, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout movesBox = Ui.vertical(c);
        movesBox.setGravity(Gravity.CENTER);
        movesBox.setBackground(Ui.round(0xFF35466D, Ui.dp(c, 14)));
        movesBox.setPadding(Ui.dp(c, 12), Ui.dp(c, 6), Ui.dp(c, 12), Ui.dp(c, 6));
        movesText = Ui.text(c, "0", 20, Palette.WHITE, Ui.BOLD);
        movesText.setGravity(Gravity.CENTER);
        movesBox.addView(movesText);
        movesLabel = Ui.text(c, daily ? "HAMLE" : "HAK", 10, Palette.TEXT_DIM, Ui.BOLD);
        movesLabel.setLetterSpacing(0.1f);
        movesBox.addView(movesLabel);
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
        if (!daily) {
            LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(-2, -2);
            ll.leftMargin = Ui.dp(c, 10);
            goal.addView(new com.isikyolu.game.ui.LivesBadge(c), ll);
        }
        root.addView(goal);

        board = new BoardView(c);
        board.setHost(this);
        board.bind(session);
        root.addView(board, new LinearLayout.LayoutParams(-1, 0, 1f));

        // alt çubuk
        LinearLayout bottom = Ui.horizontal(c);
        bottom.setGravity(Gravity.CENTER);
        bottom.setPadding(Ui.dp(c, 16), Ui.dp(c, 4), Ui.dp(c, 16), Ui.dp(c, 18));
        undoButton = action(c, Icon.UNDO, "Geri Al", Ui.SECONDARY, v -> undo());
        bottom.addView(undoButton, weightLp(c));
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
        } else if (level.id == 15 && p.stars[15] == 0) {
            act.banner(Icon.HELP, "İlk mantık bölümü!", "Bariz hamle seni çıkmaza sokabilir. Önce düşün, sonra dokun.");
        } else if (level.id <= 100 && level.id % 10 == 1 && p.stars[level.id] == 0) {
            TutorialOverlay.chapter(act, level.chapter).show(act.overlayLayer());
        } else if (level.id > 100 && level.id % 100 == 1 && p.stars[level.id] == 0) {
            TutorialOverlay.world(act, Chapters.worldOf(level.id)).show(act.overlayLayer());
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

    /** Yeni deneme için can var mı? Yoksa hamle uygulanmaz ve tek bir "can kalmadı" penceresi açılır. */
    private boolean canStart() {
        if (daily || attemptActive()) return true;
        Game.refreshLives();
        if (Game.progress().lives > 0) return true;
        board.cancelTouch();
        act.showNoLives(level.id);
        return false;
    }

    @Override public boolean onRotate(int x, int y) {
        if (finished || thinking) return false;
        if (!canStart()) return false;
        if (!session.tap(x, y)) { if (session.isOutOfMoves()) outOfMoves(); return false; }
        if (!beginAttempt()) return false;
        Game.audio().play(Audio.ROTATE, 0.9f + (float) Math.random() * 0.25f);
        Game.haptics().move();
        board.onMove(session.lastMove(), false);
        afterMove();
        return true;
    }

    @Override public boolean onPush(int x, int y, int dir) {
        if (finished || thinking) return false;
        if (!canStart()) return false;
        if (!session.drag(x, y, dir)) return false;
        if (!beginAttempt()) return false;
        Game.audio().play(Audio.PUSH);
        Game.haptics().move();
        board.onMove(session.lastMove(), false);
        afterMove();
        return true;
    }

    /** Denemenin ilk hamlesinde 1 can düşer (kazanınca geri gelir). */
    private boolean beginAttempt() {
        if (daily) return true;
        Progress p = Game.progress();
        if (p.attemptLevel == level.id) return true;
        if (!Lives.startAttempt(p, Game.clock(), level.id)) {
            session.reset();
            board.onMove(null, false);
            board.cancelTouch();
            updateHud();
            act.showNoLives(level.id);
            return false;
        }
        Game.save();
        return true;
    }

    private boolean attemptActive() { return !daily && Game.progress().attemptLevel == level.id; }

    @Override public void onHide() {
        if (pendingOverlay != null) { removeCallbacks(pendingOverlay); pendingOverlay = null; }
        // bölümden kazanmadan çıkıldı (hak bitmiş olsa bile): denemenin canı geri gelmez
        if (attemptActive() && !won) giveUpAttempt();
    }

    private boolean onScreen() { return act.currentScreen() == this; }

    @Override public void onBlockTapped() { act.toast("Altın bloğu bir kare sürükleyerek kaydır"); }

    private long lastFixedToast;

    @Override public void onFixedMirrorTapped() {
        long now = android.os.SystemClock.uptimeMillis();
        if (now - lastFixedToast > 2500) { lastFixedToast = now; act.toast("Cıvatalı koyu aynalar sabittir, dönmez"); }
    }

    private void undo() {
        if (finished || thinking) return;
        Move m = session.lastMove();
        if (m == null || !session.undo()) { act.toast("Geri alınacak hamle yok"); return; }
        Game.audio().play(Audio.UNDO);
        Game.progress().undoCount++;
        board.onMove(m, true);
        updateHud();
        checkAchievements();
        if (pendingUndoHint > 0 && --pendingUndoHint == 0) showPlanMove(false); // geri almalar bitti: sıradaki hamle ücretsiz gösterilir
    }

    private void reset() {
        if (finished || thinking) return;
        if (session.actions() == 0) { act.toast("Bölüm zaten başlangıç durumunda"); return; }
        if (attemptActive()) {
            Overlay o = new Overlay(act);
            o.title("Yeniden başla?");
            Progress pp = Game.progress();
            o.body((pp.shields > 0 ? "Bu deneme bırakılır; Can Kalkanın (" + pp.shields + ") canını korur ve 1 kalkan harcanır."
                    : "Bu denemenin canı geri gelmez.") + "\nKalan can: " + pp.lives
                    + (pp.lives == 0 && pp.shields == 0 ? "\nYeni deneme için can yenilenmesini beklemen gerekecek." : ""));
            o.button("Yeniden Başla", Ui.GOLD, Icon.RESET, v -> { o.dismiss(); giveUpAttempt(); doReset(); });
            o.button("Vazgeç", Ui.SECONDARY, -1, v -> o.dismiss());
            o.show(act.overlayLayer());
            return;
        }
        doReset();
    }

    private void doReset() {
        pendingUndoHint = 0;
        session.reset();
        Game.audio().play(Audio.UNDO, 0.8f);
        board.onMove(null, false);
        updateHud();
    }

    private void afterMove() {
        updateHud();
        if (session.isSolved()) win();
        else if (session.isOutOfMoves()) outOfMoves();
    }

    /** Deneme bırakıldı/kaybedildi: can geri gelmez (Can Kalkanı varsa korur). */
    private void giveUpAttempt() {
        if (!attemptActive()) return;
        if (Economy.loseAttempt(Game.progress(), Game.clock())) act.toast("Can Kalkanı canını korudu!");
        Game.save();
    }

    /** Hamle hakkı bitti: bilet veya kristalle +5 hak ile devam edilebilir; yoksa can gider. */
    private void outOfMoves() {
        if (finished) return;
        finished = true;
        board.setInputEnabled(false);
        Game.save();
        Game.audio().play(Audio.LOCKED, 0.8f);
        Game.haptics().error();
        final GameSession s = session;
        GameLoader.background(() -> s.plan(HINT_CAP), plan -> {
            pendingOverlay = () -> { pendingOverlay = null; if (onScreen()) showOutOfMoves(plan); };
            postDelayed(pendingOverlay, 300);
        });
    }

    private void showOutOfMoves(GameSession.Plan plan) {
        final int extra = Economy.continueMoves(plan == null || plan.remaining < 0 ? Economy.CONTINUE_MOVES : plan.remaining);
        Overlay o = new Overlay(act).setCancelable(false);
        Context c = act;
        FrameLayout ic = new FrameLayout(c);
        ic.setBackground(Ui.round(0x33FF5C7A, Ui.dp(c, 34)));
        ic.addView(Ui.icon(c, Icon.HEART, 0xFFFF5C7A, 38), new FrameLayout.LayoutParams(Ui.dp(c, 38), Ui.dp(c, 38), Gravity.CENTER));
        o.cardView().addView(ic, new LinearLayout.LayoutParams(Ui.dp(c, 68), Ui.dp(c, 68)));
        o.title("Hamle hakkın bitti");
        Progress p = Game.progress();
        int price = Economy.continuePrice(session.continues());
        o.body("Devam etmezsen bu denemenin canı gider.\nKalan can: " + p.lives + "/" + Lives.MAX
                + (p.shields > 0 ? "\nCan Kalkanı: " + p.shields + " (canını korur)" : ""));
        String contLabel = p.tickets > 0 ? "Bilet kullan · +" + extra + " hak (" + p.tickets + ")"
                : "+" + extra + " hak · " + price + " kristal";
        o.button(contLabel, Ui.GOLD, p.tickets > 0 ? Icon.TICKET : Icon.CRYSTAL, v -> {
            if (!Economy.payContinue(Game.progress(), session.continues())) {
                Game.audio().play(Audio.LOCKED);
                act.toast("Yetersiz kristal (" + price + " gerekli, sende " + Game.progress().crystals + ")");
                return;
            }
            o.dismiss();
            session.addBudget(extra);
            finished = false;
            board.setInputEnabled(true);
            Game.save();
            Game.audio().play(Audio.HINT);
            updateHud();
            act.checkAchievements();
        });
        o.button("Tekrar Dene", Ui.PRIMARY, Icon.RESET, v -> { o.dismiss(); giveUpAttempt(); act.goLevel(level.id); }); // can yoksa goLevel bölümlere döner
        o.button("Bölümler", Ui.SECONDARY, Icon.GRID, v -> { o.dismiss(); giveUpAttempt(); act.goLevels(); });
        o.button("Ana Menü", Ui.SECONDARY, Icon.HOME, v -> { o.dismiss(); giveUpAttempt(); act.goMenu(); });
        o.show(act.overlayLayer());
    }

    private void updateHud() {
        int m = session.moves();
        if (daily) movesText.setText(String.valueOf(m));
        else {
            int left = session.budgetLeft();
            movesText.setText(String.valueOf(left));
            movesText.setTextColor(left <= 3 ? 0xFFFF7A8E : Palette.WHITE);
        }
        int stars = StarRating.stars(Math.max(m, 0), level.optimal);
        live.setStars(stars);
        if (stars < lastLiveStars) {
            live.animate().scaleX(1.25f).scaleY(1.25f).setDuration(120).withEndAction(() -> live.animate().scaleX(1f).scaleY(1f).setDuration(160).start()).start();
        }
        lastLiveStars = stars;
        String pre = daily ? "" : "Hamle " + m + " · ";
        if (m <= StarRating.threeStarLimit(level.optimal))
            goalText.setText(pre + "3★ için en fazla " + level.optimal);
        else if (m <= StarRating.twoStarLimit(level.optimal))
            goalText.setText(pre + "2★ için en fazla " + StarRating.twoStarLimit(level.optimal));
        else goalText.setText(pre + "en az hamle " + level.optimal);
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
            o.body("Bir sonraki doğru hamle tahtada altın halkayla gösterilir.\nKalan ipucu: " + p.hints + "\nHer 10 bölümde +1 ipucu · sonrakine " + p.levelsUntilNextHint() + " bölüm");
            o.button("İpucu Göster", Ui.GOLD, Icon.BULB, v -> { o.dismiss(); useHint(); });
            o.button("Vazgeç", Ui.SECONDARY, -1, v -> o.dismiss());
        } else {
            o.body("İpucun kalmadı.\nHer 10 bölüm tamamladığında +1 ipucu kazanırsın.\nSonraki ipucuna " + p.levelsUntilNextHint() + " bölüm kaldı.");
            Economy.Item hi = Economy.item("hint1");
            int hp = Economy.price(p, hi, Game.shopDay());
            o.button("1 ipucu al · " + hp + " kristal", Ui.GOLD, Icon.CRYSTAL, v -> {
                String why = Economy.blockReason(Game.progress(), hi, Game.shopDay());
                if (why != null) { Game.audio().play(Audio.LOCKED); act.toast(why); return; }
                Economy.buy(Game.progress(), hi, Game.shopDay(), Game.clock());
                Game.save();
                o.dismiss();
                act.checkAchievements();
                useHint();
            });
            o.button("Tamam", Ui.SECONDARY, -1, v -> o.dismiss());
        }
        o.show(act.overlayLayer());
    }

    private void useHint() {
        if (thinking || finished) return;
        Progress p = Game.progress();
        if (!p.useHint()) return;
        session.markHintUsed();
        Game.save();
        updateHud();
        thinking = true;
        board.setInputEnabled(false);
        act.toast("Işık yolu hesaplanıyor…");
        final GameSession s = session;
        GameLoader.background(() -> s.plan(HINT_CAP), pl -> {
            thinking = false;
            board.setInputEnabled(!finished);
            if (pl == null || pl.remaining < 0) { refundHint("İpucu bulunamadı. Sıfırlayıp tekrar dene."); return; }
            if (!pl.feasible) {
                refundHint("Bu denemede kalan hak (" + session.budgetLeft() + ") yetmez: en az " + pl.remaining
                        + " hamle gerekir. Sıfırla ya da hak bitince devam et. İpucun iade edildi.");
                return;
            }
            Game.audio().play(Audio.HINT);
            if (pl.undos > 0) {
                pendingUndoHint = pl.undos;
                act.toast("Önce " + pl.undos + " kez Geri Al'a dokun (hak harcamaz), sonra sıradaki hamle gösterilecek");
                undoButton.animate().scaleX(1.15f).scaleY(1.15f).setDuration(180).withEndAction(() -> undoButton.animate().scaleX(1f).scaleY(1f).setDuration(220).start()).start();
            } else if (pl.move != null) {
                board.showHint(pl.move);
            }
            checkAchievements();
        });
    }

    private void refundHint(String msg) {
        Game.progress().hints++;
        Game.progress().hintsUsedTotal = Math.max(0, Game.progress().hintsUsedTotal - 1);
        Game.save();
        updateHud();
        act.toast(msg);
    }

    /** Geri almalar bittiğinde sıradaki hamleyi ücretsiz gösterir. */
    private void showPlanMove(boolean charge) {
        thinking = true;
        board.setInputEnabled(false);
        final GameSession s = session;
        GameLoader.background(() -> s.plan(HINT_CAP), pl -> {
            thinking = false;
            board.setInputEnabled(!finished);
            if (pl != null && pl.feasible && pl.undos == 0 && pl.move != null) board.showHint(pl.move);
        });
    }

    // ---------- tamamlanma ----------

    private void win() {
        finished = true;
        won = true;
        pendingUndoHint = 0;
        board.setInputEnabled(false);
        board.celebrate();
        Game.audio().play(Audio.WIN);
        Game.haptics().success();
        Progress p = Game.progress();
        int stars = session.stars();
        boolean first = false;
        int hintsBefore = p.hints;
        Economy.Reward reward;
        if (daily) {
            // ödül yalnızca güvenilir "bugün"ün bulmacası için ve o gün ilk kez çözülünce
            boolean isToday = epochDay == Game.today();
            boolean firstToday = isToday && !p.dailyDone.contains(DailyPuzzle.keyFor(epochDay));
            if (isToday) p.dailyDone.add(DailyPuzzle.keyFor(epochDay)); // başka günün bulmacası seriye/başarılara sayılmaz
            reward = firstToday ? Economy.onDailyComplete(p, p.dailyStreak(epochDay)) : new Economy.Reward();
        } else {
            int oldStars = p.stars[level.id];
            first = p.recordLevel(level.id, stars, session.moves(), session.hintsUsed() > 0);
            Lives.winAttempt(p, Game.clock(), level.id);
            reward = Economy.onLevelComplete(p, level.id, oldStars, p.stars[level.id]);
        }
        boolean rewarded = p.hints > hintsBefore;
        Game.save();
        final boolean firstF = first;
        pendingOverlay = () -> {
            pendingOverlay = null;
            if (!onScreen()) return;
            showComplete(stars, firstF, rewarded, reward);
            checkAchievements();
        };
        postDelayed(pendingOverlay, 1100);
    }

    private void checkAchievements() { act.checkAchievements(); }

    private void showComplete(int stars, boolean first, boolean rewarded, Economy.Reward reward) {
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
        if (daily) b.append("\nSeri: ").append(Game.progress().dailyStreak(epochDay)).append(" gün");
        o.body(b.toString());
        if (reward.any()) {
            LinearLayout gain = Ui.horizontal(c);
            gain.setGravity(Gravity.CENTER);
            gain.setBackground(Ui.round(0x3322C7B2, Ui.dp(c, 14)));
            gain.setPadding(Ui.dp(c, 12), Ui.dp(c, 6), Ui.dp(c, 14), Ui.dp(c, 6));
            if (reward.crystals > 0) {
                gain.addView(Ui.icon(c, Icon.CRYSTAL, com.isikyolu.game.ui.CurrencyBar.CRYSTAL_COLOR, 18));
                gain.addView(Ui.text(c, " +" + reward.crystals + "  ", 16, Palette.WHITE, Ui.BOLD));
            }
            if (reward.gems > 0) {
                gain.addView(Ui.icon(c, Icon.GEM, com.isikyolu.game.ui.CurrencyBar.GEM_COLOR, 18));
                gain.addView(Ui.text(c, " +" + reward.gems, 16, Palette.WHITE, Ui.BOLD));
            }
            o.cardView().addView(gain, Ui.lpMargins(c, -2, -2, 0, 0, 0, 4));
            TextView why = Ui.text(c, String.join(" · ", reward.lines), 12, Palette.TEXT_DIM, Ui.MEDIUM);
            why.setGravity(Gravity.CENTER);
            o.cardView().addView(why, Ui.lpMargins(c, -2, -2, 0, 0, 0, 6));
        }
        if (rewarded) {
            TextView r = Ui.text(c, "+1 ipucu kazandın! (" + Game.progress().completedCount() + " bölüm tamamlandı)", 15, Palette.GOLD, Ui.BOLD);
            r.setGravity(Gravity.CENTER);
            o.cardView().addView(r, Ui.lpMargins(c, -2, -2, 0, 4, 0, 4));
        }
        if (!rewarded && !daily && first) {
            TextView r = Ui.text(c, "Sonraki ipucuna " + Game.progress().levelsUntilNextHint() + " bölüm kaldı", 13, Palette.TEXT_DIM, Ui.MEDIUM);
            o.cardView().addView(r, Ui.lpMargins(c, -2, -2, 0, 0, 0, 4));
        }
        if (daily) {
            o.button("Ana Menü", Ui.PRIMARY, Icon.HOME, v -> { o.dismiss(); act.goMenu(); });
            o.button("Tekrar Oyna", Ui.SECONDARY, Icon.RESET, v -> { o.dismiss(); act.goDailyGame(level, epochDay); });
        } else if (level.id < Progress.LEVEL_COUNT) {
            o.button("Sonraki Bölüm", Ui.PRIMARY, Icon.NEXT, v -> { o.dismiss(); act.goLevel(level.id + 1); });
            o.button("Tekrar Oyna", Ui.SECONDARY, Icon.RESET, v -> { o.dismiss(); act.goLevel(level.id); });
            o.button("Bölümler", Ui.SECONDARY, Icon.GRID, v -> { o.dismiss(); act.goLevels(); });
        } else {
            o.body("Tebrikler! " + Progress.LEVEL_COUNT + " bölümün hepsini bitirdin. Sen gerçek bir Işık Ustasısın!");
            o.button("Bölümler", Ui.PRIMARY, Icon.GRID, v -> { o.dismiss(); act.goLevels(); });
            o.button("Tekrar Oyna", Ui.SECONDARY, Icon.RESET, v -> { o.dismiss(); act.goLevel(level.id); });
        }
        o.show(act.overlayLayer());
    }

    // ---------- duraklatma ----------

    private void showPause() {
        if (act.hasOverlay() || finished) return; // bitmiş bölümde (pencere gelmek üzere) duraklatma açılmaz
        Overlay o = new Overlay(act);
        o.title("Duraklatıldı");
        o.body((daily ? "Günlük Bulmaca" : "Bölüm " + level.id + " · " + level.name) + "\nHamle: " + session.moves()
                + (daily ? "" : " · Kalan hak: " + session.budgetLeft())
                + (attemptActive() ? (Game.progress().shields > 0 ? "\nÇıkarsan deneme bırakılır; Can Kalkanın canını korur (1 kalkan harcanır)."
                        : "\nÇıkarsan bu denemenin canı geri gelmez.") : ""));
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
