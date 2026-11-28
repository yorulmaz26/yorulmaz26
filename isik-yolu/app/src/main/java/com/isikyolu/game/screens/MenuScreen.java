package com.isikyolu.game.screens;

import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.isikyolu.engine.Achievements;
import com.isikyolu.engine.DailyPuzzle;
import com.isikyolu.engine.Progress;
import com.isikyolu.game.Game;
import com.isikyolu.game.MainActivity;
import com.isikyolu.game.i18n.L;
import com.isikyolu.game.ui.Icon;
import com.isikyolu.game.ui.LogoView;
import com.isikyolu.game.ui.Palette;
import com.isikyolu.game.ui.TutorialOverlay;
import com.isikyolu.game.ui.Ui;

/** 2. Ana menü. */
public final class MenuScreen extends Screen {

    public MenuScreen(MainActivity a) {
        super(a);
        Progress p = Game.progress();
        ScrollView sv = new ScrollView(a);
        sv.setFillViewport(true);
        LinearLayout col = Ui.vertical(a);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = Ui.dp(a, 24);
        col.setPadding(pad, Ui.dp(a, 28), pad, pad);

        col.addView(new LogoView(a), new LinearLayout.LayoutParams(Ui.dp(a, 130), Ui.dp(a, 130)));
        // iki dilli ad: Türkçede "Işık Yolu" büyük + PRISMWAY küçük; diğer dillerde "Prismway" büyük + IŞIK YOLU küçük
        col.addView(new com.isikyolu.game.ui.BrandTitle(a, 40));
        TextView tagline = Ui.text(a, L.t("menu.tagline"), 15, Palette.TURQUOISE, Ui.MEDIUM);
        tagline.setGravity(Gravity.CENTER);
        col.addView(tagline, Ui.lpMargins(a, -1, -2, 0, 6, 0, 0));

        // yıldız ve ilerleme göstergesi
        LinearLayout pill = Ui.horizontal(a);
        pill.setBackground(Ui.round(0x22F5C542, Ui.dp(a, 16)));
        pill.setPadding(Ui.dp(a, 14), Ui.dp(a, 6), Ui.dp(a, 16), Ui.dp(a, 6));
        pill.addView(Ui.icon(a, Icon.STAR, Palette.GOLD, 18));
        // yıldız sayıları: Türkçede eskisi gibi ayırıcısız (1500), diğer dillerde yerel biçim (1,500 / 1.500 / 1 500)
        java.util.function.IntFunction<String> num = v -> "tr".equals(L.lang()) ? String.valueOf(v)
                : java.text.NumberFormat.getIntegerInstance(L.locale()).format(v);
        TextView st = Ui.text(a, "  " + L.n("menu.progress", Progress.LEVEL_COUNT, num.apply(p.totalStars()),
                num.apply(Progress.LEVEL_COUNT * 3), num.apply(p.completedCount())), 14, Palette.GOLD, Ui.MEDIUM);
        pill.addView(st);
        col.addView(pill, Ui.lpMargins(a, -2, -2, 0, 18, 0, 10));
        LinearLayout status = Ui.horizontal(a);
        status.setGravity(Gravity.CENTER);
        status.addView(new com.isikyolu.game.ui.LivesBadge(a));
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-2, -2);
        cl.leftMargin = Ui.dp(a, 6);
        com.isikyolu.game.ui.CurrencyBar cb = new com.isikyolu.game.ui.CurrencyBar(a);
        Ui.clickable(cb, v -> a.goMarket(0));
        status.addView(cb, cl);
        col.addView(status, Ui.lpMargins(a, -2, -2, 0, 0, 0, 20));

        int next = p.nextLevel();
        String playLabel = p.completedCount() == 0 ? L.t("menu.play")
                : (p.completedCount() >= Progress.LEVEL_COUNT ? L.t("menu.play_level", Progress.LEVEL_COUNT) : L.t("menu.continue_level", next));
        col.addView(Ui.button(a, playLabel, Ui.PRIMARY, Icon.PLAY, v -> a.goLevel(next)),
                Ui.lpMargins(a, -1, Ui.dp(a, 62), 0, 0, 0, 14));

