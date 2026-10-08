package com.pinakes.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CollectionHealth(
    val status: String = "",
    @SerialName("native_offers") val nativeOffers: Boolean = false,
    @SerialName("web_url") val webUrl: String? = null,
    @SerialName("manage_url") val manageUrl: String? = null,
)

/** Library requests have no lending availability; they are distinct from personal wishes. */
@Serializable
data class WantedBook(
    val id: Int = 0,
    val title: String = "",
    val subtitle: String? = null,
    val author: String? = null,
    val publisher: String? = null,
    val isbn: String? = null,
    val year: Int? = null,
    val description: String? = null,
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("web_url") val webUrl: String? = null,
    val wanted: Boolean = true,
)

@Serializable
data class DonationOfferRequest(
    @SerialName("submission_id") val submissionId: String,
    @SerialName("book_id") val bookId: Int? = null,
    val title: String,
    val author: String = "",
    val publisher: String = "",
    val isbn: String = "",
    val notes: String = "",
    val consent: Boolean,
)

@Serializable
data class DonationReceipt(val id: Int = 0, val status: String = "")

@Serializable
data class ArchiveRecord(
    val id: Int = 0,
    @SerialName("parent_id") val parentId: Int? = null,
    val title: String = "",
    @SerialName("formal_title") val formalTitle: String? = null,
    @SerialName("reference_code") val referenceCode: String = "",
    val level: String = "",
    @SerialName("date_start") val dateStart: Int? = null,
    @SerialName("date_end") val dateEnd: Int? = null,
    val extent: String? = null,
    val material: String? = null,
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("web_url") val webUrl: String? = null,
    val fields: Map<String, String> = emptyMap(),
    val ancestors: List<ArchiveRecord> = emptyList(),
    val authorities: List<ArchiveAuthority> = emptyList(),
    val documents: List<CollectionDocument> = emptyList(),
    val exports: Map<String, String> = emptyMap(),
) {
    val datesLabel: String? get() = dateStart?.let { start ->
        dateEnd?.takeUnless { it == start }?.let { "$start–$it" } ?: start.toString()
    }
}

@Serializable
data class ArchiveAuthority(val id: Int = 0, val name: String = "", val type: String = "", val dates: String? = null, val role: String = "")

@Serializable
data class CollectionDocument(val url: String = "", val label: String = "", val mime: String = "")
