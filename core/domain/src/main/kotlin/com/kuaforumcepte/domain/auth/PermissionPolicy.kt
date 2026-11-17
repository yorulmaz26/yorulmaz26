package com.kuaforumcepte.domain.auth

import com.kuaforumcepte.domain.model.MemberRole

/**
 * İşletme tarafı yetki matrisi (docs/ARCHITECTURE.md §2).
 * İstemcide menü/buton görünürlüğü için; bağlayıcı denetim sunucu ve Firestore kurallarındadır.
 */
enum class Permission {
    VIEW_OWN_APPOINTMENTS,
    VIEW_ALL_SALON_APPOINTMENTS,
    ACT_ON_OWN_APPOINTMENTS,
    ACT_ON_ALL_SALON_APPOINTMENTS,
    REASSIGN_APPOINTMENT,
    MANAGE_OWN_SCHEDULE,
    MANAGE_OWN_SERVICES,
    VIEW_OWN_REPORTS,
    VIEW_SALON_REPORTS,
    VIEW_STAFF_REPORTS,
    MANAGE_SALON_SETTINGS,
    MANAGE_SALON_MEDIA,
    MANAGE_SALON_CLOSURES,
    MANAGE_STAFF,
    VIEW_SALON_CODE,
    SHARE_BOOKING_LINK,
}

object PermissionPolicy {
    private val staff: Set<Permission> = setOf(
        Permission.VIEW_OWN_APPOINTMENTS,
        Permission.ACT_ON_OWN_APPOINTMENTS,
        Permission.MANAGE_OWN_SCHEDULE,
        Permission.MANAGE_OWN_SERVICES,
        Permission.VIEW_OWN_REPORTS,
        Permission.VIEW_SALON_CODE,
    )
    private val owner: Set<Permission> = Permission.entries.toSet()

    fun permissionsOf(role: MemberRole): Set<Permission> = when (role) {
        MemberRole.OWNER -> owner
        MemberRole.STAFF -> staff
    }

    fun can(role: MemberRole, permission: Permission): Boolean = permission in permissionsOf(role)
}
