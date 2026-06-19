package com.pinakes.app.data.repository

import com.pinakes.app.data.model.NotificationItem
import com.pinakes.app.data.model.PushPrefs
import com.pinakes.app.data.model.PushSubscribeRequest
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.network.NetworkModule
import com.pinakes.app.data.network.apiCall

/**
 * Notification feed + push subscription management. Push subscribe/unsubscribe and prefs are
 * always accepted by the server; the prefs PUT is a partial update (send only changed keys).
 */
class NotificationsRepository(private val network: NetworkModule) {

    suspend fun notifications(): ApiResult<List<NotificationItem>> {
        val api = network.api()
        return apiCall { api.notifications() }
    }

    suspend fun subscribePush(request: PushSubscribeRequest): ApiResult<Unit> {
        val api = network.api()
        return apiCall { api.pushSubscribe(request) }
    }

    suspend fun unsubscribePush(): ApiResult<Unit> {
        val api = network.api()
        return apiCall { api.pushUnsubscribe() }
    }

    suspend fun pushPrefs(): ApiResult<PushPrefs> {
        val api = network.api()
        return apiCall { api.pushPrefs() }
    }

    /** Partial update — only non-null fields of [prefs] are sent (explicitNulls = false). */
    suspend fun updatePushPrefs(prefs: PushPrefs): ApiResult<Unit> {
        val api = network.api()
        return apiCall { api.setPushPrefs(prefs) }
    }
}
