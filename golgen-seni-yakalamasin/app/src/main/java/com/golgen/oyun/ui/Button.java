package com.golgen.oyun.ui;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;

/** Dokunmatik buton: basılma animasyonu, ikon, rozet ve stil desteği. */
public final class Button {
    public static final int PRIMARY = 0, SECONDARY = 1, GOLD = 2, ICON = 3, GHOST = 4, DANGER = 5;

    public final RectF rect = new RectF();
    public String label;
    public String id;           // otomatik testler için ad
    /** Yerelleştirme anahtarı: dil değişince etiket {@link Screen#localize()} ile yenilenir; test kimliği olarak da kullanılır. */
    public String key;
    public String sub;          // ikinci satır (ör. fiyat)
    public int icon = Icons.NONE;
    public int style;
    public boolean enabled = true;
    public boolean visible = true;
    public boolean vertical;    // ikon üstte, metin altta
    public int badge;           // > 0 ise kırmızı yerine altın rozet sayısı gösterilir
    public Runnable onClick;
    boolean pressed;
    int pointerId = -1;
    float press;                // 0..1 basılma animasyonu
    private Shader shader;
    private float shaderTop = Float.NaN, shaderBottom;

    public Button(String label, int icon, int style, Runnable onClick) {
        this.label = label;
        this.icon = icon;
        this.style = style;
        this.onClick = onClick;
    }

    /** Etiketi yerelleştirme anahtarından alır (dil değişiminde otomatik yenilenir). */
    public Button key(String k) {
        key = k;
        label = com.golgen.oyun.core.L10n.t(k);
        return this;
    }

    public Button set(float l, float t, float r, float b) {
        rect.set(l, t, r, b);
        return this;
    }

    public boolean contains(float x, float y) {
        float pad = rect.height() * 0.08f;
        return visible && x >= rect.left - pad && x <= rect.right + pad && y >= rect.top - pad && y <= rect.bottom + pad;
    }

    public void update(float dt) {
        float target = pressed ? 1f : 0f;
        press += (target - press) * Math.min(1f, dt * 18f);
    }

