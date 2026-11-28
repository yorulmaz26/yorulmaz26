package com.golgen.oyun.screens;

import android.graphics.Canvas;
import android.graphics.Paint;

import com.golgen.oyun.GameView;
import com.golgen.oyun.core.Achievements;
import com.golgen.oyun.core.Energy;
import com.golgen.oyun.core.L10n;
import com.golgen.oyun.core.Missions;
import com.golgen.oyun.core.Progress;
import com.golgen.oyun.core.Synth;
import com.golgen.oyun.ui.Button;
import com.golgen.oyun.ui.Icons;
import com.golgen.oyun.ui.Screen;
import com.golgen.oyun.ui.Theme;
import com.golgen.oyun.ui.Ui;
import com.golgen.oyun.ui.WorldRenderer;

/** Ana menü: canlı arka plan, oyuncu-gölge gösterisi ve tüm ekranlara erişim. */
public final class MenuScreen extends Screen {
    private final WorldRenderer bg = new WorldRenderer();
    private final Button playBtn, missionBtn, skinsBtn, tasksBtn, recordsBtn, settingsBtn, helpBtn;
    private String diamondText = "0", bestText = "";
    private int lastDiamonds = -1, lastBest = -1;
    private float scroll;
    private int lastLives = -1;
    private String lastTimer = "";
    private final Button loginBtn;
    private boolean loginModal;
    private int loginDay;
    private float modalT;
    private String levelText = "";
    private int lastLevel = -1, lastXp = -1;
    private int lastLang = -1;
    private final String[] dayText = new String[Progress.LOGIN_REWARDS.length];
    private String loginTitle = "", loginDesc = "";
    private String[] loginDescLines = new String[0];
    private float loginDescSize;
    private final float[] fit = new float[1];
    private static final String[] DAY_REWARD = new String[Progress.LOGIN_REWARDS.length];
    static { for (int i = 0; i < DAY_REWARD.length; i++) DAY_REWARD[i] = Integer.toString(Progress.LOGIN_REWARDS[i]); }

    public MenuScreen(GameView g) {
        super(g);
        playBtn = add(new Button(null, Icons.PLAY, Button.PRIMARY, g::startEndless).key("menu.play"));
        missionBtn = add(new Button(null, Icons.TARGET, Button.SECONDARY, () -> {
            g.missions.showTab(MissionsScreen.TAB_LEVELS);
            g.go(g.missions);
        }));
        missionBtn.id = "haritalar";
        skinsBtn = add(new Button(null, Icons.PERSON, Button.SECONDARY, () -> g.go(g.skins)).key("menu.character"));
        tasksBtn = add(new Button(null, Icons.CALENDAR, Button.SECONDARY, () -> {
            g.missions.showTab(MissionsScreen.TAB_DAILY);
            g.go(g.missions);
        }).key("menu.tasks"));
        recordsBtn = add(new Button(null, Icons.TROPHY, Button.SECONDARY, () -> g.go(g.records)).key("menu.records"));
        skinsBtn.vertical = tasksBtn.vertical = recordsBtn.vertical = true;
        loginBtn = add(new Button(null, Icons.GIFT, Button.GOLD, this::claimLogin).key("login.claim"));
        loginBtn.id = "gunluk-odul";
        loginBtn.visible = false;
        settingsBtn = add(new Button(null, Icons.GEAR, Button.ICON, () -> g.go(g.settings)));
        helpBtn = add(new Button(null, Icons.BOOK, Button.ICON, () -> {
            g.tutorial.setFromSettings(true);
            g.go(g.tutorial);
        }));
    }

    @Override
    protected void onLocalize() {
        playBtn.sub = L10n.t("menu.play_sub");
        missionBtn.label = L10n.p("menu.maps", Missions.ALL.length);
        for (int i = 0; i < dayText.length; i++) dayText[i] = L10n.f("login.day", i + 1);
        loginTitle = L10n.t("login.title");
        loginDesc = L10n.t("login.desc");
        if (w > 0) wrapLoginDesc();
        lastLang = -1;          // sayılı metinler (skor, seviye, can) bir sonraki refresh'te yeniden üretilir
    }

    private void wrapLoginDesc() {
        float pw = Math.min(w - 60f * u, 960f * u);
        loginDescLines = Ui.wrapFit(loginDesc, 30f * u, pw - 80f * u, 2, Ui.MEDIUM, fit);
        loginDescSize = fit[0];
    }

