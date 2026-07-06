package com.pinakes.app.ui.common

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Server timestamp → human, locale-aware date formatting. The API emits two shapes:
 * ISO-8601 UTC instants (rendered in the device's local zone) and raw MySQL wall-clock
 * values — `yyyy-MM-dd HH:mm[:ss]` DATETIMEs (reviews, devices, book club) and
 * `yyyy-MM-dd` DATEs — rendered as-is, matching the website. Robust to parse failures
 * (returns the raw input rather than throwing).
 */
object DateFormat {

    private val dateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault())

    private val dateTimeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(Locale.getDefault())

    private val wallClockSeconds = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val wallClockMinutes = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    /** "12 Jun 2026" — date only. Accepts ISO timestamps, MySQL DATETIMEs or yyyy-MM-dd. */
    fun date(iso: String?): String {
        if (iso.isNullOrBlank()) return "—"
        return runCatching {
            parseServerDateTime(iso)?.toLocalDate()?.format(dateFormatter)
                ?: LocalDate.parse(iso).format(dateFormatter)
        }.getOrDefault(iso)
    }

    /** "12 Jun 2026, 14:30" — date and time. Date-only inputs defer to [date]. */
    fun dateTime(iso: String?): String {
        if (iso.isNullOrBlank()) return "—"
        return runCatching {
            parseServerDateTime(iso)?.format(dateTimeFormatter) ?: date(iso)
        }.getOrDefault(iso)
    }

    /**
     * Parse any server timestamp with a time component: an ISO-8601 instant (converted to
     * the device zone) or a MySQL wall-clock DATETIME (kept as-is). Null when the input is
     * blank, date-only or unparseable.
     */
    fun parseServerDateTime(raw: String?): LocalDateTime? {
        if (raw.isNullOrBlank()) return null
        parseInstant(raw)?.let { return it.atZone(ZoneId.systemDefault()).toLocalDateTime() }
        return runCatching { LocalDateTime.parse(raw, wallClockSeconds) }.getOrNull()
            ?: runCatching { LocalDateTime.parse(raw, wallClockMinutes) }.getOrNull()
            ?: runCatching { LocalDateTime.parse(raw) }.getOrNull() // ISO local ("...T...", no zone)
    }

    /**
     * True when [raw] parses to a moment strictly in the past. False for null/unparseable
     * input — callers use this to gate optimistic UI, so unknown ≠ expired.
     *
     * TZ contract: an ISO-8601 instant (the bridge sends deadlines like `closes_at` as UTC
     * `…Z` via its `isoUtc()` helper) is compared as an absolute [Instant], so the verdict is
     * timezone-independent and correct regardless of the device zone. Only a bare wall-clock
     * DATETIME (no zone) falls back to a best-effort device-local comparison.
     */
    fun isPast(raw: String?): Boolean {
        if (raw.isNullOrBlank()) return false
        parseInstant(raw)?.let { return it.isBefore(Instant.now()) }
        return parseServerDateTime(raw)?.isBefore(LocalDateTime.now()) == true
    }

    private fun parseInstant(iso: String): Instant? = runCatching { Instant.parse(iso) }.getOrNull()

    /** Today as yyyy-MM-dd (local), used as a default reservation start date. */
    fun todayIso(): String = LocalDate.now().toString()

    /** yyyy-MM-dd N days from today (local). */
    fun isoPlusDays(days: Long): String = LocalDate.now().plusDays(days).toString()
}
