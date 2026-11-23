package com.golgen.oyun.screens;

import android.graphics.Canvas;
import android.graphics.Paint;

import com.golgen.oyun.GameView;
import com.golgen.oyun.core.Progress;
import com.golgen.oyun.ui.Button;
import com.golgen.oyun.ui.Icons;
import com.golgen.oyun.ui.Screen;
import com.golgen.oyun.ui.Theme;
import com.golgen.oyun.ui.Ui;
import com.golgen.oyun.ui.WorldRenderer;

/** Ayarlar: ses, müzik, titreşim, öğretici ve ilerleme sıfırlama. */
public final class SettingsScreen extends Screen {
    private final WorldRenderer bg = new WorldRenderer();
    private final Button back, soundRow, musicRow, vibRow, tutorialBtn, resetBtn, confirmYes, confirmNo;
    private boolean confirming;
    private final float[] knob = new float[3];
    private String version = "";

    public SettingsScreen(GameView g) {
        super(g);
        back = add(makeBackButton());
        soundRow = add(new Button("", Icons.NONE, Button.GHOST, () -> { g.progress.sound = !g.progress.sound; changed(); }));
        musicRow = add(new Button("", Icons.NONE, Button.GHOST, () -> { g.progress.music = !g.progress.music; changed(); }));
        vibRow = add(new Button("", Icons.NONE, Button.GHOST, () -> {
            g.progress.vibration = !g.progress.vibration;
            changed();
            g.haptics.medium();
        }));
        tutorialBtn = add(new Button("ÖĞRETİCİYİ OYNA", Icons.BOOK, Button.SECONDARY, () -> {
            g.tutorial.setFromSettings(true);
            g.go(g.tutorial);
        }));
        resetBtn = add(new Button("İLERLEMEYİ SIFIRLA", Icons.TRASH, Button.DANGER, () -> setConfirm(true)));
        confirmYes = add(new Button("EVET, SIFIRLA", Icons.NONE, Button.GOLD, () -> {
            g.progress.resetAll();
            g.progress.ensureDaily(g.epochDay());
            g.progress.tutorialDone = true;
            g.save();
            g.applySettings();
            setConfirm(false);
            g.toast("İlerleme sıfırlandı", Icons.TRASH);
        }));
        confirmNo = add(new Button("VAZGEÇ", Icons.NONE, Button.SECONDARY, () -> setConfirm(false)));
        soundRow.id = "ayar-ses";
        musicRow.id = "ayar-muzik";
        vibRow.id = "ayar-titresim";
        try {
            version = "Sürüm " + g.activity.getPackageManager().getPackageInfo(g.activity.getPackageName(), 0).versionName;
        } catch (Exception e) {
            version = "";
        }
    }

    private void changed() {
        g.applySettings();
        g.save();
    }

    private void setConfirm(boolean on) {
        confirming = on;
        confirmYes.visible = confirmNo.visible = on;
        soundRow.enabled = musicRow.enabled = vibRow.enabled = tutorialBtn.enabled = resetBtn.enabled = back.enabled = !on;
        dumpTargets();
    }

    @Override
    public void enter() {
        super.enter();
        setConfirm(false);
        Progress p = g.progress;
        knob[0] = p.sound ? 1f : 0f;
        knob[1] = p.music ? 1f : 0f;
        knob[2] = p.vibration ? 1f : 0f;
    }

    @Override
    protected void layout() {
        layoutBack(back);
        bg.s = w / 1000f;
        float cw = Math.min(w - 80f * u, 900f * u), l = (w - cw) / 2f, top = headerBottom() + 30f * u, rh = 150f * u;
        soundRow.set(l, top, l + cw, top + rh);
        musicRow.set(l, top + rh + 24f * u, l + cw, top + rh * 2f + 24f * u);
        vibRow.set(l, top + (rh + 24f * u) * 2f, l + cw, top + rh * 3f + 48f * u);
        float bt = vibRow.rect.bottom + 80f * u, bh = 130f * u;
        tutorialBtn.set(l, bt, l + cw, bt + bh);
        resetBtn.set(l, bt + bh + 30f * u, l + cw, bt + bh * 2f + 30f * u);
        float cy = h * 0.55f;
        confirmYes.set(w / 2f - 360f * u, cy, w / 2f - 20f * u, cy + 120f * u);
        confirmNo.set(w / 2f + 20f * u, cy, w / 2f + 360f * u, cy + 120f * u);
    }

