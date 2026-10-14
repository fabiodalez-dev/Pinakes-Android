package com.pinakes.app.data.staff

import com.pinakes.app.data.model.AuthorityDetail
import com.pinakes.app.data.model.StaffArchiveDetail
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** Fields an archive form can flag (client checks, or `meta.field` of a 422). */
enum class ArchiveField(val wire: String) {
    REFERENCE_CODE("reference_code"), LEVEL("level"), CONSTRUCTED_TITLE("constructed_title"),
    DATE_START("date_start"), DATE_END("date_end"), PARENT_ID("parent_id");

    companion object {
        fun fromWire(value: String?): ArchiveField? = entries.firstOrNull { it.wire == value }
    }
}

/**
 * An archival unit being edited: the 23 payload keys of the Archives plugin
 * (`extractArchivalUnitPayload`), as text, plus the publish flag. [toJson] sends every key so a
 * cleared field empties the column; numbers go as numbers or JSON null. Pure.
 */
data class ArchiveForm(
    val values: Map<String, String> = emptyMap(),
    val published: Boolean = true,
) {
    operator fun get(key: String): String = values[key].orEmpty()

    fun with(key: String, value: String): ArchiveForm = copy(values = values + (key to value))

    /** Client checks that mirror the plugin's validation (required fields, numeric years, order). */
    fun errors(): Set<ArchiveField> {
        val out = mutableSetOf<ArchiveField>()
        if (this["reference_code"].isBlank()) out += ArchiveField.REFERENCE_CODE
        if (this["level"].isBlank()) out += ArchiveField.LEVEL
        if (this["constructed_title"].isBlank()) out += ArchiveField.CONSTRUCTED_TITLE
        val start = this["date_start"].trim()
        val end = this["date_end"].trim()
        val startN = start.toIntOrNull()
        val endN = end.toIntOrNull()
        if (start.isNotEmpty() && (startN == null || startN !in -9999..9999)) out += ArchiveField.DATE_START
        if (end.isNotEmpty() && (endN == null || endN !in -9999..9999)) out += ArchiveField.DATE_END
        if (startN != null && endN != null && endN < startN) out += ArchiveField.DATE_END
        if (this["parent_id"].isNotBlank() && this["parent_id"].trim().toIntOrNull()?.let { it > 0 } != true) out += ArchiveField.PARENT_ID
        return out
    }

    fun toJson(): JsonObject = buildJsonObject {
        for (key in TEXT_KEYS) put(key, JsonPrimitive(this@ArchiveForm[key].trim()))
        for (key in INT_KEYS) put(key, this@ArchiveForm[key].trim().toIntOrNull()?.let { JsonPrimitive(it) } ?: JsonNull)
        put("published", JsonPrimitive(published))
    }

    companion object {
        val INT_KEYS = listOf("date_start", "date_end", "parent_id")
        val TEXT_KEYS = listOf(
            "reference_code", "institution_code", "level", "formal_title", "constructed_title", "extent", "scope_content",
            "language_codes", "specific_material", "dimensions", "color_mode", "photographer", "publisher", "collection_name",
            "local_classification", "iiif_manifest_url", "rights_statement_url", "ark_identifier", "version_note",
        )

        /** A new unit with the plugin's defaults. */
        fun new(parentId: Int? = null) = ArchiveForm(
            values = mapOf("institution_code" to "PINAKES", "language_codes" to "ita", "specific_material" to "text", "level" to "fonds") +
                (parentId?.takeIf { it > 0 }?.let { mapOf("parent_id" to it.toString()) } ?: emptyMap()),
            published = true,
        )

        fun from(detail: StaffArchiveDetail): ArchiveForm = ArchiveForm(
            values = (TEXT_KEYS + INT_KEYS).associateWith { detail.field(it) }
                .let { v -> if (v["reference_code"].isNullOrBlank()) v + ("reference_code" to detail.referenceCode) else v }
                .let { v -> if (v["level"].isNullOrBlank()) v + ("level" to detail.level) else v },
            published = detail.published,
        )
    }
}

/** An ISAAR(CPF) authority being edited (`extractAuthorityPayload` keys). Pure. */
data class AuthorityForm(val values: Map<String, String> = mapOf("type" to "person")) {
    operator fun get(key: String): String = values[key].orEmpty()
    fun with(key: String, value: String) = copy(values = values + (key to value))
    val valid: Boolean get() = this["type"].isNotBlank() && this["authorised_form"].isNotBlank()

    fun toJson(): JsonObject = buildJsonObject { KEYS.forEach { put(it, JsonPrimitive(this@AuthorityForm[it].trim())) } }

    companion object {
        val KEYS = listOf(
            "type", "authorised_form", "parallel_forms", "other_forms", "identifiers", "dates_of_existence", "history",
            "places", "legal_status", "functions", "mandates", "internal_structure", "general_context",
        )

        fun from(d: AuthorityDetail) = AuthorityForm(
            mapOf(
                "type" to d.type.orEmpty(), "authorised_form" to d.authorisedForm.orEmpty(), "parallel_forms" to d.parallelForms.orEmpty(),
                "other_forms" to d.otherForms.orEmpty(), "identifiers" to d.identifiers.orEmpty(), "dates_of_existence" to d.datesOfExistence.orEmpty(),
                "history" to d.history.orEmpty(), "places" to d.places.orEmpty(), "legal_status" to d.legalStatus.orEmpty(),
                "functions" to d.functions.orEmpty(), "mandates" to d.mandates.orEmpty(), "internal_structure" to d.internalStructure.orEmpty(),
                "general_context" to d.generalContext.orEmpty(),
            ),
        )
    }
}
