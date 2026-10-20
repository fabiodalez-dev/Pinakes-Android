package com.pinakes.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---------- Delta catalogue sync (`GET /catalog/sync`, Mobile API 1.6.0) ----------

/** A catalogue row as the sync endpoint sends it: the summary plus offline-detail fields. */
@Serializable
data class SyncBook(
    val id: Int = 0,
    val title: String = "",
    val subtitle: String? = null,
    val author: String? = null,
    val publisher: String? = null,
    val genre: String? = null,
    val year: Int? = null,
    val language: String? = null,
    @SerialName("media_type") val mediaType: String? = null,
    val isbn13: String? = null,
    val isbn10: String? = null,
    val ean: String? = null,
    val pages: Int? = null,
    val description: String? = null,
    @SerialName("location_label") val locationLabel: String? = null,
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("copies_total") val copiesTotal: Int = 0,
    @SerialName("copies_available") val copiesAvailable: Int = 0,
    @SerialName("loanable_now") val loanableNow: Boolean = false,
    @SerialName("is_wanted") val isWanted: Boolean = false,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class SyncItem(
    val id: Int = 0,
    val change: String = "upsert", // upsert | delete
    @SerialName("changed_at") val changedAt: String? = null,
    val book: SyncBook? = null,
)

@Serializable
data class SyncPage(
    val items: List<SyncItem> = emptyList(),
    @SerialName("server_time") val serverTime: String? = null,
    @SerialName("full_resync") val fullResync: Boolean = false,
)
