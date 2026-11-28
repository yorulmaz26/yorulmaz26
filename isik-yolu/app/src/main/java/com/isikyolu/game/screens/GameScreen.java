package com.isikyolu.game.screens;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.isikyolu.engine.AdPolicy;
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
import com.isikyolu.game.i18n.L;
import com.isikyolu.game.ui.BoardView;
import com.isikyolu.game.ui.Icon;
import com.isikyolu.game.ui.MiniStars;
import com.isikyolu.game.ui.Overlay;
import com.isikyolu.game.ui.Palette;
import com.isikyolu.game.ui.StarsView;
import com.isikyolu.game.ui.TutorialOverlay;
import com.isikyolu.game.ui.Ui;

import java.util.ArrayList;
import java.util.List;

/** 4. Oyun ekranı (+ 5. duraklatma, 6. bölüm tamamlandı, 7. ipucu pencereleri). */
public final class GameScreen extends Screen implements BoardView.Host {
    private static final int HINT_CAP = 150_000;

    private final Level level;
    private final boolean daily;
    private final int epochDay;
    /** Günlük bulmacada ekranın açıldığı gün: gece yarısını geçse de o günün bulmacası ödül alır. */
    private final int startDay;
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
        this.startDay = daily ? Game.today() : -1;
        this.session = new GameSession(lvl, !daily);
        Context c = a;
        LinearLayout root = Ui.vertical(c);

        // üst çubuk
        LinearLayout top = Ui.horizontal(c);
        top.setPadding(Ui.dp(c, 16), Ui.dp(c, 14), Ui.dp(c, 16), Ui.dp(c, 4));
        FrameLayout pause = Ui.iconButton(c, Icon.PAUSE, Ui.SECONDARY, 46, v -> showPause());
        pause.setContentDescription(L.t("game.pause_button"));
        top.addView(pause);
        LinearLayout tc = Ui.vertical(c);
        tc.setPadding(Ui.dp(c, 14), 0, Ui.dp(c, 8), 0);
        String t = daily ? L.t("game.daily_title") : L.t("common.level_n", lvl.id);
        String sub = daily ? DailyScreen.prettyDate(epochDay) : subtitle(lvl.chapter);
        LinearLayout titleRow = Ui.horizontal(c);
        titleRow.addView(Ui.text(c, t, 21, Palette.WHITE, Ui.BOLD));
        if (!daily && Chapters.requiresLogic(lvl.id)) {
            TextView tag = Ui.text(c, L.t("game.logic_tag"), 10, Palette.WHITE, Ui.BOLD);
            tag.setLetterSpacing(0.08f);
            tag.setSingleLine(true);
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
        movesLabel = Ui.text(c, daily ? L.t("game.moves_label") : L.t("game.moves_left_label"), 10, Palette.TEXT_DIM, Ui.BOLD);
        movesLabel.setLetterSpacing(0.1f);
        movesLabel.setSingleLine(true);
        movesLabel.setGravity(Gravity.CENTER);
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
        goalText.setMaxLines(2); // uzun dillerde iki satıra iner
        goalText.setEllipsize(android.text.TextUtils.TruncateAt.END);
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
        undoButton = action(c, Icon.UNDO, L.t("game.undo"), Ui.SECONDARY, v -> undo());
        Ui.allowRepeat(undoButton.getChildAt(0)); // art arda Geri Al meşru (akıllı ipucu "N kez Geri Al" diyebilir)
        bottom.addView(undoButton, weightLp(c));
        bottom.addView(action(c, Icon.RESET, L.t("game.reset"), Ui.SECONDARY, v -> reset()), weightLp(c));
        FrameLayout hint = action(c, Icon.BULB, L.t("game.hint"), Ui.GOLD, v -> requestHint());
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
        t.setPadding(Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4), 0);
        t.setGravity(Gravity.CENTER);
        // uzun çeviriler düğme genişliğine sığmazsa tek satırda küçülür
        // (setSingleLine yatay kaydırmayı açar ve otomatik boyutlandırmayı bozar; bu yüzden maxLines 1)
        t.setMaxLines(1);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setAutoSizeTextTypeUniformWithConfiguration(9, 13, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        b.addView(t, new LinearLayout.LayoutParams(-1, -2));
        f.addView(b, new FrameLayout.LayoutParams(-1, -1));
        return f;
    }

