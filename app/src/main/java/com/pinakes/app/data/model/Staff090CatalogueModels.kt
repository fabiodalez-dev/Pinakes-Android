package com.pinakes.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.booleanOrNull

// Pinakes 0.9.0 cataloguing areas (Mobile API 1.10.0): suppliers, catalogue cards, MARC import
// and export, copy cataloguing. Every field has a default, so a server that omits one still
// decodes.

/**
 * The `{data, meta, error}` envelope with the meta kept as raw JSON: the cataloguing routes
 * answer keys the shared [Meta] does not model (`meta.fields` of a 422, `meta.can_create` of
 * the supplier list).
 */
@Serializable
data class Envelope090<T>(
    val data: T? = null,
    val meta: JsonElement? = null,
    val error: ApiError? = null,
)

/** `{value, label}` of a server choice list (flavours, encodings, formats, indexes). */
@Serializable
data class ValueLabel(val value: String = "", val label: String = "")

/** A duplicate mode (`skip` | `fill` | `overwrite`) with the web form's explanation. */
@Serializable
data class DuplicateMode(val value: String = "", val label: String = "", val description: String = "")

// ---------- Suppliers ----------

@Serializable
data class Supplier(
    val id: Int = 0,
    val name: String = "",
    @SerialName("contact_person") val contactPerson: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val website: String? = null,
    val address: String? = null,
    @SerialName("vat_number") val vatNumber: String? = null,
    val notes: String? = null,
    @SerialName("book_count") val bookCount: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("can_edit") val canEdit: Boolean = false,
    @SerialName("can_delete") val canDelete: Boolean = false,
)

/** `GET /staff/suppliers` with its `meta.can_create`. */
data class SupplierList(val items: List<Supplier>, val canCreate: Boolean)

@Serializable
data class SupplierDeleteResult(
    val id: Int = 0,
    val deleted: Boolean = false,
    @SerialName("books_unlinked") val booksUnlinked: Int = 0,
)

// ---------- Books: supplier and RDA fields (read-only parts) ----------

/** `rda_effective` of a staff book: the override, or the triple derived from the media type. */
@Serializable
data class RdaEffective(
    @SerialName("content_type") val contentType: String? = null,
    @SerialName("media_type") val mediaType: String? = null,
    @SerialName("carrier_type") val carrierType: String? = null,
)

// ---------- MARC ----------

@Serializable
data class CopyCatalogingAvailability(val available: Boolean = false, val reason: String? = null)

@Serializable
data class MarcOptions(
    val flavours: List<ValueLabel> = emptyList(),
    val encodings: List<ValueLabel> = emptyList(),
    @SerialName("duplicate_modes") val duplicateModes: List<DuplicateMode> = emptyList(),
    @SerialName("export_formats") val exportFormats: List<ValueLabel> = emptyList(),
    val extensions: List<String> = emptyList(),
    @SerialName("max_file_bytes") val maxFileBytes: Long = 20L * 1024 * 1024,
    @SerialName("max_records") val maxRecords: Int = 20000,
    @SerialName("max_export_ids") val maxExportIds: Int = 1000,
    /** Null while the suppliers module is off: no supplier choice. */
    val suppliers: List<IdName>? = null,
    @SerialName("copy_cataloging") val copyCataloging: CopyCatalogingAvailability = CopyCatalogingAvailability(),
)

@Serializable
data class MarcPreviewRow(
    val position: Int = 0,
    val flavour: String = "",
    val title: String = "",
    val subtitle: String? = null,
    val authors: String? = null,
    val publisher: String? = null,
    val year: String? = null,
    val isbn: String? = null,
    @SerialName("has_cover") val hasCover: Boolean = false,
    /** The book already in the catalogue under the duplicate rule. */
    @SerialName("existing_id") val existingId: Int? = null,
    /** Set for a record that could not be read. */
    val error: String? = null,
)

/**
 * A MARC import job. The first keys are those of the CSV [ImportJob] with the same meaning, so
 * [asImportJob] hands it to [com.pinakes.app.data.staff.ImportProgress] unchanged.
 */
