package com.golgen.oyun.platform;

/** Reklamsız mod (test, kapalı ağ veya SDK hatası): her şey sessizce geçilir. */
public final class NoAds implements Ads {
    @Override public void setBannerWanted(boolean wanted) {}
    @Override public int bannerHeight() { return 0; }
    @Override public void setBottomMargin(int px) {}
    @Override public boolean isInterstitialReady() { return false; }
    @Override public void showInterstitial(Runnable after) { if (after != null) after.run(); }
    @Override public boolean isRewardedReady() { return false; }
    @Override public void showRewarded(Runnable onReward, Runnable onClosed) { if (onClosed != null) onClosed.run(); }
    @Override public void onPause() {}
    @Override public void onResume() {}
    @Override public void destroy() {}
}
