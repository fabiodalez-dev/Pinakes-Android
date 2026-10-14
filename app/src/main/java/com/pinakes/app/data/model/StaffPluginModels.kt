package com.pinakes.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

// Plugin content managed by staff (Mobile API 1.7.0, tag `staff-plugins`): Desiderata
// donation offers and the Archives plugin (units, authorities, attachments).

@Serializable
data class IdTitle(val id: Int = 0, val title: String = "")

// ---------- Desiderata ----------

@Serializable
data class StaffOffer(
    val id: Int = 0,
    /** `pending` | `accepted` | `received` | `rejected`. */
    val status: String = "pending",
    @SerialName("status_label") val statusLabel: String? = null,
    @SerialName("donor_name") val donorName: String? = null,
    @SerialName("donor_email") val donorEmail: String? = null,
    val title: String = "",
    val author: String? = null,
    val publisher: String? = null,
    val isbn: String? = null,
    val notes: String? = null,
    val book: IdTitle? = null,
    @SerialName("received_book") val receivedBook: IdTitle? = null,
    @SerialName("copy_id") val copyId: Int? = null,
    /** `web` | `mobile`. */
    val source: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("received_at") val receivedAt: String? = null,
)

@Serializable
data class ReceiveOfferRequest(@SerialName("received_book_id") val receivedBookId: Int? = null)

@Serializable
data class StaffWantedBook(
    val id: Int = 0,
    val title: String = "",
    val subtitle: String? = null,
    val author: String? = null,
    val publisher: String? = null,
    val isbn: String? = null,
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("open_offers") val openOffers: Int = 0,
)

@Serializable
data class WantedReceiveResult(
    @SerialName("book_id") val bookId: Int = 0,
    @SerialName("copy_id") val copyId: Int? = null,
    @SerialName("offer_id") val offerId: Int? = null,
)

// ---------- Archives ----------

@Serializable
data class StaffArchiveRow(
    val id: Int = 0,
    @SerialName("parent_id") val parentId: Int? = null,
    val title: String = "",
    @SerialName("reference_code") val referenceCode: String = "",
    val level: String = "",
    @SerialName("date_start") val dateStart: Int? = null,
    @SerialName("date_end") val dateEnd: Int? = null,
    @SerialName("cover_url") val coverUrl: String? = null,
    val published: Boolean = true,
) {
    val datesLabel: String? get() = dateStart?.let { start ->
        dateEnd?.takeUnless { it == start }?.let { "$start–$it" } ?: start.toString()
    }
}

@Serializable
data class ArchiveFile(val id: Int = 0, val url: String = "", val mime: String? = null, val label: String? = null)

@Serializable
data class ArchiveAuthorityRef(val id: Int = 0, val type: String? = null, val name: String = "", val role: String? = null)

/** `GET /staff/archives/{id}`: the public record shape plus the editable columns. */
@Serializable
data class StaffArchiveDetail(
    val id: Int = 0,
    @SerialName("parent_id") val parentId: Int? = null,
    val title: String = "",
    @SerialName("reference_code") val referenceCode: String = "",
    val level: String = "",
    @SerialName("cover_url") val coverUrl: String? = null,
    val published: Boolean = true,
    /** Every column of the row, raw (strings, numbers or null). */
    val fields: Map<String, JsonElement> = emptyMap(),
    val files: List<ArchiveFile> = emptyList(),
    val authorities: List<ArchiveAuthorityRef> = emptyList(),
) {
    /** A column as text, "" when absent or null. */
    fun field(key: String): String =
        (fields[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNull.orEmpty()
}

/** `GET /staff/archives/lookups`: each list is `[{value,label}]`. */
@Serializable
data class ArchiveLookups(
    val levels: List<JsonElement> = emptyList(),
    @SerialName("specific_materials") val specificMaterials: List<JsonElement> = emptyList(),
    @SerialName("color_modes") val colorModes: List<JsonElement> = emptyList(),
    @SerialName("authority_types") val authorityTypes: List<JsonElement> = emptyList(),
    @SerialName("authority_roles") val authorityRoles: List<JsonElement> = emptyList(),
) {
    val levelOptions: List<LookupOption> get() = StaffLookups.options(levels)
    val materialOptions: List<LookupOption> get() = StaffLookups.options(specificMaterials)
    val colorModeOptions: List<LookupOption> get() = StaffLookups.options(colorModes)
    val authorityTypeOptions: List<LookupOption> get() = StaffLookups.options(authorityTypes)
    val authorityRoleOptions: List<LookupOption> get() = StaffLookups.options(authorityRoles)
}

@Serializable
data class AuthorityRow(
    val id: Int = 0,
    val type: String? = null,
    val name: String = "",
    val dates: String? = null,
    @SerialName("units_count") val unitsCount: Int = 0,
)

@Serializable
data class AuthorityUnitRef(val id: Int = 0, val title: String = "", val role: String? = null)

/**
 * `GET /staff/archives/authorities/{id}`: the full row (ISAAR(CPF) columns) plus the linked
 * units and catalogue authors. Kept raw so a column the app does not know survives.
 */
@Serializable
data class AuthorityDetail(
    val id: Int = 0,
    val type: String? = null,
    @SerialName("authorised_form") val authorisedForm: String? = null,
    @SerialName("parallel_forms") val parallelForms: String? = null,
    @SerialName("other_forms") val otherForms: String? = null,
    val identifiers: String? = null,
    @SerialName("dates_of_existence") val datesOfExistence: String? = null,
    val history: String? = null,
    val places: String? = null,
    @SerialName("legal_status") val legalStatus: String? = null,
    val functions: String? = null,
    val mandates: String? = null,
    @SerialName("internal_structure") val internalStructure: String? = null,
    @SerialName("general_context") val generalContext: String? = null,
    val units: List<AuthorityUnitRef> = emptyList(),
    val authors: List<IdName> = emptyList(),
)

@Serializable
data class AttachAuthorityRequest(@SerialName("authority_id") val authorityId: Int, val role: String)
