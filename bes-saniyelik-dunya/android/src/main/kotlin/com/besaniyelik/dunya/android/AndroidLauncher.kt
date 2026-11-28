package com.besaniyelik.dunya.android

import android.os.Bundle
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.content.Context
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.besaniyelik.dunya.BesSaniyelikDunya
import com.besaniyelik.dunya.Platform
import com.besaniyelik.dunya.ads.AdService

class AndroidLauncher : AndroidApplication() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val config = AndroidApplicationConfiguration().apply {
            useAccelerometer = false
            useCompass = false
            useGyroscope = false
            useImmersiveMode = true
            numSamples = 0
        }
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        // Reklam altyapısı kurulamazsa oyun reklamsız çalışır
        val adService = try { AndroidAds(this) } catch (t: Throwable) { android.util.Log.w("Reklam", "reklam altyapısı kurulamadı", t); null }
        androidAds = adService
        val platform = object : Platform {
            override val name = "android"
            override val ads: AdService get() = adService ?: AdService.NONE
            override val time: com.besaniyelik.dunya.progress.TimeSource = AndroidTimeSource(applicationContext)
            override fun systemLanguage(): String =
                if (Build.VERSION.SDK_INT >= 24) resources.configuration.locales[0].language else java.util.Locale.getDefault().language
            override fun vibrate(ms: Int) {
                val v = vibrator ?: return
                try {
                    if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(ms.toLong(), VibrationEffect.DEFAULT_AMPLITUDE))
                    else @Suppress("DEPRECATION") v.vibrate(ms.toLong())
                } catch (_: Throwable) {}
            }
        }
        initialize(BesSaniyelikDunya(platform), config)
        // onay bilgisi -> (gerekirse) onay formu -> reklamların yüklenmesi; oyunu bekletmez
        adService?.start()
    }

    private var androidAds: AndroidAds? = null

    override fun onResume() {
        super.onResume()
        androidAds?.onHostResumed()
    }

    override fun onPause() {
        androidAds?.onHostPaused()
        super.onPause()
    }

    /**
     * Güvenlik ağı: geri tuşu libGDX'e ulaşmazsa (ör. odak afiş reklamdayken) uygulamadan çıkmak yerine oyuna
     * iletilir; oyun kendi geri davranışını uygular (menüye dön, duraklat).
     */
    @Deprecated("Android 13+ için yeni geri API'si var; manifestte eski davranış açık bırakıldı")
    override fun onBackPressed() {
        val app = com.badlogic.gdx.Gdx.app
        if (app == null) { @Suppress("DEPRECATION") super.onBackPressed(); return }
        app.postRunnable { com.badlogic.gdx.Gdx.input?.inputProcessor?.keyDown(com.badlogic.gdx.Input.Keys.BACK) }
    }

    override fun onDestroy() {
        androidAds?.onHostDestroyed()
        super.onDestroy()
    }
}

/**
 * Can yenilenmesi için güvenilir zaman: elapsedRealtime cihaz açıldığından beri geçen süredir (uykuyu da sayar,
 * kullanıcı saati değiştirince etkilenmez); açılış sayacı cihazın yeniden başlatıldığını anlamaya yarar.
 */
class AndroidTimeSource(private val ctx: Context) : com.besaniyelik.dunya.progress.TimeSource {
    override fun wallMs(): Long = System.currentTimeMillis()
    override fun monoMs(): Long = android.os.SystemClock.elapsedRealtime()
    override fun bootCount(): Int = try {
        android.provider.Settings.Global.getInt(ctx.contentResolver, android.provider.Settings.Global.BOOT_COUNT, -1)
    } catch (_: Throwable) { -1 }
}