    @Override
    protected void layout() {
        float cx = w / 2f;
        float colW = Math.min(w - 120f * u, 860f * u);
        float bottom = h - safeB - 70f * u;
        float smallH = 200f * u;
        float gap = 28f * u;
        float sw = (colW - gap * 2f) / 3f;
        float rowTop = bottom - smallH;
        skinsBtn.set(cx - colW / 2f, rowTop, cx - colW / 2f + sw, bottom);
        tasksBtn.set(cx - sw / 2f, rowTop, cx + sw / 2f, bottom);
        recordsBtn.set(cx + colW / 2f - sw, rowTop, cx + colW / 2f, bottom);
        float mh = 130f * u;
        missionBtn.set(cx - colW / 2f, rowTop - gap * 1.4f - mh, cx + colW / 2f, rowTop - gap * 1.4f);
        float ph = 190f * u;
        playBtn.set(cx - colW / 2f, missionBtn.rect.top - gap * 1.4f - ph, cx + colW / 2f, missionBtn.rect.top - gap * 1.4f);
        float s = 112f * u;
        settingsBtn.set(w - safeR - 40f * u - s, safeT + 32f * u, w - safeR - 40f * u, safeT + 32f * u + s);
        helpBtn.set(settingsBtn.rect.left - 24f * u - s, settingsBtn.rect.top, settingsBtn.rect.left - 24f * u, settingsBtn.rect.bottom);
        bg.s = w / 1000f;
        float bw = Math.min(w - 200f * u, 600f * u);
        loginBtn.set(w / 2f - bw / 2f, h * 0.62f, w / 2f + bw / 2f, h * 0.62f + 130f * u);
        wrapLoginDesc();
    }

    private void setModal(boolean on) {
        loginModal = on;
        loginBtn.visible = on;
        for (int i = 0; i < buttons.size(); i++) {
            Button b = buttons.get(i);
            if (b != loginBtn) b.enabled = !on;
        }
    }

    private void claimLogin() {
        int r = g.progress.claimLogin(g.epochDay());
        if (r > 0) {
            g.sound.play(Synth.SUCCESS);
            g.haptics.medium();
            g.toast(L10n.p("login.toast", r), Icons.GIFT);
            int gained = g.progress.checkAchievements(null, null);
            for (int id = 0; id < Achievements.COUNT; id++) {
                if ((gained & (1 << id)) != 0) g.toast(L10n.f("menu.achievement_toast", Achievements.title(id)), Icons.STAR);
            }
            g.save();
        }
        setModal(false);
        refresh();
        dumpTargets();
    }

    @Override
    public void enter() {
        super.enter();
        g.progress.ensureDaily(g.epochDay());
        loginDay = g.progress.nextLoginDay(g.epochDay());
        modalT = 0f;
        setModal(g.progress.tutorialDone && g.progress.loginAvailable(g.epochDay()));
        refresh();
    }

    private void refresh() {
        boolean langChanged = lastLang != L10n.version();
        lastLang = L10n.version();
        if (g.progress.diamonds != lastDiamonds) {
            lastDiamonds = g.progress.diamonds;
            diamondText = Integer.toString(lastDiamonds);
        }
        if (g.progress.bestScore != lastBest || langChanged) {
            lastBest = g.progress.bestScore;
            bestText = lastBest > 0 ? L10n.f("menu.best", lastBest) : L10n.t("menu.best_none");
        }
        tasksBtn.badge = g.progress.claimableDailyCount();
        int lives = g.lives();
        String timer = lives < Energy.MAX ? g.energyTimer() : "";
        if (lives != lastLives || !timer.equals(lastTimer) || langChanged) {
            lastLives = lives;
            lastTimer = timer;
            missionBtn.sub = timer.isEmpty() ? L10n.f("menu.lives_sub", lives, Energy.MAX)
                    : L10n.f("menu.lives_sub_timer", lives, Energy.MAX, timer);
        }
        if (g.progress.level != lastLevel || g.progress.xp != lastXp || langChanged) {
            lastLevel = g.progress.level;
            lastXp = g.progress.xp;
            levelText = L10n.f("menu.level", lastLevel);
        }
    }

    @Override
    public void update(float dt) {
        super.update(dt);
        scroll += dt * 120f;
        if (loginModal) modalT += dt;
        bg.update(dt, 0f);
        refresh();
    }

    @Override
    public boolean wantsBanner() { return true; }

    @Override
    public boolean onBack() {
        if (loginModal) {
            setModal(false);
            return true;
        }
        return false; // uygulamadan çık
    }

