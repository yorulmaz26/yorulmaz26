package com.golgen.oyun.screens;

import android.graphics.Canvas;
import android.graphics.Paint;

import com.golgen.oyun.GameView;
import com.golgen.oyun.core.Achievements;
import com.golgen.oyun.core.DailyMissions;
import com.golgen.oyun.core.Energy;
import com.golgen.oyun.core.L10n;
import com.golgen.oyun.core.Maps;
import com.golgen.oyun.core.MissionDef;
import com.golgen.oyun.core.Missions;
import com.golgen.oyun.core.Progress;
import com.golgen.oyun.core.Synth;
import com.golgen.oyun.ui.Button;
import com.golgen.oyun.ui.Icons;
import com.golgen.oyun.ui.Screen;
import com.golgen.oyun.ui.Theme;
import com.golgen.oyun.ui.Ui;
import com.golgen.oyun.ui.WorldRenderer;

/** Görevler: Haritalar (500 bölüm), Günlük görevler ve Başarılar sekmeleri. */
public final class MissionsScreen extends Screen {
    public static final int TAB_LEVELS = 0, TAB_DAILY = 1, TAB_ACH = 2;
    private final WorldRenderer bg = new WorldRenderer();
    private final Button back;
    private final Button[] tabs = new Button[3];
    private final Button[] claim = new Button[DailyMissions.PER_DAY];
    private int tab;
    private final Button refillBtn;
    private final Button videoLifeBtn;
    private float livesY;
    static final int HEART = 0xFFFF6B8B;
    private float scroll;
    private float listTop, listBottom, rowH, colL, colR;
    private final String[] levelDesc = new String[Missions.ALL.length];
    private final String[] levelReward = new String[Missions.ALL.length];
    private final String[] levelNum = new String[Missions.ALL.length];
    private final String[] levelGoal = new String[Missions.ALL.length];
    private final String[] levelMods = new String[Missions.ALL.length];
    private final String[] dailyText = new String[DailyMissions.PER_DAY];
    private final String[] dailyProg = new String[DailyMissions.PER_DAY];
    private String resetText = "";
    private final String[] achReward = new String[Achievements.COUNT];
    private final String[] achTitle = new String[Achievements.COUNT];
    private final String[] achDesc = new String[Achievements.COUNT];
    private String title = "", achEarned = "";
    private final String[] chapterTitle = new String[Missions.CHAPTERS];
    private final float[] levelY = new float[Missions.ALL.length];
    private final float[] chapterY = new float[Missions.CHAPTERS];
    private float levelsHeight, headerH;
    private final String[] chapterStarText = new String[Missions.CHAPTERS];
    private final String[] mapDesc = new String[Missions.MAPS];
    private final android.graphics.Shader[] cardShader = new android.graphics.Shader[Missions.MAPS];
    private final android.graphics.Matrix cardMatrix = new android.graphics.Matrix();
    private float cardH, nodeR;
    private final float[] nodeX = new float[Missions.PER_MAP], nodeDY = new float[Missions.PER_MAP];

