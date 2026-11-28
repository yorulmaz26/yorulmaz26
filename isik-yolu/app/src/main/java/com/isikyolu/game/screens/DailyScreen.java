package com.isikyolu.game.screens;

import android.content.Context;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.isikyolu.engine.DailyPuzzle;
import com.isikyolu.engine.Level;
import com.isikyolu.engine.Progress;
import com.isikyolu.game.Game;
import com.isikyolu.game.GameLoader;
import com.isikyolu.game.MainActivity;
import com.isikyolu.game.i18n.L;
import com.isikyolu.game.ui.Icon;
import com.isikyolu.game.ui.Palette;
import com.isikyolu.game.ui.Ui;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** 8. Günlük bulmaca: her gün tarihten üretilen yeni bir bulmaca (çevrimdışı). */
public final class DailyScreen extends Screen {
    private boolean busy;

    /** Tarih, seçili dile göre ("28 Kasım 2026, Cumartesi" / "Saturday, November 28, 2026"). */
    public static String prettyDate(int epochDay) {
        Locale loc = L.locale();
        // Türkçede alışılmış biçim aynen korunur; diğer dillerde yerel uzun tarih kalıbı
        String pattern = "tr".equals(L.lang()) ? "d MMMM yyyy, EEEE"
                : android.text.format.DateFormat.getBestDateTimePattern(loc, "EEEEdMMMMyyyy");
        return format(epochDay, pattern, loc);
    }

    /** Haftanın günü, kısa (Pzt / Mon). */
    static String shortWeekday(int epochDay) { return format(epochDay, "EEE", L.locale()); }

    private static String format(int epochDay, String pattern, Locale loc) {
        SimpleDateFormat f = new SimpleDateFormat(pattern, loc);
        f.setTimeZone(TimeZone.getTimeZone("UTC")); // gün numarası UTC gece yarısı olarak çözülür
        return f.format(new Date(epochDay * 86_400_000L));
    }