    /** Bölüm alt başlığı: grup adı · konu (ikinci dünyadan itibaren · zorluk). Motorun Türkçe adları yerine anahtarlar. */
    private static String subtitle(int chapter) {
        String topic = L.t(key("topic.", Chapters.mechanicOf(chapter), ""));
        int w = Chapters.worldOfChapter(chapter);
        if (w == 1) return L.t("game.subtitle", chapterName(chapter), topic);
        return L.t("game.subtitle_world", chapterName(chapter), topic, L.t(key("world.", w, ".level")));
    }

    /** Grup adı ("chapter.<1-50>.name"). */
    private static String chapterName(int chapter) { return L.t(key("chapter.", chapter, ".name")); }

    /** Bölümün grubundaki sırası (1–10); levels.json'daki "<grup adı> <n>" adıyla aynı. */
    private static int numberInChapter(Level l) { return (l.id - 1) % 10 + 1; }

    private static String key(String prefix, int n, String suffix) { return prefix + n + suffix; }

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
            act.banner(Icon.HELP, L.t("game.first_logic_title"), L.t("game.first_logic_body"));
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
            act.toast(L.t("game.tap_glowing_mirror"));
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
        pendingUndoHint = 0; // ipucunun "önce Geri Al" talimatından sapıldı
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
        pendingUndoHint = 0;
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

    /** Tahtayı kaplayan bir pencere açıkken süren dokunma hamle yapmaz. */
    @Override public boolean acceptsInput() { return !act.hasOverlay(); }

    @Override public void onBlockTapped() { act.toast(L.t("game.block_tapped")); }

    private long lastFixedToast;

    @Override public void onFixedMirrorTapped() {
        long now = android.os.SystemClock.uptimeMillis();
        if (now - lastFixedToast > 2500) { lastFixedToast = now; act.toast(L.t("game.fixed_mirror")); }
    }

    private void undo() {
        if (finished || thinking) return;
        Move m = session.lastMove();
        if (m == null || !session.undo()) { act.toast(L.t("game.nothing_to_undo")); return; }
        Game.audio().play(Audio.UNDO);
        Game.progress().undoCount++;
        board.onMove(m, true);
        updateHud();
        checkAchievements();
        if (pendingUndoHint > 0 && --pendingUndoHint == 0) showPlanMove(false); // geri almalar bitti: sıradaki hamle ücretsiz gösterilir
    }

