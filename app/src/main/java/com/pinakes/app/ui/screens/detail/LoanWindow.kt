package com.pinakes.app.ui.screens.detail

import com.pinakes.app.data.model.AvailabilityCalendar
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * The loan the reader can still ask for from a start day, read off the availability calendar.
 *
 * The server adds the library's `loan_duration_days` to the chosen start to set the return
 * date. When a scheduled loan or a reservation on every copy (a `full` day) begins before that
 * default return date, the server refuses the whole window, so the request must stop the day
 * before it instead: that is [endDate], sent as `end_date` of POST /reservations (Mobile API
 * 1.8.2), and [capped] says the default was shortened, which the sheet explains under the
 * calendar with [maxDays] and [firstBlocked]. Same rule as the website's calendar.
 */
data class LoanWindow(
    /** The return date to request: start + duration, or the day before [firstBlocked]. */
    val endDate: String,
    /** The first fully-booked day inside the default duration, or null. */
    val firstBlocked: String?,
    /** Days from the start to [endDate]. */
    val maxDays: Int,
    /** True when [firstBlocked] shortened the default duration. */
    val capped: Boolean,
    /** Send the displayed date when either a commitment or the configured maximum shortened it. */
    val requiresEndDate: Boolean = capped,
) {
    /** False when the very next day is blocked: no loan of at least one day exists from this start. */
    val possible: Boolean get() = maxDays >= 1
}

/**
 * A day the grid paints red and refuses to pick: listed in `unavailable_dates`, marked `full`,
 * or labelled `free` with no free copy (a stale label the server can send). One predicate for
 * the calendar and the window, so what the reader sees as blocked is what caps the loan.
 */
internal fun AvailabilityCalendar.isFullyBooked(isoDate: String): Boolean {
    if (unavailableDates.contains(isoDate)) return true
    val day = days.firstOrNull { it.date == isoDate } ?: return false
    return day.state == "full" || (day.state == "free" && day.available <= 0)
}

/**
 * The window from [startIso] (yyyy-MM-dd), or null when the duration is unknown (an older
 * server omits `loan_duration_days`) or the start is not a date: then the server's default
 * applies and nothing can be said about it.
 */
internal fun loanWindowFrom(startIso: String, calendar: AvailabilityCalendar?, loanDurationDays: Int?): LoanWindow? {
    if (loanDurationDays == null || loanDurationDays < 1) return null
    val start = runCatching { LocalDate.parse(startIso) }.getOrNull() ?: return null
    val durationDays = calendar?.maxLoanDurationDays?.takeIf { it > 0 }
        ?.let { minOf(loanDurationDays, it) } ?: loanDurationDays
    var firstBlocked: String? = null
    for (offset in 1..durationDays) {
        val day = start.plusDays(offset.toLong()).toString()
        if (calendar?.isFullyBooked(day) == true) {
            firstBlocked = day
            break
        }
    }
    val defaultEnd = start.plusDays(durationDays.toLong())
    val end = firstBlocked?.let { LocalDate.parse(it).minusDays(1) } ?: defaultEnd
    return LoanWindow(
        endDate = end.toString(),
        firstBlocked = firstBlocked,
        maxDays = ChronoUnit.DAYS.between(start, end).toInt(),
        capped = firstBlocked != null,
        requiresEndDate = end.isBefore(start.plusDays(loanDurationDays.toLong())),
    )
}
