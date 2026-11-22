package com.isikyolu.game.screens;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.isikyolu.engine.Chapters;
import com.isikyolu.engine.Progress;
import com.isikyolu.game.Audio;
import com.isikyolu.game.Game;
import com.isikyolu.game.MainActivity;
import com.isikyolu.game.ui.Icon;
import com.isikyolu.game.ui.MiniStars;
import com.isikyolu.game.ui.Palette;
import com.isikyolu.game.ui.SquareFrame;
import com.isikyolu.game.ui.Ui;

/** 3. Bölüm seçme: 5 dünya sekmesi, her dünyada 10 grup × 10 bölüm, yıldızlar ve kilitler. */
public final class LevelSelectScreen extends Screen {
    private final ScrollView sv;
    private final LinearLayout list, tabs;
    private View currentChapterView;
    private int world;

    public LevelSelectScreen(MainActivity a) {
        super(a);
        Progress p = Game.progress();
        LinearLayout root = Ui.vertical(a);
        LinearLayout pill = Ui.horizontal(a);
        pill.setBackground(Ui.round(0x22F5C542, Ui.dp(a, 14)));
        pill.setPadding(Ui.dp(a, 10), Ui.dp(a, 5), Ui.dp(a, 12), Ui.dp(a, 5));
        pill.addView(Ui.icon(a, Icon.STAR, Palette.GOLD, 16));
        // dar ekranda (ör. 360 dp) başlık sığsın diye yalnızca toplam yıldız
        boolean narrow = a.getResources().getConfiguration().screenWidthDp < 400;
        pill.addView(Ui.text(a, " " + p.totalStars() + (narrow ? "" : "/" + Progress.LEVEL_COUNT * 3), 14, Palette.GOLD, Ui.BOLD));
        LinearLayout right = Ui.horizontal(a);
        right.addView(new com.isikyolu.game.ui.LivesBadge(a));
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(-2, -2);
        pl.leftMargin = Ui.dp(a, 6);
        right.addView(pill, pl);
        root.addView(Ui.header(a, "Bölümler", v -> a.goMenu(), right));

        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        tabs = Ui.horizontal(a);
        tabs.setPadding(Ui.dp(a, 12), Ui.dp(a, 2), Ui.dp(a, 12), Ui.dp(a, 6));
        hs.addView(tabs);
        root.addView(hs);

        sv = new ScrollView(a);
        sv.setVerticalScrollBarEnabled(false);
        list = Ui.vertical(a);
        int pad = Ui.dp(a, 16);
        list.setPadding(pad, Ui.dp(a, 4), pad, Ui.dp(a, 28));
        sv.addView(list);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        addView(root, new LayoutParams(-1, -1));
        world = Chapters.worldOf(p.nextLevel());
        showWorld(world, true);
        final int w0 = world;
        hs.post(() -> { View t = tabs.getChildAt(w0 - 1); if (t != null) hs.smoothScrollTo(Math.max(0, t.getLeft() - Ui.dp(a, 24)), 0); });
    }

