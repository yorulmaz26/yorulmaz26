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
import com.isikyolu.game.i18n.EconomyTexts;
import com.isikyolu.game.i18n.L;
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
    private static final int[] TAB_ICONS = {Icon.TAG, Icon.BULB, Icon.HEART, Icon.BRUSH, Icon.CRYSTAL};

    private final LinearLayout tabs, list;
    private final ScrollView sv;
    private final HorizontalScrollView hs;
    private final CurrencyBar bar;
    private int tab;

    /** Açık sekme (dil değişince ekran aynı sekmeyle yeniden kurulur). */
    public int currentTab() { return tab; }
    private TextView countdown;
    private final Runnable tick = this::tickCountdown;

    public MarketScreen(MainActivity a, int startTab) {
        super(a);
        tab = startTab;
        LinearLayout root = Ui.vertical(a);
        bar = new CurrencyBar(a);
        root.addView(Ui.header(a, L.t("market.title"), v -> a.goMenu(), bar));
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

    /** Sekme adları (seçili dilde; sıra TAB_ICONS ile aynı). */
    private static String[] tabLabels() {
        return new String[]{L.t("market.tab.deals"), L.t("market.tab.boosts"), L.t("market.tab.lives"), L.t("market.tab.looks"),
                L.t("market.tab.earn")};
    }

    private void buildTabs() {
        Context c = getContext();
        tabs.removeAllViews();
        String[] labels = tabLabels();
        for (int i = 0; i < labels.length; i++) {
            final int ti = i;
            boolean sel = i == tab;
            LinearLayout t = Ui.horizontal(c);
            t.setPadding(Ui.dp(c, 12), Ui.dp(c, 8), Ui.dp(c, 14), Ui.dp(c, 8));
            GradientDrawable bg = sel ? new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xFFFFD45E, 0xFFF09A38})
                    : new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF2E4370, 0xFF26385F});
            bg.setCornerRadius(Ui.dp(c, 16));
            t.setBackground(bg);
            t.addView(Ui.icon(c, TAB_ICONS[i], sel ? Palette.NIGHT : Palette.WHITE, 16));
            TextView n = Ui.text(c, " " + labels[i], 14, sel ? Palette.NIGHT : Palette.WHITE, Ui.BOLD);
            n.setSingleLine(true);
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
        head.addView(Ui.text(c, L.t("market.deals_title"), 18, Palette.WHITE, Ui.BOLD), new LinearLayout.LayoutParams(0, -2, 1f));
        countdown = Ui.text(c, "", 13, Palette.GOLD, Ui.MEDIUM);
        countdown.setSingleLine(true); // her saniye değişir: satır sayısı oynamasın (başlık yanında sıkışırsa başlık kırılır)
        head.addView(countdown);
        list.addView(head, Ui.lpMargins(c, -1, -2, 4, 4, 4, 2));
        list.addView(note(L.t("market.deals_note")));
        // video yuvası: video yüklenirken yer tutar (liste kaymaz, yanlış dokunuş satın almaya dönmez)
        videoSlot = null;
        if (act.videosLeft(com.isikyolu.engine.AdPolicy.Reward.CRYSTALS) > 0) {
            videoSlot = new FrameLayout(c);
            list.addView(videoSlot, Ui.lpMargins(c, -1, -2, 0, 6, 0, 6));
            fillVideoSlot();
        }
        Progress p = Game.progress();
        int d = day();
        List<Economy.Deal> deals = Economy.deals(d);
        for (Economy.Deal deal : deals) {
            String reason = Economy.dealBlockReason(p, deal, d);
            String info = L.t("market.deal_info", deal.discount, deal.item.basePrice);
            list.addView(card(deal.item, info, deal.price, reason, L.t("market.deal_badge", deal.discount), () -> confirmBuy(EconomyTexts.itemName(deal.item), deal.item.currency, deal.price, () -> {
                if (Economy.buyDeal(Game.progress(), deal, day(), Game.clock())) bought(deal.item);
            })), Ui.lpMargins(c, -1, -2, 0, 6, 0, 6));
        }
        tickCountdown();
    }

    private void renderCategory(int cat) {
        Context c = getContext();
        Progress p = Game.progress();
        list.addView(inventory(), Ui.lpMargins(c, -1, -2, 0, 4, 0, 6));
        list.addView(note(L.t("market.boosts_note")));
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
        list.addView(note(L.n("market.lives_note", Lives.regenMinutes())));
        for (Economy.Item it : Economy.ITEMS) if (it.category == Economy.CAT_LIVES) list.addView(itemCard(p, it), Ui.lpMargins(c, -1, -2, 0, 6, 0, 6));
    }

    private void renderLooks() {
        Context c = getContext();
        Progress p = Game.progress();
        list.addView(note(L.t("market.looks_note")));
        String[] titles = {L.t("market.looks.mirrors"), L.t("market.looks.boards"), L.t("market.looks.beams")};
        Economy.Kind[] kinds = {Economy.Kind.MIRROR, Economy.Kind.BOARD, Economy.Kind.BEAM};
        String[] defaults = {L.t("market.default.mirror"), L.t("market.default.board"), L.t("market.default.beam")};
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
                {L.t("market.guide.first_clear"), L.t("market.guide.first_clear_gain")},
                {L.t("market.guide.three_stars"), L.t("market.guide.three_stars_gain")},
                {L.t("market.guide.perfect_chapter"), L.t("market.guide.perfect_chapter_gain")},
                {L.t("market.guide.daily"), L.t("market.guide.daily_gain")},
                {L.t("market.guide.achievement"), L.t("market.guide.achievement_gain")},
                {L.t("market.guide.big_achievements"), L.t("market.guide.big_achievements_gain")},
                {L.t("market.guide.top_achievements"), L.t("market.guide.top_achievements_gain")},
                {L.t("market.guide.video"), L.t("market.guide.video_gain")},
                {L.t("market.guide.replay"), L.t("market.guide.replay_gain")},
        };
        list.addView(Ui.text(c, L.t("market.guide_title"), 18, Palette.WHITE, Ui.BOLD), Ui.lpMargins(c, -2, -2, 4, 4, 0, 6));
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
        list.addView(note(L.t("market.guide_stats", p.crystalsEarned, p.purchases)));
        list.addView(note(L.t("market.no_real_money")));
    }

    // ---------- kartlar ----------

    private View inventory() {
        Context c = getContext();
        Progress p = Game.progress();
        LinearLayout r = Ui.horizontal(c);
        r.setBackground(Ui.card(c));
        r.setPadding(Ui.dp(c, 12), Ui.dp(c, 10), Ui.dp(c, 12), Ui.dp(c, 10));
        r.addView(invCell(c, Icon.BULB, Palette.GOLD, L.n("market.inv.hints", p.hints)), new LinearLayout.LayoutParams(0, -2, 1f));
        r.addView(invCell(c, Icon.TICKET, 0xFF7DF0E0, L.n("market.inv.tickets", p.tickets)), new LinearLayout.LayoutParams(0, -2, 1f));
        r.addView(invCell(c, Icon.SHIELD, 0xFFFF8FA3, L.n("market.inv.shields", p.shields)), new LinearLayout.LayoutParams(0, -2, 1f));
        return r;
    }

    /** @param countLabel sayı ve birim birlikte (ör. "3 İpucu") */
    private static View invCell(Context c, int icon, int color, String countLabel) {
        LinearLayout l = Ui.horizontal(c);
        l.setGravity(Gravity.CENTER);
        l.addView(Ui.icon(c, icon, color, 20));
        TextView t = Ui.text(c, " " + countLabel, 14, Palette.WHITE, Ui.MEDIUM);
        t.setMaxLines(2); // uzun dillerde iki satıra iner, taşmaz
        t.setGravity(Gravity.CENTER);
        l.addView(t);
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
        String info;
        if (it.cosmetic()) {
            if (p.owned.contains(it.skin)) info = Economy.equipped(p, it) ? L.t("market.in_use") : L.t("market.owned");
            else if (it.unlockLevel > 0 && p.stars[it.unlockLevel] == 0) info = L.t("market.needs_level", it.unlockLevel);
            else info = it.currency == Economy.GEM ? L.t("market.rare_skin") : L.t("market.permanent_skin");
        } else {
            int today = Economy.boughtToday(p, it.id, d);
            if (it.dailyLimit > 0 && it.step > 0) info = L.t("market.today_step", today, it.dailyLimit, it.step);
            else if (it.dailyLimit > 0) info = L.t("market.today", today, it.dailyLimit);
            else if (it.step > 0) info = L.t("market.step", it.step);
            else info = "";
        }
        if (it.cosmetic() && p.owned.contains(it.skin)) {
            boolean on = Economy.equipped(p, it);
            return card(it, info, -1, null, null, on ? null : () -> { Economy.equip(Game.progress(), it); Game.save(); Game.audio().play(Audio.CLICK); render(false); },
                    on ? L.t("market.in_use") : L.t("market.use"));
        }
        Runnable buy = () -> confirmBuy(EconomyTexts.itemName(it), it.currency, price, () -> {
            if (Economy.buy(Game.progress(), it, day(), Game.clock())) bought(it);
        });
        if (it.cosmetic()) {
            View cardV = card(it, info, price, reason, null, buy);
            Ui.clickable(cardV, v -> preview(it, price, reason));
            return cardV;
        }
        return card(it, info, price, reason, null, buy);
    }

    private View defaultCard(String name, boolean on, Runnable use) {
        Context c = getContext();
        LinearLayout r = Ui.horizontal(c);
        r.setBackground(Ui.card(c));
        r.setPadding(Ui.dp(c, 14), Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12));
        r.addView(Ui.text(c, name, 15, Palette.WHITE, Ui.MEDIUM), new LinearLayout.LayoutParams(0, -2, 1f));
        r.addView(actionButton(on ? L.t("market.in_use") : L.t("market.use"), !on, on ? null : use));
        return r;
    }

    private View card(Economy.Item it, String info, int price, String reason, String badge, Runnable onBuy) {
        return card(it, info, price, reason, badge, onBuy, null);
    }

    /**
     * Ürün kartı: ikon, ad, açıklama, bilgi satırı ve fiyat/eylem butonu.
     * @param reason satın alma engeli kodu (Economy.BLOCK_*), alınabiliyorsa null
     */
    private View card(Economy.Item it, String info, int price, String reason, String badge, Runnable onAction, String actionLabel) {
        Context c = getContext();
        String why = EconomyTexts.blockReason(reason, it);
        LinearLayout r = Ui.horizontal(c);
        r.setBackground(Ui.card(c));
        r.setPadding(Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12));
        FrameLayout ic = new FrameLayout(c);
        int col = colorFor(it);
        ic.setBackground(Ui.round(Palette.withAlpha(col, 50), Ui.dp(c, 24)));
        ic.addView(Ui.icon(c, iconFor(it), col, 26), new FrameLayout.LayoutParams(Ui.dp(c, 26), Ui.dp(c, 26), Gravity.CENTER));
        if (badge != null) {
            TextView b = Ui.text(c, badge, 10, Palette.WHITE, Ui.BOLD);
            b.setSingleLine(true);
            b.setBackground(Ui.round(0xFFFF5C7A, Ui.dp(c, 8)));
            b.setPadding(Ui.dp(c, 5), Ui.dp(c, 1), Ui.dp(c, 5), Ui.dp(c, 1));
            ic.addView(b, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END));
        }
        r.addView(ic, new LinearLayout.LayoutParams(Ui.dp(c, 50), Ui.dp(c, 50)));
        LinearLayout tc = Ui.vertical(c);
        tc.setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 8), 0);
        tc.addView(Ui.text(c, EconomyTexts.itemName(it), 16, Palette.WHITE, Ui.BOLD));
        tc.addView(Ui.text(c, EconomyTexts.itemDesc(it), 13, Palette.TEXT_DIM, Ui.REGULAR));
        if (info != null && !info.isEmpty()) tc.addView(Ui.text(c, info, 12, Palette.GOLD, Ui.MEDIUM));
        if (why != null && price >= 0) tc.addView(Ui.text(c, why, 12, 0xFFFF9AA8, Ui.MEDIUM));
        r.addView(tc, new LinearLayout.LayoutParams(0, -2, 1f));
        if (price < 0) r.addView(actionButton(actionLabel, onAction != null, onAction));
        else r.addView(priceButton(it.currency, price, reason == null, () -> {
            if (why != null) { Game.audio().play(Audio.LOCKED); Game.haptics().error(); act.toast(why); return; }
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
        b.addView(Ui.text(c, " " + java.text.NumberFormat.getIntegerInstance(L.locale()).format(price), 15, enabled ? Palette.WHITE : Palette.TEXT_DIM, Ui.BOLD));
        b.setMinimumWidth(Ui.dp(c, 76));
        b.setContentDescription(currency == Economy.GEM ? L.n("common.gems", price) : L.n("common.crystals", price));
        Ui.clickable(b, v -> r.run());
        return b;
    }

    private View actionButton(String label, boolean enabled, Runnable r) {
        Context c = getContext();
        TextView b = Ui.text(c, label, 14, enabled ? Palette.WHITE : Palette.TEXT_DIM, Ui.BOLD);
        b.setGravity(Gravity.CENTER);
        b.setBackground(Ui.round(enabled ? 0xFF6D5DFB : 0xFF314064, Ui.dp(c, 14)));
        b.setPadding(Ui.dp(c, 14), Ui.dp(c, 9), Ui.dp(c, 14), Ui.dp(c, 9));
        // uzun dillerde kartın metnini sıkıştırmasın: en fazla iki satır
        b.setMaxWidth(Ui.dp(c, 150));
        b.setMaxLines(2);
        b.setEllipsize(android.text.TextUtils.TruncateAt.END);
        if (enabled && r != null) Ui.clickable(b, v -> r.run());
        return b;
    }

    // ---------- satın alma ----------

    private void confirmBuy(String name, int currency, int price, Runnable doBuy) {
        boolean expensive = currency == Economy.GEM || price >= 150;
        if (!expensive) { doBuy.run(); return; }
        Overlay o = new Overlay(act);
        o.title(name);
        String cost = currency == Economy.GEM ? L.n("common.gems", price) : L.n("common.crystals", price);
        o.body(L.t("market.confirm_body", cost, Economy.balance(Game.progress(), currency)));
        o.button(L.t("market.buy"), Ui.GOLD, Icon.BAG, v -> { o.dismiss(); doBuy.run(); });
        o.button(L.t("common.cancel"), Ui.SECONDARY, -1, v -> o.dismiss());
        o.show(act.overlayLayer());
    }

    private void bought(Economy.Item it) {
        Game.save();
        Game.audio().play(Audio.ACHIEVE);
        Game.haptics().success();
        act.toast(L.t("market.bought", EconomyTexts.itemName(it)));
        act.checkAchievements();
        render(false);
    }

    /**
     * Görünüm önizlemesi: örnek tahta seçilen görünümle çizilir.
     * @param reason satın alma engeli kodu (Economy.BLOCK_*), alınabiliyorsa null
     */
    private void preview(Economy.Item it, int price, String reason) {
        Context c = getContext();
        String why = EconomyTexts.blockReason(reason, it);
        Overlay o = new Overlay(act);
        TextView tag = Ui.text(c, L.t("market.preview").toUpperCase(L.locale()), 13, Palette.TURQUOISE, Ui.BOLD);
        tag.setLetterSpacing(0.08f);
        o.cardView().addView(tag);
        o.title(EconomyTexts.itemName(it));
        BoardView bv = new BoardView(c);
        bv.setInputEnabled(false);
        bv.bind(new GameSession(it.kind == Economy.Kind.BOARD ? Demos.chapter(4) : Demos.chapter(2)));
        Progress p = Game.progress();
        bv.setSkinOverride(it.kind == Economy.Kind.MIRROR ? it.skin : null, it.kind == Economy.Kind.BOARD ? it.skin : null,
                it.kind == Economy.Kind.BEAM ? it.skin : null);
        o.cardView().addView(bv, new LinearLayout.LayoutParams(-1, Ui.dp(c, 200)));
        o.body(EconomyTexts.itemDesc(it) + (why != null ? "\n" + why : ""));
        if (p.owned.contains(it.skin)) {
            o.button(Economy.equipped(p, it) ? L.t("market.in_use") : L.t("market.use"), Ui.PRIMARY, Icon.CHECK, v -> { o.dismiss(); Economy.equip(Game.progress(), it); Game.save(); render(false); });
        } else {
            o.button((it.currency == Economy.GEM ? L.n("market.buy_gems", price) : L.n("market.buy_crystals", price)),
                    why == null ? Ui.GOLD : Ui.SECONDARY, Icon.BAG, v -> {
                        if (why != null) { act.toast(why); return; }
                        o.dismiss();
                        if (Economy.buy(Game.progress(), it, day(), Game.clock())) bought(it);
                    });
        }
        o.button(L.t("common.close"), Ui.SECONDARY, -1, v -> o.dismiss());
        o.show(act.overlayLayer());
    }

    private void tickCountdown() {
        if (countdown == null) return;
        long ms = Game.msUntilNextDay();
        long s = ms / 1000;
        countdown.setText(L.t("market.refresh_in", String.format(java.util.Locale.ROOT, "%02d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60)));
        postDelayed(tick, 1000);
    }

    @Override public void onHide() { removeCallbacks(tick); }

    private FrameLayout videoSlot;

    /** Yuvayı doldurur: video hazırsa etkin düğme, değilse aynı boyutta "hazırlanıyor" düğmesi. */
    private void fillVideoSlot() {
        if (videoSlot == null) return;
        Context c = getContext();
        com.isikyolu.engine.AdPolicy.Reward r = com.isikyolu.engine.AdPolicy.Reward.CRYSTALS;
        videoSlot.removeAllViews();
        int left = act.videosLeft(r);
        View b;
        if (left <= 0) {
            b = Ui.button(c, L.t("market.video_done"), Ui.SECONDARY, Icon.PLAY, v -> act.toast(L.t("market.video_tomorrow")));
            b.setAlpha(0.55f);
        } else if (act.videoAvailable(r)) {
            b = Ui.button(c, L.n("market.video_watch", com.isikyolu.engine.AdPolicy.VIDEO_CRYSTALS, left), Ui.PRIMARY, Icon.PLAY,
                    v -> act.watchVideo(r, () -> {
                        act.toast(L.n("market.video_reward", com.isikyolu.engine.AdPolicy.VIDEO_CRYSTALS));
                        bar.refresh();
                        fillVideoSlot();
                    }));
        } else {
            b = Ui.button(c, L.t("market.video_loading"), Ui.SECONDARY, Icon.PLAY, v -> act.toast(L.t("market.video_loading_toast")));
            b.setAlpha(0.55f);
        }
        videoSlot.addView(b, new FrameLayout.LayoutParams(-1, -2));
        // uzun dillerde düğme iki satıra çıkabilir: yuva baştan iki satırlık yükseklikte tutulur, video gelince liste kaymaz
        videoSlot.setMinimumHeight(Ui.dp(c, 78));
        ((FrameLayout.LayoutParams) b.getLayoutParams()).gravity = Gravity.CENTER_VERTICAL;
    }

    /** Ödüllü video yeniden hazır olunca yalnızca video yuvası güncellenir (liste baştan kurulmaz). */
    @Override public void onAdsChanged() { if (tab == 0) fillVideoSlot(); }
}