    public MissionsScreen(GameView g) {
        super(g);
        back = add(makeBackButton());
        String[] tabKeys = {"missions.tab.maps", "missions.tab.daily", "missions.tab.achievements"};
        for (int i = 0; i < 3; i++) {
            final int k = i;
            tabs[i] = add(new Button(null, Icons.NONE, Button.GHOST, () -> showTab(k)).key(tabKeys[i]));
        }
        for (int i = 0; i < claim.length; i++) {
            final int k = i;
            claim[i] = add(new Button(null, Icons.NONE, Button.GOLD, () -> {
                int got = g.progress.claimDaily(k);
                if (got > 0) {
                    g.sound.play(Synth.PURCHASE);
                    g.haptics.medium();
                    g.toast(L10n.p("missions.daily.reward_toast", got), Icons.CALENDAR);
                    g.save();
                }
                refresh();
                dumpTargets();
            }));
            claim[i].id = "gorev-odul-" + i;   // etiket dinamik (ÖDÜLÜ AL / ALINDI / +N): sabit test kimliği
        }
        refillBtn = add(new Button(null, Icons.HEART, Button.GOLD, () -> {
            if (g.progress.buyRefill(g.clock)) {
                g.sound.play(Synth.PURCHASE);
                g.haptics.medium();
                g.noteLivesChanged();
                g.toast(L10n.t("lives.refilled"), Icons.HEART);
                g.save();
            } else {
                g.toast(L10n.f("lives.need_diamonds", Progress.REFILL_COST), Icons.LOCK);
            }
            dumpTargets();
        }));
        refillBtn.id = "can-doldur";
        videoLifeBtn = add(new Button(null, Icons.PLAY, Button.PRIMARY, () -> {
            if (!g.progress.canWatchForLife(g.epochDay(), g.clock)) {
                g.toast(L10n.t("lives.video_none_left"), Icons.LOCK);
                return;
            }
            g.ads.showRewarded(() -> {
                if (g.progress.onWatchedForLife(g.epochDay(), g.clock)) {
                    g.sound.play(Synth.SUCCESS);
                    g.noteLivesChanged();
                    g.toast(L10n.p("lives.video_reward", g.progress.rewardedLivesLeft(g.epochDay())), Icons.HEART);
                    g.save();
                }
            }, this::dumpTargets);
        }).key("lives.video_short"));
        videoLifeBtn.id = "video-can";
        videoLifeBtn.visible = false;
        for (int i = 0; i < Achievements.COUNT; i++) achReward[i] = "+" + Achievements.REWARDS[i];
        for (int i = 0; i < Missions.ALL.length; i++) {
            levelReward[i] = Integer.toString(Missions.ALL[i].reward);
            levelNum[i] = Integer.toString(i + 1);
        }
    }

    /** Dil değişince (ve ilk kurulumda) önbellekli metinler yeniden üretilir. */
    @Override
    protected void onLocalize() {
        title = L10n.t("missions.title");
        achEarned = L10n.t("missions.ach.earned");
        for (int c = 0; c < Missions.MAPS; c++) {
            chapterTitle[c] = L10n.f("missions.map_title", c + 1, L10n.upper(Maps.ALL[c].name()));
            mapDesc[c] = Maps.ALL[c].desc() + "  ·  " + Maps.ALL[c].featuresText();
        }
        for (int i = 0; i < Achievements.COUNT; i++) {
            achTitle[i] = Achievements.title(i);
            achDesc[i] = Achievements.desc(i);
        }
        for (int i = 0; i < Missions.ALL.length; i++) {
            levelDesc[i] = Missions.ALL[i].description();
            levelGoal[i] = Missions.ALL[i].goal();
            levelMods[i] = Missions.ALL[i].modsText();
        }
        refresh();   // günlük görev metinleri, ödül butonları ve sıfırlama geri sayımı
    }

    public void showTab(int t) {
        tab = t;
        scroll = 0f;
        if (t == TAB_LEVELS && levelsHeight > 0f) {
            int cur = Math.min(Missions.ALL.length - 1, g.progress.missionsCompleted);
            scroll = clampScroll(chapterY[Missions.mapOf(cur)] - 40f * u, levelsHeight, listBottom - listTop);
        }
        scrollVel = 0f;
        for (int i = 0; i < 3; i++) tabs[i].style = i == t ? Button.PRIMARY : Button.GHOST;
        refresh();
        if (w > 0) dumpTargets();
    }

    @Override
    public void enter() {
        super.enter();
        g.progress.ensureDaily(g.epochDay());
        showTab(tab);
    }

