package com.isikyolu.game.screens;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import com.isikyolu.engine.Achievements;
import com.isikyolu.engine.Chapters;
import com.isikyolu.engine.Progress;
import com.isikyolu.game.Game;
import com.isikyolu.game.MainActivity;
import com.isikyolu.game.i18n.Brand;
import com.isikyolu.game.i18n.L;
import com.isikyolu.game.ui.Icon;
import com.isikyolu.game.ui.Overlay;
import com.isikyolu.game.ui.Palette;
import com.isikyolu.game.ui.TutorialOverlay;
import com.isikyolu.game.ui.Ui;

/** 10. Ayarlar: ses, müzik, titreşim, dil, öğretici ve ilerleme sıfırlama. */
public final class SettingsScreen extends Screen {

    private interface Flag { boolean get(); void set(boolean v); }

    public SettingsScreen(MainActivity a) {
        super(a);
        Context c = a;
        Progress p = Game.progress();
        LinearLayout root = Ui.vertical(c);
        root.addView(Ui.header(c, L.t("settings.title"), v -> a.goMenu(), null));
        ScrollView sv = new ScrollView(c);
        LinearLayout col = Ui.vertical(c);
        int pad = Ui.dp(c, 16);
        col.setPadding(pad, Ui.dp(c, 4), pad, pad);

        LinearLayout card = Ui.vertical(c);
        card.setBackground(Ui.card(c));
        card.setPadding(Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 6));
        card.addView(row(c, Icon.SOUND, L.t("settings.sound"), new Flag() {
            public boolean get() { return p.sound; }
            public void set(boolean v) { p.sound = v; }
        }));
        card.addView(row(c, Icon.MUSIC, L.t("settings.music"), new Flag() {
            public boolean get() { return p.music; }
            public void set(boolean v) { p.music = v; Game.audio().updateMusic(); }
        }));
        card.addView(row(c, Icon.VIBRATE, L.t("settings.vibration"), new Flag() {
            public boolean get() { return p.vibration; }
            public void set(boolean v) { p.vibration = v; if (v) Game.haptics().success(); }
        }));
        card.addView(languageRow(a));
        col.addView(card);

        col.addView(Ui.button(c, L.t("settings.tutorial"), Ui.SECONDARY, Icon.HELP, v -> TutorialOverlay.full(a).show(a.overlayLayer())),
                Ui.lpMargins(c, -1, -2, 0, 16, 0, 0));
        col.addView(Ui.button(c, L.t("settings.reset"), Ui.SECONDARY, Icon.RESET, v -> confirmReset(a)),
                Ui.lpMargins(c, -1, -2, 0, 10, 0, 0));
        col.addView(Ui.button(c, L.t("privacy.title"), Ui.SECONDARY, Icon.HELP, v -> showPrivacy(a)),
                Ui.lpMargins(c, -1, -2, 0, 10, 0, 0));
        if (com.isikyolu.game.ads.Ads.get().privacyOptionsRequired()) // AB/İngiltere: reklam izni değiştirilebilir
            col.addView(Ui.button(c, L.t("settings.ad_privacy"), Ui.SECONDARY, Icon.HELP, v -> com.isikyolu.game.ads.Ads.get().showPrivacyOptions(a)),
                    Ui.lpMargins(c, -1, -2, 0, 10, 0, 0));

        LinearLayout about = Ui.vertical(c);
        about.setBackground(Ui.card(c));
        about.setGravity(Gravity.CENTER_HORIZONTAL);
        about.setPadding(Ui.dp(c, 18), Ui.dp(c, 18), Ui.dp(c, 18), Ui.dp(c, 18));
        about.addView(Ui.text(c, Brand.primary(), 20, Palette.WHITE, Ui.BOLD));
        // sürüm adı metin olarak verilir (binlik ayırıcı yok)
        about.addView(Ui.text(c, L.t("settings.version", com.isikyolu.game.BuildConfig.VERSION_NAME), 13, Palette.TURQUOISE, Ui.MEDIUM));
        String summary = L.t("settings.about_summary", L.n("settings.about_levels", Progress.LEVEL_COUNT),
                L.n("settings.about_worlds", Chapters.WORLDS), L.n("settings.about_achievements", Achievements.ALL.size()));
        String infoText = summary + "\n" + L.t("settings.about_offline") + "\n" + L.t("settings.about_local")
                + (com.isikyolu.game.BuildConfig.AD_TEST_IDS ? "\n" + L.t("settings.about_test_ads") : "");
        TextView info = Ui.text(c, infoText, 13, Palette.TEXT_DIM, Ui.REGULAR);
        info.setGravity(Gravity.CENTER);
        info.setLineSpacing(0, 1.2f);
        about.addView(info, Ui.lpMargins(c, -2, -2, 0, 10, 0, 0));
        col.addView(about, Ui.lpMargins(c, -1, -2, 0, 20, 0, 0));