    public void draw(Canvas c, float u) {
        if (!visible) return;
        float scale = 1f - 0.05f * press;
        float cx = rect.centerX(), cy = rect.centerY();
        c.save();
        c.scale(scale, scale, cx, cy);
        float h = rect.height();
        float radius = style == ICON ? h * 0.32f : h * 0.5f;
        float alpha = enabled ? 1f : 0.45f;
        int textColor = Theme.WHITE;
        switch (style) {
            case PRIMARY: {
                if (shader == null || shaderTop != rect.top || shaderBottom != rect.bottom) {
                    shader = new LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
                            Theme.PURPLE_LIGHT, Theme.PURPLE_DARK, Shader.TileMode.CLAMP);
                    shaderTop = rect.top;
                    shaderBottom = rect.bottom;
                }
                Ui.glow(c, cx, cy + h * 0.1f, rect.width() * 0.62f, Theme.PURPLE, 0.35f * alpha);
                Ui.rf.set(rect);
                Ui.fill.setShader(shader);
                Ui.fill.setAlpha((int) (255 * alpha));
                c.drawRoundRect(Ui.rf, radius, radius, Ui.fill);
                Ui.fill.setShader(null);
                Ui.stroke.setColor(Theme.alpha(Theme.WHITE, 0.25f * alpha));
                Ui.stroke.setStrokeWidth(2f * u);
                Ui.rf.inset(2f * u, 2f * u);
                c.drawRoundRect(Ui.rf, radius, radius, Ui.stroke);
                break;
            }
            case GOLD:
                Ui.glow(c, cx, cy, rect.width() * 0.55f, Theme.GOLD, 0.25f * alpha);
                Ui.panel(c, rect.left, rect.top, rect.right, rect.bottom, radius, Theme.alpha(Theme.GOLD, alpha), 0, 0);
                textColor = Theme.NAVY;
                break;
            case DANGER:
                Ui.panel(c, rect.left, rect.top, rect.right, rect.bottom, radius, Theme.alpha(Theme.PANEL, alpha),
                        Theme.alpha(Theme.GOLD, alpha), 3f * u);
                textColor = Theme.alpha(Theme.GOLD, alpha);
                break;
            case GHOST:
                textColor = Theme.alpha(Theme.MUTED, alpha);
                break;
            case ICON:
                Ui.panel(c, rect.left, rect.top, rect.right, rect.bottom, radius, Theme.alpha(Theme.PANEL, 0.92f * alpha),
                        Theme.alpha(Theme.LINE, alpha), 3f * u);
                break;
            default: // SECONDARY
                Ui.panel(c, rect.left, rect.top, rect.right, rect.bottom, radius, Theme.alpha(Theme.PANEL, 0.95f * alpha),
                        Theme.alpha(Theme.TEAL, 0.9f * alpha), 3.5f * u);
                break;
        }
        if (press > 0.01f) {
            Ui.panel(c, rect.left, rect.top, rect.right, rect.bottom, radius, Theme.alpha(Theme.WHITE, 0.08f * press), 0, 0);
        }
        boolean hasLabel = label != null && !label.isEmpty();
        if (vertical && hasLabel) {
            Icons.draw(c, icon, cx, cy - h * 0.12f, h * 0.17f, Theme.alpha(Theme.TEAL, alpha));
            float ts = h * 0.15f;
            Ui.textFit(c, label, cx, Ui.centerY(cy + h * 0.24f, ts), ts, rect.width() * 0.9f, Theme.alpha(textColor, alpha), Paint.Align.CENTER, Ui.BOLD);
        } else if (icon != Icons.NONE && !hasLabel) {
            Icons.draw(c, icon, cx, cy, h * 0.3f, Theme.alpha(textColor, alpha));
        } else if (hasLabel) {
            float ts = h * (sub != null ? 0.3f : 0.36f);
            float textW = Ui.measure(label, ts, Ui.BOLD);
            float iconSpace = icon != Icons.NONE ? h * 0.55f : 0f;
            float total = textW + iconSpace;
            float maxW = rect.width() - h * 0.5f;
            if (total > maxW) {
                if (maxW / total < Ui.OVERFLOW_SCALE) Ui.reportOverflow(label, maxW / total);
                ts *= maxW / total;
                textW = Ui.measure(label, ts, Ui.BOLD);
                total = textW + iconSpace;
            }
            float startX = cx - total / 2f;
            float ty = sub != null ? cy - h * 0.06f : cy;
            if (icon != Icons.NONE) {
                Icons.draw(c, icon, startX + h * 0.2f, ty, h * 0.2f, Theme.alpha(textColor, alpha));
            }
            Ui.text(c, label, startX + iconSpace + textW / 2f, Ui.centerY(ty, ts), ts, Theme.alpha(textColor, alpha), Paint.Align.CENTER, Ui.BOLD);
            if (sub != null) {
                float ss = h * 0.2f;
                Ui.text(c, sub, cx, Ui.centerY(cy + h * 0.26f, ss), ss, Theme.alpha(textColor, 0.8f * alpha), Paint.Align.CENTER, Ui.MEDIUM);
            }
        }
        if (badge > 0) {
            float br = h * 0.2f;
            float bx = rect.right - br * 0.4f, by = rect.top + br * 0.4f;
            Ui.fill.setShader(null);
            Ui.fill.setColor(Theme.GOLD);
            c.drawCircle(bx, by, br, Ui.fill);
            Ui.text(c, Integer.toString(badge), bx, Ui.centerY(by, br * 1.2f), br * 1.2f, Theme.NAVY, Paint.Align.CENTER, Ui.BOLD);
        }
        c.restore();
    }
}
