package com.golgen.oyun.screens;

import android.graphics.Canvas;
import android.graphics.Paint;

import com.golgen.oyun.GameView;
import com.golgen.oyun.core.Achievements;
import com.golgen.oyun.core.DailyMissions;
import com.golgen.oyun.core.Energy;
import com.golgen.oyun.core.GameWorld;
import com.golgen.oyun.core.L10n;
import com.golgen.oyun.core.MissionDef;
import com.golgen.oyun.core.Missions;
import com.golgen.oyun.core.Progress;
import com.golgen.oyun.core.RunResult;
import com.golgen.oyun.core.Synth;
import com.golgen.oyun.ui.Button;
import com.golgen.oyun.ui.Icons;
import com.golgen.oyun.ui.Screen;
import com.golgen.oyun.ui.Theme;
import com.golgen.oyun.ui.Ui;
import com.golgen.oyun.ui.WorldRenderer;

/** Oyun sonu ekranı: skor, rekor, kazanılan elmaslar, görev sonucu ve yeniden oynama. */
public final class GameOverScreen extends Screen {
    private final WorldRenderer bg = new WorldRenderer();
    private final RunResult r = new RunResult();
    private String title = "", subtitle = "", scoreText = "", bestText = "";
    private String[] statLabels = new String[0], statValues = new String[0];
    private String rewardText = "";
    private String scoreLabel = "", newRecordText = "";
    private final Button videoBtn;
    private int videoBonus;
    private boolean videoUsed;
    private int shownTotal;
    private boolean newRecord, won;
    private int titleColor = Theme.WHITE;
    private final Button againBtn, nextBtn, menuBtn;
    private float shownScore;
    private int stars;
    private String xpText = "", levelText = "";
    private float xpFrac;
    private boolean levelUp;
    private String livesText = "";
    // Dil değişiminde metinleri yeniden üretmek için gösterilen sonucun ham değerleri
    private boolean hasResult;
    private int bestShown, levelShown, xpGained;

    public GameOverScreen(GameView g) {
        super(g);
        // Oyun sonundan çıkış = doğal mola: gerekirse (sıklık sınırıyla) geçiş reklamı burada gösterilir.
        againBtn = add(new Button(null, Icons.RESTART, Button.PRIMARY, () -> g.afterRunBreak(() -> {
            if (r.mode == RunResult.MODE_MISSION) g.startMission(r.missionIndex); else g.startEndless();
        })).key("gameover.play_again"));
        nextBtn = add(new Button(null, Icons.NEXT, Button.GOLD, () -> g.afterRunBreak(() -> g.startMission(r.missionIndex + 1)))
                .key("gameover.next_level"));
        menuBtn = add(new Button(null, Icons.HOME, Button.SECONDARY, () -> g.afterRunBreak(g::goMenu)).key("gameover.main_menu"));
        // İsteğe bağlı ödüllü video: bu oyunda kazanılan elmasları ikiye katlar
        videoBtn = add(new Button("", Icons.PLAY, Button.SECONDARY, this::watchVideo));
        videoBtn.id = "video-elmas";
        videoBtn.visible = false;
    }

    public void show(RunResult res, Progress.Report rep) {
        r.mode = res.mode; r.missionIndex = res.missionIndex; r.score = res.score; r.time = res.time;
        r.diamonds = res.diamonds; r.dodges = res.dodges; r.hits = res.hits; r.won = res.won; r.cause = res.cause;
        won = res.won;
        newRecord = rep.newRecord;
        titleColor = res.mode == RunResult.MODE_MISSION && won ? Theme.GOLD : Theme.PURPLE_LIGHT;
        scoreText = Integer.toString(res.score);
        bestShown = g.progress.bestScore;
        int sec = (int) res.time;
        statValues = new String[]{(sec / 60) + ":" + (sec % 60 < 10 ? "0" : "") + (sec % 60),
                Integer.toString(res.diamonds), Integer.toString(res.dodges), Integer.toString(res.hits)};
        int total = rep.diamondsEarned + rep.missionReward + rep.achievementReward;
        total += rep.levelReward;
        shownTotal = total;
        videoBonus = rep.diamondsEarned + rep.missionReward;
        videoUsed = false;
        videoBtn.visible = false;
        stars = res.mode == RunResult.MODE_MISSION ? res.stars : 0;
        levelUp = rep.levelUps > 0;
        levelShown = g.progress.level;
        xpGained = rep.xpGained;
        xpFrac = g.progress.xp / (float) Progress.xpForLevel(g.progress.level);
        hasResult = true;
        buildTexts();
        if (levelUp) g.toast(L10n.f("gameover.level_up_toast", g.progress.level, rep.levelReward), Icons.STAR);
        if (rep.newStars > 0) g.toast(L10n.p("gameover.new_crowns", rep.newStars), Icons.STAR);
        nextBtn.visible = res.mode == RunResult.MODE_MISSION && won && res.missionIndex + 1 < Missions.ALL.length;

        for (int id = 0; id < Achievements.COUNT; id++) {
            if ((rep.newAchievements & (1 << id)) != 0) {
                g.toast(L10n.f("gameover.achievement_toast", Achievements.title(id), Achievements.REWARDS[id]), Icons.STAR);
            }
        }
        for (int i = 0; i < DailyMissions.PER_DAY; i++) {
            if ((rep.dailyCompleted & (1 << i)) != 0) g.toast(L10n.t("gameover.daily_done"), Icons.CALENDAR);
        }
        if (newRecord || won || rep.newAchievements != 0) g.sound.play(Synth.SUCCESS);
        layout();
    }

