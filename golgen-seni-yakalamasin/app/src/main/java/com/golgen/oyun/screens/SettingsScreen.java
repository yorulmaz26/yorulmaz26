package com.golgen.oyun.screens;

import android.graphics.Canvas;
import android.graphics.Paint;

import com.golgen.oyun.GameView;
import com.golgen.oyun.core.L10n;
import com.golgen.oyun.core.Progress;
import com.golgen.oyun.ui.Button;
import com.golgen.oyun.ui.Icons;
import com.golgen.oyun.ui.Screen;
import com.golgen.oyun.ui.Theme;
import com.golgen.oyun.ui.Ui;
import com.golgen.oyun.ui.WorldRenderer;

/** Ayarlar: ses, müzik, titreşim, dil, öğretici ve ilerleme sıfırlama. */
public final class SettingsScreen extends Screen {
    private final WorldRenderer bg = new WorldRenderer();
    private final Button back, soundRow, musicRow, vibRow, langRow, tutorialBtn, resetBtn, confirmYes, confirmNo, langClose;
    private final Button[] langBtns = new Button[L10n.CODES.length];
    private boolean confirming, picking;
    private final float[] knob = new float[3];
    private String versionName = "", version = "", credits = "", title = "";
    private String sTitle = "", sDesc = "", mTitle = "", mDesc = "", vTitle = "", vDesc = "", lTitle = "", lDesc = "";
    private String confirmTitle = "", confirmText = "", pickTitle = "";

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
        langRow = add(new Button("", Icons.NONE, Button.GHOST, () -> setPicking(true)));
        tutorialBtn = add(new Button(null, Icons.BOOK, Button.SECONDARY, () -> {
            g.tutorial.setFromSettings(true);
            g.go(g.tutorial);
        }).key("settings.tutorial"));
        resetBtn = add(new Button(null, Icons.TRASH, Button.DANGER, () -> setConfirm(true)).key("settings.reset"));
        confirmYes = add(new Button(null, Icons.NONE, Button.GOLD, () -> {
            String lang = g.progress.language;     // dil tercihi bir ayardır, ilerleme değildir: korunur
            g.progress.resetAll();
            g.progress.language = lang;
            g.progress.ensureDaily(g.epochDay());
            g.progress.tutorialDone = true;
            g.save();
            g.applySettings();
            setConfirm(false);
            g.toast(L10n.t("settings.reset_done"), Icons.TRASH);
        }).key("settings.reset_yes"));
        confirmNo = add(new Button(null, Icons.NONE, Button.SECONDARY, () -> setConfirm(false)).key("common.cancel"));
        for (int i = 0; i < langBtns.length; i++) {
            final String code = L10n.CODES[i];
            langBtns[i] = add(new Button(L10n.NATIVE_NAMES[i], Icons.NONE, Button.SECONDARY, () -> {
                g.setLanguage(code);
                setPicking(false);
            }));
            langBtns[i].id = "dil-" + code;
        }
        langClose = add(new Button(null, Icons.NONE, Button.GHOST, () -> setPicking(false)).key("common.close"));
        langClose.id = "dil-kapat";
        soundRow.id = "ayar-ses";
        musicRow.id = "ayar-muzik";
        vibRow.id = "ayar-titresim";
        langRow.id = "ayar-dil";
        try {
            versionName = g.activity.getPackageManager().getPackageInfo(g.activity.getPackageName(), 0).versionName;
        } catch (Exception e) {
            versionName = "";
        }
        setPicking(false);
    }

    @Override
    protected void onLocalize() {
        title = L10n.t("settings.title");
        sTitle = L10n.t("settings.sound.title");
        sDesc = L10n.t("settings.sound.desc");
        mTitle = L10n.t("settings.music.title");
        mDesc = L10n.t("settings.music.desc");
        vTitle = L10n.t("settings.vibration.title");
        vDesc = L10n.t("settings.vibration.desc");
        lTitle = L10n.t("settings.language.title");
        lDesc = L10n.t("settings.language.desc");
        confirmTitle = L10n.t("settings.reset_confirm_title");
        confirmText = L10n.t("settings.reset_confirm_text");
        pickTitle = L10n.t("settings.language.pick_title");
        version = versionName.isEmpty() ? "" : L10n.f("settings.version", versionName);
        credits = L10n.t("settings.credits");
        for (int i = 0; i < langBtns.length; i++) {
            boolean cur = L10n.CODES[i].equals(L10n.code());
            langBtns[i].style = cur ? Button.PRIMARY : Button.SECONDARY;
            langBtns[i].icon = cur ? Icons.CHECK : Icons.NONE;
        }
    }

    private void setPicking(boolean on) {
        picking = on;
        for (Button b : langBtns) b.visible = on;
        langClose.visible = on;
        soundRow.enabled = musicRow.enabled = vibRow.enabled = langRow.enabled = tutorialBtn.enabled = resetBtn.enabled = back.enabled = !on && !confirming;
        if (w > 0) dumpTargets();
    }

    private void changed() {
        g.applySettings();
        g.save();
    }

    private void setConfirm(boolean on) {
        confirming = on;
        confirmYes.visible = confirmNo.visible = on;
        soundRow.enabled = musicRow.enabled = vibRow.enabled = langRow.enabled = tutorialBtn.enabled = resetBtn.enabled = back.enabled = !on && !picking;
        dumpTargets();
    }

    @Override
    public void enter() {
        super.enter();
        setConfirm(false);
        setPicking(false);
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
        langRow.set(l, top + (rh + 24f * u) * 3f, l + cw, top + rh * 4f + 72f * u);
        float bt = langRow.rect.bottom + 70f * u, bh = 130f * u;
        tutorialBtn.set(l, bt, l + cw, bt + bh);
        resetBtn.set(l, bt + bh + 30f * u, l + cw, bt + bh * 2f + 30f * u);
        float cy = h * 0.55f;
        confirmYes.set(w / 2f - 360f * u, cy, w / 2f - 20f * u, cy + 120f * u);
        confirmNo.set(w / 2f + 20f * u, cy, w / 2f + 360f * u, cy + 120f * u);
        // Dil seçici: tek sütun
        float pw = Math.min(w - 80f * u, 760f * u), bw = pw - 100f * u, lbh = 108f * u, gap = 18f * u;
        float total = 150f * u + langBtns.length * (lbh + gap) + 120f * u;
        float pt = Math.max(safeT + 40f * u, h / 2f - total / 2f) + 150f * u;
        for (int i = 0; i < langBtns.length; i++) {
            float y = pt + i * (lbh + gap);
            langBtns[i].set(w / 2f - bw / 2f, y, w / 2f + bw / 2f, y + lbh);
        }
        float cyb = pt + langBtns.length * (lbh + gap);
        langClose.set(w / 2f - bw / 2f, cyb, w / 2f + bw / 2f, cyb + 100f * u);
    }

    @Override
    public boolean wantsBanner() { return true; }

    @Override
    public boolean onBack() {
        if (picking) setPicking(false);
        else if (confirming) setConfirm(false);
        else g.goMenu();
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
        float tw = r - l - 150f * u - 210f * u;
        Ui.textFit(c, title, l + 150f * u, cy - 6f * u, 42f * u, tw, Theme.WHITE, Paint.Align.LEFT, Ui.BOLD);
        Ui.textFit(c, desc, l + 150f * u, cy + 40f * u, 28f * u, tw, Theme.MUTED, Paint.Align.LEFT, Ui.MEDIUM);
        // Anahtar
        float sw = 130f * u, sh = 70f * u, sl = r - 50f * u - sw, st = cy - sh / 2f;
        Ui.panel(c, sl, st, sl + sw, st + sh, sh / 2f, Theme.lerp(Theme.LINE, Theme.TEAL, on), 0, 0);
        float kx = sl + sh / 2f + (sw - sh) * on;
        Ui.fill.setColor(Theme.WHITE);
        c.drawCircle(kx, cy, sh * 0.4f, Ui.fill);
    }

    private void languageRow(Canvas c, Button b) {
        float l = b.rect.left, t = b.rect.top, r = b.rect.right, bt = b.rect.bottom, cy = (t + bt) / 2f;
        Ui.panel(c, l, t, r, bt, 36f * u, Theme.alpha(Theme.CARD, 0.95f), Theme.LINE, 2f * u);
        Icons.draw(c, Icons.BOOK, l + 80f * u, cy, 30f * u, Theme.TEAL);
        float tw = r - l - 150f * u - 330f * u;
        Ui.textFit(c, lTitle, l + 150f * u, cy - 6f * u, 42f * u, tw, Theme.WHITE, Paint.Align.LEFT, Ui.BOLD);
        Ui.textFit(c, lDesc, l + 150f * u, cy + 40f * u, 28f * u, tw, Theme.MUTED, Paint.Align.LEFT, Ui.MEDIUM);
        int i = Math.max(0, L10n.indexOf(L10n.code()));
        Ui.textFit(c, L10n.NATIVE_NAMES[i], r - 90f * u, Ui.centerY(cy, 38f * u), 38f * u, 280f * u, Theme.GOLD, Paint.Align.RIGHT, Ui.BOLD);
        Icons.draw(c, Icons.NEXT, r - 50f * u, cy, 18f * u, Theme.GOLD);
    }

    @Override
    public void draw(Canvas c) {
        bg.drawBackground(c, w, h, time * 40f);
        drawHeader(c, title);
        toggleRow(c, soundRow, sTitle, sDesc, Icons.SOUND, knob[0]);
        toggleRow(c, musicRow, mTitle, mDesc, Icons.MUSIC, knob[1]);
        toggleRow(c, vibRow, vTitle, vDesc, Icons.VIBRATE, knob[2]);
        languageRow(c, langRow);
        tutorialBtn.draw(c, u);
        resetBtn.draw(c, u);
        back.draw(c, u);
        Ui.textFit(c, version, w / 2f, h - safeB - 90f * u, 28f * u, w - 80f * u, Theme.MUTED, Paint.Align.CENTER, Ui.MEDIUM);
        Ui.textFit(c, credits, w / 2f, h - safeB - 50f * u, 26f * u, w - 80f * u,
                Theme.alpha(Theme.MUTED, 0.7f), Paint.Align.CENTER, Ui.REGULAR);
        if (confirming) {
            c.drawColor(Theme.alpha(Theme.NAVY_DEEP, 0.85f));
            float pw = Math.min(w - 80f * u, 840f * u), cx = w / 2f, top = confirmYes.rect.top - 300f * u;
            Ui.panel(c, cx - pw / 2f, top, cx + pw / 2f, confirmYes.rect.bottom + 60f * u, 44f * u, Theme.PANEL, Theme.GOLD, 3f * u);
            Ui.textFit(c, confirmTitle, cx, top + 110f * u, 56f * u, pw - 80f * u, Theme.WHITE, Paint.Align.CENTER, Ui.DISPLAY);
            Ui.textFit(c, confirmText, cx, top + 190f * u, 32f * u, pw - 80f * u,
                    Theme.MUTED, Paint.Align.CENTER, Ui.MEDIUM);
            confirmYes.draw(c, u);
            confirmNo.draw(c, u);
        }
        if (picking) {
            c.drawColor(Theme.alpha(Theme.NAVY_DEEP, 0.88f));
            float pw = Math.min(w - 80f * u, 760f * u), cx = w / 2f;
            float top = langBtns[0].rect.top - 150f * u, bottom = langClose.rect.bottom + 40f * u;
            Ui.panel(c, cx - pw / 2f, top, cx + pw / 2f, bottom, 44f * u, Theme.PANEL, Theme.TEAL, 3f * u);
            Ui.textFit(c, pickTitle, cx, top + 95f * u, 50f * u, pw - 80f * u, Theme.WHITE, Paint.Align.CENTER, Ui.DISPLAY);
            for (Button b : langBtns) b.draw(c, u);
            langClose.draw(c, u);
        }
    }
}
