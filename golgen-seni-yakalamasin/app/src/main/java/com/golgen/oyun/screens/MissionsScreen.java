package com.golgen.oyun.screens;

import android.graphics.Canvas;
import android.graphics.Paint;

import com.golgen.oyun.GameView;
import com.golgen.oyun.core.Achievements;
import com.golgen.oyun.core.DailyMissions;
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

/** Görevler: Görev Modu seviyeleri, Günlük görevler ve Başarılar sekmeleri. */
public final class MissionsScreen extends Screen {
    public static final int TAB_LEVELS = 0, TAB_DAILY = 1, TAB_ACH = 2;
    private final WorldRenderer bg = new WorldRenderer();
    private final Button back;
    private final Button[] tabs = new Button[3];
    private final Button[] claim = new Button[DailyMissions.PER_DAY];
    private int tab;
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
    private final String[] chapterTitle = new String[Missions.CHAPTERS];
    private final float[] levelY = new float[Missions.ALL.length];
    private final float[] chapterY = new float[Missions.CHAPTERS];
    private float levelsHeight, headerH;
    private final String[] chapterStarText = new String[Missions.CHAPTERS];

    public MissionsScreen(GameView g) {
        super(g);
        back = add(makeBackButton());
        String[] names = {"Görev Modu", "Günlük", "Başarılar"};
        for (int i = 0; i < 3; i++) {
            final int k = i;
            tabs[i] = add(new Button(names[i], Icons.NONE, Button.GHOST, () -> showTab(k)));
        }
        for (int i = 0; i < claim.length; i++) {
            final int k = i;
            claim[i] = add(new Button("ÖDÜLÜ AL", Icons.NONE, Button.GOLD, () -> {
                int got = g.progress.claimDaily(k);
                if (got > 0) {
                    g.sound.play(Synth.PURCHASE);
                    g.haptics.medium();
                    g.toast("+" + got + " elmas kazandın", Icons.CALENDAR);
                    g.save();
                }
                refresh();
                dumpTargets();
            }));
        }
        for (int c = 0; c < Missions.CHAPTERS; c++) {
            chapterTitle[c] = (c + 1) + ". BÖLÜM  ·  " + Missions.CHAPTER_NAMES[c].toUpperCase(new java.util.Locale("tr"));
        }
        for (int i = 0; i < Achievements.COUNT; i++) achReward[i] = "+" + Achievements.REWARDS[i];
        for (int i = 0; i < Missions.ALL.length; i++) {
            levelDesc[i] = Missions.ALL[i].description();
            levelReward[i] = Integer.toString(Missions.ALL[i].reward);
            levelNum[i] = Integer.toString(i % Missions.PER_CHAPTER + 1);
            levelGoal[i] = Missions.ALL[i].goal();
            levelMods[i] = Missions.ALL[i].modsText();
        }
    }

