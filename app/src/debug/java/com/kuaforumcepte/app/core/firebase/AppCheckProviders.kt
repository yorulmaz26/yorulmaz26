package com.kuaforumcepte.app.core.firebase

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Debug build: Logcat'te "DebugAppCheckProvider" etiketiyle yazdırılan debug token'ı
 * Firebase Console > App Check > Uygulamalar > Debug token'ları yönet kısmına ekleyin.
 */
object AppCheckProviders {
    fun factory(): AppCheckProviderFactory = DebugAppCheckProviderFactory.getInstance()
}
