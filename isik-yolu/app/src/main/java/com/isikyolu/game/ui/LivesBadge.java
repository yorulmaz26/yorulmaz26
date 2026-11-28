package com.isikyolu.game.ui;

import android.content.Context;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.isikyolu.engine.Lives;
import com.isikyolu.engine.Progress;
import com.isikyolu.game.Game;
import com.isikyolu.game.i18n.L;

/**
 * Can göstergesi: "❤ 3/4  19:42". Canlar doluyken sayaç yerine "Dolu" yazar. Her saniye kendini günceller;
 * can yenilenince kalp atar ve "+1" yükselir.
 */
public final class LivesBadge extends LinearLayout {
    private final TextView count, timer, plus;
    private final ImageView heart;
    private final FrameLayout heartBox;
    private int lastLives = -1;
    private final Runnable tick = this::refresh;

    public LivesBadge(Context c) {
        super(c);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setBackground(Ui.round(0x33FF5C7A, Ui.dp(c, 14)));
        setPadding(Ui.dp(c, 9), Ui.dp(c, 4), Ui.dp(c, 11), Ui.dp(c, 4));
        setClipChildren(false);
        heartBox = new FrameLayout(c);
        heartBox.setClipChildren(false);
        heart = Ui.icon(c, Icon.HEART, 0xFFFF5C7A, 18);
        heartBox.addView(heart, new FrameLayout.LayoutParams(Ui.dp(c, 18), Ui.dp(c, 18), Gravity.CENTER));
        plus = Ui.text(c, "+1", 12, 0xFFFF8FA3, Ui.BOLD);
        plus.setAlpha(0f);
        heartBox.addView(plus, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        addView(heartBox);
        count = Ui.text(c, "", 14, Palette.WHITE, Ui.BOLD);
        count.setPadding(Ui.dp(c, 5), 0, 0, 0);
        count.setSingleLine(true);
        addView(count);
        timer = Ui.text(c, "", 12, 0xFFFFC2CC, Ui.MEDIUM);
        timer.setPadding(Ui.dp(c, 6), 0, 0, 0);
        timer.setSingleLine(true);
        timer.setFontFeatureSettings("tnum"); // rakamlar sabit genişlikte: sayaç akarken rozet titremez
        addView(timer);
        refresh();
    }

    public void refresh() {
        removeCallbacks(tick);
        Progress p = Game.progress();
        Lives.Clock clk = Game.clock();
        Lives.update(p, clk);
        count.setText(p.lives + "/" + Lives.MAX);
        String left = Lives.format(Lives.msUntilNext(p, clk));
        if (p.lives >= Lives.MAX) timer.setText(L.t("lives.full"));
        else timer.setText(left);
        if (lastLives >= 0 && p.lives > lastLives) regenAnimation();
        lastLives = p.lives;
        setContentDescription(p.lives >= Lives.MAX ? L.t("lives.cd_full", p.lives, Lives.MAX)
                : L.t("lives.cd", p.lives, Lives.MAX, left));
        if (isAttachedToWindow()) postDelayed(tick, 1000);
    }

    /**
     * Yenilenme animasyonu: kalp büyüyüp küçülür, "+1" yukarı süzülerek kaybolur. Kare kare (saat tabanlı) çizilir;
     * ViewPropertyAnimator kullanılmaz (test ortamında saati ileri sarıp kayıtları bozuyordu).
     */
    private void regenAnimation() {
        animStart = android.os.SystemClock.uptimeMillis();
        removeCallbacks(animStep);
        postOnAnimation(animStep);
    }

    private long animStart;
    private final Runnable animStep = this::animStep;

    private void animStep() {
        float t = android.os.SystemClock.uptimeMillis() - animStart;
        float sc = t < 150 ? 1f + 0.45f * (t / 150f) : t < 370 ? 1.45f - 0.45f * ((t - 150) / 220f) : 1f;
        heart.setScaleX(sc);
        heart.setScaleY(sc);
        float p = Math.min(1f, t / 900f);
        plus.setTranslationY(-Ui.dp(getContext(), 22) * p);
        plus.setAlpha(1f - p);
        if (t < 900 && isAttachedToWindow()) postOnAnimation(animStep);
    }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); refresh(); }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(tick);
        removeCallbacks(animStep);
        heart.setScaleX(1f); heart.setScaleY(1f); plus.setAlpha(0f);
    }
}