    @Override
    protected void onLocalize() {
        scoreLabel = L10n.t("gameover.score");
        newRecordText = L10n.t("gameover.new_record");
        statLabels = new String[]{L10n.t("gameover.stat.time"), L10n.t("gameover.stat.diamonds"),
                L10n.t("gameover.stat.dodges"), L10n.t("gameover.stat.hits")};
        buildTexts();
    }

    /** Gösterilen sonuca ait tüm yerelleştirilmiş metinler (sonuç gelince ve dil değişince). */
    private void buildTexts() {
        if (!hasResult) return;
        if (r.mode == RunResult.MODE_MISSION) {
            MissionDef m = Missions.ALL[r.missionIndex];
            title = L10n.t(won ? "gameover.level_complete" : "gameover.level_failed");
            subtitle = L10n.f("gameover.level_subtitle", m.title(), m.description());
        } else {
            boolean shadow = r.cause == GameWorld.CAUSE_SHADOW;
            title = L10n.t(shadow ? "gameover.caught_by_shadow" : "gameover.stopped_by_obstacles");
            subtitle = L10n.t(shadow ? "gameover.hint_shadow" : "gameover.hint_obstacles");
        }
        bestText = r.mode == RunResult.MODE_ENDLESS ? L10n.f("gameover.best", bestShown) : "";
        rewardText = L10n.p("gameover.reward", shownTotal);
        videoBtn.label = L10n.p("gameover.video_bonus", videoBonus);
        levelText = L10n.f("gameover.level", levelShown);
        xpText = L10n.f("gameover.xp", xpGained);
        againBtn.key(r.mode == RunResult.MODE_MISSION && won ? "gameover.again" : "gameover.play_again");
        lastLivesShown = -1;
        refreshLives();
    }

    private int lastLivesShown = -1;
    private String lastTimerShown = "";
    private int lastLivesLang = -1;

    /** Can bilgisi canlı güncellenir (sayaç akar, can yenilenince değişir). */
    private void refreshLives() {
        if (r.mode != RunResult.MODE_MISSION) { livesText = ""; return; }
        int l = g.lives();
        String tm = l < Energy.MAX ? g.energyTimer() : "";
        int v = L10n.version();
        if (l == lastLivesShown && tm.equals(lastTimerShown) && v == lastLivesLang && !livesText.isEmpty()) return;
        lastLivesShown = l;
        lastTimerShown = tm;
        lastLivesLang = v;
        String count = L10n.f("lives.count", l, Energy.MAX);
        if (won) livesText = L10n.f("gameover.lives_kept", count);
        else livesText = l < Energy.MAX ? L10n.f("gameover.lives_lost_timer", count, tm) : L10n.f("gameover.lives_lost", count);
    }

    private void watchVideo() {
        if (videoUsed || videoBonus <= 0) return;
        videoUsed = true;
        videoBtn.visible = false;
        g.ads.showRewarded(() -> {
            g.progress.diamonds += videoBonus;
            g.save();
            shownTotal += videoBonus;
            rewardText = L10n.p("gameover.reward", shownTotal);
            g.sound.play(Synth.PURCHASE);
            g.toast(L10n.p("gameover.video_reward_toast", videoBonus), Icons.PLAY);
        }, () -> dumpTargets());
    }