    private void buildTabs() {
        Context c = getContext();
        Progress p = Game.progress();
        tabs.removeAllViews();
        for (int w = 1; w <= Chapters.WORLDS; w++) {
            final int ww = w;
            boolean open = p.isUnlocked((w - 1) * 100 + 1);
            boolean sel = w == world;
            LinearLayout t = Ui.vertical(c);
            t.setGravity(Gravity.CENTER);
            t.setPadding(Ui.dp(c, 14), Ui.dp(c, 8), Ui.dp(c, 14), Ui.dp(c, 8));
            GradientDrawable bg;
            if (sel) bg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xFF22C7B2, 0xFF3D8CE8});
            else bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF394D79, 0xFF2F3F61});
            bg.setCornerRadius(Ui.dp(c, 16));
            if (!sel) bg.setStroke(Ui.dp(c, 1), 0x33A9C1FF);
            t.setBackground(bg);
            TextView n = Ui.text(c, Chapters.WORLD_NAMES[w - 1], 14, sel ? Palette.WHITE : (open ? Palette.WHITE : Palette.TEXT_FAINT), Ui.BOLD);
            t.addView(n);
            TextView sub = Ui.text(c, open ? p.completedInWorld(w) + "/100" : "Kilitli", 11, sel ? 0xDDFFFFFF : Palette.TEXT_DIM, Ui.MEDIUM);
            t.addView(sub);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = Ui.dp(c, 8);
            Ui.clickable(t, v -> { if (ww != world) showWorld(ww, false); });
            tabs.addView(t, lp);
        }
    }

    private void showWorld(int w, boolean scrollToCurrent) {
        world = w;
        buildTabs();
        Progress p = Game.progress();
        Context c = getContext();
        list.removeAllViews();
        currentChapterView = null;
        TextView intro = Ui.text(c, Chapters.WORLD_INTROS[w - 1], 14, Palette.TEXT_DIM, Ui.REGULAR);
        intro.setGravity(Gravity.CENTER);
        list.addView(intro, Ui.lpMargins(c, -1, -2, 8, 4, 8, 6));
        int next = p.nextLevel();
        for (int ch = (w - 1) * 10 + 1; ch <= w * 10; ch++) {
            View v = chapter((MainActivity) c, p, ch, next);
            list.addView(v, Ui.lpMargins(c, -1, -2, 0, 8, 0, 8));
            if (ch == Chapters.chapterOf(next)) currentChapterView = v;
        }
        list.setAlpha(0f);
        list.animate().alpha(1f).setDuration(220).start();
        if (scrollToCurrent && currentChapterView != null)
            sv.post(() -> sv.scrollTo(0, Math.max(0, currentChapterView.getTop() - Ui.dp(c, 8))));
        else sv.scrollTo(0, 0);
    }

    private View chapter(MainActivity a, Progress p, int ch, int next) {
        Context c = a;
        LinearLayout card = Ui.vertical(c);
        card.setBackground(Ui.card(c));
        int pad = Ui.dp(c, 14);
        card.setPadding(pad, pad, pad, pad);
        int first = (ch - 1) * 10 + 1;
        boolean open = p.isUnlocked(first);
        int stars = 0;
        for (int i = first; i < first + 10; i++) stars += p.stars[i];

        LinearLayout head = Ui.horizontal(c);
        TextView num = Ui.text(c, String.valueOf(ch), 18, open ? Palette.NIGHT : Palette.TEXT_DIM, Ui.BOLD);
        num.setGravity(Gravity.CENTER);
        num.setBackground(Ui.round(open ? chapterColor(ch) : 0xFF4A5C87, Ui.dp(c, 18)));
        head.addView(num, new LinearLayout.LayoutParams(Ui.dp(c, 36), Ui.dp(c, 36)));
        LinearLayout tc = Ui.vertical(c);
        tc.setPadding(Ui.dp(c, 12), 0, 0, 0);
        tc.addView(Ui.text(c, Chapters.NAMES[ch - 1], 17, open ? Palette.WHITE : Palette.TEXT_DIM, Ui.BOLD));
        tc.addView(Ui.text(c, first + "–" + (first + 9) + " · " + Chapters.topic(ch), 13, Palette.TEXT_DIM, Ui.REGULAR));
        head.addView(tc, new LinearLayout.LayoutParams(0, -2, 1f));
        head.addView(Ui.icon(c, open ? Icon.STAR : Icon.LOCK, open ? Palette.GOLD : Palette.TEXT_FAINT, 16));
        head.addView(Ui.text(c, " " + stars + "/30", 13, open ? Palette.GOLD : Palette.TEXT_FAINT, Ui.MEDIUM));
        card.addView(head);

        for (int row = 0; row < 2; row++) {
            LinearLayout r = Ui.horizontal(c);
            for (int col = 0; col < 5; col++) {
                int id = first + row * 5 + col;
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
                int m = Ui.dp(c, 4);
                lp.setMargins(m, m, m, m);
                r.addView(tile(a, p, id, id == next), lp);
            }
            card.addView(r, Ui.lpMargins(c, -1, -2, -4, row == 0 ? 10 : 0, -4, 0));
        }
        return card;
    }

    static int chapterColor(int ch) {
        ch = Chapters.mechanicOf(ch);
        int[] cols = {Palette.TURQUOISE, 0xFF34C6D8, 0xFF8F9BB8, 0xFFFF6F7D, 0xFFE15CFF, 0xFFB8C8FF,
                Palette.PURPLE, Palette.GOLD, 0xFFFF9F43, 0xFF3DDC84};
        return cols[(ch - 1) % cols.length];
    }

    private View tile(MainActivity a, Progress p, int id, boolean isNext) {
        Context c = a;
        SquareFrame f = new SquareFrame(c);
        boolean open = p.isUnlocked(id);
        int stars = p.stars[id];
        GradientDrawable bg = Ui.round(open ? (stars > 0 ? 0xFF3F537D : 0xFF394A73) : 0xFF2B3654, Ui.dp(c, 14));
        if (isNext) bg.setStroke(Ui.dp(c, 2), Palette.TURQUOISE);
        else if (open) bg.setStroke(Ui.dp(c, 1), 0xFF506692);
        f.setBackground(bg);
        if (open) {
            TextView t = Ui.text(c, String.valueOf(id), 17, Palette.WHITE, Ui.BOLD);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER);
            lp.bottomMargin = Ui.dp(c, 8);
            f.addView(t, lp);
            MiniStars ms = new MiniStars(c, stars);
            FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(-1, Ui.dp(c, 11), Gravity.BOTTOM);
            sl.bottomMargin = Ui.dp(c, 8);
            sl.leftMargin = sl.rightMargin = Ui.dp(c, 6);
            f.addView(ms, sl);
            Ui.clickable(f, v -> a.goLevel(id));
        } else {
            f.addView(Ui.icon(c, Icon.LOCK, Palette.TEXT_FAINT, 18), new FrameLayout.LayoutParams(Ui.dp(c, 18), Ui.dp(c, 18), Gravity.CENTER));
            f.setOnClickListener(v -> {
                Game.audio().play(Audio.LOCKED);
                Game.haptics().error();
                v.animate().translationX(Ui.dp(c, 4)).setDuration(50).withEndAction(() ->
                        v.animate().translationX(-Ui.dp(c, 4)).setDuration(50).withEndAction(() ->
                                v.animate().translationX(0).setDuration(50).start()).start()).start();
                a.toast("Önce " + (id - 1) + ". bölümü tamamla");
            });
        }
        f.setContentDescription("Bölüm " + id + (open ? ", " + stars + " yıldız" : ", kilitli"));
        return f;
    }

    @Override public boolean wantsBanner() { return true; }
}
