package com.kuaforumcepte.app.core.firebase

import android.content.Context
import com.google.firebase.FirebaseApp
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * google-services.json olmadan derlenen build'lerde FirebaseApp başlatılmaz.
 * Firebase servisleri yalnızca isConfigured == true iken kullanılmalıdır
 * (servisler dagger.Lazy ile enjekte edilir).
 */
@Singleton
class FirebaseStatus @Inject constructor(@ApplicationContext context: Context) {
    val isConfigured: Boolean = FirebaseApp.getApps(context).isNotEmpty()
}
