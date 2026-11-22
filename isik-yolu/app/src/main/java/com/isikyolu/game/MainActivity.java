package com.isikyolu.game;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.isikyolu.engine.Level;
import com.isikyolu.game.screens.AchievementsScreen;
import com.isikyolu.game.screens.DailyScreen;
import com.isikyolu.game.screens.GameScreen;
import com.isikyolu.game.screens.LevelSelectScreen;
import com.isikyolu.game.screens.MenuScreen;
import com.isikyolu.game.screens.Screen;
import com.isikyolu.game.screens.SettingsScreen;
import com.isikyolu.game.screens.SplashScreen;
import com.isikyolu.game.ui.BackgroundView;
import com.isikyolu.game.ui.Palette;
import com.isikyolu.game.ui.Ui;

/** Tek etkinlik: ekranlar arasında animasyonlu geçiş yapar. */
public final class MainActivity extends Activity {
    /** frame: pencerenin tamamı (arka plan sistem çubuklarının altına da uzanır); root: çubukların içindeki içerik. */
    private FrameLayout frame, root, stage, bannerHost;
    private View barTop, barBottom;
    private Screen current;
    private View banner;
    private boolean bannerLoaded, adsStarted, bannerRunning, resumed;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Game.init(this);
        // kenardan kenara çizim (Android 15+ zorunlu): içerik sistem çubuklarının iç boşluğu kadar içeride
        frame = new FrameLayout(this);
        frame.addView(new BackgroundView(this), new FrameLayout.LayoutParams(-1, -1));
        barTop = new View(this);
        barBottom = new View(this);
        frame.addView(barTop, new FrameLayout.LayoutParams(-1, 0, Gravity.TOP));
        frame.addView(barBottom, new FrameLayout.LayoutParams(-1, 0, Gravity.BOTTOM));
        setBarColors(0xFF3D4D92, 0xFF213062);
        root = new FrameLayout(this);
        frame.addView(root, new FrameLayout.LayoutParams(-1, -1));
        stage = new FrameLayout(this);
        root.addView(stage, new FrameLayout.LayoutParams(-1, -1));
        // banner yuvası: ekranın en altında, üstte görünür bir ayraç ve boşlukla içerikten ayrı
        bannerHost = new FrameLayout(this);
        bannerHost.setBackgroundColor(0xFF1A2547);
        bannerHost.setPadding(0, Ui.dp(this, 9), 0, 0);
        View divider = new View(this);
        divider.setBackgroundColor(0x664A5C87);
        bannerHost.addView(divider, new FrameLayout.LayoutParams(-1, Ui.dp(this, 1), Gravity.TOP));
        bannerHost.setClipToPadding(false);
        ((FrameLayout.LayoutParams) divider.getLayoutParams()).topMargin = -Ui.dp(this, 9); // ayraç reklamın dışında, en üstte
        bannerHost.setVisibility(View.GONE);
        bannerHost.addOnLayoutChangeListener((v, l, t, r, btm, ol, ot, or, ob) -> fitStage());
        root.addView(bannerHost, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        setContentView(frame);
        enableEdgeToEdge();
        if (android.os.Build.VERSION.SDK_INT >= 33) // tahmine dayalı geri hareketi (Android 13+; 16'da zorunlu)
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::handleBack);
        show(new com.isikyolu.game.screens.StudioIntroScreen(this), true);
    }

    public FrameLayout overlayLayer() { return root; }

    /** Pencere sistem çubuklarının altına çizilir; içerik onların iç boşluğu kadar içeri alınır (tüm sürümlerde aynı). */
    @SuppressWarnings("deprecation")
    private void enableEdgeToEdge() {
        android.view.Window w = getWindow();
        if (android.os.Build.VERSION.SDK_INT >= 30) w.setDecorFitsSystemWindows(false);
        else w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        if (android.os.Build.VERSION.SDK_INT < 35) { w.setStatusBarColor(0); w.setNavigationBarColor(0); }
        if (android.os.Build.VERSION.SDK_INT >= 28)
            w.getAttributes().layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        frame.setOnApplyWindowInsetsListener((v, in) -> {
            int l, t, r, b;
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets i = in.getInsets(android.view.WindowInsets.Type.systemBars() | android.view.WindowInsets.Type.displayCutout());
                l = i.left; t = i.top; r = i.right; b = i.bottom;
            } else {
                l = in.getSystemWindowInsetLeft(); t = in.getSystemWindowInsetTop(); r = in.getSystemWindowInsetRight(); b = in.getSystemWindowInsetBottom();
            }
            root.setPadding(l, t, r, b);
            barTop.getLayoutParams().height = t;
            barBottom.getLayoutParams().height = b;
            barTop.requestLayout();
            barBottom.requestLayout();
            return in;
        });
        frame.requestApplyInsets();
    }

    /** Durum ve gezinme çubuğunun arkasındaki renkler (stüdyo açılışında logo rengi). */
    public void setBarColors(int top, int bottom) {
        barTop.setBackgroundColor(top);
        barBottom.setBackgroundColor(bottom);
    }

    public Screen currentScreen() { return current; }

    // ---------- reklamlar ----------

    /** Reklamları başlatır (ilk ana menüde: açılış animasyonu bölünmesin). İzin formu gerekiyorsa burada açılır. */
    public void startAds() {
        if (adsStarted) return;
        adsStarted = true;
        com.isikyolu.game.ads.AdProvider ads = com.isikyolu.game.ads.Ads.get();
        ads.setOnAvailabilityChanged(() -> { if (current != null) current.onAdsChanged(); });
        ads.start(this);
        int widthDp = (int) (getResources().getDisplayMetrics().widthPixels / getResources().getDisplayMetrics().density);
        banner = ads.createBanner(this, widthDp, () -> { bannerLoaded = true; updateBanner(); }, () -> { bannerLoaded = false; updateBanner(); });
        if (banner != null) bannerHost.addView(banner, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM));
    }

    /** İlk menüde başlatılmış reklamlar için: izin isteği başarısız kaldıysa (çevrimdışı açılış) yeniden dene. */
    public void retryAds() { if (adsStarted) com.isikyolu.game.ads.Ads.get().retryConsent(this); }

    /**
     * Banner yalnızca yüklendiyse ve ekran istiyorsa görünür (oyun ekranı, açılış, market ve ayarlarda yok).
     * Bir pencere açıkken yer tutar ama görünmez ve duraklatılır (karartmanın altında reklam kalmaz).
     */
    private void updateBanner() {
        boolean show = banner != null && bannerLoaded && current != null && current.wantsBanner()
                && com.isikyolu.engine.AdPolicy.bannersAllowed(Game.progress());
        boolean covered = show && hasOverlay();
        bannerHost.setVisibility(!show ? View.GONE : covered ? View.INVISIBLE : View.VISIBLE);
        setBannerRunning(show && !covered && resumed);
        fitStage();
    }

    private void setBannerRunning(boolean run) {
        if (banner == null || run == bannerRunning) return;
        bannerRunning = run;
        if (run) com.isikyolu.game.ads.Ads.get().resume(banner);
        else com.isikyolu.game.ads.Ads.get().pause(banner);
    }

    /** Overlay açıldı ya da kapanmaya başladı. */
    public void onOverlaysChanged() { updateBanner(); }

    /** Ekran banner'ın altında kalmasın: sahnenin alt boşluğu banner yüksekliği kadar. */
    private void fitStage() {
        int h = bannerHost.getVisibility() != View.GONE ? bannerHost.getHeight() : 0;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) stage.getLayoutParams();
        if (lp.bottomMargin != h) { lp.bottomMargin = h; stage.setLayoutParams(lp); }
    }

    /** Görünür banner yüksekliği (testler ve yerleşim için). */
    public int bannerHeight() { return bannerHost.getVisibility() == View.VISIBLE ? bannerHost.getHeight() : 0; }

    /** Banner yer kaplıyor ama bir pencerenin arkasında gizli mi (testler için). */
    public boolean bannerCovered() { return bannerHost.getVisibility() == View.INVISIBLE; }

    /**
     * Bölüm bitince sonraki ekrana geçiş: zamanı geldiyse önce geçiş (resim) reklamı, kapanınca devam.
     * Sıklık AdPolicy'dedir (öğreticide yok, en az 3 bölüm / 4 dk arayla).
     */
    public void afterLevel(int levelId, Runnable next) {
        com.isikyolu.engine.Progress p = Game.progress();
        long now = Game.trustedNow();
        com.isikyolu.game.ads.AdProvider ads = com.isikyolu.game.ads.Ads.get();
        if (com.isikyolu.engine.AdPolicy.interstitialDue(p, levelId, now) && ads.interstitialReady()) {
            // sayaç yalnızca reklam gerçekten ekrana gelince ilerler
            ads.showInterstitial(this, () -> { com.isikyolu.engine.AdPolicy.onInterstitialShown(Game.progress(), Game.trustedNow()); Game.save(); }, next);
        } else next.run();
    }

    /** Ödüllü video düğmesi gösterilebilir mi (sınır dolmadı ve video yüklü)? */
    public boolean videoAvailable(com.isikyolu.engine.AdPolicy.Reward r) {
        return com.isikyolu.engine.AdPolicy.canOffer(Game.progress(), r, Game.shopDay()) && com.isikyolu.game.ads.Ads.get().rewardedReady();
    }

    /** Kalan günlük video ödülü. */
    public int videosLeft(com.isikyolu.engine.AdPolicy.Reward r) {
        return com.isikyolu.engine.AdPolicy.rewardsLeft(Game.progress(), r, Game.shopDay());
    }

    /**
     * İsteğe bağlı ödüllü video. Ödül yalnızca video sonuna kadar izlenince verilir; {@code onGranted}
     * video kapandıktan sonra çalışır (arayüz güncellemesi).
     */
    public void watchVideo(com.isikyolu.engine.AdPolicy.Reward r, Runnable onGranted) {
        if (!com.isikyolu.engine.AdPolicy.canOffer(Game.progress(), r, Game.shopDay())) { toast("Bugünlük video ödülü sınırı doldu"); return; }
        com.isikyolu.game.ads.AdProvider ads = com.isikyolu.game.ads.Ads.get();
        if (!ads.rewardedReady()) { toast("Video şu an hazır değil. Bağlantını kontrol edip biraz sonra dene."); return; }
        boolean[] granted = {false};
        ads.showRewarded(this, () -> {
            granted[0] = com.isikyolu.engine.AdPolicy.grant(Game.progress(), r, Game.shopDay(), Game.trustedNow(), Game.clock());
            Game.save();
        }, result -> {
            if (granted[0]) { Game.audio().play(Audio.HINT); onGranted.run(); }
            else if (result == com.isikyolu.game.ads.AdProvider.VideoResult.FAILED) toast("Video açılamadı. Biraz sonra tekrar dene.");
            else if (result == com.isikyolu.game.ads.AdProvider.VideoResult.SKIPPED) toast("Ödül için videoyu sonuna kadar izlemelisin");
            else toast(r == com.isikyolu.engine.AdPolicy.Reward.LIFE && Game.progress().lives >= com.isikyolu.engine.Lives.MAX
                    ? "Canların zaten doldu" : "Bugünlük video ödülü sınırı doldu");
            if (current != null) current.onAdsChanged();
        });
    }

    public void show(Screen next, boolean forward) {
        final Screen old = current;
        current = next;
        float dx = Ui.dp(this, 36) * (forward ? 1 : -1);
        stage.addView(next, new FrameLayout.LayoutParams(-1, -1));
        next.setAlpha(0f);
        next.setTranslationX(dx);
        next.animate().alpha(1f).translationX(0).setDuration(260).setInterpolator(new DecelerateInterpolator()).start();
        if (old != null) {
            old.onHide();
            old.animate().alpha(0f).translationX(-dx).setDuration(200).withEndAction(() -> stage.removeView(old)).start();
        }
        updateBanner();
        next.onShow();
    }

    public void goMenu() { show(new MenuScreen(this), false); }
    public void goLevels() { show(new LevelSelectScreen(this), true); }
    /** Bölüm açılır; can yoksa "can kalmadı" penceresi gösterilir. */
    public void goLevel(int id) {
        Game.refreshLives();
        com.isikyolu.engine.Progress p = Game.progress();
        if (p.lives <= 0 && p.attemptLevel != id) {
            // bitmiş bir oyun ekranında takılı kalınmasın: önce bölümlere dön
            if (current instanceof GameScreen) show(new LevelSelectScreen(this), false);
            showNoLives(id);
            return;
        }
        show(new GameScreen(this, id), true);
    }

    public void showNoLives() { showNoLives(0); }

    /** Can kalmadı penceresi; retryLevel > 0 ise can alınınca o bölüm açılır. Aynı anda tek pencere. */
    public void showNoLives(int retryLevel) {
        if (hasOverlay()) return;
        com.isikyolu.game.ui.Overlay o = new com.isikyolu.game.ui.Overlay(this);
        FrameLayout ic = new FrameLayout(this);
        ic.setBackground(Ui.round(0x33FF5C7A, Ui.dp(this, 34)));
        ic.addView(Ui.icon(this, com.isikyolu.game.ui.Icon.HEART, 0xFFFF5C7A, 38), new FrameLayout.LayoutParams(Ui.dp(this, 38), Ui.dp(this, 38), Gravity.CENTER));
        o.cardView().addView(ic, new LinearLayout.LayoutParams(Ui.dp(this, 68), Ui.dp(this, 68)));
        o.title("Canın kalmadı");
        com.isikyolu.engine.Lives.Clock clk = Game.clock();
        TextView body = o.body("");
        Runnable[] tick = new Runnable[1];
        tick[0] = () -> {
            com.isikyolu.engine.Lives.update(Game.progress(), Game.clock());
            if (Game.progress().lives > 0) { body.setText("Yeni bir can geldi! Bölümlere dönebilirsin."); return; }
            body.setText("Her 15 dakikada 1 can yenilenir (en fazla " + com.isikyolu.engine.Lives.MAX + ").\nSonraki can: "
                    + com.isikyolu.engine.Lives.format(com.isikyolu.engine.Lives.msUntilNext(Game.progress(), Game.clock()))
                    + "\n\nGünlük bulmaca can harcamaz.");
            body.postDelayed(tick[0], 1000);
        };
        tick[0].run();
        com.isikyolu.engine.Economy.Item life = com.isikyolu.engine.Economy.item("life1");
        int lp = com.isikyolu.engine.Economy.price(Game.progress(), life, Game.shopDay());
        if (videoAvailable(com.isikyolu.engine.AdPolicy.Reward.LIFE)) {
            o.button("Video izle · +1 can (bugün " + videosLeft(com.isikyolu.engine.AdPolicy.Reward.LIFE) + ")", Ui.PRIMARY, com.isikyolu.game.ui.Icon.PLAY, v ->
                    watchVideo(com.isikyolu.engine.AdPolicy.Reward.LIFE, () -> {
                        o.dismiss();
                        toast("+1 can kazandın");
                        if (retryLevel > 0) goLevel(retryLevel);
                    }));
        }
        o.button("+1 can al · " + lp + " kristal", Ui.GOLD, com.isikyolu.game.ui.Icon.CRYSTAL, v -> {
            String why = com.isikyolu.engine.Economy.blockReason(Game.progress(), life, Game.shopDay());
            if (why != null) { Game.audio().play(Audio.LOCKED); toast(why); return; }
            com.isikyolu.engine.Economy.buy(Game.progress(), life, Game.shopDay(), Game.clock());
            Game.save();
            o.dismiss();
            toast("+1 can alındı");
            checkAchievements();
            if (retryLevel > 0) goLevel(retryLevel);
        });
        o.button("Market", Ui.PURPLE, com.isikyolu.game.ui.Icon.BAG, v -> { o.dismiss(); goMarket(2); });
        o.button("Günlük Bulmaca", Ui.SECONDARY, com.isikyolu.game.ui.Icon.CALENDAR, v -> { o.dismiss(); goDaily(); });
        o.button("Tamam", Ui.SECONDARY, -1, v -> o.dismiss());
        o.setOnDismiss(() -> body.removeCallbacks(tick[0]));
        o.show(overlayLayer());
        Game.audio().play(Audio.LOCKED);
    }
    public void goDailyGame(Level level, int epochDay) { show(new GameScreen(this, level, epochDay), true); }
    public void goDaily() { show(new DailyScreen(this), true); }
    public void goAchievements() { show(new AchievementsScreen(this), true); }
    public void goSettings() { show(new SettingsScreen(this), true); }
    public void goMarket(int tab) { show(new com.isikyolu.game.screens.MarketScreen(this, tab), true); }

    /** Yeni başarıları işaretler, ödüllerini verir ve sırayla gösterir. */
    public void checkAchievements() {
        java.util.List<com.isikyolu.engine.Achievements.Def> fresh = com.isikyolu.engine.Achievements.update(Game.progress());
        if (fresh.isEmpty()) return;
        long delay = 300;
        for (com.isikyolu.engine.Achievements.Def d : fresh) {
            com.isikyolu.engine.Economy.Reward r = com.isikyolu.engine.Economy.onAchievement(Game.progress(), d);
            String gain = "+" + r.crystals + " kristal" + (r.gems > 0 ? " · +" + r.gems + " Prizma Taşı" : "");
            root.postDelayed(() -> {
                Game.audio().play(Audio.ACHIEVE);
                banner(com.isikyolu.game.ui.Icon.TROPHY, "Başarı: " + d.title, d.description + "  " + gain);
            }, delay);
            delay += 3200;
        }
        Game.save();
    }

    /** Üstten kayan bildirim (başarı kazanıldı vb.). */
    public void banner(int icon, String title, String subtitle) {
        LinearLayout box = Ui.horizontal(this);
        box.setBackground(Ui.card(this));
        int p = Ui.dp(this, 14);
        box.setPadding(p, p, p * 2, p);
        FrameLayout ic = new FrameLayout(this);
        ic.setBackground(Ui.round(0x33F5C542, Ui.dp(this, 22)));
        ic.addView(Ui.icon(this, icon, Palette.GOLD, 26), new FrameLayout.LayoutParams(Ui.dp(this, 26), Ui.dp(this, 26), Gravity.CENTER));
        box.addView(ic, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
        LinearLayout col = Ui.vertical(this);
        col.setPadding(Ui.dp(this, 12), 0, 0, 0);
        col.addView(Ui.text(this, title, 15, Palette.GOLD, Ui.BOLD));
        TextView sub = Ui.text(this, subtitle, 13, Palette.TEXT_DIM, Ui.REGULAR);
        col.addView(sub);
        box.addView(col);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        lp.topMargin = Ui.dp(this, 16);
        lp.leftMargin = lp.rightMargin = Ui.dp(this, 16);
        root.addView(box, lp);
        box.setTranslationZ(Ui.dp(this, 40)); // sonradan açılan pencerelerin (ör. Tamamlandı) üstünde kalır
        box.setTranslationY(-Ui.dp(this, 120));
        box.animate().translationY(0).setDuration(380).setInterpolator(new DecelerateInterpolator()).start();
        box.postDelayed(() -> box.animate().translationY(-Ui.dp(this, 140)).alpha(0f).setDuration(320)
                .withEndAction(() -> root.removeView(box)).start(), 2800);
    }

    public void toast(String msg) {
        TextView t = Ui.text(this, msg, 14, Palette.WHITE, Ui.MEDIUM);
        t.setBackground(Ui.round(0xEE354870, Ui.dp(this, 18)));
        int p = Ui.dp(this, 12);
        t.setPadding(p * 2, p, p * 2, p);
        t.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lp.bottomMargin = Ui.dp(this, 120);
        lp.leftMargin = lp.rightMargin = Ui.dp(this, 24);
        root.addView(t, lp);
        t.setTranslationZ(Ui.dp(this, 40));
        t.setAlpha(0f);
        t.animate().alpha(1f).setDuration(160).start();
        t.postDelayed(() -> t.animate().alpha(0f).setDuration(220).withEndAction(() -> root.removeView(t)).start(), 1800);
    }

    @SuppressWarnings("deprecation")
    @Override public void onBackPressed() { handleBack(); }

    /** Geri: açık pencereyi kapatır, yoksa ekrana sorar; ana menüde uygulamadan çıkar. */
    public void handleBack() {
        // önce açık pencereyi kapat
        for (int i = root.getChildCount() - 1; i >= 0; i--) {
            View v = root.getChildAt(i);
            if (v instanceof com.isikyolu.game.ui.Overlay && !((com.isikyolu.game.ui.Overlay) v).isClosing()) {
                com.isikyolu.game.ui.Overlay o = (com.isikyolu.game.ui.Overlay) v;
                if (o.isCancelable()) o.dismiss();
                return;
            }
        }
        if (current != null && current.onBack()) return;
        if (current instanceof MenuScreen || current instanceof SplashScreen) { finish(); return; }
        goMenu();
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        Game.audio().onResume();
        updateBanner();
        retryAds();
        if (current != null) current.onAppResume();
    }

    @Override protected void onPause() {
        super.onPause();
        Game.save();
        Game.audio().onPause();
        resumed = false;
        setBannerRunning(false);
        // tam ekran reklam açıldı: oyun duraklatma penceresi açılmasın
        if (current != null && !com.isikyolu.game.ads.Ads.get().fullscreenShowing()) current.onAppPause();
    }

    @Override protected void onDestroy() {
        // etkinlik kapanıyor ya da yeniden oluşturuluyor: yarım deneme bırakılmış sayılır
        if (current != null) current.onHide();
        Game.save();
        com.isikyolu.game.ads.Ads.get().destroyBanner(banner);
        super.onDestroy();
        if (isFinishing()) Game.audio().release();
    }

    /** Overlay'ler için: ekranda açık pencere var mı? */
    public boolean hasOverlay() {
        for (int i = 0; i < root.getChildCount(); i++)
            if (root.getChildAt(i) instanceof com.isikyolu.game.ui.Overlay && !((com.isikyolu.game.ui.Overlay) root.getChildAt(i)).isClosing()) return true;
        return false;
    }

}
