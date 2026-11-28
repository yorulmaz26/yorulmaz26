package com.golgen.oyun.screens;

import android.graphics.Canvas;
import android.graphics.Paint;

import com.golgen.oyun.GameView;
import com.golgen.oyun.core.Energy;
import com.golgen.oyun.core.GameConfig;
import com.golgen.oyun.core.L10n;
import com.golgen.oyun.core.Progress;
import com.golgen.oyun.core.Synth;
import com.golgen.oyun.ui.Button;
import com.golgen.oyun.ui.Icons;
import com.golgen.oyun.ui.Screen;
import com.golgen.oyun.ui.Theme;
import com.golgen.oyun.ui.Ui;

/**
 * "Canın kalmadı" penceresi: tüm ekranların üstünde açılır (GameView katmanı).
 * Sonraki cana geri sayım, video izleyerek +1 can (günlük sınırlı, reklam hazırsa),
 * elmasla doldurma ve kapatma. Can kazanılırsa beklenen bölüm doğrudan başlar.
 */
public final class LivesDialog extends Screen {
    private final Button video, refill, close;
    private boolean open;
    private int pendingLevel = -1;
    private float appear;
    private String title = "", info = "", next = "", left = "";
    private final float[] fit = new float[1];
    private String[] infoLines = new String[0];
    private float infoSize;

    public LivesDialog(GameView g) {
        super(g);
        video = add(new Button(null, Icons.PLAY, Button.PRIMARY, this::watch).key("lives.dialog.video"));
        video.id = "can-video";
        refill = add(new Button(null, Icons.HEART, Button.GOLD, this::buy));
        refill.id = "can-elmas";
        close = add(new Button(null, Icons.NONE, Button.SECONDARY, this::dismiss).key("common.close"));
        close.id = "can-kapat";
    }

    public boolean isOpen() { return open; }

    /** Bölüm başlatılamadığında açılır; can kazanılırsa o bölüm başlar. */
    public void show(int level) {
        pendingLevel = level;
        open = true;
        appear = 0f;
        g.sound.play(Synth.HIT, 0.5f, 0.8f);
        onLocalize();
        refreshButtons();
        g.layoutOverlay();
    }

    public void dismiss() {
        open = false;
        pendingLevel = -1;
        g.current().dumpTargets();
    }

    private void watch() {
        if (!g.progress.canWatchForLife(g.epochDay(), g.clock) || !g.ads.isRewardedReady()) return;
        g.ads.showRewarded(() -> {
            if (g.progress.onWatchedForLife(g.epochDay(), g.clock)) {
                g.sound.play(Synth.SUCCESS);
                g.noteLivesChanged();
                g.toast(L10n.p("lives.video_left", g.progress.rewardedLivesLeft(g.epochDay())), Icons.HEART);
                g.save();
                resume();
            }
        }, this::refreshButtons);
    }

    private void buy() {
        if (g.progress.buyRefill(g.clock)) {
            g.sound.play(Synth.PURCHASE);
            g.haptics.medium();
            g.noteLivesChanged();
            g.toast(L10n.t("lives.refilled"), Icons.HEART);
            g.save();
            resume();
        } else {
            g.toast(L10n.f("lives.need_diamonds", Progress.REFILL_COST), Icons.LOCK);
        }
    }

    /** Can geldiyse pencere kapanır ve beklenen bölüm başlar. */
    private void resume() {
        int lvl = pendingLevel;
        open = false;
        pendingLevel = -1;
        if (lvl >= 0 && g.lives() > 0) g.startMission(lvl);
    }

    private void refreshButtons() {
        boolean canVideo = g.ads.isRewardedReady() && g.progress.canWatchForLife(g.epochDay(), g.clock);
        video.visible = canVideo;
        video.sub = canVideo ? L10n.p("lives.video_left", g.progress.rewardedLivesLeft(g.epochDay())) : null;
        refill.visible = true;
        refill.enabled = g.progress.diamonds >= Progress.REFILL_COST;
        refill.label = L10n.f("lives.dialog.refill", Progress.REFILL_COST);
        refill.sub = L10n.f("lives.dialog.have", g.progress.diamonds);
        if (open) dumpTargets();
    }