    public void showTab(int t) {
        tab = t;
        scroll = 0f;
        if (t == TAB_LEVELS && levelsHeight > 0f) {
            int cur = Math.min(Missions.ALL.length - 1, g.progress.missionsCompleted);
            scroll = clampScroll(levelY[cur] - (rowH + 16f * u) * 1.5f, levelsHeight, listBottom - listTop);
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
            claim[i].label = claimed ? "ALINDI" : (done ? "ÖDÜLÜ AL" : "+" + DailyMissions.reward(type, p.dailyDay));
            claim[i].style = claimed ? Button.GHOST : (done ? Button.GOLD : Button.SECONDARY);
        }
        for (int c = 0; c < Missions.CHAPTERS; c++) {
            chapterStarText[c] = p.chapterStars(c) + " / " + (Missions.PER_CHAPTER * 3);
        }
        long m = g.minutesToMidnight();
        resetText = "Yeni görevler " + (m / 60) + " sa " + (m % 60) + " dk sonra";
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
        listTop = tt + th + 30f * u;
        listBottom = h - safeB - 30f * u;
        rowH = 180f * u;
        headerH = 150f * u;
        float y = 0f;
        for (int c = 0; c < Missions.CHAPTERS; c++) {
            chapterY[c] = y;
            y += headerH;
            for (int j = 0; j < Missions.PER_CHAPTER; j++) {
                levelY[c * Missions.PER_CHAPTER + j] = y;
                y += rowH + 16f * u;
            }
            y += 24f * u;
        }
        levelsHeight = y;
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
            float cy = listTop + levelY[i] + rowH / 2f - scroll;
            if (cy > listTop && cy < listBottom) g.debugTarget(screen, "seviye-" + i, (colL + colR) / 2f, cy);
        }
    }

    @Override
    protected void onTap(float x, float y) {
        if (tab != TAB_LEVELS || y < listTop || y > listBottom || x < colL || x > colR) return;
        float yy = y - listTop + scroll;
        int idx = -1;
        for (int i = 0; i < Missions.ALL.length; i++) {
            if (yy >= levelY[i] && yy <= levelY[i] + rowH) { idx = i; break; }
        }
        if (idx < 0) return;
        if (g.progress.missionUnlocked(idx)) {
            g.sound.play(Synth.TAP);
            g.startMission(idx);
        } else {
            g.toast("Önce " + idx + ". görevi tamamla", Icons.LOCK);
        }
    }

    @Override
    public boolean onBack() {
        g.goMenu();
        return true;
    }

    @Override
    public void update(float dt) {
        super.update(dt);
        bg.update(dt, 0f);
        if (!isDragging() && Math.abs(scrollVel) > 5f) {
            scroll = clampScroll(scroll - scrollVel * dt, contentHeight(), listBottom - listTop);
            scrollVel *= (float) Math.pow(0.05, dt);
        }
    }

    @Override
    public void draw(Canvas c) {
        bg.drawBackground(c, w, h, time * 40f);
        drawHeader(c, "GÖREVLER");
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

    private void drawLevels(Canvas c) {
        Progress p = g.progress;
        for (int ch = 0; ch < Missions.CHAPTERS; ch++) {
            float top = listTop + chapterY[ch] - scroll;
            if (top > listBottom || top + headerH < listTop - 20f * u) continue;
            boolean open = p.missionUnlocked(ch * Missions.PER_CHAPTER);
            Ui.textFit(c, chapterTitle[ch], colL + 10f * u, top + 62f * u,
                    40f * u, colR - colL - 260f * u, open ? Theme.TEAL : Theme.MUTED, Paint.Align.LEFT, Ui.DISPLAY);
            Ui.text(c, Missions.CHAPTER_DESCS[ch], colL + 10f * u, top + 108f * u, 28f * u, Theme.MUTED, Paint.Align.LEFT, Ui.MEDIUM);
            float tw = Ui.measure(chapterStarText[ch], 32f * u, Ui.NUMBERS);
            Ui.text(c, chapterStarText[ch], colR - 10f * u, top + 74f * u, 32f * u, Theme.GOLD, Paint.Align.RIGHT, Ui.NUMBERS);
            Icons.draw(c, Icons.STAR, colR - 34f * u - tw, top + 63f * u, 20f * u, Theme.GOLD);
        }
        for (int i = 0; i < Missions.ALL.length; i++) {
            float top = listTop + levelY[i] - scroll;
            if (top > listBottom || top + rowH < listTop - 20f * u) continue;
            MissionDef m = Missions.ALL[i];
            boolean unlocked = p.missionUnlocked(i);
            boolean done = i < p.missionsCompleted;
            boolean current = unlocked && !done;
            int border = current ? Theme.PURPLE : (m.boss ? Theme.alpha(Theme.GOLD, unlocked ? 0.7f : 0.3f) : (done ? Theme.alpha(Theme.TEAL, 0.5f) : Theme.LINE));
            Ui.panel(c, colL, top, colR, top + rowH, 36f * u, Theme.alpha(Theme.CARD, unlocked ? 0.95f : 0.6f), border, current ? 4f * u : 2f * u);
            float cy = top + rowH / 2f;
            float nx = colL + 82f * u;
            Ui.fill.setShader(null);
            Ui.fill.setColor(done ? Theme.TEAL : (current ? Theme.PURPLE : Theme.LINE));
            c.drawCircle(nx, cy, 46f * u, Ui.fill);
            if (!unlocked) Icons.draw(c, Icons.LOCK, nx, cy, 22f * u, Theme.MUTED);
            else Ui.text(c, levelNum[i], nx, Ui.centerY(cy, 40f * u), 40f * u, done ? Theme.NAVY : Theme.WHITE, Paint.Align.CENTER, Ui.NUMBERS);
            int tc = unlocked ? Theme.WHITE : Theme.MUTED;
            float textL = colL + 150f * u, textW = colR - colL - 360f * u;
            Ui.textFit(c, m.title, textL, cy - 26f * u, 38f * u, textW, m.boss ? Theme.GOLD : tc, Paint.Align.LEFT, Ui.BOLD);
            Ui.textFit(c, levelGoal[i], textL, cy + 16f * u, 29f * u, textW, Theme.MUTED, Paint.Align.LEFT, Ui.MEDIUM);
            if (!levelMods[i].isEmpty()) {
                Ui.textFit(c, levelMods[i], textL, cy + 54f * u, 26f * u, textW, Theme.alpha(Theme.PURPLE_LIGHT, unlocked ? 1f : 0.5f), Paint.Align.LEFT, Ui.BOLD);
            }
            // Yıldızlar
            int stars = p.missionStars[i];
            float sx = colR - 160f * u;
            for (int k = 0; k < 3; k++) {
                Icons.draw(c, Icons.STAR, sx + k * 46f * u, cy - 22f * u, 20f * u, k < stars ? Theme.GOLD : Theme.alpha(Theme.LINE, 1f));
            }
            // Ödül
            float rx = colR - 40f * u;
            Ui.text(c, levelReward[i], rx, Ui.centerY(cy + 34f * u, 34f * u), 34f * u, unlocked ? Theme.GOLD : Theme.MUTED, Paint.Align.RIGHT, Ui.NUMBERS);
            float tw = Ui.measure(levelReward[i], 34f * u, Ui.NUMBERS);
            Ui.diamond(c, rx - tw - 24f * u, cy + 34f * u, 15f * u, unlocked ? 1f : 0.4f);
        }
    }

    private void drawDaily(Canvas c) {
        Progress p = g.progress;
        Ui.text(c, resetText, w / 2f, listTop + 10f * u, 30f * u, Theme.MUTED, Paint.Align.CENTER, Ui.MEDIUM);
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
            Ui.text(c, Achievements.TITLES[i], colL + 150f * u, cy - 10f * u, 40f * u, has ? Theme.WHITE : Theme.MUTED, Paint.Align.LEFT, Ui.BOLD);
            Ui.textFit(c, Achievements.DESCS[i], colL + 150f * u, cy + 40f * u, 30f * u, colR - colL - 330f * u, Theme.MUTED, Paint.Align.LEFT, Ui.MEDIUM);
            Ui.text(c, has ? "Kazanıldı" : achReward[i], colR - 40f * u, Ui.centerY(cy, 34f * u), 34f * u, has ? Theme.TEAL : Theme.GOLD, Paint.Align.RIGHT, Ui.BOLD);
        }
    }
}
