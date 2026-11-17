package com.kuaforumcepte.app.data.session

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import com.kuaforumcepte.app.core.data.AppException
import com.kuaforumcepte.app.core.data.FunctionsClient
import com.kuaforumcepte.app.core.data.list
import com.kuaforumcepte.app.core.data.map
import com.kuaforumcepte.app.core.data.str
import com.kuaforumcepte.app.core.datastore.AppPreferences
import com.kuaforumcepte.app.data.auth.PhoneAuthRepository
import com.kuaforumcepte.domain.model.MemberRole
import com.kuaforumcepte.domain.model.MemberStatus
import dagger.Lazy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** İşletme tarafında seçili salon bağlamı. */
data class ActiveSalon(val membership: Membership, val allActive: List<Membership>)

@Singleton
class SessionRepository @Inject constructor(
    private val auth: Lazy<FirebaseAuth>,
    private val firestore: Lazy<FirebaseFirestore>,
    private val functions: FunctionsClient,
    private val preferences: AppPreferences,
    private val phoneAuth: PhoneAuthRepository,
) {
    private val _snapshot = MutableStateFlow<SessionSnapshot?>(null)
    /** Son bootstrap sonucu (profil adı, bekleyen davetler). */
    val snapshot: StateFlow<SessionSnapshot?> = _snapshot.asStateFlow()

    val currentUid: String? get() = auth.get().currentUser?.uid
    val currentPhone: String? get() = auth.get().currentUser?.phoneNumber

    val uidFlow: Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) }
        auth.get().addAuthStateListener(listener)
        awaitClose { auth.get().removeAuthStateListener(listener) }
    }

    suspend fun bootstrap(): SessionSnapshot {
        if (currentUid == null) throw AppException(AppException.NOT_SIGNED_IN)
        val r = functions.call("bootstrapSession")
        val user = r.map("user")
        val snap = SessionSnapshot(
            profileExists = user["exists"] == true,
            firstName = user.str("firstName"),
            lastName = user.str("lastName"),
            phoneE164 = user.str("phoneE164"),
            memberships = r.list("memberships").mapNotNull(::parseMembership),
            pendingInvitations = r.list("pendingInvitations").map {
                PendingInvitation(
                    salonId = it.str("salonId").orEmpty(),
                    inviteId = it.str("inviteId").orEmpty(),
                    salonName = it.str("salonName").orEmpty(),
                    firstName = it.str("firstName").orEmpty(),
                    lastName = it.str("lastName").orEmpty(),
                    specialty = it.str("specialty").orEmpty(),
                    expiresAtIso = it.str("expiresAt").orEmpty(),
                )
            },
            activeAppointmentId = r.str("activeAppointmentId"),
        )
        _snapshot.value = snap
        return snap
    }

    /** users/{uid}/memberships aynası — rol/üyelik değişiklikleri anında yansır. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val memberships: Flow<List<Membership>> = uidFlow.flatMapLatest { uid ->
        if (uid == null) flowOf(emptyList())
        else firestore.get().collection("users").document(uid).collection("memberships").snapshots()
            .map { qs -> qs.documents.mapNotNull { d -> parseMembership(d.data.orEmpty() + ("salonId" to d.id)) } }
            .catch { emit(emptyList()) }
    }

    /** Aktif salon: tercih edilen salon aktifse o, değilse tek aktif üyelik. */
    val activeSalon: Flow<ActiveSalon?> = combine(memberships, preferences.settings) { list, settings ->
        val active = list.filter { it.isActive }.sortedBy { it.salonName }
        val chosen = active.firstOrNull { it.salonId == settings.activeBusinessSalonId }
            ?: active.singleOrNull()
        chosen?.let { ActiveSalon(it, active) }
    }

    suspend fun selectActiveSalon(salonId: String) = preferences.setActiveBusinessSalon(salonId)

    suspend fun signOut() {
        auth.get().signOut()
        phoneAuth.reset()
        preferences.clearSessionScoped()
        _snapshot.value = null
    }

    private fun parseMembership(m: Map<String, Any?>): Membership? {
        val role = MemberRole.entries.firstOrNull { it.name == m["role"] } ?: return null
        val status = MemberStatus.entries.firstOrNull { it.name == m["status"] } ?: return null
        return Membership(
            salonId = m.str("salonId") ?: return null,
            salonName = m.str("salonName").orEmpty(),
            salonCode = m.str("salonCode").orEmpty(),
            role = role,
            status = status,
        )
    }
}
