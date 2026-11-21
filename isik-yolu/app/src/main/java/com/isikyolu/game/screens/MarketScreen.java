package com.isikyolu.game.screens;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.isikyolu.engine.Economy;
import com.isikyolu.engine.GameSession;
import com.isikyolu.engine.Lives;
import com.isikyolu.engine.Progress;
import com.isikyolu.game.Audio;
import com.isikyolu.game.Game;
import com.isikyolu.game.MainActivity;
import com.isikyolu.game.ui.BoardView;
import com.isikyolu.game.ui.CurrencyBar;
import com.isikyolu.game.ui.Demos;
import com.isikyolu.game.ui.Icon;
import com.isikyolu.game.ui.LivesBadge;
import com.isikyolu.game.ui.Overlay;
import com.isikyolu.game.ui.Palette;
import com.isikyolu.game.ui.Ui;

import java.util.List;

/** Market: günlük teklifler, güçlendiriciler, canlar, görünümler ve kazanç rehberi. */
public final class MarketScreen extends Screen {
    private static final String[] TABS = {"Teklifler", "Güçlendiriciler", "Canlar", "Görünüm", "Nasıl Kazanılır"};
    private static final int[] TAB_ICONS = {Icon.TAG, Icon.BULB, Icon.HEART, Icon.BRUSH, Icon.CRYSTAL};

    private final LinearLayout tabs, list;
    private final ScrollView sv;
    private final HorizontalScrollView hs;
    private final CurrencyBar bar;
    private int tab;
    private TextView countdown;
    private final Runnable tick = this::tickCountdown;

    public MarketScreen(MainActivity a, int startTab) {
        super(a);
        tab = startTab;
        LinearLayout root = Ui.vertical(a);
        bar = new CurrencyBar(a);
        root.addView(Ui.header(a, "Market", v -> a.goMenu(), bar));
        hs = new HorizontalScrollView(a);
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
        render(false);
    }

    private int day() { return Game.shopDay(); }

