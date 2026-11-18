package com.golgen.oyun.screens;

import android.graphics.Canvas;
import android.graphics.Paint;

import com.golgen.oyun.GameView;
import com.golgen.oyun.ui.Screen;
import com.golgen.oyun.ui.Theme;
import com.golgen.oyun.ui.Ui;
import com.golgen.oyun.ui.WorldRenderer;

/** Açılış ekranı: logo animasyonu, dokununca geçilir. */
public final class SplashScreen extends Screen {
    private static final float DURATION = 2.4f;
    private boolean left;

    public SplashScreen(GameView g) {
        super(g);
    }

    @Override
    public void enter() {
        super.enter();
        left = false;
    }

    private void leave() {
        if (left) return;
        left = true;
        if (g.progress.tutorialDone) g.goMenu(); else { g.tutorial.setFromSettings(false); g.go(g.tutorial); }
    }

    @Override
    public void update(float dt) {
        super.update(dt);
        if (time > DURATION) leave();
    }

    @Override
    protected void onTap(float x, float y) {
        if (time > 0.4f) leave();
    }

    @Override
    public void draw(Canvas c) {
        c.drawColor(Theme.NAVY);
        float cx = w / 2f, cy = h * 0.42f;
        float k = Math.min(1f, time / 0.8f);
        float ease = 1f - (1f - k) * (1f - k) * (1f - k);
        float r = 70f * u;
        // Gölge karakterin arkasından kayarak gelir
        float off = (1f - ease) * 260f * u + 70f * u;
        WorldRenderer.drawSkinPreview(c, g.skin(), cx - off * 0.7f, cy + off * 0.5f, r, 0f, ease, true);
        WorldRenderer.drawSkinPreview(c, g.skin(), cx, cy, r, (1f - ease) * 1.5f, ease, false);

        float tk = Math.max(0f, Math.min(1f, (time - 0.5f) / 0.6f));
        float ty = cy + 260f * u + (1f - tk) * 40f * u;
        Ui.text(c, "GÖLGEN", cx, ty, 120f * u, Theme.alpha(Theme.WHITE, tk), Paint.Align.CENTER, Ui.DISPLAY);
        Ui.text(c, "SENİ YAKALAMASIN", cx, ty + 82f * u, 58f * u, Theme.alpha(Theme.TEAL, tk), Paint.Align.CENTER, Ui.BOLD);

        // Yükleme çubuğu
        float bw = 360f * u, by = h - safeB - 180f * u;
        Ui.panel(c, cx - bw / 2f, by, cx + bw / 2f, by + 8f * u, 4f * u, Theme.alpha(Theme.LINE, 0.8f), 0, 0);
        float p = Math.min(1f, time / DURATION);
        Ui.panel(c, cx - bw / 2f, by, cx - bw / 2f + bw * p, by + 8f * u, 4f * u, Theme.PURPLE, 0, 0);
        Ui.text(c, "Dokunarak geç", cx, by + 70f * u, 30f * u, Theme.alpha(Theme.MUTED, tk), Paint.Align.CENTER, Ui.MEDIUM);
    }
}
