package com.pinakes.app.ui.screens.staff.s090.stocktake

import android.media.AudioManager
import androidx.annotation.StringRes
import com.pinakes.app.R
import com.pinakes.app.data.model.Staff090StocktakeColor
import com.pinakes.app.data.model.Staff090StocktakeCreateRequest
import com.pinakes.app.data.model.Staff090StocktakeLimits
import com.pinakes.app.data.model.Staff090StocktakeOptions
import com.pinakes.app.data.model.Staff090StocktakeScan
import com.pinakes.app.data.model.Staff090StocktakeSession
import com.pinakes.app.data.model.Staff090StocktakeShelf
import com.pinakes.app.data.model.Staff090StocktakeSummary
import com.pinakes.app.data.model.Staff090StocktakeTone
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.staff.ScanDebouncer
import com.pinakes.app.data.staff.Staff090StocktakeErrors
import com.pinakes.app.ui.common.DateFormat
import com.pinakes.app.ui.screens.staff.StaffMessage
import com.pinakes.app.ui.screens.staff.toStaffMessage
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

// The pure decisions of the stocktake and statistics screens, kept apart so they are unit-tested
// without a device: which camera reads become scans, which tone and colour an outcome gets, the
// create form, the close choices, the counters and the statistics bars.

/**
 * Which camera decodes become scans. A stocktake scans shelf after shelf, so the scanner never
 * stops: a label held under the camera is sent once, but reading it again after moving away
 * (it left the view for [awayMs], or another code was read meanwhile) is a deliberate second
 * read and is sent too; the server records it as a repeat, as the web scan page does.
 *
 * [ScanDebouncer] still asks for [requiredReads] identical decodes, so a misread while the
 * camera focuses is never sent; its own window is off ([ScanDebouncer.suppressMs] = 0) because
 * the window here slides: every decode of the held label pushes it forward. Clock-injected.
 */
class Staff090StocktakeScanGate(
    private val awayMs: Long = AWAY_MS,
    requiredReads: Int = 2,
) {
    private val debouncer = ScanDebouncer(requiredReads = requiredReads, suppressMs = 0)
    private var held: String? = null
    private var heldSeenAt = Long.MIN_VALUE

    /** Feed one decode (already normalised); returns the code when it should be sent. */
    fun offer(code: String, nowMs: Long): String? {
        if (code.isEmpty() || debouncer.paused) return null
        if (code == held && nowMs - heldSeenAt < awayMs) {
            // Still under the camera: no new scan, and the window moves with it.
            heldSeenAt = nowMs
            return null
        }
        val accepted = debouncer.offer(code, nowMs) ?: return null
        held = accepted
        heldSeenAt = nowMs
        return accepted
    }

    /** A code sent by hand (typed or a keyboard-wedge reader): the camera must not send it again at once. */
    fun markSent(code: String, nowMs: Long) {
        held = code
        heldSeenAt = nowMs
    }

    /** Stop sending (a dialog or a sheet covers the scanner). */
    fun pause() = debouncer.pause()

    /** Send again; the label still under the camera keeps its window. */
    fun resume() = debouncer.resume()

    val paused: Boolean get() = debouncer.paused

    companion object {
        /** A label out of view this long has been moved away. */
        const val AWAY_MS = 1_500L
    }
}

/**
 * The scan beep: the server's frequency and duration (the web page's), clamped to what a phone
 * speaker plays, and silent unless the ringer is in normal mode. Pure: the samples are made
 * here, the player only writes them to an AudioTrack.
 */
object Staff090StocktakeSound {
    const val SAMPLE_RATE = 22_050
    private const val MIN_HZ = 150
    private const val MAX_HZ = 4_000
    private const val MIN_MS = 40
    private const val MAX_MS = 800
    private const val FADE_MS = 8

    data class Spec(val frequencyHz: Int, val durationMs: Int)

