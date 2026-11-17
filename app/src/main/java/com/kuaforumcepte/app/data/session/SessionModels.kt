package com.kuaforumcepte.app.data.session

import com.kuaforumcepte.domain.model.MemberRole
import com.kuaforumcepte.domain.model.MemberStatus

data class Membership(
    val salonId: String,
    val salonName: String,
    val salonCode: String,
    val role: MemberRole,
    val status: MemberStatus,
) {
    val isActive: Boolean get() = status == MemberStatus.ACTIVE
}

data class PendingInvitation(
    val salonId: String,
    val inviteId: String,
    val salonName: String,
    val firstName: String,
    val lastName: String,
    val specialty: String,
    val expiresAtIso: String,
)

/** bootstrapSession yanıtı. */
data class SessionSnapshot(
    val profileExists: Boolean,
    val firstName: String?,
    val lastName: String?,
    val phoneE164: String?,
    val memberships: List<Membership>,
    val pendingInvitations: List<PendingInvitation>,
    val activeAppointmentId: String?,
) {
    val activeMemberships: List<Membership> get() = memberships.filter { it.isActive }
}
