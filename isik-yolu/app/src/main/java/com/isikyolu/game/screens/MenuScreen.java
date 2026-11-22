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
        TextView title = Ui.text(a, "Işık Yolu", 40, Palette.WHITE, Ui.BOLD);
        Ui.gradientText(title, 0xFFFFE08A, 0xFFFFFFFF, 0xFF7DF0E0);
        title.setShadowLayer(Ui.dp(a, 14), 0, 0, 0x6620B8A6);
        col.addView(title);
        col.addView(Ui.text(a, "Işığı yönlendir, yolu bul", 15, Palette.TURQUOISE, Ui.MEDIUM));

        // yıldız ve ilerleme göstergesi
        LinearLayout pill = Ui.horizontal(a);
        pill.setBackground(Ui.round(0x22F5C542, Ui.dp(a, 16)));
        pill.setPadding(Ui.dp(a, 14), Ui.dp(a, 6), Ui.dp(a, 16), Ui.dp(a, 6));
        pill.addView(Ui.icon(a, Icon.STAR, Palette.GOLD, 18));
        TextView st = Ui.text(a, "  " + p.totalStars() + " / " + Progress.LEVEL_COUNT * 3 + "   ·   " + p.completedCount() + " / " + Progress.LEVEL_COUNT + " bölüm", 14, Palette.GOLD, Ui.MEDIUM);
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
        String playLabel = p.completedCount() == 0 ? "Oyna" : (p.completedCount() >= Progress.LEVEL_COUNT ? "Oyna · Bölüm " + Progress.LEVEL_COUNT : "Devam Et · Bölüm " + next);
        col.addView(Ui.button(a, playLabel, Ui.PRIMARY, Icon.PLAY, v -> a.goLevel(next)),
                Ui.lpMargins(a, -1, Ui.dp(a, 62), 0, 0, 0, 14));

        boolean dailyDone = p.dailyDone.contains(DailyPuzzle.keyFor(Game.today()));
        LinearLayout row1 = Ui.horizontal(a);
        row1.addView(tile(Icon.GRID, "Bölümler", Palette.TURQUOISE, null, () -> a.goLevels()), weight(0, 6));
        row1.addView(tile(Icon.CALENDAR, "Günlük Bulmaca", Palette.GOLD, dailyDone ? null : "YENİ", () -> a.goDaily()), weight(6, 0));
        col.addView(row1, Ui.lpMargins(a, -1, -2, 0, 0, 0, 12));
        LinearLayout row2 = Ui.horizontal(a);
        row2.addView(tile(Icon.TROPHY, "Başarılar", Palette.PURPLE,
                p.achievements.size() + "/" + Achievements.ALL.size(), () -> a.goAchievements()), weight(0, 6));
        row2.addView(tile(Icon.GEAR, "Ayarlar", Palette.TEXT_DIM, null, () -> a.goSettings()), weight(6, 0));
        col.addView(row2, Ui.lpMargins(a, -1, -2, 0, 0, 0, 12));
        col.addView(Ui.button(a, "Market", Ui.GOLD, Icon.BAG, v -> a.goMarket(0)), Ui.lpMargins(a, -1, Ui.dp(a, 58), 0, 0, 0, 12));

        col.addView(Ui.button(a, "Nasıl Oynanır?", Ui.GHOST, Icon.HELP, v -> TutorialOverlay.full(a).show(a.overlayLayer())),
                Ui.lpMargins(a, -1, -2, 0, 0, 0, 0));
        col.addView(Ui.space(a, 18));
        TextView foot = Ui.text(a, "Çevrimdışı oynanır · v1.7.0", 12, Palette.TEXT_FAINT, Ui.REGULAR);
        col.addView(foot);
        sv.addView(col, new ScrollView.LayoutParams(-1, -2));
        addView(sv, new LayoutParams(-1, -1));
    }

    @Override public boolean wantsBanner() { return true; }

    @Override public void onShow() {
        act.startAds(); // ilk ana menüde (açılış animasyonu bölünmez); izin formu gerekirse burada açılır
        act.retryAds();
        if (Game.takeAbandonedNotice())
            act.toast(Game.abandonedShieldUsed() ? "Yarım bırakılan bölümde Can Kalkanı canını korudu" : "Yarım bırakılan bölüm için 1 can harcandı");
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
        t.setPadding(0, Ui.dp(c, 8), 0, 0);
        col.addView(t);
        f.addView(col, new FrameLayout.LayoutParams(-1, -1));
        if (badge != null) {
            TextView b = Ui.text(c, badge, 11, Palette.NIGHT, Ui.BOLD);
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
