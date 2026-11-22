package com.isikyolu.game.ads;

/** Etkin reklam sağlayıcısı. Testler {@link #setForTests} ile sahte sağlayıcı kurar. */
public final class Ads {
    private Ads() {}

    private static AdProvider provider;
    private static AdProvider testOverride;

    public static void setForTests(AdProvider p) { testOverride = p; provider = p; }

    public static void resetForTests() { testOverride = null; provider = null; }

    public static AdProvider get() {
        if (provider == null) {
            if (testOverride != null) provider = testOverride;
            else if ("robolectric".equals(android.os.Build.FINGERPRINT)) provider = new NoAds(); // JVM testleri: SDK yok
            else provider = new AdMobProvider();
        }
        return provider;
    }
}