        sv.addView(col);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        addView(root, new LayoutParams(-1, -1));
    }

    /** Satır: yuvarlak zeminde simge, uzayabilen etiket (uzun çeviriler alt satıra geçer); sağ taraf çağırana kalır. */
    private static LinearLayout rowBase(Context c, View icon, String label) {
        LinearLayout r = Ui.horizontal(c);
        r.setPadding(Ui.dp(c, 12), Ui.dp(c, 10), Ui.dp(c, 8), Ui.dp(c, 10));
        FrameLayout ic = new FrameLayout(c);
        ic.setBackground(Ui.round(0x2220B8A6, Ui.dp(c, 19)));
        ic.addView(icon); // simgenin kendi FrameLayout yerleşimiyle (ortalı)
        ic.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        r.addView(ic, new LinearLayout.LayoutParams(Ui.dp(c, 38), Ui.dp(c, 38)));
        TextView t = Ui.text(c, label, 16, Palette.WHITE, Ui.MEDIUM);
        t.setPadding(Ui.dp(c, 14), 0, 0, 0);
        r.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        return r;
    }

    private View row(Context c, int icon, String label, Flag f) {
        View iv = Ui.icon(c, icon, Palette.TURQUOISE, 20);
        iv.setLayoutParams(new FrameLayout.LayoutParams(Ui.dp(c, 20), Ui.dp(c, 20), Gravity.CENTER));
        LinearLayout r = rowBase(c, iv, label);
        Switch sw = new Switch(c);
        sw.setChecked(f.get());
        int[][] states = {{android.R.attr.state_checked}, {}};
        sw.setThumbTintList(new ColorStateList(states, new int[]{Palette.TURQUOISE, 0xFF8792A8}));
        sw.setTrackTintList(new ColorStateList(states, new int[]{0x8820B8A6, 0xFF576A94}));
        sw.setOnCheckedChangeListener((b, v) -> { f.set(v); Game.save(); Game.haptics().tick(); });
        r.addView(sw);
        r.setOnClickListener(v -> sw.toggle());
        return r;
    }

    /** Dil satırı: simgede dil kodu, sağda seçili dilin kendi adı; dokununca dil listesi açılır. */
    private View languageRow(MainActivity a) {
        Context c = a;
        TextView code = Ui.text(c, L.lang().toUpperCase(L.locale()), 13, Palette.TURQUOISE, Ui.BOLD);
        code.setGravity(Gravity.CENTER);
        code.setLayoutParams(new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        LinearLayout r = rowBase(c, code, L.t("settings.language"));
        TextView value = Ui.text(c, L.nativeName(L.lang()), 15, Palette.TURQUOISE, Ui.MEDIUM);
        value.setSingleLine(true);
        value.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(-2, -2);
        vlp.leftMargin = Ui.dp(c, 8);
        r.addView(value, vlp);
        View next = Ui.icon(c, Icon.NEXT, Palette.TEXT_DIM, 18);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(Ui.dp(c, 18), Ui.dp(c, 18));
        nlp.leftMargin = Ui.dp(c, 8);
        nlp.rightMargin = Ui.dp(c, 6);
        r.addView(next, nlp);
        r.setMinimumHeight(Ui.dp(c, 58));
        Ui.clickable(r, v -> showLanguages(a));
        return r;
    }

    /** Desteklenen diller kendi adlarıyla; seçili olan altın renkli ve işaretli. Seçim kaydedilir, ilerleme değişmez. */
    private void showLanguages(MainActivity a) {
        Overlay o = new Overlay(a);
        o.title(L.t("settings.language"));
        String current = L.lang();
        for (int i = 0; i < L.SUPPORTED.length; i++) {
            String code = L.SUPPORTED[i];
            boolean selected = code.equals(current);
            View b = o.button(L.NATIVE_NAMES[i], selected ? Ui.GOLD : Ui.SECONDARY, selected ? Icon.CHECK : -1, v -> {
                o.dismiss();
                // MainActivity dinleyicisi açık ekranı yeni dilde yeniden kurar (yeniden başlatma yok)
                if (!code.equals(L.lang())) L.set(getContext(), code);
            });
            b.setSelected(selected);
        }
        o.button(L.t("common.cancel"), Ui.GHOST, -1, v -> o.dismiss());
        o.show(a.overlayLayer());
    }

    private void confirmReset(MainActivity a) {
        Overlay o = new Overlay(a);
        o.title(L.t("settings.reset_title"));
        o.body(L.t("settings.reset_body"));
        o.button(L.t("settings.reset_confirm"), Ui.GOLD, Icon.RESET, v -> {
            Game.progress().resetAll();
            Game.save();
            o.dismiss();
            a.toast(L.t("settings.reset_done"));
        });
        o.button(L.t("common.cancel"), Ui.SECONDARY, -1, v -> o.dismiss());
        o.show(a.overlayLayer());
    }

    /** Gizlilik politikası: uygulama içinde (çevrimdışı da okunur); adres tanımlıysa web'de açılabilir. */
    static void showPrivacy(MainActivity a) {
        com.isikyolu.game.ui.Overlay o = new com.isikyolu.game.ui.Overlay(a);
        o.title(L.t("privacy.title"));
        TextView body = o.body(privacyText());
        body.setGravity(Gravity.START);
        body.setTextSize(13);
        String url = com.isikyolu.game.BuildConfig.PRIVACY_URL;
        if (!url.isEmpty()) o.button(L.t("privacy.open_web"), Ui.PRIMARY, Icon.NEXT, v -> {
            try { a.startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))); }
            catch (RuntimeException e) { a.toast(url); }
        });
        o.button(L.t("common.ok"), Ui.SECONDARY, -1, v -> o.dismiss());
        o.show(a.overlayLayer());
    }

    /** Uygulama içi kısa gizlilik metni (seçili dilde; paragraflar boş satırla ayrılır). */
    static String privacyText() {
        return L.t("privacy.intro", Brand.primary()) + "\n\n"
                + L.t("privacy.ads") + "\n\n"
                + L.t("privacy.consent") + "\n\n"
                + L.t("privacy.rewarded") + "\n\n"
                + L.t("privacy.children") + "\n\n"
                + L.t("privacy.delete");
    }
}
