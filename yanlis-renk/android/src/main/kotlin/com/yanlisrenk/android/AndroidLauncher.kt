package com.yanlisrenk.android

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.yanlisrenk.game.PlatformServices
import com.yanlisrenk.game.YanlisRenkGame
import com.yanlisrenk.logic.save.SaveStorage
import com.yanlisrenk.logic.time.PlatformClock
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.Executors

class AndroidLauncher : AndroidApplication() {
    private var adService: AdMobService? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installCrashReporter()
        val cfg = AndroidApplicationConfiguration()
        cfg.useImmersiveMode = true
        cfg.numSamples = 4 // kenar yumuşatma
        cfg.useAccelerometer = false
        cfg.useCompass = false
        cfg.useGyroscope = false
        // oyun görünümü + altta reklam banner'ı (oyun, banner yüksekliği kadar yukarıya yerleşir)
        val bannerView = com.google.android.gms.ads.AdView(this)
        val ads = AdMobService(this, bannerView)
        adService = ads
        val gameView = initializeForView(YanlisRenkGame(AndroidPlatform(this, ads)), cfg)
        val layout = android.widget.RelativeLayout(this)
        layout.addView(gameView, android.widget.RelativeLayout.LayoutParams(-1, -1))
        val lp = android.widget.RelativeLayout.LayoutParams(-1, -2)
        lp.addRule(android.widget.RelativeLayout.ALIGN_PARENT_BOTTOM)
        lp.addRule(android.widget.RelativeLayout.CENTER_HORIZONTAL)
        bannerView.visibility = android.view.View.GONE
        layout.addView(bannerView, lp)
        setContentView(layout)
        runCatching { ads.start() }
        showPreviousCrash()
    }

    override fun onPause() { adService?.pause(); super.onPause() }
    override fun onResume() { super.onResume(); adService?.resume() }
    override fun onDestroy() { adService?.destroy(); super.onDestroy()
    }

    private val crashFile get() = File(filesDir, "son_hata.txt")

    /** Yakalanmamış hatayı dosyaya yazar; bir sonraki açılışta gösterilir. */
    private fun installCrashReporter() {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                val sw = java.io.StringWriter()
                e.printStackTrace(java.io.PrintWriter(sw))
                crashFile.writeText("Yanlış Renk ${BuildConfig.VERSION_NAME} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}\n\n$sw")
            }
            prev?.uncaughtException(t, e)
        }
    }

    private fun showPreviousCrash() {
        val f = crashFile
        if (!f.exists()) return
        val text = runCatching { f.readText() }.getOrDefault("")
        f.delete()
        runOnUiThread {
            android.app.AlertDialog.Builder(this)
                .setTitle(getString(R.string.crash_title))
                .setMessage(getString(R.string.crash_message) + "\n\n" + text.take(3000))
                .setPositiveButton(getString(R.string.crash_copy)) { _, _ ->
                    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("Yanlış Renk hata", text))
                }
                .setNegativeButton(getString(R.string.crash_close), null)
                .show()
        }
    }
}

/** Android platform hizmetleri. */
class AndroidPlatform(private val ctx: Context, override val ads: com.yanlisrenk.game.AdService = com.yanlisrenk.game.NoAds) : PlatformServices {
    private val io = Executors.newSingleThreadExecutor()

    override val clock = object : PlatformClock {
        override fun wallMillis() = System.currentTimeMillis()
        /** Uyku dahil, kullanıcı tarafından değiştirilemeyen monoton saat. */
        override fun elapsedMillis() = SystemClock.elapsedRealtime()
        override fun bootCount(): Int = runCatching { Settings.Global.getInt(ctx.contentResolver, Settings.Global.BOOT_COUNT) }.getOrDefault(-1)
    }

    override val storage: SaveStorage = AtomicFileStorage(ctx.filesDir)

    override fun tzOffsetMillis() = TimeZone.getDefault().getOffset(System.currentTimeMillis()).toLong()

    /**
     * Güvenilir zaman: önce yapılandırılmış arka uç (server modülü, /time), sonra HTTPS "Date" başlığı.
     * TLS sayesinde yanıtı aradaki biri değiştiremez.
     */
    override fun fetchServerTime(onResult: (Long?) -> Unit) {
        io.execute {
            val t = backendUrl?.let { fetchJson(it) } ?: fetchDate("https://www.google.com/generate_204") ?: fetchDate("https://www.cloudflare.com/cdn-cgi/trace")
            onResult(t)
        }
    }

    private fun fetchDate(url: String): Long? = runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "HEAD"; c.connectTimeout = 5000; c.readTimeout = 5000; c.useCaches = false
        c.connect(); val d = c.date; c.disconnect()
        if (d > 0) d else null
    }.getOrNull()

    private fun fetchJson(url: String): Long? = runCatching {
        val c = URL("$url/time").openConnection() as HttpURLConnection
        c.connectTimeout = 5000; c.readTimeout = 5000
        val body = c.inputStream.bufferedReader().readText(); c.disconnect()
        Regex("\"now\"\\s*:\\s*(\\d+)").find(body)?.groupValues?.get(1)?.toLong()
    }.getOrNull()

    override fun vibrate(ms: Int) {
        runCatching {
            @Suppress("DEPRECATION")
            val v = ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(ms.toLong(), VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    override fun newInstallId() = UUID.randomUUID().toString()
    override val platformName = "Android ${Build.VERSION.RELEASE}"
    override val versionName: String = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "1.0.0" }.getOrDefault("1.0.0")

    companion object {
        /** Kendi zaman sunucunuzu kurduğunuzda adresini buraya yazın (ör. "https://zaman.ornek.com"). */
        var backendUrl: String? = null
    }
}

/** Atomik dosya yazımı: geçici dosya + yeniden adlandırma; yarım yazılmış kayıt oluşmaz. */
class AtomicFileStorage(private val dir: File) : SaveStorage {
    override fun read(name: String): String? = runCatching { File(dir, name).takeIf { it.exists() }?.readText() }.getOrNull()
    override fun write(name: String, content: String) {
        val f = File(dir, name)
        val tmp = File(dir, "$name.tmp")
        tmp.outputStream().use { out -> out.write(content.toByteArray()); out.fd.sync() }
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
    }
    override fun delete(name: String) { File(dir, name).delete() }
}
