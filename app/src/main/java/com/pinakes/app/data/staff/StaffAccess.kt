package com.pinakes.app.data.staff

/**
 * Who sees the staff area. Mirrors the server's `AdminAuthMiddleware::ALLOWED_ROLES`
 * (`admin`, `staff`): the server re-checks the role on every `/staff/...` call, so this only
 * decides whether the entry points are shown.
 */
object StaffAccess {
    private val STAFF_ROLES = setOf("admin", "staff")

    fun isStaff(role: String?): Boolean = role?.trim()?.lowercase() in STAFF_ROLES

    /** The staff area needs both a staff role and a server that advertises `staff_catalog`. */
    fun canManageCatalog(role: String?, serverSupportsStaff: Boolean): Boolean =
        serverSupportsStaff && isStaff(role)
}
