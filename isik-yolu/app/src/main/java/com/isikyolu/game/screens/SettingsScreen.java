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
import com.isikyolu.engine.Progress;
import com.isikyolu.game.Game;
import com.isikyolu.game.MainActivity;
import com.isikyolu.game.ui.Icon;
import com.isikyolu.game.ui.Overlay;
import com.isikyolu.game.ui.Palette;
import com.isikyolu.game.ui.TutorialOverlay;
import com.isikyolu.game.ui.Ui;

/** 10. Ayarlar: ses, müzik, titreşim, öğretici ve ilerleme sıfırlama. */
public final class SettingsScreen extends Screen {

    private interface Flag { boolean get(); void set(boolean v); }

    public SettingsScreen(MainActivity a) {
        super(a);
        Context c = a;
        Progress p = Game.progress();
        LinearLayout root = Ui.vertical(c);
        root.addView(Ui.header(c, "Ayarlar", v -> a.goMenu(), null));
        ScrollView sv = new ScrollView(c);
        LinearLayout col = Ui.vertical(c);
        int pad = Ui.dp(c, 16);
        col.setPadding(pad, Ui.dp(c, 4), pad, pad);

        LinearLayout card = Ui.vertical(c);
        card.setBackground(Ui.card(c));
        card.setPadding(Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 6));
        card.addView(row(c, Icon.SOUND, "Ses efektleri", new Flag() {
            public boolean get() { return p.sound; }
            public void set(boolean v) { p.sound = v; }
        }));
        card.addView(row(c, Icon.MUSIC, "Arka plan müziği", new Flag() {
            public boolean get() { return p.music; }
            public void set(boolean v) { p.music = v; Game.audio().updateMusic(); }
        }));
        card.addView(row(c, Icon.VIBRATE, "Titreşim", new Flag() {
            public boolean get() { return p.vibration; }
            public void set(boolean v) { p.vibration = v; if (v) Game.haptics().success(); }
        }));
        col.addView(card);

        col.addView(Ui.button(c, "Öğreticiyi Göster", Ui.SECONDARY, Icon.HELP, v -> TutorialOverlay.full(a).show(a.overlayLayer())),
                Ui.lpMargins(c, -1, -2, 0, 16, 0, 0));
        col.addView(Ui.button(c, "İlerlemeyi Sıfırla", Ui.SECONDARY, Icon.RESET, v -> confirmReset(a)),
                Ui.lpMargins(c, -1, -2, 0, 10, 0, 0));
        col.addView(Ui.button(c, "Gizlilik Politikası", Ui.SECONDARY, Icon.HELP, v -> showPrivacy(a)),
                Ui.lpMargins(c, -1, -2, 0, 10, 0, 0));
        if (com.isikyolu.game.ads.Ads.get().privacyOptionsRequired()) // AB/İngiltere: reklam izni değiştirilebilir
            col.addView(Ui.button(c, "Reklam Gizlilik Seçenekleri", Ui.SECONDARY, Icon.HELP, v -> com.isikyolu.game.ads.Ads.get().showPrivacyOptions(a)),
                    Ui.lpMargins(c, -1, -2, 0, 10, 0, 0));

        LinearLayout about = Ui.vertical(c);
        about.setBackground(Ui.card(c));
        about.setGravity(Gravity.CENTER_HORIZONTAL);
        about.setPadding(Ui.dp(c, 18), Ui.dp(c, 18), Ui.dp(c, 18), Ui.dp(c, 18));
        about.addView(Ui.text(c, "Işık Yolu", 20, Palette.WHITE, Ui.BOLD));
        about.addView(Ui.text(c, "Sürüm 1.7.0", 13, Palette.TURQUOISE, Ui.MEDIUM));
        TextView info = Ui.text(c, Progress.LEVEL_COUNT + " bölüm · 5 dünya · " + Achievements.ALL.size() + " başarı · günlük bulmaca\n"
                + "Oyun çevrimdışı oynanır. İnternet varken reklamlar Google AdMob ile gösterilir; video izlemek isteğe bağlıdır.\n"
                + "İlerlemen yalnızca cihazına kaydedilir."
                + (com.isikyolu.game.BuildConfig.AD_TEST_IDS ? "\n(Bu derleme Google test reklamlarını gösterir.)" : ""), 13, Palette.TEXT_DIM, Ui.REGULAR);
        info.setGravity(Gravity.CENTER);
        info.setLineSpacing(0, 1.2f);
        about.addView(info, Ui.lpMargins(c, -2, -2, 0, 10, 0, 0));
        col.addView(about, Ui.lpMargins(c, -1, -2, 0, 20, 0, 0));

