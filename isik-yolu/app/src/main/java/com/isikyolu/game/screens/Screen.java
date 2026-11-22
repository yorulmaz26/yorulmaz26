package com.isikyolu.game.screens;

import android.widget.FrameLayout;

import com.isikyolu.game.MainActivity;

/** Tam ekran bir oyun ekranı. */
public abstract class Screen extends FrameLayout {
    protected final MainActivity act;

    protected Screen(MainActivity a) {
        super(a);
        act = a;
    }

    public void onShow() {}

    public void onHide() {}

    /** Geri tuşunu kendisi işlerse true. */
    public boolean onBack() { return false; }

    /** Uygulama arka plana geçti. */
    public void onAppPause() {}

    public void onAppResume() {}

    /** Ekranın altında banner reklam gösterilsin mi (oyun akışını bozmayan ekranlar). */
    public boolean wantsBanner() { return false; }

    /** Bir reklam yüklendi ya da video izlendi: reklam düğmeleri olan ekran kendini yenileyebilir. */
    public void onAdsChanged() {}
}
