package com.kuaforumcepte.app.core.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation route'ları. Ekran haritası: docs/ARCHITECTURE.md §3.
 * Sonraki aşamalarda auth, customer ve business grafikleri buraya eklenir.
 */
@Serializable data object SplashRoute
@Serializable data object FirebaseSetupRoute
@Serializable data object RoleSelectionRoute

/** role: EntryRole.name (CUSTOMER | BUSINESS) */
@Serializable data class AuthRoute(val role: String)