        sv.addView(col);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        addView(root, new LayoutParams(-1, -1));
    }

    private View row(Context c, int icon, String label, Flag f) {
        LinearLayout r = Ui.horizontal(c);
        r.setPadding(Ui.dp(c, 12), Ui.dp(c, 10), Ui.dp(c, 8), Ui.dp(c, 10));
        FrameLayout ic = new FrameLayout(c);
        ic.setBackground(Ui.round(0x2220B8A6, Ui.dp(c, 19)));
        ic.addView(Ui.icon(c, icon, Palette.TURQUOISE, 20), new FrameLayout.LayoutParams(Ui.dp(c, 20), Ui.dp(c, 20), Gravity.CENTER));
        r.addView(ic, new LinearLayout.LayoutParams(Ui.dp(c, 38), Ui.dp(c, 38)));
        TextView t = Ui.text(c, label, 16, Palette.WHITE, Ui.MEDIUM);
        t.setPadding(Ui.dp(c, 14), 0, 0, 0);
        r.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
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

    private void confirmReset(MainActivity a) {
        Overlay o = new Overlay(a);
        o.title("Emin misin?");
        o.body("Tüm yıldızların, bölüm kilitlerin, günlük serin ve başarıların silinecek. Bu işlem geri alınamaz.");
        o.button("Evet, Sıfırla", Ui.GOLD, Icon.RESET, v -> {
            Game.progress().resetAll();
            Game.save();
            o.dismiss();
            a.toast("İlerleme sıfırlandı");
        });
        o.button("Vazgeç", Ui.SECONDARY, -1, v -> o.dismiss());
        o.show(a.overlayLayer());
    }

    /** Gizlilik politikası: uygulama içinde (çevrimdışı da okunur); adres tanımlıysa web'de açılabilir. */
    static void showPrivacy(MainActivity a) {
        com.isikyolu.game.ui.Overlay o = new com.isikyolu.game.ui.Overlay(a);
        o.title("Gizlilik Politikası");
        TextView body = o.body(PRIVACY_TEXT);
        body.setGravity(Gravity.START);
        body.setTextSize(13);
        String url = com.isikyolu.game.BuildConfig.PRIVACY_URL;
        if (!url.isEmpty()) o.button("Web'de Aç", Ui.PRIMARY, Icon.NEXT, v -> {
            try { a.startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))); }
            catch (RuntimeException e) { a.toast(url); }
        });
        o.button("Tamam", Ui.SECONDARY, -1, v -> o.dismiss());
        o.show(a.overlayLayer());
    }

    static final String PRIVACY_TEXT =
            "Işık Yolu bir bulmaca oyunudur. Oyunun kendisi hesap açtırmaz, kişisel veri istemez ve sunucuya veri göndermez. "
            + "İlerlemen, ayarların, canların ve satın aldığın oyun içi ürünler yalnızca cihazında saklanır.\n\n"
            + "Reklamlar: İnternet bağlantısı varken reklamlar Google AdMob ile gösterilir. Google; reklam kimliği, cihaz ve uygulama "
            + "bilgileri, IP adresinden yaklaşık konum ve reklam etkileşimleri gibi verileri reklam sunmak, ölçmek ve kötüye kullanımı "
            + "önlemek için işleyebilir. Ayrıntılar: policies.google.com/technologies/ads\n\n"
            + "İzin: Gereken bölgelerde (ör. AB, İngiltere) reklamlardan önce Google'ın izin formu gösterilir; seçimini Ayarlar'daki "
            + "\"Reklam Gizlilik Seçenekleri\" ile değiştirebilirsin. Reklam kimliğini cihazının Ayarlar > Gizlilik > Reklamlar "
            + "bölümünden sıfırlayabilir ya da silebilirsin.\n\n"
            + "Ödüllü videolar isteğe bağlıdır. Oyun internet olmadan da tamamen oynanır.\n\n"
            + "Çocuklar: Oyun genel kitleye yöneliktir; reklam içeriği \"PG\" ile sınırlıdır.\n\n"
            + "Verilerini silmek için Ayarlar > İlerlemeyi Sıfırla'yı kullanabilir ya da uygulamayı kaldırabilirsin.";
}