    private void buildTabs() {
        Context c = getContext();
        tabs.removeAllViews();
        for (int i = 0; i < TABS.length; i++) {
            final int ti = i;
            boolean sel = i == tab;
            LinearLayout t = Ui.horizontal(c);
            t.setPadding(Ui.dp(c, 12), Ui.dp(c, 8), Ui.dp(c, 14), Ui.dp(c, 8));
            GradientDrawable bg = sel ? new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xFFFFD45E, 0xFFF09A38})
                    : new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF2E4370, 0xFF26385F});
            bg.setCornerRadius(Ui.dp(c, 16));
            t.setBackground(bg);
            t.addView(Ui.icon(c, TAB_ICONS[i], sel ? Palette.NIGHT : Palette.WHITE, 16));
            TextView n = Ui.text(c, " " + TABS[i], 14, sel ? Palette.NIGHT : Palette.WHITE, Ui.BOLD);
            t.addView(n);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = Ui.dp(c, 8);
            Ui.clickable(t, v -> { if (ti != tab) { tab = ti; render(true); } });
            tabs.addView(t, lp);
        }
        hs.post(() -> {
            View sel = tabs.getChildAt(tab);
            if (sel != null) hs.smoothScrollTo(Math.max(0, sel.getLeft() - Ui.dp(c, 40)), 0);
        });
    }

    private void render(boolean animate) {
        buildTabs();
        bar.refresh();
        list.removeAllViews();
        removeCallbacks(tick);
        countdown = null;
        switch (tab) {
            case 0: renderDeals(); break;
            case 1: renderCategory(Economy.CAT_BOOST); break;
            case 2: renderLives(); break;
            case 3: renderLooks(); break;
            default: renderGuide();
        }
        if (animate) { list.setAlpha(0f); list.animate().alpha(1f).setDuration(200).start(); sv.scrollTo(0, 0); }
    }

    // ---------- sekmeler ----------

    private void renderDeals() {
        Context c = getContext();
        LinearLayout head = Ui.horizontal(c);
        head.addView(Ui.text(c, "Günün teklifleri", 18, Palette.WHITE, Ui.BOLD), new LinearLayout.LayoutParams(0, -2, 1f));
        countdown = Ui.text(c, "", 13, Palette.GOLD, Ui.MEDIUM);
        head.addView(countdown);
        list.addView(head, Ui.lpMargins(c, -1, -2, 4, 4, 4, 2));
        list.addView(note("Her gün 3 indirimli ürün, her biri tek adet. Teklifte günlük ürün sınırı uygulanmaz."));
        Progress p = Game.progress();
        int d = day();
        List<Economy.Deal> deals = Economy.deals(d);
        for (Economy.Deal deal : deals) {
            String reason = Economy.dealBlockReason(p, deal, d);
            String info = "%" + deal.discount + " indirim · normal fiyat " + deal.item.basePrice;
            list.addView(card(deal.item, info, deal.price, reason, "%" + deal.discount, () -> confirmBuy(deal.item.name, deal.item.currency, deal.price, () -> {
                if (Economy.buyDeal(Game.progress(), deal, day(), Game.clock())) bought(deal.item);
            })), Ui.lpMargins(c, -1, -2, 0, 6, 0, 6));
        }
        tickCountdown();
    }

    private void renderCategory(int cat) {
        Context c = getContext();
        Progress p = Game.progress();
        list.addView(inventory(), Ui.lpMargins(c, -1, -2, 0, 4, 0, 6));
        list.addView(note("Aynı gün tekrar alınan ürünlerin fiyatı her alımda artar. Sayaçlar gece yarısı sıfırlanır."));
        for (Economy.Item it : Economy.ITEMS) {
            if (it.category != cat) continue;
            list.addView(itemCard(p, it), Ui.lpMargins(c, -1, -2, 0, 6, 0, 6));
        }
    }

    private void renderLives() {
        Context c = getContext();
        Progress p = Game.progress();
        LinearLayout row = Ui.horizontal(c);
        row.setGravity(Gravity.CENTER);
        row.addView(new LivesBadge(c));
        list.addView(row, Ui.lpMargins(c, -1, -2, 0, 4, 0, 6));
        list.addView(note("Canlar 15 dakikada bir kendiliğinden yenilenir. Beklemek istemezsen buradan alabilirsin."));
        for (Economy.Item it : Economy.ITEMS) if (it.category == Economy.CAT_LIVES) list.addView(itemCard(p, it), Ui.lpMargins(c, -1, -2, 0, 6, 0, 6));
    }

    private void renderLooks() {
        Context c = getContext();
        Progress p = Game.progress();
        list.addView(note("Görünümler bir kez alınır ve kalıcıdır. Kartlara dokunarak önizleyebilirsin."));
        String[] titles = {"Ayna Kaplamaları", "Tahta Temaları", "Işın Efektleri"};
        Economy.Kind[] kinds = {Economy.Kind.MIRROR, Economy.Kind.BOARD, Economy.Kind.BEAM};
        String[] defaults = {"Gümüş Aynalar (varsayılan)", "Gece Tahtası (varsayılan)", "Klasik Işın (varsayılan)"};
        for (int k = 0; k < 3; k++) {
            list.addView(Ui.text(c, titles[k], 17, Palette.WHITE, Ui.BOLD), Ui.lpMargins(c, -2, -2, 4, 12, 0, 2));
            final Economy.Kind kind = kinds[k];
            boolean defOn = kind == Economy.Kind.MIRROR ? p.mirrorSkin.equals(Economy.DEFAULT_MIRROR)
                    : kind == Economy.Kind.BOARD ? p.boardSkin.equals(Economy.DEFAULT_BOARD) : p.beamSkin.equals(Economy.DEFAULT_BEAM);
            list.addView(defaultCard(defaults[k], defOn, () -> { Economy.equipDefault(Game.progress(), kind); Game.save(); render(false); }),
                    Ui.lpMargins(c, -1, -2, 0, 6, 0, 6));
            for (Economy.Item it : Economy.ITEMS) if (it.kind == kind) list.addView(itemCard(p, it), Ui.lpMargins(c, -1, -2, 0, 6, 0, 6));
        }
    }

    private void renderGuide() {
        Context c = getContext();
        String[][] rows = {
                {"Bölümü ilk kez bitirmek", "4 kristal (her dünyada +2: 5. dünyada 12)"},
                {"Bir bölümü ilk kez 3 yıldıza çıkarmak", "+3 kristal"},
                {"Bir grubun 10 bölümünü 3 yıldızla bitirmek", "1 Prizma Taşı"},
                {"Günlük bulmaca", "20 kristal + seri başına 3 (en fazla +30)"},
                {"Her başarı", "30 kristal"},
                {"Dünya başarıları, 900 yıldız, 100 kusursuz bölüm, 30 günlük bulmaca", "+1 Prizma Taşı"},
                {"Işık Yolu Efsanesi, Kusursuz Işık", "+3 Prizma Taşı"},
                {"Aynı bölümü tekrar oynamak", "kazandırmaz"},
        };
        list.addView(Ui.text(c, "Kazanç rehberi", 18, Palette.WHITE, Ui.BOLD), Ui.lpMargins(c, -2, -2, 4, 4, 0, 6));
        LinearLayout card = Ui.vertical(c);
        card.setBackground(Ui.card(c));
        card.setPadding(Ui.dp(c, 16), Ui.dp(c, 10), Ui.dp(c, 16), Ui.dp(c, 10));
        for (String[] r : rows) {
            LinearLayout row = Ui.vertical(c);
            row.setPadding(0, Ui.dp(c, 7), 0, Ui.dp(c, 7));
            row.addView(Ui.text(c, r[0], 15, Palette.WHITE, Ui.MEDIUM));
            row.addView(Ui.text(c, r[1], 13, CurrencyBar.CRYSTAL_COLOR, Ui.MEDIUM));
            card.addView(row);
        }
        list.addView(card);
        Progress p = Game.progress();
        list.addView(note("Şimdiye kadar kazandığın toplam kristal: " + p.crystalsEarned + " · alışveriş sayısı: " + p.purchases));
        list.addView(note("Market tamamen oyun içi para kullanır; gerçek para ile satın alma yoktur."));
    }

    // ---------- kartlar ----------

    private View inventory() {
        Context c = getContext();
        Progress p = Game.progress();
        LinearLayout r = Ui.horizontal(c);
        r.setBackground(Ui.card(c));
        r.setPadding(Ui.dp(c, 12), Ui.dp(c, 10), Ui.dp(c, 12), Ui.dp(c, 10));
        r.addView(invCell(c, Icon.BULB, Palette.GOLD, p.hints, "İpucu"), new LinearLayout.LayoutParams(0, -2, 1f));
        r.addView(invCell(c, Icon.TICKET, 0xFF7DF0E0, p.tickets, "Bilet"), new LinearLayout.LayoutParams(0, -2, 1f));
        r.addView(invCell(c, Icon.SHIELD, 0xFFFF8FA3, p.shields, "Kalkan"), new LinearLayout.LayoutParams(0, -2, 1f));
        return r;
    }

    private static View invCell(Context c, int icon, int color, int n, String label) {
        LinearLayout l = Ui.horizontal(c);
        l.setGravity(Gravity.CENTER);
        l.addView(Ui.icon(c, icon, color, 20));
        l.addView(Ui.text(c, " " + n + " " + label, 14, Palette.WHITE, Ui.MEDIUM));
        return l;
    }

    private TextView note(String s) {
        TextView t = Ui.text(getContext(), s, 13, Palette.TEXT_DIM, Ui.REGULAR);
        t.setLineSpacing(0, 1.15f);
        t.setPadding(Ui.dp(getContext(), 4), Ui.dp(getContext(), 2), Ui.dp(getContext(), 4), Ui.dp(getContext(), 6));
        return t;
    }

    static int iconFor(Economy.Item it) {
        switch (it.kind) {
            case HINT: case HINT3: return Icon.BULB;
            case TICKET: return Icon.TICKET;
            case SHIELD: return Icon.SHIELD;
            case CONVERT: return Icon.GEM;
            case LIFE: case LIFE_FULL: return Icon.HEART;
            default: return Icon.BRUSH;
        }
    }

    static int colorFor(Economy.Item it) {
        switch (it.kind) {
            case HINT: case HINT3: return Palette.GOLD;
            case TICKET: return 0xFF7DF0E0;
            case SHIELD: case LIFE: case LIFE_FULL: return 0xFFFF7A90;
            case CONVERT: return CurrencyBar.GEM_COLOR;
            case MIRROR: return com.isikyolu.game.ui.Skins.mirror(it.skin).glass[2];
            case BOARD: return com.isikyolu.game.ui.Skins.board(it.skin).frame[1];
            default: return it.skin.equals("beam_plasma") ? 0xFFE15CFF : it.skin.equals("beam_stardust") ? 0xFFFFE9A8 : Palette.GOLD;
        }
    }

    private View itemCard(Progress p, Economy.Item it) {
        int d = day();
        int price = Economy.price(p, it, d);
        String reason = Economy.blockReason(p, it, d, price);
        StringBuilder info = new StringBuilder();
        if (it.cosmetic()) {
            if (p.owned.contains(it.skin)) info.append(Economy.equipped(p, it) ? "Kullanılıyor" : "Sahipsin");
            else if (it.unlockLevel > 0 && p.stars[it.unlockLevel] == 0) info.append("Bölüm ").append(it.unlockLevel).append(" gerekli");
            else info.append(it.currency == Economy.GEM ? "Nadir görünüm" : "Kalıcı görünüm");
        } else {
            if (it.dailyLimit > 0) info.append("Bugün ").append(Economy.boughtToday(p, it.id, d)).append("/").append(it.dailyLimit);
            if (it.step > 0) info.append(" · her alımda +").append(it.step);
        }
        if (it.cosmetic() && p.owned.contains(it.skin)) {
            boolean on = Economy.equipped(p, it);
            return card(it, info.toString(), -1, null, null, on ? null : () -> { Economy.equip(Game.progress(), it); Game.save(); Game.audio().play(Audio.CLICK); render(false); },
                    on ? "Kullanılıyor" : "Kullan");
        }
        Runnable buy = () -> confirmBuy(it.name, it.currency, price, () -> {
            if (Economy.buy(Game.progress(), it, day(), Game.clock())) bought(it);
        });
        if (it.cosmetic()) {
            View cardV = card(it, info.toString(), price, reason, null, buy);
            Ui.clickable(cardV, v -> preview(it, price, reason));
            return cardV;
        }
        return card(it, info.toString(), price, reason, null, buy);
    }

    private View defaultCard(String name, boolean on, Runnable use) {
        Context c = getContext();
        LinearLayout r = Ui.horizontal(c);
        r.setBackground(Ui.card(c));
        r.setPadding(Ui.dp(c, 14), Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12));
        r.addView(Ui.text(c, name, 15, Palette.WHITE, Ui.MEDIUM), new LinearLayout.LayoutParams(0, -2, 1f));
        r.addView(actionButton(on ? "Kullanılıyor" : "Kullan", !on, on ? null : use));
        return r;
    }

    private View card(Economy.Item it, String info, int price, String reason, String badge, Runnable onBuy) {
        return card(it, info, price, reason, badge, onBuy, null);
    }

    /** Ürün kartı: ikon, ad, açıklama, bilgi satırı ve fiyat/eylem butonu. */
    private View card(Economy.Item it, String info, int price, String reason, String badge, Runnable onAction, String actionLabel) {
        Context c = getContext();
        LinearLayout r = Ui.horizontal(c);
        r.setBackground(Ui.card(c));
        r.setPadding(Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12));
        FrameLayout ic = new FrameLayout(c);
        int col = colorFor(it);
        ic.setBackground(Ui.round(Palette.withAlpha(col, 50), Ui.dp(c, 24)));
        ic.addView(Ui.icon(c, iconFor(it), col, 26), new FrameLayout.LayoutParams(Ui.dp(c, 26), Ui.dp(c, 26), Gravity.CENTER));
        if (badge != null) {
            TextView b = Ui.text(c, badge, 10, Palette.WHITE, Ui.BOLD);
            b.setBackground(Ui.round(0xFFFF5C7A, Ui.dp(c, 8)));
            b.setPadding(Ui.dp(c, 5), Ui.dp(c, 1), Ui.dp(c, 5), Ui.dp(c, 1));
            ic.addView(b, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END));
        }
        r.addView(ic, new LinearLayout.LayoutParams(Ui.dp(c, 50), Ui.dp(c, 50)));
        LinearLayout tc = Ui.vertical(c);
        tc.setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 8), 0);
        tc.addView(Ui.text(c, it.name, 16, Palette.WHITE, Ui.BOLD));
        tc.addView(Ui.text(c, it.description, 13, Palette.TEXT_DIM, Ui.REGULAR));
        if (info != null && !info.isEmpty()) tc.addView(Ui.text(c, info, 12, Palette.GOLD, Ui.MEDIUM));
        if (reason != null && price >= 0) tc.addView(Ui.text(c, reason, 12, 0xFFFF9AA8, Ui.MEDIUM));
        r.addView(tc, new LinearLayout.LayoutParams(0, -2, 1f));
        if (price < 0) r.addView(actionButton(actionLabel, onAction != null, onAction));
        else r.addView(priceButton(it.currency, price, reason == null, () -> {
            if (reason != null) { Game.audio().play(Audio.LOCKED); Game.haptics().error(); act.toast(reason); return; }
            onAction.run();
        }));
        return r;
    }

    private View priceButton(int currency, int price, boolean enabled, Runnable r) {
        Context c = getContext();
        LinearLayout b = Ui.horizontal(c);
        b.setGravity(Gravity.CENTER);
        GradientDrawable bg = enabled ? new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xFF22C7B2, 0xFF3D8CE8})
                : new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF3A4A70, 0xFF314064});
        bg.setCornerRadius(Ui.dp(c, 14));
        b.setBackground(bg);
        b.setPadding(Ui.dp(c, 10), Ui.dp(c, 8), Ui.dp(c, 12), Ui.dp(c, 8));
        b.addView(Ui.icon(c, currency == Economy.GEM ? Icon.GEM : Icon.CRYSTAL, currency == Economy.GEM ? CurrencyBar.GEM_COLOR : CurrencyBar.CRYSTAL_COLOR, 16));
        b.addView(Ui.text(c, " " + price, 15, enabled ? Palette.WHITE : Palette.TEXT_DIM, Ui.BOLD));
        b.setMinimumWidth(Ui.dp(c, 76));
        Ui.clickable(b, v -> r.run());
        return b;
    }

    private View actionButton(String label, boolean enabled, Runnable r) {
        Context c = getContext();
        TextView b = Ui.text(c, label, 14, enabled ? Palette.WHITE : Palette.TEXT_DIM, Ui.BOLD);
        b.setGravity(Gravity.CENTER);
        b.setBackground(Ui.round(enabled ? 0xFF6D5DFB : 0xFF314064, Ui.dp(c, 14)));
        b.setPadding(Ui.dp(c, 14), Ui.dp(c, 9), Ui.dp(c, 14), Ui.dp(c, 9));
        if (enabled && r != null) Ui.clickable(b, v -> r.run());
        return b;
    }

    // ---------- satın alma ----------

    private void confirmBuy(String name, int currency, int price, Runnable doBuy) {
        boolean expensive = currency == Economy.GEM || price >= 150;
        if (!expensive) { doBuy.run(); return; }
        Overlay o = new Overlay(act);
        o.title(name);
        o.body("Fiyat: " + price + (currency == Economy.GEM ? " Prizma Taşı" : " kristal")
                + "\nBakiye: " + Economy.balance(Game.progress(), currency) + "\n\nSatın almak istiyor musun?");
        o.button("Satın Al", Ui.GOLD, Icon.BAG, v -> { o.dismiss(); doBuy.run(); });
        o.button("Vazgeç", Ui.SECONDARY, -1, v -> o.dismiss());
        o.show(act.overlayLayer());
    }

    private void bought(Economy.Item it) {
        Game.save();
        Game.audio().play(Audio.ACHIEVE);
        Game.haptics().success();
        act.toast(it.name + " alındı!");
        act.checkAchievements();
        render(false);
    }

    /** Görünüm önizlemesi: örnek tahta seçilen görünümle çizilir. */
    private void preview(Economy.Item it, int price, String reason) {
        Context c = getContext();
        Overlay o = new Overlay(act);
        TextView tag = Ui.text(c, "ÖNİZLEME", 13, Palette.TURQUOISE, Ui.BOLD);
        tag.setLetterSpacing(0.08f);
        o.cardView().addView(tag);
        o.title(it.name);
        BoardView bv = new BoardView(c);
        bv.setInputEnabled(false);
        bv.bind(new GameSession(it.kind == Economy.Kind.BOARD ? Demos.chapter(4) : Demos.chapter(2)));
        Progress p = Game.progress();
        bv.setSkinOverride(it.kind == Economy.Kind.MIRROR ? it.skin : null, it.kind == Economy.Kind.BOARD ? it.skin : null,
                it.kind == Economy.Kind.BEAM ? it.skin : null);
        o.cardView().addView(bv, new LinearLayout.LayoutParams(-1, Ui.dp(c, 200)));
        o.body(it.description + (reason != null ? "\n" + reason : ""));
        if (p.owned.contains(it.skin)) {
            o.button(Economy.equipped(p, it) ? "Kullanılıyor" : "Kullan", Ui.PRIMARY, Icon.CHECK, v -> { o.dismiss(); Economy.equip(Game.progress(), it); Game.save(); render(false); });
        } else {
            o.button((it.currency == Economy.GEM ? "Satın Al · " + price + " taş" : "Satın Al · " + price + " kristal"),
                    reason == null ? Ui.GOLD : Ui.SECONDARY, Icon.BAG, v -> {
                        if (reason != null) { act.toast(reason); return; }
                        o.dismiss();
                        if (Economy.buy(Game.progress(), it, day(), Game.clock())) bought(it);
                    });
        }
        o.button("Kapat", Ui.SECONDARY, -1, v -> o.dismiss());
        o.show(act.overlayLayer());
    }

    private void tickCountdown() {
        if (countdown == null) return;
        long ms = Economy.msUntilNextDayAt(Game.trustedNow(), Game.zoneOffset());
        long s = ms / 1000;
        countdown.setText(String.format(java.util.Locale.ROOT, "Yenilenme: %02d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60));
        postDelayed(tick, 1000);
    }

    @Override public void onHide() { removeCallbacks(tick); }
}
