package com.pinakes.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// Staff content (Mobile API 1.7.0, tag `staff-content`): per-copy management, series,
// duplicate merges, labels, CSV/LibraryThing imports, covers and bulk enrichment.

@Serializable
data class CopyUpdate(val status: String? = null, val notes: String? = null)

// ---------- Series ----------

@Serializable
data class StaffSeries(
    val id: Int = 0,
    val name: String = "",
    /** `serie` | `universo` | `ciclo` | `stagione` | `spin_off` | `arco` | `collezione_editoriale` | `altro`. */
    val type: String? = null,
    @SerialName("parent_id") val parentId: Int? = null,
    @SerialName("parent_name") val parentName: String? = null,
    val group: String? = null,
    val cycle: String? = null,
    @SerialName("cycle_order") val cycleOrder: Int? = null,
    val complete: Boolean = false,
    @SerialName("book_count") val bookCount: Int = 0,
    @SerialName("min_number") val minNumber: String? = null,
    @SerialName("max_number") val maxNumber: String? = null,
    val children: List<StaffSeries> = emptyList(),
)

@Serializable
data class SeriesBook(
    val id: Int = 0,
    val title: String = "",
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("series_number") val seriesNumber: String? = null,
    @SerialName("is_primary") val isPrimary: Boolean = false,
    val year: String? = null,
)

@Serializable
data class SeriesRelated(
    val id: Int = 0,
    val name: String = "",
    val type: String? = null,
    val relation: String? = null,
)

@Serializable
data class StaffSeriesDetail(
    val id: Int = 0,
    val name: String = "",
    val type: String? = null,
    @SerialName("parent_id") val parentId: Int? = null,
    @SerialName("parent_name") val parentName: String? = null,
    val group: String? = null,
    val cycle: String? = null,
    @SerialName("cycle_order") val cycleOrder: Int? = null,
    val complete: Boolean = false,
    @SerialName("book_count") val bookCount: Int = 0,
    @SerialName("min_number") val minNumber: String? = null,
    @SerialName("max_number") val maxNumber: String? = null,
    val children: List<StaffSeries> = emptyList(),
    val description: String? = null,
    val books: List<SeriesBook> = emptyList(),
    val related: List<SeriesRelated> = emptyList(),
)

@Serializable
data class SeriesWrite(
    val name: String,
    val type: String? = null,
    @SerialName("parent_id") val parentId: Int? = null,
    @SerialName("parent_name") val parentName: String? = null,
    val group: String? = null,
    val cycle: String? = null,
    @SerialName("cycle_order") val cycleOrder: Int? = null,
    val description: String? = null,
    val complete: Boolean? = null,
)

@Serializable
data class SeriesRenameRequest(val name: String, @SerialName("merge_if_exists") val mergeIfExists: Boolean)

@Serializable
data class TargetRequest(@SerialName("target_id") val targetId: Int)

@Serializable
data class SeriesMergeResult(val target: IdName = IdName(), @SerialName("books_moved") val booksMoved: Int = 0)

@Serializable
data class SeriesDeleteResult(
    val id: Int = 0,
    val deleted: Boolean = false,
    @SerialName("books_updated") val booksUpdated: Int = 0,
    @SerialName("children_reattached") val childrenReattached: Int = 0,
)

@Serializable
data class BookIdsRequest(@SerialName("book_ids") val bookIds: List<Int>)

@Serializable
data class AssignResult(val assigned: Int = 0)

@Serializable
data class ParentWorkRequest(val title: String)

@Serializable
data class ParentWorkResult(@SerialName("book_id") val bookId: Int = 0, val title: String = "")

// ---------- Merges ----------

@Serializable
data class DuplicateAuthor(val id: Int = 0, val name: String = "", @SerialName("books_count") val booksCount: Int = 0)

@Serializable
data class MergeRequest(
    val ids: List<Int>,
    @SerialName("primary_id") val primaryId: Int? = null,
    @SerialName("new_name") val newName: String? = null,
)

