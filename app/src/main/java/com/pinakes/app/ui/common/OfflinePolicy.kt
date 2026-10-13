package com.pinakes.app.ui.common

/**
 * What the app may offer while it is showing cached data (server unreachable). Availability
 * cannot be guaranteed offline, so loan and reservation affordances are hidden outright, never
 * shown disabled, and cached availability is never presented as current. Pure, unit-tested.
 */
object OfflinePolicy {

    /**
     * Whether "Request loan" / "Reserve", the loan calendar and loan or reservation cancel
     * actions may be shown: only online, and only when the instance offers loans or
     * reservations ([canBorrow], false in catalogue mode).
     */
    fun showLoanActions(canBorrow: Boolean, offline: Boolean): Boolean = canBorrow && !offline

    /** Whether an availability pill may claim the current state (never for cached rows). */
    fun showCurrentAvailability(offline: Boolean): Boolean = !offline
}