    private void refresh() {
        Progress p = g.progress;
        for (int i = 0; i < DailyMissions.PER_DAY; i++) {
            int type = p.dailyTypes[i];
            int target = DailyMissions.target(type, p.dailyDay);
            dailyText[i] = DailyMissions.text(type, target);
            dailyProg[i] = Math.min(target, p.dailyProgress[i]) + " / " + target;
            claim[i].visible = tab == TAB_DAILY;
            boolean done = p.dailyDone(i), claimed = p.dailyIsClaimed(i);
            claim[i].enabled = done && !claimed;
            claim[i].label = claimed ? L10n.t("missions.daily.claimed")
                    : (done ? L10n.t("missions.daily.claim") : "+" + DailyMissions.reward(type, p.dailyDay));
            claim[i].style = claimed ? Button.GHOST : (done ? Button.GOLD : Button.SECONDARY);
        }
        for (int c = 0; c < Missions.CHAPTERS; c++) {
            chapterStarText[c] = p.chapterStars(c) + " / " + (Missions.PER_MAP * 3);
        }
        long m = g.minutesToMidnight();
        resetText = L10n.f("missions.daily.reset", m / 60, m % 60);
        layoutClaims();
    }

    @Override
    protected void layout() {
        layoutBack(back);
        bg.s = w / 1000f;
        float cw = Math.min(w - 80f * u, 940f * u);
        colL = (w - cw) / 2f;
        colR = colL + cw;
        float tt = headerBottom() + 10f * u, th = 96f * u, tw = cw / 3f;
        for (int i = 0; i < 3; i++) tabs[i].set(colL + tw * i + 6f * u, tt, colL + tw * (i + 1) - 6f * u, tt + th);
        livesY = tt + th + 62f * u;
        refillBtn.set(colR - 250f * u, livesY - 42f * u, colR, livesY + 42f * u);
        videoLifeBtn.set(colR - 520f * u, livesY - 42f * u, colR - 266f * u, livesY + 42f * u);
        listTop = tt + th + 130f * u;
        listBottom = h - safeB - 30f * u;
        rowH = 180f * u;
        cardH = 470f * u;
        nodeR = 52f * u;
        float y = 0f;
        for (int c = 0; c < Missions.MAPS; c++) {
            chapterY[c] = y;
            for (int j = 0; j < Missions.PER_MAP; j++) levelY[c * Missions.PER_MAP + j] = y;
            y += cardH + 28f * u;
            cardShader[c] = new android.graphics.LinearGradient(0, 0, 0, cardH, Maps.ALL[c].bgTop,
                    Theme.lerp(Maps.ALL[c].bgBottom, Maps.ALL[c].obstacle, 0.18f), android.graphics.Shader.TileMode.CLAMP);
        }
        levelsHeight = y;
        float pad = 100f * u, span = (colR - colL) - 2f * pad;
        for (int k = 0; k < Missions.PER_MAP; k++) {
            nodeX[k] = colL + pad + span * k / (Missions.PER_MAP - 1);
            nodeDY[k] = 250f * u + ((k % 2 == 0) ? 0f : 100f * u);
        }
        layoutClaims();
    }

    private void layoutClaims() {
        for (int i = 0; i < claim.length; i++) {
            float bottom = listTop + i * (rowH + 70f * u) + 40f * u + rowH + 30f * u;
            claim[i].set(colR - 280f * u, bottom - 120f * u, colR - 40f * u, bottom - 30f * u);
        }
    }

    private float contentHeight() {
        switch (tab) {
            case TAB_LEVELS: return levelsHeight;
            case TAB_ACH: return Achievements.COUNT * (150f * u + 16f * u);
            default: return 0f;
        }
    }

    @Override
    protected void onDrag(float dy) {
        scroll = clampScroll(scroll - dy, contentHeight(), listBottom - listTop);
    }

    @Override
    protected void dumpExtraTargets(String screen) {
        if (tab != TAB_LEVELS) return;
        for (int i = 0; i < Missions.ALL.length; i++) {
            float cy = listTop + chapterY[Missions.mapOf(i)] + nodeDY[i % Missions.PER_MAP] - scroll;
            if (cy > listTop + nodeR && cy < listBottom - nodeR) g.debugTarget(screen, "bolum-" + i, nodeX[i % Missions.PER_MAP], cy);
        }
    }

