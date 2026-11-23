package com.golgen.oyun.ui;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;

import com.golgen.oyun.GameView;
import com.golgen.oyun.core.Synth;

import java.util.ArrayList;

/**
 * Ekran temel sınıfı: buton dağıtımı, dokunma/sürükleme algılama, güvenli alan ölçüleri
 * ve ortak başlık çizimi.
 */
public abstract class Screen {
    protected final GameView g;
    protected final ArrayList<Button> buttons = new ArrayList<>();
    protected float w, h, u;
    protected float safeL, safeT, safeR, safeB;
    protected float time;

    private int trackId = -1;
    private float downX, downY, lastY, lastT;
    private boolean dragging;
    protected float scrollVel;

    protected Screen(GameView g) {
        this.g = g;
    }

    public final void resize(float w, float h, float u, float sl, float st, float sr, float sb) {
        this.w = w;
        this.h = h;
        this.u = u;
        safeL = sl;
        safeT = st;
        safeR = sr;
        safeB = sb;
        layout();
    }

    /** Ekran boyutuna göre öğeleri yerleştirir. */
    protected void layout() {}

    public void enter() { time = 0f; }

    public void exit() {}

    public void update(float dt) {
        time += dt;
        for (int i = 0; i < buttons.size(); i++) buttons.get(i).update(dt);
    }

    public abstract void draw(Canvas c);

    /** true dönerse geri tuşu ele alındı. */
    public boolean onBack() { return false; }

    public void onAppPause() {}

    /** Bu ekranda banner reklam gösterilebilir mi? (Oyun, öğretici ve açılışta asla.) */
    public boolean wantsBanner() { return false; }

    protected Button add(Button b) {
        buttons.add(b);
        return b;
    }

    // ---------------- Dokunma ----------------
    public boolean onTouch(MotionEvent e) {
        if (dispatchButtons(e)) return true;
        int action = e.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                trackId = e.getPointerId(0);
                downX = e.getX();
                downY = lastY = e.getY();
                lastT = e.getEventTime();
                dragging = false;
                scrollVel = 0f;
                break;
            case MotionEvent.ACTION_MOVE: {
                int idx = e.findPointerIndex(trackId);
                if (idx < 0) break;
                float y = e.getY(idx), x = e.getX(idx);
                if (!dragging && (Math.abs(y - downY) > 14f * u || Math.abs(x - downX) > 14f * u)) dragging = true;
                if (dragging) {
                    float dy = y - lastY;
                    long t = e.getEventTime();
                    float dtMs = Math.max(1f, t - lastT);
                    scrollVel = dy / dtMs * 1000f;
                    onDrag(dy);
                    lastT = t;
                }
                lastY = y;
                break;
            }
            case MotionEvent.ACTION_UP: {
                if (!dragging && trackId == e.getPointerId(0)) onTap(e.getX(), e.getY());
                if (e.getEventTime() - lastT > 80) scrollVel = 0f;
                trackId = -1;
                dragging = false;
                break;
            }
            case MotionEvent.ACTION_CANCEL:
                trackId = -1;
                dragging = false;
                break;
            default:
                break;
        }
        return true;
    }

    protected boolean isDragging() { return dragging || trackId != -1; }

    protected void onTap(float x, float y) {}

    protected void onDrag(float dy) {}

    /** Butonlara dokunmayı dağıtır; buton tarafından ele alındıysa true. */
    protected boolean dispatchButtons(MotionEvent e) {
        int action = e.getActionMasked();
        int ai = e.getActionIndex();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                float x = e.getX(ai), y = e.getY(ai);
                for (int i = buttons.size() - 1; i >= 0; i--) {
                    Button b = buttons.get(i);
                    if (b.visible && b.enabled && b.pointerId == -1 && b.contains(x, y)) {
                        b.pressed = true;
                        b.pointerId = e.getPointerId(ai);
                        return true;
                    }
                }
                return false;
            }
            case MotionEvent.ACTION_MOVE: {
                boolean any = false;
                for (int i = 0; i < buttons.size(); i++) {
                    Button b = buttons.get(i);
                    if (b.pointerId == -1) continue;
                    int idx = e.findPointerIndex(b.pointerId);
                    if (idx >= 0) b.pressed = b.contains(e.getX(idx), e.getY(idx));
                    any = true;
                }
                return any;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                int pid = e.getPointerId(ai);
                Button clicked = null;
                boolean handled = false;
                for (int i = 0; i < buttons.size(); i++) {
                    Button b = buttons.get(i);
                    if (b.pointerId != pid) continue;
                    handled = true;
                    if (b.pressed && b.contains(e.getX(ai), e.getY(ai)) && b.visible && b.enabled) clicked = b;
                    b.pressed = false;
                    b.pointerId = -1;
                }
                if (clicked != null) {
                    if (g.debuggable) android.util.Log.i("GolgenUI", "tik ad=" + (clicked.id != null ? clicked.id : clicked.label));
                    g.sound.play(Synth.TAP, 0.7f, 1f);
                    g.haptics.light();
                    if (clicked.onClick != null) clicked.onClick.run();
                }
                return handled;
            }
            case MotionEvent.ACTION_CANCEL:
                for (int i = 0; i < buttons.size(); i++) {
                    buttons.get(i).pressed = false;
                    buttons.get(i).pointerId = -1;
                }
                return false;
            default:
                return false;
        }
    }

    /** Otomatik UI testleri için görünür butonları ve dokunma hedeflerini loglar (yalnızca debug). */
    public void dumpTargets() {
        if (!g.debuggable) return;
        String name = getClass().getSimpleName();
        for (int i = 0; i < buttons.size(); i++) {
            Button b = buttons.get(i);
            if (!b.visible || !b.enabled) continue;
            String id = b.id != null ? b.id : (b.label != null && !b.label.isEmpty() ? b.label : "ikon-" + b.icon);
            g.debugTarget(name, id, b.rect.centerX(), b.rect.centerY());
        }
        dumpExtraTargets(name);
    }

    protected void dumpExtraTargets(String screen) {}

    protected void drawButtons(Canvas c) {
        for (int i = 0; i < buttons.size(); i++) buttons.get(i).draw(c, u);
    }

    // ---------------- Ortak çizimler ----------------
    /** Alt ekranlar için başlık çubuğu yüksekliği. */
    protected float headerBottom() {
        return safeT + 170f * u;
    }

    /** Geri butonunun konumunu ayarlar ve başlık için buton döndürür. */
    protected Button makeBackButton() {
        return new Button(null, Icons.BACK, Button.ICON, () -> g.goMenu());
    }

    protected void layoutBack(Button back) {
        float s = 112f * u;
        back.set(safeL + 40f * u, safeT + 32f * u, safeL + 40f * u + s, safeT + 32f * u + s);
    }

    protected void drawHeader(Canvas c, String title) {
        float cy = safeT + 88f * u;
        Ui.textFit(c, title, w / 2f, Ui.centerY(cy, 56f * u), 56f * u, w - 420f * u, Theme.WHITE, Paint.Align.CENTER, Ui.DISPLAY);
        Ui.fill.setShader(null);
        Ui.fill.setColor(Theme.alpha(Theme.PURPLE, 0.8f));
        Ui.rf.set(w / 2f - 40f * u, cy + 44f * u, w / 2f + 40f * u, cy + 50f * u);
        c.drawRoundRect(Ui.rf, 3f * u, 3f * u, Ui.fill);
    }

    protected float clampScroll(float scroll, float contentH, float viewH) {
        float max = Math.max(0f, contentH - viewH);
        return Math.max(0f, Math.min(max, scroll));
    }
}