    public DailyScreen(MainActivity a) {
        super(a);
        Context c = a;
        Progress p = Game.progress();
        int today = Game.today(); // saat hilesine karşı güvenilir gün
        boolean done = p.dailyDone.contains(DailyPuzzle.keyFor(today));
        LinearLayout root = Ui.vertical(c);
        root.addView(Ui.header(c, L.t("daily.title"), v -> a.goMenu(), null));
        ScrollView sv = new ScrollView(c);
        LinearLayout col = Ui.vertical(c);
        int pad = Ui.dp(c, 16);
        col.setPadding(pad, Ui.dp(c, 6), pad, pad);

        LinearLayout card = Ui.vertical(c);
        card.setBackground(Ui.card(c));
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(Ui.dp(c, 20), Ui.dp(c, 24), Ui.dp(c, 20), Ui.dp(c, 22));
        FrameLayout ic = new FrameLayout(c);
        ic.setBackground(Ui.round(0x33F5C542, Ui.dp(c, 40)));
        ic.addView(Ui.icon(c, done ? Icon.CHECK : Icon.CALENDAR, Palette.GOLD, 42), new FrameLayout.LayoutParams(Ui.dp(c, 42), Ui.dp(c, 42), Gravity.CENTER));
        card.addView(ic, new LinearLayout.LayoutParams(Ui.dp(c, 80), Ui.dp(c, 80)));
        TextView date = Ui.text(c, prettyDate(today), 19, Palette.WHITE, Ui.BOLD);
        date.setGravity(Gravity.CENTER);
        card.addView(date, Ui.lpMargins(c, -2, -2, 0, 14, 0, 4));
        TextView status = Ui.text(c, L.t(done ? "daily.status_done" : "daily.status_open"), 15, Palette.TEXT_DIM, Ui.REGULAR);
        status.setGravity(Gravity.CENTER);
        card.addView(status, Ui.lpMargins(c, -1, -2, 0, 4, 0, 16));

        // seri ve toplam
        LinearLayout stats = Ui.horizontal(c);
        stats.addView(stat(c, Icon.FLAME, 0xFFFF9F43, String.valueOf(p.currentDailyStreak(today)), L.t("daily.streak")), new LinearLayout.LayoutParams(0, -2, 1f));
        stats.addView(stat(c, Icon.CHECK, Palette.TURQUOISE, String.valueOf(p.dailyDone.size()), L.t("daily.total")), new LinearLayout.LayoutParams(0, -2, 1f));
        card.addView(stats, Ui.lpMargins(c, -1, -2, 0, 0, 0, 16));

        // son 7 gün
        LinearLayout week = Ui.horizontal(c);
        week.setGravity(Gravity.CENTER);
        for (int d = today - 6; d <= today; d++) {
            LinearLayout dc = Ui.vertical(c);
            dc.setGravity(Gravity.CENTER);
            boolean ok = p.dailyDone.contains(DailyPuzzle.keyFor(d));
            FrameLayout dot = new FrameLayout(c);
            dot.setBackground(Ui.round(ok ? Palette.TURQUOISE : (d == today ? 0x5520B8A6 : 0xFF4A5C87), Ui.dp(c, 15)));
            if (ok) dot.addView(Ui.icon(c, Icon.CHECK, Palette.NIGHT, 16), new FrameLayout.LayoutParams(Ui.dp(c, 16), Ui.dp(c, 16), Gravity.CENTER));
            dc.addView(dot, new LinearLayout.LayoutParams(Ui.dp(c, 30), Ui.dp(c, 30)));
            String dn = shortWeekday(d);
            TextView tl = Ui.text(c, dn, 11, d == today ? Palette.WHITE : Palette.TEXT_DIM, Ui.MEDIUM);
            tl.setPadding(0, Ui.dp(c, 4), 0, 0);
            dc.addView(tl);
            week.addView(dc, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        card.addView(week, Ui.lpMargins(c, -1, -2, 0, 0, 0, 18));

        LinearLayout play = Ui.button(c, L.t(done ? "daily.replay" : "daily.play"), done ? Ui.SECONDARY : Ui.GOLD, Icon.PLAY, null);
        TextView playLabel = (TextView) play.getChildAt(1);
        Ui.clickable(play, v -> {
            if (busy) return;
            busy = true;
            playLabel.setText(L.t("daily.preparing"));
            final int day = Game.today(); // ekran gece yarısından önce açıldıysa da o anın günü oynanır
            GameLoader.background(() -> DailyPuzzle.generate(day), (Level l) -> {
                busy = false;
                if (l == null) { playLabel.setText(L.t("common.retry")); a.toast(L.t("daily.failed")); return; }
                a.goDailyGame(l, day);
            });
        });
        card.addView(play, new LinearLayout.LayoutParams(-1, Ui.dp(c, 58)));
        col.addView(card);

        TextView info = Ui.text(c, L.t("daily.info"), 13, Palette.TEXT_FAINT, Ui.REGULAR);
        info.setGravity(Gravity.CENTER);
        col.addView(info, Ui.lpMargins(c, -1, -2, 8, 16, 8, 0));
        sv.addView(col);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        addView(root, new LayoutParams(-1, -1));
    }

    private static LinearLayout stat(Context c, int icon, int color, String value, String label) {
        LinearLayout l = Ui.vertical(c);
        l.setGravity(Gravity.CENTER);
        LinearLayout r = Ui.horizontal(c);
        r.setGravity(Gravity.CENTER);
        r.addView(Ui.icon(c, icon, color, 22));
        r.addView(Ui.text(c, " " + value, 24, Palette.WHITE, Ui.BOLD));
        l.addView(r);
        TextView lt = Ui.text(c, label, 12, Palette.TEXT_DIM, Ui.MEDIUM);
        lt.setGravity(Gravity.CENTER); // uzun çeviride iki satıra ortalı iner
        lt.setMaxLines(2);
        lt.setEllipsize(android.text.TextUtils.TruncateAt.END);
        l.addView(lt);
        return l;
    }

    @Override public boolean wantsBanner() { return true; }
}
