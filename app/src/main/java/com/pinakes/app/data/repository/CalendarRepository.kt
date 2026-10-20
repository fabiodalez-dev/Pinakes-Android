package com.pinakes.app.data.repository

import com.pinakes.app.data.model.CalendarStatus
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.network.NetworkModule
import com.pinakes.app.data.network.apiCall

/**
 * The personal loans calendar feed (`/me/calendar`): status, create/rotate (the old URL stops
 * working immediately) and revoke. The feed itself is fetched by the user's calendar app.
 */
class CalendarRepository(private val network: NetworkModule) {

    suspend fun status(): ApiResult<CalendarStatus> = apiCall { network.api().calendarStatus() }

    suspend fun rotate(): ApiResult<CalendarStatus> = apiCall { network.api().rotateCalendarToken() }

    /** Disables the feed. The server answers `{enabled:false}`; an empty body means the same. */
    suspend fun revoke(): ApiResult<CalendarStatus> =
        when (val res = apiCall { network.api().revokeCalendarToken() }) {
            is ApiResult.Success -> {
                // apiCall maps an empty `data` to Unit at runtime: never trust the static type here.
                val body: Any? = res.data
                ApiResult.Success((body as? CalendarStatus) ?: CalendarStatus(enabled = false), res.meta)
            }
            is ApiResult.Failure -> res
        }
}
