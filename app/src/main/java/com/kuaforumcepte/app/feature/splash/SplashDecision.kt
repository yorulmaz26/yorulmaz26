package com.kuaforumcepte.app.feature.splash

import com.kuaforumcepte.domain.model.EntryRole

sealed interface SplashDecision {
    data object SetupRequired : SplashDecision
    data object RoleSelection : SplashDecision
    /** Oturum sürüyor: ilgili giriş grafiği bootstrapSession ile yönlendirir (Aşama 2). */
    data class Resume(val role: EntryRole) : SplashDecision
}

/** Saf karar fonksiyonu (unit test edilir). */
object SplashDecider {
    data class Input(
        val firebaseConfigured: Boolean,
        val signedIn: Boolean,
        val rememberMe: Boolean,
        val lastRole: EntryRole?,
    )

    data class Output(val decision: SplashDecision, val signOut: Boolean)

    fun decide(input: Input): Output = when {
        !input.firebaseConfigured -> Output(SplashDecision.SetupRequired, signOut = false)
        input.signedIn && !input.rememberMe -> Output(SplashDecision.RoleSelection, signOut = true)
        input.signedIn && input.lastRole != null -> Output(SplashDecision.Resume(input.lastRole), signOut = false)
        else -> Output(SplashDecision.RoleSelection, signOut = false)
    }
}
