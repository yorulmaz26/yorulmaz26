package com.golgen.oyun;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.window.OnBackInvokedDispatcher;

/** Tek aktivite: tam ekran, dikey, kesik (notch) alanına uyumlu. */
public final class MainActivity extends Activity {
    private GameView view;
    private android.widget.FrameLayout root;

    /** Reklamları kurar veya kaldırır (test modu). Reklam kimlikleri derleme ayarından gelir. */
    public void setupAds(boolean enabled) {
        if (view == null || root == null) return;
        boolean active = !(view.ads instanceof com.golgen.oyun.platform.NoAds);
        if (enabled == active) return;
        view.ads.destroy();
        view.onBannerHeightChanged(0);
        if (enabled) {
            try {
                view.ads = new com.golgen.oyun.platform.AdMobAds(this, root, view,
                        BuildConfig.ADMOB_BANNER_ID, BuildConfig.ADMOB_INTERSTITIAL_ID, BuildConfig.ADMOB_REWARDED_ID);
            } catch (RuntimeException e) {
                android.util.Log.w("GolgenAds", "Reklamlar başlatılamadı", e);
                view.ads = new com.golgen.oyun.platform.NoAds();
            }
        } else {
            view.ads = new com.golgen.oyun.platform.NoAds();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window win = getWindow();
        win.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams lp = win.getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            win.setAttributes(lp);
        }
        if (Build.VERSION.SDK_INT >= 30) win.setDecorFitsSystemWindows(false);
        view = new GameView(this);
        root = new android.widget.FrameLayout(this);
        root.addView(view, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT, android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);
        hideSystemBars();
        view.handleDebugIntent(getIntent());
        setupAds(!view.adsDisabledForTest());
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, () -> view.handleBack());
        }
    }

    @SuppressWarnings("deprecation")
    private void hideSystemBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.systemBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        view.handleDebugIntent(intent);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemBars();
    }

    @Override
    protected void onResume() {
        super.onResume();
        view.onResume();
    }

    @Override
    protected void onPause() {
        view.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        view.release();
        super.onDestroy();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        // API 33 altı
        view.handleBack();
    }
}