    @Override
    protected void onLocalize() {
        title = L10n.t("lives.dialog.title");
        info = L10n.p("lives.dialog.info", GameConfig.LIFE_REGEN_SECONDS / 60, Energy.MAX);
        if (w > 0) infoLines = Ui.wrapFit(info, 34f * u, Math.min(w - 60f * u, 900f * u) - 120f * u, 3, Ui.MEDIUM, fit);
        infoSize = fit[0];
        refreshButtons();
    }

    @Override
    protected void layout() {
        float pw = Math.min(w - 60f * u, 900f * u), cx = w / 2f;
        float top = h * 0.5f - 470f * u;
        float bh = 130f * u, bw = pw - 120f * u, y = top + 560f * u;
        video.set(cx - bw / 2f, y, cx + bw / 2f, y + bh);
        refill.set(cx - bw / 2f, y + bh + 24f * u, cx + bw / 2f, y + bh * 2f + 24f * u);
        close.set(cx - bw / 2f, y + bh * 2f + 48f * u, cx + bw / 2f, y + bh * 2f + 48f * u + 110f * u);
        infoLines = Ui.wrapFit(info, 34f * u, pw - 120f * u, 3, Ui.MEDIUM, fit);
        infoSize = fit[0];
    }

    @Override
    public void update(float dt) {
        if (!open) return;
        super.update(dt);
        appear = Math.min(1f, appear + dt * 5f);
        int lives = g.lives();
        next = lives >= Energy.MAX ? L10n.t("lives.full") : L10n.f("lives.next", g.energyTimer());
        left = L10n.f("lives.count", lives, Energy.MAX);
        // Beklerken can yenilendiyse bölüme dönülebilir
        if (lives > 0 && close.key.equals("common.close")) {
            close.key("lives.dialog.play");
            close.style = Button.PRIMARY;
            close.onClick = this::resume;
            dumpTargets();
        } else if (lives == 0 && !close.key.equals("common.close")) {
            close.key("common.close");
            close.style = Button.SECONDARY;
            close.onClick = this::dismiss;
        }
        boolean canVideo = g.ads.isRewardedReady() && g.progress.canWatchForLife(g.epochDay(), g.clock);
        if (canVideo != video.visible) refreshButtons();
    }

    @Override
    public boolean onBack() {
        dismiss();
        return true;
    }

    @Override
    public void draw(Canvas c) {
        if (!open) return;
        float k = appear;
        c.drawColor(Theme.alpha(Theme.NAVY_DEEP, 0.88f * k));
        float pw = Math.min(w - 60f * u, 900f * u), cx = w / 2f;
        float top = h * 0.5f - 470f * u, bottom = close.rect.bottom + 60f * u;
        Ui.glow(c, cx, (top + bottom) / 2f, pw * 0.6f, MissionsScreen.HEART, 0.18f * k);
        Ui.panel(c, cx - pw / 2f, top, cx + pw / 2f, bottom, 48f * u, Theme.alpha(Theme.PANEL, 0.98f * k),
                Theme.alpha(MissionsScreen.HEART, k), 3f * u);
        float pulse = 1f + 0.08f * (float) Math.sin(time * 5f);
        Ui.glow(c, cx, top + 110f * u, 110f * u * pulse, MissionsScreen.HEART, 0.35f * k);
        Icons.draw(c, Icons.HEART, cx, top + 110f * u, 46f * u * pulse, Theme.alpha(MissionsScreen.HEART, k));
        Ui.textFit(c, title, cx, top + 230f * u, 60f * u, pw - 80f * u, Theme.alpha(Theme.WHITE, k), Paint.Align.CENTER, Ui.DISPLAY);
        Ui.textFit(c, left, cx, top + 300f * u, 40f * u, pw - 80f * u, Theme.alpha(MissionsScreen.HEART, k), Paint.Align.CENTER, Ui.NUMBERS);
        Ui.textFit(c, next, cx, top + 360f * u, 40f * u, pw - 80f * u, Theme.alpha(Theme.GOLD, k), Paint.Align.CENTER, Ui.BOLD);
        float ly = top + 420f * u;
        for (int i = 0; i < infoLines.length; i++) {
            Ui.text(c, infoLines[i], cx, ly + i * infoSize * 1.3f, infoSize, Theme.alpha(Theme.MUTED, k), Paint.Align.CENTER, Ui.MEDIUM);
        }
        drawButtons(c);
    }
}