    @Override
    public boolean wantsBanner() { return true; }

    @Override
    public boolean onBack() {
        if (confirming) setConfirm(false); else g.goMenu();
        return true;
    }

    @Override
    public void update(float dt) {
        super.update(dt);
        bg.update(dt, 0f);
        Progress p = g.progress;
        float k = Math.min(1f, dt * 14f);
        knob[0] += ((p.sound ? 1f : 0f) - knob[0]) * k;
        knob[1] += ((p.music ? 1f : 0f) - knob[1]) * k;
        knob[2] += ((p.vibration ? 1f : 0f) - knob[2]) * k;
    }

    private void toggleRow(Canvas c, Button b, String title, String desc, int icon, float on) {
        float l = b.rect.left, t = b.rect.top, r = b.rect.right, bt = b.rect.bottom, cy = (t + bt) / 2f;
        Ui.panel(c, l, t, r, bt, 36f * u, Theme.alpha(Theme.CARD, 0.95f), Theme.LINE, 2f * u);
        Icons.draw(c, icon, l + 80f * u, cy, 30f * u, Theme.TEAL);
        Ui.text(c, title, l + 150f * u, cy - 6f * u, 42f * u, Theme.WHITE, Paint.Align.LEFT, Ui.BOLD);
        Ui.text(c, desc, l + 150f * u, cy + 40f * u, 28f * u, Theme.MUTED, Paint.Align.LEFT, Ui.MEDIUM);
        // Anahtar
        float sw = 130f * u, sh = 70f * u, sl = r - 50f * u - sw, st = cy - sh / 2f;
        Ui.panel(c, sl, st, sl + sw, st + sh, sh / 2f, Theme.lerp(Theme.LINE, Theme.TEAL, on), 0, 0);
        float kx = sl + sh / 2f + (sw - sh) * on;
        Ui.fill.setColor(Theme.WHITE);
        c.drawCircle(kx, cy, sh * 0.4f, Ui.fill);
    }

    @Override
    public void draw(Canvas c) {
        bg.drawBackground(c, w, h, time * 40f);
        drawHeader(c, "AYARLAR");
        toggleRow(c, soundRow, "Ses Efektleri", "Dokunma, elmas ve çarpma sesleri", Icons.SOUND, knob[0]);
        toggleRow(c, musicRow, "Müzik", "Arka plan müziği", Icons.MUSIC, knob[1]);
        toggleRow(c, vibRow, "Titreşim", "Çarpma ve toplama titreşimi", Icons.VIBRATE, knob[2]);
        tutorialBtn.draw(c, u);
        resetBtn.draw(c, u);
        back.draw(c, u);
        Ui.text(c, version, w / 2f, h - safeB - 90f * u, 28f * u, Theme.MUTED, Paint.Align.CENTER, Ui.MEDIUM);
        Ui.text(c, "Tüm görseller ve sesler oyun içinde kodla üretilir.", w / 2f, h - safeB - 50f * u, 26f * u,
                Theme.alpha(Theme.MUTED, 0.7f), Paint.Align.CENTER, Ui.REGULAR);
        if (confirming) {
            c.drawColor(Theme.alpha(Theme.NAVY_DEEP, 0.85f));
            float pw = Math.min(w - 80f * u, 840f * u), cx = w / 2f, top = confirmYes.rect.top - 300f * u;
            Ui.panel(c, cx - pw / 2f, top, cx + pw / 2f, confirmYes.rect.bottom + 60f * u, 44f * u, Theme.PANEL, Theme.GOLD, 3f * u);
            Ui.text(c, "Emin misin?", cx, top + 110f * u, 56f * u, Theme.WHITE, Paint.Align.CENTER, Ui.DISPLAY);
            Ui.textFit(c, "Elmaslar, karakterler, rekorlar ve görevler silinecek.", cx, top + 190f * u, 32f * u, pw - 80f * u,
                    Theme.MUTED, Paint.Align.CENTER, Ui.MEDIUM);
            confirmYes.draw(c, u);
            confirmNo.draw(c, u);
        }
    }
}
