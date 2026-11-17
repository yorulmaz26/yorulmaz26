package com.kuaforumcepte.app.core.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.Clock
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class IoDispatcher
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class DefaultDispatcher
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object CoreModule {
    @Provides @IoDispatcher fun provideIo(): CoroutineDispatcher = Dispatchers.IO
    @Provides @DefaultDispatcher fun provideDefault(): CoroutineDispatcher = Dispatchers.Default

    @Provides @Singleton @ApplicationScope
    fun provideAppScope(@DefaultDispatcher dispatcher: CoroutineDispatcher): CoroutineScope =
        CoroutineScope(SupervisorJob() + dispatcher)

    /** Testlerde sabit saat enjekte edilebilsin diye. Saklama UTC; gösterim BusinessTime.ZONE. */
    @Provides @Singleton fun provideClock(): Clock = Clock.systemUTC()
}
