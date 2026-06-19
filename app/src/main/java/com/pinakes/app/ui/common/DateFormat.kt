package com.pinakes.app.ui.common

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * ISO-8601 (UTC) → human, locale-aware date formatting. All API timestamps are UTC; we render
 * them in the device's local zone. Robust to date-only strings and parse failures (returns the
 * raw input rather than throwing).
 */
object DateFormat {

    private val dateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault())

    private val dateTimeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(Locale.getDefault())

    /** "12 Jun 2026" — date only. Accepts full ISO timestamps or yyyy-MM-dd. */
    fun date(iso: String?): String {
        if (iso.isNullOrBlank()) return "—"
        return runCatching {
            val instant = parseInstant(iso)
            if (instant != null) {
                instant.atZone(ZoneId.systemDefault()).toLocalDate().format(dateFormatter)
            } else {
                LocalDate.parse(iso).format(dateFormatter)
            }
        }.getOrDefault(iso)
    }

    /** "12 Jun 2026, 14:30" — date and time in the local zone. */
    fun dateTime(iso: String?): String {
        if (iso.isNullOrBlank()) return "—"
        return runCatching {
            val instant = parseInstant(iso) ?: return date(iso)
            instant.atZone(ZoneId.systemDefault()).format(dateTimeFormatter)
        }.getOrDefault(iso)
    }

    private fun parseInstant(iso: String): Instant? = runCatching { Instant.parse(iso) }.getOrNull()

    /** Today as yyyy-MM-dd (local), used as a default reservation start date. */
    fun todayIso(): String = LocalDate.now().toString()

    /** yyyy-MM-dd N days from today (local). */
    fun isoPlusDays(days: Long): String = LocalDate.now().plusDays(days).toString()
}
