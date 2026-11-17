package com.kuaforumcepte.app.core.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.persistentCacheSettings
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
import com.kuaforumcepte.app.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Firebase servis örnekleri. Debug + local.properties'te useFirebaseEmulator=true ise
 * Emulator Suite'e bağlanır; release build'de BuildConfig.USE_FIREBASE_EMULATOR daima false.
 */
@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    private val emulatorHost: String get() = BuildConfig.FIREBASE_EMULATOR_HOST

    @Provides @Singleton
    fun provideAuth(): FirebaseAuth = FirebaseAuth.getInstance().apply {
        setLanguageCode("tr")
        if (BuildConfig.USE_FIREBASE_EMULATOR) useEmulator(emulatorHost, 9099)
    }

    @Provides @Singleton
    fun provideFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance().apply {
        if (BuildConfig.USE_FIREBASE_EMULATOR) useEmulator(emulatorHost, 8080)
        // Düşük bağlantıda okunabilirlik için kalıcı yerel önbellek (yazımlar yine sunucu fonksiyonlarıyla).
        firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(persistentCacheSettings { setSizeBytes(50L * 1024 * 1024) })
            .build()
    }

    @Provides @Singleton
    fun provideFunctions(): FirebaseFunctions = FirebaseFunctions.getInstance(BuildConfig.FUNCTIONS_REGION).apply {
        if (BuildConfig.USE_FIREBASE_EMULATOR) useEmulator(emulatorHost, 5001)
    }

    @Provides @Singleton
    fun provideStorage(): FirebaseStorage = FirebaseStorage.getInstance().apply {
        if (BuildConfig.USE_FIREBASE_EMULATOR) useEmulator(emulatorHost, 9199)
    }

    @Provides @Singleton
    fun provideMessaging(): FirebaseMessaging = FirebaseMessaging.getInstance()
}
