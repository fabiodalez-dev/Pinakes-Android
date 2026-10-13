package com.pinakes.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---------- Personal loans calendar (`/me/calendar`, Mobile API 1.6.0) ----------

@Serializable
data class CalendarStatus(
    val enabled: Boolean = false,
    @SerialName("feed_url") val feedUrl: String? = null,
    @SerialName("webcal_url") val webcalUrl: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("rotated_at") val rotatedAt: String? = null,
)
