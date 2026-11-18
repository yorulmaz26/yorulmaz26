package com.golgen.oyun.screens;

import android.graphics.Canvas;
import android.graphics.Paint;

import com.golgen.oyun.GameView;
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

    public MenuScreen(GameView g) {
        super(g);
        playBtn = add(new Button("OYNA", Icons.PLAY, Button.PRIMARY, g::startEndless));
        playBtn.sub = "Sonsuz Mod";
        missionBtn = add(new Button("GÖREV MODU", Icons.TARGET, Button.SECONDARY, () -> {
            g.missions.showTab(MissionsScreen.TAB_LEVELS);
            g.go(g.missions);
        }));
        skinsBtn = add(new Button("Karakter", Icons.PERSON, Button.SECONDARY, () -> g.go(g.skins)));
        tasksBtn = add(new Button("Görevler", Icons.CALENDAR, Button.SECONDARY, () -> {
            g.missions.showTab(MissionsScreen.TAB_DAILY);
            g.go(g.missions);
        }));
        recordsBtn = add(new Button("Rekorlar", Icons.TROPHY, Button.SECONDARY, () -> g.go(g.records)));
        skinsBtn.vertical = tasksBtn.vertical = recordsBtn.vertical = true;
        settingsBtn = add(new Button(null, Icons.GEAR, Button.ICON, () -> g.go(g.settings)));
        helpBtn = add(new Button(null, Icons.BOOK, Button.ICON, () -> {
            g.tutorial.setFromSettings(true);
            g.go(g.tutorial);
        }));
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
    }

    @Override
    public void enter() {
        super.enter();
        g.progress.ensureDaily(g.epochDay());
        refresh();
    }

    private void refresh() {
        if (g.progress.diamonds != lastDiamonds) {
            lastDiamonds = g.progress.diamonds;
            diamondText = Integer.toString(lastDiamonds);
        }
        if (g.progress.bestScore != lastBest) {
            lastBest = g.progress.bestScore;
            bestText = lastBest > 0 ? "EN İYİ SKOR  " + lastBest : "İlk rekorunu kır!";
        }
        tasksBtn.badge = g.progress.claimableDailyCount();
    }

    @Override
    public void update(float dt) {
        super.update(dt);
        scroll += dt * 120f;
        bg.update(dt, 0f);
        refresh();
    }

    @Override
    public boolean onBack() {
        return false; // uygulamadan çık
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
        Ui.text(c, "GÖLGEN", cx, ty, 150f * u, Theme.WHITE, Paint.Align.CENTER, Ui.DISPLAY);
        Ui.text(c, "SENİ YAKALAMASIN", cx, ty + 84f * u, 62f * u, Theme.TEAL, Paint.Align.CENTER, Ui.BOLD);

        // Üst çubuk: elmaslar
        Ui.diamondPill(c, safeL + 40f * u, safeT + 40f * u, 96f * u, diamondText, false);

        // En iyi skor
        float by = playBtn.rect.top - 70f * u;
        Ui.text(c, bestText, cx, by, 38f * u, Theme.GOLD, Paint.Align.CENTER, Ui.BOLD);
        drawButtons(c);
    }
}
