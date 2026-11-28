package com.isikyolu.game.ui;

import android.content.Context;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.isikyolu.engine.Progress;
import com.isikyolu.game.Game;
import com.isikyolu.game.i18n.L;

/** Işık Kristali ve Prizma Taşı bakiyesi; değişince sayı canlanarak güncellenir. */
public final class CurrencyBar extends LinearLayout {
    public static final int CRYSTAL_COLOR = 0xFF5EE6FF, GEM_COLOR = 0xFFC08CFF;
    private final TextView kr, gem;
    private int shownKr = -1, shownGem = -1;

    public CurrencyBar(Context c) {
        super(c);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        kr = pill(c, Icon.CRYSTAL, CRYSTAL_COLOR);
        gem = pill(c, Icon.GEM, GEM_COLOR);
        refresh();
    }

    private TextView pill(Context c, int icon, int color) {
        LinearLayout l = Ui.horizontal(c);
        l.setBackground(Ui.round(Palette.withAlpha(color, 46), Ui.dp(c, 14)));
        l.setPadding(Ui.dp(c, 8), Ui.dp(c, 4), Ui.dp(c, 11), Ui.dp(c, 4));
        l.addView(Ui.icon(c, icon, color, 17));
        TextView t = Ui.text(c, "", 14, Palette.WHITE, Ui.BOLD);
        t.setPadding(Ui.dp(c, 5), 0, 0, 0);
        l.addView(t);
        LayoutParams lp = new LayoutParams(-2, -2);
        if (getChildCount() > 0) lp.leftMargin = Ui.dp(c, 6);
        addView(l, lp);
        return t;
    }

    public void refresh() {
        Progress p = Game.progress();
        animateTo(kr, shownKr, p.crystals);
        animateTo(gem, shownGem, p.gems);
        shownKr = p.crystals;
        shownGem = p.gems;
        setContentDescription(L.t("market.balance_cd", L.n("common.crystals", p.crystals), L.n("market.gems_cd", p.gems)));
    }

    private static void animateTo(TextView t, int from, int to) {
        t.setText(String.valueOf(to));
        if (from >= 0 && from != to) {
            t.setTextColor(to > from ? 0xFF8CFFB0 : 0xFFFF9AA8);
            t.animate().scaleX(1.25f).scaleY(1.25f).setDuration(140).withEndAction(() -> {
                t.animate().scaleX(1f).scaleY(1f).setDuration(200).start();
                t.setTextColor(Palette.WHITE);
            }).start();
        }
    }
}
