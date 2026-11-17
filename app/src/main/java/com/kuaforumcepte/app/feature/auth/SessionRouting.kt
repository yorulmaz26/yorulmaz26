package com.kuaforumcepte.app.feature.auth

import com.kuaforumcepte.app.data.session.SessionSnapshot
import com.kuaforumcepte.domain.model.EntryRole

sealed interface SessionTarget {
    data object CustomerRegister : SessionTarget
    data object CustomerHome : SessionTarget
    data object Invitations : SessionTarget
    data object OwnerRegister : SessionTarget
    data object SalonPicker : SessionTarget
    data class BusinessHome(val salonId: String) : SessionTarget
    /** Platform yöneticisi (moderatör): girişte Moderatör Paneli açılır. */
    data object AdminPanel : SessionTarget
}

/**
 * Giriş sonrası yönlendirme (saf fonksiyon, unit test edilir).
 * İşletme girişinde rol, telefon değil sunucudaki aktif üyeliklerden belirlenir.
 */
object SessionRouting {
    fun decide(role: EntryRole, snapshot: SessionSnapshot, preferredSalonId: String?): SessionTarget = when (role) {
        EntryRole.CUSTOMER -> if (snapshot.profileExists) SessionTarget.CustomerHome else SessionTarget.CustomerRegister
        EntryRole.BUSINESS -> {
            val active = snapshot.activeMemberships
            when {
                snapshot.pendingInvitations.isNotEmpty() -> SessionTarget.Invitations
                active.isEmpty() -> SessionTarget.OwnerRegister
                active.size == 1 -> SessionTarget.BusinessHome(active.single().salonId)
                active.any { it.salonId == preferredSalonId } -> SessionTarget.BusinessHome(preferredSalonId!!)
                else -> SessionTarget.SalonPicker
            }
        }
    }
}