    @Override
    protected void onTap(float x, float y) {
        if (tab != TAB_LEVELS || y < listTop || y > listBottom || x < colL || x > colR) return;
        float yy = y - listTop + scroll;
        int idx = -1;
        float hit = (nodeR + 26f * u) * (nodeR + 26f * u);
        for (int i = 0; i < Missions.ALL.length && idx < 0; i++) {
            float nx = nodeX[i % Missions.PER_MAP], ny = chapterY[Missions.mapOf(i)] + nodeDY[i % Missions.PER_MAP];
            float dx = x - nx, dy = yy - ny;
            if (dx * dx + dy * dy < hit) idx = i;
        }
        if (idx < 0) return;
        if (g.progress.missionUnlocked(idx)) {
            g.sound.play(Synth.TAP);
            g.startMission(idx);
        } else {
            g.toast(L10n.f("missions.locked_toast", idx), Icons.LOCK);
        }
    }

    @Override
    public boolean wantsBanner() { return true; }

    @Override
    public boolean onBack() {
        g.goMenu();
        return true;
    }

    @Override
    public void update(float dt) {
        super.update(dt);
        bg.update(dt, 0f);
        refillBtn.visible = tab == TAB_LEVELS && g.lives() < Energy.MAX;
        if (refillLang != L10n.version()) { refillLang = L10n.version(); refillBtn.label = L10n.f("lives.refill_short", Progress.REFILL_COST); }
        boolean vl = refillBtn.visible && g.ads.isRewardedReady() && g.progress.canWatchForLife(g.epochDay(), g.clock);
        if (vl != videoLifeBtn.visible) { videoLifeBtn.visible = vl; dumpTargets(); }
        if (!isDragging() && Math.abs(scrollVel) > 5f) {
            scroll = clampScroll(scroll - scrollVel * dt, contentHeight(), listBottom - listTop);
            scrollVel *= (float) Math.pow(0.05, dt);
        }
    }

    @Override
    public void draw(Canvas c) {
        bg.drawBackground(c, w, h, time * 40f);
        drawHeader(c, title);
        if (tab == TAB_LEVELS) drawLives(c);
        c.save();
        c.clipRect(0, listTop - 10f * u, w, listBottom);
        switch (tab) {
            case TAB_LEVELS: drawLevels(c); break;
            case TAB_DAILY: drawDaily(c); break;
            default: drawAchievements(c); break;
        }
        c.restore();
        drawButtons(c);
    }

    private int livesShown = -1, livesLang = -1, refillLang = -1;
    private String livesCount = "";

    /** ❤ 3/4 + "Sonraki can: 19:42" (dk:sn, canlı) / "Canlar dolu"; can yenilenince kalp atar. */
    private void drawLives(Canvas c) {
        int lives = g.lives();
        if (lives != livesShown || livesLang != L10n.version()) {
            livesShown = lives;
            livesLang = L10n.version();
            livesCount = L10n.f("lives.count", lives, Energy.MAX);
        }
        float pulse = g.livesPulse;
        float hy = livesY - 18f * u;
        for (int i = 0; i < Energy.MAX; i++) {
            float x = colL + 34f * u + i * 58f * u;
            boolean full = i < lives;
            float sz = 22f * u * (full && i == lives - 1 ? 1f + 0.45f * pulse : 1f);
            if (full && i == lives - 1 && pulse > 0f) Ui.glow(c, x, hy, 70f * u * (1f + pulse), HEART, 0.5f * pulse);
            Icons.draw(c, Icons.HEART, x, hy, sz, full ? HEART : Theme.alpha(Theme.LINE, 0.9f));
        }
        float cx = colL + 34f * u + Energy.MAX * 58f * u - 10f * u;
        Ui.text(c, livesCount, cx, Ui.centerY(hy, 40f * u), 40f * u, lives > 0 ? Theme.WHITE : HEART, Paint.Align.LEFT, Ui.NUMBERS);
        String t = lives >= Energy.MAX ? L10n.t("lives.full") : L10n.f("lives.next", g.energyTimer());
        float right = videoLifeBtn.visible ? videoLifeBtn.rect.left : (refillBtn.visible ? refillBtn.rect.left : colR);
        Ui.textFit(c, t, colL + 10f * u, livesY + 40f * u, 30f * u, right - colL - 30f * u,
                lives > 0 ? (lives >= Energy.MAX ? Theme.TEAL : Theme.WHITE) : HEART, Paint.Align.LEFT, Ui.BOLD);
    }

