package com.kuaforumcepte.app.core.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation route'ları. Ekran haritası: docs/ARCHITECTURE.md §3.
 * role alanları EntryRole.name (CUSTOMER | BUSINESS) taşır.
 */
@Serializable data object SplashRoute
@Serializable data object FirebaseSetupRoute
@Serializable data object RoleSelectionRoute

// Kimlik
@Serializable data class PhoneEntryRoute(val role: String)
@Serializable data class OtpRoute(val role: String)
@Serializable data class SessionGateRoute(val role: String)

// Kayıt
@Serializable data object CustomerRegisterRoute
@Serializable data object OwnerRegisterRoute
@Serializable data class OwnerRegisterSuccessRoute(val salonName: String, val code: String)
@Serializable data class LegalRoute(val document: String)

// İşletme
@Serializable data object InvitationsRoute
@Serializable data object SalonPickerRoute
@Serializable data object BusinessHomeRoute
@Serializable data object StaffManagementRoute
@Serializable data object ShopSettingsRoute
@Serializable data object MediaManagerRoute
@Serializable data object ShareSalonRoute

// Salon (herkese açık sayfa; deep link / QR hedefi)
@Serializable data class SalonDetailRoute(val salonId: String)

// Müşteri
@Serializable data object CustomerHomeRoute

// Ortak
@Serializable data class AccountRoute(val role: String)
