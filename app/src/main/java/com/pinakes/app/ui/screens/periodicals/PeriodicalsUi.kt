package com.pinakes.app.ui.screens.periodicals

import androidx.annotation.StringRes
import com.pinakes.app.R
import com.pinakes.app.ui.components.AvailabilityStatus

/**
 * Pure helpers for the Periodicals screens: enum → localized label lookups and the
 * issue-status → badge mapping. Kept free of Compose so they are unit-testable
 * (see PeriodicalsUiStateTest).
 */

/** The masthead types the server may emit, in filter-chip order. */
val PERIODICAL_TYPES = listOf("rivista", "giornale", "magazine", "bollettino", "fanzine")

/** Localized label for a masthead type. Unknown values fall back to the generic "rivista". */
@StringRes
fun periodicalTypeLabelRes(type: String): Int = when (type) {
    "giornale" -> R.string.periodicals_type_giornale
    "magazine" -> R.string.periodicals_type_magazine
    "bollettino" -> R.string.periodicals_type_bollettino
    "fanzine" -> R.string.periodicals_type_fanzine
    else -> R.string.periodicals_type_rivista
}

/** Localized label for a publication frequency, or null for unknown/absent values. */
@StringRes
fun periodicalFrequencyLabelRes(frequency: String?): Int? = when (frequency) {
    "quotidiano" -> R.string.periodicals_freq_quotidiano
    "settimanale" -> R.string.periodicals_freq_settimanale
    "quindicinale" -> R.string.periodicals_freq_quindicinale
    "mensile" -> R.string.periodicals_freq_mensile
    "bimestrale" -> R.string.periodicals_freq_bimestrale
    "trimestrale" -> R.string.periodicals_freq_trimestrale
    "semestrale" -> R.string.periodicals_freq_semestrale
    "annuale" -> R.string.periodicals_freq_annuale
    "irregolare" -> R.string.periodicals_freq_irregolare
    else -> null
}

/** Localized label for an issue status. Unknown values read as "expected" (neutral). */
@StringRes
fun issueStatusLabelRes(status: String): Int = when (status) {
    "posseduto" -> R.string.periodicals_status_posseduto
    "mancante" -> R.string.periodicals_status_mancante
    "danneggiato" -> R.string.periodicals_status_danneggiato
    "in_restauro" -> R.string.periodicals_status_in_restauro
    "smarrito" -> R.string.periodicals_status_smarrito
    else -> R.string.periodicals_status_atteso
}

/**
 * Issue status → badge tone, reusing [AvailabilityStatus] so the chip colours stay
 * consistent with the rest of the app:
 * posseduto = ok (green) · mancante/smarrito = error (red) · danneggiato/in_restauro =
 * warning (amber) · atteso and anything unknown = neutral (grey).
 */
fun issueStatusBadge(status: String): AvailabilityStatus = when (status) {
    "posseduto" -> AvailabilityStatus.Available
    "mancante", "smarrito" -> AvailabilityStatus.Overdue
    "danneggiato", "in_restauro" -> AvailabilityStatus.DueSoon
    else -> AvailabilityStatus.Returned
}
