package com.kuaforumcepte.app.core.firebase

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/** Release build: Play Integrity (Play Console'da uygulama bağlantısı ve SHA-256 gerekir). */
object AppCheckProviders {
    fun factory(): AppCheckProviderFactory = PlayIntegrityAppCheckProviderFactory.getInstance()
}