    @Override
    protected void layout() {
        float cx = w / 2f, bw = Math.min(w - 140f * u, 800f * u), bh = 140f * u;
        float bottom = h - safeB - 70f * u;
        menuBtn.set(cx - bw / 2f, bottom - bh, cx + bw / 2f, bottom);
        if (nextBtn.visible) {
            float half = (bw - 30f * u) / 2f;
            againBtn.set(cx - bw / 2f, menuBtn.rect.top - 30f * u - bh, cx - bw / 2f + half, menuBtn.rect.top - 30f * u);
            nextBtn.set(cx + bw / 2f - half, againBtn.rect.top, cx + bw / 2f, againBtn.rect.bottom);
        } else {
            againBtn.set(cx - bw / 2f, menuBtn.rect.top - 30f * u - bh, cx + bw / 2f, menuBtn.rect.top - 30f * u);
        }
        float vh = 104f * u;
        videoBtn.set(cx - bw / 2f, againBtn.rect.top - 26f * u - vh, cx + bw / 2f, againBtn.rect.top - 26f * u);
        bg.s = w / 1000f;
    }

    @Override
    public void enter() {
        super.enter();
        shownScore = 0f;
    }

    @Override
    public void update(float dt) {
        super.update(dt);
        bg.update(dt, 0f);
        boolean v = !videoUsed && videoBonus > 0 && g.ads.isRewardedReady();
        if (v != videoBtn.visible) { videoBtn.visible = v; dumpTargets(); }
        refreshLives();
        if (shownScore < r.score) shownScore = Math.min(r.score, shownScore + Math.max(1f, r.score * dt * 1.6f));
    }

    @Override
    public boolean wantsBanner() { return true; }

    @Override
    public boolean onBack() {
        g.goMenu();
        return true;
    }