@Serializable
data class MarcImportJob(
    val id: String = "",
    val kind: String = "marc",
    @SerialName("file_name") val fileName: String = "",
    val status: String = "ready",
    @SerialName("total_rows") val totalRows: Int = 0,
    val processed: Int = 0,
    @SerialName("chunk_size") val chunkSize: Int = 10,
    @SerialName("enable_scraping") val enableScraping: Boolean = false,
    val imported: Int = 0,
    val updated: Int = 0,
    val scraped: Int = 0,
    @SerialName("authors_created") val authorsCreated: Int = 0,
    @SerialName("publishers_created") val publishersCreated: Int = 0,
    val errors: Int = 0,
    @SerialName("error_details") val errorDetails: List<JsonElement> = emptyList(),
    val complete: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    // MARC only
    val skipped: Int = 0,
    val covers: Int = 0,
    val failed: Int = 0,
    @SerialName("import_id") val importId: String? = null,
    val duplicates: String = "skip",
    @SerialName("download_covers") val downloadCovers: Boolean = false,
    @SerialName("supplier_id") val supplierId: Int? = null,
    val flavour: String = "auto",
    val encoding: String = "auto",
    /** `iso2709` | `xml`. */
    val container: String = "",
    val unreadable: Int = 0,
    /** Message when the file holds more records than the server imports. */
    val truncated: String? = null,
    val preview: List<MarcPreviewRow> = emptyList(),
) {
    fun asImportJob(): ImportJob = ImportJob(
        id = id, kind = kind, fileName = fileName, status = status, totalRows = totalRows, processed = processed,
        chunkSize = chunkSize, enableScraping = enableScraping, imported = imported, updated = updated, scraped = scraped,
        authorsCreated = authorsCreated, publishersCreated = publishersCreated, errors = errors, errorDetails = errorDetails,
        complete = complete, createdAt = createdAt, updatedAt = updatedAt, expiresAt = expiresAt,
    )

    /** "#position (title): message" lines, as the server writes them. */
    val errorLines: List<String> get() = asImportJob().errorLines
}

@Serializable
data class MarcCancelResult(val id: String = "", val cancelled: Boolean = false)

// ---------- Copy cataloguing ----------

@Serializable
data class CopyCatalogingServer(
    /** `cfg:N`, `sbn`, `preset:loc|bnf|dnb|k10plus`, `local`. */
    val key: String = "",
    val name: String = "",
    /** `configured` | `preset` | `local`. */
    val kind: String = "",
    val url: String = "",
    val syntax: String = "",
)

@Serializable
data class CopyCatalogingServers(
    val servers: List<CopyCatalogingServer> = emptyList(),
    val indexes: List<ValueLabel> = emptyList(),
    @SerialName("duplicate_modes") val duplicateModes: List<DuplicateMode> = emptyList(),
    @SerialName("max_results_per_server") val maxResultsPerServer: Int = 25,
    @SerialName("max_import_ids") val maxImportIds: Int = 100,
)

@Serializable
data class MarcField(val tag: String = "", val ind: String = "", val value: String = "")

@Serializable
data class CopyCatalogingResult(
    /** 16 hex characters, passed back to the import. */
    val id: String = "",
    /** The catalogue's display name. */
    val server: String = "",
    val flavour: String = "",
    val title: String = "",
    val subtitle: String? = null,
    val authors: String? = null,
    val publisher: String? = null,
    val year: String? = null,
    val isbn: String? = null,
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("existing_id") val existingId: Int? = null,
    val detail: List<MarcField> = emptyList(),
)

@Serializable
data class CopyCatalogingServerReport(
    val key: String = "",
    val name: String = "",
    val total: Int = 0,
    val count: Int = 0,
    val error: String? = null,
)

@Serializable
data class CopyCatalogingSearch(
    @SerialName("search_id") val searchId: String = "",
    @SerialName("expires_at") val expiresAt: String? = null,
    val results: List<CopyCatalogingResult> = emptyList(),
    val servers: List<CopyCatalogingServerReport> = emptyList(),
)

@Serializable
data class CopyCatalogingItem(
    val id: String = "",
    /** `created` | `updated` | `skipped` | `failed`. */
    val action: String = "failed",
    @SerialName("book_id") val bookId: Int? = null,
    val title: String = "",
    val warnings: List<String> = emptyList(),
    val message: String? = null,
)

@Serializable
data class CopyCatalogingImport(
    val imported: Int = 0,
    val updated: Int = 0,
    val skipped: Int = 0,
    val failed: Int = 0,
    val covers: Int = 0,
    @SerialName("import_id") val importId: String? = null,
    val items: List<CopyCatalogingItem> = emptyList(),
)

@Serializable
data class CopyCatalogingSearchRequest(
    val servers: List<String>,
    val index: String,
    val term: String,
    val max: Int,
)

@Serializable
data class CopyCatalogingImportRequest(
    @SerialName("search_id") val searchId: String,
    val ids: List<String>,
    val duplicates: String,
    val covers: Boolean,
)

/** `meta.fields` of a 422 `validation`: field → message (empty when absent). */
fun JsonElement?.fieldErrors(): Map<String, String> {
    val fields = (this as? JsonObject)?.get("fields") as? JsonObject ?: return emptyMap()
    return fields.mapNotNull { (key, value) -> (value as? JsonPrimitive)?.contentOrNull?.let { key to it } }.toMap()
}

/** A boolean of the raw meta, or null. */
fun JsonElement?.metaBoolean(key: String): Boolean? =
    ((this as? JsonObject)?.get(key) as? JsonPrimitive)?.booleanOrNull
