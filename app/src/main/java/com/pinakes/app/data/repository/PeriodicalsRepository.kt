package com.pinakes.app.data.repository

import com.pinakes.app.data.model.PeriodicalDetail
import com.pinakes.app.data.model.PeriodicalIssue
import com.pinakes.app.data.model.PeriodicalIssueDetail
import com.pinakes.app.data.model.PeriodicalSummary
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.network.ErrorCodes
import com.pinakes.app.data.network.NetworkModule
import com.pinakes.app.data.network.apiCall
import com.pinakes.app.data.store.FeatureStore
import com.pinakes.app.data.store.SessionStore

/** One cursor page of the mastheads list. */
data class PeriodicalsPage(
    val items: List<PeriodicalSummary> = emptyList(),
    val nextCursor: String? = null,
)

/**
 * Periodicals ("Emeroteca") plugin surface (`/api/v1/periodicals/…`): availability discovery
 * plus the read-only browse chain — mastheads list, masthead detail (with years), a year's
 * issues and the issue detail.
 *
 * Availability is probed alongside every `/health` refresh and stored as an
 * [com.pinakes.app.data.store.InstanceFeatures] flag, so the UI only shows the section when
 * the plugin is active for this instance — the same lifecycle as [BookClubRepository].
 */
class PeriodicalsRepository(
    private val network: NetworkModule,
    private val features: FeatureStore,
    private val session: SessionStore,
) {

    /**
     * Probe `GET /periodicals/health`.
     * Returns true on 2xx (plugin on), false on an explicit 404 (plugin off), and null on
     * any other failure — the caller keeps the last-known flag rather than hiding a working
     * section on a network blip.
     */
    suspend fun probeAvailability(): Boolean? =
        when (val res = apiCall { network.periodicalsApi().health() }) {
            is ApiResult.Success -> true
            is ApiResult.Failure ->
                if (res.httpStatus == 404 || res.code == ErrorCodes.NOT_FOUND) false else null
        }

    /**
     * Apply a probe result, guarded against instance switches: a late response from the
     * previous instance (the user tapped "change library" mid-flight) must not resurrect
     * or clobber the flag of the instance now configured. Null keeps the last-known value.
     */
    fun applyAvailability(available: Boolean?, probedInstanceUrl: String?): Boolean {
        if (available == null) return false
        if (probedInstanceUrl == null || session.instanceUrl != probedInstanceUrl) return false
        features.setPeriodicalsAvailable(available)
        return true
    }

    /** One page of mastheads, optionally filtered by free text and/or type. */
    suspend fun periodicals(
        query: String? = null,
        type: String? = null,
        cursor: String? = null,
        limit: Int? = null,
    ): ApiResult<PeriodicalsPage> =
        when (val res = apiCall { network.periodicalsApi().periodicals(query, type, cursor, limit) }) {
            is ApiResult.Success ->
                ApiResult.Success(PeriodicalsPage(res.data, res.meta?.nextCursor), res.meta)
            is ApiResult.Failure -> res
        }

    suspend fun periodical(id: Int): ApiResult<PeriodicalDetail> =
        apiCall { network.periodicalsApi().periodical(id) }

    suspend fun yearIssues(yearId: Int): ApiResult<List<PeriodicalIssue>> =
        apiCall { network.periodicalsApi().yearIssues(yearId) }

    suspend fun issue(id: Int): ApiResult<PeriodicalIssueDetail> =
        apiCall { network.periodicalsApi().issue(id) }

    /**
     * A periodicals endpoint answered 404: re-probe the plugin health and, when the plugin
     * is confirmed gone, flip the feature flag so every entry point hides immediately.
     * Returns true when the plugin is really unavailable (vs a single missing resource).
     */
    suspend fun confirmGone(): Boolean {
        val instance = session.instanceUrl
        val available = probeAvailability()
        // Only treat the plugin as gone when the probe actually applied to the still-current
        // instance — a stale 404 from a since-switched instance must not drive pluginGone.
        val applied = applyAvailability(available, instance)
        return applied && available == false
    }
}
