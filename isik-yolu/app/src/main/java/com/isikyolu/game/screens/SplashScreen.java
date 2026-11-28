package com.isikyolu.game.screens;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.isikyolu.game.Game;
import com.isikyolu.game.GameLoader;
import com.isikyolu.game.MainActivity;
import com.isikyolu.game.i18n.L;
import com.isikyolu.game.ui.LogoView;
import com.isikyolu.game.ui.Palette;
import com.isikyolu.game.ui.Ui;

/** 1. Açılış ekranı: amblem animasyonu, bölümler arka planda yüklenir. */
public final class SplashScreen extends Screen {
    private final TextView status;
    private final long start = SystemClock.uptimeMillis();

    public SplashScreen(MainActivity a) {
        super(a);
        LinearLayout col = Ui.vertical(a);
        col.setGravity(Gravity.CENTER);
        LogoView logo = new LogoView(a);
        col.addView(logo, new LinearLayout.LayoutParams(Ui.dp(a, 170), Ui.dp(a, 170)));
        com.isikyolu.game.ui.BrandTitle title = new com.isikyolu.game.ui.BrandTitle(a, 44); // iki dilli ad (bkz. Brand)
        col.addView(title);
        TextView sub = Ui.text(a, L.t("menu.tagline"), 16, Palette.TURQUOISE, Ui.MEDIUM);
        col.addView(sub);
        col.addView(Ui.space(a, 40));
        status = Ui.text(a, L.t("splash.loading"), 14, Palette.TEXT_DIM, Ui.REGULAR);
        col.addView(status);
        addView(col, new LayoutParams(-1, -1));
        title.setAlpha(0f); title.setTranslationY(Ui.dp(a, 12));
        title.animate().alpha(1f).translationY(0).setStartDelay(500).setDuration(600).start();
        sub.setAlpha(0f);
        sub.animate().alpha(1f).setStartDelay(900).setDuration(600).start();
    }

    @Override public void onShow() {
        GameLoader.load(act, ok -> {
            if (!ok) { status.setText(L.t("splash.load_failed")); return; }
            long wait = Math.max(0, 1500 - (SystemClock.uptimeMillis() - start));
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (Game.levels() != null) act.goMenu();
            }, wait);
        });
    }
}
