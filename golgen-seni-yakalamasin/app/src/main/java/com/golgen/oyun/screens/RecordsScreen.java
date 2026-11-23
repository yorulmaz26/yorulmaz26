package com.golgen.oyun.screens;

import android.graphics.Canvas;
import android.graphics.Paint;

import com.golgen.oyun.GameView;
import com.golgen.oyun.core.Missions;
import com.golgen.oyun.core.Progress;
import com.golgen.oyun.ui.Button;
import com.golgen.oyun.ui.Icons;
import com.golgen.oyun.ui.Screen;
import com.golgen.oyun.ui.Theme;
import com.golgen.oyun.ui.Ui;
import com.golgen.oyun.ui.WorldRenderer;

/** Rekorlar: en yüksek skor, istatistikler ve ilk 10 skor listesi. */
public final class RecordsScreen extends Screen {
    private final WorldRenderer bg = new WorldRenderer();
    private final Button back;
    private String best = "0";
    private final String[] labels = {"En uzun süre", "Toplam elmas", "Oynanan oyun", "Gölgeden kaçış", "Tamamlanan bölüm", "Başarılar",
            "Oyuncu seviyesi", "Taçlar", "En yüksek kombo", "Güçlendirme", "Giriş serisi", "Karakterler"};
    private final String[] values = new String[12];
    private final String[] top = new String[Progress.TOP_N];
    private float scroll, contentH;

    public RecordsScreen(GameView g) {
        super(g);
        back = add(makeBackButton());
    }

    @Override
    public void enter() {
        super.enter();
        scroll = 0f;
        Progress p = g.progress;
        best = Integer.toString(p.bestScore);
        int sec = (int) p.bestTime;
        values[0] = (sec / 60) + ":" + (sec % 60 < 10 ? "0" : "") + (sec % 60);
        values[1] = Integer.toString(p.totalDiamonds);
        values[2] = Integer.toString(p.gamesPlayed);
        values[3] = Integer.toString(p.totalDodges);
        values[4] = p.missionsCompleted + " / " + Missions.ALL.length;
        values[5] = Integer.bitCount(p.achievements) + " / " + com.golgen.oyun.core.Achievements.COUNT;
        values[6] = Integer.toString(p.level);
        values[7] = p.totalStars() + " / " + (Missions.ALL.length * 3);
        values[8] = Integer.toString(p.maxCombo);
        values[9] = Integer.toString(p.totalPowerups);
        values[10] = p.loginStreak + " gün";
        values[11] = Integer.bitCount(p.unlockedSkins) + " / " + com.golgen.oyun.core.Skins.ALL.length;
        for (int i = 0; i < Progress.TOP_N; i++) top[i] = p.topScores[i] > 0 ? Integer.toString(p.topScores[i]) : "—";
    }

    @Override
    protected void layout() {
        layoutBack(back);
        bg.s = w / 1000f;
        contentH = 340f * u + 6 * 190f * u + 110f * u + Progress.TOP_N * 86f * u + 40f * u;
    }

    @Override
    protected void onDrag(float dy) {
        scroll = clampScroll(scroll - dy, contentH, h - safeB - headerBottom());
    }

    @Override
    public void update(float dt) {
        super.update(dt);
        bg.update(dt, 0f);
        if (!isDragging() && Math.abs(scrollVel) > 5f) {
            scroll = clampScroll(scroll - scrollVel * dt, contentH, h - safeB - headerBottom());
            scrollVel *= (float) Math.pow(0.05, dt);
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
    public void draw(Canvas c) {
        bg.drawBackground(c, w, h, time * 40f);
        float cw = Math.min(w - 80f * u, 940f * u), l = (w - cw) / 2f, r = l + cw, cx = w / 2f;
        c.save();
        c.clipRect(0, headerBottom(), w, h);
        float y = headerBottom() + 20f * u - scroll;
        // En yüksek skor kartı
        Ui.glow(c, cx, y + 150f * u, cw * 0.55f, Theme.GOLD, 0.25f);
        Ui.panel(c, l, y, r, y + 300f * u, 44f * u, Theme.alpha(Theme.PANEL, 0.95f), Theme.alpha(Theme.GOLD, 0.7f), 3f * u);
        Icons.draw(c, Icons.TROPHY, cx, y + 70f * u, 36f * u, Theme.GOLD);
        Ui.text(c, "EN YÜKSEK SKOR", cx, y + 150f * u, 34f * u, Theme.MUTED, Paint.Align.CENTER, Ui.BOLD);
        Ui.text(c, best, cx, y + 260f * u, 110f * u, Theme.WHITE, Paint.Align.CENTER, Ui.NUMBERS);
        y += 340f * u;
        // İstatistik ızgarası 2x3
        float gw = (cw - 24f * u) / 2f;
        for (int i = 0; i < values.length; i++) {
            int col = i % 2, row = i / 2;
            float cl = l + col * (gw + 24f * u), ct = y + row * 190f * u;
            Ui.panel(c, cl, ct, cl + gw, ct + 166f * u, 32f * u, Theme.alpha(Theme.CARD, 0.95f), Theme.LINE, 2f * u);
            Ui.text(c, values[i], cl + 40f * u, ct + 85f * u, 56f * u, i == 1 ? Theme.GOLD : Theme.WHITE, Paint.Align.LEFT, Ui.NUMBERS);
            Ui.text(c, labels[i], cl + 40f * u, ct + 132f * u, 30f * u, Theme.MUTED, Paint.Align.LEFT, Ui.MEDIUM);
        }
        y += 6 * 190f * u + 20f * u;
        Ui.text(c, "EN İYİ 10 SKOR", l + 10f * u, y + 50f * u, 36f * u, Theme.TEAL, Paint.Align.LEFT, Ui.BOLD);
        y += 90f * u;
        for (int i = 0; i < Progress.TOP_N; i++) {
            float rt = y + i * 86f * u;
            Ui.panel(c, l, rt, r, rt + 72f * u, 24f * u, Theme.alpha(i == 0 ? Theme.CARD : Theme.PANEL, 0.9f), i == 0 ? Theme.alpha(Theme.GOLD, 0.6f) : Theme.LINE, 2f * u);
            Ui.text(c, (i + 1) + ".", l + 40f * u, Ui.centerY(rt + 36f * u, 34f * u), 34f * u, i < 3 ? Theme.GOLD : Theme.MUTED, Paint.Align.LEFT, Ui.NUMBERS);
            Ui.text(c, top[i], r - 40f * u, Ui.centerY(rt + 36f * u, 36f * u), 36f * u, Theme.WHITE, Paint.Align.RIGHT, Ui.NUMBERS);
        }
        c.restore();
        drawHeader(c, "REKORLAR");
        drawButtons(c);
    }
}