    private void reset() {
        if (finished || thinking) return;
        if (session.actions() == 0) { act.toast(L.t("game.already_reset")); return; }
        if (attemptActive()) {
            Overlay o = new Overlay(act);
            o.title(L.t("game.restart_title"));
            Progress pp = Game.progress();
            StringBuilder rb = new StringBuilder(pp.shields > 0 ? L.t("game.restart_shield", pp.shields) : L.t("game.restart_lose"));
            rb.append('\n').append(L.t("game.lives_left", pp.lives));
            if (pp.lives == 0 && pp.shields == 0) rb.append('\n').append(L.t("game.restart_wait"));
            o.body(rb.toString());
            o.button(L.t("game.restart"), Ui.GOLD, Icon.RESET, v -> { o.dismiss(); giveUpAttempt(); doReset(); });
            o.button(L.t("common.cancel"), Ui.SECONDARY, -1, v -> o.dismiss());
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
        if (Economy.loseAttempt(Game.progress(), Game.clock())) act.toast(L.t("game.shield_saved"));
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
        o.title(L.t("game.oom_title"));
        Progress p = Game.progress();
        int price = Economy.continuePrice(session.continues());
        StringBuilder ob = new StringBuilder(L.t("game.oom_body"));
        ob.append('\n').append(L.t("game.lives_left_of", p.lives, Lives.MAX));
        if (p.shields > 0) ob.append('\n').append(L.t("game.oom_shield", p.shields));
        o.body(ob.toString());
        String contLabel = p.tickets > 0 ? L.n("game.oom_ticket", extra, p.tickets)
                : L.n("game.oom_buy", extra, L.n("common.crystals", price));
        if (!daily && act.videoAvailable(AdPolicy.Reward.MOVES)) {
            o.button(L.n("game.oom_video", extra, act.videosLeft(AdPolicy.Reward.MOVES)), Ui.PRIMARY, Icon.PLAY, v ->
                    act.watchVideo(AdPolicy.Reward.MOVES, () -> {
                        o.dismiss();
                        session.grantBudget(extra);
                        finished = false;
                        board.setInputEnabled(true);
                        Game.save();
                        updateHud();
                    }));
        }
        o.button(contLabel, Ui.GOLD, p.tickets > 0 ? Icon.TICKET : Icon.CRYSTAL, v -> {
            if (!Economy.payContinue(Game.progress(), session.continues())) {
                Game.audio().play(Audio.LOCKED);
                act.toast(L.t("game.no_crystals_need", price, Game.progress().crystals));
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
        o.button(L.t("game.try_again"), Ui.PRIMARY, Icon.RESET, v -> { o.dismiss(); giveUpAttempt(); act.goLevel(level.id); }); // can yoksa goLevel bölümlere döner
        o.button(L.t("common.levels"), Ui.SECONDARY, Icon.GRID, v -> { o.dismiss(); giveUpAttempt(); act.goLevels(); });
        o.button(L.t("game.main_menu"), Ui.SECONDARY, Icon.HOME, v -> { o.dismiss(); giveUpAttempt(); act.goMenu(); });
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
        int two = StarRating.twoStarLimit(level.optimal);
        if (m <= StarRating.threeStarLimit(level.optimal))
            goalText.setText(daily ? L.t("game.goal_three", level.optimal) : L.t("game.goal_three_n", m, level.optimal));
        else if (m <= two)
            goalText.setText(daily ? L.t("game.goal_two", two) : L.t("game.goal_two_n", m, two));
        else goalText.setText(daily ? L.t("game.goal_min", level.optimal) : L.t("game.goal_min_n", m, level.optimal));
        hintBadge.setText(String.valueOf(Game.progress().hints));
    }

    // ---------- ipucu ----------

    private void requestHint() {
        if (finished || thinking) return;
        Progress p = Game.progress();
        if (board.hasHint()) { act.toast(L.t("game.hint_on_board")); return; }
        Overlay o = new Overlay(act);
        Context c = act;
        FrameLayout ic = new FrameLayout(c);
        ic.setBackground(Ui.round(0x33F5C542, Ui.dp(c, 34)));
        ic.addView(Ui.icon(c, Icon.BULB, Palette.GOLD, 38), new FrameLayout.LayoutParams(Ui.dp(c, 38), Ui.dp(c, 38), Gravity.CENTER));
        o.cardView().addView(ic, new LinearLayout.LayoutParams(Ui.dp(c, 68), Ui.dp(c, 68)));
        o.title(L.t("game.hint"));
        if (p.hints > 0) {
            o.body(lines(L.t("game.hint_body"), L.t("game.hints_left", p.hints), L.n("game.hint_every10", p.levelsUntilNextHint())));
            o.button(L.t("game.hint_show"), Ui.GOLD, Icon.BULB, v -> { o.dismiss(); useHint(); });
            o.button(L.t("common.cancel"), Ui.SECONDARY, -1, v -> o.dismiss());
        } else {
            o.body(lines(L.t("game.hint_none"), L.t("game.hint_earn"), L.n("game.hint_next", p.levelsUntilNextHint())));
            Economy.Item hi = Economy.item("hint1");
            int hp = Economy.price(p, hi, Game.shopDay());
            if (act.videoAvailable(AdPolicy.Reward.HINT)) {
                o.button(L.t("game.hint_video", act.videosLeft(AdPolicy.Reward.HINT)), Ui.PRIMARY, Icon.PLAY, v ->
                        act.watchVideo(AdPolicy.Reward.HINT, () -> {
                            o.dismiss();
                            updateHud();
                            useHint();
                        }));
            }
            o.button(L.t("game.hint_buy", L.n("common.crystals", hp)), Ui.GOLD, Icon.CRYSTAL, v -> {
                String why = Economy.blockReason(Game.progress(), hi, Game.shopDay());
                if (why != null) { Game.audio().play(Audio.LOCKED); act.toast(com.isikyolu.game.i18n.EconomyTexts.blockReason(why, hi)); return; }
                Economy.buy(Game.progress(), hi, Game.shopDay(), Game.clock());
                Game.save();
                o.dismiss();
                act.checkAchievements();
                useHint();
            });
            o.button(L.t("common.ok"), Ui.SECONDARY, -1, v -> o.dismiss());
        }
        o.show(act.overlayLayer());
    }

    /** Ayrı cümleleri alt alta dizer. */
    private static String lines(String... parts) { return String.join("\n", parts); }

    private void useHint() {
        if (thinking || finished) return;
        Progress p = Game.progress();
        if (!p.useHint()) return;
        session.markHintUsed();
        Game.save();
        updateHud();
        thinking = true;
        board.setInputEnabled(false);
        act.toast(L.t("game.hint_thinking"));
        final GameSession s = session;
        GameLoader.background(() -> s.plan(HINT_CAP), pl -> {
            thinking = false;
            board.setInputEnabled(!finished);
            if (pl == null || pl.remaining < 0) { refundHint(L.t("game.hint_not_found")); return; }
            if (!pl.feasible) {
                refundHint(L.n("game.hint_not_enough", pl.remaining, session.budgetLeft()));
                return;
            }
            Game.audio().play(Audio.HINT);
            if (pl.undos > 0) {
                pendingUndoHint = pl.undos;
                act.toast(L.n("game.hint_undo_first", pl.undos));
                undoButton.animate().scaleX(1.15f).scaleY(1.15f).setDuration(180).withEndAction(() -> undoButton.animate().scaleX(1f).scaleY(1f).setDuration(220).start()).start();
            } else if (pl.move != null) {
                board.showHint(pl.move);
            }
            checkAchievements();
        });
    }

    private void refundHint(String msg) {
        session.unmarkHintUsed(); // iade edilen ipucu bölümü "ipucuyla çözüldü" saymaz
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
            if (pl == null || !pl.feasible) return;
            if (pl.undos == 0 && pl.move != null) board.showHint(pl.move);
            else if (pl.undos > 0) {
                pendingUndoHint = pl.undos;
                act.toast(L.n("game.hint_undo_more", pl.undos));
            }
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
            boolean isToday = epochDay == startDay || epochDay == Game.today();
            boolean firstToday = isToday && !p.dailyDone.contains(DailyPuzzle.keyFor(epochDay));
            if (isToday) p.dailyDone.add(DailyPuzzle.keyFor(epochDay)); // başka günün bulmacası seriye/başarılara sayılmaz
            reward = firstToday ? Economy.onDailyComplete(p, p.dailyStreak(epochDay)) : new Economy.Reward();
        } else {
            int oldStars = p.stars[level.id];
            first = p.recordLevel(level.id, stars, session.moves(), session.hintsUsed() > 0);
            Lives.winAttempt(p, Game.clock(), level.id);
            int newStars = p.stars[level.id];
            reward = Economy.onLevelComplete(p, level.id, oldStars, newStars);
        }
        boolean rewarded = p.hints > hintsBefore;
        AdPolicy.onLevelCompleted(p, daily ? 0 : level.id);
        Game.save();
        checkAchievements(); // hemen: pencere gelmeden çıkılsa da başarı ve ödülü kaybolmaz
        final boolean firstF = first;
        pendingOverlay = () -> {
            pendingOverlay = null;
            if (!onScreen()) return;
            showComplete(stars, firstF, rewarded, reward);
        };
        postDelayed(pendingOverlay, 1100);
    }

    private void checkAchievements() { act.checkAchievements(); }

    private void showComplete(int stars, boolean first, boolean rewarded, Economy.Reward reward) {
        Overlay o = new Overlay(act).setCancelable(false);
        Context c = act;
        String[] titles = {"", L.t("game.complete_1"), L.t("game.complete_2"), L.t("game.complete_3")};
        TextView tag = Ui.text(c, daily ? L.t("game.complete_daily_tag") : L.t("game.complete_level_tag", level.id), 13, Palette.TURQUOISE, Ui.BOLD);
        tag.setGravity(Gravity.CENTER);
        tag.setLetterSpacing(0.08f);
        o.cardView().addView(tag);
        o.title(titles[stars]);
        StarsView sv = new StarsView(c);
        o.cardView().addView(sv, Ui.lpMargins(c, -1, Ui.dp(c, 78), 0, 6, 0, 8));
        sv.setStars(stars, true);
        StringBuilder b = new StringBuilder();
        b.append(L.t("game.complete_moves", session.moves(), level.optimal));
        if (!daily && Game.progress().bestMoves[level.id] > 0)
            b.append('\n').append(L.n("game.complete_best", Game.progress().bestMoves[level.id]));
        if (stars < 3) b.append('\n').append(L.n("game.complete_tip", level.optimal));
        if (daily) {
            if (Game.progress().dailyDone.contains(DailyPuzzle.keyFor(epochDay)))
                b.append('\n').append(L.n("game.complete_streak", Game.progress().dailyStreak(epochDay)));
            else b.append('\n').append(L.t("game.complete_not_today"));
        }
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
            TextView why = Ui.text(c, com.isikyolu.game.i18n.EconomyTexts.rewardSummary(reward), 12, Palette.TEXT_DIM, Ui.MEDIUM);
            why.setGravity(Gravity.CENTER);
            o.cardView().addView(why, Ui.lpMargins(c, -2, -2, 0, 0, 0, 6));
        }
        if (rewarded) {
            TextView r = Ui.text(c, L.n("game.complete_hint_earned", Game.progress().completedCount()), 15, Palette.GOLD, Ui.BOLD);
            r.setGravity(Gravity.CENTER);
            o.cardView().addView(r, Ui.lpMargins(c, -2, -2, 0, 4, 0, 4));
        }
        if (!rewarded && !daily && first) {
            TextView r = Ui.text(c, L.n("game.complete_next_hint", Game.progress().levelsUntilNextHint()), 13, Palette.TEXT_DIM, Ui.MEDIUM);
            r.setGravity(Gravity.CENTER);
            o.cardView().addView(r, Ui.lpMargins(c, -2, -2, 0, 0, 0, 4));
        }
        // sonraki ekrana geçerken, zamanı geldiyse geçiş reklamı (bkz. AdPolicy)
        int adId = daily ? 0 : level.id;
        if (daily) {
            o.button(L.t("game.main_menu"), Ui.PRIMARY, Icon.HOME, v -> { o.dismiss(); act.afterLevel(adId, act::goMenu); });
            o.button(L.t("game.play_again"), Ui.SECONDARY, Icon.RESET, v -> { o.dismiss(); act.afterLevel(adId, () -> act.goDailyGame(level, epochDay)); });
        } else if (level.id < Progress.LEVEL_COUNT) {
            o.button(L.t("game.next_level"), Ui.PRIMARY, Icon.NEXT, v -> { o.dismiss(); act.afterLevel(adId, () -> act.goLevel(level.id + 1)); });
            o.button(L.t("game.play_again"), Ui.SECONDARY, Icon.RESET, v -> { o.dismiss(); act.afterLevel(adId, () -> act.goLevel(level.id)); });
            o.button(L.t("common.levels"), Ui.SECONDARY, Icon.GRID, v -> { o.dismiss(); act.afterLevel(adId, act::goLevels); });
        } else {
            o.body(L.t("game.complete_all", Progress.LEVEL_COUNT));
            o.button(L.t("common.levels"), Ui.PRIMARY, Icon.GRID, v -> { o.dismiss(); act.afterLevel(adId, act::goLevels); });
            o.button(L.t("game.play_again"), Ui.SECONDARY, Icon.RESET, v -> { o.dismiss(); act.afterLevel(adId, () -> act.goLevel(level.id)); });
        }
        o.show(act.overlayLayer());
    }

    // ---------- duraklatma ----------

    private void showPause() {
        if (act.hasOverlay() || finished) return; // bitmiş bölümde (pencere gelmek üzere) duraklatma açılmaz
        Overlay o = new Overlay(act);
        o.title(L.t("game.paused"));
        StringBuilder b = new StringBuilder();
        // levels.json'daki Türkçe level.name yerine: grup adı anahtarı + gruptaki sıra
        b.append(daily ? L.t("game.daily_title") : L.t("game.pause_level", level.id, chapterName(level.chapter), numberInChapter(level)));
        b.append('\n').append(daily ? L.t("game.pause_moves", session.moves()) : L.t("game.pause_moves_left", session.moves(), session.budgetLeft()));
        if (attemptActive())
            b.append('\n').append(Game.progress().shields > 0 ? L.t("game.pause_quit_shield") : L.t("game.pause_quit_lose"));
        o.body(b.toString());
        o.button(L.t("game.resume"), Ui.PRIMARY, Icon.PLAY, v -> o.dismiss());
        o.button(L.t("game.restart"), Ui.SECONDARY, Icon.RESET, v -> { o.dismiss(); reset(); });
        if (daily) o.button(L.t("game.daily_title"), Ui.SECONDARY, Icon.CALENDAR, v -> { o.dismiss(); act.goDaily(); });
        else o.button(L.t("game.select_level"), Ui.SECONDARY, Icon.GRID, v -> { o.dismiss(); act.goLevels(); });
        o.button(L.t("game.main_menu"), Ui.SECONDARY, Icon.HOME, v -> { o.dismiss(); act.goMenu(); });
        LinearLayout row = Ui.horizontal(act);
        row.setGravity(Gravity.CENTER);
        row.addView(toggle(Icon.SOUND, L.t("game.sound_on"), L.t("game.sound_off"), () -> Game.progress().sound, () -> { Game.progress().sound = !Game.progress().sound; }));
        row.addView(toggle(Icon.MUSIC, L.t("game.music_on"), L.t("game.music_off"), () -> Game.progress().music, () -> { Game.progress().music = !Game.progress().music; Game.audio().updateMusic(); }));
        row.addView(toggle(Icon.VIBRATE, L.t("game.vibration_on"), L.t("game.vibration_off"), () -> Game.progress().vibration, () -> { Game.progress().vibration = !Game.progress().vibration; }));
        o.cardView().addView(row, Ui.lpMargins(act, -1, -2, 0, 14, 0, 0));
        o.show(act.overlayLayer());
    }

    private interface Getter { boolean get(); }

    private View toggle(int icon, String onLabel, String offLabel, Getter g, Runnable flip) {
        Context c = act;
        LinearLayout col = Ui.vertical(c);
        col.setGravity(Gravity.CENTER);
        FrameLayout btn = Ui.iconButton(c, icon, g.get() ? Ui.PRIMARY : Ui.SECONDARY, 52, null);
        col.addView(btn);
        TextView t = Ui.text(c, g.get() ? onLabel : offLabel, 12, Palette.TEXT_DIM, Ui.MEDIUM);
        t.setPadding(0, Ui.dp(c, 4), 0, 0);
        t.setGravity(Gravity.CENTER);
        t.setMaxLines(2); // uzun dillerde iki satır
        btn.setContentDescription(g.get() ? onLabel : offLabel);
        col.addView(t);
        Ui.clickable(btn, v -> {
            flip.run();
            Game.save();
            boolean on = g.get();
            android.widget.ImageView iv = (android.widget.ImageView) btn.getChildAt(0);
            ((Icon) iv.getDrawable()).setColor(Palette.WHITE);
            btn.setAlpha(on ? 1f : 0.55f);
            t.setText(on ? onLabel : offLabel);
            btn.setContentDescription(on ? onLabel : offLabel);
        });
        btn.setAlpha(g.get() ? 1f : 0.55f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        col.setLayoutParams(lp);
        return col;
    }
}
