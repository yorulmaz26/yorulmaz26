package com.golgen.oyun.screens;

import android.graphics.Canvas;
import android.graphics.Paint;

import com.golgen.oyun.GameView;
import com.golgen.oyun.core.SkinDef;
import com.golgen.oyun.core.Skins;
import com.golgen.oyun.core.Synth;
import com.golgen.oyun.ui.Button;
import com.golgen.oyun.ui.Icons;
import com.golgen.oyun.ui.Screen;
import com.golgen.oyun.ui.Theme;
import com.golgen.oyun.ui.Ui;
import com.golgen.oyun.ui.WorldRenderer;

/** Karakter seçimi: 8 görünüm, elmasla satın alma, seçme. */
public final class SkinsScreen extends Screen {
    private final WorldRenderer bg = new WorldRenderer();
    private final Button back, actionBtn;
    private int focus;
    private float gridTop, cellW, cellH, gridL;
    private final String[] priceText = new String[Skins.ALL.length];
    private String diamondText = "";

    public SkinsScreen(GameView g) {
        super(g);
        back = add(makeBackButton());
        actionBtn = add(new Button("", Icons.NONE, Button.PRIMARY, this::act));
        for (int i = 0; i < Skins.ALL.length; i++) priceText[i] = Integer.toString(Skins.ALL[i].price);
    }

    @Override
    public void enter() {
        super.enter();
        focus = g.progress.selectedSkin;
        refresh();
    }

    private void refresh() {
        diamondText = Integer.toString(g.progress.diamonds);
        SkinDef s = Skins.ALL[focus];
        if (g.progress.isUnlocked(focus)) {
            boolean sel = g.progress.selectedSkin == focus;
            actionBtn.label = sel ? "SEÇİLİ" : "SEÇ";
            actionBtn.icon = sel ? Icons.CHECK : Icons.NONE;
            actionBtn.style = sel ? Button.SECONDARY : Button.PRIMARY;
            actionBtn.enabled = !sel;
            actionBtn.sub = null;
        } else {
            boolean afford = g.progress.diamonds >= s.price;
            actionBtn.label = "SATIN AL  ·  " + s.price;
            actionBtn.icon = Icons.NONE;
            actionBtn.style = Button.GOLD;
            actionBtn.enabled = afford;
            actionBtn.sub = afford ? null : (s.price - g.progress.diamonds) + " elmas daha gerekli";
        }
    }

    private void act() {
        if (g.progress.isUnlocked(focus)) {
            g.progress.select(focus);
            g.save();
        } else if (g.progress.buySkin(focus)) {
            g.progress.select(focus);
            g.sound.play(Synth.PURCHASE);
            g.haptics.medium();
            g.toast(Skins.ALL[focus].name + " açıldı!", Icons.PERSON);
            int gained = g.progress.checkAchievements(null, null);
            for (int id = 0; id < com.golgen.oyun.core.Achievements.COUNT; id++) {
                if ((gained & (1 << id)) != 0) g.toast("Başarı: " + com.golgen.oyun.core.Achievements.TITLES[id], Icons.STAR);
            }
            g.save();
        }
        refresh();
        dumpTargets();
    }

    @Override
    protected void layout() {
        layoutBack(back);
        bg.s = w / 1000f;
        float cw = Math.min(w - 100f * u, 920f * u);
        gridL = (w - cw) / 2f;
        float bh = 120f * u;
        float btnTop = headerBottom() + 420f * u;
        actionBtn.set(w / 2f - 300f * u, btnTop, w / 2f + 300f * u, btnTop + bh);
        gridTop = btnTop + bh + 60f * u;
        cellW = cw / 4f;
        float avail = h - safeB - 40f * u - gridTop;
        cellH = Math.min(300f * u, avail / 2f);
    }

    @Override
    protected void dumpExtraTargets(String screen) {
        for (int i = 0; i < Skins.ALL.length; i++) {
            g.debugTarget(screen, "kart-" + i, gridL + (i % 4) * cellW + cellW / 2f, gridTop + (i / 4) * cellH + cellH / 2f);
        }
    }