        boolean dailyDone = p.dailyDone.contains(DailyPuzzle.keyFor(Game.today()));
        LinearLayout row1 = Ui.horizontal(a);
        row1.addView(tile(Icon.GRID, L.t("common.levels"), Palette.TURQUOISE, null, () -> a.goLevels()), weight(0, 6));
        row1.addView(tile(Icon.CALENDAR, L.t("menu.daily"), Palette.GOLD, dailyDone ? null : L.t("menu.new"), () -> a.goDaily()), weight(6, 0));
        col.addView(row1, Ui.lpMargins(a, -1, -2, 0, 0, 0, 12));
        LinearLayout row2 = Ui.horizontal(a);
        row2.addView(tile(Icon.TROPHY, L.t("menu.achievements"), Palette.PURPLE,
                p.achievements.size() + "/" + Achievements.ALL.size(), () -> a.goAchievements()), weight(0, 6));
        row2.addView(tile(Icon.GEAR, L.t("menu.settings"), Palette.TEXT_DIM, null, () -> a.goSettings()), weight(6, 0));
        col.addView(row2, Ui.lpMargins(a, -1, -2, 0, 0, 0, 12));
        col.addView(Ui.button(a, L.t("menu.shop"), Ui.GOLD, Icon.BAG, v -> a.goMarket(0)), Ui.lpMargins(a, -1, Ui.dp(a, 58), 0, 0, 0, 12));

        col.addView(Ui.button(a, L.t("menu.how_to_play"), Ui.GHOST, Icon.HELP, v -> TutorialOverlay.full(a).show(a.overlayLayer())),
                Ui.lpMargins(a, -1, -2, 0, 0, 0, 0));
        col.addView(Ui.space(a, 18));
        TextView foot = Ui.text(a, L.t("menu.footer", com.isikyolu.game.BuildConfig.VERSION_NAME), 12, Palette.TEXT_FAINT, Ui.REGULAR);
        col.addView(foot);
        sv.addView(col, new ScrollView.LayoutParams(-1, -2));
        addView(sv, new LayoutParams(-1, -1));
    }

    @Override public boolean wantsBanner() { return true; }

    @Override public void onShow() {
        act.startAds(); // ilk ana menüde (açılış animasyonu bölünmez); izin formu gerekirse burada açılır
        act.retryAds();
        if (Game.takeAbandonedNotice())
            act.toast(Game.abandonedShieldUsed() ? L.t("menu.abandoned_shield") : L.n("menu.abandoned_life", 1));
    }

    private LinearLayout.LayoutParams weight(float l, float r) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(getContext(), 104), 1f);
        lp.leftMargin = Ui.dp(getContext(), l);
        lp.rightMargin = Ui.dp(getContext(), r);
        return lp;
    }

    private FrameLayout tile(int icon, String label, int color, String badge, Runnable r) {
        android.content.Context c = getContext();
        FrameLayout f = new FrameLayout(c);
        f.setBackground(Ui.card(c));
        LinearLayout col = Ui.vertical(c);
        col.setGravity(Gravity.CENTER);
        FrameLayout ic = new FrameLayout(c);
        ic.setBackground(Ui.round(Palette.withAlpha(color, 40), Ui.dp(c, 21)));
        ic.addView(Ui.icon(c, icon, color, 24), new FrameLayout.LayoutParams(Ui.dp(c, 24), Ui.dp(c, 24), Gravity.CENTER));
        col.addView(ic, new LinearLayout.LayoutParams(Ui.dp(c, 42), Ui.dp(c, 42)));
        TextView t = Ui.text(c, label, 15, Palette.WHITE, Ui.BOLD);
        t.setGravity(Gravity.CENTER);
        // sabit yükseklikli karo: uzun çeviriler en fazla iki satır
        t.setMaxLines(2);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setHyphenationFrequency(android.text.Layout.HYPHENATION_FREQUENCY_NORMAL);
        int side = Ui.dp(c, 8);
        t.setPadding(side, Ui.dp(c, 8), side, 0);
        col.addView(t);
        f.addView(col, new FrameLayout.LayoutParams(-1, -1));
        if (badge != null) {
            TextView b = Ui.text(c, badge, 11, Palette.NIGHT, Ui.BOLD);
            b.setSingleLine(true);
            b.setBackground(Ui.round(color == Palette.TEXT_DIM ? Palette.TURQUOISE : color, Ui.dp(c, 10)));
            b.setPadding(Ui.dp(c, 8), Ui.dp(c, 2), Ui.dp(c, 8), Ui.dp(c, 2));
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
            lp.setMargins(0, Ui.dp(c, 10), Ui.dp(c, 10), 0);
            f.addView(b, lp);
        }
        Ui.clickable(f, v -> r.run());
        return f;
    }
}
