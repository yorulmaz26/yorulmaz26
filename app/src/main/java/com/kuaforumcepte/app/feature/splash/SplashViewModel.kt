package com.kuaforumcepte.app.feature.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.kuaforumcepte.app.core.datastore.AppPreferences
import com.kuaforumcepte.app.core.firebase.FirebaseStatus
import dagger.Lazy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val firebaseStatus: FirebaseStatus,
    private val auth: Lazy<FirebaseAuth>,
    private val preferences: AppPreferences,
) : ViewModel() {

    private val _decision = MutableStateFlow<SplashDecision?>(null)
    val decision: StateFlow<SplashDecision?> = _decision.asStateFlow()

    init {
        viewModelScope.launch {
            val minimumShow = async { delay(MIN_SPLASH_MS) }
            val settings = preferences.current()
            val configured = firebaseStatus.isConfigured
            val signedIn = configured && auth.get().currentUser != null
            val out = SplashDecider.decide(
                SplashDecider.Input(configured, signedIn, settings.rememberMe, settings.lastEntryRole),
            )
            if (out.signOut) {
                auth.get().signOut()
                preferences.clearSessionScoped()
            }
            minimumShow.await()
            _decision.value = out.decision
        }
    }

    private companion object {
        const val MIN_SPLASH_MS = 1_200L
    }
}