    @Override
    protected void onTap(float x, float y) {
        if (y < gridTop || x < gridL || x > gridL + cellW * 4f) return;
        int col = (int) ((x - gridL) / cellW);
        int row = (int) ((y - gridTop) / cellH);
        if (row < 0 || row > 1 || col < 0 || col > 3) return;
        focus = row * 4 + col;
        g.sound.play(Synth.TAP, 0.6f, 1.1f);
        refresh();
        dumpTargets();
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
    }

    @Override
    public void draw(Canvas c) {
        bg.drawBackground(c, w, h, time * 40f);
        drawHeader(c, "KARAKTERLER");
        Ui.diamondPill(c, w - safeR - 40f * u, safeT + 40f * u, 96f * u, diamondText, true);

        // Önizleme
        SkinDef s = Skins.ALL[focus];
        float cx = w / 2f, py = headerBottom() + 150f * u;
        float t = time * 1.4f;
        WorldRenderer.drawSkinPreview(c, s, cx - 150f * u + (float) Math.sin(t - 0.8f) * 40f * u, py + 20f * u, 70f * u, 0f, 0.9f, true);
        WorldRenderer.drawSkinPreview(c, s, cx + (float) Math.sin(t) * 40f * u, py, 90f * u, (float) Math.sin(t) * 0.25f, 1f, false);
        Ui.text(c, s.name, cx, py + 205f * u, 50f * u, Theme.WHITE, Paint.Align.CENTER, Ui.BOLD);

        // Izgara
        for (int i = 0; i < Skins.ALL.length; i++) {
            SkinDef k = Skins.ALL[i];
            int col = i % 4, row = i / 4;
            float l = gridL + col * cellW + 10f * u, tp = gridTop + row * cellH + 10f * u;
            float r = l + cellW - 20f * u, b = tp + cellH - 20f * u;
            boolean unlocked = g.progress.isUnlocked(i);
            boolean selected = g.progress.selectedSkin == i;
            int border = i == focus ? Theme.TEAL : (selected ? Theme.alpha(Theme.TEAL, 0.5f) : Theme.LINE);
            Ui.panel(c, l, tp, r, b, 32f * u, Theme.alpha(Theme.CARD, 0.95f), border, i == focus ? 4f * u : 2f * u);
            float ccx = (l + r) / 2f, ccy = tp + (b - tp) * 0.4f;
            float sz = Math.min(cellW, cellH) * 0.17f;
            if (unlocked) {
                WorldRenderer.drawSkinPreview(c, k, ccx, ccy, sz, 0f, 1f, false);
            } else {
                Ui.fill.setColor(Theme.alpha(Theme.MUTED, 0.35f));
                Ui.shape(c, k.shape, ccx, ccy, sz, 0f, Ui.fill);
                Icons.draw(c, Icons.LOCK, ccx, ccy, sz * 0.55f, Theme.WHITE);
            }
            float ly = b - (b - tp) * 0.16f;
            if (selected) {
                Ui.text(c, "SEÇİLİ", ccx, Ui.centerY(ly, 26f * u), 26f * u, Theme.TEAL, Paint.Align.CENTER, Ui.BOLD);
            } else if (unlocked) {
                Ui.text(c, "AÇIK", ccx, Ui.centerY(ly, 26f * u), 26f * u, Theme.MUTED, Paint.Align.CENTER, Ui.BOLD);
            } else {
                float tw = Ui.measure(priceText[i], 30f * u, Ui.NUMBERS);
                Ui.diamond(c, ccx - tw / 2f - 8f * u, ly, 13f * u, 1f);
                Ui.text(c, priceText[i], ccx + 14f * u, Ui.centerY(ly, 30f * u), 30f * u, Theme.GOLD, Paint.Align.CENTER, Ui.NUMBERS);
            }
        }
        drawButtons(c);
    }
}
