package com.isikyolu.game.screens;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.isikyolu.engine.Achievements;
import com.isikyolu.engine.Progress;
import com.isikyolu.game.Game;
import com.isikyolu.game.MainActivity;
import com.isikyolu.game.ui.Icon;
import com.isikyolu.game.ui.Palette;
import com.isikyolu.game.ui.Ui;

/** 9. Başarılar listesi ve ilerleme çubukları. */
public final class AchievementsScreen extends Screen {

    public AchievementsScreen(MainActivity a) {
        super(a);
        Context c = a;
        Progress p = Game.progress();
        a.checkAchievements(); // yeni başarılar ödülleriyle birlikte verilir
        LinearLayout root = Ui.vertical(c);
        TextView count = Ui.text(c, p.achievements.size() + "/" + Achievements.ALL.size(), 15, Palette.GOLD, Ui.BOLD);
        root.addView(Ui.header(c, "Başarılar", v -> a.goMenu(), count));
        ScrollView sv = new ScrollView(c);
        LinearLayout list = Ui.vertical(c);
        int pad = Ui.dp(c, 16);
        list.setPadding(pad, Ui.dp(c, 4), pad, pad);
        for (Achievements.Def d : Achievements.ALL) list.addView(item(c, p, d), Ui.lpMargins(c, -1, -2, 0, 5, 0, 5));
        sv.addView(list);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        addView(root, new LayoutParams(-1, -1));
    }

    private static View item(Context c, Progress p, Achievements.Def d) {
        boolean got = p.achievements.contains(d.id);
        LinearLayout row = Ui.horizontal(c);
        row.setBackground(Ui.card(c));
        int pad = Ui.dp(c, 14);
        row.setPadding(pad, pad, pad, pad);
        if (!got) row.setAlpha(0.8f);
        FrameLayout ic = new FrameLayout(c);
        ic.setBackground(Ui.round(got ? 0x33F5C542 : 0xFF3F4F75, Ui.dp(c, 24)));
        ic.addView(Ui.icon(c, got ? Icon.TROPHY : Icon.LOCK, got ? Palette.GOLD : Palette.TEXT_FAINT, 24),
                new FrameLayout.LayoutParams(Ui.dp(c, 24), Ui.dp(c, 24), Gravity.CENTER));
        row.addView(ic, new LinearLayout.LayoutParams(Ui.dp(c, 48), Ui.dp(c, 48)));
        LinearLayout col = Ui.vertical(c);
        col.setPadding(Ui.dp(c, 14), 0, 0, 0);
        col.addView(Ui.text(c, d.title, 16, got ? Palette.WHITE : Palette.TEXT_DIM, Ui.BOLD));
        col.addView(Ui.text(c, d.description, 13, Palette.TEXT_DIM, Ui.REGULAR));
        int cur = Math.min(d.current(p), d.target()), tgt = d.target();
        FrameLayout bar = new FrameLayout(c);
        bar.setBackground(Ui.round(0xFF45567D, Ui.dp(c, 4)));
        View fillV = new View(c);
        fillV.setBackground(Ui.round(got ? Palette.GOLD : Palette.TURQUOISE, Ui.dp(c, 4)));
        bar.addView(fillV, new FrameLayout.LayoutParams(0, -1));
        bar.post(() -> {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) fillV.getLayoutParams();
            lp.width = (int) (bar.getWidth() * (tgt == 0 ? 1f : cur / (float) tgt));
            fillV.setLayoutParams(lp);
        });
        LinearLayout br = Ui.horizontal(c);
        br.addView(bar, new LinearLayout.LayoutParams(0, Ui.dp(c, 7), 1f));
        TextView num = Ui.text(c, "  " + cur + "/" + tgt, 12, Palette.TEXT_DIM, Ui.MEDIUM);
        br.addView(num);
        col.addView(br, Ui.lpMargins(c, -1, -2, 0, 8, 0, 0));
        row.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        return row;
    }

    @Override public boolean wantsBanner() { return true; }
}
