package com.pinakes.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Inventory stocktake (Mobile API 1.10.0, Pinakes 0.9.0 /admin/inventory; tag `staff-stocktake`).
// The phone is the scanner of a revision session. Codes are the API's English ones (`found`,
// `misplaced`, `open`, `lost`, ...); labels come already translated by the server. Every field
// has a default: the server may add keys, and a missing one must never break a scan.

/** A badge colour of the web page: family name plus the hex pair (Tailwind 800 on 100). */
@Serializable
data class Staff090StocktakeColor(
    val name: String = "gray",
    val foreground: String = "",
    val background: String = "",
)

/** The beep the web scan page plays for an outcome. */
@Serializable
data class Staff090StocktakeTone(
    @SerialName("frequency_hz") val frequencyHz: Int = 0,
    @SerialName("duration_ms") val durationMs: Int = 0,
)

/** A scan outcome / report category. `missing` is never the answer to a scan (`scan_result` false, no tone). */
@Serializable
data class Staff090StocktakeOutcome(
    val code: String = "",
    val label: String = "",
    val color: Staff090StocktakeColor? = null,
    val tone: Staff090StocktakeTone? = null,
    @SerialName("scan_result") val scanResult: Boolean = true,
)

@Serializable
data class Staff090StocktakeStatus(
    val code: String = "",
    val label: String = "",
    val color: Staff090StocktakeColor? = null,
)

/** What closing does with the copies nobody scanned: `none`, or `lost` (administrators only). */
@Serializable
data class Staff090StocktakeMissingAction(
    val code: String = "",
    val label: String = "",
    @SerialName("admin_only") val adminOnly: Boolean = false,
)

@Serializable
data class Staff090StocktakeLevel(val id: Int = 0, val level: Int = 0)

@Serializable
data class Staff090StocktakeShelf(
    val id: Int = 0,
    val code: String = "",
    val name: String = "",
    /** "[A] Narrativa", as the web lists it. */
    val label: String = "",
    val levels: List<Staff090StocktakeLevel> = emptyList(),
)

@Serializable
data class Staff090StocktakeGenre(val id: Int = 0, val label: String = "")

@Serializable
data class Staff090StocktakeCodeLabel(val code: String = "", val label: String = "")

@Serializable
data class Staff090StocktakeLimits(
    @SerialName("max_code_length") val maxCodeLength: Int = 100,
    @SerialName("max_upload_codes") val maxUploadCodes: Int = 5000,
    @SerialName("max_upload_bytes") val maxUploadBytes: Long = 2_097_152,
    @SerialName("upload_extensions") val uploadExtensions: List<String> = listOf("txt", "csv"),
    @SerialName("max_name_length") val maxNameLength: Int = 255,
    @SerialName("max_collocation_length") val maxCollocationLength: Int = 50,
    @SerialName("scans_per_minute") val scansPerMinute: Int = 600,
)

/** `GET /staff/stocktake/options`: everything the create form and the scan screen need. */
@Serializable
data class Staff090StocktakeOptions(
    val branches: List<IdName> = emptyList(),
    val shelves: List<Staff090StocktakeShelf> = emptyList(),
    val genres: List<Staff090StocktakeGenre> = emptyList(),
    @SerialName("media_types") val mediaTypes: List<Staff090StocktakeCodeLabel> = emptyList(),
    val outcomes: List<Staff090StocktakeOutcome> = emptyList(),
    val statuses: List<Staff090StocktakeStatus> = emptyList(),
    @SerialName("missing_actions") val missingActions: List<Staff090StocktakeMissingAction> = emptyList(),
    /** True for administrators: the "mark the missing copies lost" close. */
    @SerialName("can_mark_lost") val canMarkLost: Boolean = false,
    val limits: Staff090StocktakeLimits = Staff090StocktakeLimits(),
)

/** The live counters of a session (`mine` = scans by the signed-in operator). */
@Serializable
data class Staff090StocktakeSummary(
    val expected: Int = 0,
    val found: Int = 0,
    val misplaced: Int = 0,
    @SerialName("on_loan") val onLoan: Int = 0,
    val missing: Int = 0,
    val lost: Int = 0,
    val unknown: Int = 0,
    @SerialName("out_of_scope") val outOfScope: Int = 0,
    val duplicates: Int = 0,
    val scans: Int = 0,
    val mine: Int = 0,
)

/** The frozen selection of a session; absent keys are null. */
@Serializable
data class Staff090StocktakeFilter(
    @SerialName("shelf_id") val shelfId: Int? = null,
    @SerialName("level_id") val levelId: Int? = null,
    @SerialName("genre_id") val genreId: Int? = null,
    @SerialName("media_type") val mediaType: String? = null,
    @SerialName("collocation_from") val collocationFrom: String? = null,
    @SerialName("collocation_to") val collocationTo: String? = null,
)

/** One recorded scan. `code` is what was read, `inventory_number` the copy's code when known. */
@Serializable
data class Staff090StocktakeScan(
    val id: Int = 0,
    val code: String = "",
    @SerialName("inventory_number") val inventoryNumber: String? = null,
    val outcome: String = "",
    @SerialName("outcome_label") val outcomeLabel: String = "",
    val color: Staff090StocktakeColor? = null,
    @SerialName("copy_id") val copyId: Int? = null,
    @SerialName("book_id") val bookId: Int? = null,
    val title: String? = null,
    val collocation: String? = null,
    @SerialName("media_type") val mediaType: String? = null,
    @SerialName("copy_status") val copyStatus: String? = null,
    @SerialName("copy_status_label") val copyStatusLabel: String? = null,
    /** The copy is recorded as out of circulation (lost, withdrawn...) yet it turned up. */
    @SerialName("off_shelf") val offShelf: Boolean = false,
    /** Where a misplaced copy belongs, as a shelf label. */
    @SerialName("expected_shelf") val expectedShelf: String? = null,
    val operator: IdName? = null,
    @SerialName("scanned_at") val scannedAt: String? = null,
)