    /** The tone of a scan answer: its own `tone`, else the outcome's from the options. */
    fun toneFor(scan: Staff090StocktakeScan, answered: Staff090StocktakeTone?, options: Staff090StocktakeOptions?): Staff090StocktakeTone? =
        answered?.takeIf { it.frequencyHz > 0 && it.durationMs > 0 }
            ?: options?.outcomes?.firstOrNull { it.code == scan.outcome }?.tone?.takeIf { it.frequencyHz > 0 && it.durationMs > 0 }

    /** What to play for [tone] with the phone's [ringerMode]; null = stay silent. */
    fun spec(tone: Staff090StocktakeTone?, ringerMode: Int): Spec? {
        if (tone == null || tone.frequencyHz <= 0 || tone.durationMs <= 0) return null
        if (ringerMode != AudioManager.RINGER_MODE_NORMAL) return null
        return Spec(tone.frequencyHz.coerceIn(MIN_HZ, MAX_HZ), tone.durationMs.coerceIn(MIN_MS, MAX_MS))
    }

    /** 16-bit mono PCM of [spec], with a short fade at both ends so the speaker does not click. */
    fun samples(spec: Spec, sampleRate: Int = SAMPLE_RATE, volume: Double = 0.6): ShortArray {
        val count = (sampleRate.toLong() * spec.durationMs / 1000).toInt()
        val fade = min(count / 2, sampleRate * FADE_MS / 1000).coerceAtLeast(1)
        return ShortArray(count) { i ->
            val envelope = when {
                i < fade -> i.toDouble() / fade
                i >= count - fade -> (count - 1 - i).toDouble() / fade
                else -> 1.0
            }
            (sin(2 * PI * spec.frequencyHz * i / sampleRate) * envelope * volume * Short.MAX_VALUE).roundToInt().toShort()
        }
    }
}

/** The server's badge colours ("#166534") as ARGB, for Compose `Color(Long)`. Pure. */
object Staff090StocktakeColors {

    /** `#rrggbb` (or `#rgb`) → 0xFFrrggbb; null for anything else. */
    fun argb(hex: String?): Long? {
        val raw = hex?.trim()?.removePrefix("#") ?: return null
        val full = when (raw.length) {
            3 -> raw.map { "$it$it" }.joinToString("")
            6 -> raw
            else -> return null
        }
        return full.toLongOrNull(16)?.let { 0xFF000000L or it }
    }

    /** The colour of [outcome]: the one the row carries, else the options', else null (neutral). */
    fun forOutcome(outcome: String, own: Staff090StocktakeColor?, options: Staff090StocktakeOptions?): Staff090StocktakeColor? =
        own?.takeIf { argb(it.background) != null && argb(it.foreground) != null }
            ?: options?.outcomes?.firstOrNull { it.code == outcome }?.color?.takeIf { argb(it.background) != null && argb(it.foreground) != null }
}

/** One counter tile of the session screen. */
data class Staff090StocktakeCounter(val key: String, @param:StringRes val label: Int, val value: Int, val outcome: String? = null)

object Staff090StocktakeCounters {
    /** Every counter the web shows, in its order; `lost` only once a close marked copies lost. */
    fun of(s: Staff090StocktakeSummary): List<Staff090StocktakeCounter> = listOfNotNull(
        Staff090StocktakeCounter("expected", R.string.staff090_stocktake_count_expected, s.expected),
        Staff090StocktakeCounter("found", R.string.staff090_stocktake_count_found, s.found, "found"),
        Staff090StocktakeCounter("misplaced", R.string.staff090_stocktake_count_misplaced, s.misplaced, "misplaced"),
        Staff090StocktakeCounter("on_loan", R.string.staff090_stocktake_count_on_loan, s.onLoan, "on_loan"),
        Staff090StocktakeCounter("missing", R.string.staff090_stocktake_count_missing, s.missing, "missing"),
        if (s.lost > 0) Staff090StocktakeCounter("lost", R.string.staff090_stocktake_count_lost, s.lost) else null,
        Staff090StocktakeCounter("unknown", R.string.staff090_stocktake_count_unknown, s.unknown, "unknown"),
        Staff090StocktakeCounter("out_of_scope", R.string.staff090_stocktake_count_out_of_scope, s.outOfScope, "out_of_scope"),
        Staff090StocktakeCounter("duplicates", R.string.staff090_stocktake_count_duplicates, s.duplicates, "duplicate"),
        Staff090StocktakeCounter("mine", R.string.staff090_stocktake_count_mine, s.mine),
    )
}