    private void drawLoginModal(Canvas c) {
        float k = Math.min(1f, modalT / 0.25f);
        c.drawColor(Theme.alpha(Theme.NAVY_DEEP, 0.85f * k));
        float cx = w / 2f, pw = Math.min(w - 60f * u, 960f * u);
        float top = h * 0.24f, bottom = loginBtn.rect.bottom + 60f * u;
        Ui.glow(c, cx, (top + bottom) / 2f, pw * 0.6f, Theme.GOLD, 0.2f * k);
        Ui.panel(c, cx - pw / 2f, top, cx + pw / 2f, bottom, 48f * u, Theme.alpha(Theme.PANEL, 0.98f * k), Theme.alpha(Theme.GOLD, k), 3f * u);
        Icons.draw(c, Icons.GIFT, cx, top + 90f * u, 44f * u, Theme.alpha(Theme.GOLD, k));
        Ui.textFit(c, loginTitle, cx, top + 200f * u, 60f * u, pw - 80f * u, Theme.alpha(Theme.WHITE, k), Paint.Align.CENTER, Ui.DISPLAY);
        // Açıklama: tek satırsa eski konumunda, iki satırsa aynı merkez etrafında
        float lineH = loginDescSize * 1.25f;
        float dy = top + 255f * u - (loginDescLines.length - 1) * lineH / 2f;
        for (int i = 0; i < loginDescLines.length; i++) {
            Ui.text(c, loginDescLines[i], cx, dy + i * lineH, loginDescSize, Theme.alpha(Theme.MUTED, k), Paint.Align.CENTER, Ui.MEDIUM);
        }
        // 7 gün hücreleri: 4 + 3
        float cellW = (pw - 100f * u) / 4f, cellH = 170f * u;
        for (int i = 0; i < 7; i++) {
            int row = i / 4, col = i % 4;
            float rowW = row == 0 ? cellW * 4f : cellW * 3f;
            float l = cx - rowW / 2f + col * cellW + 8f * u, t = top + 300f * u + row * (cellH + 16f * u);
            boolean today = i + 1 == loginDay, past = i + 1 < loginDay;
            int border = today ? Theme.GOLD : (past ? Theme.alpha(Theme.TEAL, 0.6f) : Theme.LINE);
            Ui.panel(c, l, t, l + cellW - 16f * u, t + cellH, 26f * u, Theme.alpha(today ? Theme.CARD : Theme.NAVY_DEEP, k), Theme.alpha(border, k), today ? 4f * u : 2f * u);
            float ccx = l + (cellW - 16f * u) / 2f;
            Ui.textFit(c, dayText[i], ccx, t + 46f * u, 26f * u, cellW - 40f * u, Theme.alpha(today ? Theme.GOLD : Theme.MUTED, k), Paint.Align.CENTER, Ui.BOLD);
            if (past) {
                Icons.draw(c, Icons.CHECK, ccx, t + 100f * u, 26f * u, Theme.alpha(Theme.TEAL, k));
            } else {
                Ui.diamond(c, ccx - 34f * u, t + 108f * u, 16f * u, k);
                Ui.text(c, DAY_REWARD[i], ccx + 12f * u, Ui.centerY(t + 108f * u, 36f * u), 36f * u, Theme.alpha(Theme.GOLD, k), Paint.Align.CENTER, Ui.NUMBERS);
            }
        }
    }

    @Override
    public void draw(Canvas c) {
        bg.drawBackground(c, w, h, scroll);
        float cx = w / 2f;
        // Gösteri: oyuncu Lissajous eğrisinde, gölge 0.9 sn geriden takip eder
        float titleBottom = safeT + 420f * u;
        float areaY = (titleBottom + playBtn.rect.top - 110f * u) / 2f;
        float ax = 320f * u, ay = Math.min(220f * u, (playBtn.rect.top - titleBottom) * 0.25f);
        float t1 = time * 1.2f, t2 = (time - 0.9f) * 1.2f;
        float px = cx + (float) Math.sin(t1) * ax, py = areaY + (float) Math.sin(t1 * 2f) * ay;
        float sx = cx + (float) Math.sin(t2) * ax, sy = areaY + (float) Math.sin(t2 * 2f) * ay;
        float appear = Math.min(1f, time / 0.6f);
        WorldRenderer.drawSkinPreview(c, g.skin(), sx, sy, 56f * u, 0f, appear, true);
        WorldRenderer.drawSkinPreview(c, g.skin(), px, py, 56f * u, (float) Math.cos(t1) * 0.3f, appear, false);

        // Başlık
        float ty = safeT + 290f * u;
        Ui.glow(c, cx, ty - 40f * u, 520f * u, Theme.PURPLE, 0.35f);
        Ui.brandTitle(c, cx, ty, 150f * u, w - 120f * u, 1f);

        // Üst çubuk: elmaslar + seviye
        float pw = Ui.diamondPill(c, safeL + 40f * u, safeT + 40f * u, 96f * u, diamondText, false);
        float lx = safeL + 40f * u + pw + 24f * u, lw = 190f * u;
        Ui.textFit(c, levelText, lx, safeT + 80f * u, 30f * u, Math.max(lw, helpBtn.rect.left - 24f * u - lx), Theme.WHITE, Paint.Align.LEFT, Ui.BOLD);
        Ui.panel(c, lx, safeT + 98f * u, lx + lw, safeT + 110f * u, 6f * u, Theme.alpha(Theme.NAVY_DEEP, 0.9f), Theme.LINE, 1.5f * u);
        float xk = lastXp / (float) Progress.xpForLevel(Math.max(1, lastLevel));
        if (xk > 0f) Ui.panel(c, lx, safeT + 98f * u, lx + lw * Math.min(1f, xk), safeT + 110f * u, 6f * u, Theme.TEAL, 0, 0);

        // En iyi skor
        float by = playBtn.rect.top - 70f * u;
        Ui.textFit(c, bestText, cx, by, 38f * u, playBtn.rect.width(), Theme.GOLD, Paint.Align.CENTER, Ui.BOLD);
        if (loginModal) {
            for (int i = 0; i < buttons.size(); i++) if (buttons.get(i) != loginBtn) buttons.get(i).draw(c, u);
            drawLoginModal(c);
            loginBtn.draw(c, u);
        } else {
            drawButtons(c);
        }
    }
}
