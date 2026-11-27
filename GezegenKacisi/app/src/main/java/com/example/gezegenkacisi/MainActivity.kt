package com.example.gezegenkacisi

import android.app.Activity
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import android.window.OnBackInvokedDispatcher

class MainActivity : Activity() {

    private lateinit var gameView: GameView
    private var ads: AdManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        gameView = GameView(this)
        // Oyun tüm ekranı kaplar; banner reklam yalnızca menülerde, altta üstüne biner
        val root = FrameLayout(this)
        root.addView(gameView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        setContentView(root)
        hideSystemBars()
        ads = runCatching { AdManager(this, root) }.getOrNull()?.also {
            gameView.ads = it
            it.start()
        }
        // Android 13+: geri hareketi bu geri çağrıyla gelir (Android 16'yı hedefleyen uygulamalarda
        // onBackPressed artık çağrılmaz). Oyundayken menüye döner, ana ekrandayken uygulama arka plana gider.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) {
                if (!gameView.onBack()) moveTaskToBack(true)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ads?.onResume()
        gameView.resume()
    }

    override fun onPause() {
        gameView.pause()
        ads?.onPause()
        super.onPause()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        ads?.onConfigurationChanged()
        gameView.onConfigChanged() // telefonun dili değiştiyse metinler yenilenir
    }

    override fun onDestroy() {
        gameView.release()
        ads?.onDestroy()
        super.onDestroy()
    }

    /** Oyundayken geri tuşu menüye döner; menüdeyken uygulamadan çıkar. */
    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (!gameView.onBack()) super.onBackPressed()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars() else gameView.onFocusLost()
    }

    /** Durum ve gezinme çubuklarını gizleyip oyunu tam ekran yapar. */
    @Suppress("DEPRECATION")
    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
        }
    }
}