/** The create sheet's fields, as typed. */
data class Staff090StocktakeForm(
    val name: String = "",
    val note: String = "",
    val branchId: Int? = null,
    val shelfId: Int? = null,
    val levelId: Int? = null,
    val genreId: Int? = null,
    val mediaType: String? = null,
    val collocationFrom: String = "",
    val collocationTo: String = "",
) {
    fun nameValid(limits: Staff090StocktakeLimits): Boolean = name.trim().let { it.isNotEmpty() && it.length <= limits.maxNameLength }

    fun collocationValid(limits: Staff090StocktakeLimits): Boolean =
        collocationFrom.trim().length <= limits.maxCollocationLength && collocationTo.trim().length <= limits.maxCollocationLength

    fun valid(limits: Staff090StocktakeLimits): Boolean = nameValid(limits) && collocationValid(limits)

    /** A new shelf drops a level of another shelf. */
    fun withShelf(id: Int?): Staff090StocktakeForm = copy(shelfId = id, levelId = levelId.takeIf { id != null && id == shelfId })

    /** The levels the level picker offers: only the chosen shelf's. */
    fun levels(shelves: List<Staff090StocktakeShelf>) = shelves.firstOrNull { it.id == shelfId }?.levels.orEmpty()

    /** The request: trimmed texts, blank ones and unset pickers left out. */
    fun request(): Staff090StocktakeCreateRequest = Staff090StocktakeCreateRequest(
        name = name.trim(),
        note = note.trim().ifEmpty { null },
        branchId = branchId,
        shelfId = shelfId,
        levelId = levelId.takeIf { shelfId != null },
        genreId = genreId,
        mediaType = mediaType?.takeIf { it.isNotBlank() },
        collocationFrom = collocationFrom.trim().ifEmpty { null },
        collocationTo = collocationTo.trim().ifEmpty { null },
    )
}

/** What the close dialog offers and says. */
object Staff090StocktakeClose {
    const val NONE = "none"
    const val LOST = "lost"

    /**
     * The missing actions to offer: `none` while the session is open, `lost` only when the
     * server says this user may mark them (the session's `can_mark_lost`, which also covers
     * finishing a mark-lost close cut short). Never decided here beyond hiding.
     */
    fun actions(session: Staff090StocktakeSession): List<String> = buildList {
        if (session.canClose) add(NONE)
        if (session.canMarkLost) add(LOST)
    }

    /** A closed session whose mark-lost close did not reach every copy: it can be finished. */
    fun canResume(session: Staff090StocktakeSession): Boolean = !session.isOpen && session.canMarkLost && session.pendingLost > 0

    /** The copies the confirmation names: the missing ones, or those still to mark when resuming. */
    fun affected(session: Staff090StocktakeSession): Int = if (canResume(session)) session.pendingLost else session.summary.missing

    @StringRes
    fun confirmText(session: Staff090StocktakeSession, action: String): Int = when {
        canResume(session) -> R.string.staff090_stocktake_close_confirm_resume
        action == LOST -> R.string.staff090_stocktake_close_confirm_lost
        else -> R.string.staff090_stocktake_close_confirm_none
    }
}

/** Whether a picked code list is one the server takes (extension and size), before uploading it. */
object Staff090StocktakeUploadRules {
    fun acceptsName(name: String, limits: Limits): Boolean =
        name.substringAfterLast('.', "").lowercase(Locale.ROOT) in limits.extensions

    fun acceptsSize(size: Long, limits: Limits): Boolean = size in 1..limits.maxBytes

    /** The two limits as a small value, so the check is testable without the whole options payload. */
    data class Limits(val extensions: List<String>, val maxBytes: Long) {
        companion object {
            fun of(limits: Staff090StocktakeLimits) = Limits(limits.uploadExtensions.map { it.lowercase(Locale.ROOT) }, limits.maxUploadBytes)
        }
    }
}

