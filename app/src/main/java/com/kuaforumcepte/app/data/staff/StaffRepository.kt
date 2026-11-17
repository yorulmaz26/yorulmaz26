package com.kuaforumcepte.app.data.staff

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import com.kuaforumcepte.app.core.data.AppException
import com.kuaforumcepte.app.core.data.FunctionsClient
import com.kuaforumcepte.app.data.registration.LegalDocument
import com.kuaforumcepte.domain.model.MemberRole
import com.kuaforumcepte.domain.model.MemberStatus
import com.kuaforumcepte.domain.model.Rating
import com.kuaforumcepte.domain.phone.PhoneNumbers
import dagger.Lazy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class StaffMember(
    val uid: String,
    val firstName: String,
    val lastName: String,
    val specialty: String,
    val role: MemberRole,
    val status: MemberStatus,
    val rating: Rating,
) {
    val fullName: String get() = "$firstName $lastName"
}

enum class InvitationStatus(val label: String) {
    PENDING("Bekliyor"), ACCEPTED("Kabul edildi"), DECLINED("Reddedildi"), REVOKED("İptal edildi"), EXPIRED("Süresi doldu")
}

data class StaffInvitation(
    val id: String,
    val firstName: String,
    val lastName: String,
    val specialty: String,
    val phoneE164: String,
    val status: InvitationStatus,
    val createdAt: Instant?,
    val expiresAt: Instant?,
) {
    val fullName: String get() = "$firstName $lastName"
    val phoneDisplay: String get() = PhoneNumbers.formatForDisplay(phoneE164)
}

/** Salon personeli: Firestore anlık dinleyici (okuma) + yetkili callable'lar (yazma). */
@Singleton
class StaffRepository @Inject constructor(
    private val firestore: Lazy<FirebaseFirestore>,
    private val functions: FunctionsClient,
) {
    private fun salon(salonId: String) = firestore.get().collection("salons").document(salonId)

    fun members(salonId: String): Flow<List<StaffMember>> =
        salon(salonId).collection("members").snapshots().map { qs ->
            qs.documents.mapNotNull { d ->
                StaffMember(
                    uid = d.id,
                    firstName = d.getString("firstName").orEmpty(),
                    lastName = d.getString("lastName").orEmpty(),
                    specialty = d.getString("specialty").orEmpty(),
                    role = MemberRole.entries.firstOrNull { it.name == d.getString("role") } ?: return@mapNotNull null,
                    status = MemberStatus.entries.firstOrNull { it.name == d.getString("status") } ?: return@mapNotNull null,
                    rating = Rating(d.getLong("ratingSum") ?: 0, d.getLong("ratingCount") ?: 0),
                )
            }.sortedWith(compareBy<StaffMember>({ it.role != MemberRole.OWNER }, { it.status != MemberStatus.ACTIVE }, { it.fullName }))
        }.catch { throw AppException.from(it) }

    /** Yalnızca salon sahibi okuyabilir (Firestore kuralı). */
    fun invitations(salonId: String): Flow<List<StaffInvitation>> =
        salon(salonId).collection("invitations").orderBy("createdAt", Query.Direction.DESCENDING).limit(50).snapshots().map { qs ->
            val now = Instant.now()
            qs.documents.map { d ->
                val expires = d.getTimestamp("expiresAt")?.toInstant()
                var status = InvitationStatus.entries.firstOrNull { it.name == d.getString("status") } ?: InvitationStatus.PENDING
                if (status == InvitationStatus.PENDING && expires != null && !expires.isAfter(now)) status = InvitationStatus.EXPIRED
                StaffInvitation(
                    id = d.id,
                    firstName = d.getString("firstName").orEmpty(),
                    lastName = d.getString("lastName").orEmpty(),
                    specialty = d.getString("specialty").orEmpty(),
                    phoneE164 = d.getString("phoneE164").orEmpty(),
                    status = status,
                    createdAt = d.getTimestamp("createdAt")?.toInstant(),
                    expiresAt = expires,
                )
            }
        }.catch { throw AppException.from(it) }

    suspend fun invite(salonId: String, firstName: String, lastName: String, specialty: String, phone: String, key: String) {
        functions.call(
            "createStaffInvitation",
            mapOf(
                "salonId" to salonId, "firstName" to firstName.trim(), "lastName" to lastName.trim(),
                "specialty" to specialty.trim(), "phone" to phone, "idempotencyKey" to key,
            ),
        )
    }

    suspend fun revoke(salonId: String, inviteId: String) {
        functions.call("revokeInvitation", mapOf("salonId" to salonId, "inviteId" to inviteId))
    }

    suspend fun accept(salonId: String, inviteId: String) {
        functions.call(
            "acceptInvitation",
            mapOf("salonId" to salonId, "inviteId" to inviteId, "consents" to LegalDocument.consentPayload()),
        )
    }

    suspend fun decline(salonId: String, inviteId: String) {
        functions.call("declineInvitation", mapOf("salonId" to salonId, "inviteId" to inviteId))
    }

    suspend fun updateMember(salonId: String, memberUid: String, firstName: String, lastName: String, specialty: String) {
        functions.call(
            "updateStaffMember",
            mapOf("salonId" to salonId, "memberUid" to memberUid, "firstName" to firstName.trim(), "lastName" to lastName.trim(), "specialty" to specialty.trim()),
        )
    }

    /** @return etkilenen gelecek randevu sayısı */
    suspend fun setStatus(salonId: String, memberUid: String, active: Boolean): Int {
        val r = functions.call(
            "setMemberStatus",
            mapOf("salonId" to salonId, "memberUid" to memberUid, "status" to if (active) "ACTIVE" else "INACTIVE"),
        )
        return (r["affectedFutureAppointments"] as? List<*>)?.size ?: 0
    }

    suspend fun leave(salonId: String): Int {
        val r = functions.call("leaveSalon", mapOf("salonId" to salonId))
        return (r["affectedFutureAppointments"] as? List<*>)?.size ?: 0
    }

    fun newKey(): String = UUID.randomUUID().toString()
}