@Serializable
data class MergeResult(@SerialName("primary_id") val primaryId: Int = 0)

@Serializable
data class GenreMergeResult(
    @SerialName("target_id") val targetId: Int = 0,
    @SerialName("books_updated") val booksUpdated: Int = 0,
    @SerialName("children_moved") val childrenMoved: Int = 0,
)

// ---------- Labels ----------

/** `GET|PUT /staff/settings/labels`: dimensions in millimetres (10..100). */
@Serializable
data class LabelSettings(
    val width: Double? = null,
    val height: Double? = null,
    val padding: Double? = null,
    @SerialName("show_app_name") val showAppName: Boolean? = null,
    @SerialName("show_title") val showTitle: Boolean? = null,
    @SerialName("show_subtitle") val showSubtitle: Boolean? = null,
    @SerialName("show_author_publisher") val showAuthorPublisher: Boolean? = null,
    @SerialName("show_dewey") val showDewey: Boolean? = null,
)

// ---------- Imports ----------

@Serializable
data class ImportJob(
    val id: String = "",
    /** `csv` | `librarything`. */
    val kind: String = "csv",
    @SerialName("file_name") val fileName: String = "",
    /** `ready` | `running` | `completed` | `failed` | `cancelled`. */
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
) {
    /** One readable line per error: a string, or an object's `message`/`error` (with its `row`). */
    val errorLines: List<String> get() = errorDetails.mapNotNull { detail ->
        when (detail) {
            is JsonObject -> {
                val text = (detail["message"] ?: detail["error"] ?: detail["title"])?.primitiveText()
                val row = detail["row"]?.primitiveText()
                when {
                    text != null && row != null && !text.contains(row) -> "$row: $text"
                    text != null -> text
                    else -> detail.toString()
                }
            }
            else -> detail.displayText()
        }
    }
}

@Serializable
data class ChunkRequest(val start: Int? = null, val size: Int? = null)

@Serializable
data class ImportHistoryRow(
    @SerialName("import_id") val importId: String = "",
    val kind: String? = null,
    @SerialName("file_name") val fileName: String? = null,
    val user: IdName? = null,
    @SerialName("total_rows") val totalRows: Int = 0,
    val imported: Int = 0,
    val updated: Int = 0,
    val failed: Int = 0,
    val status: String? = null,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
)

@Serializable
data class ImportsOverview(
    val jobs: List<ImportJob> = emptyList(),
    val history: List<ImportHistoryRow> = emptyList(),
)

// ---------- Covers / enrichment ----------

@Serializable
data class MissingCover(val id: Int = 0, val title: String = "", val isbn13: String? = null)

@Serializable
data class FetchCoverResult(
    val fetched: Boolean = false,
    @SerialName("cover_url") val coverUrl: String? = null,
    /** `already_has_cover` | `no_isbn` | `invalid_isbn` | `no_cover_found` | null. */
    val reason: String? = null,
)

@Serializable
data class SyncResult(val synced: Int = 0, val skipped: Int = 0, val errors: Int = 0, val total: Int = 0)

@Serializable
data class EnrichStats(
    @SerialName("total_with_isbn") val totalWithIsbn: Int = 0,
    @SerialName("missing_cover") val missingCover: Int = 0,
    @SerialName("missing_description") val missingDescription: Int = 0,
    val pending: Int = 0,
)

@Serializable
data class EnrichOverview(val stats: EnrichStats = EnrichStats(), @SerialName("cron_enabled") val cronEnabled: Boolean = false)

@Serializable
data class EnrichBatchResult(
    val processed: Int = 0,
    val enriched: Int = 0,
    @SerialName("not_found") val notFound: Int = 0,
    val skipped: Int = 0,
    val errors: Int = 0,
    val details: List<JsonElement> = emptyList(),
)

@Serializable
data class CronRequest(val enabled: Boolean)

@Serializable
data class CronResult(val enabled: Boolean = false)