/** The live session updated by a scan answer or a poll. Pure. */
object Staff090StocktakeMerge {
    /** Newest first, without the same scan twice (a poll may already carry it). */
    fun prepend(list: List<Staff090StocktakeScan>, scan: Staff090StocktakeScan, max: Int = 50): List<Staff090StocktakeScan> =
        (listOf(scan) + list.filter { it.id != scan.id }).take(max)

    /**
     * The session after a status change seen by a scan answer or a poll: no more scanning once not
     * open. Closed and cancelled are final: an answer still saying "open" was read before the
     * session ended (a poll in flight during the close) and never reopens it.
     */
    fun withStatus(session: Staff090StocktakeSession, status: String): Staff090StocktakeSession =
        if (status == session.status || status.isBlank() || (session.status != "open" && status == "open")) session
        else session.copy(status = status, canScan = status == "open" && session.canScan, canUpload = status == "open" && session.canUpload,
            canClose = status == "open" && session.canClose, canCancel = status == "open" && session.canCancel)

    /** Polling runs only while the session takes scans. */
    fun shouldPoll(session: Staff090StocktakeSession?): Boolean = session?.isOpen == true

    /**
     * A first page read again by the poll, applied to the outcome list. Until the operator loads a
     * further page it replaces the list. Afterwards the scans newer than the list go on top and the
     * pages already read stay, with their cursor: replacing them dropped every row past the first
     * page a few seconds after "Show more", and offered "Show more" again on a list at its end.
     */
    fun refreshedFiltered(
        current: List<Staff090StocktakeScan>,
        currentCursor: String?,
        paged: Boolean,
        firstPage: List<Staff090StocktakeScan>,
        firstCursor: String?,
    ): Pair<List<Staff090StocktakeScan>, String?> =
        if (!paged) firstPage to firstCursor else (firstPage + current).distinctBy(Staff090StocktakeScan::id) to currentCursor
}

/** The statistics bars and labels. Pure. */
object Staff090StatisticsBars {
    /** Width share of [value] against the largest bar; 0 for an empty chart, a sliver for a non-zero value. */
    fun fraction(value: Int, max: Int): Float = when {
        max <= 0 || value <= 0 -> 0f
        else -> (value.toFloat() / max).coerceIn(0.02f, 1f)
    }

    /** "2026-09" → "Sep 2026" in [locale]; the raw text when it is not a month. */
    fun monthLabel(month: String, locale: Locale): String =
        runCatching { YearMonth.parse(month).format(DateTimeFormatter.ofPattern("MMM yyyy", locale)) }.getOrDefault(month)
}

/** A stocktake timestamp ("2026-11-01T20:15:26+00:00") shown in the app's date-time style. */
internal fun stocktakeDateTime(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    val instant = runCatching { OffsetDateTime.parse(raw).toInstant().toString() }.getOrNull()
    return DateFormat.dateTime(instant ?: raw)
}

/** The message for a failed stocktake call: ours for a stocktake code, the server's for `not_permitted`, else the staff wording. */
internal fun ApiResult.Failure.toStocktakeMessage(@StringRes fallback: Int): StaffMessage {
    Staff090StocktakeErrors.messageRes(code)?.takeIf { fromEnvelope }?.let { return StaffMessage(it) }
    if (Staff090StocktakeErrors.isNotPermitted(this)) {
        return message.takeIf { it.isNotBlank() }?.let { StaffMessage(text = it) } ?: StaffMessage(R.string.staff090_stocktake_not_permitted)
    }
    return toStaffMessage(fallback)
}

/**
 * A typed or read label as the server stores it (StocktakeService::normalizeCode): control
 * characters removed, trimmed. Inner spaces stay, unlike [com.pinakes.app.data.staff.CopyCodeCodec]:
 * the stocktake compares the code as read. A code over the limit is sent as is and the server
 * explains the refusal.
 */
fun normalizeStocktakeCode(raw: String): String = raw.filterNot { it.isISOControl() }.trim()
