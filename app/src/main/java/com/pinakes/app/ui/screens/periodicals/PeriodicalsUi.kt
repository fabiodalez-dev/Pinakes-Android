package com.pinakes.app.ui.screens.periodicals

import androidx.annotation.StringRes
import com.pinakes.app.R
import com.pinakes.app.data.model.Meta
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.network.ErrorCodes
import com.pinakes.app.ui.common.UiState
import com.pinakes.app.ui.components.AvailabilityStatus

/**
 * Pure helpers for the Periodicals screens: enum → localized label lookups, the
 * issue-status → badge mapping, the truncated-list decision and the failure classification
 * the detail screens share. Kept free of Compose so they are unit-testable
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

/**
 * True when the server says it cut the list short (`meta.truncated`), which is the only
 * signal for the 400-issue cap on a year's fascicoli.
 *
 * Servers that predate the field omit it, so `null` MUST read as "complete": inferring
 * truncation from the item count instead would cry wolf on any year that happens to sit
 * exactly on the cap, and would be flatly wrong the day the cap changes.
 */
internal fun isTruncatedList(meta: Meta?): Boolean = meta?.truncated == true

/** How a Periodicals detail failure should be presented. */
internal enum class PeriodicalsFailure {
    /** The plugin itself is off: a terminal, non-retryable state. */
    Gone,

    /** The section is alive, this one masthead/year/issue is not. */
    NotFound,

    /** Anything else — network, 5xx, auth — worth a retry. */
    Error,
}

/** True for a 404, the only failure worth spending a health probe on. */
internal fun isNotFoundFailure(failure: ApiResult.Failure): Boolean =
    failure.httpStatus == 404 || failure.code == ErrorCodes.NOT_FOUND

/**
 * Classify a detail failure. A 404 is ambiguous — the single resource may have been deleted,
 * or the whole plugin may have been switched off server-side — and the two deserve opposite
 * treatments: "this issue no longer exists" (the section still works, go back and browse) vs
 * "the periodicals section is gone" (nothing here will ever load again).
 *
 * The health endpoint is the oracle, exactly as it is for the availability probe:
 * [goneConfirmed] is `PeriodicalsRepository.confirmGone()`, which re-probes health and only
 * answers true when health 404s too. Callers must evaluate it ONLY for a 404 — probing on
 * every timeout would turn a network blip into an extra doomed request.
 */
internal fun periodicalsFailureKind(
    failure: ApiResult.Failure,
    goneConfirmed: Boolean,
): PeriodicalsFailure = when {
    !isNotFoundFailure(failure) -> PeriodicalsFailure.Error
    goneConfirmed -> PeriodicalsFailure.Gone
    else -> PeriodicalsFailure.NotFound
}

/**
 * Build the [UiState.Error] for a classified detail failure.
 *
 * Gone and NotFound deliberately drop the server's message: `resolvedMessage()` prefers a
 * non-blank message over the localized fallback, so keeping the bare "Not found." would show
 * that instead of wording that tells the user which of the two situations they are in.
 */
internal fun periodicalsErrorState(
    failure: ApiResult.Failure,
    kind: PeriodicalsFailure,
    @StringRes genericRes: Int,
    @StringRes notFoundRes: Int,
): UiState.Error = when (kind) {
    PeriodicalsFailure.Gone ->
        UiState.Error("", failure.code, R.string.periodicals_gone_subtitle)
    PeriodicalsFailure.NotFound ->
        UiState.Error("", failure.code, notFoundRes)
    PeriodicalsFailure.Error ->
        UiState.Error(failure.message, failure.code, genericRes)
}