    @Override
    public void draw(Canvas c) {
        bg.drawBackground(c, w, h, time * 60f);
        float cx = w / 2f;
        float top = safeT + 250f * u;
        Ui.textFit(c, title, cx, top, 72f * u, w - 100f * u, titleColor, Paint.Align.CENTER, Ui.DISPLAY);
        Ui.textFit(c, subtitle, cx, top + 66f * u, 34f * u, w - 140f * u, Theme.MUTED, Paint.Align.CENTER, Ui.MEDIUM);
        if (!livesText.isEmpty()) {
            float lmax = w - 200f * u;
            float lw = Math.min(Ui.measure(livesText, 30f * u, Ui.BOLD), lmax);
            Icons.draw(c, Icons.HEART, cx - lw / 2f - 30f * u, top + 104f * u, 16f * u, MissionsScreen.HEART);
            Ui.textFit(c, livesText, cx, top + 114f * u, 30f * u, lmax, MissionsScreen.HEART, Paint.Align.CENTER, Ui.BOLD);
        }

        // Skor kartı
        float cw = Math.min(w - 120f * u, 860f * u);
        float cardT = top + 150f * u, cardB = cardT + 400f * u;
        Ui.glow(c, cx, (cardT + cardB) / 2f, cw * 0.6f, won || newRecord ? Theme.GOLD : Theme.PURPLE, 0.25f);
        Ui.panel(c, cx - cw / 2f, cardT, cx + cw / 2f, cardB, 48f * u, Theme.alpha(Theme.PANEL, 0.95f), Theme.alpha(Theme.PURPLE, 0.6f), 3f * u);
        Ui.textFit(c, scoreLabel, cx, cardT + 80f * u, 34f * u, cw - 80f * u, Theme.MUTED, Paint.Align.CENTER, Ui.BOLD);
        Ui.text(c, Integer.toString((int) shownScore), cx, cardT + 230f * u, 150f * u, Theme.WHITE, Paint.Align.CENTER, Ui.NUMBERS);
        if (stars > 0) {
            for (int k = 0; k < 3; k++) {
                float sx = cx + (k - 1) * 90f * u, sy = cardT + 300f * u - (k == 1 ? 14f * u : 0f);
                float appear = Math.max(0f, Math.min(1f, (time - 0.3f - k * 0.25f) / 0.25f));
                boolean on = k < stars;
                if (on && appear > 0f) Ui.glow(c, sx, sy, 70f * u * appear, Theme.GOLD, 0.5f * appear);
                Icons.draw(c, Icons.STAR, sx, sy, (k == 1 ? 40f : 34f) * u * (on ? (0.6f + 0.4f * appear) : 1f),
                        on && appear > 0f ? Theme.GOLD : Theme.LINE);
            }
        } else if (newRecord) {
            float pulse = 0.75f + 0.25f * (float) Math.sin(time * 6f);
            float rs = 34f * u;
            // Hap, uzun dillerde metne göre genişler (en az eski 330 genişliği)
            float bw2 = Math.max(330f * u, Math.min(cw - 80f * u, Ui.measure(newRecordText, rs, Ui.BOLD) + 70f * u));
            float by = cardT + 280f * u;
            Ui.panel(c, cx - bw2 / 2f, by, cx + bw2 / 2f, by + 64f * u, 32f * u, Theme.alpha(Theme.GOLD, pulse), 0, 0);
            Ui.textFit(c, newRecordText, cx, Ui.centerY(by + 32f * u, rs), rs, bw2 - 50f * u, Theme.NAVY, Paint.Align.CENTER, Ui.BOLD);
        } else if (!bestText.isEmpty()) {
            Ui.textFit(c, bestText, cx, cardT + 320f * u, 36f * u, cw - 80f * u, Theme.GOLD, Paint.Align.CENTER, Ui.BOLD);
        }

        // İstatistikler
        float st = cardB + 40f * u, sw = cw / 4f;
        int statCount = Math.min(statLabels.length, statValues.length);
        for (int i = 0; i < statCount; i++) {
            float x = cx - cw / 2f + sw * i + sw / 2f;
            Ui.text(c, statValues[i], x, st + 60f * u, 56f * u, i == 1 ? Theme.GOLD : Theme.WHITE, Paint.Align.CENTER, Ui.NUMBERS);
            Ui.textFit(c, statLabels[i], x, st + 104f * u, 30f * u, sw - 16f * u, Theme.MUTED, Paint.Align.CENTER, Ui.MEDIUM);
        }
        // Ödül
        float ry = st + 190f * u;
        float rmax = cw - 110f * u;
        float rw = Math.min(Ui.measure(rewardText, 38f * u, Ui.BOLD), rmax) + 110f * u;
        Ui.panel(c, cx - rw / 2f, ry - 46f * u, cx + rw / 2f, ry + 46f * u, 46f * u, Theme.alpha(Theme.NAVY_DEEP, 0.8f), Theme.alpha(Theme.GOLD, 0.7f), 2.5f * u);
        Ui.diamond(c, cx - rw / 2f + 50f * u, ry, 20f * u, 1f);
        Ui.textFit(c, rewardText, cx + 25f * u, Ui.centerY(ry, 38f * u), 38f * u, rmax, Theme.GOLD, Paint.Align.CENTER, Ui.BOLD);

        // Seviye / XP
        float ly = ry + 100f * u;
        float bw3 = Math.min(cw, 620f * u);
        float xw = Math.min(Ui.measure(xpText, 30f * u, Ui.NUMBERS), bw3 / 2f);
        Ui.textFit(c, levelText, cx - bw3 / 2f, ly, 32f * u, bw3 - xw - 24f * u, levelUp ? Theme.GOLD : Theme.WHITE, Paint.Align.LEFT, Ui.BOLD);
        Ui.textFit(c, xpText, cx + bw3 / 2f, ly, 30f * u, bw3 / 2f, Theme.TEAL, Paint.Align.RIGHT, Ui.NUMBERS);
        Ui.panel(c, cx - bw3 / 2f, ly + 18f * u, cx + bw3 / 2f, ly + 34f * u, 8f * u, Theme.alpha(Theme.NAVY_DEEP, 0.9f), Theme.LINE, 2f * u);
        float xk = Math.min(1f, xpFrac * Math.min(1f, time / 0.8f));
        if (xk > 0f) Ui.panel(c, cx - bw3 / 2f, ly + 18f * u, cx - bw3 / 2f + bw3 * xk, ly + 34f * u, 8f * u, Theme.TEAL, 0, 0);
        ry = ly + 40f * u;
        // Karakter + gölge süsü
        float limitTop = videoBtn.visible ? videoBtn.rect.top : againBtn.rect.top;
        float dy = (limitTop + ry + 46f * u) / 2f;
        if (limitTop - (ry + 46f * u) > 160f * u) {
            WorldRenderer.drawSkinPreview(c, g.skin(), cx - 70f * u, dy + 10f * u, 40f * u, 0f, 1f, true);
            WorldRenderer.drawSkinPreview(c, g.skin(), cx + 70f * u, dy - 10f * u, 40f * u, (float) Math.sin(time * 2f) * 0.3f, 1f, false);
        }
        drawButtons(c);
    }
}