@Serializable
data class Staff090StocktakeHistoryEvent(
    val event: String = "",
    val label: String = "",
    @SerialName("operator_name") val operatorName: String? = null,
    val at: String? = null,
)

/** What a close did: how many copies it marked lost, and whether some are still pending. */
@Serializable
data class Staff090StocktakeCloseResult(
    val resumed: Boolean = false,
    @SerialName("marked_lost") val markedLost: Int = 0,
    @SerialName("pending_lost") val pendingLost: Int = 0,
    val partial: Boolean = false,
    val message: String = "",
)

/** A session; the detail (and every write's answer) adds `recent_scans` and `history`. */
@Serializable
data class Staff090StocktakeSession(
    val id: Int = 0,
    val name: String = "",
    val status: String = "",
    @SerialName("status_label") val statusLabel: String = "",
    @SerialName("status_color") val statusColor: Staff090StocktakeColor? = null,
    val branch: IdName? = null,
    val filter: Staff090StocktakeFilter = Staff090StocktakeFilter(),
    @SerialName("filter_labels") val filterLabels: List<String> = emptyList(),
    val note: String? = null,
    @SerialName("expected_total") val expectedTotal: Int = 0,
    @SerialName("opened_at") val openedAt: String? = null,
    @SerialName("opened_by") val openedBy: IdName? = null,
    @SerialName("closed_at") val closedAt: String? = null,
    @SerialName("closed_by") val closedBy: IdName? = null,
    @SerialName("missing_action") val missingAction: String? = null,
    val summary: Staff090StocktakeSummary = Staff090StocktakeSummary(),
    /** Copies a mark-lost close did not reach yet (resumable). */
    @SerialName("pending_lost") val pendingLost: Int = 0,
    @SerialName("can_scan") val canScan: Boolean = false,
    @SerialName("can_upload") val canUpload: Boolean = false,
    @SerialName("can_close") val canClose: Boolean = false,
    @SerialName("can_cancel") val canCancel: Boolean = false,
    @SerialName("can_mark_lost") val canMarkLost: Boolean = false,
    @SerialName("recent_scans") val recentScans: List<Staff090StocktakeScan> = emptyList(),
    val history: List<Staff090StocktakeHistoryEvent> = emptyList(),
    @SerialName("close_result") val closeResult: Staff090StocktakeCloseResult? = null,
    /** The server's sentence after a cancel. */
    val message: String? = null,
) {
    val isOpen: Boolean get() = status == "open"
}

/** `GET .../state`: the light payload the scan screen polls. */
@Serializable
data class Staff090StocktakeState(
    val id: Int = 0,
    val status: String = "",
    val summary: Staff090StocktakeSummary = Staff090StocktakeSummary(),
    @SerialName("recent_scans") val recentScans: List<Staff090StocktakeScan> = emptyList(),
)

/** `POST .../scans`: the scan, what to play, the updated counters. */
@Serializable
data class Staff090StocktakeScanResult(
    val scan: Staff090StocktakeScan = Staff090StocktakeScan(),
    val tone: Staff090StocktakeTone? = null,
    val summary: Staff090StocktakeSummary = Staff090StocktakeSummary(),
    @SerialName("session_status") val sessionStatus: String = "open",
)

/** A report row: every expected copy with its bucket, then every scan not about an expected copy. */
@Serializable
data class Staff090StocktakeReportRow(
    val category: String = "",
    @SerialName("category_label") val categoryLabel: String = "",
    val color: Staff090StocktakeColor? = null,
    val code: String = "",
    val title: String? = null,
    @SerialName("media_type") val mediaType: String? = null,
    val collocation: String? = null,
    @SerialName("expected_shelf") val expectedShelf: String? = null,
    @SerialName("copy_status") val copyStatus: String? = null,
    @SerialName("copy_status_label") val copyStatusLabel: String? = null,
    @SerialName("operator_name") val operatorName: String? = null,
    @SerialName("scanned_at") val scannedAt: String? = null,
)

@Serializable
data class Staff090StocktakeUploadCount(val code: String = "", val label: String = "", val count: Int = 0)

/** `POST .../upload`: how many codes the file held and what each became. */
@Serializable
data class Staff090StocktakeUpload(
    val total: Int = 0,
    val outcomes: List<Staff090StocktakeUploadCount> = emptyList(),
    val summary: Staff090StocktakeSummary = Staff090StocktakeSummary(),
    val message: String = "",
)

// ---------- Requests ----------

/** `POST /staff/stocktake/sessions`. Null keys are left out (the app Json drops explicit nulls). */
@Serializable
data class Staff090StocktakeCreateRequest(
    val name: String,
    val note: String? = null,
    @SerialName("branch_id") val branchId: Int? = null,
    @SerialName("shelf_id") val shelfId: Int? = null,
    @SerialName("level_id") val levelId: Int? = null,
    @SerialName("genre_id") val genreId: Int? = null,
    @SerialName("media_type") val mediaType: String? = null,
    @SerialName("collocation_from") val collocationFrom: String? = null,
    @SerialName("collocation_to") val collocationTo: String? = null,
)

@Serializable
data class Staff090StocktakeScanRequest(
    val code: String,
    @SerialName("shelf_id") val shelfId: Int? = null,
)

@Serializable
data class Staff090StocktakeCloseRequest(@SerialName("missing_action") val missingAction: String)
