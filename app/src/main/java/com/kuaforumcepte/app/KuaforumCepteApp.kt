package com.kuaforumcepte.app

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.kuaforumcepte.app.core.firebase.AppCheckProviders
import com.kuaforumcepte.app.core.firebase.FirebaseStatus
import com.kuaforumcepte.app.core.push.Notifications
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class KuaforumCepteApp : Application() {

    @Inject lateinit var firebaseStatus: FirebaseStatus

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
        if (firebaseStatus.isConfigured) {
            // App Check: release → Play Integrity, debug → Debug provider (src/debug|release).
            FirebaseAppCheck.getInstance(FirebaseApp.getInstance())
                .installAppCheckProviderFactory(AppCheckProviders.factory())
            // Çökme raporları yalnızca yayın sürümünde gönderilir; içerik kişisel veri taşımaz (bkz. docs/PLAY_DATA_SAFETY.md).
            FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = !BuildConfig.DEBUG
        }
    }
}
