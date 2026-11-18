package com.isikyolu.game.ui;

import android.content.Context;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.isikyolu.engine.Lives;
import com.isikyolu.engine.Progress;
import com.isikyolu.game.Game;

/** Kalp sayısı ve bir sonraki cana kalan süre; her saniye kendini günceller. */
public final class LivesBadge extends LinearLayout {
    private final TextView count, timer;
    private final ImageView heart;
    private int lastLives = -1;
    private final Runnable tick = this::refresh;

    public LivesBadge(Context c) {
        super(c);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setBackground(Ui.round(0x33FF5C7A, Ui.dp(c, 14)));
        setPadding(Ui.dp(c, 9), Ui.dp(c, 4), Ui.dp(c, 11), Ui.dp(c, 4));
        heart = Ui.icon(c, Icon.HEART, 0xFFFF5C7A, 18);
        addView(heart);
        count = Ui.text(c, "", 14, Palette.WHITE, Ui.BOLD);
        count.setPadding(Ui.dp(c, 5), 0, 0, 0);
        addView(count);
        timer = Ui.text(c, "", 12, 0xFFFFC2CC, Ui.MEDIUM);
        timer.setPadding(Ui.dp(c, 6), 0, 0, 0);
        addView(timer);
        refresh();
    }

    public void refresh() {
        removeCallbacks(tick);
        Progress p = Game.progress();
        Lives.Clock clk = Game.clock();
        Lives.update(p, clk);
        count.setText(String.valueOf(p.lives));
        if (p.lives >= Lives.MAX) timer.setText("Dolu");
        else timer.setText(Lives.format(Lives.msUntilNext(p, clk)));
        if (lastLives >= 0 && p.lives > lastLives) {
            heart.animate().scaleX(1.4f).scaleY(1.4f).setDuration(150).withEndAction(() -> heart.animate().scaleX(1f).scaleY(1f).setDuration(200).start()).start();
        }
        lastLives = p.lives;
        setContentDescription(p.lives + " can");
        if (isAttachedToWindow()) postDelayed(tick, 1000);
    }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); refresh(); }

    @Override protected void onDetachedFromWindow() { super.onDetachedFromWindow(); removeCallbacks(tick); }
}