    private void drawLevels(Canvas c) {
        Progress p = g.progress;
        for (int m = 0; m < Missions.MAPS; m++) {
            float top = listTop + chapterY[m] - scroll;
            if (top > listBottom || top + cardH < listTop - 20f * u) continue;
            com.golgen.oyun.core.MapTheme th = Maps.ALL[m];
            boolean open = p.missionUnlocked(m * Missions.PER_MAP);
            // Kart: harita renkleriyle
            cardMatrix.setTranslate(0, top);
            cardShader[m].setLocalMatrix(cardMatrix);
            Ui.rf.set(colL, top, colR, top + cardH);
            Ui.fill.setShader(cardShader[m]);
            c.drawRoundRect(Ui.rf, 40f * u, 40f * u, Ui.fill);
            Ui.fill.setShader(null);
            Ui.stroke.setColor(Theme.alpha(th.obstacle, open ? 0.8f : 0.3f));
            Ui.stroke.setStrokeWidth(3f * u);
            c.drawRoundRect(Ui.rf, 40f * u, 40f * u, Ui.stroke);
            Ui.textFit(c, chapterTitle[m], colL + 40f * u, top + 70f * u, 38f * u, colR - colL - 300f * u,
                    open ? Theme.WHITE : Theme.MUTED, Paint.Align.LEFT, Ui.DISPLAY);
            Ui.textFit(c, mapDesc[m], colL + 40f * u, top + 116f * u, 27f * u, colR - colL - 80f * u,
                    Theme.alpha(open ? th.accent : Theme.MUTED, 0.9f), Paint.Align.LEFT, Ui.MEDIUM);
            float tw = Ui.measure(chapterStarText[m], 32f * u, Ui.NUMBERS);
            Ui.text(c, chapterStarText[m], colR - 36f * u, top + 72f * u, 32f * u, Theme.GOLD, Paint.Align.RIGHT, Ui.NUMBERS);
            Icons.draw(c, Icons.STAR, colR - 60f * u - tw, top + 61f * u, 20f * u, Theme.GOLD);
            // Yol
            Ui.stroke.setStrokeWidth(10f * u);
            for (int k = 0; k < Missions.PER_MAP - 1; k++) {
                int idx = m * Missions.PER_MAP + k;
                boolean done = idx < p.missionsCompleted;
                Ui.stroke.setColor(Theme.alpha(done ? th.accent : Theme.LINE, done ? 0.8f : 0.6f));
                c.drawLine(nodeX[k], top + nodeDY[k], nodeX[k + 1], top + nodeDY[k + 1], Ui.stroke);
            }
            // Düğümler
            for (int k = 0; k < Missions.PER_MAP; k++) {
                int idx = m * Missions.PER_MAP + k;
                boolean unlocked = p.missionUnlocked(idx);
                boolean done = idx < p.missionsCompleted;
                boolean current = unlocked && !done;
                boolean boss = k == Missions.PER_MAP - 1;
                float x = nodeX[k], y = top + nodeDY[k], r = boss ? nodeR * 1.18f : nodeR;
                if (current) Ui.glow(c, x, y, r * 2.6f, th.accent, 0.4f + 0.2f * (float) Math.sin(time * 4f));
                Ui.fill.setColor(done ? th.accent : (current ? Theme.PURPLE : Theme.alpha(Theme.NAVY_DEEP, 0.9f)));
                c.drawCircle(x, y, r, Ui.fill);
                Ui.stroke.setStrokeWidth(4f * u);
                Ui.stroke.setColor(boss ? Theme.GOLD : (unlocked ? Theme.WHITE : Theme.LINE));
                c.drawCircle(x, y, r, Ui.stroke);
                if (!unlocked) Icons.draw(c, Icons.LOCK, x, y, 22f * u, Theme.MUTED);
                else Ui.text(c, levelNum[idx], x, Ui.centerY(y, 38f * u), 38f * u, done ? Theme.NAVY : Theme.WHITE, Paint.Align.CENTER, Ui.NUMBERS);
                // Taçlar
                int crowns = p.missionStars[idx];
                for (int q = 0; q < 3; q++) {
                    Icons.draw(c, Icons.STAR, x + (q - 1) * 30f * u, y + r + 26f * u, 13f * u, q < crowns ? Theme.GOLD : Theme.alpha(Theme.LINE, 0.9f));
                }
            }
        }
    }

