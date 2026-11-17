package com.kuaforumcepte.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.kuaforumcepte.app.core.designsystem.theme.KuaforumTheme
import com.kuaforumcepte.app.core.navigation.KcNavHost
import com.kuaforumcepte.app.data.link.DeepLinkRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var deepLinks: DeepLinkRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Uygulamayı açan bağlantı (App Link / QR / bildirim) oturum hazır olana kadar bekletilir.
        if (savedInstanceState == null) deepLinks.offer(intent?.dataString)
        enableEdgeToEdge()
        setContent {
            val appViewModel: AppViewModel = hiltViewModel()
            val themeMode = appViewModel.themeMode.collectAsStateWithLifecycle()
            KuaforumTheme(themeMode = themeMode.value) {
                KcNavHost()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLinks.offer(intent.dataString)
    }
}