    private void drawDaily(Canvas c) {
        Progress p = g.progress;
        Ui.textFit(c, resetText, w / 2f, listTop + 10f * u, 30f * u, colR - colL, Theme.MUTED, Paint.Align.CENTER, Ui.MEDIUM);
        for (int i = 0; i < DailyMissions.PER_DAY; i++) {
            float top = listTop + i * (rowH + 70f * u) + 40f * u;
            float bottom = top + rowH + 30f * u;
            boolean done = p.dailyDone(i);
            Ui.panel(c, colL, top, colR, bottom, 36f * u, Theme.alpha(Theme.CARD, 0.95f), done ? Theme.GOLD : Theme.LINE, done ? 3.5f * u : 2f * u);
            Icons.draw(c, Icons.CALENDAR, colL + 70f * u, top + 70f * u, 28f * u, done ? Theme.GOLD : Theme.TEAL);
            Ui.textFit(c, dailyText[i], colL + 130f * u, top + 82f * u, 40f * u, colR - colL - 200f * u, Theme.WHITE, Paint.Align.LEFT, Ui.BOLD);
            int type = p.dailyTypes[i];
            int target = DailyMissions.target(type, p.dailyDay);
            float prog = Math.min(1f, p.dailyProgress[i] / (float) target);
            float bl = colL + 50f * u, br = colR - 320f * u, by = bottom - 75f * u;
            Ui.panel(c, bl, by, br, by + 22f * u, 11f * u, Theme.alpha(Theme.NAVY_DEEP, 0.9f), Theme.LINE, 2f * u);
            if (prog > 0f) Ui.panel(c, bl, by, bl + (br - bl) * prog, by + 22f * u, 11f * u, done ? Theme.GOLD : Theme.TEAL, 0, 0);
            Ui.text(c, dailyProg[i], br, by - 16f * u, 28f * u, Theme.MUTED, Paint.Align.RIGHT, Ui.MEDIUM);
        }
    }

    private void drawAchievements(Canvas c) {
        Progress p = g.progress;
        float rh = 150f * u;
        for (int i = 0; i < Achievements.COUNT; i++) {
            float top = listTop + i * (rh + 16f * u) - scroll;
            if (top > listBottom || top + rh < listTop - 20f * u) continue;
            boolean has = p.hasAchievement(i);
            Ui.panel(c, colL, top, colR, top + rh, 32f * u, Theme.alpha(Theme.CARD, has ? 0.95f : 0.6f), has ? Theme.alpha(Theme.GOLD, 0.7f) : Theme.LINE, 2f * u);
            float cy = top + rh / 2f;
            if (has) {
                Ui.glow(c, colL + 80f * u, cy, 80f * u, Theme.GOLD, 0.4f);
                Icons.draw(c, Icons.STAR, colL + 80f * u, cy, 36f * u, Theme.GOLD);
            } else {
                Icons.draw(c, Icons.LOCK, colL + 80f * u, cy, 26f * u, Theme.MUTED);
            }
            Ui.textFit(c, achTitle[i], colL + 150f * u, cy - 10f * u, 40f * u, colR - colL - 330f * u, has ? Theme.WHITE : Theme.MUTED, Paint.Align.LEFT, Ui.BOLD);
            Ui.textFit(c, achDesc[i], colL + 150f * u, cy + 40f * u, 30f * u, colR - colL - 330f * u, Theme.MUTED, Paint.Align.LEFT, Ui.MEDIUM);
            Ui.textFit(c, has ? achEarned : achReward[i], colR - 40f * u, Ui.centerY(cy, 34f * u), 34f * u, 180f * u, has ? Theme.TEAL : Theme.GOLD, Paint.Align.RIGHT, Ui.BOLD);
        }
    }
}
